package bsoelch.concat;

public class SyntaxError extends Exception {
    final FilePosition pos;
    public SyntaxError(String message, FilePosition pos) {
        super(message);
        this.pos=pos;
    }

    public SyntaxError(Throwable parent, FilePosition pos) {
        super(parent);
        this.pos=pos;
    }
}
