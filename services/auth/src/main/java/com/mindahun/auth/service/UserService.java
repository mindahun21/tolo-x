package com.mindahun.auth.service;

import com.mindahun.auth.client.RoleClient;
import com.mindahun.auth.client.UserClient;
import com.mindahun.auth.dto.RoleDto;
import com.mindahun.auth.dto.UserDto;
import com.mindahun.auth.mapper.RegistrationRequest;
import com.mindahun.auth.mapper.UserResponseDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserClient userClient;
    private final RoleClient roleClient;
    private final PasswordEncoder passwordEncoder;

    public void saveNewUser(RegistrationRequest registrationRequest) {
        try{
            RoleDto defaultRole = roleClient.getRoleByName("ROLE_USER");
            UserDto user = new UserDto();
            user.setEmail(registrationRequest.getEmail());
            user.setName(registrationRequest.getName());
            user.setProviderId("LOCAL");

            String hashedPassword = passwordEncoder.encode(registrationRequest.getPassword());

            user.setPassword(hashedPassword);
            user.setRoles(Set.of(defaultRole));
            user.setEnabled(true);
            userClient.create(user);
        } catch (FeignException.NotFound e) {
            log.error("Role not found in USER-SERVICE", e);
            throw new RuntimeException("Default role not found in USER-SERVICE");
        } catch (FeignException e) {
            log.error("Error communicating with USER-SERVICE", e);
            throw new RuntimeException("Error calling USER-SERVICE: " + e.status());
        }

    }

    public UserResponseDto currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return UserResponseDto.fromCustomUserDetails(userDetails);
    }
}
