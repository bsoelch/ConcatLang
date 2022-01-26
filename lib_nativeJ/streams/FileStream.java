package streams;

public interface FileStream {

    //TODO better handling of return codes
    // - ensure that IOException and EOF can be distinguished
    // - the return value should tell the actual number of bytes read
    long read(byte[] buff, long off, long count);

    boolean write(byte[] buff, long off, long count);

    long size();

    boolean seek(long pos);

    boolean truncate();

    boolean seekEnd();

    long pos();

    boolean close();
}
