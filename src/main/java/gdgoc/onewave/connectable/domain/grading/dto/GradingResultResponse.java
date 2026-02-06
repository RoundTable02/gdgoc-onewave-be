package gdgoc.onewave.connectable.domain.grading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Grading Result Item")
public record GradingResultResponse(
    @Schema(description = "Grading Result ID")
    UUID id,
    @Schema(description = "Task Name")
    String taskName,
    @Schema(description = "Pass status")
    Boolean isPassed,
    @Schema(description = "Detailed feedback")
    String feedback
) {}