package bsoelch.concat;

public class SyntaxError extends Exception {
    final Interpreter.TokenPosition pos;
    public SyntaxError(String message, Interpreter.TokenPosition pos) {
        super(message);
        this.pos=pos;
    }

    public SyntaxError(Throwable parent, Interpreter.TokenPosition pos) {
        super(parent);
        this.pos=pos;
    }
}
