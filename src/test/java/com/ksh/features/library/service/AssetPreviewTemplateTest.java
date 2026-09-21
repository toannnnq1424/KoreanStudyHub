package com.ksh.features.library.service;

import com.ksh.features.library.dto.LibraryDtos.LibraryAssetPreview;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.context.Context;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.context.IExpressionContext;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AssetPreviewTemplateTest {
    @Test void rendersSpreadsheetAndEscapesCellText() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override protected String computeContextPath(IExpressionContext context, String base, Map<String,Object> parameters) { return ""; }
        });
        var context = new Context();
        context.setVariable("assetPreview", new LibraryAssetPreview(11L, "Tài liệu", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "XLSX", "excel",
                "SPREADSHEET", "/content", "/download", "Sheet1", List.of(List.of("<script>")), List.of(), null));
        assertThat(engine.process("library/asset-preview", context)).contains("&lt;script&gt;", "Sheet1");
    }
}
