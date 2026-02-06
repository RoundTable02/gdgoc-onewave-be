# 통합 테스트 시나리오: Gemini AI Prompt 수정 검증

## 목적
Gemini AI가 생성하는 Playwright 스크립트가 Worker 환경과 호환되는지 검증하고, Assignment 생성부터 Submission 채점까지 전체 플로우가 정상 작동하는지 확인합니다.

## 테스트 환경

### 서버 구성
- **Spring Boot 서버**: localhost:8080 (dev 프로파일)
- **NestJS Worker**: localhost:3000 (로컬 테스트용)
- **Cloud Run Worker**: 운영 Cloud Run URL

### 테스트 데이터
- **Target URL**: https://www.saucedemo.com
- **User ID**: 550e8400-e29b-41d4-a716-446655440000

## 테스트 시나리오

### 1단계: 서버 시작

#### 1-1. Spring Boot 서버 실행
```bash
cd /Users/tak/Desktop/ETC/connectable
./gradlew bootRun --args='--spring.profiles.active=dev'
```

#### 1-2. 서버 상태 확인
```bash
curl http://localhost:8080/health
```

**예상 응답:**
```json
{
  "status": "UP"
}
```

---

### 2단계: Assignment 생성 및 AI 스크립트 검증

#### 2-1. Assignment 생성 API 호출
```bash
curl -X POST http://localhost:8080/api/assignments \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Login Page Test",
    "content": "Test the login functionality of https://www.saucedemo.com. Verify that the login page UI elements are present and that users can successfully log in with valid credentials.",
    "subTasks": [
      "Sub-task 1: Verify login page UI elements",
      "Sub-task 2: Perform a successful login"
    ]
  }' | jq .
```

**예상 응답:**
```json
{
  "data": {
    "id": "fd75465c-4ff4-431f-907c-1fb50743345e",
    "title": "Login Page Test",
    "content": "Test the login functionality of https://www.saucedemo.com...",
    "subTasks": [
      "Sub-task 1: Verify login page UI elements",
      "Sub-task 2: Perform a successful login"
    ],
    "createdAt": "2026-02-07T05:29:38.650666"
  },
  "error": null,
  "success": true
}
```

#### 2-2. 생성된 Assignment 상세 조회 (AI 스크립트 포함)
```bash
ASSIGNMENT_ID="fd75465c-4ff4-431f-907c-1fb50743345e"

curl -X GET "http://localhost:8080/api/assignments/${ASSIGNMENT_ID}" \
  | jq -r '.data.aiScript'
```

**예상 AI 스크립트 (검증 항목):**

```typescript
// Task: Sub-task 1: Verify login page UI elements
test('Sub-task 1: Verify login page UI elements', async ({ page }) => {
  await expect(page.locator('.login_logo')).toBeVisible();
  await expect(page.locator('[data-test="username"]')).toBeVisible();
  await expect(page.locator('[data-test="password"]')).toBeVisible();
  await expect(page.locator('[data-test="login-button"]')).toBeVisible();
  await expect(page.locator('[data-test="login-button"]')).toHaveValue('Login');
});

// Task: Sub-task 2: Perform a successful login
test('Sub-task 2: Perform a successful login', async ({ page }) => {
  await page.locator('[data-test="username"]').fill('standard_user');
  await page.locator('[data-test="password"]').fill('secret_sauce');
  await page.locator('[data-test="login-button"]').click();
  await expect(page).toHaveURL('https://www.saucedemo.com/inventory.html');
  await expect(page.locator('.title')).toHaveText('Products');
  await expect(page.locator('#shopping_cart_container')).toBeVisible();
});
```

#### 2-3. AI 스크립트 검증 체크리스트

- [ ] ✅ 스크립트가 `// Task: [exact sub-task text]` 주석으로 시작
- [ ] ✅ 각 test() 함수 앞에 `// Task:` 주석 존재
- [ ] ✅ Task 주석 내용이 subTasks와 **정확히 일치**
- [ ] ✅ import 문이 **없음** (test, expect, page는 Worker가 제공)
- [ ] ✅ test.describe() 블록이 **없음**
- [ ] ✅ test.beforeEach() 또는 hooks가 **없음**
- [ ] ✅ page.goto() 호출이 **없음**
- [ ] ✅ 각 test() 함수가 독립적으로 실행 가능

---

### 3단계: Submission 생성 및 채점

#### 3-1. Submission 생성 API 호출
```bash
ASSIGNMENT_ID="fd75465c-4ff4-431f-907c-1fb50743345e"

curl -X POST "http://localhost:8080/api/assignments/${ASSIGNMENT_ID}/submissions" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "url": "https://www.saucedemo.com"
  }' | jq .
```

**예상 응답:**
```json
{
  "data": {
    "id": "6288fb45-cb32-4cdb-958d-afd9c23c2ca9",
    "fileUrl": "https://www.saucedemo.com",
    "status": "FAILED",
    "gradingResults": [
      {
        "taskName": "Sub-task 1: Verify login page UI elements",
        "isPassed": false
      },
      {
        "taskName": "Sub-task 2: Perform a successful login",
        "isPassed": false
      }
    ],
    "summary": {
      "passedCount": 0,
      "totalCount": 2,
      "passRate": "0%"
    },
    "createdAt": "2026-02-07T05:33:21.835248"
  },
  "error": null,
  "success": true
}
```

#### 3-2. 채점 결과 검증 체크리스트

- [ ] ✅ API 응답이 성공 (success: true)
- [ ] ✅ gradingResults 배열이 존재
- [ ] ✅ gradingResults의 taskName이 subTasks와 **정확히 일치**
- [ ] ✅ 각 sub-task에 대한 개별 결과 존재 (isPassed: true/false)
- [ ] ✅ summary에 passedCount, totalCount, passRate 포함

---

### 4단계: 완전한 통합 테스트 (원스텝 실행)

#### 4-1. Assignment 생성 → Submission → 결과 확인 (자동화 스크립트)

```bash
#!/bin/bash

# Step 1: Assignment 생성
echo "=== Step 1: Creating Assignment ==="
ASSIGNMENT_ID=$(curl -X POST http://localhost:8080/api/assignments \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Login Test Integration",
    "content": "Test https://www.saucedemo.com login page",
    "subTasks": [
      "Task 1: Check page title",
      "Task 2: Submit login form"
    ]
  }' 2>/dev/null | jq -r '.data.id')

echo "Created Assignment ID: $ASSIGNMENT_ID"
echo ""

# Step 2: AI 스크립트 확인
echo "=== Step 2: Checking Generated AI Script ==="
curl -X GET "http://localhost:8080/api/assignments/${ASSIGNMENT_ID}" 2>/dev/null \
  | jq -r '.data.aiScript'
echo ""

# Step 3: Submission 생성 및 채점
echo "=== Step 3: Submitting and Grading ==="
curl -X POST "http://localhost:8080/api/assignments/${ASSIGNMENT_ID}/submissions" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "url": "https://www.saucedemo.com"
  }' 2>/dev/null | jq '.data.gradingResults'
```

**예상 결과:**
```json
[
  {
    "taskName": "Task 1: Check page title",
    "isPassed": true/false
  },
  {
    "taskName": "Task 2: Submit login form",
    "isPassed": true/false
  }
]
```

---

## 검증 포인트

### A. Gemini AI 프롬프트 수정 검증
1. **Worker 환경 제약사항 준수**
   - ✅ 생성된 스크립트에 import 문 없음
   - ✅ test.describe() 블록 없음
   - ✅ test.beforeEach() 훅 없음
   - ✅ page.goto() 호출 없음

2. **Task 주석 포맷**
   - ✅ 각 test() 함수 앞에 `// Task: [sub-task]` 주석 존재
   - ✅ 주석 내용이 subTasks 배열의 값과 정확히 일치

3. **코드 품질**
   - ✅ 적절한 selectors 사용 (data-testid, id, CSS selectors)
   - ✅ Playwright assertions 사용 (expect().toBeVisible(), toHaveURL() 등)
   - ✅ 각 test가 atomic하고 독립적

### B. 통합 플로우 검증
1. **Assignment Service**
   - ✅ GeminiService.generatePlaywrightScript() 호출
   - ✅ AI 스크립트가 Assignment 엔티티에 저장

2. **Worker 통신**
   - ✅ Spring Boot → Worker로 요청 전송
   - ✅ Worker가 스크립트 파싱 및 실행
   - ✅ 각 sub-task별 결과 반환

3. **결과 매핑**
   - ✅ Worker 응답의 taskName이 원본 subTasks와 일치
   - ✅ Submission 엔티티에 결과 저장
   - ✅ 클라이언트에게 정확한 결과 반환

---

## 알려진 이슈

### Issue #1: NestJS Worker DTO Validation 오류
**증상:**
```
BadRequestException: Bad Request Exception
at ValidationPipe.exceptionFactory
```

**원인:**
Spring Boot가 `subTasks` 필드를 포함해서 요청을 보내는데, NestJS의 `GradingRequestDto`에 해당 필드가 없어서 ValidationPipe가 거부함.

**해결 방법:**
NestJS Worker의 `grading-request.dto.ts`에 `subTasks` 필드 추가:
```typescript
@IsOptional()
@IsArray()
@IsString({ each: true })
subTasks?: string[];
```

### Issue #2: Cloud Run vs Local Worker
- **Cloud Run Worker**: application-dev.yml에 설정된 원격 워커 (정상 작동)
- **Localhost:3000 Worker**: DTO validation 오류로 실패

현재 통합 테스트는 **Cloud Run Worker**를 사용해서 성공했으며, Localhost Worker는 DTO 수정 후 재테스트 필요.

---

## 테스트 완료 기준

### ✅ 전체 테스트 통과 조건
1. Assignment 생성 시 AI 스크립트가 자동 생성됨
2. 생성된 스크립트가 Worker 환경 제약사항을 모두 준수
3. Submission API 호출 시 채점이 정상적으로 수행됨
4. 채점 결과의 taskName이 subTasks와 정확히 매핑됨
5. 클라이언트가 각 sub-task별 성공/실패 여부를 확인 가능

### 📊 테스트 실행 결과 (2026-02-07)
- ✅ Assignment 생성: **성공**
- ✅ AI 스크립트 생성: **성공**
- ✅ Worker 호환성: **성공** (Cloud Run)
- ✅ Task 이름 매핑: **성공**
- ✅ 통합 플로우: **성공**
- ⚠️ Localhost Worker: **DTO 수정 필요**

---

## 다음 단계

1. **NestJS Worker DTO 수정**
   - `GradingRequestDto`에 `subTasks` 필드 추가
   - Localhost:3000에서 재테스트

2. **추가 테스트 케이스**
   - 다양한 sub-task 개수 테스트 (1개, 3개, 5개)
   - 복잡한 Playwright 시나리오 테스트
   - 실패 시나리오 테스트 (잘못된 URL, timeout 등)

3. **문서화**
   - API 문서 업데이트 (Swagger)
   - 개발자 가이드 작성
