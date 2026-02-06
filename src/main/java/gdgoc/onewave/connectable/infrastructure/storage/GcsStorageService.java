package gdgoc.onewave.connectable.infrastructure.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import gdgoc.onewave.connectable.global.exception.BusinessException;
import gdgoc.onewave.connectable.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcsStorageService {

    private final Storage storage;

    @Value("${gcs.bucket-name}")
    private String bucketName;

    @Value("${gcs.base-url}")
    private String baseUrl;

    public String uploadAndExtractZip(MultipartFile file, UUID submissionId) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("submission-");
            extractZip(file.getInputStream(), tempDir);
            uploadDirectory(tempDir, "submissions/" + submissionId);
            return baseUrl + "/" + bucketName + "/submissions/" + submissionId + "/index.html";
        } catch (IOException e) {
            log.error("Failed to upload zip file for submission {}", submissionId, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    private void extractZip(InputStream inputStream, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolvedPath = targetDir.resolve(entry.getName()).normalize();

                // Zip Slip protection
                if (!resolvedPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry is outside of the target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    Files.copy(zis, resolvedPath);
                }
                zis.closeEntry();
            }
        }
    }

    private void uploadDirectory(Path localDir, String gcsPrefix) throws IOException {
        Files.walkFileTree(localDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativePath = localDir.relativize(file).toString();
                String objectName = gcsPrefix + "/" + relativePath;
                uploadFile(file, objectName);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void uploadFile(Path filePath, String objectName) throws IOException {
        String contentType = URLConnection.guessContentTypeFromName(filePath.toString());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        storage.create(blobInfo, Files.readAllBytes(filePath));
        log.debug("Uploaded {} to gs://{}/{}", filePath.getFileName(), bucketName, objectName);
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory: {}", dir, e);
        }
    }
}
