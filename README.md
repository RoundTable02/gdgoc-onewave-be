# Connectable

> AI-Powered Frontend Coding Assessment Platform

**자동화된 프론트엔드 코딩 과제 채점 시스템** - Gemini AI가 Playwright 테스트 스크립트를 생성하고, Cloud Run Worker가 실시간으로 채점합니다.

[![CI/CD](https://github.com/gdgoc-onewave/connectable/actions/workflows/cicd.yml/badge.svg)](https://github.com/gdgoc-onewave/connectable/actions)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Terraform](https://img.shields.io/badge/IaC-Terraform-purple)](https://www.terraform.io/)
[![GCP](https://img.shields.io/badge/Cloud-GCP-blue)](https://cloud.google.com/)

---

## Overview

Connectable은 **구인자와 구직자를 연결하는 프론트엔드 코딩 과제 플랫폼**입니다.

### 핵심 가치

| 사용자 | 기능 |
|--------|------|
| **구인자 (Recruiter)** | 자연어로 과제 요구사항 작성 → AI가 자동으로 채점 스크립트 생성 |
| **구직자 (Candidate)** | 과제 확인 → 프로젝트 배포 → URL 제출 → **즉시** 채점 결과 확인 |

### Why Connectable?

- **No Manual Grading**: AI가 생성한 Playwright 테스트로 객관적이고 일관된 채점
- **Instant Feedback**: 동기식 채점으로 제출 즉시 결과 확인
- **Real Deployment**: 실제 배포된 프로젝트를 테스트하여 실무 역량 검증
- **Scalable**: Cloud Run 기반 서버리스 워커로 자동 스케일링

---

## Architecture

```
┌──────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   Frontend       │────▶│   Spring Boot API   │────▶│   Cloud Run Worker  │
│   (React)        │     │   (Main Server)     │◀────│   (NestJS+Playwright)│
└──────────────────┘     └─────────────────────┘     └─────────────────────┘
                                   │                          │
                                   │                          │ Playwright
                                   ▼                          │ Test Execution
                         ┌─────────────────────┐              │
                         │    Supabase         │              ▼
                         │    (PostgreSQL)     │     ┌─────────────────┐
                         └─────────────────────┘     │   Target URL    │
                                   │                 │   (Candidate's  │
                                   │                 │   Deployed App) │
                                   ▼                 └─────────────────┘
                         ┌─────────────────────┐
                         │    Gemini API       │
                         │ (Playwright Script  │
                         │    Generation)      │
                         └─────────────────────┘
```

### Request Flow

```
1. 구직자가 배포된 프로젝트의 URL 제출
2. Spring Boot가 URL 검증 + Assignment 조회
3. Cloud Run Worker에 채점 요청 (동기 호출)
4. Worker가 Playwright로 실제 웹페이지 테스트
5. 채점 결과를 Spring Boot로 반환
6. DB 저장 + 클라이언트에 즉시 응답
```

---

## Tech Stack

### Backend

| Technology | Purpose |
|------------|---------|
| **Spring Boot 4.0.2** | Main API Server |
| **Java 17** | Language |
| **Spring Data JPA** | ORM |
| **WebFlux/WebClient** | Async HTTP Client |
| **SpringDoc OpenAPI** | API Documentation |

### Infrastructure

| Technology | Purpose |
|------------|---------|
| **Google Cloud Platform** | Cloud Provider |
| **Compute Engine** | API Server Hosting (e2-medium) |
| **Cloud Run** | Serverless Playwright Worker |
| **Cloud Storage (GCS)** | Static File Hosting |
| **Secret Manager** | Secrets Management |
| **Artifact Registry** | Docker Image Registry |

### Database & AI

| Technology | Purpose |
|------------|---------|
| **Supabase (PostgreSQL)** | Database |
| **Google Gemini API** | AI Script Generation |

### DevOps

| Technology | Purpose |
|------------|---------|
| **Terraform** | Infrastructure as Code |
| **GitHub Actions** | CI/CD Pipeline |
| **Docker** | Containerization |

---

## Key Features

### 1. AI-Powered Test Script Generation

구인자가 자연어로 작성한 요구사항을 Gemini AI가 분석하여 Playwright 테스트 스크립트를 자동 생성합니다.

```json
// 요청
{
  "title": "Login Page Test",
  "content": "React로 로그인 페이지를 구현하세요",
  "subTasks": [
    "로그인 폼이 표시되어야 함",
    "유효한 자격증명으로 로그인 성공"
  ]
}

// AI가 생성한 Playwright 스크립트
test('로그인 폼이 표시되어야 함', async ({ page }) => {
  await expect(page.locator('[data-test="username"]')).toBeVisible();
  await expect(page.locator('[data-test="password"]')).toBeVisible();
  await expect(page.locator('[data-test="login-button"]')).toBeVisible();
});
```

### 2. Synchronous Grading

제출과 동시에 채점이 시작되고, 결과가 즉시 반환됩니다. 비동기 폴링이나 웹소켓 없이 단일 API 호출로 완료됩니다.

```json
// 응답 (채점 결과 포함)
{
  "status": "COMPLETED",
  "gradingResults": [
    { "taskName": "로그인 폼이 표시되어야 함", "isPassed": true },
    { "taskName": "유효한 자격증명으로 로그인 성공", "isPassed": true }
  ],
  "summary": {
    "passedCount": 2,
    "totalCount": 2,
    "passRate": "100%"
  }
}
```

### 3. URL-Based Submission

구직자는 자신의 프로젝트를 배포(Vercel, Netlify, GCS 등)한 후 URL만 제출합니다. 실제 배포된 환경에서 테스트가 진행되므로 실무 역량을 정확히 검증합니다.

### 4. Infrastructure as Code

Terraform으로 전체 인프라를 코드로 관리합니다. 해커톤 예산 최적화 설계 (< $10/day).

---

## API Endpoints

### Assignments

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/assignments` | 과제 생성 (AI 스크립트 자동 생성) |
| `GET` | `/api/assignments` | 과제 목록 조회 (페이지네이션) |
| `GET` | `/api/assignments/{id}` | 과제 상세 조회 |

### Submissions

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/assignments/{id}/submissions` | URL 제출 및 채점 (동기식) |
| `GET` | `/api/assignments/{id}/results` | 채점 결과 목록 조회 |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | 서버 상태 확인 |

> API 문서: `/swagger-ui.html` (SpringDoc OpenAPI)

---

## Database Schema

```
┌─────────────────────┐
│     Assignment      │
├─────────────────────┤
│ id (PK, UUID)       │
│ user_id (UUID)      │
│ title (VARCHAR)     │
│ content (TEXT)      │
│ sub_tasks (JSONB)   │
│ ai_script (TEXT)    │
│ created_at          │
│ updated_at          │
└─────────────────────┘
          │
          │ 1:N
          ▼
┌─────────────────────┐
│     Submission      │
├─────────────────────┤
│ id (PK, UUID)       │
│ user_id (UUID)      │
│ assignment_id (FK)  │
│ file_url (TEXT)     │
│ status (ENUM)       │
│ created_at          │
└─────────────────────┘
          │
          │ 1:N
          ▼
┌─────────────────────┐
│   GradingResult     │
├─────────────────────┤
│ id (PK, UUID)       │
│ submission_id (FK)  │
│ task_name (VARCHAR) │
│ is_passed (BOOLEAN) │
│ created_at          │
└─────────────────────┘
```

---

## Infrastructure (Terraform)

### Provisioned Resources

| Module | Resource | Description |
|--------|----------|-------------|
| `compute-engine` | GCE VM (e2-medium) | Spring Boot API 호스팅 |
| `cloud-run-worker` | Cloud Run Service | NestJS + Playwright 워커 |
| `gcs` | Cloud Storage Bucket | 정적 파일 호스팅 |
| `secrets` | Secret Manager | 민감 정보 관리 |
| `iam` | Service Accounts | 권한 관리 |

### Cost Optimization (Hackathon)

- **Compute Engine**: e2-medium (~$0.82/day)
- **Cloud Run**: min_instances=0 (scale to zero)
- **GCS**: STANDARD class, 7-day lifecycle
- **Total Budget**: < $10/day

### Terraform Usage

```bash
cd terraform

# Initialize
terraform init

# Plan (preview changes)
terraform plan -var-file="terraform.tfvars"

# Apply
terraform apply -var-file="terraform.tfvars"
```

---

## Getting Started

### Prerequisites

- Java 17+
- Docker
- GCP Account (with enabled APIs)
- Supabase Project

### Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/gdgoc-onewave/connectable.git
   cd connectable
   ```

2. **Set up environment variables**
   ```bash
   # Create application-dev.yml or set environment variables
   export SUPABASE_HOST=db.xxxxx.supabase.co
   export SUPABASE_PASSWORD=your-password
   export GEMINI_API_KEY=your-api-key
   export WORKER_URL=https://your-worker.run.app
   ```

3. **Run the application**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

4. **Verify**
   ```bash
   curl http://localhost:8080/health
   # {"status":"UP"}
   ```

### Docker Build

```bash
# Build JAR
./gradlew clean bootJar

# Build Docker image
docker build --build-arg JAR_FILE=build/libs/*.jar -t connectable-api .

# Run container
docker run -p 8080:8080 \
  -e SUPABASE_HOST=... \
  -e SUPABASE_PASSWORD=... \
  -e GEMINI_API_KEY=... \
  connectable-api
```

---

## CI/CD Pipeline

GitHub Actions가 자동화된 CI/CD 파이프라인을 제공합니다.

### Workflow

```
Push to main
    │
    ├── Build & Test (Gradle)
    │
    ├── Docker Build & Push
    │       │
    │       └── Artifact Registry
    │
    └── [Manual] Deploy to VM
            │
            └── systemctl restart connectable-api
```

### Required Secrets

| Secret | Description |
|--------|-------------|
| `GCP_PROJECT_ID` | GCP 프로젝트 ID |
| `GCP_SA_KEY` | 서비스 계정 키 (JSON) |
| `SUPABASE_HOST` | Supabase 호스트 |
| `SUPABASE_PASSWORD` | Supabase 비밀번호 |
| `GEMINI_API_KEY` | Gemini API 키 |
| `GCS_BUCKET_NAME` | GCS 버킷 이름 |

---

## Project Structure

```
connectable/
├── src/
│   └── main/java/gdgoc/onewave/connectable/
│       ├── config/                    # Configuration classes
│       │   ├── WebClientConfig.java
│       │   ├── GcsConfig.java
│       │   ├── CorsConfig.java
│       │   └── OpenApiConfig.java
│       ├── domain/
│       │   ├── assignment/            # Assignment domain
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── repository/
│       │   │   └── dto/
│       │   ├── submission/            # Submission domain
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── repository/
│       │   │   └── dto/
│       │   ├── grading/               # Grading results
│       │   │   ├── repository/
│       │   │   └── dto/
│       │   └── entity/                # JPA Entities
│       ├── infrastructure/
│       │   ├── ai/
│       │   │   └── GeminiService.java     # AI script generation
│       │   ├── storage/
│       │   │   └── GcsStorageService.java # GCS file upload
│       │   └── worker/
│       │       └── GradingWorkerClient.java # Cloud Run worker client
│       └── global/
│           ├── exception/             # Exception handling
│           ├── response/              # API response wrapper
│           └── health/                # Health check endpoint
├── terraform/                         # Infrastructure as Code
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── modules/
│       ├── compute-engine/
│       ├── cloud-run-worker/
│       ├── gcs/
│       ├── secrets/
│       └── iam/
├── .github/workflows/
│   └── cicd.yml                       # CI/CD pipeline
├── Dockerfile
├── build.gradle
└── docs/
    ├── DEPLOYMENT.md                  # Deployment guide
    └── plan/                          # API design docs
```

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SUPABASE_HOST` | Supabase PostgreSQL 호스트 | `db.xxx.supabase.co` |
| `SUPABASE_DB` | 데이터베이스 이름 | `postgres` |
| `SUPABASE_USER` | 데이터베이스 사용자 | `postgres` |
| `SUPABASE_PASSWORD` | 데이터베이스 비밀번호 | `***` |
| `GEMINI_API_KEY` | Gemini API 키 | `AIzaSy...` |
| `GEMINI_MODEL` | Gemini 모델명 | `gemini-2.5-pro` |
| `GCS_BUCKET_NAME` | GCS 버킷 이름 | `connectable-submissions` |
| `WORKER_URL` | Cloud Run Worker URL | `https://worker.run.app` |
| `WORKER_TIMEOUT_SECONDS` | 워커 타임아웃 (초) | `60` |

---

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Test

전체 플로우 테스트 (Assignment 생성 → Submission → 채점):

```bash
# 1. 서버 시작
./gradlew bootRun --args='--spring.profiles.active=dev'

# 2. Assignment 생성
curl -X POST http://localhost:8080/api/assignments \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Login Test",
    "content": "Test login page",
    "subTasks": ["Verify form elements", "Test login"]
  }'

# 3. Submission (채점)
curl -X POST http://localhost:8080/api/assignments/{id}/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "url": "https://www.saucedemo.com"
  }'
```

자세한 테스트 시나리오는 [INTEGRATION_TEST_SCENARIO.md](./INTEGRATION_TEST_SCENARIO.md) 참조.

---

## Documentation

| Document | Description |
|----------|-------------|
| [SPECIFICATION.md](./SPECIFICATION.md) | 상세 기술 명세서 |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | 요구사항 정의 |
| [DEPLOYMENT.md](./docs/DEPLOYMENT.md) | 배포 가이드 |
| [INTEGRATION_TEST_SCENARIO.md](./INTEGRATION_TEST_SCENARIO.md) | 통합 테스트 시나리오 |
| [.github/workflows/README.md](./.github/workflows/README.md) | CI/CD 설정 가이드 |

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is developed for **GDG on Campus OneWave Hackathon**.

---

## Team

**GDG on Campus OneWave**

Built with Spring Boot, Gemini AI, and Playwright.
