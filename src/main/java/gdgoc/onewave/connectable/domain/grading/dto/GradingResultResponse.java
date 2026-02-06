package gdgoc.onewave.connectable.domain.grading.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Grading Result Item")
public record GradingResultResponse(
    @Schema(description = "Task Name")
    String taskName,
    @Schema(description = "Pass status")
    Boolean isPassed
) {}
