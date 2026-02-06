package gdgoc.onewave.connectable.domain.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Assignment Create Request")
public record AssignmentCreateRequest(
    @Schema(description = "Client-generated user UUID (stored in localStorage)", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "User ID is required")
    String userId,

    @Schema(description = "Assignment Title", example = "Frontend React Assignment")
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be less than 255 characters")
    String title,

    @Schema(description = "Assignment Content (Markdown)", example = "# Description\nThis assignment is...")
    @NotBlank(message = "Content is required")
    String content,

    @Schema(description = "Sub tasks for grading", example = "[\"GNB UI\", \"Login validation\"]")
    @NotEmpty(message = "At least one sub-task is required")
    List<@NotBlank(message = "Sub-task cannot be empty") String> subTasks
) {}