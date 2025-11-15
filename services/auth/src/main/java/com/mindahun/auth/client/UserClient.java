package com.mindahun.auth.client;

import com.mindahun.auth.config.FeignInternalAuthConfig;
import com.mindahun.auth.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "USER-SERVICE", contextId = "userClient", configuration = FeignInternalAuthConfig.class)
public interface UserClient {
    @GetMapping("/users/email/{email}")
    UserDto getUserByEmail(@PathVariable("email") String email);

    @PatchMapping("/users")
    UserDto update(@RequestBody UserDto userDto);

    @PostMapping("/users")
    UserDto create(@RequestBody UserDto userDto);
}
