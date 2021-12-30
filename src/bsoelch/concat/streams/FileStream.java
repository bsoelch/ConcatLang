package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.TypeError;
import bsoelch.concat.Value;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.util.*;

public class FileStream {
    final RandomAccessFile file;

    public FileStream(String path,String options) throws ConcatRuntimeError {
        try {
            this.file = new RandomAccessFile(path,options);
        } catch (FileNotFoundException e) {
            throw new ConcatRuntimeError(e.getMessage());
        }
    }

    public long read(ArrayList<Value> buff,long off,long count){
        buff.ensureCapacity((int)count);
        byte[] tmp=new byte[(int)count];
        try {
            count=file.read(tmp);

            for(int i=0;i<count;i++){//addLater? more effective method
                int j = (int) off + i;
                if(j<buff.size()){
                    buff.set(j,Value.ofByte(tmp[i]));
                }else{
                    buff.add(Value.ofByte(tmp[i]));
                }
            }

            return count;
        } catch (IOException e) {
            //TODO better handling of return codes
            // - ensure that IOException and EOF can be distinguished
            // - the return value should tell the actual number of bytes read
            return -2;
        }
    }
    public boolean write(ArrayList<Value> buff,long off,long count) throws TypeError {
        byte[] tmp=new byte[(int)count];
        for(int i=0;i<count;i++){
            tmp[i]=buff.get(((int)off)+i).asByte();
        }
        try {
            file.write(tmp);
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
