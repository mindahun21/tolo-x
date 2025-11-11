package com.mindahun.auth.controller;

import com.mindahun.auth.dto.UserDto;
import com.mindahun.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final AuthService authService;

    @GetMapping("/user/{email}")
    public UserDto getDemoUserFromUserService(@PathVariable String email) {
        log.debug("==========getDemoUserFromUserService called with email {}", email);
        return authService.getUserByEmail(email);
    }
}
