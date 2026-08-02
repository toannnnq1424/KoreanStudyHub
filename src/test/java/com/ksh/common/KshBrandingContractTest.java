package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KshBrandingContractTest {

    private static final Path PRODUCTION_ROOT = Path.of("src/main");
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
                    .filter(KshBrandingContractTest::isTextSource)
                    .filter(KshBrandingContractTest::containsLegacyUlpName)
                    .toList();
        }

        assertTrue(
                offenders.isEmpty(),
                "Live production sources must use KSH/Ksh instead of legacy ULP/Ulp names: "
                        + offenders);
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
