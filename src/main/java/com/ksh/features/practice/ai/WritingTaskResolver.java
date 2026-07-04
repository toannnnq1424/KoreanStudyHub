package com.ksh.features.practice.ai;

import com.ksh.entities.WritingTaskType;
import org.springframework.stereotype.Component;

@Component
public class WritingTaskResolver {

    public String resolve(WritingTaskType explicitTaskType, String prompt) {
        if (explicitTaskType == null) {
            return WritingRuleEngine.detectTaskType(prompt);
        }
        return switch (explicitTaskType) {
            case Q51, Q52 -> "Q51_52";
            case Q53 -> "Q53";
            case Q54 -> "Q54";
            case GENERAL -> "GENERAL";
        };
    }
}
