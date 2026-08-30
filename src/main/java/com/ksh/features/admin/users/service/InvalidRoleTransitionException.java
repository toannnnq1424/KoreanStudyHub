package com.ksh.features.admin.users.service;

/** Raised when an administrator tries to move an identity between account categories. */
public class InvalidRoleTransitionException extends RuntimeException {
    public InvalidRoleTransitionException(String message) {
        super(message);
    }
}
