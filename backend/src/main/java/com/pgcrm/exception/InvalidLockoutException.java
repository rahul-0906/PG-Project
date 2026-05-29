package com.pgcrm.exception;

public class InvalidLockoutException extends RuntimeException {
    public InvalidLockoutException(String message) {
        super(message);
    }
}
