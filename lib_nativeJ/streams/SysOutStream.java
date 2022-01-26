package streams;

@SuppressWarnings("ClassCanBeRecord")
public class SysOutStream implements FileStream {
    final boolean err;

    public SysOutStream(boolean err){
        this.err=err;
    }
    @Override
    public long read(byte[] buff, long off, long count){
        return -2;
    }
    @Override
    public boolean write(byte[] buff, long off, long count) {
        (err?System.err:System.out).write(buff,(int)off,(int)count);
        return true;
    }

    @Override
    public long size(){
        return -1;
    }
    @Override
    public boolean seek(long pos) {
        return false;
    }
    /**truncate file at current pos*/
    @Override
    public boolean truncate() {
        return false;
    }
    @Override
    public boolean seekEnd() {
        return false;
    }
    @Override
    public long pos() {
        return -1;
    }
    @Override
    public boolean close(){
        return false;
    }
}
