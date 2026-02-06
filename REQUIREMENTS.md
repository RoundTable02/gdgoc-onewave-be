## 🏗️ Spring Boot 메인 서버 기술 규격

### 1. 기술 스택

* **Framework:** Spring Boot 3.x (Java 17+)
* **Database:** Supabase (PostgreSQL) + Spring Data JPA
* **Storage:** Google Cloud Storage (GCS) - 정적 파일 호스팅용
* **AI:** Gemini API (Spring AI 또는 WebClient 사용)
* **Worker 연동:** WebClient (Cloud Run 직접 호출)

---

## 📂 데이터베이스 스키마 (Supabase / PostgreSQL)

| 테이블명 | 주요 컬럼 | 비고 |
| --- | --- | --- |
| **Assignment** | `id`, `title`, `content`, `sub_tasks(JSON)`, `ai_script(TEXT)` | 과제 정의 및 생성된 채점 코드 |
| **Submission** | `id`, `assignment_id`, `candidate_name`, `file_url`, `status` | 제출 정보 및 호스팅 URL |
| **GradingResult** | `id`, `submission_id`, `task_name`, `is_passed`, `feedback` | 세부 항목별 채점 결과 |

---

## 🚀 API 엔드포인트 명세

### [사용자: 구인자 - 과제 생성 및 관리]

**1. 구현과제 생성 및 AI 스크립트 도출**

* **Endpoint:** `POST /api/assignments`
* **기능:** 자연어 sub-tasks를 받아 Gemini API를 호출하여 Playwright 스크립트를 생성하고 과제를 저장합니다.
* **Request Body:**
```json
{
  "title": "로그인 페이지 구현 과제",
  "content": "React를 이용해 로그인 기능을 구현하세요.",
  "subTasks": ["로그인 버튼이 있어야 함", "클릭 시 /main으로 이동"]
}

```



**2. 채점 결과 목록 및 배포 페이지 조회**

* **Endpoint:** `GET /api/assignments/{id}/results`
* **기능:** 구직자들이 업로드한 결과물들의 채점 결과와 배포된 URL(`file_url`)을 반환합니다.

---

### [사용자: 구직자 - 제출 및 결과 확인]

**1. 구현과제 리스트 및 상세 조회**

* **Endpoint:** `GET /api/assignments`
* **Endpoint:** `GET /api/assignments/{id}`

**2. 파일 업로드 및 채점 트리거 (Drag & Drop 대응)**

* **Endpoint:** `POST /api/assignments/{id}/submissions`
* **기능:**
1. 업로드된 빌드 파일(Zip)을 GCS에 저장.
2. GCS 정적 호스팅 주소 추출 (예: `https://storage.googleapis.com/.../index.html`).
3. **Cloud Run 워커 즉시 호출:** 스크립트와 URL을 담아 채점 요청.


* **Request:** `MultipartFile file`, `candidateName`

---

## ⚙️ 핵심 비즈니스 로직 구현 가이드

### 1. Google Cloud Storage (GCS) 파일 업로드 및 호스팅

Spring Boot에서 파일을 업로드하고, Playwright가 접근할 수 있도록 공개 URL을 생성합니다.

```java
@Service
public class StorageService {
    public String uploadBuildFile(MultipartFile file, String path) {
        // GCS 라이브러리를 사용해 파일 업로드
        // 업로드 후 해당 객체의 Public Read 권한 부여
        // 결과 URL 반환: https://storage.googleapis.com/[버킷]/[경로]/index.html
        return publicUrl;
    }
}

```

### 2. Cloud Run 워커 직접 호출 (WebClient)

채점 서버가 서버리스(Cloud Run)인 경우, Spring Boot에서 다음과 같이 직접 호출합니다.

```java
@Service
public class GradingTriggerService {
    public void triggerCloudRun(String workerUrl, GradingRequestDto request) {
        WebClient.create(workerUrl)
            .post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Void.class)
            .subscribe(); // 비동기 실행
    }
}

```

---

## 💡 해커톤 구현 팁 (Success Points)

1. **GCS 압축 해제:** 구직자가 Zip 파일을 올리면, Spring Boot 서버에서 압축을 풀어 GCS에 폴더 구조 그대로 올려야 정적 호스팅이 작동합니다. (`unzip` 라이브러리 활용)
2. **Supabase Realtime:** Spring Boot가 채점 결과를 DB에 `INSERT`하면, 프론트엔드(React/Next.js)는 Supabase SDK를 통해 새로고침 없이 화면에 점수가 뜨게 할 수 있습니다.
3. **CORS 설정:** Cloud Run 워커와 Spring Boot 서버 간의 통신 시 CORS 에러가 나지 않도록 GCP 콘솔에서 Cloud Run의 인보커(Invoker) 권한을 `allUsers`로 설정하거나 적절한 인증을 추가하세요.