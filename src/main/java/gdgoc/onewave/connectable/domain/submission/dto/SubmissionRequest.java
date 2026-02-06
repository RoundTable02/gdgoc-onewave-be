package gdgoc.onewave.connectable.domain.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Submission Request with URL")
public record SubmissionRequest(
    @Schema(description = "Client-generated user UUID",
            example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "User ID is required")
    String userId,

    @Schema(description = "Direct URL to the hosted submission",
            example = "https://example.com/my-project/index.html")
    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    String url
) {}
