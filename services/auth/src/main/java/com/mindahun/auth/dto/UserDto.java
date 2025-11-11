package com.mindahun.auth.dto;

import com.mindahun.auth.models.AuthProvider;
import lombok.Data;

import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String password;
    private String name;
    private Boolean enabled;
    private String imageUrl;
    private Set<RoleDto> roles;
    private AuthProvider provider;
    private String providerId;
}
