package com.tolox.user.controllers;


import com.tolox.user.dto.UserUpdateDto;
import com.tolox.user.repository.UserRepository;
import com.tolox.user.services.UserService;
import com.tolox.user.models.User;
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

    @GetMapping("/email/{email}")
    public ResponseEntity<User> findUserByEmail(@PathVariable String email){
        return userService.findUserByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping()
    public ResponseEntity<List<User>> findAll(){
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication){
        if(authentication != null && authentication.isAuthenticated()){
            return ResponseEntity.ok(authentication);
        }
        log.info("user is authenticated--------------------------------------------------------");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated.");
    }

}
