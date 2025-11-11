package com.mindahun.auth.service;

import com.mindahun.auth.mapper.RegistrationRequest;
import com.mindahun.auth.mapper.UserResponseDto;
import com.mindahun.auth.models.Role;
import com.mindahun.auth.models.User;
import com.mindahun.auth.repository.RoleRepository;
import com.mindahun.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {


    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public void saveNewUser(RegistrationRequest registrationRequest) {
            Role defaultRole = roleRepository.findByName("ROLE_USER").orElseThrow(() -> new RuntimeException("Default role not found"));
            User user = new User();
            user.setEmail(registrationRequest.getEmail());
            user.setName(registrationRequest.getName());
            user.setProviderId("LOCAL");

            String hashedPassword = passwordEncoder.encode(registrationRequest.getPassword());

            user.setPassword(hashedPassword);
            user.setRoles(Set.of(defaultRole));
            user.setEnabled(true);
            userRepository.save(user);
    }

    public UserResponseDto currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return UserResponseDto.fromCustomUserDetails(userDetails);
    }
}
