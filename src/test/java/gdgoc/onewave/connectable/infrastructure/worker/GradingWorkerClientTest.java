package gdgoc.onewave.connectable.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GradingWorkerClientTest {

    private MockWebServer mockWebServer;
    private GradingWorkerClient gradingWorkerClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();
        WebClient webClient = WebClient.builder().build();

        gradingWorkerClient = new GradingWorkerClient(webClient, objectMapper);

        // Use reflection to set the workerUrl field
        try {
            var field = GradingWorkerClient.class.getDeclaredField("workerUrl");
            field.setAccessible(true);
            field.set(gradingWorkerClient, mockWebServer.url("/").toString().replaceAll("/$", ""));

            var timeoutField = GradingWorkerClient.class.getDeclaredField("timeoutSeconds");
            timeoutField.setAccessible(true);
            timeoutField.set(gradingWorkerClient, 5);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void grade_withSuccessfulResponse_shouldReturnGradingResponse() throws Exception {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Task 1", "Task 2");

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        String responseJson = """
                {
                    "submissionId": "%s",
                    "success": true,
                    "results": [
                        {"taskName": "Task 1", "isPassed": true},
                        {"taskName": "Task 2", "isPassed": true}
                    ],
                    "errorMessage": null
                }
                """.formatted(submissionId);

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isTrue();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).taskName()).isEqualTo("Task 1");
        assertThat(response.results().get(0).isPassed()).isTrue();
    }

    @Test
    void grade_withConnectionRefused_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Task 1", "Task 2");

        // Shutdown server to simulate connection refused
        mockWebServer.shutdown();

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).taskName()).isEqualTo("Task 1");
        assertThat(response.results().get(0).isPassed()).isFalse();
        assertThat(response.results().get(1).taskName()).isEqualTo("Task 2");
        assertThat(response.results().get(1).isPassed()).isFalse();
        assertThat(response.errorMessage()).contains("Network error");

        // Restart server for next tests
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Update workerUrl for the restarted server
        var field = GradingWorkerClient.class.getDeclaredField("workerUrl");
        field.setAccessible(true);
        field.set(gradingWorkerClient, mockWebServer.url("/").toString().replaceAll("/$", ""));
    }

    @Test
    void grade_withInvalidJson_shouldReturnFailureResponse() {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Login Test");

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody("Invalid JSON {[}]")
                .addHeader("Content-Type", "application/json"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).taskName()).isEqualTo("Login Test");
        assertThat(response.results().get(0).isPassed()).isFalse();
        assertThat(response.errorMessage()).isEqualTo("Invalid JSON response");
    }

    @Test
    void grade_withEmptyResponse_shouldReturnFailureResponse() {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Task 1", "Task 2", "Task 3");

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody("")
                .addHeader("Content-Type", "application/json"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(3);
        assertThat(response.results()).allMatch(r -> !r.isPassed());
        assertThat(response.errorMessage()).isEqualTo("Empty response from grading worker");
    }

    @Test
    void grade_withNoSubTasks_shouldReturnDefaultTask() {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of(); // Empty subTasks

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody("")
                .addHeader("Content-Type", "application/json"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).taskName()).isEqualTo("Grading Evaluation");
        assertThat(response.results().get(0).isPassed()).isFalse();
    }

    @Test
    void grade_withEmptyResults_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Task 1", "Task 2", "Task 3");

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        // Response with empty results array
        String responseJson = """
                {
                    "submissionId": "%s",
                    "success": false,
                    "results": [],
                    "errorMessage": "Grading failed"
                }
                """.formatted(submissionId);

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(3);
        assertThat(response.results()).allMatch(r -> !r.isPassed());
        assertThat(response.results().get(0).taskName()).isEqualTo("Task 1");
        assertThat(response.results().get(1).taskName()).isEqualTo("Task 2");
        assertThat(response.results().get(2).taskName()).isEqualTo("Task 3");
    }

    @Test
    void grade_withNullResults_shouldReturnFailureResponse() throws Exception {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Login", "Submit Form");

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        // Response with null results
        String responseJson = """
                {
                    "submissionId": "%s",
                    "success": false,
                    "results": null,
                    "errorMessage": "Worker error"
                }
                """.formatted(submissionId);

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).allMatch(r -> !r.isPassed());
        assertThat(response.results().get(0).taskName()).isEqualTo("Login");
        assertThat(response.results().get(1).taskName()).isEqualTo("Submit Form");
    }

    @Test
    void grade_withServerError_shouldReturnFailureResponse() {
        // Given
        UUID submissionId = UUID.randomUUID();
        String script = "await page.click('button');";
        List<String> subTasks = List.of("Error Test");

        GradingWorkerClient.GradingRequest request = new GradingWorkerClient.GradingRequest(
                submissionId,
                "https://example.com",
                script,
                subTasks
        );

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // When
        GradingWorkerClient.GradingResponse response = gradingWorkerClient.grade(request);

        // Then
        assertThat(response.submissionId()).isEqualTo(submissionId);
        assertThat(response.success()).isFalse();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).taskName()).isEqualTo("Error Test");
        assertThat(response.results().get(0).isPassed()).isFalse();
        assertThat(response.errorMessage()).contains("Network error");
    }
}
