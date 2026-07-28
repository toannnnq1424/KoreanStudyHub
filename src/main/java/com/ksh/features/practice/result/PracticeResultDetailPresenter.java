package com.ksh.features.practice.result;

import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPayload;

interface PracticeResultDetailPresenter {

    boolean supports(String skill);

    ResultDetailPayload presentDetail(
            PracticeResultContext context,
            PracticeAttemptResultView overview,
            Long questionId);
}
