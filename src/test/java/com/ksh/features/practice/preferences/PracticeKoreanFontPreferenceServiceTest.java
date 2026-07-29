package com.ksh.features.practice.preferences;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeKoreanFontPreferenceServiceTest {

    @Mock
    private PracticeUserPreferenceRepository repository;

    @InjectMocks
    private PracticeKoreanFontPreferenceService service;

    @Test
    void missingPreferenceUsesStableNanumDefaultWithoutImplicitWrite() {
        when(repository.findById(41L)).thenReturn(Optional.empty());

        PracticeKoreanFontPreferenceService.Snapshot snapshot =
                service.read(41L);

        assertThat(snapshot.accountId()).isEqualTo(41L);
        assertThat(snapshot.koreanFont())
                .isEqualTo(PracticeKoreanFont.NANUM_MYEONGJO);
        assertThat(snapshot.koreanFontSize())
                .isEqualTo(PracticeKoreanFontSize.DEFAULT);
        assertThat(snapshot.schemaVersion()).isEqualTo(2);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void canonicalStoredAllowlistedValueIsReturned() {
        PracticeUserPreference row = mock(PracticeUserPreference.class);
        when(row.getUserId()).thenReturn(41L);
        when(row.getKoreanFont()).thenReturn(PracticeKoreanFont.DIPHYLLEIA);
        when(row.getKoreanFontSize()).thenReturn(PracticeKoreanFontSize.LARGE);
        when(repository.findById(41L)).thenReturn(Optional.of(row));

        assertThat(service.read(41L).koreanFont())
                .isEqualTo(PracticeKoreanFont.DIPHYLLEIA);
        assertThat(service.read(41L).koreanFontSize())
                .isEqualTo(PracticeKoreanFontSize.LARGE);
    }

    @Test
    void updateAtomicallyUpsertsOnlyAuthenticatedAccountAndSchemaV2() {
        PracticeKoreanFontPreferenceService.Snapshot snapshot =
                service.update(
                        41L,
                        PracticeKoreanFont.NANUM_GOTHIC,
                        PracticeKoreanFontSize.EXTRA_LARGE,
                        2);

        assertThat(snapshot).isEqualTo(
                new PracticeKoreanFontPreferenceService.Snapshot(
                        41L,
                        PracticeKoreanFont.NANUM_GOTHIC,
                        PracticeKoreanFontSize.EXTRA_LARGE,
                        2));
        verify(repository).upsert(
                41L,
                "NANUM_GOTHIC",
                "EXTRA_LARGE",
                2);
    }

    @Test
    void invalidSchemaAndInvalidIdentityFailClosedBeforeRepositoryAccess() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(
                        41L,
                        PracticeKoreanFont.DIPHYLLEIA,
                        PracticeKoreanFontSize.DEFAULT,
                        1))
                .withMessageContaining("schema");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(
                        null,
                        PracticeKoreanFont.DIPHYLLEIA,
                        PracticeKoreanFontSize.DEFAULT,
                        2))
                .withMessageContaining("user id");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(
                        41L,
                        null,
                        PracticeKoreanFontSize.DEFAULT,
                        2))
                .withMessageContaining("font");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(
                        41L,
                        PracticeKoreanFont.GUGI,
                        null,
                        2))
                .withMessageContaining("size");
        verifyNoInteractions(repository);
    }
}
