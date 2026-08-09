package com.ksh.features.lessons.controller.support;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.Constructor;

import static com.ksh.common.IConstant.ATTR_FLASH_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MutationFailureHandlerTest {

    private static final String TARGET = "/lecturer/classes/9/lessons";
    private static final String RETRY_MESSAGE = "Kh\u00f4ng th\u1ec3 l\u01b0u. H\u00e3y th\u1eed l\u1ea1i.";

    @Test
    void access_denied_exception_is_rethrown_for_the_global_403_handler() {
        RedirectAttributes attributes = mock(RedirectAttributes.class);
        Logger logger = mock(Logger.class);
        AccessDeniedException denied = new AccessDeniedException("not allowed");

        assertThatThrownBy(() -> MutationFailureHandler.handle(denied, TARGET, attributes,
                RETRY_MESSAGE, logger, "Failed mutation for {}", 9L))
                .isSameAs(denied);

        verify(attributes, never()).addFlashAttribute(any(), any());
        verify(logger, never()).error(any(String.class), any(Object[].class));
    }

    @Test
    void entity_not_found_uses_its_specific_message_and_redirects_back() {
        RedirectAttributes attributes = mock(RedirectAttributes.class);
        EntityNotFoundException missing = new EntityNotFoundException("B\u00e0i gi\u1ea3ng kh\u00f4ng t\u1ed3n t\u1ea1i");

        String result = MutationFailureHandler.handle(missing, TARGET, attributes,
                RETRY_MESSAGE, mock(Logger.class), "Ignored {}");

        assertThat(result).isEqualTo("redirect:" + TARGET);
        verify(attributes).addFlashAttribute(ATTR_FLASH_ERROR, missing.getMessage());
    }

    @Test
    void validation_exception_uses_its_specific_message_and_redirects_back() {
        RedirectAttributes attributes = mock(RedirectAttributes.class);
        IllegalArgumentException invalid = new IllegalArgumentException("Th\u1ee9 t\u1ef1 kh\u00f4ng h\u1ee3p l\u1ec7");

        String result = MutationFailureHandler.handle(invalid, TARGET, attributes,
                RETRY_MESSAGE, mock(Logger.class), "Ignored {}");

        assertThat(result).isEqualTo("redirect:" + TARGET);
        verify(attributes).addFlashAttribute(ATTR_FLASH_ERROR, invalid.getMessage());
    }

    @Test
    void unexpected_exception_is_logged_and_replaced_with_safe_retry_message() {
        RedirectAttributes attributes = mock(RedirectAttributes.class);
        Logger logger = mock(Logger.class);
        IllegalStateException unexpected = new IllegalStateException("database detail must not be exposed");

        String result = MutationFailureHandler.handle(unexpected, TARGET, attributes,
                RETRY_MESSAGE, logger, "Failed lesson mutation in class {}", 9L);

        assertThat(result).isEqualTo("redirect:" + TARGET);
        verify(logger).error(eq("Failed lesson mutation in class {}"), any(Object[].class));
        verify(attributes).addFlashAttribute(ATTR_FLASH_ERROR, RETRY_MESSAGE);
    }

    @Test
    void utility_constructor_is_private_but_instantiable_for_coverage() throws Exception {
        Constructor<MutationFailureHandler> constructor = MutationFailureHandler.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
