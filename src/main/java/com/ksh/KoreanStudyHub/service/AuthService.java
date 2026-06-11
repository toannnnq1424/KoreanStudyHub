package com.ksh.KoreanStudyHub.service;

import com.ksh.KoreanStudyHub.dto.request.RegisterRequest;
import com.ksh.KoreanStudyHub.entity.Role;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.entity.enums.RoleName;
import com.ksh.KoreanStudyHub.repository.RoleRepository;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public void register(RegisterRequest request) {

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        Role userRole = roleRepository
                .findByRoleName(RoleName.USER)
                .orElseThrow(() ->
                        new RuntimeException("Role USER not found"));

        User user = new User();

        user.setUsername(request.getUsername());

        user.setEmail(request.getEmail());

        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        user.setRole(userRole);

        user.setIsActive(true);

        userRepository.save(user);
    }
}