package com.tolox.user.controllers;


import com.tolox.user.config.InternalOnly;
import com.tolox.user.dto.UserResponseDto;
import com.tolox.user.dto.UserUpdateDto;
import com.tolox.user.repository.UserRepository;
import com.tolox.user.services.UserService;
import com.tolox.user.models.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;

    @InternalOnly
    @GetMapping("/email/{email}")
    public ResponseEntity<User> findUserByEmail(@PathVariable String email, HttpServletRequest request) {
        log.info("header in /email/ email {}, role,{},  internal token, {}", request.getHeader("X-User-Email"), request.getHeader("X-User-Roles"), request.getHeader("X-Service-Token"));

        return userService.findUserByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @InternalOnly
    @PatchMapping()
    public ResponseEntity<User> update(@RequestBody UserUpdateDto user){
        return ResponseEntity.ok(userService.update(user));
    }

    @PostMapping()
    public ResponseEntity<User> create(@RequestBody User user){

        boolean userExist = userRepository.existsByEmail(user.getEmail());

        User saved = userService.create(user);
        return ResponseEntity.status(userExist ? HttpStatus.CONFLICT : HttpStatus.CREATED).body(saved);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping()
    public ResponseEntity<List<UserResponseDto>> findAll(){
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication, HttpServletRequest request){
        log.info("header in /me email {}, role,{},  internal token, {}", request.getHeader("X-User-Email"), request.getHeader("X-User-Roles"), request.getHeader("X-Service-Token"));
        if(authentication != null && authentication.isAuthenticated()){
            return ResponseEntity.ok(authentication);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated.");
    }

}
