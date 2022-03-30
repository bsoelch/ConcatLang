package bsoelch.concat;

public class SyntaxError extends Exception {
    final FilePosition pos;
    public SyntaxError(String message, FilePosition pos) {
        super(message);
        this.pos=pos;
    }

    public SyntaxError(SyntaxError parent, FilePosition pos) {
        super(parent.getMessage()+"\n  at "+parent.pos);
        this.pos=pos;
    }

    public SyntaxError(Throwable parent, FilePosition pos) {
        super(parent);
        this.pos=pos;
    }

    public interface ThrowingConsumer<T> {
        void accept(T val) throws SyntaxError;
    }
    public interface ThrowingFunction<K,V> {
        V apply(K val) throws SyntaxError;
    }
}
