package com.ksh.features.assignments.service;

import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StorageTransactionLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.UUID;

/** Private, bounded submission files. Never exposed through a public upload URL. */
@Service
public class AssignmentAttachmentStorage {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private final ObjectStorage storage;
    public AssignmentAttachmentStorage(ObjectStorage storage) { this.storage = storage; }

    public String store(MultipartFile file, Long assignmentId, Long userId) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Tệp bài nộp tối đa 10 MB");
        String name = file.getOriginalFilename();
        String ext = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Tệp bài nộp tối đa 10 MB");
            boolean valid = switch (ext) {
                case "pdf" -> starts(bytes, 0x25, 0x50, 0x44, 0x46, 0x2d);
                case "png" -> starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
                case "jpg", "jpeg" -> starts(bytes, 0xff, 0xd8, 0xff);
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Chỉ nhận ảnh JPG/PNG hoặc PDF có định dạng hợp lệ");
            String key = "assignment-submissions/" + assignmentId + "/" + userId + "/" + UUID.randomUUID() + "." + ext;
            StorageTransactionLifecycle.deleteOnRollback(() -> delete(key));
            try { storage.put(key, new ByteArrayInputStream(bytes), mime(ext), bytes.length); }
            catch (IOException ex) { delete(key); throw ex; }
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException("Không thể lưu tệp bài nộp", ex);
        }
    }

    public byte[] read(String key, Long assignmentId, Long userId) throws IOException {
        String prefix = "assignment-submissions/" + assignmentId + "/" + userId + "/";
        if (key == null || !key.startsWith(prefix) || !key.substring(prefix.length()).matches("[a-f0-9-]{36}\\.(pdf|png|jpg|jpeg)"))
            throw new jakarta.persistence.EntityNotFoundException("Không tìm thấy tệp bài nộp");
        try (var object = storage.open(key)) {
            byte[] bytes = object.inputStream().readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Submission attachment exceeds size limit");
            return bytes;
        }
    }
    public static String mime(String ext) { return switch (ext) { case "pdf" -> "application/pdf"; case "png" -> "image/png"; default -> "image/jpeg"; }; }
    private static boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((bytes[i] & 255) != signature[i]) return false;
        return true;
    }
    private void delete(String key) { try { storage.delete(key); } catch (IOException ex) { throw new UncheckedIOException(ex); } }
}
