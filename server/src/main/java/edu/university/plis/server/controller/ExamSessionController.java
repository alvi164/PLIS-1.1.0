package edu.university.plis.server.controller;

import edu.university.plis.server.service.ExamSessionService;
import edu.university.plis.server.service.SubmissionService;
import edu.university.plis.shared.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamSessionController {
    private final ExamSessionService examSessionService;
    private final SubmissionService submissionService;
    private final edu.university.plis.server.service.ExamCodeService examCodeService;

    public ExamSessionController(
            ExamSessionService examSessionService,
            SubmissionService submissionService,
            edu.university.plis.server.service.ExamCodeService examCodeService) {
        this.examSessionService = examSessionService;
        this.submissionService = submissionService;
        this.examCodeService = examCodeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<ExamSummary> list(Authentication authentication) {
        return examSessionService.listForTeacher(authentication.getName());
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ExamSummary> available(Authentication authentication) {
        return examSessionService.listAvailableForStudent(authentication.getName());
    }

    @GetMapping("/{examSessionId}")
    public ExamDetails details(Authentication authentication, @PathVariable long examSessionId) {
        return examSessionService.details(authentication.getName(), examSessionId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ExamSummary> create(
            Authentication authentication, @RequestBody CreateExamRequest request) {
        ExamSummary created = examSessionService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/exams/" + created.id())).body(created);
    }

    @PostMapping("/{examSessionId}/start")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ExamSummary start(Authentication authentication, @PathVariable long examSessionId) {
        return examSessionService.start(authentication.getName(), examSessionId);
    }

    @PostMapping("/{examSessionId}/end")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ExamSummary end(Authentication authentication, @PathVariable long examSessionId) {
        return examSessionService.end(authentication.getName(), examSessionId);
    }

    @PostMapping("/{examSessionId}/signals")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> signal(
            Authentication authentication,
            @PathVariable long examSessionId,
            @RequestBody ExamSignalRequest request) {
        examSessionService.recordSignal(authentication.getName(), examSessionId, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{examSessionId}/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionReceipt> submit(
            Authentication authentication,
            @PathVariable long examSessionId,
            @RequestBody CodeSubmissionRequest request) {
        SubmissionReceipt receipt = submissionService.submit(authentication.getName(), examSessionId, request);
        return ResponseEntity.created(URI.create(
                "/api/exams/" + examSessionId + "/submissions/" + receipt.submissionId())).body(receipt);
    }

    @GetMapping("/{examSessionId}/code")
    @PreAuthorize("hasRole('STUDENT')")
    public ExamCodeSnapshot studentCode(
            Authentication authentication, @PathVariable long examSessionId) {
        return examCodeService.readForStudent(authentication.getName(), examSessionId);
    }

    @PutMapping("/{examSessionId}/code")
    @PreAuthorize("hasRole('STUDENT')")
    public ExamCodeSnapshot updateStudentCode(
            Authentication authentication,
            @PathVariable long examSessionId,
            @RequestBody ExamCodeUpdateRequest request) {
        return examCodeService.updateForStudent(authentication.getName(), examSessionId, request);
    }

    @GetMapping("/{examSessionId}/students/{studentId}/code")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ExamCodeSnapshot teacherCode(
            Authentication authentication,
            @PathVariable long examSessionId,
            @PathVariable long studentId) {
        return examCodeService.readForTeacher(authentication.getName(), examSessionId, studentId);
    }

    @GetMapping("/{examSessionId}/dashboard")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<ExamParticipantSnapshot> dashboard(
            Authentication authentication, @PathVariable long examSessionId) {
        return examSessionService.dashboard(authentication.getName(), examSessionId);
    }

    @GetMapping("/{examSessionId}/integrity")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<IntegrityFlagView> integrity(
            Authentication authentication, @PathVariable long examSessionId) {
        return examSessionService.integrityReport(authentication.getName(), examSessionId);
    }
}
