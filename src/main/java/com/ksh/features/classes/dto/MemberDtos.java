package com.ksh.features.classes.dto;

/** View-model DTOs for class detail page — Members tab. */
public class MemberDtos {

    /**
     * A row in the members list table (Members tab).
     * Rendered by {@code templates/classes/detail-members.html}.
     */
    public record MemberRow(
            Long userId,
            String fullName,
            String avatarLabel,
            String avatarGradient,
            String email,
            String phone,
            String joinedVia
    ) {
        /** Displays formatted phone number (fallback to "—" if null or empty). */
        public String displayPhone() {
            return phone == null || phone.isBlank() ? "—" : phone;
        }

        /** Displays joined-via badge: CODE/LINK/IMPORT/MANUAL → Vietnamese labels. */
        public String displayJoinedVia() {
            if (joinedVia == null) return "—";
            return switch (joinedVia) {
                case "CODE" -> "Mã mời";
                case "LINK" -> "Link";
                case "IMPORT" -> "Import";
                case "MANUAL" -> "Thủ công";
                default -> joinedVia;
            };
        }
    }
}
