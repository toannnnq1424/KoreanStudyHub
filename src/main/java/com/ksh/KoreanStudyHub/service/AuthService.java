package com.ksh.KoreanStudyHub.service;

import com.ksh.KoreanStudyHub.dto.request.*;
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
        if (!request.getPassword().equals(request.getConfirmPassword()))
            throw new RuntimeException("Mật khẩu xác nhận không khớp");
        if (userRepository.findByEmail(request.getEmail()).isPresent())
            throw new RuntimeException("Email đã được sử dụng");

        Role studentRole = roleRepository.findByRoleName(RoleName.STUDENT)
                .orElseThrow(() -> new RuntimeException("Role không tồn tại"));

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(studentRole);
        user.setStatus("ACTIVE");
        userRepository.save(user);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword()))
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        if (!request.getNewPassword().equals(request.getConfirmPassword()))
            throw new RuntimeException("Mật khẩu xác nhận không khớp");
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public void updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        user.setFullName(request.getFullName());
        if (request.getAvatar() != null && !request.getAvatar().isBlank())
            user.setAvatar(request.getAvatar());
        userRepository.save(user);
    }
}
