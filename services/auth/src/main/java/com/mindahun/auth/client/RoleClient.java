package com.mindahun.auth.client;

import com.mindahun.auth.dto.RoleDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "USER-SERVICE", contextId = "roleClient")
public interface RoleClient {
    @GetMapping("/roles/name/{name}")
    RoleDto getRoleByName(@PathVariable("name") String name);
}
