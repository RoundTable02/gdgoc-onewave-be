# GET /api/assignments & GET /api/assignments/{id} API 개발 계획서

> 과제 목록 조회 및 상세 조회 API 구현을 위한 상세 기술 계획

---

## 1. 개요

### 1.1 API 목적
- **목록 조회**: 구직자가 등록된 과제 목록을 페이지네이션으로 조회 (content 200자 truncate)
- **상세 조회**: 특정 과제의 전체 내용과 subTasks를 확인

### 1.2 현재 상태 분석

| 구성 요소 | 상태 | 비고 |
|---------|------|------|
| Controller (GET 목록) | ⚠️ Stub | 빈 리스트 반환 중 (`List.of()`) |
| Controller (GET 상세) | ⚠️ Stub | null 반환 중 (`ApiResponse.success(null)`) |
| AssignmentService | ⚠️ create만 구현 | 조회 메서드 미구현 |
| AssignmentRepository | ✅ 존재 | `JpaRepository<Assignment, UUID>` 기본 메서드 사용 가능 |
| AssignmentListResponse | ✅ 완료 | `id, title, content, createdAt` |
| AssignmentListDataResponse | ⚠️ 수정 필요 | 페이지네이션 필드 누락 (현재 `data`만 존재) |
| AssignmentResponse | ✅ 완료 | `id, title, content, subTasks, createdAt` |
| Assignment Entity | ✅ 완료 | 모든 필드 매핑 완료 |
| ErrorCode (ASSIGNMENT_NOT_FOUND) | ✅ 완료 | `A001` |

---

## 2. API 스펙

### 2.1 과제 목록 조회 (GET /api/assignments)

**Request**
```yaml
Method: GET
Path: /api/assignments
Query Parameters:
  page: integer (default: 0)
  size: integer (default: 20)
```

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Frontend React Assignment",
      "content": "# Description\nThis assignment is about building a...",
      "createdAt": "2026-02-07T01:30:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

**주요 사항:**
- `content` 필드는 200자로 truncate
- 최신순 정렬 (`createdAt DESC`)
- `ApiResponse` 래퍼 없이 직접 반환 (현재 Controller 시그니처와 일치)

### 2.2 과제 상세 조회 (GET /api/assignments/{id})

**Request**
```yaml
Method: GET
Path: /api/assignments/{id}
Path Variable:
  id: UUID (required)
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Frontend React Assignment",
    "content": "# Description\nThis assignment is about building a full React application...",
    "subTasks": ["GNB UI 구현", "로그인 폼 검증"],
    "createdAt": "2026-02-07T01:30:00"
  },
  "error": null
}
```

**Response (404 Not Found)**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "A001",
    "message": "Assignment not found"
  }
}
```

---

## 3. 비즈니스 로직 흐름

### 3.1 목록 조회

```
GET /api/assignments?page=0&size=20
          │
          ▼
┌──────────────────────────────────────────┐
│ 1. Controller: page, size 파라미터 수신    │
│    - @RequestParam으로 기본값 적용          │
└──────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────┐
│ 2. Service: 페이지네이션 조회              │
│    - PageRequest.of(page, size, Sort)    │
│    - Sort.by(Sort.Direction.DESC,        │
│            "createdAt")                  │
│    - assignmentRepository.findAll(page)  │
└──────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────┐
│ 3. Service: DTO 변환                      │
│    - Entity → AssignmentListResponse     │
│    - content 200자 truncate 처리          │
│    - Page 메타데이터 포함                   │
└──────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────┐
│ 4. Controller: AssignmentListDataResponse │
│    반환 (200 OK)                          │
└──────────────────────────────────────────┘
```

### 3.2 상세 조회

```
GET /api/assignments/{id}
          │
          ▼
┌──────────────────────────────────────────┐
│ 1. Controller: UUID 경로 변수 수신         │
└──────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────┐
│ 2. Service: ID로 조회                     │
│    - assignmentRepository.findById(id)   │
│    - 없으면 BusinessException(A001) throw │
└──────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────┐
│ 3. Service: DTO 변환                      │
│    - Entity → AssignmentResponse         │
│    - content 전체 포함, subTasks 포함      │
└──────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────┐
│ 4. Controller: ApiResponse.success()      │
│    반환 (200 OK)                          │
└──────────────────────────────────────────┘
```

---

## 4. 구현 상세

### 4.1 수정 파일 목록

| 순서 | 파일 | 변경 유형 | 설명 |
|-----|------|---------|------|
| 1 | `AssignmentListDataResponse.java` | 수정 | 페이지네이션 필드 추가 |
| 2 | `AssignmentService.java` | 수정 | `getAssignments`, `getAssignment` 메서드 추가 |
| 3 | `AssignmentController.java` | 수정 | Stub 구현부를 Service 호출로 교체 |

### 4.2 파일별 변경 상세

#### 4.2.1 AssignmentListDataResponse.java (수정)

**현재:**
```java
public record AssignmentListDataResponse(
    List<AssignmentListResponse> data
) {}
```

**변경 후:**
```java
public record AssignmentListDataResponse(
    @Schema(description = "List of assignments")
    List<AssignmentListResponse> content,

    @Schema(description = "Current page number")
    int page,

    @Schema(description = "Page size")
    int size,

    @Schema(description = "Total number of elements")
    long totalElements,

    @Schema(description = "Total number of pages")
    int totalPages
) {}
```

**변경 이유:**
- 명세서 스펙에 맞춘 페이지네이션 메타데이터 추가
- 필드명 `data` → `content`로 변경 (명세서 일치)

#### 4.2.2 AssignmentService.java (수정)

**추가 메서드 1: `getAssignments`**
```java
@Transactional(readOnly = true)
public AssignmentListDataResponse getAssignments(int page, int size) {
    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Assignment> assignmentPage = assignmentRepository.findAll(pageRequest);

    List<AssignmentListResponse> content = assignmentPage.getContent().stream()
            .map(a -> new AssignmentListResponse(
                    a.getId(),
                    a.getTitle(),
                    truncateContent(a.getContent(), 200),
                    a.getCreatedAt()
            ))
            .toList();

    return new AssignmentListDataResponse(
            content,
            assignmentPage.getNumber(),
            assignmentPage.getSize(),
            assignmentPage.getTotalElements(),
            assignmentPage.getTotalPages()
    );
}
```

**추가 메서드 2: `getAssignment`**
```java
@Transactional(readOnly = true)
public AssignmentResponse getAssignment(UUID id) {
    Assignment assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));

    return new AssignmentResponse(
            assignment.getId(),
            assignment.getTitle(),
            assignment.getContent(),
            assignment.getSubTasks(),
            assignment.getCreatedAt()
    );
}
```

**추가 private 메서드: `truncateContent`**
```java
private String truncateContent(String content, int maxLength) {
    if (content == null || content.length() <= maxLength) {
        return content;
    }
    return content.substring(0, maxLength) + "...";
}
```

**주요 설계 결정:**
- `@Transactional(readOnly = true)`: 읽기 전용 트랜잭션으로 성능 최적화
- `Page<Assignment>` 활용: Spring Data JPA 기본 페이지네이션 사용
- Content truncate: 200자 초과 시 `...` 추가
- 존재하지 않는 ID 조회 시 `BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND)` throw

#### 4.2.3 AssignmentController.java (수정)

**목록 조회 변경:**

현재:
```java
@GetMapping
public AssignmentListDataResponse getAssignments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) {
    return new AssignmentListDataResponse(List.of());
}
```

변경 후:
```java
@GetMapping
public AssignmentListDataResponse getAssignments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) {
    return assignmentService.getAssignments(page, size);
}
```

**상세 조회 변경:**

현재:
```java
@GetMapping("/{id}")
public ApiResponse<AssignmentResponse> getAssignment(@PathVariable UUID id) {
    return ApiResponse.success(null);
}
```

변경 후:
```java
@GetMapping("/{id}")
public ApiResponse<AssignmentResponse> getAssignment(@PathVariable UUID id) {
    return ApiResponse.success(assignmentService.getAssignment(id));
}
```

---

## 5. 의존성 관계

```
┌──────────────────────────────────────┐
│         AssignmentController          │
│  GET /api/assignments                │
│  GET /api/assignments/{id}           │
└──────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────┐
│          AssignmentService            │
│  getAssignments(page, size)          │
│  getAssignment(id)                   │
└──────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────┐
│        AssignmentRepository           │
│  findAll(Pageable) - 기본 제공        │
│  findById(UUID) - 기본 제공           │
└──────────────────────────────────────┘
```

신규 파일 생성 없음. Repository에 커스텀 쿼리 추가 불필요 (JpaRepository 기본 메서드로 충분).

---

## 6. 에러 처리 매트릭스

| 상황 | Exception | ErrorCode | HTTP Status |
|-----|-----------|-----------|-------------|
| 존재하지 않는 과제 ID | BusinessException | A001 (ASSIGNMENT_NOT_FOUND) | 404 |
| 잘못된 UUID 형식 (PathVariable) | MethodArgumentTypeMismatchException | - | 400 |
| 예상치 못한 오류 | Exception | C002 (INTERNAL_ERROR) | 500 |

**참고:** `MethodArgumentTypeMismatchException`은 Spring이 `@PathVariable UUID id`에 잘못된 형식이 들어오면 자동으로 발생. 현재 `GlobalExceptionHandler`에서 `Exception` catch-all로 처리됨 (500 반환). 400으로 반환하려면 별도 핸들러 추가 필요하지만, 이번 스코프에서는 제외.

---

## 7. 구현 순서 (권장)

| 순서 | 작업 | 의존성 |
|-----|------|--------|
| 1 | `AssignmentListDataResponse.java` 수정 (페이지네이션 필드 추가) | - |
| 2 | `AssignmentService.java`에 `getAssignments`, `getAssignment` 메서드 추가 | 순서 1 |
| 3 | `AssignmentController.java` Stub → Service 호출로 교체 | 순서 2 |
| 4 | 빌드 및 동작 확인 | 순서 3 |

---

## 8. 주의사항

### 8.1 content truncate 로직
- 목록 조회에서만 200자 truncate 적용
- 상세 조회에서는 전체 content 반환
- null safety 처리 필수

### 8.2 aiScript 미노출
- `AssignmentResponse`에 `aiScript` 필드 없음
- 채점 스크립트는 외부에 노출하지 않음 (보안)

### 8.3 정렬 기준
- 목록 조회 시 `createdAt DESC` (최신순)
- Spring Data JPA의 `Sort` 사용

### 8.4 Controller 불필요 import 정리
- 수정 후 `List` import가 불필요해질 수 있음 (Service에서 DTO를 직접 반환하므로)

---

## 9. 체크리스트

### 9.1 구현
- [ ] `AssignmentListDataResponse` 페이지네이션 필드 추가
- [ ] `AssignmentService.getAssignments()` 구현
- [ ] `AssignmentService.getAssignment()` 구현
- [ ] `AssignmentController.getAssignments()` Service 연동
- [ ] `AssignmentController.getAssignment()` Service 연동

### 9.2 검증
- [ ] 빌드 성공 확인
- [ ] Swagger UI에서 목록 조회 동작 확인
- [ ] Swagger UI에서 상세 조회 동작 확인
- [ ] 존재하지 않는 ID 조회 시 404 반환 확인
- [ ] 페이지네이션 파라미터 동작 확인
- [ ] content truncate 200자 확인

---

## 10. 참고 파일

| 파일 | 역할 |
|-----|------|
| `SPECIFICATION.md` 섹션 6.1.2, 6.1.3 | API 스펙 정의 |
| `POST_api_assignments.md` | 이전 구현 계획 참고 |
| `AssignmentService.java` | 기존 Service 패턴 참고 |
| `GlobalExceptionHandler.java` | 예외 처리 동작 확인 |
| `ErrorCode.java` | `ASSIGNMENT_NOT_FOUND (A001)` 확인 |
