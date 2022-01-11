package bsoelch.concat;

import java.util.Arrays;

/**Stack that supports random access of elements*/
public class RandomAccessStack<T> {
    private Object[] data;
    private int size;

    public RandomAccessStack(int initCap){
        data=new Object[Math.max(initCap,10)];
    }


    private void ensureCap(int newCap) {
        if(newCap>data.length){
            newCap=Math.min(2* data.length,newCap);
            Object[] newData=new Object[newCap];
            System.arraycopy(data,0,newData,0,data.length);
            data=newData;
        }
    }
    public void push(T val){
        if(size>=data.length){
            ensureCap(data.length+1);
        }
        data[size++]=val;
    }

    public T peek() throws StackUnderflow {
        if(size<=0){
            throw new StackUnderflow();
        }else{
            //noinspection unchecked
            return (T)data[size-1];
        }
    }
    public T pop() throws StackUnderflow {
        if(size<=0){
            throw new StackUnderflow();
        }else{
            //noinspection unchecked
            return (T)data[--size];
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(data,size));
    }

    public void dropAll(long off, long count) throws StackUnderflow {
        if(off+count>size){
            throw new StackUnderflow();
        }
        if(off>0){
            System.arraycopy(data,size-(int)off,data,size-(int)(off+count),(int)off);
        }
        size-=count;
    }

    public void dupAll(long off, long count) throws StackUnderflow {
        if(off+count>size){
            throw new StackUnderflow();
        }
        ensureCap((int)(size+count));
        System.arraycopy(data,size-(int)(off+count),data,size,(int)count);
        size+=count;
    }

    public static class StackUnderflow extends Exception {
        StackUnderflow(){
            super("stack underflow");
        }
    }
}
