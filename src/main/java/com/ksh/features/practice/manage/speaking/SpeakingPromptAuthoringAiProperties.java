package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded, provider-neutral configuration for lecturer prompt STT/TTS.
 *
 * <p>This prefix is intentionally distinct from
 * {@code app.practice.speaking-transcription}, which owns learner responses.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.practice.speaking-prompt-authoring")
public class SpeakingPromptAuthoringAiProperties implements InitializingBean {

    private static final Duration LEASE_SAFETY_MARGIN = Duration.ofSeconds(30);

    public static final long MAX_AUTHORING_AUDIO_BYTES =
            SpeakingPromptAiContract.MAX_AUDIO_BYTES;
    public static final Duration MAX_AUTHORING_AUDIO_DURATION =
            Duration.ofMillis(SpeakingPromptAiContract.MAX_AUDIO_DURATION_MILLIS);

    private int maxAttempts = 4;
    private int workerBatchSize = 20;
    private int maxActiveTasksPerLecturer = 4;
    private int maxActiveTasksPerDraft = 2;
    private int maxRequestsPerLecturerPerHour = 20;
    private boolean workerEnabled;
    private Duration leaseDuration = Duration.ofMinutes(3);
    private Duration manualRetryCooldown = Duration.ofSeconds(30);
    private Duration retryInitialDelay = Duration.ofSeconds(10);
    private Duration retryMaxDelay = Duration.ofMinutes(5);
    private Stt stt = new Stt();
    private Tts tts = new Tts();
    private PracticeAiBindingResolver bindingResolver;

    @Override
    public void afterPropertiesSet() {
        taskBounds();
    }

    TaskBounds taskBounds() {
        TaskBounds bounds = new TaskBounds(
                maxAttempts,
                workerBatchSize,
                maxActiveTasksPerLecturer,
                maxActiveTasksPerDraft,
                maxRequestsPerLecturerPerHour,
                leaseDuration,
                manualRetryCooldown,
                retryInitialDelay,
                retryMaxDelay);
        SttConfig currentStt = stt.toConfig();
        TtsConfig currentTts = tts.toConfig();
        Duration maximumCallEnvelope = maximum(
                currentStt.connectTimeout().plus(currentStt.readTimeout()),
                currentTts.connectTimeout().plus(currentTts.readTimeout()));
        if (bounds.leaseDuration().compareTo(
                maximumCallEnvelope.plus(LEASE_SAFETY_MARGIN)) <= 0) {
            throw new IllegalArgumentException(
                    "Authoring task lease must exceed the maximum single-call "
                            + "timeout envelope plus safety margin");
        }
        return bounds;
    }

    SttConfig sttConfig() {
        SttConfig local = stt.toConfig();
        if (bindingResolver == null) {
            return local;
        }
        try {
            PracticeAiResolvedBinding binding = bindingResolver.resolve(
                    PracticeAiPurpose.PRACTICE_SPEAKING_STT);
            return new SttConfig(
                    true,
                    "openai",
                    binding.baseUrl().toString(),
                    binding.credentialSecret(),
                    binding.snapshot().model(),
                    local.language(),
                    binding.snapshot().purpose().name(),
                    binding.snapshot().retentionCode(),
                    binding.snapshot().bindingRevision(),
                    binding.snapshot().providerProfileRevision(),
                    Math.min(local.maxInputBytes(),
                            binding.snapshot().limits().maxRequestBytes()),
                    local.maxInputDuration(),
                    binding.snapshot().limits().connectTimeout(),
                    binding.snapshot().limits().readTimeout(),
                    local.allowedMimeTypes());
        } catch (PracticeAiControlPlaneException exception) {
            return local;
        }
    }

    TtsConfig ttsConfig() {
        TtsConfig local = tts.toConfig();
        if (bindingResolver == null) {
            return local;
        }
        try {
            PracticeAiResolvedBinding binding = bindingResolver.resolve(
                    PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
            return new TtsConfig(
                    true,
                    "openai",
                    binding.baseUrl().toString(),
                    binding.credentialSecret(),
                    binding.snapshot().model(),
                    local.language(),
                    local.voice(),
                    local.speed(),
                    local.outputFormat(),
                    local.maxInputCharacters(),
                    binding.snapshot().purpose().name(),
                    binding.snapshot().retentionCode(),
                    binding.snapshot().bindingRevision(),
                    binding.snapshot().providerProfileRevision(),
                    Math.min(local.maxOutputBytes(),
                            binding.snapshot().limits().maxResponseBytes()),
                    local.maxOutputDuration(),
                    binding.snapshot().limits().connectTimeout(),
                    binding.snapshot().limits().readTimeout(),
                    local.allowedOutputFormats(),
                    local.allowedOutputMimeTypes());
        } catch (PracticeAiControlPlaneException exception) {
            return local;
        }
    }

    void requireOperational(SpeakingPromptAiContract.Operation operation) {
        if (!workerEnabled) {
            throw unavailable();
        }
        if (bindingResolver != null) {
            try {
                bindingResolver.resolve(operation == SpeakingPromptAiContract.Operation.STT
                        ? PracticeAiPurpose.PRACTICE_SPEAKING_STT
                        : PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
                return;
            } catch (PracticeAiControlPlaneException exception) {
                throw unavailable();
            }
        }
        if (operation == SpeakingPromptAiContract.Operation.STT) {
            SttConfig config = sttConfig();
            requireOpenAi(config.enabled(), config.provider(), config.baseUrl(),
                    config.apiKey(), config.model());
            return;
        }
        TtsConfig config = ttsConfig();
        requireOpenAi(config.enabled(), config.provider(), config.baseUrl(),
                config.apiKey(), config.model());
    }

    @Autowired(required = false)
    void setBindingResolver(PracticeAiBindingResolver bindingResolver) {
        this.bindingResolver = bindingResolver;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getWorkerBatchSize() {
        return workerBatchSize;
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }

    public int getMaxActiveTasksPerLecturer() {
        return maxActiveTasksPerLecturer;
    }

    public void setMaxActiveTasksPerLecturer(int maxActiveTasksPerLecturer) {
        this.maxActiveTasksPerLecturer = maxActiveTasksPerLecturer;
    }

    public int getMaxActiveTasksPerDraft() {
        return maxActiveTasksPerDraft;
    }

    public void setMaxActiveTasksPerDraft(int maxActiveTasksPerDraft) {
        this.maxActiveTasksPerDraft = maxActiveTasksPerDraft;
    }

    public int getMaxRequestsPerLecturerPerHour() {
        return maxRequestsPerLecturerPerHour;
    }

    public void setMaxRequestsPerLecturerPerHour(int maxRequestsPerLecturerPerHour) {
        this.maxRequestsPerLecturerPerHour = maxRequestsPerLecturerPerHour;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getManualRetryCooldown() {
        return manualRetryCooldown;
    }

    public void setManualRetryCooldown(Duration manualRetryCooldown) {
        this.manualRetryCooldown = manualRetryCooldown;
    }

    public Duration getRetryInitialDelay() {
        return retryInitialDelay;
    }

    public void setRetryInitialDelay(Duration retryInitialDelay) {
        this.retryInitialDelay = retryInitialDelay;
    }

    public Duration getRetryMaxDelay() {
        return retryMaxDelay;
    }

    public void setRetryMaxDelay(Duration retryMaxDelay) {
        this.retryMaxDelay = retryMaxDelay;
    }

    public Stt getStt() {
        return stt;
    }

    public void setStt(Stt stt) {
        this.stt = stt == null ? new Stt() : stt;
    }

    public Tts getTts() {
        return tts;
    }

    public void setTts(Tts tts) {
        this.tts = tts == null ? new Tts() : tts;
    }

    public static final class Stt {
        private boolean enabled;
        private String provider = "disabled";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String language = "ko";
        private String purposeCode = "speaking_prompt_stt";
        private String retentionCode = "provider_default";
        private long maxInputBytes = MAX_AUTHORING_AUDIO_BYTES;
        private Duration maxInputDuration = MAX_AUTHORING_AUDIO_DURATION;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(60);
        private Set<String> allowedMimeTypes = new LinkedHashSet<>(Set.of(
                "audio/mpeg",
                "audio/wav",
                "audio/x-wav",
                "audio/mp4",
                "audio/x-m4a",
                "audio/ogg",
                "audio/webm"));

        private SttConfig toConfig() {
            return new SttConfig(
                    enabled,
                    provider,
                    baseUrl,
                    apiKey,
                    model,
                    language,
                    purposeCode,
                    retentionCode,
                    -1L,
                    -1L,
                    maxInputBytes,
                    maxInputDuration,
                    connectTimeout,
                    readTimeout,
                    allowedMimeTypes);
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getPurposeCode() { return purposeCode; }
        public void setPurposeCode(String purposeCode) { this.purposeCode = purposeCode; }
        public String getRetentionCode() { return retentionCode; }
        public void setRetentionCode(String retentionCode) { this.retentionCode = retentionCode; }
        public long getMaxInputBytes() { return maxInputBytes; }
        public void setMaxInputBytes(long maxInputBytes) { this.maxInputBytes = maxInputBytes; }
        public Duration getMaxInputDuration() { return maxInputDuration; }
        public void setMaxInputDuration(Duration maxInputDuration) {
            this.maxInputDuration = maxInputDuration;
        }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public Set<String> getAllowedMimeTypes() { return allowedMimeTypes; }
        public void setAllowedMimeTypes(Set<String> allowedMimeTypes) {
            this.allowedMimeTypes = allowedMimeTypes;
        }
    }

    public static final class Tts {
        private boolean enabled;
        private String provider = "disabled";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String language = "ko";
        private String voice = "default";
        private BigDecimal speed = BigDecimal.ONE;
        private String outputFormat = "mp3";
        private int maxInputCharacters = SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS;
        private String purposeCode = "speaking_prompt_tts";
        private String retentionCode = "provider_default";
        private long maxOutputBytes = MAX_AUTHORING_AUDIO_BYTES;
        private Duration maxOutputDuration = MAX_AUTHORING_AUDIO_DURATION;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(90);
        private Set<String> allowedOutputFormats = new LinkedHashSet<>(Set.of("mp3", "wav"));
        private Set<String> allowedOutputMimeTypes =
                new LinkedHashSet<>(Set.of("audio/mpeg", "audio/wav", "audio/x-wav"));

        private TtsConfig toConfig() {
            return new TtsConfig(
                    enabled,
                    provider,
                    baseUrl,
                    apiKey,
                    model,
                    language,
                    voice,
                    speed,
                    outputFormat,
                    maxInputCharacters,
                    purposeCode,
                    retentionCode,
                    -1L,
                    -1L,
                    maxOutputBytes,
                    maxOutputDuration,
                    connectTimeout,
                    readTimeout,
                    allowedOutputFormats,
                    allowedOutputMimeTypes);
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getVoice() { return voice; }
        public void setVoice(String voice) { this.voice = voice; }
        public BigDecimal getSpeed() { return speed; }
        public void setSpeed(BigDecimal speed) { this.speed = speed; }
        public String getOutputFormat() { return outputFormat; }
        public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
        public int getMaxInputCharacters() { return maxInputCharacters; }
        public void setMaxInputCharacters(int maxInputCharacters) {
            this.maxInputCharacters = maxInputCharacters;
        }
        public String getPurposeCode() { return purposeCode; }
        public void setPurposeCode(String purposeCode) { this.purposeCode = purposeCode; }
        public String getRetentionCode() { return retentionCode; }
        public void setRetentionCode(String retentionCode) { this.retentionCode = retentionCode; }
        public long getMaxOutputBytes() { return maxOutputBytes; }
        public void setMaxOutputBytes(long maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }
        public Duration getMaxOutputDuration() { return maxOutputDuration; }
        public void setMaxOutputDuration(Duration maxOutputDuration) {
            this.maxOutputDuration = maxOutputDuration;
        }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public Set<String> getAllowedOutputFormats() { return allowedOutputFormats; }
        public void setAllowedOutputFormats(Set<String> allowedOutputFormats) {
            this.allowedOutputFormats = allowedOutputFormats;
        }
        public Set<String> getAllowedOutputMimeTypes() { return allowedOutputMimeTypes; }
        public void setAllowedOutputMimeTypes(Set<String> allowedOutputMimeTypes) {
            this.allowedOutputMimeTypes = allowedOutputMimeTypes;
        }
    }

    record TaskBounds(
            int maxAttempts,
            int workerBatchSize,
            int maxActiveTasksPerLecturer,
            int maxActiveTasksPerDraft,
            int maxRequestsPerLecturerPerHour,
            Duration leaseDuration,
            Duration manualRetryCooldown,
            Duration retryInitialDelay,
            Duration retryMaxDelay
    ) {
        public TaskBounds {
            requireRange(maxAttempts, 1, 10, "authoring task max attempts");
            requireRange(workerBatchSize, 1, 100, "authoring worker batch size");
            requireRange(
                    maxActiveTasksPerLecturer, 1, 100,
                    "authoring active tasks per lecturer");
            requireRange(
                    maxActiveTasksPerDraft, 1, 20,
                    "authoring active tasks per draft");
            if (maxActiveTasksPerDraft > maxActiveTasksPerLecturer) {
                throw new IllegalArgumentException(
                        "Active tasks per draft cannot exceed the lecturer limit");
            }
            requireRange(
                    maxRequestsPerLecturerPerHour, 1, 1_000,
                    "authoring requests per lecturer per hour");
            leaseDuration = duration(
                    leaseDuration, Duration.ofSeconds(1), Duration.ofMinutes(10),
                    "authoring task lease duration");
            manualRetryCooldown = duration(
                    manualRetryCooldown, Duration.ofSeconds(1), Duration.ofHours(1),
                    "authoring manual retry cooldown");
            retryInitialDelay = duration(
                    retryInitialDelay, Duration.ofSeconds(1), Duration.ofMinutes(5),
                    "authoring retry initial delay");
            retryMaxDelay = duration(
                    retryMaxDelay, retryInitialDelay, Duration.ofHours(1),
                    "authoring retry maximum delay");
        }
    }

    record SttConfig(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            String language,
            String purposeCode,
            String retentionCode,
            long bindingRevision,
            long providerProfileRevision,
            long maxInputBytes,
            Duration maxInputDuration,
            Duration connectTimeout,
            Duration readTimeout,
            Set<String> allowedMimeTypes
    ) {
        public SttConfig {
            provider = code(provider, "STT provider", 64);
            /*
             * Partial/disabled provider configuration is a runtime
             * availability state, not malformed authoring input. The enqueue
             * gate maps it to CONFIGURATION before any source/artifact/task
             * mutation; structural bounds and a present endpoint are still
             * validated here.
             */
            baseUrl = endpoint(baseUrl, false, "STT base URL");
            apiKey = secret(apiKey, false, "STT API key");
            model = configuredText(model, false, "STT model", 128);
            language = code(language, "STT language", 32);
            purposeCode = exactPurpose(purposeCode, "STT purpose code");
            retentionCode = exactPurpose(retentionCode, "STT retention code");
            if (bindingRevision < -1 || providerProfileRevision < -1) {
                throw new IllegalArgumentException("STT binding revisions are invalid");
            }
            maxInputBytes = bytes(maxInputBytes, "STT max input bytes");
            maxInputDuration = duration(
                    maxInputDuration, Duration.ofSeconds(1), MAX_AUTHORING_AUDIO_DURATION,
                    "STT max input duration");
            connectTimeout = duration(
                    connectTimeout, Duration.ofMillis(100), Duration.ofSeconds(30),
                    "STT connect timeout");
            readTimeout = duration(
                    readTimeout, Duration.ofSeconds(1), Duration.ofMinutes(2),
                    "STT read timeout");
            allowedMimeTypes = values(allowedMimeTypes, "STT allowed MIME types");
        }

        @Override
        public String toString() {
            return "SttConfig{"
                    + "enabled=" + enabled
                    + ", provider='" + provider + '\''
                    + ", baseUrlConfigured=" + !baseUrl.isBlank()
                    + ", apiKeyPresent=" + !apiKey.isBlank()
                    + ", model='" + model + '\''
                    + ", language='" + language + '\''
                    + ", purposeCode='" + purposeCode + '\''
                    + ", retentionCode='" + retentionCode + '\''
                    + ", maxInputBytes=" + maxInputBytes
                    + ", maxInputDuration=" + maxInputDuration
                    + ", connectTimeout=" + connectTimeout
                    + ", readTimeout=" + readTimeout
                    + ", allowedMimeTypes=" + allowedMimeTypes
                    + '}';
        }
    }

    record TtsConfig(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            String language,
            String voice,
            BigDecimal speed,
            String outputFormat,
            int maxInputCharacters,
            String purposeCode,
            String retentionCode,
            long bindingRevision,
            long providerProfileRevision,
            long maxOutputBytes,
            Duration maxOutputDuration,
            Duration connectTimeout,
            Duration readTimeout,
            Set<String> allowedOutputFormats,
            Set<String> allowedOutputMimeTypes
    ) {
        TtsConfig(
                boolean enabled,
                String provider,
                String baseUrl,
                String apiKey,
                String model,
                String language,
                String voice,
                BigDecimal speed,
                String outputFormat,
                int maxInputCharacters,
                String purposeCode,
                String retentionCode,
                long maxOutputBytes,
                Duration maxOutputDuration,
                Duration connectTimeout,
                Duration readTimeout,
                Set<String> allowedOutputFormats,
                Set<String> allowedOutputMimeTypes) {
            this(enabled, provider, baseUrl, apiKey, model, language, voice,
                    speed, outputFormat, maxInputCharacters, purposeCode,
                    retentionCode, -1L, -1L, maxOutputBytes,
                    maxOutputDuration, connectTimeout, readTimeout,
                    allowedOutputFormats, allowedOutputMimeTypes);
        }

        public TtsConfig {
            provider = code(provider, "TTS provider", 64);
            baseUrl = endpoint(baseUrl, false, "TTS base URL");
            apiKey = secret(apiKey, false, "TTS API key");
            model = configuredText(model, false, "TTS model", 128);
            language = code(language, "TTS language", 32);
            voice = configuredText(voice, true, "TTS voice", 128);
            speed = validatedSpeed(speed);
            outputFormat = code(outputFormat, "TTS output format", 32);
            requireRange(
                    maxInputCharacters,
                    1,
                    SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS,
                    "TTS max input characters");
            purposeCode = exactPurpose(purposeCode, "TTS purpose code");
            retentionCode = exactPurpose(retentionCode, "TTS retention code");
            if (bindingRevision < -1 || providerProfileRevision < -1) {
                throw new IllegalArgumentException("TTS binding revisions are invalid");
            }
            maxOutputBytes = bytes(maxOutputBytes, "TTS max output bytes");
            maxOutputDuration = duration(
                    maxOutputDuration, Duration.ofSeconds(1), MAX_AUTHORING_AUDIO_DURATION,
                    "TTS max output duration");
            connectTimeout = duration(
                    connectTimeout, Duration.ofMillis(100), Duration.ofSeconds(30),
                    "TTS connect timeout");
            readTimeout = duration(
                    readTimeout, Duration.ofSeconds(1), Duration.ofMinutes(2),
                    "TTS read timeout");
            allowedOutputFormats = values(
                    allowedOutputFormats, "TTS allowed output formats");
            allowedOutputMimeTypes = values(
                    allowedOutputMimeTypes, "TTS allowed output MIME types");
            if (!allowedOutputFormats.contains(outputFormat)) {
                throw new IllegalArgumentException(
                        "TTS output format must be present in allowed output formats");
            }
        }

        @Override
        public String toString() {
            return "TtsConfig{"
                    + "enabled=" + enabled
                    + ", provider='" + provider + '\''
                    + ", baseUrlConfigured=" + !baseUrl.isBlank()
                    + ", apiKeyPresent=" + !apiKey.isBlank()
                    + ", model='" + model + '\''
                    + ", language='" + language + '\''
                    + ", voice='" + voice + '\''
                    + ", speed=" + speed
                    + ", outputFormat='" + outputFormat + '\''
                    + ", maxInputCharacters=" + maxInputCharacters
                    + ", purposeCode='" + purposeCode + '\''
                    + ", retentionCode='" + retentionCode + '\''
                    + ", maxOutputBytes=" + maxOutputBytes
                    + ", maxOutputDuration=" + maxOutputDuration
                    + ", connectTimeout=" + connectTimeout
                    + ", readTimeout=" + readTimeout
                    + ", allowedOutputFormats=" + allowedOutputFormats
                    + ", allowedOutputMimeTypes=" + allowedOutputMimeTypes
                    + '}';
        }
    }

    private static long bytes(long value, String label) {
        if (value <= 0L || value > MAX_AUTHORING_AUDIO_BYTES) {
            throw new IllegalArgumentException(
                    label + " must be between 1 and " + MAX_AUTHORING_AUDIO_BYTES);
        }
        return value;
    }

    private static Duration maximum(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static void requireOpenAi(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String model) {
        if (!enabled
                || !"openai".equals(provider)
                || baseUrl.isBlank()
                || apiKey.isBlank()
                || model.isBlank()) {
            throw unavailable();
        }
    }

    private static SpeakingPromptAiContract.ProviderFailure unavailable() {
        return new SpeakingPromptAiContract.ProviderFailure(
                SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                false,
                null,
                null);
    }

    private static BigDecimal validatedSpeed(BigDecimal value) {
        if (value == null
                || value.compareTo(new BigDecimal("0.25")) < 0
                || value.compareTo(new BigDecimal("4.00")) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("TTS speed must be between 0.25 and 4.00");
        }
        return value.stripTrailingZeros();
    }

    private static String code(String value, String label, int maximumLength) {
        String normalized = configuredText(value, true, label, maximumLength)
                .toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(label + " is not a bounded code");
        }
        return normalized;
    }

    private static String exactPurpose(String value, String label) {
        String normalized = configuredText(value, true, label, 64);
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_]{1,63}")) {
            throw new IllegalArgumentException(label + " is not a bounded code");
        }
        return normalized;
    }

    private static String configuredText(String value, boolean required, String label) {
        return configuredText(value, required, label, 128);
    }

    private static String configuredText(
            String value,
            boolean required,
            String label,
            int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    private static String endpoint(String value, boolean required, String label) {
        String endpoint = configuredText(value, required, label);
        if (endpoint.isBlank()) {
            return endpoint;
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URI", exception);
        }
        if (!uri.isAbsolute()
                || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URI");
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private static String secret(String value, boolean required, String label) {
        String secret = value == null ? "" : value.trim();
        if (required && secret.isBlank()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        if (secret.length() > 4_096) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return secret;
    }

    private static Set<String> values(Set<String> rawValues, String label) {
        if (rawValues == null || rawValues.isEmpty()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String value = configuredText(rawValue, true, label).toLowerCase(Locale.ROOT);
            normalized.add(value);
        }
        if (normalized.size() > 16) {
            throw new IllegalArgumentException(label + " contains too many values");
        }
        return Set.copyOf(normalized);
    }

    private static Duration duration(
            Duration value,
            Duration minimum,
            Duration maximum,
            String label) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    label + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static void requireRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + " must be between " + minimum + " and " + maximum);
        }
    }

}
