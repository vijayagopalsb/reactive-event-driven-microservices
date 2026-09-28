package com.reactiveevent.platform.auth.application.error;

public final class InvalidCredentialsException extends IllegalArgumentException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
