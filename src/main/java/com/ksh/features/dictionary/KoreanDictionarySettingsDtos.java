package com.ksh.features.dictionary;

import jakarta.validation.constraints.Size;

public final class KoreanDictionarySettingsDtos {
    private KoreanDictionarySettingsDtos() {}

    public record Form(
            @Size(max = 500) String apiKey,
            @Size(max = 500) String baseUrl
    ) {}
}
