package bsoelch.concat.streams;

import bsoelch.concat.ConcatRuntimeError;
import bsoelch.concat.Type;
import bsoelch.concat.Value;

import java.util.List;
import java.util.Optional;

public interface ValueStream {
    int STATE_OK  =  0;
    int STATE_EOF = -1;
    int STATE_ERR =  1;
    /* merge stream and iterator

     <path> <flags> open => <byte stream>

     <byte stream>          chars  => <char stream>
     <byte stream> <string> chars  => <char stream>
     <char stream>          bytes  => <byte stream>
     <char stream> <string> cBytes => <byte stream>

     <stream> close => (<list>) <bool>

     <stream>           state => <int>

     <stream>            read => <stream> <val> <bool>
     <stream> <int>     read+ => <stream> <val list>
     <stream>            size => <stream> <int> <bool>
     <stream> <int>      skip => <stream> <int>
     <stream> <int>      seek => <stream> <bool>
     <stream> <val>     write => <stream> <bool>
     <stream> <stream> write+ => <stream> <bool>
     */
    int state();
    void resetState();
    Optional<Value> read();
    List<Value> readMultiple(int count);
    int readValues(Value[] buffer);
    Optional<Long> size();
    long skip(int upTo);
    boolean seek(long pos);
    boolean seekEnd();
    Optional<Long> pos();
    boolean write(Value value)  throws ConcatRuntimeError;

    boolean write(Value[] src, int count) throws ConcatRuntimeError;

    boolean write(ValueStream values)  throws ConcatRuntimeError;
    boolean close();

    Type contentType();
}
