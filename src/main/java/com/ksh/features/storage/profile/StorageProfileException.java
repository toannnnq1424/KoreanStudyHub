package com.ksh.features.storage.profile;

public final class StorageProfileException extends RuntimeException {
    private final String errorCode;

    public StorageProfileException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public StorageProfileException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
