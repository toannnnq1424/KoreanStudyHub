package com.ksh.features.practice.result;

import com.ksh.features.practice.dto.PracticeDtos.ResultAnswerDistribution;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.ResultSkillPayload;

interface PracticeResultPresenter {

    boolean supports(String skill);

    Presentation present(PracticeResultContext context);

    record Presentation(
            ResultScoreSummary score,
            ResultAnswerDistribution answers,
            ResultFeedbackAvailability feedback,
            ResultSkillPayload payload
    ) {
    }
}
