package bsoelch.concat;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Context for running the program
 */
@SuppressWarnings("ClassCanBeRecord")
final class IOContext {
    final InputStream stdIn;
    final PrintStream stdOut;
    final PrintStream stdErr;

    IOContext(InputStream stdIn, PrintStream stdOut, PrintStream stdErr) {
        this.stdIn = stdIn;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }
}
