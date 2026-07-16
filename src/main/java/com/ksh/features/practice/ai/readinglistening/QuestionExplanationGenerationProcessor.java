package com.ksh.features.practice.ai.readinglistening;

import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.ai.media.AiQuestionImageResolver;
import com.ksh.features.practice.ai.readinglistening.ExplanationArtifactInput.MediaDescriptor;
import com.ksh.features.practice.ai.readinglistening.ExplanationInputFactory.RuntimeMedia;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationTaskTransactions.ClaimedTask;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionExplanationGenerationProcessor {

    private static final Logger log = LoggerFactory.getLogger(QuestionExplanationGenerationProcessor.class);

    private final QuestionExplanationGenerationTaskRepository taskRepository;
    private final QuestionExplanationTaskTransactions transactions;
    private final QuestionExplanationWorkLoader workLoader;
    private final AiQuestionImageResolver imageResolver;
    private final ReadingListeningExplanationClient client;
    private final String workerId = "rl-explanation-" + UUID.randomUUID();

    public QuestionExplanationGenerationProcessor(
            QuestionExplanationGenerationTaskRepository taskRepository,
            QuestionExplanationTaskTransactions transactions,
            QuestionExplanationWorkLoader workLoader,
            AiQuestionImageResolver imageResolver,
            ReadingListeningExplanationClient client) {
        this.taskRepository = taskRepository;
        this.transactions = transactions;
        this.workLoader = workLoader;
        this.imageResolver = imageResolver;
        this.client = client;
    }

    public int processDue(int limit) {
        if (limit <= 0) return 0;
        List<Long> ids = taskRepository.findClaimableIds(
                LocalDateTime.now(), PageRequest.of(0, Math.min(limit, 100)));
        int claimed = 0;
        for (Long id : ids) {
            String claimOwner = workerId + ":" + UUID.randomUUID();
            ClaimedTask task = transactions.claim(id, claimOwner, LocalDateTime.now()).orElse(null);
            if (task == null) continue;
            claimed++;
            process(task);
        }
        return claimed;
    }

    private void process(ClaimedTask task) {
        try {
            QuestionExplanationWorkLoader.ExplanationWork work = workLoader.load(task);
            List<ExplanationImageEvidence> images = resolveImages(work);
            String explanation = client.generate(work.prepared().context(), images);
            if (!transactions.complete(task, explanation, LocalDateTime.now())) {
                log.info("[ReadingListeningAI] Discarded stale completion taskId={}", task.taskId());
            }
        } catch (ExplanationProviderException exception) {
            transactions.fail(
                    task,
                    exception.category(),
                    exception.getMessage(),
                    exception.retryable(),
                    LocalDateTime.now());
        } catch (Exception exception) {
            transactions.fail(
                    task,
                    "GENERATION_INTERNAL_ERROR",
                    "Explanation generation failed before completion.",
                    true,
                    LocalDateTime.now());
            log.warn("[ReadingListeningAI] Internal generation failure taskId={} exception={}",
                    task.taskId(), exception.getClass().getSimpleName());
        }
    }

    private List<ExplanationImageEvidence> resolveImages(
            QuestionExplanationWorkLoader.ExplanationWork work) {
        Map<String, MediaDescriptor> descriptors = work.prepared().input().media().stream()
                .collect(Collectors.toMap(MediaDescriptor::role, Function.identity()));
        List<ExplanationImageEvidence> images = new ArrayList<>();
        for (RuntimeMedia media : work.prepared().runtimeMedia()) {
            if (!"IMAGE".equals(media.kind())) continue;
            AiImageEvidence evidence = imageResolver.resolvePublishedVersion(
                            media.reference(), work.publishedVersionId())
                    .orElseThrow(() -> new ExplanationProviderException(
                            "MEDIA_RESOLUTION_FAILED",
                            "Verified image evidence could not be loaded.",
                            true));
            MediaDescriptor descriptor = descriptors.get(media.role());
            if (descriptor == null || !evidence.sha256().equalsIgnoreCase(descriptor.sha256())) {
                throw new ExplanationProviderException(
                        "MEDIA_DIGEST_MISMATCH",
                        "Loaded image digest does not match the immutable explanation input.",
                        false);
            }
            images.add(new ExplanationImageEvidence(media.role(), evidence));
        }
        return List.copyOf(images);
    }
}
