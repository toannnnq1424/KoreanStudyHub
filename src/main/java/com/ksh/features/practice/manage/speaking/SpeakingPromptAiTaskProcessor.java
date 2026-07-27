package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.manage.speaking.SpeakingPromptAssetService.StoredGeneratedCandidate;
import com.ksh.features.practice.manage.speaking.SpeakingPromptTaskTransactions.ClaimedTask;
import com.ksh.features.practice.manage.speaking.SpeakingPromptWorkLoader.LoadedWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SpeakingPromptAiTaskProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(SpeakingPromptAiTaskProcessor.class);

    private final SpeakingPromptAiTaskRepository taskRepository;
    private final SpeakingPromptTaskTransactions transactions;
    private final SpeakingPromptWorkLoader workLoader;
    private final SpeakingPromptSttPort sttPort;
    private final SpeakingPromptTtsPort ttsPort;
    private final SpeakingPromptAssetService assetService;
    private final SpeakingPromptAuthoringAiProperties properties;
    private final String workerId = "speaking-prompt-" + UUID.randomUUID();

    public SpeakingPromptAiTaskProcessor(
            SpeakingPromptAiTaskRepository taskRepository,
            SpeakingPromptTaskTransactions transactions,
            SpeakingPromptWorkLoader workLoader,
            SpeakingPromptSttPort sttPort,
            SpeakingPromptTtsPort ttsPort,
            SpeakingPromptAssetService assetService,
            SpeakingPromptAuthoringAiProperties properties) {
        this.taskRepository = taskRepository;
        this.transactions = transactions;
        this.workLoader = workLoader;
        this.sttPort = sttPort;
        this.ttsPort = ttsPort;
        this.assetService = assetService;
        this.properties = properties;
    }

    public int processDue(int requestedLimit) {
        int limit = Math.max(
                1,
                Math.min(
                        requestedLimit,
                        properties.taskBounds().workerBatchSize()));
        List<Long> ids = taskRepository.findClaimableIds(
                LocalDateTime.now(), PageRequest.of(0, limit));
        int claimedCount = 0;
        for (Long id : ids) {
            SpeakingPromptAiTask queued = taskRepository.findById(id).orElse(null);
            if (queued == null) {
                continue;
            }
            SpeakingPromptAiContract.Operation operation =
                    SpeakingPromptAiContract.Operation.STT.code().equals(
                            queued.getOperation())
                            ? SpeakingPromptAiContract.Operation.STT
                            : SpeakingPromptAiContract.Operation.TTS;
            try {
                properties.requireOperational(operation);
            } catch (SpeakingPromptAiContract.ProviderFailure unavailable) {
                log.info(
                        "Speaking prompt work remains unclaimed because authoring provider/worker is unavailable operation={} category={}",
                        operation,
                        unavailable.publicCategory());
                continue;
            }
            String claimToken = workerId + ":" + UUID.randomUUID();
            ClaimedTask claim = transactions.claim(
                    id, claimToken, LocalDateTime.now()).orElse(null);
            if (claim == null) {
                continue;
            }
            claimedCount++;
            process(claim);
        }
        return claimedCount;
    }

    private void process(ClaimedTask claim) {
        StoredGeneratedCandidate candidate = null;
        try {
            LoadedWork work = workLoader.load(claim);
            if (claim.operation() == SpeakingPromptAiContract.Operation.STT) {
                SpeakingPromptAiContract.SttResult result =
                        sttPort.transcribe(work.sttRequest());
                if (!transactions.completeStt(
                        claim,
                        result,
                        work.exactInputSha256(),
                        LocalDateTime.now())) {
                    log.info(
                            "Discarded stale Speaking prompt STT completion taskId={}",
                            claim.taskId());
                }
                return;
            }

            SpeakingPromptAiContract.TtsResult result =
                    ttsPort.synthesize(work.ttsRequest());
            candidate = assetService.storeGeneratedCandidate(
                    claim.ownerId(),
                    claim.draftId(),
                    claim.questionClientId(),
                    result.generatedAudio());
            if (!transactions.completeTts(
                    claim,
                    result,
                    candidate,
                    work.exactInputSha256(),
                    LocalDateTime.now())) {
                assetService.discardCandidate(candidate);
                log.info(
                        "Discarded stale Speaking prompt TTS completion taskId={}",
                        claim.taskId());
            }
        } catch (SpeakingPromptAiContract.ProviderFailure failure) {
            if (candidate != null) {
                assetService.discardCandidate(candidate);
            }
            transactions.fail(
                    claim,
                    failure.publicCategory(),
                    failure.retryable(),
                    LocalDateTime.now());
            log.info(
                    "Speaking prompt provider operation failed taskId={} operation={} category={} retryable={}",
                    claim.taskId(),
                    claim.operation(),
                    failure.publicCategory(),
                    failure.retryable());
        } catch (IllegalArgumentException exception) {
            if (candidate != null) {
                assetService.discardCandidate(candidate);
            }
            transactions.fail(
                    claim,
                    SpeakingPromptAiContract.PublicErrorCategory.INVALID_INPUT,
                    false,
                    LocalDateTime.now());
            log.info(
                    "Speaking prompt input verification failed taskId={} operation={}",
                    claim.taskId(),
                    claim.operation());
        } catch (RuntimeException exception) {
            if (candidate != null) {
                assetService.discardCandidate(candidate);
            }
            transactions.fail(
                    claim,
                    SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                    true,
                    LocalDateTime.now());
            log.warn(
                    "Speaking prompt processing failed taskId={} operation={} exception={}",
                    claim.taskId(),
                    claim.operation(),
                    exception.getClass().getSimpleName());
        }
    }
}
