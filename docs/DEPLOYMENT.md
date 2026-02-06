# Connectable API 배포 가이드

## 아키텍처 개요

```
GitHub Actions (CI/CD)
    │
    ├── Build & Test (Gradle)
    │
    ├── Docker Build & Push
    │       │
    │       └── Artifact Registry (asia-northeast3-docker.pkg.dev)
    │
    └── [Manual] Deploy to VM

GCE VM (Container-Optimized OS)
    │
    ├── systemd service (connectable-api.service)
    │
    ├── /var/lib/pull-image.sh (Docker 인증 & pull)
    │
    └── /var/lib/start-connectable.sh (Secrets 가져와서 컨테이너 실행)
```

## 배포 프로세스

### 1. CI/CD 파이프라인 (자동)

main 브랜치에 push 시 자동 실행:

1. Gradle 빌드 & 테스트
2. Docker 이미지 빌드
3. Artifact Registry에 push (`latest` 태그)

### 2. VM 배포 (수동)

CI/CD 완료 후 수동으로 VM에 접속하여 배포:

```bash
# 1. VM 접속
gcloud compute ssh connectable-api --zone=asia-northeast3-a

# 2. 서비스 재시작 (자동으로 새 이미지 pull)
sudo systemctl restart connectable-api

# 3. 상태 확인
sudo systemctl status connectable-api
sudo docker ps
```

### 3. 수동 이미지 Pull (필요시)

서비스 재시작으로 안될 경우:

```bash
# Docker 인증
export DOCKER_CONFIG=/home/chronos/.docker
TOKEN=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
echo $TOKEN | sudo DOCKER_CONFIG=$DOCKER_CONFIG docker login -u oauth2accesstoken --password-stdin asia-northeast3-docker.pkg.dev

# 이미지 Pull
sudo DOCKER_CONFIG=$DOCKER_CONFIG docker pull asia-northeast3-docker.pkg.dev/gdgoc-onewave/connectable/connectable-api:latest

# 서비스 재시작
sudo systemctl restart connectable-api
```

## VM 설정 파일

### /var/lib/pull-image.sh

Docker 인증 및 이미지 pull 담당:

```bash
#!/bin/bash
set -e
mkdir -p /home/chronos/.docker
export DOCKER_CONFIG=/home/chronos/.docker
TOKEN=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
echo $TOKEN | docker login -u oauth2accesstoken --password-stdin asia-northeast3-docker.pkg.dev
docker pull asia-northeast3-docker.pkg.dev/gdgoc-onewave/connectable/connectable-api:latest
```

### /var/lib/start-connectable.sh

Secret Manager에서 secrets 가져와서 컨테이너 실행:

```bash
#!/bin/bash
set -e

TOKEN=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

get_secret() {
  curl -s -H "Authorization: Bearer $TOKEN" \
    "https://secretmanager.googleapis.com/v1/projects/gdgoc-onewave/secrets/$1/versions/latest:access" \
    | grep -o '"data": *"[^"]*' | cut -d'"' -f4 | base64 -d
}

GEMINI_API_KEY=$(get_secret "connectable-gemini-api-key")
SUPABASE_DB=$(get_secret "connectable-supabase-db")
SUPABASE_HOST=$(get_secret "connectable-supabase-host")
SUPABASE_PASSWORD=$(get_secret "connectable-supabase-password")
SUPABASE_USER=$(get_secret "connectable-supabase-user")

exec docker run --rm --name connectable-api \
  -p 8080:8080 \
  -e GCS_BASE_URL="https://storage.googleapis.com" \
  -e GCS_BUCKET_NAME="connectable-submissions-hackathon" \
  -e GEMINI_MODEL="gemini-1.5-pro" \
  -e SPRING_PROFILES_ACTIVE="prod" \
  -e WORKER_TIMEOUT_SECONDS="60" \
  -e GEMINI_API_KEY="$GEMINI_API_KEY" \
  -e SUPABASE_DB="$SUPABASE_DB" \
  -e SUPABASE_HOST="$SUPABASE_HOST" \
  -e SUPABASE_PASSWORD="$SUPABASE_PASSWORD" \
  -e SUPABASE_USER="$SUPABASE_USER" \
  -e WORKER_URL="https://connectable-worker-y3a76allja-du.a.run.app" \
  asia-northeast3-docker.pkg.dev/gdgoc-onewave/connectable/connectable-api:latest
```

### /etc/systemd/system/connectable-api.service

```ini
[Unit]
Description=Connectable API Container
After=docker.service
Requires=docker.service

[Service]
Type=simple
Restart=always
RestartSec=10
Environment="DOCKER_CONFIG=/home/chronos/.docker"
ExecStartPre=/bin/bash /var/lib/pull-image.sh
ExecStart=/bin/bash /var/lib/start-connectable.sh
ExecStop=/usr/bin/docker stop connectable-api

[Install]
WantedBy=multi-user.target
```

## Container-Optimized OS (COS) 제약사항

| 제약 | 설명 | 해결책 |
|------|------|--------|
| gcloud CLI 없음 | COS에는 gcloud가 설치되어 있지 않음 | curl로 GCP REST API 직접 호출 |
| /root read-only | 루트 홈 디렉토리에 쓰기 불가 | `/home/chronos/.docker` 사용 |
| noexec 파일시스템 | 대부분 경로에서 스크립트 직접 실행 불가 | `/bin/bash /path/to/script` 형식 사용 |
| docker-credential-gcr 미작동 | credHelper 설정해도 인증 안됨 | access token으로 직접 docker login |

## 로그 확인

```bash
# 서비스 로그
sudo journalctl -u connectable-api -f

# 최근 50줄
sudo journalctl -u connectable-api -n 50 --no-pager

# Docker 컨테이너 로그
sudo docker logs connectable-api -f
```

## 트러블슈팅 히스토리

### 1. `gcloud: command not found`

**원인**: COS에는 gcloud CLI가 없음

**해결**: curl + metadata API로 access token 가져와서 사용
```bash
TOKEN=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
```

### 2. `Unauthenticated request` (Docker pull 실패)

**원인**: Docker가 Artifact Registry 인증 없이 pull 시도

**해결**: access token으로 docker login
```bash
echo $TOKEN | docker login -u oauth2accesstoken --password-stdin asia-northeast3-docker.pkg.dev
```

### 3. `mkdir /root/.docker: read-only file system`

**원인**: COS에서 /root는 read-only

**해결**: DOCKER_CONFIG를 writable 경로로 변경
```bash
export DOCKER_CONFIG=/home/chronos/.docker
```

### 4. Secrets 빈 값

**원인**: Secret Manager API 응답의 `payload.data` 필드를 파싱하는 grep 패턴 오류

**해결**: 올바른 grep 패턴 사용
```bash
# 잘못된 패턴
grep -o '"data":"[^"]*'

# 올바른 패턴 (공백 허용)
grep -o '"data": *"[^"]*'
```

### 5. systemd에서 스크립트 실행 불가

**원인**: /var/lib 등 대부분 경로가 noexec

**해결**: `/bin/bash /path/to/script` 형식으로 실행
```ini
ExecStart=/bin/bash /var/lib/start-connectable.sh
```

### 6. `$(gcloud ...)` 명령어 치환 실패

**원인**: systemd는 shell이 아니라서 command substitution 안됨

**해결**: 별도 bash 스크립트로 분리
