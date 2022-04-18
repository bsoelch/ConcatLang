package bsoelch.concat;

public interface ThrowingSupplier<T, E extends Exception> {
    T get() throws E;
}
