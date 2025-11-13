package com.tolox.user.services;

import com.tolox.user.dto.UserUpdateDto;
import com.tolox.user.models.User;
import com.tolox.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    public Optional<User> findUserByEmail(String email){
        return userRepository.findByEmail(email);
    }

    public Optional<User> findUserById(Long id){
        return userRepository.findById(id);
    }

    @Transactional
    public User update(UserUpdateDto user) {
        User userToUpdate = userRepository.findByEmail(user.getEmail()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: "+user.getEmail()));
        if(user.getName() != null) userToUpdate.setName(user.getName());
        if(user.getImageUrl() != null) userToUpdate.setImageUrl(user.getImageUrl());

        return userRepository.save(userToUpdate);
    }

    @Transactional
    public User create(User user) {
        boolean exists = userRepository.existsByEmail(user.getEmail());
        if (exists) {
            return userRepository.findByEmail(user.getEmail()).get();
        }
        return userRepository.save(user);
    }

    public List<User> findAll(){
        return userRepository.findAll();
    }

}
