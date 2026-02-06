package gdgoc.onewave.connectable.domain.grading.repository;

import gdgoc.onewave.connectable.domain.entity.GradingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GradingResultRepository extends JpaRepository<GradingResult, UUID> {
}
