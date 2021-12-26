package bsoelch.concat;

import java.util.*;

/**immutable reversed View of a List*/
public class ReversedList<T> implements List<T> {
    final List<T> reversed;
    public static <T> List<T> reverse(List<T> list){
        if(list instanceof ReversedList<T>){
            return ((ReversedList<T>) list).reversed;
        }else if(list instanceof RandomAccess){
            return new RAReverseList<>(list);
        }else{
            return new ReversedList<>(list);
        }
    }
    private static class RAReverseList<T> extends ReversedList<T> implements RandomAccess{
        private RAReverseList(List<T> reversed) {
            super(reversed);
        }
    }
    private ReversedList(List<T> reversed) {
        this.reversed = reversed;
    }

    @Override
    public int size() {
        return reversed.size();
    }
    @Override
    public boolean isEmpty() {
        return reversed.isEmpty();
    }
    @Override
    public boolean contains(Object o) {
        return reversed.contains(o);
    }
    @Override
    public Iterator<T> iterator() {
        return new ReversedIterator(reversed.listIterator(reversed.size()));
    }
    @Override
    public ListIterator<T> listIterator() {
        return new ReversedIterator(reversed.listIterator(reversed.size()));
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return new ReversedIterator(reversed.listIterator(reversed.size()-index-1));
    }


    private static <T> void reverse(T[] array,int size){
        for(int i=0;i<size/2;i++){
            T tmp=array[i];
            array[i]=array[size-i-1];
            array[size-i-1]=tmp;
        }
    }
    @Override
    public Object[] toArray() {
        Object[] array=reversed.toArray();
        reverse(array,size());
        return array;
    }
    @Override
    public <T1> T1[] toArray(T1[] a) {
        @SuppressWarnings("SuspiciousToArrayCall")
        T1[] array=reversed.toArray(a);
        reverse(array,size());
        return array;
    }


    @Override
    public boolean containsAll(Collection<?> c) {
        return reversed.containsAll(c);
    }


    @Override
    public T get(int index) {
        return reversed.get(reversed.size()-index-1);
    }
    @Override
    public T set(int index, T element) {
        return reversed.set(reversed.size()-index-1,element);
    }

    @Override
    public int indexOf(Object o) {
        //noinspection SuspiciousMethodCalls
        return reversed.size()-1-reversed.lastIndexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        //noinspection SuspiciousMethodCalls
        return reversed.size()-1-reversed.indexOf(o);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return reverse(reversed.subList(toIndex-1,fromIndex+1));
    }

    @Override
    public boolean add(T t) {
        reversed.add(0,t);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if(reversed instanceof RandomAccess){
            //noinspection SuspiciousMethodCalls
            int index = reversed.lastIndexOf(o);
            if(index>=0) {
                reversed.remove(index);
                return true;
            }else{
                return false;
            }
        }else{
            ListIterator<T> itr=reversed.listIterator(reversed.size());
            while(itr.hasPrevious()){
                if(Objects.equals(itr.previous(),equals(o))){
                    itr.remove();
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void add(int index, T element) {
        reversed.add(reversed.size()-index-1,element);
    }

    @Override
    public T remove(int index) {
        return reversed.remove(reversed.size()-index-1);
    }
    @Override
    public boolean addAll(Collection<? extends T> c) {
        //noinspection unchecked
        return reversed.addAll(0,reverse(c instanceof List<?>?(List<T>)c:new ArrayList<>(c)));
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        //noinspection unchecked
        return reversed.addAll(reversed.size()-index-1,reverse(c instanceof List<?>?(List<T>)c:new ArrayList<>(c)));
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return reversed.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return reversed.retainAll(c);
    }

    @Override
    public void clear() {
        reversed.clear();
    }
    private class ReversedIterator implements ListIterator<T>{
        final ListIterator<T> reversedIterator;

        public ReversedIterator(ListIterator<T> reversedIterator) {
            this.reversedIterator = reversedIterator;
        }
        @Override
        public boolean hasNext() {
            return reversedIterator.hasPrevious();
        }
        @Override
        public T next() {
            return reversedIterator.previous();
        }
        @Override
        public boolean hasPrevious() {
            return reversedIterator.hasNext();
        }
        @Override
        public T previous() {
            return reversedIterator.previous();
        }

        @Override
        public int nextIndex() {
            return reversed.size()- reversedIterator.previousIndex()-1;
        }

        @Override
        public int previousIndex() {
            return reversed.size()- reversedIterator.nextIndex()-1;
        }

        @Override
        public void set(T t) {
            reversedIterator.set(t);
        }
        @Override
        public void remove() {
            reversedIterator.remove();
        }
        @Override
        public void add(T t) {
            reversedIterator.add(t);
        }
    }
}
