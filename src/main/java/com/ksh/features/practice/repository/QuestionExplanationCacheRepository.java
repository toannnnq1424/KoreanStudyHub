package com.ksh.features.practice.repository;

import com.ksh.entities.QuestionExplanationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QuestionExplanationCacheRepository extends JpaRepository<QuestionExplanationCache, Long> {
    Optional<QuestionExplanationCache> findByQuestionIdAndQuestionHashAndCorrectAnswer(
            Long questionId, String questionHash, String correctAnswer
    );
}
