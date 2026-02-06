package gdgoc.onewave.connectable.infrastructure.ai;

import gdgoc.onewave.connectable.global.exception.BusinessException;
import gdgoc.onewave.connectable.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1/models/";

    public String generatePlaywrightScript(List<String> subTasks, String assignmentContent) {
        try {
            String prompt = buildPrompt(subTasks, assignmentContent);

            // Gemini API 요청 구성
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            // API 호출
            String endpoint = GEMINI_API_BASE_URL + model + ":generateContent?key=" + apiKey;

            Map<String, Object> response = webClient.post()
                    .uri(endpoint)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(e -> {
                        log.error("Failed to call Gemini API", e);
                        return Mono.error(new BusinessException(ErrorCode.AI_GENERATION_FAILED));
                    })
                    .block();

            // 응답 파싱
            if (response == null) {
                throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
            }

            return extractScriptFromResponse(response);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during AI script generation", e);
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    private String buildPrompt(List<String> subTasks, String assignmentContent) {
        StringBuilder prompt = new StringBuilder();

        // Introduction and assignment description
        prompt.append("You are an expert Playwright test script generator.\n\n");
        prompt.append("## Assignment Description\n");
        prompt.append(assignmentContent).append("\n\n");

        // Sub-tasks enumeration
        prompt.append("## Grading Criteria (Sub-tasks)\n");
        for (int i = 0; i < subTasks.size(); i++) {
            prompt.append(i + 1).append(". ").append(subTasks.get(i)).append("\n");
        }

        // Worker environment constraints
        prompt.append("\n## CRITICAL: Worker Environment Constraints\n");
        prompt.append("The generated script will run in a worker environment with these constraints:\n");
        prompt.append("- The page is ALREADY LOADED at the target URL before each test runs\n");
        prompt.append("- The worker provides test, expect, and page automatically\n");
        prompt.append("- ONLY individual test() functions are executed (no describe blocks, no beforeEach hooks)\n");
        prompt.append("- Each test runs independently with a fresh page context\n");

        // Requirements section
        prompt.append("\n## Requirements\n");
        prompt.append("- Generate a TypeScript + Playwright test script\n");
        prompt.append("- Create a separate test() function for each sub-task\n");
        prompt.append("- MANDATORY: Each test MUST have a comment in the format: // Task: [exact sub-task text]\n");
        prompt.append("  - This comment must appear immediately before the corresponding test() function\n");
        prompt.append("  - Use the EXACT sub-task text from the \"Grading Criteria\" section\n");
        prompt.append("  - Example:\n");
        prompt.append("    // Task: Sub-task 1: Verify login form elements\n");
        prompt.append("    test('Sub-task 1: Verify login form elements', async ({ page }) => { ... });\n");
        prompt.append("- DO NOT include any import statements (test, expect, page are auto-provided)\n");
        prompt.append("- DO NOT use test.describe() blocks (they are ignored)\n");
        prompt.append("- DO NOT use test.beforeEach() or any hooks (each test runs independently)\n");
        prompt.append("- DO NOT call page.goto() (the page is already at the target URL)\n");
        prompt.append("- Use appropriate selectors (prefer data-testid, id, or specific CSS selectors)\n");
        prompt.append("- Use Playwright assertions (expect(page).toHaveTitle, expect(locator).toBeVisible, etc.)\n");
        prompt.append("- Each test should be atomic and verify the corresponding sub-task requirement\n");

        // Output format requirements
        prompt.append("\n## CRITICAL OUTPUT FORMAT REQUIREMENTS\n");
        prompt.append("- Do NOT use markdown code blocks (no ```typescript or ``` markers)\n");
        prompt.append("- Do NOT add any explanations, comments, or descriptions outside the script\n");
        prompt.append("- Start DIRECTLY with the first // Task: comment\n");
        prompt.append("- Output ONLY the raw TypeScript code that can be saved directly to a .ts file\n\n");

        prompt.append("Generate the complete Playwright test script now:");

        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractScriptFromResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) {
                throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
            }

            String text = (String) parts.get(0).get("text");
            if (text == null || text.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
            }

            return stripMarkdownCodeBlocks(text.trim());

        } catch (ClassCastException | NullPointerException e) {
            log.error("Failed to parse Gemini API response", e);
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    /**
     * Remove markdown code block markers from the response
     */
    private String stripMarkdownCodeBlocks(String text) {
        // Remove ```typescript or ```ts or ``` at the start
        String cleaned = text.replaceFirst("^```(?:typescript|ts)?\\s*\\n?", "");

        // Remove trailing ```
        cleaned = cleaned.replaceFirst("\\n?```\\s*$", "");

        return cleaned.trim();
    }
}
