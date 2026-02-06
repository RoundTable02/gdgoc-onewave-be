package gdgoc.onewave.connectable.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "C001", "Invalid request"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "Internal server error"),
    
    // Assignment
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "Assignment not found"),
    AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "A002", "Failed to generate AI script"),
    
    // Submission
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "Submission not found"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "S002", "Only .zip files are allowed"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "S003", "File size exceeds limit"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S004", "Failed to upload file"),
    
    // Grading
    GRADING_TRIGGER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "Failed to trigger grading");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
