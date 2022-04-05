package bsoelch.concat;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**Stack that supports random access of elements*/
public class RandomAccessStack<T> implements Cloneable,Iterable<T>{
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
    public Iterator<T> iterator() {
        //noinspection unchecked
        return (Iterator<T>) Arrays.asList(data).subList(0,size).iterator();
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(data,size));
    }

    public List<T> drop(int count) throws StackUnderflow {
        if(count>size){
            throw new StackUnderflow();
        }
        size-=count;
        //noinspection unchecked
        return (List<T>) Arrays.asList(Arrays.copyOfRange(data,size,size+count));
    }
    public void dup(int src) throws StackUnderflow {
        if(src>size){
            throw new StackUnderflow();
        }
        ensureCap(size+1);
        data[size]=data[size-src];
        size++;
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
    public void rotate(int count,int steps){
        if(count<1||count>size){
            throw new IndexOutOfBoundsException("count has to be between 1 and "+size+" got:"+count);
        }
        steps=(steps%count+count)%count;
        Object[] buff=new Object[steps];
        System.arraycopy(data,size-count,buff,0,steps);
        System.arraycopy(data,size-(count-steps),data,size-count,count-steps);
        System.arraycopy(buff,0,data,size-steps,steps);
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
