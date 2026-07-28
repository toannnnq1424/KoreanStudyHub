package com.ksh.features.practice.assessment;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpeakingPromptDeliveryPresenter {

    public SpeakingPromptDelivery present(
            QuestionContent content,
            String immutablePrompt,
            String legacyAudioFallback) {
        if (content == null) {
            throw new IllegalArgumentException("Thiếu nội dung bất biến của câu Speaking.");
        }
        QuestionContent.SpeakingDelivery delivery = content.speakingDelivery();
        if (QuestionContent.SCHEMA_VERSION_V2.equals(content.schemaVersion())) {
            if (delivery == null || delivery.deliveryMode() == null) {
                throw new IllegalArgumentException("Thiếu chế độ phát đề Speaking v2.");
            }
            return switch (delivery.deliveryMode()) {
                case AUDIO_ONLY -> audioOnly(content, delivery);
                case TEXT_AND_AUDIO -> textAndAudio(content, delivery, immutablePrompt);
                case TEXT_ONLY -> textOnly(content, delivery, immutablePrompt);
            };
        }

        String audio = firstNonBlank(
                delivery == null ? null : delivery.promptAudioReference(),
                content.audioReference(),
                legacyAudioFallback);
        if (blank(audio)) {
            throw new IllegalArgumentException("Câu Speaking v1 thiếu audio đề bài bất biến.");
        }
        return new SpeakingPromptDelivery(
                SpeakingPromptDelivery.SCHEMA_VERSION,
                QuestionContent.SCHEMA_VERSION_V1,
                "audio_only",
                blankToNull(immutablePrompt),
                audio,
                false,
                !blank(immutablePrompt),
                delivery == null || delivery.promptPlayLimit() == null
                        ? 1 : delivery.promptPlayLimit(),
                delivery == null || delivery.preparationSeconds() == null
                        ? 30 : delivery.preparationSeconds(),
                delivery == null || delivery.responseSeconds() == null
                        ? 60 : delivery.responseSeconds(),
                List.of(
                        SpeakingPromptDelivery.Step.PROMPT_PLAYBACK,
                        SpeakingPromptDelivery.Step.PREPARATION,
                        SpeakingPromptDelivery.Step.RECORDING));
    }

    private static SpeakingPromptDelivery audioOnly(
            QuestionContent content,
            QuestionContent.SpeakingDelivery delivery) {
        requireAudio(delivery);
        return new SpeakingPromptDelivery(
                SpeakingPromptDelivery.SCHEMA_VERSION,
                content.schemaVersion(),
                delivery.deliveryMode().code(),
                null,
                delivery.promptAudioReference(),
                false,
                false,
                delivery.promptPlayLimit(),
                delivery.preparationSeconds(),
                delivery.responseSeconds(),
                List.of(
                        SpeakingPromptDelivery.Step.PROMPT_PLAYBACK,
                        SpeakingPromptDelivery.Step.PREPARATION,
                        SpeakingPromptDelivery.Step.RECORDING));
    }

    private static SpeakingPromptDelivery textAndAudio(
            QuestionContent content,
            QuestionContent.SpeakingDelivery delivery,
            String immutablePrompt) {
        requireText(immutablePrompt);
        requireAudio(delivery);
        return new SpeakingPromptDelivery(
                SpeakingPromptDelivery.SCHEMA_VERSION,
                content.schemaVersion(),
                delivery.deliveryMode().code(),
                immutablePrompt,
                delivery.promptAudioReference(),
                true,
                true,
                delivery.promptPlayLimit(),
                delivery.preparationSeconds(),
                delivery.responseSeconds(),
                List.of(
                        SpeakingPromptDelivery.Step.PROMPT_PLAYBACK,
                        SpeakingPromptDelivery.Step.PREPARATION,
                        SpeakingPromptDelivery.Step.RECORDING));
    }

    private static SpeakingPromptDelivery textOnly(
            QuestionContent content,
            QuestionContent.SpeakingDelivery delivery,
            String immutablePrompt) {
        requireText(immutablePrompt);
        if (!blank(delivery.promptAudioReference())
                || delivery.promptPlayLimit() != null) {
            throw new IllegalArgumentException(
                    "Câu Speaking chỉ dùng văn bản không được có bước hoặc giới hạn phát audio.");
        }
        return new SpeakingPromptDelivery(
                SpeakingPromptDelivery.SCHEMA_VERSION,
                content.schemaVersion(),
                delivery.deliveryMode().code(),
                immutablePrompt,
                null,
                true,
                true,
                null,
                delivery.preparationSeconds(),
                delivery.responseSeconds(),
                List.of(
                        SpeakingPromptDelivery.Step.PREPARATION,
                        SpeakingPromptDelivery.Step.RECORDING));
    }

    private static void requireAudio(QuestionContent.SpeakingDelivery delivery) {
        if (blank(delivery.promptAudioReference()) || delivery.promptPlayLimit() == null) {
            throw new IllegalArgumentException("Chế độ Speaking có audio thiếu dữ liệu phát đề.");
        }
    }

    private static void requireText(String prompt) {
        if (blank(prompt)) {
            throw new IllegalArgumentException("Nội dung đề Speaking bằng văn bản không được để trống.");
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
