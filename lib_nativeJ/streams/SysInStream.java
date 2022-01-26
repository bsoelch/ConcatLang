package streams;

import java.io.IOException;
public class SysInStream implements FileStream{
    @Override
    public long read(byte[] buff, long off, long count){
        try {
            count=System.in.read(buff,(int)off,(int)count);
            return count;
        } catch (IOException e) {
            return -2;
        }
    }
    @Override
    public boolean write(byte[] buff, long off, long count){
        return false;
    }

    @Override
    public long size(){
        try {
            return System.in.available();
        } catch (IOException e) {
            return -1;
        }
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
