package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticeOverrideContextServiceTest {

    private final PracticeOverrideContextService service =
            new PracticeOverrideContextService();

    @Test
    void keepsReasonServerSidePerTargetAndCanClearDraftContext() {
        MockHttpSession session = new MockHttpSession();

        service.establishForSet(session, 10L, "  Xử lý lỗi phát hành khẩn cấp  ");
        service.establishForDraft(session, 20L, "Sửa nội dung bị khóa");

        assertEquals("Xử lý lỗi phát hành khẩn cấp",
                service.reasonForSet(session, 10L, null));
        assertEquals("Sửa nội dung bị khóa",
                service.reasonForDraft(session, 20L, null));
        assertNull(service.reasonForDraft(session, 21L, null));

        service.clearDraft(session, 20L);
        assertNull(service.reasonForDraft(session, 20L, null));
    }

    @Test
    void rejectsBlankOrOversizedReason() {
        MockHttpSession session = new MockHttpSession();

        assertThrows(IllegalArgumentException.class,
                () -> service.establishForSet(session, 10L, " "));
        assertThrows(IllegalArgumentException.class,
                () -> service.establishForSet(session, 10L, "x".repeat(501)));
    }

    @Test
    void expiredContextIsRemovedAndCannotAuthorizeAnotherRequest() {
        PracticeOverrideContextService expiringService =
                new PracticeOverrideContextService(
                        Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC),
                        Duration.ZERO);
        MockHttpSession session = new MockHttpSession();

        expiringService.establishForDraft(session, 20L, "Can thiệp có thời hạn");

        assertNull(expiringService.reasonForDraft(session, 20L, null));
    }
}
