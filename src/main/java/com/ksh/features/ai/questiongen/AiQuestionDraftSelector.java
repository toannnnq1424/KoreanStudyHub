package com.ksh.features.ai.questiongen;

import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftOption;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftQuestion;
import com.ksh.features.tests.dto.LecturerTestDtos.OptionForm;
import com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure mapping from selected preview rows to the existing exam-writer contract. */
final class AiQuestionDraftSelector {

    private static final BigDecimal DEFAULT_POINTS = BigDecimal.ONE;
    private static final String MSG_INVALID_SELECTION =
            "Lựa chọn câu hỏi không hợp lệ, vui lòng sinh lại";

    private AiQuestionDraftSelector() {
    }

    static List<QuestionForm> select(List<DraftQuestion> drafts, List<Integer> indexes) {
        if (drafts == null || indexes == null || indexes.isEmpty()) {
            return List.of();
        }
        List<QuestionForm> forms = new ArrayList<>();
        LinkedHashSet<Integer> uniqueIndexes = new LinkedHashSet<>(indexes);
        if (uniqueIndexes.contains(null)) {
            throw new IllegalArgumentException(MSG_INVALID_SELECTION);
        }
        for (Integer index : uniqueIndexes) {
            if (index == null || index < 0 || index >= drafts.size()) {
                throw new IllegalArgumentException(MSG_INVALID_SELECTION);
            }
            DraftQuestion draft = drafts.get(index);
            List<OptionForm> options = new ArrayList<>();
            for (DraftOption option : draft.options()) {
                options.add(new OptionForm(null, option.content(), option.correct()));
            }
            forms.add(new QuestionForm(null, draft.type(), draft.content(),
                    draft.explanation(), DEFAULT_POINTS, List.copyOf(options)));
        }
        return List.copyOf(forms);
    }
}
