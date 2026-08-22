package com.ksh.features.admin.users.imports;

/** Whole-file or staged-session failure reported to the import modal. */
public class InvalidRosterFileException extends RuntimeException {
    public InvalidRosterFileException(String message) {
        super(message);
    }

    public InvalidRosterFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
