package com.tolox.user.services;

import com.tolox.user.models.ERole;
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
        try{
            ERole role = ERole.valueOf(name.toUpperCase());
            return roleRepository.findByName(role);
        }catch (IllegalArgumentException e){
            return Optional.empty();
        }
    }

    public Role save(Role role){
        return roleRepository.save(role);
    }

}
