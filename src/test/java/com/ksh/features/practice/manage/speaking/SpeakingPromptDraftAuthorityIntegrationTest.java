package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class SpeakingPromptDraftAuthorityIntegrationTest {

    @Autowired
    private PracticeDraftRepository drafts;

    @Autowired
    private SpeakingPromptDraftAuthority authority;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private PracticeAuthorizationService authorization;

    @Test
    void authorizedLockedMutationFlushesThroughJpaWithExactQuestionIdentity() throws Exception {
        PracticeDraft draft = drafts.saveAndFlush(new PracticeDraft(
                "Speaking integration", "", "PRIVATE", null, "DRAFT", 710L,
                "{\"sections\":[{\"skill\":\"SPEAKING\",\"groups\":[{\"questions\":["
                        + "{\"clientId\":\"q-speaking\",\"questionType\":\"SPEAKING\","
                        + "\"prompt\":\"기존 질문\"}]}]}]}"));
        when(authorization.requireDraft(draft.getId(), 710L, PracticeAction.EDIT))
                .thenReturn(new PracticeAuthorizationService.Decision(710L));

        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                authority.authorizeAndLock(
                        draft.getId(), "q-speaking", 710L, PracticeAction.EDIT);
        authority.replacePromptText(authorized, "통합 테스트 질문");
        drafts.saveAndFlush(authorized.draft());

        PracticeDraft reloaded = drafts.findById(draft.getId()).orElseThrow();
        assertThat(mapper.readTree(reloaded.getDraftJson())
                .path("sections").get(0).path("groups").get(0)
                .path("questions").get(0).path("prompt").asText())
                .isEqualTo("통합 테스트 질문");
    }
}
