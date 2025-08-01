package ru.laefye.luxorium.player.exceptions;

public class NativeException extends RuntimeException {
    public NativeException(String message) {
        super(message);
    }

    public NativeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NativeException(Throwable cause) {
        super(cause);
    }
}
