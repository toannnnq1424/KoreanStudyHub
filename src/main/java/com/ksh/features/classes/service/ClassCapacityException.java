package com.ksh.features.classes.service;

/** Raised when a class edit would make its capacity smaller than its roster. */
public final class ClassCapacityException extends IllegalArgumentException {

    public ClassCapacityException(long activeStudents) {
        super("Sĩ số tối đa không thể nhỏ hơn số học viên đang hoạt động ("
                + activeStudents + ")");
    }
}
