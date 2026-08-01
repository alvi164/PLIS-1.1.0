package edu.university.plis.server.controller;

import edu.university.plis.server.security.AuthTokenService;
import edu.university.plis.server.service.ConnectionApprovalService;
import edu.university.plis.server.service.StudentAccountService;
import edu.university.plis.shared.dto.PasswordChangeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final StudentAccountService accountService;
    private final ConnectionApprovalService connectionService;
    private final AuthTokenService tokenService;

    public AccountController(
            StudentAccountService accountService,
            ConnectionApprovalService connectionService,
            AuthTokenService tokenService) {
        this.accountService = accountService;
        this.connectionService = connectionService;
        this.tokenService = tokenService;
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication, @RequestBody PasswordChangeRequest request) {
        accountService.changeOwnPassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> heartbeat(
            Authentication authentication,
            @RequestHeader("Authorization") String authorization) {
        String token = authorization.substring("Bearer ".length());
        connectionService.heartbeat(authentication.getName(), tokenService.extractDeviceId(token));
        return ResponseEntity.noContent().build();
    }
}
