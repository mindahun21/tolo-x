package com.tolox.user.dto;

import com.tolox.user.models.Role;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class UserResponseDto {
    private Long id;
    private String email;
    private String name;
    private Boolean enabled;
    private String imageUrl;
    private Set<Role> roles;
}
