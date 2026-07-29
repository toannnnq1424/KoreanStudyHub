package com.ksh.features.practice.preferences;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PracticeKoreanFontPreferenceService.class)
@Transactional
class PracticeKoreanFontPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticeUserPreferenceRepository preferenceRepository;

    @Autowired
    private PracticeKoreanFontPreferenceService preferenceService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void preferencePersistsAcrossFreshReadsAndRetainsOneRowPerAccount() {
        User user = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn")
                .orElseThrow();
        preferenceRepository.deleteById(user.getId());
        preferenceRepository.flush();

        assertThat(preferenceService.read(user.getId()).koreanFont())
                .isEqualTo(PracticeKoreanFont.NANUM_MYEONGJO);
        assertThat(preferenceService.read(user.getId()).koreanFontSize())
                .isEqualTo(PracticeKoreanFontSize.DEFAULT);

        preferenceService.update(
                user.getId(),
                PracticeKoreanFont.DIPHYLLEIA,
                PracticeKoreanFontSize.LARGE,
                2);
        entityManager.clear();

        PracticeKoreanFontPreferenceService.Snapshot firstDeviceRead =
                preferenceService.read(user.getId());
        assertThat(firstDeviceRead.koreanFont())
                .isEqualTo(PracticeKoreanFont.DIPHYLLEIA);
        assertThat(firstDeviceRead.koreanFontSize())
                .isEqualTo(PracticeKoreanFontSize.LARGE);
        assertThat(firstDeviceRead.schemaVersion()).isEqualTo(2);

        preferenceService.update(
                user.getId(),
                PracticeKoreanFont.NANUM_GOTHIC,
                PracticeKoreanFontSize.EXTRA_LARGE,
                2);
        entityManager.clear();

        assertThat(preferenceService.read(user.getId()).koreanFont())
                .isEqualTo(PracticeKoreanFont.NANUM_GOTHIC);
        assertThat(preferenceService.read(user.getId()).koreanFontSize())
                .isEqualTo(PracticeKoreanFontSize.EXTRA_LARGE);
        assertThat(preferenceRepository.findAll()
                .stream()
                .filter(row -> row.getUserId().equals(user.getId())))
                .hasSize(1);
    }
}
