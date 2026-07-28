package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRecoveryQueryService;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService;
import com.ksh.features.practice.governance.PracticeCollaborationService;
import com.ksh.features.practice.governance.PracticeLifecycleService;
import com.ksh.features.practice.manage.service.PracticeDraftService;
import com.ksh.features.practice.manage.service.PracticeRevisionService;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
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
        PracticeAuthoringCollaborationRepository collaborations =
                mock(PracticeAuthoringCollaborationRepository.class);
        PracticeManageController controller = new PracticeManageController(
                sets,
                drafts,
                users,
                draftService,
                mock(PracticeRevisionService.class),
                mock(PracticePublishedVersionRepository.class),
                collaborations,
                mock(PracticeLifecycleService.class),
                mock(PracticeCollaborationService.class),
                mock(QuestionExplanationRecoveryQueryService.class),
                mock(QuestionExplanationRetryService.class));
        KshUserDetails actor = mock(KshUserDetails.class);
        when(actor.getId()).thenReturn(7L);

        List<PracticeSet> owned = LongStream.rangeClosed(1, 120)
                .mapToObj(id -> set(id, 7L, "PUBLISHED"))
                .toList();
        List<PracticeAuthoringCollaboration> ownedGrants =
                new ArrayList<>();
        for (long id = 1; id <= 120; id++) {
            ownedGrants.add(new PracticeAuthoringCollaboration(id, 1_000L + id));
        }
        PracticeAuthoringCollaboration sharedGrant =
                new PracticeAuthoringCollaboration(900L, 7L);
        PracticeSet shared = set(900L, 88L, "PUBLISHED");
        when(sets.findByCreatedByOrderByCreatedAtDesc(7L))
                .thenReturn(owned);
        when(collaborations.findBySetIdInAndRevokedAtIsNull(any()))
                .thenReturn(ownedGrants);
        when(collaborations
                .findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(7L))
                .thenReturn(List.of(sharedGrant));
        when(sets.findAllById(any())).thenReturn(List.of(shared));
        when(drafts.findByOwnerIdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of());
        when(users.findAllById(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(PracticeManageControllerDashboardTest::user)
                    .toList();
        });
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.dashboard(null, actor, model);

        assertThat(view).isEqualTo("practice/manage/dashboard");
        assertThat(model.get("sets")).isEqualTo(owned);
        assertThat(model.get("sharedSets")).isEqualTo(List.of(shared));
        assertThat(model.get("publishedCount")).isEqualTo(120L);
        assertThat((java.util.Map<?, ?>) model.get("collaboratorsBySet"))
                .hasSize(120);

        verify(collaborations, times(1))
                .findBySetIdInAndRevokedAtIsNull(any());
        verify(collaborations, times(1))
                .findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(7L);
        verify(sets, times(1)).findAllById(any());
        verify(users, times(1)).findAllById(any());
        verify(users, never()).findById(any());
        verify(collaborations, never())
                .findBySetIdAndRevokedAtIsNull(any());
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
