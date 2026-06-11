package com.ksh.KoreanStudyHub.config;

import com.ksh.KoreanStudyHub.entity.Role;
import com.ksh.KoreanStudyHub.entity.enums.RoleName;
import com.ksh.KoreanStudyHub.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;

    @Bean
    public CommandLineRunner initData() {

        return args -> {

            if (roleRepository.findByRoleName(RoleName.USER).isEmpty()) {

                Role role = new Role();
                role.setRoleName(RoleName.USER);

                roleRepository.save(role);
            }

            if (roleRepository.findByRoleName(RoleName.ADMIN).isEmpty()) {

                Role role = new Role();
                role.setRoleName(RoleName.ADMIN);

                roleRepository.save(role);
            }

        };
    }
}