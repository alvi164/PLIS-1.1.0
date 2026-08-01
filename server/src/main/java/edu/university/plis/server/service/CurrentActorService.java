package edu.university.plis.server.service;

import edu.university.plis.server.domain.AppUser;
import edu.university.plis.server.domain.StudentProfile;
import edu.university.plis.server.domain.TeacherProfile;
import edu.university.plis.server.repository.AppUserRepository;
import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.server.repository.TeacherProfileRepository;
import edu.university.plis.shared.model.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentActorService {
    private final AppUserRepository userRepository;
    private final TeacherProfileRepository teacherRepository;
    private final StudentProfileRepository studentRepository;

    public CurrentActorService(
            AppUserRepository userRepository,
            TeacherProfileRepository teacherRepository,
            StudentProfileRepository studentRepository) {
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
    }

    public AppUser requireUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    @Transactional
    public TeacherProfile requireTeachingProfile(String username) {
        AppUser user = requireUser(username);
        if (user.getRole() != RoleName.ROLE_TEACHER && user.getRole() != RoleName.ROLE_ADMIN) {
            throw new InvalidRequestException("A teacher or administrator account is required");
        }
        return teacherRepository.findByUserUsernameIgnoreCase(username)
                .orElseGet(() -> teacherRepository.save(new TeacherProfile(user, null)));
    }

    public StudentProfile requireStudent(String username) {
        return studentRepository.findByUserUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidRequestException("A student profile is required"));
    }

    public void requireCanManage(String username, TeacherProfile owner) {
        AppUser actor = requireUser(username);
        if (actor.getRole() == RoleName.ROLE_ADMIN) {
            return;
        }
        if (actor.getRole() != RoleName.ROLE_TEACHER
                || !owner.getUser().getUsername().equalsIgnoreCase(username)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This session belongs to another teacher");
        }
    }
}
