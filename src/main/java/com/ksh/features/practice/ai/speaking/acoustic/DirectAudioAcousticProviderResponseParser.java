package com.ksh.features.practice.ai.speaking.acoustic;

import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.StrictOpenAiStructuredResponseDecoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Strict wire-envelope decoder plus exact direct-audio contract normalizer. */
@Component
public final class DirectAudioAcousticProviderResponseParser {

    private final StrictOpenAiStructuredResponseDecoder decoder;
    private final DirectAudioAcousticResponseNormalizer normalizer;

    public DirectAudioAcousticProviderResponseParser(
            StrictOpenAiStructuredResponseDecoder decoder,
            DirectAudioAcousticResponseNormalizer normalizer) {
        this.decoder = Objects.requireNonNull(decoder);
        this.normalizer = Objects.requireNonNull(normalizer);
    }

    public DirectAudioAcousticObservationResult parse(
            byte[] providerResponse,
            int maximumBytes,
            DirectAudioAcousticResponseNormalizer.ExpectedContext expected) {
        try {
            var decoded = decoder.decode(providerResponse, maximumBytes);
            if (!expected.providerRequestId().equals(decoded.providerRequestId())) {
                return DirectAudioAcousticObservationResult.rejected(
                        "DIRECT_AUDIO_CONSUMPTION_RECEIPT_MISMATCH");
            }
            return normalizer.normalize(decoded.output(), expected);
        } catch (PracticeAiContractException exception) {
            return DirectAudioAcousticObservationResult.rejected(
                    exception.category());
        } catch (RuntimeException exception) {
            return DirectAudioAcousticObservationResult.rejected(
                    "DIRECT_AUDIO_PROVIDER_RESPONSE_INVALID");
        }
    }
}
