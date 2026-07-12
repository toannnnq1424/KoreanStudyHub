package com.ksh.features.practice.controller;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

final class PracticeByteRange {
    private static final int BUFFER_SIZE = 8192;

    private PracticeByteRange() {
    }

    static Selection parse(String header, long total) {
        if (total < 0L) {
            throw new IllegalArgumentException("Total byte size cannot be negative.");
        }
        if (header == null || header.isBlank()) {
            return new Selection(0L, total - 1L, false, false);
        }
        if (total == 0L) {
            return unsatisfiable(total);
        }
        String value = header.trim();
        if (!value.startsWith("bytes=")) {
            return unsatisfiable(total);
        }
        String spec = value.substring("bytes=".length()).trim();
        if (spec.isBlank() || spec.contains(",")) {
            return unsatisfiable(total);
        }
        int separator = spec.indexOf('-');
        if (separator < 0 || separator != spec.lastIndexOf('-')) {
            return unsatisfiable(total);
        }

        String startText = spec.substring(0, separator).trim();
        String endText = spec.substring(separator + 1).trim();
        if (startText.isEmpty()) {
            Long suffixLength = parseDigits(endText);
            if (suffixLength == null || suffixLength <= 0L) {
                return unsatisfiable(total);
            }
            long start = suffixLength >= total ? 0L : total - suffixLength;
            return new Selection(start, total - 1L, true, false);
        }

        Long start = parseDigits(startText);
        if (start == null || start >= total) {
            return unsatisfiable(total);
        }
        long end = total - 1L;
        if (!endText.isEmpty()) {
            Long parsedEnd = parseDigits(endText);
            if (parsedEnd == null || start > parsedEnd) {
                return unsatisfiable(total);
            }
            end = Math.min(parsedEnd, total - 1L);
        }
        return new Selection(start, end, true, false);
    }

    static StreamingResponseBody body(InputStreamSource source, Selection range) {
        return outputStream -> {
            try (InputStream input = source.open()) {
                skipFully(input, range.start());
                copyBounded(input, outputStream, range.length());
            }
        };
    }

    static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response has already been rejected, so no body can report this failure.
        }
    }

    private static void copyBounded(InputStream input, java.io.OutputStream output,
                                    long byteSize) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = byteSize;
        while (remaining > 0L) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                return;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            if (input.read() == -1) {
                throw new EOFException("Media stream ended before requested range.");
            }
            remaining--;
        }
    }

    private static Long parseDigits(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Selection unsatisfiable(long total) {
        return new Selection(0L, total - 1L, false, true);
    }

    @FunctionalInterface
    interface InputStreamSource {
        InputStream open() throws IOException;
    }

    record Selection(long start, long end, boolean partial, boolean unsatisfiable) {
        long length() {
            return unsatisfiable ? 0L : Math.max(0L, end - start + 1L);
        }
    }
}
