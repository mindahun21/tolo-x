package com.mindahun.auth.controller;

import com.mindahun.auth.mapper.LoginRequest;
import com.mindahun.auth.mapper.RegistrationRequest;
import com.mindahun.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest registrationRequest) {
        userService.saveNewUser(registrationRequest);
        return ResponseEntity.ok("User is registered");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            String clientType = request.getHeader("X-Client-Type");
            Map<String, Object> result = userService.login(
                    loginRequest.getEmail(),
                    loginRequest.getPassword(),
                    clientType,
                    response
            );
            return ResponseEntity.ok(result);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password.");
        }

    }

    @GetMapping("/check")
    public ResponseEntity<?> check() {
        return ResponseEntity.ok("User is checked");
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return ResponseEntity.ok(userService.currentUser());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated.");
    }
}
