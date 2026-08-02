package com.ksh.features.practice.ai.controlplane;

import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.CapabilityTestResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PracticeAiCapabilityTestService {

    private final PracticeAiBindingResolver resolver;
    private final PracticeAiCapabilityProbe probe;
    private final PracticeAiCapabilityTestRunRepository repository;

    public PracticeAiCapabilityTestService(
            PracticeAiBindingResolver resolver,
            PracticeAiCapabilityProbe probe,
            PracticeAiCapabilityTestRunRepository repository) {
        this.resolver = resolver;
        this.probe = probe;
        this.repository = repository;
    }

    public CapabilityTestResult test(PracticeAiPurpose purpose, Long actorId) {
        PracticeAiResolvedBinding binding = resolver.resolve(purpose);
        PracticeAiCapabilityTestRun run = repository.saveAndFlush(
                new PracticeAiCapabilityTestRun(
                        binding.snapshot(), actorId, LocalDateTime.now()));
        long started = System.nanoTime();
        try {
            resolver.assertCurrent(binding.snapshot());
            probe.probe(binding);
            long duration = elapsed(started);
            run.complete("PASS", duration, null, LocalDateTime.now());
            repository.saveAndFlush(run);
            return new CapabilityTestResult(
                    true, "PASS", null,
                    binding.snapshot().bindingRevision(), duration);
        } catch (RuntimeException exception) {
            String errorCode = safeError(exception);
            String status = Thread.currentThread().isInterrupted()
                    ? "CANCELLED"
                    : "FAIL";
            long duration = elapsed(started);
            run.complete(status, duration, errorCode, LocalDateTime.now());
            repository.saveAndFlush(run);
            return new CapabilityTestResult(
                    false, status, errorCode,
                    binding.snapshot().bindingRevision(), duration);
        }
    }

    private static String safeError(RuntimeException exception) {
        String code = exception instanceof PracticeAiControlPlaneException control
                ? control.errorCode()
                : "CAPABILITY_TEST_FAILED";
        return code != null && code.matches("[A-Z][A-Z0-9_]{1,63}")
                ? code
                : "CAPABILITY_TEST_FAILED";
    }

    private static long elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
