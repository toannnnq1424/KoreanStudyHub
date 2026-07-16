package com.ksh.features.practice.ai.writing;

import com.ksh.entities.WritingTaskType;
import org.springframework.stereotype.Component;

@Component
public class WritingTaskResolver {

    public String resolve(WritingTaskType explicitTaskType, String prompt) {
        if (explicitTaskType == null) {
            return WritingRuleEngine.detectTaskType(prompt);
        }
        return explicitTaskType.name();
    }
}
