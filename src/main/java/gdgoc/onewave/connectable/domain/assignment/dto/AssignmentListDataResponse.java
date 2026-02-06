package gdgoc.onewave.connectable.domain.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Assignment List Response")
public record AssignmentListDataResponse(
    @Schema(description = "List of assignments")
    List<AssignmentListResponse> data
) {}
