package gdgoc.onewave.connectable.domain.submission.controller;

import gdgoc.onewave.connectable.domain.submission.dto.SubmissionRequest;
import gdgoc.onewave.connectable.domain.submission.dto.SubmissionResponse;
import gdgoc.onewave.connectable.domain.submission.service.SubmissionService;
import gdgoc.onewave.connectable.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Submission", description = "Submission and Grading API")
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(
        summary = "Submit and Grade",
        description = "Submits a deployed project URL and performs immediate grading."
    )
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(
            name = "id",
            description = "Assignment ID",
            in = ParameterIn.PATH,
            required = true,
            schema = @Schema(type = "string", format = "uuid")
        )
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Submission graded successfully",
            content = @Content(schema = @Schema(implementation = SubmissionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid URL format or missing required fields",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Assignment not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Grading process failed",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping("/{id}/submissions")
    public ApiResponse<SubmissionResponse> submitAssignment(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionRequest request
    ) {
        return ApiResponse.success(submissionService.submit(id, request));
    }
}
