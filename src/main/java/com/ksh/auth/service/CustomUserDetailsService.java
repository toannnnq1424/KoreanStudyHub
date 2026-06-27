package com.ksh.auth.service;

import com.ksh.auth.entity.User;
import com.ksh.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nap thong tin dang nhap tu DB cho Spring Security.
 *
 * <p>Trong ksh, nguoi dung dang nhap bang <b>email</b> nen tham so
 * {@code username} chinh la email. Tra ve {@link kshUserDetails} — principal
 * nay lo them {@code fullName} de template dung chung accessor voi luong OAuth
 * (xem {@link CustomOidcUserPrincipal}).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Khong tim thay tai khoan: " + email));

        // kshUserDetails tu map role -> ROLE_<name> va expose isEnabled()/isAccountNonLocked()
        // de Spring Security nem DisabledException / LockedException tuong ung.
        return new KshUserDetails(user);
    }
}