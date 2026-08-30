package com.ksh.features.admin.users.imports.parser;

import com.ksh.features.admin.users.imports.InvalidRosterFileException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Defensive .xls/.xlsx parser for the admin account-import preview. */
@Component
public class UserRosterParser {
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 500;
    private static final byte[] MAGIC_ZIP = {0x50, 0x4b, 0x03, 0x04};
    private static final byte[] MAGIC_OLE2 = {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};
    private static final Map<String, String> HEADER_ALIASES = aliases();

    static {
        ZipSecureFile.setMinInflateRatio(0.005);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
    }

    public record RawRosterRow(int rowNumber, String email, String fullName,
                               String role, String subject, String phone) {}
    public record ParsedRoster(String fileName, List<RawRosterRow> rows) {}

    public ParsedRoster parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRosterFileException("Vui lòng chọn file Excel để tải lên.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidRosterFileException("File vượt quá kích thước tối đa 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidRosterFileException("Không đọc được file đã tải lên.", ex);
        }
        if (!looksLikeExcel(bytes)) {
            throw new InvalidRosterFileException("File không phải Excel .xlsx hoặc .xls hợp lệ.");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidRosterFileException("File Excel không có sheet nào.");
            }
            return read(workbook.getSheetAt(0), file.getOriginalFilename());
        } catch (InvalidRosterFileException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new InvalidRosterFileException("Không đọc được nội dung file Excel.", ex);
        }
    }

    private ParsedRoster read(Sheet sheet, String fileName) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null || header.getLastCellNum() <= 0) {
            throw new InvalidRosterFileException("Sheet đầu tiên không có dòng tiêu đề.");
        }
        DataFormatter formatter = new DataFormatter(Locale.US);
        Map<Integer, String> columns = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String resolved = resolveHeader(value(header.getCell(i), formatter));
            if (resolved != null && seen.add(resolved)) columns.put(i, resolved);
        }
        if (!seen.contains("email")) {
            throw new InvalidRosterFileException("File phải có cột Email ở dòng tiêu đề.");
        }

        List<RawRosterRow> rows = new ArrayList<>();
        for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            Map<String, String> cells = new HashMap<>();
            boolean any = false;
            for (Map.Entry<Integer, String> column : columns.entrySet()) {
                String text = value(row.getCell(column.getKey()), formatter);
                cells.put(column.getValue(), text);
                any |= !text.isBlank();
            }
            if (!any) continue;
            if (rows.size() >= MAX_DATA_ROWS) {
                throw new InvalidRosterFileException("Mỗi lần import tối đa 500 dòng dữ liệu.");
            }
            rows.add(new RawRosterRow(index + 1,
                    cells.getOrDefault("email", ""),
                    cells.getOrDefault("fullName", ""),
                    cells.getOrDefault("role", ""),
                    cells.getOrDefault("subject", ""),
                    cells.getOrDefault("phone", "")));
        }
        return new ParsedRoster(fileName == null ? "import.xlsx" : fileName, rows);
    }

    static String resolveHeader(String raw) {
        String resolved = HEADER_ALIASES.get(normalize(raw));
        if (resolved != null) return resolved;
        if (raw == null) return null;
        String trimmed = raw.trim();
        int open = trimmed.lastIndexOf('(');
        if (open <= 0 || !trimmed.endsWith(")")) return null;
        return HEADER_ALIASES.get(normalize(trimmed.substring(0, open)));
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String value = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ").trim();
        return value.replaceAll("\\s+", " ");
    }

    private static String value(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static boolean looksLikeExcel(byte[] bytes) {
        return bytes.length >= MAGIC_ZIP.length
                && Arrays.equals(Arrays.copyOf(bytes, MAGIC_ZIP.length), MAGIC_ZIP)
                || bytes.length >= MAGIC_OLE2.length
                && Arrays.equals(Arrays.copyOf(bytes, MAGIC_OLE2.length), MAGIC_OLE2);
    }

    private static Map<String, String> aliases() {
        Map<String, String> map = new HashMap<>();
        add(map, "email", "email", "e mail", "mail", "dia chi email");
        add(map, "fullName", "ho ten", "ho va ten", "fullname", "full name", "name", "ten day du");
        add(map, "role", "role", "vai tro", "quyen", "chuc vu");
        add(map, "subject", "subject", "ma mon", "bo mon", "khoa bo mon", "subject code");
        add(map, "phone", "so dien thoai", "sdt", "phone", "phone number", "mobile");
        return Map.copyOf(map);
    }

    private static void add(Map<String, String> map, String key, String... aliases) {
        for (String alias : aliases) map.put(alias, key);
    }
}
