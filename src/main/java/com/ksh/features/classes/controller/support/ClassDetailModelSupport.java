package com.ksh.features.classes.controller.support;

import com.ksh.entities.ClassEntity;
import com.ksh.security.Role;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import static com.ksh.common.IConstant.*;

/**
 * Shared model-population + URL/label helpers for the class detail screens.
 * Extracted from {@code ClassesController} during the file-size refactor so
 * the controllers stay focused on request mapping.
 *
 * <p>{@link #populateDetail} centralises the attributes consumed by the
 * detail layout across board, members, settings and placeholder tabs.
 */
@Component
public class ClassDetailModelSupport {

    public ClassDetailModelSupport() {
    }

    /**
     * Populates common model attributes required by the class-detail layout.
     *
     * <p>The invite system is retired; only common class and tab state remains.
     */
    public void populateDetail(Model model, ClassEntity clazz, String activeTab,
                               Long userId, Role role) {
        model.addAttribute(ATTR_CLAZZ, clazz);
        model.addAttribute(ATTR_ACTIVE_TAB, activeTab);

    }

    /** Builds the canonical URL for a single class — used by redirects and form actions. */
    public static String classUrl(Long id) {
        return URL_CLASSES_LIST + "/" + id;
    }

    /** Maps a tab key to its Vietnamese sidebar label; unknown keys pass through. */
    public static String labelFor(String tab) {
        return switch (tab) {
            case TAB_BOARD       -> "Bảng tin";
            case TAB_SCHEDULE    -> "Lịch học";
            case TAB_MEMBERS     -> "Thành viên";
            case TAB_ROLES       -> "Vai trò lớp";
            case TAB_GROUPS      -> "Nhóm học tập";
            case TAB_ASSIGNMENTS -> "Bài tập";
            case TAB_SCORES      -> "Bảng điểm";
            case TAB_PROGRESS    -> "Tiến độ";
            case TAB_LESSONS     -> "Bài giảng";
            case TAB_MATERIALS   -> "Tài liệu";
            case TAB_SETTINGS    -> "Cài đặt lớp học";
            // Fallback so future tabs render their raw key until labelled.
            default -> tab;
        };
    }
}
