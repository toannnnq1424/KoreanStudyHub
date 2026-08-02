package com.ksh.features.profile.controller;

import com.ksh.entities.User;
import com.ksh.features.profile.service.ProfileService;
import com.ksh.features.storage.profile.StorageProfileException;
import com.ksh.features.upload.AvatarStorageService;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static com.ksh.common.IConstant.MSG_STORAGE_PROFILE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private ProfileController controller;

    @Test
    void unavailableGeneralUploadsFailsClosedWithoutEscapingAs500() throws Exception {
        KshUserDetails principal = org.mockito.Mockito.mock(KshUserDetails.class);
        User user = org.mockito.Mockito.mock(User.class);
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[]{1, 2, 3});
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(principal.getId()).thenReturn(42L);
        when(profileService.getCurrentUser(42L)).thenReturn(user);
        when(avatarStorageService.store(file))
                .thenThrow(new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE"));

        String view = controller.uploadAvatar(file, principal, redirect);

        assertThat(view).isEqualTo("redirect:/profile");
        assertThat(redirect.getFlashAttributes().get("avatarError"))
                .isEqualTo(MSG_STORAGE_PROFILE_UNAVAILABLE);
        verify(profileService, never()).updateAvatar(user, "/uploads/avatars/avatar.png");
    }

    @Test
    void availableGeneralUploadsPersistsReturnedAvatarUrl() throws Exception {
        KshUserDetails principal = org.mockito.Mockito.mock(KshUserDetails.class);
        User user = org.mockito.Mockito.mock(User.class);
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[]{1, 2, 3});
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(principal.getId()).thenReturn(42L);
        when(profileService.getCurrentUser(42L)).thenReturn(user);
        when(avatarStorageService.store(file)).thenReturn("/uploads/avatars/avatar.png");

        String view = controller.uploadAvatar(file, principal, redirect);

        assertThat(view).isEqualTo("redirect:/profile");
        assertThat(redirect.getFlashAttributes().get("avatarUpdated")).isEqualTo(true);
        verify(profileService).updateAvatar(user, "/uploads/avatars/avatar.png");
        verify(principal).updateAvatarUrl("/uploads/avatars/avatar.png");
    }
}
