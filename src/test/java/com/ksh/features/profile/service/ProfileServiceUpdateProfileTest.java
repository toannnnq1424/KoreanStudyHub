package com.ksh.features.profile.service;

import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceUpdateProfileTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileService service = new ProfileService(userRepository);

    @Test
    void updateProfile_withValidInput_updatesAndReturnsSavedUser() {
        User user = user("old.name@ksh.test");

        when(userRepository.save(user)).thenReturn(user);

        User result = service.updateProfile(
                user,
                "Nguyen Van A",
                "Korean learner",
                "0912345678"
        );

        assertThat(result).isSameAs(user);
        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getBio()).isEqualTo("Korean learner");
        assertThat(result.getPhone()).isEqualTo("0912345678");
    }

    @Test
    void updateProfile_withBlankOptionalFields_storesBioAndPhoneAsNull() {
        User user = user("blank.profile@ksh.test");

        when(userRepository.save(user)).thenReturn(user);

        User result = service.updateProfile(
                user,
                "Nguyen Van A",
                "",
                " "
        );

        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getBio()).isNull();
        assertThat(result.getPhone()).isNull();
    }

    @Test
    void updateProfile_whenSaveFails_throwsException() {
        User user = user("save.fail@ksh.test");

        when(userRepository.save(user))
                .thenThrow(new RuntimeException("database error"));

        assertThatThrownBy(() -> service.updateProfile(
                user,
                "Nguyen Van A",
                "Updated bio",
                "0912345678"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database error");
    }

    private static User user(String email) {
        return UserFactory.newAdminCreated(
                email,
                "encoded-password",
                "Old Name",
                Role.STUDENT,
                true,
                null,
                null
        );
    }
}