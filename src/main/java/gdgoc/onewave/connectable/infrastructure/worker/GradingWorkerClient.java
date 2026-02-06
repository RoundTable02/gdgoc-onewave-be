package gdgoc.onewave.connectable.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.onewave.connectable.global.exception.BusinessException;
import gdgoc.onewave.connectable.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradingWorkerClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${worker.url}")
    private String workerUrl;

    @Value("${worker.timeout-seconds:60}")
    private int timeoutSeconds;

    public record GradingRequest(
            UUID submissionId,
            String targetUrl,
            String playwrightScript,
            List<String> subTasks
    ) {}

    public record GradingResponse(
            UUID submissionId,
            Boolean success,
            List<GradingResultItem> results,
            String errorMessage
    ) {}

    public record GradingResultItem(
            String taskName,
            Boolean isPassed
    ) {}

    public GradingResponse grade(GradingRequest request) {
        try {
            String endpoint = workerUrl + "/grade";
            log.info("==== Grading Worker Request ====");
            log.info("Endpoint: {}", endpoint);
            log.info("Submission ID: {}", request.submissionId());
            log.info("Target URL: {}", request.targetUrl());
            log.info("Playwright Script length: {} characters",
                    request.playwrightScript() != null ? request.playwrightScript().length() : 0);
            log.info("Timeout: {} seconds", timeoutSeconds);

            // Get the raw response body as String to log it
            String rawResponseBody;
            try {
                rawResponseBody = webClient.post()
                        .uri(endpoint)
                        .header("Content-Type", "application/json")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .block();
            } catch (Exception e) {
                log.error("Network error during grading: {}", e.getMessage(), e);
                return createFailureResponse(request, "Network error: " + e.getMessage());
            }

            log.info("==== Raw Response Body ====");
            log.info("Response length: {} characters", rawResponseBody != null ? rawResponseBody.length() : 0);
            log.info("Response body:\n{}", rawResponseBody);
            log.info("===========================");

            if (rawResponseBody == null || rawResponseBody.trim().isEmpty()) {
                log.error("Empty response from grading worker");
                return createFailureResponse(request, "Empty response from grading worker");
            }

            // Parse the JSON response
            GradingResponse response;
            try {
                response = objectMapper.readValue(rawResponseBody, GradingResponse.class);
            } catch (Exception e) {
                log.error("Failed to parse grading response: {}", e.getMessage(), e);
                return createFailureResponse(request, "Invalid JSON response");
            }

            log.info("==== Grading Worker Response ====");
            log.info("Submission ID: {}", response.submissionId());
            log.info("Success: {}", response.success());
            log.info("Error Message: {}", response.errorMessage());
            log.info("Results count: {}", response.results() != null ? response.results().size() : 0);

            if (response.results() != null) {
                for (int i = 0; i < response.results().size(); i++) {
                    GradingResultItem item = response.results().get(i);
                    log.info("Result[{}]: taskName='{}', isPassed={}", i, item.taskName(), item.isPassed());
                }
            }
            log.info("================================");

            // Check if results are null or empty - create failure response with task names
            if (response.results() == null || response.results().isEmpty()) {
                log.warn("Grading response has empty results, creating failure response");
                String errorMsg = response.errorMessage() != null ? response.errorMessage() : "No grading results returned";
                return createFailureResponse(request, errorMsg);
            }

            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during grading", e);
            return createFailureResponse(request, e.getMessage());
        }
    }

    /**
     * Creates a fallback GradingResponse when grading fails.
     * Uses the assignment's subTasks and marks all tasks as failed.
     *
     * @param request The original grading request
     * @param errorMessage The error message describing what went wrong
     * @return A GradingResponse with all tasks marked as failed
     */
    private GradingResponse createFailureResponse(GradingRequest request, String errorMessage) {
        log.info("Creating failure response for submission {}", request.submissionId());

        // Use subTasks from the assignment
        List<String> taskNames = request.subTasks();

        // If subTasks is null or empty, use a default task
        if (taskNames == null || taskNames.isEmpty()) {
            log.warn("No subTasks provided, using default task");
            taskNames = List.of("Grading Evaluation");
        }

        // Create failed results for each task
        List<GradingResultItem> failedResults = taskNames.stream()
                .map(taskName -> new GradingResultItem(taskName, false))
                .collect(Collectors.toList());

        log.info("Created {} failed result(s)", failedResults.size());

        return new GradingResponse(
                request.submissionId(),
                false,  // success = false
                failedResults,
                errorMessage != null ? errorMessage : "Grading failed due to unexpected error"
        );
    }
}
