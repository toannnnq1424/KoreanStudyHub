package com.ksh.features.practice.result;

import com.ksh.entities.PracticeAttempt;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.service.PracticeVersionSnapshot;

import java.util.Map;

record PracticeResultContext(
        PracticeAttempt attempt,
        PracticeVersionSnapshot snapshot,
        Map<String, String> answers,
        ResultScoreSummary score
) {
}
