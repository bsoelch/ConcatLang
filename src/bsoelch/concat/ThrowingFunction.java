package bsoelch.concat;

interface ThrowingFunction<K, V, E extends Exception> {
    V apply(K val) throws E;
}
