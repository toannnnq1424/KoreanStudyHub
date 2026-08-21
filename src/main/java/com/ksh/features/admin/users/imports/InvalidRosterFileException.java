package com.ksh.features.admin.users.imports;

/**
 * Thrown when an uploaded roster file fails a whole-file validation: wrong
 * format, too large, too many rows, or no recognisable email column.
 *
 * <p>The controller turns this into an HTTP 400 whose body carries the
 * Vietnamese {@link #getMessage()} so the page script can render it through
 * {@code UlpToast.error(...)}.
 */
public class InvalidRosterFileException extends RuntimeException {

    public InvalidRosterFileException(String message) {
        super(message);
    }

    public InvalidRosterFileException(String message, Throwable cause) {
        super(message, cause);
    }
}