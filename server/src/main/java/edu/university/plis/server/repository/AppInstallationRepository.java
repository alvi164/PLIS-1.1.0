package edu.university.plis.server.repository;

import edu.university.plis.server.domain.AppInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppInstallationRepository extends JpaRepository<AppInstallation, Long> {
}
