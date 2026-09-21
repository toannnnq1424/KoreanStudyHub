package com.ksh.features.assignments;

import com.ksh.features.assignments.dto.AssignmentDtos.AssignmentForm;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.context.Context;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AssignmentFormTemplateTest {
    @Test void editFieldsRenderExistingDeadline() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var context = new Context();
        context.setVariable("f", new AssignmentForm(1L, "Bài tập", "Nội dung", BigDecimal.TEN, LocalDateTime.of(2026,9,25,13,30), false));
        assertThat(engine.process("assignments/fragments", Set.of("formFields"), context)).contains("2026-09-25T13:30");
    }
}
