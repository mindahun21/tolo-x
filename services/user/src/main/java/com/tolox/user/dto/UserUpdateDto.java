package com.tolox.user.dto;

import com.tolox.user.models.Role;
import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateDto {
    private String email;
    private String name;
    private Boolean enabled;
    private String imageUrl;
    private Set<Role> roles;
    private String providerId;
}
