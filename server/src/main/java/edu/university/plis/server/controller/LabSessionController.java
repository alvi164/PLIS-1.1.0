package edu.university.plis.server.controller;

import edu.university.plis.server.service.LabSessionService;
import edu.university.plis.server.service.LabCodeService;
import edu.university.plis.shared.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/labs")
public class LabSessionController {
    private final LabSessionService labSessionService;
    private final LabCodeService labCodeService;

    public LabSessionController(LabSessionService labSessionService, LabCodeService labCodeService) {
        this.labSessionService = labSessionService;
        this.labCodeService = labCodeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<LabSessionSummary> list(Authentication authentication) {
        return labSessionService.listForTeacher(authentication.getName());
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('STUDENT')")
    public List<LabSessionSummary> available(Authentication authentication) {
        return labSessionService.listAvailableForStudent(authentication.getName());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<LabSessionSummary> create(
            Authentication authentication, @RequestBody CreateLabSessionRequest request) {
        LabSessionSummary created = labSessionService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/labs/" + created.id())).body(created);
    }

    @PostMapping("/{labSessionId}/start")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public LabSessionSummary start(Authentication authentication, @PathVariable long labSessionId) {
        return labSessionService.start(authentication.getName(), labSessionId);
    }

    @PostMapping("/{labSessionId}/end")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public LabSessionSummary end(Authentication authentication, @PathVariable long labSessionId) {
        return labSessionService.end(authentication.getName(), labSessionId);
    }

    @PutMapping("/{labSessionId}/policy")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public LabSessionSummary updatePolicy(
            Authentication authentication,
            @PathVariable long labSessionId,
            @RequestBody UpdateLabPolicyRequest request) {
        return labSessionService.updatePolicy(authentication.getName(), labSessionId, request);
    }

    @PostMapping("/{labSessionId}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> join(
            Authentication authentication,
            @PathVariable long labSessionId,
            @RequestBody JoinLabRequest request) {
        labSessionService.join(authentication.getName(), labSessionId, request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{labSessionId}/code")
    @PreAuthorize("hasRole('STUDENT')")
    public LabCodeSnapshot studentCode(Authentication authentication, @PathVariable long labSessionId) {
        return labCodeService.readForStudent(authentication.getName(), labSessionId);
    }

    @PutMapping("/{labSessionId}/code")
    @PreAuthorize("hasRole('STUDENT')")
    public LabCodeSnapshot updateStudentCode(
            Authentication authentication,
            @PathVariable long labSessionId,
            @RequestBody LabCodeUpdateRequest request) {
        return labCodeService.updateForStudent(authentication.getName(), labSessionId, request);
    }

    @GetMapping("/{labSessionId}/students/{studentId}/code")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public LabCodeSnapshot teacherCode(
            Authentication authentication,
            @PathVariable long labSessionId,
            @PathVariable long studentId) {
        return labCodeService.readForTeacher(authentication.getName(), labSessionId, studentId);
    }

    @PutMapping("/{labSessionId}/students/{studentId}/code")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public LabCodeSnapshot updateTeacherCode(
            Authentication authentication,
            @PathVariable long labSessionId,
            @PathVariable long studentId,
            @RequestBody LabCodeUpdateRequest request) {
        return labCodeService.updateForTeacher(authentication.getName(), labSessionId, studentId, request);
    }

    @PostMapping("/{labSessionId}/events")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> recordEvent(
            Authentication authentication,
            @PathVariable long labSessionId,
            @RequestBody LabEventRequest request) {
        labSessionService.recordEvent(authentication.getName(), labSessionId, request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{labSessionId}/activity")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<StudentActivitySnapshot> activity(
            Authentication authentication, @PathVariable long labSessionId) {
        return labSessionService.activity(authentication.getName(), labSessionId);
    }

    @GetMapping("/{labSessionId}/students/{studentId}/events")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<EventLogView> history(
            Authentication authentication,
            @PathVariable long labSessionId,
            @PathVariable long studentId) {
        return labSessionService.history(authentication.getName(), labSessionId, studentId);
    }
}
