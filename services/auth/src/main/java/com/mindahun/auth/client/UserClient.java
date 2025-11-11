package com.mindahun.auth.client;

import com.mindahun.auth.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "USER-SERVICE")
public interface UserClient {
    @GetMapping("/users/email/{email}")
    UserDto getUserByEmail(@PathVariable("email") String email);
}
