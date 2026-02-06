package gdgoc.onewave.connectable.domain.assignment.controller;

import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentCreateRequest;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentResponse;
import gdgoc.onewave.connectable.domain.submission.dto.SubmissionResponse;
import gdgoc.onewave.connectable.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Assignment", description = "Assignment Management API")
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    @Operation(summary = "Create Assignment", description = "Creates a new assignment and generates grading script via AI.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssignmentResponse> createAssignment(@Valid @RequestBody AssignmentCreateRequest request) {
        return ApiResponse.success(null);
    }

    @Operation(summary = "Get Assignments", description = "Lists created assignments.")
    @GetMapping
    public ApiResponse<List<AssignmentResponse>> getAssignments(
            @Parameter(description = "Page number (starts from 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(List.of());
    }

    @Operation(summary = "Get Assignment Detail", description = "Gets details of a specific assignment.")
    @GetMapping("/{id}")
    public ApiResponse<AssignmentResponse> getAssignment(@PathVariable UUID id) {
        return ApiResponse.success(null);
    }

    @Operation(summary = "Get Assignment Results", description = "Gets all submission results for a specific assignment.")
    @GetMapping("/{id}/results")
    public ApiResponse<List<SubmissionResponse>> getAssignmentResults(@PathVariable UUID id) {
        return ApiResponse.success(List.of());
    }
}