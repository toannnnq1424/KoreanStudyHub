package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KshBrandingContractTest {

    private static final Path PRODUCTION_ROOT = Path.of("src/main");
    private static final Path PUBLISHED_V54_AI_PROMPTS = Path.of(
            "src/main/resources/db/migration/V54__ai_system_prompts.sql");
    private static final String PUBLISHED_V54_AI_PROMPTS_SHA256 =
            "f03dfecb7e6c9e4ea4ec2b66b893d249ea988d38e158f614b85e49a20ac828ba";
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "css", "html", "java", "js", "json", "properties", "sql", "svg", "xml", "yaml", "yml");
    private static final Pattern LEGACY_ULP_NAME = Pattern.compile(
            "\\b(?:ULP|ulp)(?:\\b|_|(?=[A-Z]))|\\bUlp[A-Z][A-Za-z0-9_]*");

    @Test
    void liveProductionSourcesUseCanonicalKshBranding() throws Exception {
        List<Path> offenders;
        try (Stream<Path> paths = Files.walk(PRODUCTION_ROOT)) {
            offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.equals(PUBLISHED_V54_AI_PROMPTS))
                    .filter(KshBrandingContractTest::isTextSource)
                    .filter(KshBrandingContractTest::containsLegacyUlpName)
                    .toList();
        }

        assertTrue(
                offenders.isEmpty(),
                "Live production sources must use KSH/Ksh instead of legacy ULP/Ulp names: "
                        + offenders);
    }

    @Test
    void publishedLegacyBrandMigrationExceptionRemainsByteIdentical() throws Exception {
        String actualSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(PUBLISHED_V54_AI_PROMPTS)));

        assertEquals(PUBLISHED_V54_AI_PROMPTS_SHA256, actualSha256);
    }

    private static boolean isTextSource(Path path) {
        String fileName = path.getFileName().toString();
        int extensionSeparator = fileName.lastIndexOf('.');
        return extensionSeparator >= 0
                && TEXT_EXTENSIONS.contains(fileName.substring(extensionSeparator + 1));
    }

    private static boolean containsLegacyUlpName(Path path) {
        try {
            return LEGACY_ULP_NAME.matcher(Files.readString(path)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect production source " + path, exception);
        }
    }
}
