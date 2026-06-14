package com.ksh.KoreanStudyHub.config;

import com.ksh.KoreanStudyHub.entity.Role;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.entity.enums.RoleName;
import com.ksh.KoreanStudyHub.repository.RoleRepository;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {

            // Tạo các role nếu chưa có
            if (roleRepository.findByRoleName(RoleName.STUDENT).isEmpty()) {
                Role role = new Role();
                role.setRoleName(RoleName.STUDENT);
                roleRepository.save(role);
            }

            if (roleRepository.findByRoleName(RoleName.TEACHER).isEmpty()) {
                Role role = new Role();
                role.setRoleName(RoleName.TEACHER);
                roleRepository.save(role);
            }

            if (roleRepository.findByRoleName(RoleName.SUBJECT_LEADER).isEmpty()) {
                Role role = new Role();
                role.setRoleName(RoleName.SUBJECT_LEADER);
                roleRepository.save(role);
            }

            if (roleRepository.findByRoleName(RoleName.ADMIN).isEmpty()) {
                Role role = new Role();
                role.setRoleName(RoleName.ADMIN);
                roleRepository.save(role);
            }

            // Tạo tài khoản admin mặc định nếu chưa có
            if (userRepository.findByEmail("admin@ksh.com").isEmpty()) {
                Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN).get();

                User admin = new User();
                admin.setFullName("Administrator");
                admin.setEmail("admin@ksh.com");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole(adminRole);
                admin.setStatus("ACTIVE");

                userRepository.save(admin);
            }
        };
    }
}
