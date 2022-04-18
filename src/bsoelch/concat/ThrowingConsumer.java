package bsoelch.concat;

public interface ThrowingConsumer<T, E extends Exception> {
    void accept(T val) throws E;
}
