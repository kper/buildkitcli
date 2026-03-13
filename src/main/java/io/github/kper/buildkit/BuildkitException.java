package io.github.kper.buildkit;

public class BuildkitException extends Exception {
    public BuildkitException(String message) {
        super(message);
    }

    public BuildkitException(String message, Throwable cause) {
        super(message, cause);
    }
}
