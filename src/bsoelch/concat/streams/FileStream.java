package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.Value;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;


public class FileStream {
    final RandomAccessFile file;

    public FileStream(String path,String options) throws ConcatRuntimeError {
        try {
            this.file = new RandomAccessFile(path,options);
        } catch (FileNotFoundException e) {
            throw new ConcatRuntimeError(e.getMessage());
        }
    }

    public long read(Value.ByteList buff, long off, long count) throws ConcatRuntimeError {
        buff.ensureCap(count);
        byte[] tmp=new byte[(int)count];
        try {
            count=file.read(tmp);
            buff.setSlice(off,Math.min(count,buff.length()),tmp);
            return count;
        } catch (IOException e) {
            //TODO better handling of return codes
            // - ensure that IOException and EOF can be distinguished
            // - the return value should tell the actual number of bytes read
            return -2;
        }
    }
    public boolean write(Value.ByteList buff,long off,long count) throws ConcatRuntimeError {
        try {
            file.write(buff.getSlice(off,count).toByteArray());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public long size(){
        try {
            return file.length();
        } catch (IOException e) {
            return -1;
        }
    }
    public boolean seek(long pos) {
        try {
            file.seek(pos);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    /**truncate file at current pos*/
    public boolean truncate() {
        try {
            file.getChannel().truncate(file.getFilePointer());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public boolean seekEnd() {
        try {
            file.seek(file.length());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public long pos() {
        try {
            return file.getFilePointer();
        } catch (IOException e) {
            return -1;
        }
    }
    public boolean close(){
        try {
            file.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
