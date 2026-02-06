package gdgoc.onewave.connectable.domain.assignment.controller;

import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentCreateRequest;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentListDataResponse;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentListResponse;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentResponse;
import gdgoc.onewave.connectable.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(
        summary = "Create Assignment",
        description = "Creates a new assignment and generates grading script via AI."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Assignment created successfully",
            content = @Content(schema = @Schema(implementation = AssignmentResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request - validation failed",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "AI script generation failed",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssignmentResponse> createAssignment(
            @Valid @RequestBody AssignmentCreateRequest request) {
        return ApiResponse.success(null);
    }

    @Operation(summary = "Get Assignments", description = "Lists all assignments.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Assignments retrieved successfully",
            content = @Content(schema = @Schema(implementation = AssignmentListDataResponse.class))
        )
    })
    @GetMapping
    public AssignmentListDataResponse getAssignments(
            @Parameter(description = "Page number (starts from 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        return new AssignmentListDataResponse(List.of());
    }

    @Operation(summary = "Get Assignment Detail", description = "Gets details of a specific assignment.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Assignment found",
            content = @Content(schema = @Schema(implementation = AssignmentResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Assignment not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/{id}")
    public ApiResponse<AssignmentResponse> getAssignment(
            @Parameter(description = "Assignment ID", required = true) @PathVariable UUID id) {
        return ApiResponse.success(null);
    }
}
