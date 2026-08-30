package com.ksh.features.classes.imports.service;

import com.ksh.entities.User;
import com.ksh.entities.Enrollment;
import com.ksh.features.classes.imports.dto.ImportRow;
import com.ksh.features.classes.imports.dto.ImportRowStatus;
import com.ksh.features.classes.imports.dto.RowOutcome;
import com.ksh.features.classes.repository.EnrollmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.security.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Maps the status of a previewed {@link ImportRow} to a database action and a
 * {@link RowOutcome} contribution. Extracted from {@link ImportStudentsService}
 * so the confirm-loop body stays at one level of abstraction.
 *
 * <p>The processor never flushes — it appends to the caller-supplied
 * {@code pending} list, leaving batch sizing and persistence to the service.
 */
@Component
class ImportRowProcessor {

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    ImportRowProcessor(EnrollmentRepository enrollmentRepository,
                       UserRepository userRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Routes a single row to the appropriate handler based on its preview status.
     */
    RowOutcome process(ImportRow row, Long classId, List<Enrollment> pending,
                       boolean seatAvailable) {
        ImportRowStatus status = row.getStatus();
        return switch (status) {
            case OK, RE_ENROLL -> processCurrent(row, classId, pending, seatAvailable);
            case DUPLICATE_IN_CLASS -> RowOutcome.SKIPPED_DUP;
            default -> status.isError() ? RowOutcome.SKIPPED_ERR : RowOutcome.ZERO;
        };
    }

    /** Revalidates mutable account/enrollment state under locks before writing. */
    private RowOutcome processCurrent(ImportRow row, Long classId,
                                      List<Enrollment> pending, boolean seatAvailable) {
        User user = row.getUserId() == null ? null
                : userRepository.findByIdForUpdate(row.getUserId()).orElse(null);
        if (user == null || user.getRole() != Role.STUDENT
                || !user.isActive() || user.isLocked() || user.isDeleted()) {
            row.mark(ImportRowStatus.PERSISTENCE_FAILED,
                    "Tài khoản không còn đủ điều kiện giữa lúc preview và xác nhận");
            return RowOutcome.FAILED;
        }
        Optional<Enrollment> existing = enrollmentRepository
                .findByUserIdAndClassIdForUpdate(user.getId(), classId);
        if (existing.isEmpty()) {
            if (!seatAvailable) {
                row.mark(ImportRowStatus.CLASS_FULL);
                return RowOutcome.FAILED;
            }
            pending.add(Enrollment.createFor(user, classId, Enrollment.JoinedVia.IMPORT, null));
            return RowOutcome.IMPORTED;
        }
        Enrollment e = existing.get();
        if (Enrollment.STATUS_ACTIVE.equals(e.getStatus())) {
            return RowOutcome.SKIPPED_DUP;
        }
        if (Enrollment.STATUS_COMPLETED.equals(e.getStatus())) {
            row.mark(ImportRowStatus.ENROLLMENT_COMPLETED);
            return RowOutcome.SKIPPED_ERR;
        }
        if (!seatAvailable) {
            row.mark(ImportRowStatus.CLASS_FULL);
            return RowOutcome.FAILED;
        }
        e.reactivateVia(Enrollment.JoinedVia.IMPORT, null);
        pending.add(e);
        return RowOutcome.REACTIVATED;
    }
}
