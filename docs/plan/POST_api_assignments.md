# POST /api/assignments API 개발 계획서

> 과제 생성 API 구현을 위한 상세 기술 계획

---

## 1. 개요

### 1.1 API 목적
구인자가 프론트엔드 구현 과제를 생성하고, AI(Gemini)가 Playwright 채점 스크립트를 자동 생성하는 엔드포인트

### 1.2 현재 상태 분석

| 구성 요소 | 상태 | 위치 |
|---------|------|------|
| Controller | ✅ Stub 존재 | `domain/assignment/controller/AssignmentController.java` |
| AssignmentService | ❌ 미구현 | `domain/assignment/service/` (생성 필요) |
| GeminiService | ❌ 미구현 | `infrastructure/ai/` (생성 필요) |
| WebClientConfig | ❌ 미구현 | `config/` (생성 필요) |
| AssignmentCreateRequest | ✅ 완료 | `domain/assignment/dto/AssignmentCreateRequest.java` |
| AssignmentResponse | ✅ 완료 | `domain/assignment/dto/AssignmentResponse.java` |
| Assignment Entity | ✅ 완료 | `domain/entity/Assignment.java` |
| AssignmentRepository | ✅ 완료 | `domain/assignment/repository/AssignmentRepository.java` |
| ErrorCode (AI_GENERATION_FAILED) | ✅ 완료 | `global/exception/ErrorCode.java` |

---

## 2. API 스펙

### 2.1 Request
```yaml
Method: POST
Path: /api/assignments
Content-Type: application/json

Body:
  userId: string (required)     # 클라이언트 localStorage UUID
  title: string (required)      # max 255자
  content: string (required)    # Markdown 형식
  subTasks: string[] (required) # 최소 1개 이상
```

### 2.2 Response

**성공 (201 Created)**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Frontend React Assignment",
    "content": "# Description\nThis assignment is...",
    "subTasks": ["GNB UI", "Login validation"],
    "createdAt": "2026-02-07T01:30:00"
  },
  "error": null
}
```

**실패 (400 Bad Request - Validation)**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "title: Title is required"
  }
}
```

**실패 (500 Internal Server Error - AI 생성 실패)**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "A002",
    "message": "Failed to generate AI script"
  }
}
```

---

## 3. 비즈니스 로직 흐름

```
┌──────────────────────────────────────────────────────────────────┐
│                    POST /api/assignments                         │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 1. Controller: 요청 수신 및 유효성 검증 (@Valid)                    │
│    - AssignmentCreateRequest DTO 바인딩                          │
│    - jakarta.validation 자동 검증                                 │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 2. Service: userId 파싱                                          │
│    - UUID.fromString(request.userId())                          │
│    - 잘못된 형식 시 IllegalArgumentException → 400 반환            │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 3. GeminiService: Playwright 스크립트 생성                        │
│    - WebClient로 Gemini API 호출                                 │
│    - subTasks + content 기반 프롬프트 구성                         │
│    - 실패 시 BusinessException(AI_GENERATION_FAILED) throw        │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 4. Service: Assignment 엔티티 생성 및 저장                         │
│    - Assignment.builder()로 엔티티 구성                            │
│    - subTasks는 List<String> → JSONB 자동 매핑                    │
│    - assignmentRepository.save()                                 │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 5. Controller: 응답 반환                                          │
│    - AssignmentResponse DTO 생성                                 │
│    - ApiResponse.success(response) 반환                          │
│    - HTTP 201 Created                                            │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. 구현 상세

### 4.1 신규 생성 파일

#### 4.1.1 WebClientConfig.java
```
경로: src/main/java/gdgoc/onewave/connectable/config/WebClientConfig.java
```

**책임:**
- WebClient 빈 설정
- Gemini API 호출을 위한 타임아웃 설정 (90초)
- ReactorClientHttpConnector 구성

**주요 설정:**
| 설정 | 값 | 이유 |
|-----|---|------|
| Connection Timeout | 10초 | 연결 실패 빠른 감지 |
| Response Timeout | 90초 | AI 생성 대기 |
| Max In-Memory Size | 10MB | 대용량 스크립트 응답 처리 |

#### 4.1.2 GeminiService.java
```
경로: src/main/java/gdgoc/onewave/connectable/infrastructure/ai/GeminiService.java
```

**책임:**
- Gemini API 호출
- 프롬프트 구성
- 응답 파싱 및 스크립트 추출

**메서드:**
```java
public String generatePlaywrightScript(List<String> subTasks, String assignmentContent)
```

**응답 후처리 (중요):**
Gemini API는 마크다운 코드 블록으로 감싸서 응답합니다:
```
```typescript
import { test, expect } from '@playwright/test';
...
```　
```

Worker 실행을 위해 순수 TypeScript 코드만 추출해야 합니다:
```java
private String extractCodeFromMarkdown(String response) {
    // ```typescript 또는 ```ts 로 시작하는 코드 블록 추출
    String pattern = "```(?:typescript|ts)?\\s*\\n([\\s\\S]*?)```";
    Pattern regex = Pattern.compile(pattern);
    Matcher matcher = regex.matcher(response);
    
    if (matcher.find()) {
        return matcher.group(1).trim();
    }
    
    // 코드 블록이 없으면 원본 반환 (이미 순수 코드인 경우)
    return response.trim();
}
```

**프롬프트 구성 요소:**
1. 역할 정의: "You are an expert Playwright test script generator"
2. 과제 설명: assignmentContent
3. 채점 항목: subTasks (번호 매김)
4. 요구사항:
   - TypeScript + Playwright 사용
   - 각 subTask를 별도 테스트 케이스로
   - test.describe로 그룹화
   - 코드만 출력 (설명 제외)
   - **`{{TARGET_URL}}` 플레이스홀더 사용** (Worker가 실제 URL로 치환)

**프롬프트 예시:**
```
You are an expert Playwright test script generator.

Assignment Description:
{assignmentContent}

Generate a Playwright test script that verifies the following requirements:

1. {subTask1}
2. {subTask2}
...

Requirements:
- Use TypeScript with Playwright
- Each subtask should be a separate test case with descriptive name
- Use test.describe for grouping all tests
- Use `{{TARGET_URL}}` as placeholder for page.goto() - the grading worker will replace it
- Include proper assertions using expect()
- Handle dynamic content with appropriate waitFor strategies
- Output ONLY the raw TypeScript code without any markdown formatting or explanation
- Do NOT wrap the code in ```typescript``` code blocks
```

**참고:** 프롬프트에서 "markdown formatting 없이 출력"을 명시해도 Gemini가 코드 블록으로 감쌀 수 있으므로, 후처리 로직은 필수입니다.

**에러 처리:**
- API 호출 실패 → `BusinessException(ErrorCode.AI_GENERATION_FAILED)`

#### 4.1.3 AssignmentService.java
```
경로: src/main/java/gdgoc/onewave/connectable/domain/assignment/service/AssignmentService.java
```

**책임:**
- 과제 생성 비즈니스 로직
- GeminiService 연동
- 트랜잭션 관리

**메서드:**
```java
@Transactional
public AssignmentResponse create(AssignmentCreateRequest request)
```

**구현 흐름:**
1. userId 파싱 (`UUID.fromString`)
2. GeminiService 호출
3. Assignment 엔티티 빌드
4. Repository 저장
5. AssignmentResponse 반환

### 4.2 수정 파일

#### 4.2.1 AssignmentController.java
```
경로: src/main/java/gdgoc/onewave/connectable/domain/assignment/controller/AssignmentController.java
```

**수정 내용:**
- AssignmentService 주입
- `createAssignment` 메서드 구현부 변경

**현재 (Stub):**
```java
public ApiResponse<AssignmentResponse> createAssignment(
        @Valid @RequestBody AssignmentCreateRequest request) {
    return ApiResponse.success(null);
}
```

**수정 후:**
```java
private final AssignmentService assignmentService;

public ApiResponse<AssignmentResponse> createAssignment(
        @Valid @RequestBody AssignmentCreateRequest request) {
    return ApiResponse.success(assignmentService.create(request));
}
```

---

## 5. 의존성 관계

```
┌─────────────────────────────────────────────────────────────────┐
│                    AssignmentController                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     AssignmentService                           │
└─────────────────────────────────────────────────────────────────┘
                    │                   │
                    ▼                   ▼
┌───────────────────────────┐   ┌─────────────────────────────────┐
│   AssignmentRepository    │   │         GeminiService           │
│   (JpaRepository)         │   │   (infrastructure/ai)           │
└───────────────────────────┘   └─────────────────────────────────┘
                                              │
                                              ▼
                                ┌─────────────────────────────────┐
                                │          WebClient              │
                                │      (WebClientConfig)          │
                                └─────────────────────────────────┘
                                              │
                                              ▼
                                ┌─────────────────────────────────┐
                                │       Gemini API (External)     │
                                └─────────────────────────────────┘
```

---

## 6. 환경 설정

### 6.1 application.yml 추가 설정
```yaml
# Gemini API 설정
gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-1.5-pro
```

### 6.2 필요 환경 변수
| 변수명 | 설명 | 예시 |
|--------|------|------|
| GEMINI_API_KEY | Gemini API 키 | AIzaSy... |

---

## 7. 에러 처리 매트릭스

| 상황 | Exception | ErrorCode | HTTP Status |
|-----|-----------|-----------|-------------|
| 유효성 검증 실패 | MethodArgumentNotValidException | C001 | 400 |
| userId 형식 오류 | IllegalArgumentException | C001 | 400 |
| AI 스크립트 생성 실패 | BusinessException | A002 | 500 |
| 예상치 못한 오류 | Exception | C002 | 500 |

### 7.1 userId 형식 검증 추가 권장
현재 `AssignmentCreateRequest.userId`는 String 타입으로 `@NotBlank`만 검증.
UUID 형식 검증을 위해 Service에서 try-catch 또는 커스텀 Validator 추가 권장:

```java
// Service에서 처리
try {
    UUID parsedUserId = UUID.fromString(request.userId());
} catch (IllegalArgumentException e) {
    throw new BusinessException(ErrorCode.INVALID_REQUEST);
}
```

---

## 8. 구현 순서 (권장)

| 순서 | 작업 | 예상 시간 | 의존성 |
|-----|------|----------|--------|
| 1 | application.yml 설정 추가 | 5분 | - |
| 2 | WebClientConfig.java 생성 | 15분 | - |
| 3 | GeminiService.java 생성 | 30분 | WebClientConfig |
| 4 | AssignmentService.java 생성 | 20분 | GeminiService, Repository |
| 5 | AssignmentController.java 수정 | 10분 | AssignmentService |
| 6 | 통합 테스트 작성 | 30분 | 전체 |

**총 예상 시간: 약 2시간**

---

## 9. 테스트 계획

### 9.1 단위 테스트

| 대상 | 테스트 케이스 |
|-----|-------------|
| GeminiService | 프롬프트 생성 로직, API 응답 파싱, 타임아웃 처리 |
| AssignmentService | 정상 생성, UUID 파싱 실패, AI 실패 시 예외 전파 |

### 9.2 통합 테스트

| 시나리오 | 검증 항목 |
|---------|----------|
| 정상 과제 생성 | 201 반환, DB 저장 확인, aiScript 포함 |
| 유효성 검증 실패 | 400 반환, 에러 메시지 확인 |
| AI 생성 실패 | 500 반환, A002 코드 확인 |

### 9.3 Mock 전략
- **GeminiService**: Gemini API 호출을 Mock하여 테스트
- **WebClient**: `@MockBean` 또는 WireMock 사용

---

## 10. 코드 컨벤션 (기존 코드베이스 기반)

### 10.1 Service 패턴
```java
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxRepository xxxRepository;
    private final ExternalService externalService;

    @Transactional
    public XxxResponse create(XxxRequest request) {
        // 1. 외부 서비스 호출 (실패 시 저장 방지)
        // 2. 엔티티 빌드 및 저장
        // 3. 응답 DTO 반환
    }
}
```

### 10.2 Controller 패턴
```java
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController {
    private final XxxService xxxService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<XxxResponse> create(@Valid @RequestBody XxxRequest request) {
        return ApiResponse.success(xxxService.create(request));
    }
}
```

### 10.3 Exception 처리 패턴
```java
// 도메인 에러
throw new BusinessException(ErrorCode.XXX_NOT_FOUND);

// 외부 API 에러
try {
    // API 호출
} catch (Exception e) {
    log.error("API call failed", e);
    throw new BusinessException(ErrorCode.XXX_FAILED);
}
```

---

## 11. 주의사항

### 11.1 JSONB 처리
- `subTasks`는 `List<String>` 타입
- `@Type(JsonType.class)` 및 `columnDefinition = "jsonb"` 설정 완료
- 별도 JSON 직렬화 불필요

### 11.2 트랜잭션 경계
- AI 스크립트 생성 **후** DB 저장
- AI 실패 시 DB 저장 없음 (의도된 동작)

### 11.3 userId 처리
- 클라이언트에서 String으로 전달
- Service에서 `UUID.fromString()` 변환
- 형식 오류 시 400 반환

### 11.4 aiScript 노출
- `AssignmentResponse`에 `aiScript` 필드 없음 (현재 설계)
- 채점 스크립트는 DB에 저장되지만 클라이언트에 노출 안함
- 필요시 별도 Admin API 또는 응답 DTO 수정 검토

---

## 12. 체크리스트

### 12.1 구현 전
- [ ] GEMINI_API_KEY 환경 변수 확인
- [ ] Gemini API 접근 권한 확인
- [ ] 기존 테스트 통과 확인

### 12.2 구현 중
- [ ] WebClientConfig.java 생성
- [ ] GeminiService.java 생성
- [ ] AssignmentService.java 생성
- [ ] AssignmentController.java 수정
- [ ] application.yml 설정 추가

### 12.3 구현 후
- [ ] 단위 테스트 작성 및 통과
- [ ] 통합 테스트 작성 및 통과
- [ ] Swagger UI에서 API 동작 확인
- [ ] 에러 케이스 수동 테스트
- [ ] 코드 리뷰

---

## 13. 참고 파일

| 파일 | 역할 |
|-----|------|
| `SPECIFICATION.md` | 전체 시스템 명세서 |
| `SubmissionService.java` | Service 패턴 참고 |
| `GcsStorageService.java` | Infrastructure 서비스 패턴 참고 |
| `GlobalExceptionHandler.java` | 예외 처리 패턴 참고 |
| `ApiResponse.java` | 응답 래퍼 사용법 |
| `ErrorCode.java` | 에러 코드 목록 |
