package com.mindahun.auth.service;

import com.mindahun.auth.client.RoleClient;
import com.mindahun.auth.client.UserClient;
import com.mindahun.auth.config.PropertiesConfig;
import com.mindahun.auth.dto.RoleDto;
import com.mindahun.auth.dto.UserDto;
import com.mindahun.auth.mapper.LoginRequest;
import com.mindahun.auth.mapper.RegistrationRequest;
import com.mindahun.auth.mapper.UserResponseDto;
import com.mindahun.auth.utils.JwtUtil;
import feign.FeignException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserClient userClient;
    private final RoleClient roleClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final PropertiesConfig propertiesConfig;




    public void saveNewUser(RegistrationRequest registrationRequest) {
        try{
            RoleDto defaultRole = roleClient.getRoleByName("USER");
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

    public Map<String, Object> login(String email, String password, String clientType, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtil.generateToken(userDetails);

        Map<String, Object> result = new HashMap<>();

        if ("web".equalsIgnoreCase(clientType)) {
            Cookie cookie = new Cookie("access_token", accessToken);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge((int) propertiesConfig.getTokenExpirationMsec() / 1000);
            response.addCookie(cookie);
            result.put("message", "login successful");
        } else {
            String refreshToken = jwtUtil.generateRefreshToken(userDetails);
            result.put("token", accessToken);
            result.put("refresh_token", refreshToken);
        }

        return result;
    }

    public UserResponseDto currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return UserResponseDto.fromCustomUserDetails(userDetails);
    }
}
