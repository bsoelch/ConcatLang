package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.Value;

import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("ClassCanBeRecord")
public class FileInStream implements FileStream{
    final InputStream stream;
    final boolean closeable;

    public FileInStream(InputStream stream, boolean closeable){
        this.stream=stream;
        this.closeable=closeable;
    }

    @Override
    public long read(Value.ByteList buff, long off, long count) throws ConcatRuntimeError {
        buff.ensureCap(count);
        byte[] tmp=new byte[(int)count];
        try {
            count=stream.read(tmp);
            if (count >= 0) {
                buff.setSlice(off,Math.min(count,buff.length()),tmp);
            }
            return count;
        } catch (IOException e) {
            return -2;
        }
    }
    @Override
    public boolean write(Value.ByteList buff, long off, long count) throws ConcatRuntimeError {
        return false;
    }

    @Override
    public long size(){
        try {
            return stream.available();
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
        if(closeable){
            try {
                stream.close();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }
}
