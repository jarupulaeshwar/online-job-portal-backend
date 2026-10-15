package com.jobportal.controller;

import com.jobportal.dto.Dtos.*;
import com.jobportal.security.AuthUser;
import com.jobportal.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register/seeker")
    public ResponseEntity<AuthResponse> registerSeeker(@Valid @RequestBody RegisterSeekerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerSeeker(req));
    }

    @PostMapping("/register/recruiter")
    public ResponseEntity<AuthResponse> registerRecruiter(@Valid @RequestBody RegisterRecruiterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerRecruiter(req));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(user.id(), req);
        return Map.of("message", "Password updated.");
    }
}
