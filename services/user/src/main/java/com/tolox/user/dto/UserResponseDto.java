package com.tolox.user.dto;

import com.tolox.user.models.Role;
import com.tolox.user.models.User;
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

    public static UserResponseDto fromUser(User user){
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .enabled(user.isEnabled())
                .imageUrl(user.getImageUrl())
                .roles(user.getRoles())
                .build();
    }
}
