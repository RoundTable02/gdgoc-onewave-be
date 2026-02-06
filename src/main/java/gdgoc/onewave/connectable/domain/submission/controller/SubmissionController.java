package gdgoc.onewave.connectable.domain.submission.controller;

import gdgoc.onewave.connectable.domain.submission.dto.SubmissionResponse;
import gdgoc.onewave.connectable.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Submission", description = "Submission and Grading API")
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class SubmissionController {

    @Operation(
        summary = "Submit and Grade",
        description = "Submits a Zip file, uploads to GCS, and performs immediate grading."
    )
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(
            name = "X-User-Id",
            description = "Client-generated user UUID (stored in localStorage)",
            in = ParameterIn.HEADER,
            required = true,
            schema = @Schema(type = "string", format = "uuid")
        ),
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
            description = "Invalid file type or size",
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
    @PostMapping(value = "/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SubmissionResponse> submitAssignment(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @Parameter(description = "Zip file to submit (max 50MB)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Candidate Name") @RequestPart("candidateName") String candidateName
    ) {
        return ApiResponse.success(null);
    }
}