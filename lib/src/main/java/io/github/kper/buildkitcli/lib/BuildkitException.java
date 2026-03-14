package io.github.kper.buildkitcli.lib;

/**
 * Exception when error occurs during the connection to the buildkit daemon.
 */
public class BuildkitException extends Exception {
    /**
     * Create an exception with a message.
     */
    public BuildkitException(String message) {
        super(message);
    }

    /**
     * Create an exception with a message and a cause.
     */
    public BuildkitException(String message, Throwable cause) {
        super(message, cause);
    }
}
