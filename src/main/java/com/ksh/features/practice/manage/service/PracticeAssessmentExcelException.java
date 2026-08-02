package com.ksh.features.practice.manage.service;

/** Stable fail-closed error at the Excel workbook contract boundary. */
public class PracticeAssessmentExcelException extends IllegalArgumentException {

    private final String code;

    public PracticeAssessmentExcelException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PracticeAssessmentExcelException(
            String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
