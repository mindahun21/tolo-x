package com.mindahun.auth.service;

import com.mindahun.auth.client.UserClient;
import com.mindahun.auth.dto.UserDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {
    private final UserClient userClient;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try{
            UserDto user = userClient.getUserByEmail(email);
            return new CustomUserDetails(user);

        }catch(FeignException.NotFound e){
            throw new RuntimeException("User not found");
        }
    }
}
