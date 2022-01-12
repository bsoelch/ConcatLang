package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.Value;

import java.io.*;


@SuppressWarnings("ClassCanBeRecord")
public class FileOutStream implements FileStream {
    final OutputStream stream;
    final boolean closeable;

    public FileOutStream(OutputStream stream, boolean closeable){
        this.stream=stream;
        this.closeable=closeable;
    }

    @Override
    public long read(Value.ByteList buff, long off, long count){
        return -2;
    }
    @Override
    public boolean write(Value.ByteList buff, long off, long count) throws ConcatRuntimeError {
        try {
            stream.write(buff.getSlice(off,count).toByteArray());
            return true;
        } catch (IOException e) {
            return false;
        }
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
