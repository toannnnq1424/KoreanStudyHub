package com.ksh.features.admin.users.imports.validator;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.users.dto.AccountStatusLabel;
import com.ksh.features.admin.users.imports.dto.UserImportRow;
import com.ksh.features.admin.users.imports.dto.UserImportRowStatus;
import com.ksh.features.admin.users.imports.parser.UserRosterParser;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifies the raw rows produced by {@link UserRosterParser}, attaching a
 * final {@link UserImportRowStatus} plus the resolved role and department.
 *
 * <p>Validation order (first match wins):
 * <ol>
 *   <li>Blank email → {@code MISSING_EMAIL}.</li>
 *   <li>Email repeated within the uploaded file → {@code DUPLICATE_IN_FILE}.</li>
 *   <li>Email malformed → {@code INVALID_EMAIL}.</li>
 *   <li>Role named but outside the closed set → {@code INVALID_ROLE}. A blank
 *       role is not an error: it resolves to {@link #DEFAULT_ROLE} and the row
 *       is flagged {@code roleDefaulted} so the preview can say so.</li>
 *   <li>Department named but unknown → {@code UNKNOWN_DEPARTMENT}.</li>
 *   <li>Email already belongs to an account → {@code ALREADY_EXISTS}, carrying
 *       that account's current status label.</li>
 *   <li>Otherwise → {@code CREATABLE}.</li>
 * </ol>
 *
 * <p>Existing-account matching is case-insensitive and deliberately includes
 * soft-deleted rows: {@code idx_users_email} is unique across the whole table,
 * so a deleted account still owns its address and creating a second one would
 * fail on the index rather than be reported as a skipped row.
 *
 * <p>The validator never mutates the database.
 */
@Component
public class UserRosterRowValidator {

    /** Same pragmatic email shape the class-roster import already accepts. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** Role assigned when the file omits the column or leaves the cell blank. */
    private static final Role DEFAULT_ROLE = Role.STUDENT;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public UserRosterRowValidator(UserRepository userRepository,
                                  DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Validates {@code rawRows} and returns the fully-populated rows in the
     * same order.
     *
     * @param rawRows rows straight from the parser
     * @return one {@link UserImportRow} per input row, each carrying a status
     */
    public List<UserImportRow> validate(List<UserRosterParser.RawRosterRow> rawRows) {
        List<UserImportRow> result = new ArrayList<>(rawRows.size());
        Set<String> seenEmails = new HashSet<>();
        Map<String, Department> departmentsByKey = loadDepartments();

        for (UserRosterParser.RawRosterRow raw : rawRows) {
            UserImportRow row = new UserImportRow(raw.rowNumber(), raw.email(), raw.fullName(),
                    raw.role(), raw.subject(), raw.phone());
            result.add(row);

            String email = blankToNull(raw.email());
            if (email == null) {
                row.mark(UserImportRowStatus.MISSING_EMAIL);
                continue;
            }

            // Duplicate-within-file is checked before the pattern check: a
            // repeat is a repeat even when the second copy is also malformed.
            String emailKey = email.toLowerCase(Locale.ROOT);
            if (!seenEmails.add(emailKey)) {
                row.mark(UserImportRowStatus.DUPLICATE_IN_FILE);
                continue;
            }

            if (!EMAIL_PATTERN.matcher(email).matches()) {
                row.mark(UserImportRowStatus.INVALID_EMAIL);
                continue;
            }

            boolean roleDefaulted = blankToNull(raw.role()) == null;
            Role role = resolveRole(raw.role());
            if (role == null) {
                row.mark(UserImportRowStatus.INVALID_ROLE,
                        "Chấp nhận: STUDENT, LECTURER, HEAD, ADMIN");
                continue;
            }

            String subjectRaw = blankToNull(raw.subject());
            Long departmentId = null;
            if (subjectRaw != null) {
                Department department = departmentsByKey.get(normalizeKey(subjectRaw));
                if (department == null) {
                    row.mark(UserImportRowStatus.UNKNOWN_SUBJECT, subjectRaw);
                    continue;
                }
                departmentId = department.getId();
            }

            row.resolve(role, departmentId, roleDefaulted);

            User existing = userRepository.findByEmailIncludingDeleted(emailKey).orElse(null);
            if (existing != null) {
                row.attachExistingStatusLabel(AccountStatusLabel.resolve(
                        existing.isDeleted(), existing.isLocked(),
                        existing.isActive(), existing.getActivatedAt()));
                row.mark(UserImportRowStatus.ALREADY_EXISTS);
                continue;
            }

            // The defaulted role only matters on rows that actually create an
            // account, so the note rides along with CREATABLE and not with the
            // skipped/error statuses handled above.
            row.mark(UserImportRowStatus.CREATABLE,
                    roleDefaulted ? "Vai trò để trống → mặc định STUDENT" : null);
        }

        return result;
    }

    /**
     * Resolves a role cell. A blank cell falls back to {@link #DEFAULT_ROLE};
     * anything outside the closed set returns {@code null} so the caller can
     * mark the row invalid.
     */
    private static Role resolveRole(String raw) {
        String value = blankToNull(raw);
        if (value == null) return DEFAULT_ROLE;
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Indexes departments by both name and code so the file may name either.
     * Keys are normalised the same way as headers (diacritic-free, lowercase).
     */
    private Map<String, Department> loadDepartments() {
        Map<String, Department> map = new HashMap<>();
        for (Department department : departmentRepository.findAll()) {
            if (department.getName() != null) {
                map.putIfAbsent(normalizeKey(department.getName()), department);
            }
            if (department.getCode() != null) {
                map.putIfAbsent(normalizeKey(department.getCode()), department);
            }
        }
        return map;
    }

    private static String normalizeKey(String raw) {
        return UserRosterParser.normalize(raw);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}