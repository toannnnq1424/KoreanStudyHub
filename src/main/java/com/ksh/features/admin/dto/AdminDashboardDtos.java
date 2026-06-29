package com.ksh.features.admin.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** DTOs for the Admin Dashboard screen. */
public class AdminDashboardDtos {

    /** The 4 main numbers displayed on the 4 stat cards. */
    public record DashboardStats(
            Long userCount,
            Long classCount,
            Long departmentCount,
            Long courseCount
    ) {}

    /** A row/entry in the user-by-role chart (donut). */
    public record UserRoleCount(String role, Long count) {
        /** Gradient color for donut slice, by role. */
        public String color() {
            return switch (role) {
                case "STUDENT" -> "#42A5F5";
                case "LECTURER" -> "#26A69A";
                case "HEAD" -> "#7E57C2";
                case "ADMIN" -> "#EF5350";
                default -> "#9AA0AB";
            };
        }

        /** Vietnamese label for legend. */
        public String displayRole() {
            return switch (role) {
                case "STUDENT" -> "Sinh viên";
                case "LECTURER" -> "Giảng viên";
                case "HEAD" -> "Trưởng bộ môn";
                case "ADMIN" -> "Quản trị viên";
                default -> role;
            };
        }
    }

    /** A row/entry in the "5 most recent classes" / recent activity table. */
    public record RecentClass(
            Long id,
            String name,
            String code,
            String status,
            LocalDateTime createdAt,
            String lecturerName
    ) {
        private static final DateTimeFormatter DF =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));

        public String displayCreatedAt() {
            return createdAt != null ? createdAt.format(DF) : "—";
        }

        /** Vietnamese label for class status. */
        public String displayStatus() {
            return switch (status) {
                case "UPCOMING" -> "Sắp khai giảng";
                case "ACTIVE" -> "Đang hoạt động";
                case "COMPLETED" -> "Đã kết thúc";
                case "CANCELLED" -> "Đã huỷ";
                default -> status;
            };
        }
    }
}