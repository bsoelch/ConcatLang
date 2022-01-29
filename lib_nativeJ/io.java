import streams.*;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class io{
    public static Class<?> nativeImpl_FILE=FileStream.class;

    public static FileStream nativeImpl_stdIn  = new SysInStream();
    public static FileStream nativeImpl_stdOut = new SysOutStream(false);
    public static FileStream nativeImpl_stdErr = new SysOutStream(true);

    public static Optional<FileStream> nativeImpl_open(Object[] path, Object[] flags){
        try {
            return Optional.of(new RandomAccessFileStream(
                    new String((byte[])path[0],(int)path[1],(int)path[2],StandardCharsets.UTF_8),
                    new String((byte[])flags[0],(int)flags[1],(int)flags[2], StandardCharsets.UTF_8)));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }
    public static boolean nativeImpl_close(FileStream file){
        return file.close();
    }
    public static long nativeImpl_read(FileStream file,Object[] buff,long off,long count){
        byte[] bytes = (byte[])buff[0];
        int bytes_off  = (int)buff[1];
        int bytes_len  = (int)buff[2];
        int bytes_init = (int)buff[3];
        if(bytes_len<off+count){
            if(bytes.length<off+count+bytes_init-bytes_len){
                byte[] tmp=new byte[(int)(off+count+bytes_init-bytes_len)];
                System.arraycopy(bytes,0,tmp,0,bytes_off);
                bytes=tmp;
            }
            System.arraycopy(bytes,bytes_off+bytes_len,bytes,(int)(bytes_off+off+count),
                    bytes_init-(bytes_off+bytes_len));
            buff[0]=bytes;
            bytes_init=(int)(off+count+bytes_init-bytes_len);
            bytes_len=(int)(off+count);
            buff[2]=bytes_len;
            buff[3]=bytes_init;
        }
        long r=file.read(bytes, bytes_off+off, count);
        if(r<count){
            long del=count-Math.max(r,0);
            if(bytes_off+bytes_len<bytes_init){
                System.arraycopy(bytes,bytes_off+(int)count,bytes,bytes_off+(int)Math.max(r,0),
                        bytes_init-(int)(bytes_off+count));
            }
            bytes_len-=del;
            buff[2]=bytes_len;
            bytes_init-=del;
            buff[3]=bytes_init;
        }
        return r;
    }
    public static boolean nativeImpl_write(FileStream file,Object[] buff,long off,long count){
        byte[] bytes = (byte[])buff[0];
        int bytes_off  = (int)buff[1];
        int bytes_len  = (int)buff[2];
        if(off+count>bytes_len){
            throw new IndexOutOfBoundsException("Index out ouf Bounds: "+(off+count)+" length:"+bytes_len);
        }
        return file.write(bytes, bytes_off+off, count);
    }
    public static long nativeImpl_size(FileStream file){
        return file.size();
    }
    public static long nativeImpl_pos(FileStream file){
        return file.pos();
    }
    public static boolean nativeImpl_truncate(FileStream file){
        return file.truncate();
    }
    public static boolean nativeImpl_seek(FileStream file,long to){
        return file.seek(to);
    }
    public static boolean nativeImpl_seekEnd(FileStream file){
        return file.seekEnd();
    }
}