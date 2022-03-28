import streams.*;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class io{
    public static Class<?> nativeImpl_FILE=FileStream.class;

    public static FileStream nativeImpl_stdIn  = new SysInStream();
    public static FileStream nativeImpl_stdOut = new SysOutStream(false);
    public static FileStream nativeImpl_stdErr = new SysOutStream(true);

    public static Optional<FileStream> nativeImpl_open(byte[] path, byte[] flags){
        try {
            return Optional.of(new RandomAccessFileStream(
                    new String(path,StandardCharsets.UTF_8),
                    new String(flags,StandardCharsets.UTF_8)));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }
    public static boolean nativeImpl_close(FileStream file){
        return file.close();
    }
    public static long nativeImpl_read(FileStream file,byte[] buff,long off,long count){
        return file.read(buff, off, count);
    }
    public static long nativeImpl_read(FileStream file,Object[] buff,long off,long count){
        byte[] bytes = (byte[])buff[0];
        int bytes_off  = (int)buff[1];
        int bytes_len  = (int)buff[2];
        if(bytes.length<bytes_off+off+count){
            count=Math.min(count,bytes.length-bytes_off);
            int newOff = bytes.length - (int) (bytes_off + off + count);
            System.arraycopy(bytes,bytes_off,bytes, newOff,(int)off);
            bytes_off=newOff;
        }
        long r=file.read(bytes, bytes_off+off, count);
        long nRead=Math.max(r,0);
        if(nRead<count){
            int oldOff=(int)buff[1];
            if(oldOff!=bytes_off&&bytes_len>oldOff+(int)(off+nRead)){
                System.arraycopy(bytes,oldOff+(int)(off+nRead),bytes,bytes_off+(int)(off+nRead),
                        (bytes_len-oldOff+(int)(off+nRead)));
            }
        }
        buff[1]=bytes_off;
        buff[2]=(int)(bytes_len+nRead);
        return r;
    }
    public static boolean nativeImpl_write(FileStream file,byte[] buff,long off,long count){
        if(off+count>buff.length){
            throw new IndexOutOfBoundsException("Index out ouf Bounds: "+(off+count)+" length:"+buff.length);
        }
        return file.write(buff, off, count);
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