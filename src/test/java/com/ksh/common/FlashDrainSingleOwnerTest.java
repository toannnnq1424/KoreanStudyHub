package com.ksh.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mechanical guard for the non-Practice UI: {@code notifications.js} owns the
 * {@code #flash-data} to {@code KshToast} drain.
 *
 * <p>Practice is deliberately excluded because it has an independent UI/runtime
 * configuration and is outside this change. The exclusion is path-scoped rather
 * than filename-scoped so it cannot accidentally allow a duplicate drainer in a
 * non-Practice feature.
 */
class FlashDrainSingleOwnerTest {

    private static final Path JS_DIR = Paths.get("src/main/resources/static/js");
    private static final Path OWNER = Paths.get("notifications.js");

    private static final List<String> DRAIN_MARKERS = List.of(
            "data-flash-success", "data-flash-error",
            "data-flash-info", "data-flash-warning",
            "flashsuccess", "flasherror", "flashinfo", "flashwarning");

    @Test
    @DisplayName("only notifications.js drains flash data outside Practice")
    void onlyNotificationsJsDrainsNonPracticeFlashData() {
        assertThat(JS_DIR)
                .as("static JS directory must resolve from the Maven project root")
                .isDirectory();

        List<String> offenders = jsFiles().stream()
                .filter(file -> !isPractice(file))
                .filter(file -> !relative(file).equals(OWNER))
                .filter(FlashDrainSingleOwnerTest::containsDrainMarker)
                .map(file -> relative(file).toString().replace('\\', '/'))
                .sorted()
                .collect(Collectors.toList());

        assertThat(offenders)
                .as("Non-Practice page scripts must let notifications.js drain server flash payloads")
                .isEmpty();
    }

    @Test
    @DisplayName("the non-Practice flash owner still performs the drain")
    void notificationsStillDrainsFlashData() {
        Path owner = JS_DIR.resolve(OWNER);
        assertThat(owner).isRegularFile();
        assertThat(containsDrainMarker(owner))
                .as("notifications.js must continue reading data-flash-* attributes")
                .isTrue();
    }

    @Test
    @DisplayName("notifications inbox relies on the app-header script include only")
    void notificationsInboxDoesNotBootTheOwnerTwice() throws IOException {
        String inbox = Files.readString(
                Paths.get("src/main/resources/templates/notifications/index.html"),
                StandardCharsets.UTF_8);
        String header = Files.readString(
                Paths.get("src/main/resources/templates/fragments/app-header.html"),
                StandardCharsets.UTF_8);

        assertThat(inbox).doesNotContain("/js/notifications.js");
        assertThat(header).contains("/js/notifications.js");
    }

    private static List<Path> jsFiles() {
        try (Stream<Path> paths = Files.walk(JS_DIR)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot scan " + JS_DIR.toAbsolutePath(), exception);
        }
    }

    private static boolean isPractice(Path file) {
        Path relative = relative(file);
        return relative.getNameCount() > 1 && "practice".equals(relative.getName(0).toString());
    }

    private static Path relative(Path file) {
        return JS_DIR.relativize(file);
    }

    private static boolean containsDrainMarker(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            return DRAIN_MARKERS.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), exception);
        }
    }
}
