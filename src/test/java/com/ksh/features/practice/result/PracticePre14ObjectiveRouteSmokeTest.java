package com.ksh.features.practice.result;

import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveResultPayload;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultDetailView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in browser-route backing check for the disposable Pre-14 R/L fixture.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "openai.api-key=",
                "spring.flyway.enabled=false"
        })
@EnabledIfEnvironmentVariable(
        named = "KSH_PRE14_UI_ROUTE_SMOKE_ENABLED",
        matches = "true")
class PracticePre14ObjectiveRouteSmokeTest {

    private static final long SEED_LEARNER_ID = 4L;

    @Autowired
    private PracticeResultAssembler resultAssembler;

    @Autowired
    private PracticeResultDetailAssembler detailAssembler;

    @Test
    void readingAndListeningStableRoutesResolveTwelveReadyQuestions() {
        for (long attemptId : List.of(14100L, 14200L)) {
            PracticeAttemptResultView overview = resultAssembler.assemble(
                    attemptId, SEED_LEARNER_ID);

            assertThat(overview.payload())
                    .isInstanceOf(ObjectiveResultPayload.class);
            assertThat(overview.answers().total()).isEqualTo(12);
            assertThat(overview.feedback().state()).isEqualTo("READY");
            assertThat(overview.feedback().readyCount()).isEqualTo(12);
            assertThat(overview.feedback().totalCount()).isEqualTo(12);

            PracticeResultDetailView detail = detailAssembler.assemble(
                    attemptId, SEED_LEARNER_ID, null);
            assertThat(detail.payload())
                    .isInstanceOf(ObjectiveDetailPayload.class);
            ObjectiveDetailPayload payload =
                    (ObjectiveDetailPayload) detail.payload();
            assertThat(payload.questions()).hasSize(12);
            assertThat(payload.questions())
                    .allSatisfy(question -> {
                        assertThat(question.explanation().state())
                                .isEqualTo("READY");
                        assertThat(question.core().anchorId())
                                .isEqualTo(
                                        "objective-question-"
                                                + question.core()
                                                .questionVersionId());
                    });
        }
    }
}
