package com.mindahun.auth.controller;

import com.mindahun.auth.config.PropertiesConfig;
import com.mindahun.auth.mapper.LoginRequest;
import com.mindahun.auth.mapper.RegistrationRequest;
import com.mindahun.auth.service.UserService;
import com.mindahun.auth.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PropertiesConfig propertiesConfig;


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest registrationRequest) {
        userService.saveNewUser(registrationRequest);
        return ResponseEntity.ok("User is registered");
    }
//    TODO: make conditional response based on the client type (web, other)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String accessToken = jwtUtil.generateToken(userDetails);

            String clientTypeHeader = request.getHeader("X-Client-Type");
            if("web".equalsIgnoreCase(clientTypeHeader)){
                Cookie cookie = new Cookie("access_token", accessToken);
                cookie.setHttpOnly(true);
                cookie.setPath("/");
                cookie.setMaxAge((int) propertiesConfig.getTokenExpirationMsec() / 1000);
                response.addCookie(cookie);
                return ResponseEntity.ok("login successful");

            }
            String refreshToken = jwtUtil.generateRefreshToken(userDetails);
            Map<String, String> tokens = new HashMap<>();
            tokens.put("token", accessToken);
            tokens.put("refresh_token", refreshToken);

            return ResponseEntity.ok(tokens);
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
