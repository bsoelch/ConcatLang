package bsoelch.concat;

/**
 * A wrapper for TypeError that can be thrown inside functional interfaces
 */
class WrappedConcatError extends RuntimeException {
    final ConcatRuntimeError wrapped;

    public WrappedConcatError(ConcatRuntimeError wrapped) {
        this.wrapped = wrapped;
    }
}
