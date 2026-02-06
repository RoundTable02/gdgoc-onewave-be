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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Deprecated
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
        Path tempZipFile = null;
        try {
            tempDir = Files.createTempDirectory("submission-");
            tempZipFile = Files.createTempFile("upload-", ".zip");
            file.transferTo(tempZipFile.toFile());
            extractZip(tempZipFile, tempDir);
            Path uploadRoot = findUploadRoot(tempDir);
            if (!Files.exists(uploadRoot.resolve("index.html"))) {
                generateIndexHtml(uploadRoot);
            }
            rewritePathsInHtml(uploadRoot.resolve("index.html"), uploadRoot);
            uploadDirectory(uploadRoot, "submissions/" + submissionId);
            return baseUrl + "/" + bucketName + "/submissions/" + submissionId + "/index.html";
        } catch (IOException e) {
            log.error("Failed to upload zip file for submission {}", submissionId, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } finally {
            if (tempZipFile != null) {
                try { Files.deleteIfExists(tempZipFile); } catch (IOException ignored) {}
            }
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    private void extractZip(Path zipFilePath, Path targetDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // Skip macOS metadata
                String entryName = entry.getName();
                if (entryName.startsWith("__MACOSX/") || entryName.contains("/__MACOSX/")
                        || entryName.endsWith(".DS_Store") || entryName.contains("/.DS_Store")) {
                    continue;
                }

                Path resolvedPath = targetDir.resolve(entry.getName()).normalize();

                // Zip Slip protection
                if (!resolvedPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry is outside of the target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, resolvedPath);
                    }
                }
            }
        }
    }

    /**
     * Find the directory containing index.html and use it as the upload root.
     * Handles any level of nesting (e.g. zip/folder/subfolder/index.html).
     */
    private Path findUploadRoot(Path extractedDir) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(extractedDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("index.html")) {
                    found.add(file.getParent());
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (!found.isEmpty()) {
            log.debug("Found index.html in '{}', using as upload root", extractedDir.relativize(found.get(0)));
            return found.get(0);
        }
        // No index.html found — fall back to single root folder unwrap
        File[] children = extractedDir.toFile().listFiles(
                f -> !f.getName().startsWith(".") && !f.getName().equals("__MACOSX")
        );
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            log.debug("No index.html found, unwrapping single root folder '{}'", children[0].getName());
            return children[0].toPath();
        }
        return extractedDir;
    }

    private void generateIndexHtml(Path dir) throws IOException {
        List<String> files = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String relativePath = dir.relativize(file).toString();
                files.add(relativePath);
                return FileVisitResult.CONTINUE;
            }
        });

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<title>Submission Files</title>");
        html.append("<style>body{font-family:monospace;padding:20px}a{display:block;padding:4px 0}</style>");
        html.append("</head><body><h2>Submitted Files</h2>");
        for (String filePath : files.stream().sorted().toList()) {
            html.append("<a href=\"").append(filePath).append("\">").append(filePath).append("</a>");
        }
        html.append("</body></html>");

        Files.writeString(dir.resolve("index.html"), html.toString());
        log.debug("Generated index.html with {} file entries", files.size());
    }

    private void rewritePathsInHtml(Path indexHtml, Path uploadRoot) throws IOException {
        if (!Files.exists(indexHtml)) {
            return;
        }
        String content = Files.readString(indexHtml);
        String rewritten = content;

        // Detect and strip non-existent path prefixes from HTML
        // React builds often have "/app-name/" prefix even when folder doesn't exist
        java.util.regex.Pattern prefixPattern = java.util.regex.Pattern.compile(
                "(?:href|src|action)=[\"'](?:\\.?)/([a-zA-Z0-9_-]+)/(static|assets|js|css|images|img|fonts|media)/");
        java.util.regex.Matcher matcher = prefixPattern.matcher(content);
        
        java.util.Set<String> nonExistentPrefixes = new java.util.HashSet<>();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            Path prefixPath = uploadRoot.resolve(prefix);
            if (!Files.exists(prefixPath)) {
                nonExistentPrefixes.add(prefix);
            }
        }
        
        for (String prefix : nonExistentPrefixes) {
            rewritten = rewritten.replace("\"/" + prefix + "/", "\"/");
            rewritten = rewritten.replace("'/" + prefix + "/", "'/");
            rewritten = rewritten.replace("\"./" + prefix + "/", "\"./");
            rewritten = rewritten.replace("'./" + prefix + "/", "'./");
            log.debug("Stripped non-existent path prefix '{}' from index.html", prefix);
        }

        // Convert remaining absolute paths to relative
        rewritten = rewritten
                .replaceAll("(href|src|action)=\"/(?!/)", "$1=\"./")
                .replaceAll("(href|src|action)='/(?!/)", "$1='./");

        if (!content.equals(rewritten)) {
            Files.writeString(indexHtml, rewritten);
            log.debug("Rewrote paths in index.html for GCS hosting");
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
