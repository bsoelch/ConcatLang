package streams;

import java.io.*;


public class RandomAccessFileStream implements FileStream {

    final RandomAccessFile file;

    public RandomAccessFileStream(String path, String options) throws FileNotFoundException {
        this.file = new RandomAccessFile(path,options);
    }

    @Override
    public long read(byte[] buff, long off, long count){
        try {
            count=file.read(buff,(int)off,(int)count);
            return count;
        } catch (IOException e) {
            return -2;
        }
    }
    @Override
    public boolean write(byte[] buff, long off, long count){
        try {
            file.write(buff,(int)off,(int)count);
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
