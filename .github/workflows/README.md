# GitHub Actions CI/CD 설정 가이드

## 개요

이 프로젝트는 GitHub Actions를 사용하여 다음 CI/CD 파이프라인을 제공합니다:

- **CI**: Gradle 빌드, 테스트, Docker 이미지 빌드 및 Artifact Registry 푸시
- **CD**: Terraform을 통한 GCP 인프라 배포 및 Compute Engine VM 롤링 업데이트

## 필수 GitHub Secrets 설정

### GCP 관련

| Secret 이름 | 설명 | 예시/형식 |
|------------|------|----------|
| `GCP_PROJECT_ID` | GCP 프로젝트 ID | `my-project-123456` |
| `GCP_SA_KEY` | GCP 서비스 계정 키 (JSON) | `{ "type": "service_account", ... }` |

**서비스 계정 필요 권한:**
```
- Compute Admin
- Storage Admin
- Secret Manager Admin
- Artifact Registry Administrator
- Service Account User
- Cloud Run Admin
- Project IAM Admin
```

### 데이터베이스 (Supabase)

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `SUPABASE_HOST` | Supabase PostgreSQL 호스트 | `db.xxxxxx.supabase.co` |
| `SUPABASE_DB` | 데이터베이스 이름 | `postgres` |
| `SUPABASE_USER` | 데이터베이스 사용자 | `postgres` |
| `SUPABASE_PASSWORD` | 데이터베이스 비밀번호 | `********` |

### 외부 서비스

| Secret 이름 | 설명 | 예시 |
|------------|------|------|
| `GEMINI_API_KEY` | Google Gemini API 키 | `AIzaSy...` |
| `GCS_BUCKET_NAME` | GCS 버킷 이름 (전역 고유) | `connectable-files-123456` |
| `WORKER_IMAGE` | Cloud Run Worker 이미지 | `asia-northeast3-docker.pkg.dev/.../worker:latest` |

## Secrets 설정 방법

1. GitHub Repository → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** 클릭
3. 각 Secret 이름과 값을 입력하여 추가

## GCP 서비스 계정 생성 및 키 발급

```bash
# 서비스 계정 생성
gcloud iam service-accounts create github-actions \
  --display-name="GitHub Actions"

# 필요한 역할 부여
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:github-actions@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/compute.admin"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:github-actions@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.admin"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:github-actions@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.admin"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:github-actions@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.admin"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:github-actions@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:github-actions@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# 키 생성 (JSON 파일 다운로드)
gcloud iam service-accounts keys create key.json \
  --iam-account=github-actions@PROJECT_ID.iam.gserviceaccount.com

# key.json의 내용을 GCP_SA_KEY에 복사
cat key.json | pbcopy  # Mac
cat key.json | xclip -selection clipboard  # Linux
```

## 워크플로우 동작

### Pull Request 시
- ✅ Gradle 빌드 및 테스트
- ✅ Terraform Plan (변경사항 미리보기)
- ❌ Docker 이미지 푸시 및 배포 (안함)

### Main 브랜치 Push 시
- ✅ Gradle 빌드 및 테스트
- ✅ Docker 이미지 빌드 및 Artifact Registry 푸시
- ✅ Terraform Apply (인프라 업데이트)
- ✅ Compute Engine VM 배포
- ✅ 헬스 체크

## 주요 출력값

배포 완료 후 다음 정보를 확인할 수 있습니다:

- **API URL**: Spring Boot 애플리케이션 접속 주소
- **VM IP**: Compute Engine 외부 IP
- **Worker URL**: Cloud Run Worker 서비스 URL
- **Image**: 배포된 Docker 이미지 태그

## 수동 배포

GitHub Actions 탭 → **CI/CD Pipeline** → **Run workflow** → 브랜치 선택 → **Run workflow**

## 문제 해결

### 빌드 실패 시
```bash
# 로컬에서 빌드 테스트
./gradlew clean build

# Docker 빌드 테스트
docker build -t test-image .
```

### Terraform 오류 시
```bash
cd terraform
terraform init
terraform plan
```

### 배포 후 접속 불가
- GCP Console → Compute Engine → VM 인스턴스 상태 확인
- Serial Port 로그 확인
- 방화벽 규칙 확인 (포트 8080)
