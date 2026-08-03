package com.ksh.features.questionbank.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.features.questionbank.entity.QuestionBankItem;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionBankReviewServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LeaderDepartmentResolver resolver = mock(LeaderDepartmentResolver.class);
    private final QuestionBankAccessPolicy accessPolicy = new QuestionBankAccessPolicy(resolver);
    private final QuestionBankItemRepository itemRepository = mock(QuestionBankItemRepository.class);
    private final QuestionBankReviewService service = new QuestionBankReviewService(
            userRepository, accessPolicy, itemRepository);
    private User leader;
    private QuestionBankItem item;

    @BeforeEach
    void setUp() {
        leader = mock(User.class);
        when(leader.getId()).thenReturn(30L);
        when(leader.getRole()).thenReturn(Role.LEADER);
        Department subject = new Department("Tiếng Hàn 3.1.1", "KOR311", null, true);
        ReflectionTestUtils.setField(subject, "id", 5L);
        subject.assignLeader(30L);
        when(userRepository.findById(30L)).thenReturn(Optional.of(leader));
        when(resolver.resolve(30L)).thenReturn(Optional.of(subject));
        item = new QuestionBankItem(5L, 20L, QuestionBankItem.TYPE_MCQ,
                QuestionBankItem.STATUS_REVIEW, "<p>Question</p>", null);
        ReflectionTestUtils.setField(item, "id", 10L);
        when(itemRepository.findByIdAndSubjectId(10L, 5L)).thenReturn(Optional.of(item));
    }

    @Test
    void leader_approves_review_item_in_own_subject() {
        service.approve(30L, 10L);

        assertThat(item.getWorkflowStatus()).isEqualTo(QuestionBankItem.STATUS_APPROVED);
        assertThat(item.getReviewedBy()).isEqualTo(30L);
        assertThat(item.getApprovedAt()).isNotNull();
    }

    @Test
    void invalid_transition_is_rejected() {
        item.transitionWorkflow(QuestionBankItem.STATUS_DRAFT, null, null, null, null);

        assertThatThrownBy(() -> service.approve(30L, 10L))
                .isInstanceOf(QuestionBankValidationException.class);
    }

    @Test
    void leader_cannot_review_cross_subject_item() {
        when(itemRepository.findByIdAndSubjectId(10L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(30L, 10L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
