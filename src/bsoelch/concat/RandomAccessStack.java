package bsoelch.concat;

import java.util.Arrays;
import java.util.List;

/**Stack that supports random access of elements*/
public class RandomAccessStack<T> {
    private Object[] data;
    private int size;

    public RandomAccessStack(int initCap){
        data=new Object[Math.max(initCap,10)];
    }


    private void grow(int newCap) {
        Object[] newData=new Object[newCap];
        System.arraycopy(data,0,newData,0,data.length);
        data=newData;
    }
    public void push(T val){
        if(size>=data.length){
            grow(2*data.length);
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
    //random access methods
    /**gets the element at position index (counted from the top of the stack, starting with 1)*/
    public T get(int index){
        if(index<1||index>size){
            throw new IndexOutOfBoundsException("index out of bounds:"+index+" size:"+size);
        }
        //noinspection unchecked
        return (T)data[size-index];
    }
    /**sets the element at position index (counted from the top of the stack, starting with 1) to the newValue*/
    public void set(int index,T newValue){
        if(index<1||index>size){
            throw new IndexOutOfBoundsException("index out of bounds:"+index+" size:"+size);
        }
        data[size-index]=newValue;
    }
    /**insert element below position at (counted from top of the stack, starting with 1)*/
    public void insert(int at,T value){
        if(at<0||at>size){
            throw new IndexOutOfBoundsException("index out of bounds:"+at+" size:"+size);
        }
        if(size>=data.length){
            grow(2*data.length);
        }
        System.arraycopy(data,size-at,data,size-at+1,at);
        data[size-at]=value;
    }
    /**insert elements below position at (counted from top of the stack, starting with 1)*/
    public void insertAll(int at, List<T> elements) {
        if(at<0||at>size){
            throw new IndexOutOfBoundsException("index out of bounds:"+at+" size:"+size);
        }
        Object[] asArray= elements.toArray();
        if(size+asArray.length>data.length){
            grow(2*data.length);
        }
        System.arraycopy(data,size-at,data,size-at+asArray.length,at);
        System.arraycopy(asArray,0,data,size-at,asArray.length);
    }
    /** get the elements in this stack between bottom and top as list
     * !!! bottom has to be greater or equal to top (the elements of stack slices are accessed in reversed order) !!!
     * @param bottom starting index (counted from top of the stack, starting with 1)
     * @param top    end index      (counted from top of the stack, starting with 1) */
    public List<T> subList(int bottom, int top) {
        if(top<0||bottom>size||top>bottom){
            throw new IndexOutOfBoundsException("invalid stack-slice: "+bottom+":"+top+" length:"+size);
        }
        //noinspection unchecked
        return (List<T>) Arrays.asList(data).subList(size-bottom,size-top);
    }
    /** get the elements in this stack between bottom and top as list
     * !!! bottom has to be greater or equal to top (the elements of stack slices are accessed in reversed order) !!!
     * @param bottom starting index (counted from top of the stack, starting with 1)
     * @param top    end index      (counted from top of the stack, starting with 1) */
    public void setSlice(int bottom, int top, List<T> elements) {
        if(top<0||bottom>size||top>bottom){
            throw new IndexOutOfBoundsException("invalid stack-slice: "+bottom+":"+top+" length:"+size);
        }
        Object[] asArray= elements.toArray();
        if(data.length<size+asArray.length-(bottom-top)){
            int newCap=data.length*2;
            while(newCap<size+asArray.length-(bottom-top)){
                newCap*=2;
            }
            grow(newCap);
        }
        System.arraycopy(data,size-top,data,size-bottom+asArray.length,top);
        System.arraycopy(asArray,0,data,size-bottom,asArray.length);
        size+=asArray.length-(bottom-top);
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(data,size));
    }


    public static class StackUnderflow extends Exception {
        StackUnderflow(){
            super("stack underflow");
        }
    }
}
