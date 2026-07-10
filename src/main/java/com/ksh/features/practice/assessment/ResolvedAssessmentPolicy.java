package com.ksh.features.practice.assessment;

public record ResolvedAssessmentPolicy(
        String programCode,
        long programVersionId,
        int programVersion,
        AssessmentSkill skill,
        AssessmentDeliveryMode deliveryMode,
        CanonicalQuestionType questionType,
        ScoringPolicyCode scoringPolicyCode,
        ProfileReference scoringProfile,
        ProfileReference promptProfile,
        ProfileReference rubricProfile
) {
}
