package com.mindahun.auth.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class Utils {
    private final PasswordEncoder passwordEncoder;

    public String randomPassword() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

}
