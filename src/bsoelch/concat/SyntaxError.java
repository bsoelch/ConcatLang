package bsoelch.concat;

public class SyntaxError extends Exception {
    final Interpreter.FilePosition pos;
    public SyntaxError(String message, Interpreter.FilePosition pos) {
        super(message);
        this.pos=pos;
    }

    public SyntaxError(Throwable parent, Interpreter.FilePosition pos) {
        super(parent);
        this.pos=pos;
    }
}
