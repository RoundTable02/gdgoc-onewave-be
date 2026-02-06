package gdgoc.onewave.connectable.domain.assignment.service;

import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentCreateRequest;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentListDataResponse;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentListResponse;
import gdgoc.onewave.connectable.domain.assignment.dto.AssignmentResponse;
import gdgoc.onewave.connectable.domain.assignment.repository.AssignmentRepository;
import gdgoc.onewave.connectable.domain.entity.Assignment;
import gdgoc.onewave.connectable.global.exception.BusinessException;
import gdgoc.onewave.connectable.global.exception.ErrorCode;
import gdgoc.onewave.connectable.infrastructure.ai.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final GeminiService geminiService;

    @Transactional
    public AssignmentResponse create(AssignmentCreateRequest request) {
        // 1. Parse userId
        UUID userId;
        try {
            userId = UUID.fromString(request.userId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format: {}", request.userId());
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 2. Generate Playwright script via AI
        String aiScript = geminiService.generatePlaywrightScript(
                request.subTasks(),
                request.content()
        );

        // 3. Build and save Assignment entity
        Assignment assignment = Assignment.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .subTasks(request.subTasks())
                .aiScript(aiScript)
                .build();

        assignment = assignmentRepository.save(assignment);

        log.info("Assignment created successfully: id={}, title={}", assignment.getId(), assignment.getTitle());

        // 4. Return response DTO
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getContent(),
                assignment.getSubTasks(),
                assignment.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AssignmentListDataResponse getAssignments(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Assignment> assignmentPage = assignmentRepository.findAll(pageRequest);

        List<AssignmentListResponse> content = assignmentPage.getContent().stream()
                .map(a -> new AssignmentListResponse(
                        a.getId(),
                        a.getTitle(),
                        truncateContent(a.getContent(), 200),
                        a.getCreatedAt()
                ))
                .toList();

        return new AssignmentListDataResponse(
                content,
                assignmentPage.getNumber(),
                assignmentPage.getSize(),
                assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(UUID id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getContent(),
                assignment.getSubTasks(),
                assignment.getCreatedAt()
        );
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
