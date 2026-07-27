package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeakingPromptAutosaveAuthorityMergerTest {

    @Test
    void genericAutosaveCannotReplaceManagedPromptOrAuthoringOptions()
            throws Exception {
        SpeakingPromptSourceRepository sources =
                mock(SpeakingPromptSourceRepository.class);
        SpeakingPromptSource source = mock(SpeakingPromptSource.class);
        when(source.getQuestionClientId()).thenReturn("speaking-a");
        when(sources.findByDraftId(91L)).thenReturn(List.of(source));
        ObjectMapper mapper = new ObjectMapper();
        SpeakingPromptAutosaveAuthorityMerger merger =
                new SpeakingPromptAutosaveAuthorityMerger(sources, mapper);
        String persisted = """
                {"sections":[{"skill":"SPEAKING","groups":[{"questions":[{
                  "clientId":"speaking-a","questionType":"SPEAKING",
                  "prompt":"권한 있는 문장",
                  "speakingPromptAuthoring":{"inputType":"manual_text",
                    "ttsEnabled":true,"voiceCode":"approved","speed":1,
                    "outputFormat":"mp3"},
                  "prepTimeSeconds":30
                }]}]}]}
                """;
        String submitted = """
                {"sections":[{"skill":"SPEAKING","groups":[{"questions":[{
                  "clientId":"speaking-a","questionType":"SPEAKING",
                  "prompt":"stale writer",
                  "speakingPromptAuthoring":{"inputType":"audio_upload",
                    "ttsEnabled":false},
                  "prepTimeSeconds":45
                }]}]}]}
                """;

        JsonNode merged = mapper.readTree(
                merger.preserveAcceptedAuthority(
                        91L, persisted, submitted));
        JsonNode question = merged.path("sections").get(0)
                .path("groups").get(0)
                .path("questions").get(0);

        assertThat(question.path("prompt").asText())
                .isEqualTo("권한 있는 문장");
        assertThat(question.path("speakingPromptAuthoring")
                .path("inputType").asText()).isEqualTo("manual_text");
        assertThat(question.path("prepTimeSeconds").asInt()).isEqualTo(45);
    }

    @Test
    void managedClientIdCannotBeRecastAsAnotherQuestionMode() {
        SpeakingPromptSourceRepository sources =
                mock(SpeakingPromptSourceRepository.class);
        SpeakingPromptSource source = mock(SpeakingPromptSource.class);
        when(source.getQuestionClientId()).thenReturn("speaking-a");
        when(sources.findByDraftId(91L)).thenReturn(List.of(source));
        SpeakingPromptAutosaveAuthorityMerger merger =
                new SpeakingPromptAutosaveAuthorityMerger(
                        sources, new ObjectMapper());
        String persisted = """
                {"sections":[{"groups":[{"questions":[{
                  "clientId":"speaking-a","questionType":"SPEAKING",
                  "prompt":"권한 있는 문장"
                }]}]}]}
                """;
        String submitted = """
                {"sections":[{"groups":[{"questions":[{
                  "clientId":"speaking-a","questionType":"SINGLE_CHOICE",
                  "prompt":"stale writer"
                }]}]}]}
                """;

        assertThatThrownBy(() -> merger.preserveAcceptedAuthority(
                91L, persisted, submitted))
                .isInstanceOf(SpeakingPromptAuthoringConflictException.class);
    }
}
