package com.tolox.user.services;

import com.tolox.user.models.Role;
import com.tolox.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public Optional<Role> findRoleByName(String name){
        return roleRepository.findByName(name);
    }

    public Role save(Role role){
        return roleRepository.save(role);
    }

}
