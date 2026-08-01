package edu.university.plis.server.repository;

import edu.university.plis.server.domain.StudentConnection;
import edu.university.plis.shared.model.DeviceConnectionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentConnectionRepository extends JpaRepository<StudentConnection, Long> {
    @EntityGraph(attributePaths = {"student.user"})
    Optional<StudentConnection> findByStudentIdAndDeviceId(Long studentId, String deviceId);

    @EntityGraph(attributePaths = {"student.user"})
    List<StudentConnection> findAllByOrderByRequestedAtDesc();

    List<StudentConnection> findByStudentId(Long studentId);

    long countByStudentIdAndStatus(Long studentId, DeviceConnectionStatus status);

    boolean existsByStudentUserUsernameIgnoreCaseAndDeviceIdAndStatus(
            String username, String deviceId, DeviceConnectionStatus status);
}
