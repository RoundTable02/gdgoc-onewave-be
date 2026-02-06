package gdgoc.onewave.connectable.domain.submission.service;

import gdgoc.onewave.connectable.domain.assignment.repository.AssignmentRepository;
import gdgoc.onewave.connectable.domain.entity.Assignment;
import gdgoc.onewave.connectable.domain.entity.GradingResult;
import gdgoc.onewave.connectable.domain.entity.Submission;
import gdgoc.onewave.connectable.domain.entity.SubmissionStatus;
import gdgoc.onewave.connectable.domain.grading.dto.GradingResultResponse;
import gdgoc.onewave.connectable.domain.grading.repository.GradingResultRepository;
import gdgoc.onewave.connectable.domain.submission.dto.SubmissionRequest;
import gdgoc.onewave.connectable.domain.submission.dto.SubmissionResponse;
import gdgoc.onewave.connectable.domain.submission.repository.SubmissionRepository;
import gdgoc.onewave.connectable.global.exception.BusinessException;
import gdgoc.onewave.connectable.global.exception.ErrorCode;
import gdgoc.onewave.connectable.infrastructure.worker.GradingWorkerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final GradingWorkerClient gradingWorkerClient;
    private final GradingResultRepository gradingResultRepository;

    @Transactional
    public SubmissionResponse submit(UUID assignmentId, SubmissionRequest request) {
        // 1. Find assignment
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // 2. Create submission temporarily (status=FAILED initially)
        Submission submission = Submission.builder()
                .userId(UUID.fromString(request.userId()))
                .assignment(assignment)
                .fileUrl(request.url())
                .status(SubmissionStatus.FAILED)
                .build();
        submission = submissionRepository.save(submission);

        // 3. Call grading worker
        GradingWorkerClient.GradingRequest gradingRequest = new GradingWorkerClient.GradingRequest(
                submission.getId(),
                request.url(),
                assignment.getAiScript()
        );
        GradingWorkerClient.GradingResponse gradingResponse = gradingWorkerClient.grade(gradingRequest);

        // 4. Determine status based on worker response
        SubmissionStatus finalStatus = gradingResponse.success() ? SubmissionStatus.COMPLETED : SubmissionStatus.FAILED;

        // 5. Update submission with final status
        submission = Submission.builder()
                .id(submission.getId())
                .userId(submission.getUserId())
                .assignment(assignment)
                .fileUrl(request.url())
                .status(finalStatus)
                .createdAt(submission.getCreatedAt())
                .build();
        Submission finalSubmission = submissionRepository.save(submission);

        // 6. Create and save GradingResult entities
        List<GradingResult> gradingResults = gradingResponse.results().stream()
                .map(item -> GradingResult.builder()
                        .submission(finalSubmission)
                        .taskName(item.taskName())
                        .isPassed(item.isPassed())
                        .build())
                .collect(Collectors.toList());
        gradingResultRepository.saveAll(gradingResults);

        // 7. Build response with grading results
        List<GradingResultResponse> gradingResultResponses = gradingResults.stream()
                .map(gr -> new GradingResultResponse(gr.getTaskName(), gr.getIsPassed()))
                .collect(Collectors.toList());

        int passedCount = (int) gradingResults.stream().filter(GradingResult::getIsPassed).count();
        int totalCount = gradingResults.size();
        String passRate = totalCount > 0 ? String.format("%.0f%%", (passedCount * 100.0 / totalCount)) : "0%";

        return new SubmissionResponse(
                finalSubmission.getId(),
                finalSubmission.getFileUrl(),
                finalSubmission.getStatus(),
                gradingResultResponses,
                new SubmissionResponse.GradingSummary(passedCount, totalCount, passRate),
                finalSubmission.getCreatedAt()
        );
    }
}
