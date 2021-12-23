package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.TypeError;
import bsoelch.concat.Value;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.util.*;

public class FileStream implements ByteStream {
    public static final int STATE_OK  =  0;
    public static final int STATE_EOF = -1;
    public static final int STATE_ERR =  1;//addLater? distinguish different exceptions

    final RandomAccessFile file;

    private int state=STATE_OK;

    public FileStream(String path,String options) throws ConcatRuntimeError {
        try {
            this.file = new RandomAccessFile(path,options);
        } catch (FileNotFoundException e) {
            throw new ConcatRuntimeError(e.getMessage());
        }
    }
    @Override
    public int state() {
        return state;
    }
    @Override
    public void resetState(){
        state=STATE_OK;
    }
    @Override
    public Optional<Value> read(){
        try {
            int r=file.read();//TODO caching
            if(r<0){
                state=STATE_EOF;
                return Optional.empty();
            }
            return Optional.of(Value.ofByte((byte)r));
        } catch (IOException e) {
            state=STATE_ERR;
            return Optional.empty();
        }
    }
    @Override
    public List<Value> readMultiple(int c){
        try {
            byte[] buf=new byte[c];
            int n=file.read(buf);
            if(n<0){
                state=STATE_EOF;
                return Collections.emptyList();
            }
            ArrayList<Value> res=new ArrayList<>(n);
            for(int i=0;i<n;i++){
                res.add(Value.ofByte(buf[i]));
            }
            return res;
        }catch (IOException e){
            state=STATE_ERR;
            return Collections.emptyList();
        }
    }
    @Override
    public int readBytes(byte[] buffer){
        try {
            int n=file.read(buffer);
            if(n<0){
                state=STATE_EOF;
            }
            return n;
        }catch (IOException e){
            state=STATE_ERR;
            return 0;
        }
    }
    @Override
    public int readValues(Value[] buffer) {
        byte[] buff2=new byte[buffer.length];
        int r=readBytes(buff2);
        for(int i=0;i<r;i++){
            buffer[i]=Value.ofByte(buff2[i]);
        }
        return r;
    }
    @Override
    public Optional<Long> size(){
        try {
            return Optional.of(file.length());
        } catch (IOException e) {
            state=STATE_ERR;
            return Optional.empty();
        }
    }
    @Override
    public long skip(int n) {
        try {
            return file.skipBytes(n);
        } catch (IOException e) {
            state=STATE_ERR;
            return 0;
        }
    }
    @Override
    public boolean seek(long pos) {
        try {
            file.seek(pos);
            return true;
        } catch (IOException e) {
            state=STATE_ERR;
            return false;
        }
    }

    @Override
    public boolean seekEnd() {
        try {
            file.seek(file.length());
            return true;
        } catch (IOException e) {
            state=STATE_ERR;
            return false;
        }
    }

    @Override
    public Optional<Long> pos() {
        try {
            return Optional.of(file.getFilePointer());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean write(byte value) {
        try {
            file.write(value);
            return true;
        } catch (IOException e) {
            state=STATE_ERR;
            return false;
        }
    }
    @Override
    public boolean write(byte[] value, int n){
        try {
            file.write(value, 0, n);
            return true;
        } catch (IOException e) {
            state=STATE_ERR;
            return false;
        }
    }
    @Override
    public boolean write(Value value) throws TypeError {
        return write(value.asByte());
    }
    @Override
    public boolean write(Value[] source,int c) throws TypeError {
        byte[] bytes = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            bytes[i] = source[i].asByte();
        }
        return write(bytes, bytes.length);
    }
    @Override
    public boolean write(ValueStream source) throws TypeError {
        if(source instanceof ByteStream) {
            byte[] buffer = new byte[8192];
            int r;
            while ((r = ((ByteStream)source).readBytes(buffer)) > 0) {
                write(buffer, r);
            }
        }else{
            Value[] buffer = new Value[8192];
            int r;
            while ((r = source.readValues(buffer)) > 0) {
                if(!write(buffer,r)){
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean close(){
        try {
            file.close();
            return true;
        } catch (IOException e) {
            state=STATE_ERR;
            return false;
        }
    }
}
