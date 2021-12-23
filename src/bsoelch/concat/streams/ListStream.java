package bsoelch.concat.streams;

import bsoelch.concat.Type;
import bsoelch.concat.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

//stream a List
public class ListStream implements ValueStream{
    public static ValueStream create(Type contentType, List<Value> elements){
        //addLater? special cases for char list and byte list
        return new ListStream(contentType,elements);
    }

    final List<Value> data;
    int index=0;
    final Type contentType;
    private ListStream(Type contentType,List<Value> data) {
        this.data = data;
        this.contentType=contentType;
    }

    @Override
    public Type contentType() {
        return contentType;
    }

    @Override
    public int state() {
        return index<data.size()?STATE_OK:STATE_EOF;
    }

    @Override
    public void resetState() {}

    @Override
    public Optional<Value> read() {
        if(index<data.size()){
            return Optional.of(data.get(index++));
        }else{
            return Optional.empty();
        }
    }

    @Override
    public List<Value> readMultiple(int count) {
        count=Math.min(count,data.size()-index);
        List<Value> res=new ArrayList<>(data.subList(index,count));
        index+=count;
        return res;
    }

    @Override
    public int readValues(Value[] buffer) {
        int count=Math.min(buffer.length, data.size()-index);
        data.subList(index,count).toArray(buffer);
        index+=count;
        return count;
    }

    @Override
    public Optional<Long> size() {
        return Optional.of((long)data.size());
    }

    @Override
    public long skip(int upTo) {
        upTo=Math.min(upTo,data.size()-index);
        index+=upTo;
        return upTo;
    }

    @Override
    public boolean seek(long pos) {
        if(pos<0||pos>data.size()){
            return false;
        }
        index=(int)pos;
        return true;
    }

    @Override
    public boolean seekEnd() {
        index=data.size();
        return true;
    }

    @Override
    public Optional<Long> pos() {
        return Optional.of((long)index);
    }

    @Override
    public boolean write(Value value){
        //TODO implement ListStream.write
        throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public boolean write(Value[] src, int count){
        //TODO implement ListStream.write
        throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public boolean write(ValueStream values){
        //TODO implement ListStream.write
        throw new UnsupportedOperationException("unimplemented");
    }

    @Override
    public boolean close() {
        return true;
    }
}
