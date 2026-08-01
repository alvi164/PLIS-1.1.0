package edu.university.plis.server.controller;

import edu.university.plis.server.service.StudentDirectoryService;
import edu.university.plis.server.service.StudentAccountService;
import edu.university.plis.server.service.ConnectionApprovalService;
import edu.university.plis.server.service.AuditService;
import edu.university.plis.shared.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherController {
    private final StudentDirectoryService studentDirectoryService;
    private final StudentAccountService accountService;
    private final ConnectionApprovalService connectionService;
    private final AuditService auditService;

    public TeacherController(
            StudentDirectoryService studentDirectoryService,
            StudentAccountService accountService,
            ConnectionApprovalService connectionService,
            AuditService auditService) {
        this.studentDirectoryService = studentDirectoryService;
        this.accountService = accountService;
        this.connectionService = connectionService;
        this.auditService = auditService;
    }

    @GetMapping("/students")
    public List<StudentSummary> students() {
        return studentDirectoryService.listStudents();
    }

    @GetMapping("/accounts")
    public List<StudentAccountView> accounts() {
        return accountService.list();
    }

    @PostMapping("/accounts")
    public StudentAccountView createAccount(
            Authentication authentication, @RequestBody CreateStudentAccountRequest request) {
        return accountService.create(authentication.getName(), request);
    }

    @PostMapping("/accounts/bulk")
    public List<StudentAccountView> createAccounts(
            Authentication authentication, @RequestBody BulkStudentAccountsRequest request) {
        return accountService.createBulk(authentication.getName(), request);
    }

    @PutMapping("/accounts/{studentId}/password")
    public StudentAccountView resetPassword(
            Authentication authentication,
            @PathVariable long studentId,
            @RequestBody PasswordResetRequest request) {
        return accountService.resetPassword(authentication.getName(), studentId, request);
    }

    @PutMapping("/accounts/{studentId}/enabled")
    public StudentAccountView setEnabled(
            Authentication authentication,
            @PathVariable long studentId,
            @RequestBody AccountEnabledRequest request) {
        return accountService.setEnabled(authentication.getName(), studentId, request);
    }

    @PostMapping("/accounts/{studentId}/terminate")
    public ResponseEntity<Void> terminate(
            Authentication authentication, @PathVariable long studentId) {
        connectionService.terminateStudent(studentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/connections")
    public List<ConnectionRequestView> connections() {
        return connectionService.list();
    }

    @PutMapping("/connections/{connectionId}")
    public ConnectionRequestView decideConnection(
            Authentication authentication,
            @PathVariable long connectionId,
            @RequestBody ConnectionDecisionRequest request) {
        return connectionService.decide(connectionId, request.approved(), authentication.getName());
    }

    @GetMapping("/audit")
    public List<AuditLogView> audit(@RequestParam(required = false) String username) {
        return auditService.recent(username);
    }

    @GetMapping("/students/{studentId}/all-events")
    public List<EventLogView> studentEvents(@PathVariable long studentId) {
        return auditService.studentSessionEvents(studentId);
    }
}
