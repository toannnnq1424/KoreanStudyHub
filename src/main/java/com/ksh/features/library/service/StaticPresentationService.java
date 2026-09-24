package com.ksh.features.library.service;

import com.ksh.features.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

/** Local, bounded static conversion. Callers must authorize the original on every request. */
@Service
public class StaticPresentationService {
    private final ObjectStorage storage;
    private final String executable;
    public StaticPresentationService(@org.springframework.beans.factory.annotation.Qualifier("objectStorage") ObjectStorage storage, @Value("${ksh.preview.soffice:soffice}") String executable) {
        this.storage = storage;
        this.executable = executable;
    }
    public synchronized byte[] pdf(String key, String filename) throws IOException {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("pdf", "ppt", "pptx").contains(ext)) throw new IllegalArgumentException("Chỉ hỗ trợ PDF/PPT/PPTX");
        byte[] input;
        try (var object = storage.open(key)) {
            input = object.inputStream().readNBytes(25 * 1024 * 1024 + 1);
        }
        if (input.length > 25 * 1024 * 1024) throw new IOException("Tệp trình chiếu tối đa 25 MB");
        if (ext.equals("pdf")) return input;
        Path dir = Files.createTempDirectory("ksh-slides-");
        Process process = null;
        try {
            Path profile = Files.createDirectories(dir.resolve("profile/user"));
            Files.writeString(profile.resolve("registrymodifications.xcu"),
                    "<?xml version=\"1.0\"?><oor:items xmlns:oor=\"http://openoffice.org/2001/registry\"><item oor:path=\"/org.openoffice.Office.Common/Security/Scripting\"><prop oor:name=\"MacroSecurityLevel\" oor:op=\"fuse\"><value>3</value></prop></item></oor:items>");
            Path source = dir.resolve("slides." + ext);
            Files.write(source, input);
            process = new ProcessBuilder(executable, "-env:UserInstallation=" + dir.resolve("profile").toUri(),
                    "--headless", "--norestore", "--convert-to", "pdf:impress_pdf_Export", "--outdir", dir.toString(), source.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IOException("Chuyển đổi quá thời gian; hãy tải file gốc");
            Path result = dir.resolve("slides.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(result) || Files.size(result) > 50L * 1024 * 1024)
                throw new IOException("Không thể chuyển đổi slide; hãy tải file gốc");
            return Files.readAllBytes(result);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Chuyển đổi bị ngắt", ex);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
