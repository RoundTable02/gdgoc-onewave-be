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
            String playwrightScript
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
            String rawResponseBody = webClient.post()
                    .uri(endpoint)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("Failed to call grading worker: {}", e.getMessage(), e);
                        return Mono.error(new BusinessException(ErrorCode.GRADING_TRIGGER_FAILED));
                    })
                    .block();

            log.info("==== Raw Response Body ====");
            log.info("Response length: {} characters", rawResponseBody != null ? rawResponseBody.length() : 0);
            log.info("Response body:\n{}", rawResponseBody);
            log.info("===========================");

            if (rawResponseBody == null || rawResponseBody.trim().isEmpty()) {
                log.error("Grading worker returned null or empty response");
                throw new BusinessException(ErrorCode.GRADING_TRIGGER_FAILED);
            }

            // Parse the JSON response
            GradingResponse response;
            try {
                response = objectMapper.readValue(rawResponseBody, GradingResponse.class);
            } catch (Exception e) {
                log.error("Failed to parse response as JSON: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.GRADING_TRIGGER_FAILED);
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

            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during grading worker call", e);
            throw new BusinessException(ErrorCode.GRADING_TRIGGER_FAILED);
        }
    }
}
