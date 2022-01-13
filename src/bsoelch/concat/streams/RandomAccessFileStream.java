package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.Value;

import java.io.*;


public class RandomAccessFileStream implements FileStream {

    final RandomAccessFile file;

    public RandomAccessFileStream(String path, String options) throws ConcatRuntimeError {
        try {
            this.file = new RandomAccessFile(path,options);
        } catch (FileNotFoundException e) {
            throw new ConcatRuntimeError(e.getMessage());
        }
    }

    @Override
    public long read(Value.ByteList buff, long off, long count) throws ConcatRuntimeError {
        buff.ensureCap(count);
        byte[] tmp=new byte[(int)count];
        try {
            count=file.read(tmp);
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
        try {
            file.write(buff.getSlice(off,count).toByteArray());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public long size(){
        try {
            return file.length();
        } catch (IOException e) {
            return -1;
        }
    }
    @Override
    public boolean seek(long pos) {
        try {
            file.seek(pos);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    /**truncate file at current pos*/
    @Override
    public boolean truncate() {
        try {
            file.getChannel().truncate(file.getFilePointer());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    @Override
    public boolean seekEnd() {
        try {
            file.seek(file.length());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    @Override
    public long pos() {
        try {
            return file.getFilePointer();
        } catch (IOException e) {
            return -1;
        }
    }
    @Override
    public boolean close(){
        try {
            file.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
