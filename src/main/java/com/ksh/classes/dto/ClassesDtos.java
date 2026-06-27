package com.ksh.classes.dto;

import com.ksh.classes.entity.ClassEntity;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** View-model DTOs for the lecturer's class management screen. */
public class ClassesDtos {

    /**
     * View-model for a single row in the class list. Rendered by
     * {@code templates/classes/manage.html}.
     *
     * <p>{@code thumbLabel} is derived from {@link #name} (first 2 characters,
     * uppercased). {@code gradientCss} is computed by the Service from the list index
     * so each class has a distinct color — see {@link com.ksh.classes.ClassGradient}.
     *
     * <p>Stat columns (studentCount/lectureCount/assignmentCount/materialCount)
     * return 0 temporarily for Sprint 2. Sprint 3/5 will wire in real counts.
     *
     * <p>{@code createdAtIso} is {@code created_at.toString()} in
     * ISO-8601 format, used for client-side sorting by creation date.
     */
    public record ClassRow(
            Long id,
            String name,
            String code,
            String gradientCss,
            int studentCount,
            int lectureCount,
            int assignmentCount,
            int materialCount,
            String createdAtIso
    ) {
        /** Returns first 2 characters of class name, uppercased, for use as a thumbnail label. */
        public String thumbLabel() {
            if (name == null || name.isBlank()) return "?";
            String trimmed = name.trim();
            int end = Math.min(2, trimmed.length());
            return trimmed.substring(0, end).toUpperCase();
        }
    }

    /**
     * Form payload for both {@code GET /lecturer/classes/new} + {@code /edit}
     * and {@code POST /lecturer/classes} + {@code /{id}}.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>{@code name}: required, 3–300 characters</li>
     *   <li>{@code description}: optional, ≤2000 characters</li>
     *   <li>{@code maxStudents}: optional, 1–1000</li>
     *   <li>{@code endDate} must be STRICTLY after {@code startDate} when both are present
     *       (equal dates are also rejected to prevent "1-day classes") — checked via
     *       {@link #isDateRangeValid()}</li>
     * </ul>
     */
    public record ClassForm(
            @NotBlank(message = "Tên lớp không được để trống")
            @Size(min = 3, max = 300, message = "Tên lớp 3–300 ký tự")
            String name,

            @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
            String description,

            LocalDate startDate,
            LocalDate endDate,

            @Min(value = 1, message = "Sĩ số tối thiểu là 1")
            @Max(value = 1000, message = "Sĩ số tối đa là 1000")
            Integer maxStudents
    ) {

        /** Empty form, used when rendering {@code GET /new}. */
        public static ClassForm empty() {
            return new ClassForm("", "", null, null, 100);
        }

        /** Converts an entity to a form for pre-filling when editing. */
        public static ClassForm fromEntity(ClassEntity e) {
            return new ClassForm(
                    e.getName(),
                    e.getDescription(),
                    e.getStartDate(),
                    e.getEndDate(),
                    e.getMaxStudents()
            );
        }

        /**
         * Bean Validation constraint: {@code endDate} must be STRICTLY after
         * {@code startDate} when both are non-null. Equal dates are also rejected.
         * NULL values (one or both) are permitted.
         *
         * <p>Violations are bound to the field {@code endDate} via {@code @AssertTrue}
         * — Spring/Hibernate Validator derives the property name from the method name
         * ({@code isDateRangeValid} → {@code dateRangeValid}). The controller
         * will rebind the error to field {@code endDate} for correct UX display.
         */
        @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
        public boolean isDateRangeValid() {
            if (startDate == null || endDate == null) return true;
            return endDate.isAfter(startDate);
        }
    }
}
