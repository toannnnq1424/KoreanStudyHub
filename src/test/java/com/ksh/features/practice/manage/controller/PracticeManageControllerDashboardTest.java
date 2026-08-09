package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRecoveryQueryService;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService;
import com.ksh.features.practice.governance.PracticeLifecycleService;
import com.ksh.features.practice.manage.service.PracticeDraftService;
import com.ksh.features.practice.manage.service.PracticeRevisionService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ExtendedModelMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeManageControllerDashboardTest {

    @Test
    void dashboardUsesFixedBulkIdentityQueriesForLargeCatalog() {
        PracticeSetRepository sets = mock(PracticeSetRepository.class);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        UserRepository users = mock(UserRepository.class);
        PracticeDraftService draftService = mock(PracticeDraftService.class);
        PracticeManageController controller = new PracticeManageController(
                sets,
                drafts,
                users,
                draftService,
                mock(PracticeRevisionService.class),
                mock(PracticePublishedVersionRepository.class),
                mock(PracticeLifecycleService.class),
                mock(QuestionExplanationRecoveryQueryService.class),
                mock(QuestionExplanationRetryService.class));
        KshUserDetails actor = mock(KshUserDetails.class);
        when(actor.getId()).thenReturn(7L);
        when(actor.getFullName()).thenReturn("Giảng viên A");

        List<PracticeSet> owned = LongStream.rangeClosed(1, 120)
                .mapToObj(id -> set(id, 7L, "PUBLISHED"))
                .toList();
        when(sets.findByCreatedByOrderByCreatedAtDesc(7L))
                .thenReturn(owned);
        when(drafts.findByOwnerIdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.dashboard(null, actor, model);

        assertThat(view).isEqualTo("practice/manage/dashboard");
        assertThat(model.get("sets")).isEqualTo(owned);
        assertThat(model.get("publishedCount")).isEqualTo(120L);
        assertThat(model).doesNotContainKeys(
                "sharedSets", "collaboratorsBySet", "collaboratorEmailsMap");

        verify(sets, never()).findAllById(any());
        verify(users, never()).findAllById(any());
        verify(users, never()).findById(any());
    }

    @Test
    void controllerRetainsExactLecturerAuthorizationBoundary() {
        PreAuthorize boundary =
                PracticeManageController.class.getAnnotation(PreAuthorize.class);
        assertThat(boundary.value()).isEqualTo(Roles.PREAUTH_LECTURER);
    }

    private static PracticeSet set(long id, long ownerId, String status) {
        PracticeSet set = mock(PracticeSet.class);
        when(set.getId()).thenReturn(id);
        when(set.getCreatedBy()).thenReturn(ownerId);
        when(set.getStatus()).thenReturn(status);
        return set;
    }

    private static User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getFullName()).thenReturn("User " + id);
        when(user.getEmail()).thenReturn("user-" + id + "@example.test");
        return user;
    }
}
