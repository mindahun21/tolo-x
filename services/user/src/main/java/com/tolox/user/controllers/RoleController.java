package com.tolox.user.controllers;

import com.tolox.user.models.Role;
import com.tolox.user.services.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {


    private final RoleService roleService;

    @GetMapping("/name/{name}")
    public ResponseEntity<Role> findRoleByName(@PathVariable String name){
        return roleService.findRoleByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping()
    public Role save(@RequestBody Role role){
        return roleService.save(role);
    }
}
