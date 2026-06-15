package com.ksh.KoreanStudyHub.security;

import com.ksh.KoreanStudyHub.entity.Role;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.entity.enums.RoleName;
import com.ksh.KoreanStudyHub.repository.RoleRepository;
import com.ksh.KoreanStudyHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        
        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from Google provider");
        }
        
        if (name == null) {
            name = email;
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            // If the user logs in via Google but their provider was LOCAL, or null, we can set provider to GOOGLE
            // (or respect their local password but also allow Google login). Let's set provider to GOOGLE if it's not set.
            boolean updated = false;
            if (user.getProvider() == null || user.getProvider().equals("LOCAL")) {
                user.setProvider("GOOGLE");
                updated = true;
            }
            if (user.getAvatar() == null && picture != null) {
                user.setAvatar(picture);
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
            }
        } else {
            user = new User();
            user.setEmail(email);
            user.setFullName(name);
            user.setAvatar(picture);
            user.setProvider("GOOGLE");
            user.setStatus("ACTIVE");
            user.setFailedAttemptCount(0);
            
            Role studentRole = roleRepository.findByRoleName(RoleName.STUDENT)
                    .orElseThrow(() -> new OAuth2AuthenticationException("ROLE_STUDENT not found in database"));
            user.setRole(studentRole);
            
            userRepository.save(user);
        }

        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }
}
