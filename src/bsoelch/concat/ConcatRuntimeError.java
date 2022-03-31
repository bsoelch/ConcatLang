package bsoelch.concat;

public class ConcatRuntimeError extends Exception {
    public ConcatRuntimeError(String message) {
        super(message);
    }

    interface Function<K,V>{
        K apply(V val) throws ConcatRuntimeError;
    }
}
