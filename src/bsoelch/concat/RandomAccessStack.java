package bsoelch.concat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**Stack that supports random access of elements*/
public class RandomAccessStack<T> implements Cloneable{
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

    public int size() {
        return size;
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

    public List<T> asList() {
        //noinspection unchecked
        return (List<T>) Arrays.asList(Arrays.copyOf(data,size));
    }

    public T get(int i){
        if(i<1||i>size){
            throw new IndexOutOfBoundsException("stack-index has to be between 1 and "+size+" got:"+i);
        }
        //noinspection unchecked
        return (T) data[size-i];
    }
    public void set(int i,T val){
        if(i<1||i>size){
            throw new IndexOutOfBoundsException("stack-index has to be between 1 and "+size+" got:"+i);
        }
        data[size-i]=val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RandomAccessStack<?> that = (RandomAccessStack<?>) o;
        return size == that.size && Arrays.equals(Arrays.copyOf(data,size), Arrays.copyOf(that.data,that.size));
    }

    @Override
    public int hashCode() {
        int result = size;
        for(int i=0;i<size;i++){
            result = 31 * result + Objects.hashCode(data[i]);
        }
        return result;
    }

    @Override
    public RandomAccessStack<T> clone(){
        try {
            @SuppressWarnings("unchecked")
            RandomAccessStack<T> stack = (RandomAccessStack<T>) super.clone();
            stack.data=data.clone();
            return stack;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class StackUnderflow extends Exception {
        StackUnderflow(){
            super("stack underflow");
        }
    }
}
