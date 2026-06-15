package com.ksh.KoreanStudyHub.security;

import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomLoginFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("username"); // Spring Security uses "username" for the login field by default

        if (email != null && !email.isEmpty()) {
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                if (user.getLockTime() == null) {
                    user.setFailedAttemptCount(user.getFailedAttemptCount() + 1);
                    if (user.getFailedAttemptCount() >= 5) {
                        user.setLockTime(LocalDateTime.now());
                    }
                    userRepository.save(user);
                }
            }
        }

        if (exception instanceof LockedException) {
            response.sendRedirect("/login?error=locked");
        } else {
            response.sendRedirect("/login?error=invalid");
        }
    }
}
