package com.ksh.KoreanStudyHub.service;

import com.ksh.KoreanStudyHub.dto.request.CreateUserRequest;
import com.ksh.KoreanStudyHub.entity.Role;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.repository.RoleRepository;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<User> getAllUsers(Pageable pageable) { return userRepository.findAll(pageable); }

    public List<User> searchUsers(String keyword) {
        return userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword);
    }

    public List<User> getUsersByRole(String roleName) {
        return userRepository.findByRoleRoleName(roleName);
    }

    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
    }

    public void createUser(CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent())
            throw new RuntimeException("Email đã tồn tại");
        Role role = roleRepository.findByRoleName(request.getRoleName())
                .orElseThrow(() -> new RuntimeException("Role không tồn tại"));
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setStatus("ACTIVE");
        userRepository.save(user);
    }

    public void toggleStatus(Long id) {
        User user = getById(id);
        user.setStatus("ACTIVE".equals(user.getStatus()) ? "INACTIVE" : "ACTIVE");
        userRepository.save(user);
    }

    public void resetPassword(Long id) {
        User user = getById(id);
        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);
    }

    public void deleteUser(Long id) { userRepository.deleteById(id); }

    public List<Role> getAllRoles() { return roleRepository.findAll(); }

    public long countByRole(String roleName) { return userRepository.countByRoleRoleName(roleName); }
    public long countTotal() { return userRepository.count(); }
    public long countActive() { return userRepository.countByStatus("ACTIVE"); }
}
