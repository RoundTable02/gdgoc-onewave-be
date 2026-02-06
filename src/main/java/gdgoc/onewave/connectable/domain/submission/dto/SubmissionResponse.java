package gdgoc.onewave.connectable.domain.submission.dto;

import gdgoc.onewave.connectable.domain.grading.dto.GradingResultResponse;
import gdgoc.onewave.connectable.domain.submission.entity.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Submission and Grading Result Response")
public record SubmissionResponse(
    @Schema(description = "Submission ID")
    UUID id,
    @Schema(description = "Candidate Name")
    String candidateName,
    @Schema(description = "Static hosting URL")
    String fileUrl,
    @Schema(description = "Grading status")
    SubmissionStatus status,
    @Schema(description = "Detailed grading results")
    List<GradingResultResponse> gradingResults,
    @Schema(description = "Grading summary")
    GradingSummary summary,
    @Schema(description = "Created at")
    LocalDateTime createdAt
) {
    @Schema(description = "Grading Summary Information")
    public record GradingSummary(
        @Schema(description = "Passed tasks count")
        int passedCount,
        @Schema(description = "Total tasks count")
        int totalCount,
        @Schema(description = "Pass rate", example = "80%")
        String passRate
    ) {}
}