package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.Value;

import java.io.InputStream;
import java.io.OutputStream;

public interface FileStream {
    static FileStream of(InputStream in,boolean closeable) {
        return new FileInStream(in,closeable);
    }
    static FileStream of(OutputStream out,boolean closeable) {
        return new FileOutStream(out,closeable);
    }

    long read(Value.ByteList buff, long off, long count) throws ConcatRuntimeError;

    boolean write(Value.ByteList buff, long off, long count) throws ConcatRuntimeError;

    long size();

    boolean seek(long pos);

    boolean truncate();

    boolean seekEnd();

    long pos();

    boolean close();
}
