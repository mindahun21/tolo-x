package com.mindahun.auth.mapper;

import com.mindahun.auth.dto.UserDto;
import com.mindahun.auth.service.CustomUserDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String email;
    private String name;
    private String imageUrl;
    private Set<String> roles;

    public static UserResponseDto fromCustomUserDetails(CustomUserDetails userDetails) {
        UserDto user = userDetails.getUser();
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getImageUrl(),
                roleNames
        );
    }
}
