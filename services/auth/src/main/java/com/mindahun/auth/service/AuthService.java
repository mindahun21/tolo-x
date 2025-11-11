package com.mindahun.auth.service;

import com.mindahun.auth.client.UserClient;
import com.mindahun.auth.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserClient userClient;

    public UserDto getUserByEmail(String email) {
        return userClient.getUserByEmail(email);
    }
}
