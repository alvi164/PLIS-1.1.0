package edu.university.plis.server.service;

import edu.university.plis.server.repository.StudentProfileRepository;
import edu.university.plis.shared.dto.StudentSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentDirectoryService {
    private final StudentProfileRepository studentRepository;

    public StudentDirectoryService(StudentProfileRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentSummary> listStudents() {
        return studentRepository.findAllByOrderByStudentNumberAsc().stream()
                .map(ViewMapper::toStudentSummary)
                .toList();
    }
}
