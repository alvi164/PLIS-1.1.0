package edu.university.plis.server.repository;

import edu.university.plis.server.domain.AppUser;
import edu.university.plis.shared.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRole(RoleName role);
}
