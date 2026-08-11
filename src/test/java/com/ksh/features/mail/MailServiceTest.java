package com.ksh.features.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceTest {

    private final DbConfiguredMailSender sender = mock(DbConfiguredMailSender.class);
    private final MailService service = new MailService(sender);

    @Test
    void send_whenSenderSucceeds_returnsTrue() {
        when(sender.send("student@ksh.test", "Welcome", "Hello")).thenReturn(true);

        boolean result = service.send("student@ksh.test", "Welcome", "Hello");

        assertThat(result).isTrue();
        verify(sender).send("student@ksh.test", "Welcome", "Hello");
    }

    @Test
    void send_whenSenderFails_returnsFalse() {
        when(sender.send("student@ksh.test", "Welcome", "Hello")).thenReturn(false);

        boolean result = service.send("student@ksh.test", "Welcome", "Hello");

        assertThat(result).isFalse();
        verify(sender).send("student@ksh.test", "Welcome", "Hello");
    }
}
