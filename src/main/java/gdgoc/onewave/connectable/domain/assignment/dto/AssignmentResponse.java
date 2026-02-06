package gdgoc.onewave.connectable.domain.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Assignment Detail Response")
public record AssignmentResponse(
    @Schema(description = "Assignment ID")
    UUID id,
    @Schema(description = "Assignment Title")
    String title,
    @Schema(description = "Assignment Content")
    String content,
    @Schema(description = "Sub tasks")
    List<String> subTasks,
    @Schema(description = "AI-generated Playwright script")
    String aiScript,
    @Schema(description = "Created at")
    LocalDateTime createdAt
) {}
