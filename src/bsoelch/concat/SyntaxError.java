package bsoelch.concat;

public class SyntaxError extends Error {
    public SyntaxError(String message) {
        super(message);
    }

    public SyntaxError(Throwable parent) {
        super(parent);
    }
}
