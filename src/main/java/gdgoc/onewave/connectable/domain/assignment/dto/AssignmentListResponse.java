package gdgoc.onewave.connectable.domain.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Assignment List Item Response")
public record AssignmentListResponse(
    @Schema(description = "Assignment ID")
    UUID id,
    @Schema(description = "Assignment Title")
    String title,
    @Schema(description = "Assignment Content (truncated to 200 chars)")
    String content,
    @Schema(description = "Created at")
    LocalDateTime createdAt
) {}
