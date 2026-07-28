package com.ksh.features.practice.result;

import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultDetailView;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailPayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only, fail-closed Result Detail dispatcher over the same immutable
 * attempt context used by {@link PracticeResultAssembler}.
 */
@Service
public class PracticeResultDetailAssembler {

    private final PracticeResultAssembler resultAssembler;
    private final List<PracticeResultDetailPresenter> presenters;

    public PracticeResultDetailAssembler(
            PracticeResultAssembler resultAssembler,
            List<PracticeResultDetailPresenter> presenters) {
        this.resultAssembler = resultAssembler;
        this.presenters = List.copyOf(presenters);
    }

    @Transactional(readOnly = true)
    public PracticeResultDetailView assemble(Long attemptId, Long userId, Long questionId) {
        PracticeResultContext context = resultAssembler.loadContext(attemptId, userId);
        String attemptSkill = context.attempt().getSkill();
        if (questionId != null
                && !"WRITING".equals(attemptSkill)
                && !"SPEAKING".equals(attemptSkill)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "questionId chỉ áp dụng cho Result Detail kỹ năng Viết hoặc Nói.");
        }
        PracticeAttemptResultView overview = resultAssembler.assemble(context);
        String skill = overview.identity().skill();

        List<PracticeResultDetailPresenter> matches = presenters.stream()
                .filter(presenter -> presenter.supports(skill))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Kỹ năng phải được xử lý bởi đúng một Result Detail presenter.");
        }
        ResultDetailPayload payload = matches.get(0)
                .presentDetail(context, overview, questionId);
        return new PracticeResultDetailView(overview.identity(), overview.state(), payload);
    }
}
