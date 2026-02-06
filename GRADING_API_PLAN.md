# 채점 워커 연동 구현 계획

## Context

현재 `POST /api/assignments/{id}/submissions` API는 zip 파일 업로드 및 GCS 정적 호스팅 URL 생성까지 완료된 상태이다. 채점 워커(Cloud Run NestJS) 호출과 결과 저장 로직이 빠져 있어, mock 응답(빈 gradingResults, 0%)을 반환하고 있다. 이를 실제 워커 호출로 교체하여 API를 완성한다.

## 구현 항목

### 1. GradingWorkerClient 생성
**파일:** `src/main/java/gdgoc/onewave/connectable/infrastructure/worker/GradingWorkerClient.java` (신규)

- 기존 `GeminiService`와 동일한 패턴으로 WebClient Bean 주입
- `@Value("${worker.url}")`, `@Value("${worker.timeout-seconds:60}")` 사용
- record 3개 정의: `GradingRequest(submissionId, targetUrl, playwrightScript)`, `GradingResponse(submissionId, success, results, errorMessage)`, `GradingResultItem(taskName, isPassed)`
- `grade(GradingRequest)` 메서드: POST `{workerUrl}/grade` 호출, `Duration.ofSeconds(timeoutSeconds)` 타임아웃 적용
- 실패 시 `BusinessException(ErrorCode.GRADING_TRIGGER_FAILED)` throw

### 2. SubmissionService 수정
**파일:** `src/main/java/gdgoc/onewave/connectable/domain/submission/service/SubmissionService.java` (수정)

현재 흐름:
1. 파일 검증 → 2. Assignment 조회 → 3. Submission 저장(COMPLETED) → 4. GCS 업로드 → 5. fileUrl 갱신 → 6. mock 응답 반환

변경 후 흐름:
1. 파일 검증
2. Assignment 조회
3. Submission 임시 저장 (fileUrl="" 상태, status는 나중에 결정)
4. GCS 업로드 → fileUrl 획득
5. **GradingWorkerClient.grade() 호출** (targetUrl=fileUrl, playwrightScript=assignment.getAiScript())
6. 워커 응답의 `success` 필드로 status 결정 (true→COMPLETED, false→FAILED)
7. Submission fileUrl + status 갱신 후 저장
8. **GradingResult 엔티티 목록 생성 및 일괄 저장** (GradingResultRepository.saveAll)
9. 채점 결과 포함 SubmissionResponse 반환

변경사항:
- `GradingWorkerClient`, `GradingResultRepository` 의존성 추가
- 워커 호출 실패(네트워크 오류, 타임아웃) 시: BusinessException 전파 → Submission 저장 안 됨 (`@Transactional` 롤백)
- Submission 저장 시점 조정: GCS 업로드 전에 save하되 status를 처음부터 설정하지 않고, 워커 응답 이후 최종 status 결정

> **주의:** 현재 Submission 엔티티에 status가 `nullable=false`이므로, 초기 저장 시에도 임시 status가 필요하다. 워커 호출 실패 시 트랜잭션이 롤백되므로 초기 status는 FAILED로 설정하고, 성공 시 COMPLETED로 변경하는 방식 사용.

### 3. 수정 대상 파일 요약

| 파일 | 작업 |
|------|------|
| `infrastructure/worker/GradingWorkerClient.java` | 신규 생성 |
| `domain/submission/service/SubmissionService.java` | 워커 호출 + 결과 저장 로직 추가 |

### 4. 기존 재사용 요소

- `WebClient` Bean: `config/WebClientConfig.java` (90초 타임아웃 설정 완료)
- `GradingResult` 엔티티: `domain/entity/GradingResult.java` (이미 존재)
- `GradingResultRepository`: `domain/grading/repository/GradingResultRepository.java` (이미 존재)
- `GradingResultResponse` DTO: `domain/grading/dto/GradingResultResponse.java` (이미 존재)
- `SubmissionResponse` + `GradingSummary` DTO: 이미 존재
- `ErrorCode.GRADING_TRIGGER_FAILED`: 이미 정의됨
- `application.yml`의 `worker.url`, `worker.timeout-seconds`: 이미 설정됨

## 검증 방법

- 빌드 확인: `./gradlew build` 성공 여부
- Swagger UI에서 제출 API 호출하여 워커 연동 동작 확인 (실제 워커 URL 필요)
- 워커 미가용 시: 의도대로 GRADING_TRIGGER_FAILED 에러 응답 확인