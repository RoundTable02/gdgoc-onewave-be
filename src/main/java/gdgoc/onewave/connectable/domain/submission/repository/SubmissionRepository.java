package gdgoc.onewave.connectable.domain.submission.repository;

import gdgoc.onewave.connectable.domain.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
}
