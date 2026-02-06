package gdgoc.onewave.connectable.domain.submission.controller;

import gdgoc.onewave.connectable.domain.submission.dto.SubmissionResponse;
import gdgoc.onewave.connectable.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @PostMapping(value = "/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SubmissionResponse> submitAssignment(
            @PathVariable UUID id,
            @Parameter(description = "Zip file to submit (max 50MB)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Candidate Name") @RequestPart("candidateName") String candidateName
    ) {
        return ApiResponse.success(null);
    }
}