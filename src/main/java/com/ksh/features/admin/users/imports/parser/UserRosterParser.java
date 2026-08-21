package com.ksh.features.admin.users.imports.parser;

import com.ksh.features.admin.users.imports.InvalidRosterFileException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses an uploaded admin roster (.xls / .xlsx) into raw rows carrying email,
 * full name, role, subject, and phone.
 *
 * <p>Whole-file concerns are validated here: format via magic bytes rather than
 * file extension, size (≤2 MB), row count (≤500 data rows), sheet existence,
 * and the presence of an email column. Every failure raises
 * {@link InvalidRosterFileException} with a Vietnamese message ready to display.
 *
 * <p>Header matching is forgiving: values are lowercased, stripped of
 * Vietnamese diacritics, and reduced to single-spaced alphanumerics before
 * lookup, so "Họ và tên", "ho ten", and "FullName" all resolve to one column.
 * A trailing parenthesised annotation is dropped as a fallback, so the
 * self-documenting headers of the generated template — "Email (bắt buộc)" —
 * import back in; see {@link #resolveHeader(String)}.
 */
@Component
public class UserRosterParser {

    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 500;

    private static final byte[] MAGIC_ZIP = {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04};
    private static final byte[] MAGIC_OLE2 = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1};

    // Canonical column keys recognized by the validator.
    static final String COL_EMAIL = "email";
    static final String COL_FULL_NAME = "fullName";
    static final String COL_ROLE = "role";
    static final String COL_SUBJECT = "subject";
    static final String COL_PHONE = "phone";

    /** Normalised header → canonical key; see {@link #normalize(String)}. */
    private static final Map<String, String> HEADER_ALIASES = buildAliases();

    static {
        // Reject xlsx files with an abnormally high compression ratio (zip bomb
        // defence), matching the limits the class-roster parser already applies.
        ZipSecureFile.setMinInflateRatio(0.005);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
    }

    /** The spreadsheet identity plus its parsed rows; produced by {@link #parse}. */
    public record ParsedRoster(String fileName, List<String> headers, List<RawRosterRow> rows) {}

    /** Raw row content before validation. Cells already trimmed; missing cells → empty strings. */
    public record RawRosterRow(int rowNumber, String email, String fullName,
                               String role, String subject, String phone) {}

    /**
     * Validates the supplied file and produces its raw rows. The first
     * non-empty row is the header row; data rows follow it.
     *
     * @param file the multipart upload
     * @return the parsed roster
     * @throws InvalidRosterFileException on any whole-file failure
     */
    public ParsedRoster parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRosterFileException("Vui lòng chọn một file Excel để tải lên.");
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
            throw new InvalidRosterFileException("File không phải định dạng Excel (.xlsx hoặc .xls).");
        }

        try (InputStream in = new BufferedInputStream(file.getInputStream());
             Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidRosterFileException("File Excel không có sheet nào.");
            }
            return readSheet(workbook.getSheetAt(0), file.getOriginalFilename());
        } catch (NotOfficeXmlFileException ex) {
            throw new InvalidRosterFileException("File không phải định dạng Excel (.xlsx hoặc .xls).", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidRosterFileException("Không đọc được nội dung file Excel.", ex);
        }
    }

    private ParsedRoster readSheet(Sheet sheet, String fileName) {
        int firstRow = sheet.getFirstRowNum();
        Row headerRow = sheet.getRow(firstRow);
        if (headerRow == null) {
            throw new InvalidRosterFileException("Sheet đầu tiên không có dòng tiêu đề.");
        }

        DataFormatter formatter = new DataFormatter(Locale.US);
        Map<Integer, String> headerByIndex = new HashMap<>();
        List<String> headersOriginal = new ArrayList<>();
        Set<String> seenColumns = new HashSet<>();

        short lastCell = headerRow.getLastCellNum();
        for (int c = 0; c < lastCell; c++) {
            Cell cell = headerRow.getCell(c);
            String raw = cell == null ? "" : formatter.formatCellValue(cell).trim();
            headersOriginal.add(raw);
            String canonical = resolveHeader(raw);
            if (canonical != null && !seenColumns.contains(canonical)) {
                headerByIndex.put(c, canonical);
                seenColumns.add(canonical);
            }
        }

        // Email is the only identity this import matches on; without it there
        // is nothing to create accounts from, so the whole file is rejected.
        if (!seenColumns.contains(COL_EMAIL)) {
            throw new InvalidRosterFileException("File phải có cột Email ở dòng tiêu đề.");
        }

        int lastRow = sheet.getLastRowNum();
        List<RawRosterRow> rows = new ArrayList<>();
        int dataRowsParsed = 0;

        for (int r = firstRow + 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String email = "";
            String fullName = "";
            String role = "";
            String subject = "";
            String phone = "";
            boolean anyValue = false;

            for (Map.Entry<Integer, String> entry : headerByIndex.entrySet()) {
                Cell cell = row.getCell(entry.getKey());
                String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (!value.isEmpty()) anyValue = true;
                switch (entry.getValue()) {
                    case COL_EMAIL -> email = value;
                    case COL_FULL_NAME -> fullName = value;
                    case COL_ROLE -> role = value;
                    case COL_SUBJECT -> subject = value;
                    case COL_PHONE -> phone = value;
                    default -> { /* unknown canonical key — ignore */ }
                }
            }

            if (!anyValue) {
                continue; // skip wholly blank rows so trailing ones do not count toward the cap
            }

            dataRowsParsed++;
            if (dataRowsParsed > MAX_DATA_ROWS) {
                throw new InvalidRosterFileException(
                        "Vượt quá " + MAX_DATA_ROWS + " dòng cho phép trong một lần import.");
            }

            // rowNumber is 1-based, matching what Excel shows to the admin.
            rows.add(new RawRosterRow(r + 1, email, fullName, role, subject, phone));
        }

        return new ParsedRoster(fileName, headersOriginal, rows);
    }

    /**
     * Returns {@code true} when the leading bytes match either the ZIP/OOXML
     * magic ({@code PK\x03\x04}) or the legacy OLE2 magic.
     */
    private static boolean looksLikeExcel(byte[] bytes) {
        if (bytes.length >= MAGIC_OLE2.length
                && Arrays.equals(Arrays.copyOf(bytes, MAGIC_OLE2.length), MAGIC_OLE2)) {
            return true;
        }
        return bytes.length >= MAGIC_ZIP.length
                && Arrays.equals(Arrays.copyOf(bytes, MAGIC_ZIP.length), MAGIC_ZIP);
    }

    /**
     * Resolves one header cell to a canonical column key, or {@code null} when
     * the cell names no column this import understands.
     *
     * <p>Tried in order: the header as written, then the header with a trailing
     * parenthesised group removed. The generated template annotates its headers
     * ("Email (bắt buộc)", "Vai trò (có thể để trống…)") so the rules are legible
     * while the file is being edited offline; without the second attempt the
     * template we hand out would not import back in.
     *
     * <p>The fallback runs only when the header as written matched nothing, so a
     * name that is itself an alias always wins over its stripped form. A header
     * that matches nothing either way stays unrecognised and its cells are not
     * read — stripping never invents a column, it only reaches the bare name.
     * "Email (cá nhân)" therefore lands on Email, which is intended: for these
     * five columns the bare name carries the meaning and a parenthesised
     * qualifier only narrows it.
     */
    static String resolveHeader(String raw) {
        String canonical = HEADER_ALIASES.get(normalize(raw));
        if (canonical != null) {
            return canonical;
        }
        String withoutAnnotation = stripTrailingAnnotation(raw);
        return withoutAnnotation == null ? null : HEADER_ALIASES.get(normalize(withoutAnnotation));
    }

    /**
     * Removes one trailing {@code (...)} group, returning {@code null} when the
     * header has none or when nothing but the annotation would be left.
     */
    private static String stripTrailingAnnotation(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (!trimmed.endsWith(")")) return null;
        int open = trimmed.lastIndexOf('(');
        if (open <= 0) return null; // no opener, or the cell is only an annotation
        String head = trimmed.substring(0, open).trim();
        return head.isEmpty() ? null : head;
    }

    /**
     * Lowercases, strips Vietnamese diacritics, removes punctuation, collapses
     * whitespace. Public so the row validator can match subject names and
     * codes with exactly the tolerance applied to header cells.
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // NFD does not decompose đ/Đ, so map it explicitly.
        decomposed = decomposed.replace('đ', 'd');
        decomposed = decomposed.replaceAll("[^a-z0-9]+", " ").trim();
        return decomposed.replaceAll("\\s+", " ");
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> map = new HashMap<>();

        for (String alias : List.of("email", "e mail", "mail", "dia chi email", "dia chi mail")) {
            map.put(alias, COL_EMAIL);
        }
        for (String alias : List.of("ho ten", "ho va ten", "ten", "fullname", "full name",
                "name", "hoten", "ten day du")) {
            map.put(alias, COL_FULL_NAME);
        }
        for (String alias : List.of("role", "vai tro", "quyen", "chuc vu", "nhom quyen")) {
            map.put(alias, COL_ROLE);
        }
        for (String alias : List.of("subject", "khoa", "bo mon", "khoa bo mon",
                "don vi", "ma khoa", "subject code")) {
            map.put(alias, COL_SUBJECT);
        }
        for (String alias : List.of("so dien thoai", "sdt", "phone", "phone number",
                "dien thoai", "mobile")) {
            map.put(alias, COL_PHONE);
        }

        return Map.copyOf(map);
    }
}