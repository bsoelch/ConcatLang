package bsoelch.concat.streams;

import bsoelch.concat.Type;

public interface ByteStream extends ValueStream {
    int readBytes(byte[] buffer);

    boolean write(byte value);

    boolean write(byte[] value, int n);

    @Override
    default Type contentType() {
        return Type.BYTE;
    }
}
