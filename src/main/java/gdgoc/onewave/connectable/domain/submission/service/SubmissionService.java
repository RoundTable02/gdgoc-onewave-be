package gdgoc.onewave.connectable.domain.submission.service;

import gdgoc.onewave.connectable.domain.assignment.repository.AssignmentRepository;
import gdgoc.onewave.connectable.domain.entity.Assignment;
import gdgoc.onewave.connectable.domain.entity.Submission;
import gdgoc.onewave.connectable.domain.entity.SubmissionStatus;
import gdgoc.onewave.connectable.domain.submission.dto.SubmissionResponse;
import gdgoc.onewave.connectable.domain.submission.repository.SubmissionRepository;
import gdgoc.onewave.connectable.global.exception.BusinessException;
import gdgoc.onewave.connectable.global.exception.ErrorCode;
import gdgoc.onewave.connectable.infrastructure.storage.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final GcsStorageService gcsStorageService;

    @Transactional
    public SubmissionResponse submit(UUID assignmentId, String userId, MultipartFile file) {
        // 1. Validate file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        // 2. Find assignment
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // 3. Upload zip to GCS
        Submission submission = Submission.builder()
                .userId(UUID.fromString(userId))
                .assignment(assignment)
                .fileUrl("")
                .status(SubmissionStatus.COMPLETED)
                .build();
        submission = submissionRepository.save(submission);

        String fileUrl = gcsStorageService.uploadAndExtractZip(file, submission.getId());

        // Update fileUrl - rebuild since entity is immutable via builder
        submission = Submission.builder()
                .id(submission.getId())
                .userId(submission.getUserId())
                .assignment(assignment)
                .fileUrl(fileUrl)
                .status(SubmissionStatus.COMPLETED)
                .createdAt(submission.getCreatedAt())
                .build();
        submission = submissionRepository.save(submission);

        // 4. Mock grading - empty results
        return new SubmissionResponse(
                submission.getId(),
                submission.getFileUrl(),
                submission.getStatus(),
                Collections.emptyList(),
                new SubmissionResponse.GradingSummary(0, 0, "0%"),
                submission.getCreatedAt()
        );
    }
}
