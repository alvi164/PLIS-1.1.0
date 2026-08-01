package edu.university.plis.server.controller;

import edu.university.plis.server.service.SetupService;
import edu.university.plis.shared.dto.InitialTeacherSetupRequest;
import edu.university.plis.shared.dto.SetupStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setup")
public class SetupController {
    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public SetupStatus status() {
        return setupService.status();
    }

    @PostMapping("/teacher")
    public SetupStatus initializeTeacher(@RequestBody InitialTeacherSetupRequest request) {
        return setupService.initializeTeacher(request);
    }
}
