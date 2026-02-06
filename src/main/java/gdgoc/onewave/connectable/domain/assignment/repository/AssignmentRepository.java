package gdgoc.onewave.connectable.domain.assignment.repository;

import gdgoc.onewave.connectable.domain.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
}
