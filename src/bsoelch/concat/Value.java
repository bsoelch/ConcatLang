package bsoelch.concat;

import bsoelch.concat.streams.FileStream;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public abstract class Value {
    public static final Value FALSE = new Value(Type.BOOL) {
        @Override
        public boolean asBool() {
            return false;
        }
        @Override
        public String stringValue() {
            return "false";
        }
    };
    public static final Value TRUE = new Value(Type.BOOL) {
        @Override
        public boolean asBool() {
            return true;
        }
        @Override
        public String stringValue() {
            return "true";
        }
    };

    final Type type;
    protected Value(Type type) {
        this.type = type;
    }

    public long id() {
        return System.identityHashCode(this);
    }
    public boolean asBool() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to bool");
    }
    public byte asByte() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to byte");
    }
    public long asLong() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to int");
    }
    public double asDouble() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to float");
    }
    public Type asType() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to type");
    }
    public FileStream asStream() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to stream");
    }
    public ByteList toByteList() throws ConcatRuntimeError {
        throw new TypeError("Converting "+type+" to raw-bytes is not supported");
    }
    public ByteList asByteList() throws ConcatRuntimeError {
        throw new TypeError(type+" cannot be assigned to byte list");
    }
    /**checks if this and v are the same object (reference)
     * unlike equals this method distinguishes mutable objects with different ids but the same elements*/
    boolean isEqualTo(Value v){
        return equals(v);
    }

    public Value negate() throws TypeError {
        throw new TypeError("Cannot negate values of type "+type);
    }
    public Value invert() throws TypeError {
        throw new TypeError("Cannot invert values of type "+type);
    }
    public Value flip() throws TypeError {
        throw new TypeError("Cannot invert values of type "+type);
    }
    public int length() throws TypeError {
        throw new TypeError(type+" does not have a length");
    }
    /**returns true if this Value is NOT a list (elements() throws a Type error)*/
    boolean notList(){
        return true;
    }
    public List<Value> getElements() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to list");
    }
    public Value get(long index) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void set(long index,Value value) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public Value getSlice(long off, long to) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void setSlice(long off, long to,Value value) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void ensureCap(long newCap) throws ConcatRuntimeError {
        throw new TypeError("changing capacity is not supported for type "+type);
    }
    public void fill(Value val, long off, long count) throws ConcatRuntimeError {
        throw new TypeError("fill is not supported for type "+type);
    }
    public void push(Value value,boolean start) throws ConcatRuntimeError {
        throw new TypeError("adding elements is not supported for type "+type);
    }
    public void pushAll(Value value,boolean start) throws ConcatRuntimeError {
        throw new TypeError("adding elements is not supported for type "+type);
    }
    //addLater add instructions to insert/remove arbitrary elements of lists
    public Value clone(boolean deep) {
        return this;
    }

    public boolean isString(){
        return Type.STRING().equals(type);
    }
    public abstract String stringValue();

    @Override
    public String toString() {
        return type+":"+stringValue();
    }

    public Value castTo(Type type) throws ConcatRuntimeError {
        if(type==Type.ANY||this.type.equals(type)){
            return this;
        }else if(type.equals(Type.STRING())){
            return ofString(stringValue());
        }else{
            throw new TypeError("cannot cast from "+this.type+" to "+type);
        }
    }


    /**A wrapper for TypeError that can be thrown inside functional interfaces*/
    private static class WrappedConcatError extends RuntimeException {
        final ConcatRuntimeError wrapped;
        public WrappedConcatError(ConcatRuntimeError wrapped) {
            this.wrapped = wrapped;
        }
    }
    private Value unsafeCastTo(Type type) throws WrappedConcatError {
        try {
            return castTo(type);
        } catch (ConcatRuntimeError e) {
            throw new WrappedConcatError(e);
        }
    }

    private interface NumberValue{}
    private static int valueOf(char digit,int base) throws ConcatRuntimeError {
        int val;
        if(digit>='0'&&digit<='9'){
            val=digit-'0';
        }else if(digit>='A'&&digit<='Z'){
            val=digit-'A'+10;
        }else if(digit>='a'&&digit<='z'){
            val=digit-'a'+base<37?10:36;
        }else{
            throw new ConcatRuntimeError("invalid digit for base "+base+" number '"+digit+"'");
        }
        if(val>=base){
            throw new ConcatRuntimeError("invalid digit for base "+base+" number '"+digit+"'");
        }
        return val;
    }

    public static Value ofInt(long intValue) {
        return new IntValue(intValue);
    }

    public static long parseInt(String source,int base) throws ConcatRuntimeError {
        if(base<2||base>62){
            throw new ConcatRuntimeError("base out of range:"+base+" base has to be between 2 and 62");
        }
        int i=0;
        boolean sgn=source.startsWith("-");
        if(sgn){
            i++;
        }
        long res=0;
        for(;i<source.length();i++){
            if(res>Long.MAX_VALUE/base){
                throw new ConcatRuntimeError("invalid string-format for int \""+source+"\" (overflow)");
            }
            res*=base;
            res+=valueOf(source.charAt(i),base);
        }
        return sgn?-res:res;
    }
    private static class IntValue extends Value implements NumberValue{
        final long intValue;
        private IntValue(long intValue) {
            super(Type.INT);
            this.intValue = intValue;
        }

        @Override
        public long id() {
            return intValue;
        }

        @Override
        public double asDouble() {
            return intValue;
        }
        @Override
        public long asLong() {
            return intValue;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.BYTE){
                if(intValue<0||intValue>0xff){
                    throw new ConcatRuntimeError("cannot cast 0x"+Long.toHexString(intValue)+" to byte");
                }
                return Value.ofByte((byte)intValue);

            }else if(type==Type.CHAR){
                if(intValue<0||intValue>Character.MAX_CODE_POINT){
                    throw new ConcatRuntimeError("cannot cast 0x"+Long.toHexString(intValue)+" to char");
                }
                return Value.ofChar((int)intValue);
            }else if(type==Type.FLOAT){
                return ofFloat(intValue);
            }else{
                return super.castTo(type);
            }
        }

        public Value negate(){
            return ofInt(-intValue);
        }
        public Value flip(){
            return ofInt(~intValue);
        }
        public Value invert(){
            return ofFloat(1.0/intValue);
        }

        @Override
        public String stringValue() {
            return Long.toString(intValue);
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntValue intValue1 = (IntValue) o;
            return intValue == intValue1.intValue;
        }
        @Override
        public int hashCode() {
            return Objects.hash(intValue);
        }
    }

    private static boolean isExpChar(char c,int base){
        switch (c){
            case 'e','E'-> {return base<('E'-'A'+10+1);}
            case 'p','P'-> {return base<('P'-'A'+10+1);}
            case 'x','X'-> {return base<('X'-'A'+10+1);}
            case '#'-> {return true;}
            default -> {return false;}
        }
    }

    public static Value ofFloat(double d) {
        return new FloatValue(d);
    }
    public static double parseFloat(String str, int base) throws ConcatRuntimeError {
        if(base<2||base>62){
            throw new ConcatRuntimeError("base out of range:"+base+" base has to be between 2 and 62");
        }
        int i=0;
        boolean sgn=str.startsWith("-");
        if(sgn){
            i++;
        }
        long val=0;
        int c=0;
        int d=0,e=-1;
        for(;i<str.length();i++){
            if(str.charAt(i)=='.'){
                if(d!=0){
                    throw new ConcatRuntimeError("invalid string-format for float \""+str+"\"");
                }
                d=1;
            }else if(isExpChar(str.charAt(i),base)) {
                e = i + 1;
                break;
            }else{
                if(val<Long.MAX_VALUE/base){
                    val*=base;
                    val+=valueOf(str.charAt(i),base);
                    c+=d;
                }else{
                    valueOf(str.charAt(i),base);//check digit without storing value
                    c += 1 - d;//decrease c if on the left of comma
                }
            }
        }
        if (e > 0) {
            c-=(int)parseInt(str.substring(e),base);
        }
        return (sgn?-val:val)*Math.pow(base,-c);
    }
    private static class FloatValue extends Value implements NumberValue{
        final double floatValue;
        private FloatValue(double floatValue) {
            super(Type.FLOAT);
            this.floatValue = floatValue;
        }

        @Override
        public long id() {
            return Double.doubleToRawLongBits(floatValue);
        }

        @Override
        public double asDouble() {
            return floatValue;
        }

        public Value negate(){
            return ofFloat(-floatValue);
        }
        public Value invert(){
            return ofFloat(1/floatValue);
        }

        @Override
        public String stringValue() {
            return Double.toString(floatValue);
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                return ofInt((long)floatValue);
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FloatValue that = (FloatValue) o;
            return Double.compare(that.floatValue, floatValue) == 0;
        }
        @Override
        boolean isEqualTo(Value v) {
            return v instanceof FloatValue&&
                   floatValue==((FloatValue) v).floatValue;
        }

        @Override
        public int hashCode() {
            return Objects.hash(floatValue);
        }
    }

    public static Value ofType(Type typeValue) {
        return new TypeValue(typeValue);
    }
    private static class TypeValue extends Value{
        final Type typeValue;
        private TypeValue(Type typeValue) {
            super(Type.TYPE);
            this.typeValue = typeValue;
        }

        @Override
        public long id() {
            return System.identityHashCode(typeValue);
        }

        @Override
        public Type asType() {
            return typeValue;
        }

        @Override
        public int length() throws TypeError {
            if(typeValue instanceof Type.Tuple){
                return ((Type.Tuple) typeValue).elementCount();
            }else{
                return super.length();
            }
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(typeValue instanceof Type.Tuple){
                if(index<0||index>=((Type.Tuple) typeValue).elementCount()){
                    throw new ConcatRuntimeError("index out of bounds:"+index+" size:"
                            +((Type.Tuple) typeValue).elementCount());
                }
                return ofType(((Type.Tuple) typeValue).get((int)index));
            }else{
                return super.get(index);
            }
        }

        @Override
        public String stringValue() {
            return typeValue.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeValue typeValue1 = (TypeValue) o;
            return Objects.equals(typeValue, typeValue1.typeValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeValue);
        }
    }

    public static Value ofChar(int codePoint) {
        return new CharValue(codePoint);
    }
    private static class CharValue extends Value{
        final int codePoint;
        private CharValue(int codePoint) {
            super(Type.CHAR);
            this.codePoint = codePoint;
        }

        @Override
        public long id() {
            return codePoint;
        }

        public int getChar() {
            return codePoint;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                return ofInt(codePoint);
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            return String.valueOf(Character.toChars(codePoint));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CharValue aChar = (CharValue) o;
            return codePoint == aChar.codePoint;
        }

        @Override
        public int hashCode() {
            return Objects.hash(codePoint);
        }
    }

    public static Value ofByte(byte aByte) {
        return new ByteValue(aByte);
    }
    private static class ByteValue extends Value{
        final byte byteValue;
        private ByteValue(byte byteValue) {
            super(Type.BYTE);
            this.byteValue = byteValue;
        }

        @Override
        public long id() {
            return byteValue;
        }

        @Override
        public byte asByte(){
            return byteValue;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                return ofInt(byteValue&0xff);
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public ByteList toByteList(){
            byte[] data=new byte[16];
            data[0]=byteValue;
            return new ByteListImpl(1,data);
        }

        @Override
        public String stringValue() {
            return String.format("0x%02x", byteValue);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ByteValue byteValue = (ByteValue) o;
            return this.byteValue == byteValue.byteValue;
        }
        @Override
        public int hashCode() {
            return Byte.hashCode(byteValue);
        }
    }

    public static Value ofString(String stringValue) {
        return new ListValue(Type.STRING(),stringValue.codePoints().mapToObj(Value::ofChar)
                .collect(Collectors.toCollection(ArrayList::new)));
    }
    public static Value createList(Type type, ArrayList<Value> elements) throws ConcatRuntimeError {
        return new ListValue(type,elements);
    }
    public static Value createList(Type type, long initCap) throws ConcatRuntimeError {
        if(initCap<0){
            throw new ConcatRuntimeError("initial capacity has to be at least 0");
        }else if(initCap>Integer.MAX_VALUE){
            throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
        }
        if(type==Type.BYTES()){
            return new ByteListImpl((int)initCap);
        }else{
            return new ListValue(type,new ArrayList<>((int) initCap));
        }
    }

    private static class ListValue extends Value{
        final ArrayList<Value> elements;
        /**true when this value is currently used in toString (used to handle self containing lists)*/
        private boolean inToString;
        private ListValue(Type type,ArrayList<Value> elements) {
            super(type);
            this.elements = elements;
        }

        @Override
        public long id() {
            return System.identityHashCode(elements);
        }

        @Override
        boolean isEqualTo(Value v) {
            return v instanceof ListValue &&
                    ((ListValue) v).elements==elements;//check reference equality
        }

        @Override
        public ListValue clone(boolean deep) {
            ArrayList<Value> newElements;
            if(deep){
                newElements=elements.stream().map(v->v.clone(true)).collect(Collectors.toCollection(ArrayList::new));
            }else{
                newElements=new ArrayList<>(elements);
            }
            return new ListValue(type,newElements);
        }

        @Override
        public ByteList toByteList() throws ConcatRuntimeError {
            if(type.content()==Type.BYTE){
                byte[] bytes=new byte[elements.size()];
                for(int i=0;i<elements.size();i++){
                    bytes[i]=elements.get(i).asByte();
                }
                return new ByteListImpl(bytes.length,bytes);
            }
            return super.toByteList();
        }

        @Override
        public int length() {
            return elements.size();
        }

        @Override
        boolean notList() {
            return false;
        }
        @Override
        public List<Value> getElements() {
            return elements;
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(index<0||index>=elements.size()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+elements.size());
            }
            return elements.get((int)index);
        }
        @Override
        public void set(long index,Value value) throws ConcatRuntimeError {
            if(index<0||index>=elements.size()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+elements.size());
            }
            elements.set((int)index,value.castTo(type.content()));
        }
        @Override
        public Value getSlice(long off, long to) throws ConcatRuntimeError {
            if(off<0||to>elements.size()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+elements.size());
            }
            return new ListSlice(this,(int)off,(int)to);
        }

        @Override
        public void setSlice(long off, long to, Value value) throws ConcatRuntimeError {
            if(off<0||to>elements.size()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+elements.size());
            }
            List<Value> sublist=elements.subList((int)off,(int)to);
            try {
                Type content=type.content();
                List<Value> add=value.getElements().stream().map(e->e.unsafeCastTo(content)).toList();
                sublist.clear();
                sublist.addAll(add);
            }catch (WrappedConcatError e){
                throw e.wrapped;
            }
        }

        @Override
        public void fill(Value val, long off, long count) throws ConcatRuntimeError {
            if(off<0){
                throw new ConcatRuntimeError("Index out of bounds:"+off+" length:"+elements.size());
            }
            if(count<0){
                throw new ConcatRuntimeError("Count has to be at least 0");
            }
            if(off+count>Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            val=val.castTo(type.content());
            elements.ensureCapacity((int)(off+count));
            int set=(int)Math.min(elements.size()-off,count);
            int add=(int)(count-set);
            for(int i=0;i<set;i++){
                elements.set(i+(int)off,val);
            }
            for(int i=0;i<add;i++){
                elements.add(val);
            }
        }

        @Override
        public void ensureCap(long newCap) throws ConcatRuntimeError {
            if(newCap> Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            elements.ensureCapacity((int)newCap);
        }

        @Override
        public void push(Value value, boolean start) throws ConcatRuntimeError {
            if(start){
                elements.add(0,value.castTo(type.content()));
            }else{
                elements.add(value.castTo(type.content()));
            }
        }
        @Override
        public void pushAll(Value value, boolean start) throws ConcatRuntimeError {
            if(value.type.isList()){
                try{
                    List<Value> push=value.getElements().stream().map(v->v.unsafeCastTo(type.content())).toList();
                    if(start){
                        elements.addAll(0,push);
                    }else{
                        elements.addAll(push);
                    }
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                throw new TypeError("Cannot concat "+type+" and "+value.type);
            }
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.GENERIC_LIST||this.type.equals(type)){
                return this;
            }else if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, elements.stream().map(v -> v.unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            if(Type.CHAR.equals(type.content())){
                StringBuilder str=new StringBuilder();
                for(Value v:elements){
                    str.append(Character.toChars(((CharValue)v).getChar()));
                }
                return str.toString();
            }else{
                if(inToString){
                    return "[...]";
                }else{
                    inToString=true;
                    String ret=elements.toString();
                    inToString=false;
                    return ret;
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value asValue)|| asValue.notList()) return false;
            try {
                return Objects.equals(elements, asValue.getElements());
            } catch (TypeError e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(elements);
        }
    }
    private static class ListSlice extends Value{
        final ListValue list;
        final   int off;
        private int to;
        /**true when this value is currently used in toString (used to handle self containing lists)*/
        private boolean inToString;
        private ListSlice(ListValue list, int off, int to) {
            super(list.type);
            this.list = list;
            this.off = off;
            this.to = to;
        }

        @Override
        boolean isEqualTo(Value v) {
            return v instanceof ListSlice && ((ListSlice) v).list.isEqualTo(list)
                    && ((ListSlice) v).off == off&&((ListSlice) v).to == to;
        }

        @Override
        public Value clone(boolean deep) {
            ArrayList<Value> newElements;
            if(deep){
                newElements= getElements().stream().map(v->v.clone(true)).collect(Collectors.toCollection(ArrayList::new));
            }else{
                newElements=new ArrayList<>(getElements());
            }
            return new ListValue(type,newElements);
        }

        @Override
        public ByteList toByteList() throws ConcatRuntimeError {
            if(type.content()==Type.BYTE){
                if(type.content()==Type.BYTE){
                    List<Value> list = this.list.elements.subList(off, to);
                    byte[] bytes=new byte[list.size()];
                    for(int i=0;i<list.size();i++){
                        bytes[i]= list.get(i).asByte();
                    }
                    return new ByteListImpl(bytes.length,bytes);
                }
            }
            return super.toByteList();
        }

        @Override
        public int length() {
            return to-off;
        }

        @Override
        boolean notList() {
            return false;
        }
        @Override
        public List<Value> getElements() {
            return list.elements.subList(off,to);
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(index<0||index>=length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            return list.get(index+off);
        }
        @Override
        public void set(long index,Value value) throws ConcatRuntimeError {
            if(index<0||index>=length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            list.set(index+off,value.castTo(type.content()));
        }
        @Override
        public Value getSlice(long off, long to) throws ConcatRuntimeError {
            if(off<0||to>length()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+length());
            }
            return new ListSlice(list,(int)(this.off+off),(int)(this.off+to));
        }

        @Override
        public void setSlice(long off, long to, Value value) throws ConcatRuntimeError {
            if(off<0||to>length()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+length());
            }
            list.setSlice(this.off+off,this.off+to,value);
            this.to+=value.length()-(to-off);
        }

        @Override
        public void fill(Value val, long off, long count) throws ConcatRuntimeError {
            if(off<0){
                throw new ConcatRuntimeError("Index out of bounds:"+off+" length:"+length());
            }
            if(count<0){
                throw new ConcatRuntimeError("Count has to be at least 0");
            }
            if(off+count>Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            val=val.castTo(type.content());
            ensureCap(off+count);
            int set=(int)Math.min(length()-off,count);
            int add=(int)(count-set);
            for(int i=0;i<set;i++){
                list.elements.set(this.off+i+(int)off,val);
            }
            for(int i=0;i<add;i++){
                list.elements.add(to++,val);
            }
        }

        @Override
        public void ensureCap(long newCap) throws ConcatRuntimeError {
            if(newCap> Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            list.ensureCap(newCap+Math.max(list.length()-length(),0));
        }

        @Override
        public void push(Value value, boolean start) throws ConcatRuntimeError {
            if(start){
                list.elements.add(off,value.castTo(type.content()));
            }else{
                list.elements.add(to,value.castTo(type.content()));
            }
            to++;
        }
        @Override
        public void pushAll(Value value, boolean start) throws ConcatRuntimeError {
            if(value.type.isList()){
                try{
                    List<Value> push=value.getElements().stream().map(v->v.unsafeCastTo(type.content())).toList();
                    if(start){
                        list.elements.addAll(off,push);
                    }else{
                        list.elements.addAll(to,push);
                    }
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                throw new TypeError("Cannot concat "+type+" and "+value.type);
            }
            to+=value.length();
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.GENERIC_LIST||this.type.equals(type)){
                return this;
            }else if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, getElements().stream().map(v -> v.unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            if(Type.CHAR.equals(type.content())){
                StringBuilder str=new StringBuilder();
                for(Value v: getElements()){
                    str.append(Character.toChars(((CharValue)v).getChar()));
                }
                return str.toString();
            }else{
                if(inToString){
                    return "[...]";
                }else{
                    inToString=true;
                    String ret=getElements().toString();
                    inToString=false;
                    return ret;
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value asValue)|| asValue.notList()) return false;
            try {
                return Objects.equals(getElements(), asValue.getElements());
            } catch (TypeError e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public int hashCode() {
            return Objects.hash(getElements());
        }
    }
    public static abstract class ByteList extends Value {
        private ByteList(){
            super(Type.BYTES());
        }
        public abstract int length();
        @Override
        public ByteList toByteList(){
            return this;
        }
        @Override
        public ByteList asByteList() {
            return this;
        }
        @Override
        boolean notList() {
            return false;
        }
        @Override
        public List<Value> getElements() {
            ArrayList<Value> wrapped=new ArrayList<>(length());
            for(int i=0;i<length();i++){
                wrapped.add(ofByte(unsafeGetByte(i)));
            }
            return wrapped;
        }
        protected abstract byte unsafeGetByte(int index);
        public byte getByte(long index) throws ConcatRuntimeError {
            if(index<0||index>=length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            return unsafeGetByte((int)index);
        }
        @Override
        public Value get(long index) throws ConcatRuntimeError {
            return ofByte(getByte(index));
        }
        public abstract void setByte(long index,byte b) throws ConcatRuntimeError;
        @Override
        public void set(long index, Value value) throws ConcatRuntimeError {
            setByte(index,value.asByte());
        }
        @SuppressWarnings("unused")
        protected abstract void insert(int index, byte b) throws ConcatRuntimeError;
        @SuppressWarnings("unused")
        protected abstract void insertAll(int index,byte[] bytes) throws ConcatRuntimeError;
        public abstract void ensureCap(long newCap) throws ConcatRuntimeError;
        public abstract ByteList getSlice(long off, long to) throws ConcatRuntimeError;
        public abstract void setSlice(long off, long to, byte[] bytes) throws ConcatRuntimeError;
        public abstract byte[] toByteArray();
        @Override
        public void setSlice(long off, long to, Value value) throws ConcatRuntimeError {
            setSlice(off, to, value.toByteList().toByteArray());
        }
        public abstract void fill(byte val, long off, long count) throws ConcatRuntimeError;
        @Override
        public void fill(Value val, long off, long count) throws ConcatRuntimeError {
            fill(val.asByte(), off, count);
        }
        public abstract void push(byte value, boolean start) throws ConcatRuntimeError;
        @Override
        public void push(Value value, boolean start) throws ConcatRuntimeError {
            push(value.asByte(), start);
        }
        public abstract void pushAll(byte[] value, boolean start) throws ConcatRuntimeError;
        @Override
        public void pushAll(Value value, boolean start) throws ConcatRuntimeError {
            pushAll(value.toByteList().toByteArray(), start);
        }
        @Override
        public String stringValue() {
            byte[] bytes=toByteArray();
            StringBuilder ret=new StringBuilder("[");
            for(byte b: bytes){
                if(ret.length()>1){
                    ret.append(", ");
                }
                ret.append("0x").append(Integer.toHexString(b&0xff));
            }
            return ret.append("]").toString();
        }

    }
    private static class ByteListImpl extends ByteList {
        private byte[] elements;
        private int size;
        private ByteListImpl(int initCap) {
            this.elements = new byte[Math.max(initCap,16)];
            size=0;
        }
        private ByteListImpl(int size, byte[] initValue) {
            this.size=size;
            this.elements = initValue;
        }
        @Override
        public long id() {
            return System.identityHashCode(elements);
        }
        @Override
        boolean isEqualTo(Value v) {
            return this==v;//check reference equality
        }
        @Override
        public ByteListImpl clone(boolean deep) {
            return new ByteListImpl(size,elements.clone());
        }
        @Override
        public int length() {
            return size;
        }
        @Override
        public byte[] toByteArray() {
            return Arrays.copyOf(elements,size);
        }
        @Override
        protected byte unsafeGetByte(int index) {
            return elements[index];
        }
        @Override
        public void setByte(long index, byte value) throws ConcatRuntimeError {
            if(index<0||index>=size){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+size);
            }
            elements[(int)index]=value;
        }
        @Override
        protected void insert(int index, byte b) throws ConcatRuntimeError {
            if(index<0||index>size){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+size);
            }
            ensureCap(size+1);
            System.arraycopy(elements,index,elements,index+1,size);
            elements[index]=b;
            size++;
        }
        @Override
        protected void insertAll(int index, byte[] bytes) throws ConcatRuntimeError {
            if(index<0||index>length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            ensureCap(size+bytes.length);
            System.arraycopy(elements,index,elements,index+bytes.length,size);
            System.arraycopy(bytes,0,elements,index,bytes.length);
            size+=bytes.length;
        }

        @Override
        public ByteList getSlice(long off, long to) throws ConcatRuntimeError {
            if(off<0||to>size||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+size);
            }
            return new ByteListSlice(this,(int)off,(int)to);
        }
        @Override
        public void setSlice(long off, long to, byte[] bytes) throws ConcatRuntimeError {
            if(off<0||to>size||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+size);
            }
            ensureCap(size+bytes.length-(to-off));
            if(to<size) {
                System.arraycopy(elements, (int) to, elements, (int) off + bytes.length, size - (int) to);
            }
            System.arraycopy(bytes,0,elements,(int)off,bytes.length);
            size+=bytes.length-(to-off);
        }

        @Override
        public void fill(byte val, long off, long count) throws ConcatRuntimeError {
            if(off<0){
                throw new ConcatRuntimeError("Index out of bounds:"+off+" length:"+size);
            }
            if(count<0){
                throw new ConcatRuntimeError("Count has to be at least 0");
            }
            if(off+count>Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            ensureCap((int)(off+count));
            Arrays.fill(elements,(int)off,(int)(off+count),val);
            size=Math.max(size,(int)(off+count));
        }
        @Override
        public void ensureCap(long newCap) throws ConcatRuntimeError {
            if(newCap> Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            if(elements.length<newCap){
                byte[] newElements=new byte[Math.max((int)newCap,size+16)];
                System.arraycopy(elements,0,newElements,0,size);
                elements=newElements;
            }
        }
        @Override
        public void push(byte value, boolean start) throws ConcatRuntimeError {
            ensureCap(size+1);
            if(start){
                System.arraycopy(elements,0,elements,1,size);
                elements[0]=value;
            }else{
                elements[size]=value;
            }
            size++;
        }
        @Override
        public void pushAll(byte[] bytes, boolean start) throws ConcatRuntimeError {
            ensureCap(size+bytes.length);
            if(start){
                System.arraycopy(elements,0,elements,bytes.length,size);
                System.arraycopy(bytes,0,elements,0,bytes.length);
            }else{
                System.arraycopy(bytes,0,elements,size,bytes.length);
            }
            size+=bytes.length;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.GENERIC_LIST||this.type.equals(type)){
                return this;
            }else if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, getElements().stream().map(v -> v.unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value asValue)|| asValue.notList()) return false;
            try {
                return Objects.equals(getElements(), asValue.getElements());
            } catch (TypeError e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }
    }
    private static class ByteListSlice extends ByteList{
        final ByteListImpl list;
        final int off;
        int to;
        private ByteListSlice(ByteListImpl list, int off,int to) {
            this.list = list;
            this.off = off;
            this.to = to;
        }
        @Override
        boolean isEqualTo(Value v) {
            return v instanceof ByteListSlice && ((ByteListSlice) v).list.isEqualTo(list)
                    && ((ByteListSlice) v).off == off&&((ByteListSlice) v).to == to;
        }
        @Override
        public ByteList clone(boolean deep) {
            return new ByteListImpl(length(),toByteArray());
        }
        @Override
        public ByteList toByteList(){
            return this;
        }
        @Override
        public int length() {
            return to-off;
        }

        @Override
        boolean notList() {
            return false;
        }

        @Override
        public byte[] toByteArray() {
            return Arrays.copyOfRange(list.elements,off,to);
        }

        @Override
        public byte unsafeGetByte(int index){
            return list.unsafeGetByte(index+off);
        }
        @Override
        public void setByte(long index,byte value) throws ConcatRuntimeError {
            if(index<0||index>=length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            list.setByte(index+off,value);
        }
        @Override
        protected void insert(int index, byte b) throws ConcatRuntimeError {
            list.insert(index+off,b);
        }
        @Override
        protected void insertAll(int index, byte[] bytes) throws ConcatRuntimeError {
            list.insertAll(index+off,bytes);
        }
        @Override
        public ByteList getSlice(long off, long to) throws ConcatRuntimeError {
            if(off<0||to>length()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+length());
            }
            return new ByteListSlice(list,(int)(this.off+off),(int)(this.off+to));
        }
        @Override
        public void setSlice(long off, long to, byte[] bytes) throws ConcatRuntimeError {
            if(off<0||to>length()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+length());
            }
            list.setSlice(this.off+off,this.off+to,bytes);
            this.to+=bytes.length-(to-off);
        }
        @Override
        public void fill(byte val, long off, long count) throws ConcatRuntimeError {
            if(off<0){
                throw new ConcatRuntimeError("Index out of bounds:"+off+" length:"+length());
            }
            if(count<0){
                throw new ConcatRuntimeError("Count has to be at least 0");
            }
            if(off+count>Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            ensureCap(off+count);
            int set=(int)Math.min(length()-off,count);
            int add=(int)(count-set);
            for(int i=0;i<set;i++){
                list.setByte(this.off+i+(int)off,val);
            }
            for(int i=0;i<add;i++){
                list.insert(to++,val);
            }
        }

        @Override
        public void ensureCap(long newCap) throws ConcatRuntimeError {
            if(newCap> Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            list.ensureCap(newCap+Math.max(list.length()-length(),0));
        }

        @Override
        public void push(byte value, boolean start) throws ConcatRuntimeError {
            if(start){
                list.insert(off,value);
            }else{
                list.insert(to,value);
            }
            to++;
        }
        @Override
        public void pushAll(byte[] value, boolean start) throws ConcatRuntimeError {
            if(start){
                list.insertAll(off,value);
            }else{
                list.insertAll(to,value);
            }
        }
        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.GENERIC_LIST||this.type.equals(type)){
                return this;
            }else if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, getElements().stream().map(v -> v.unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value asValue)|| asValue.notList()) return false;
            try {
                return Objects.equals(getElements(), asValue.getElements());
            } catch (TypeError e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(toByteArray());
        }
    }

    public static Value newStackSlice(RandomAccessStack<Value> stack, long lower, long upper) throws ConcatRuntimeError {
        if(lower<0||upper>stack.size()||lower>upper){
            throw new ConcatRuntimeError("invalid stack-slice: "+lower+":"+upper+" length:"+stack.size());
        }
        return new StackSlice(stack, (int)lower, (int)upper);
    }
    private static class StackSlice extends Value{
        final RandomAccessStack<Value> stack;
        /**slice end closer to top of stack,
         * counted in elements from the top of the stack with 1 being the top element*/
        final   int top;
        /**slice end closer to bottom of stack,
         * counted in elements from the top of the stack with 1 being the top element*/
        private int bottom;
        /**true when this value is currently used in toString (used to handle self containing lists)*/
        private boolean inToString;

        private StackSlice(RandomAccessStack<Value> stack, int top, int bottom) {
            super(Type.listOf(Type.ANY));
            this.stack = stack;
            this.top = top;
            this.bottom = bottom;
        }

        @Override
        public Value clone(boolean deep) {
            return new ListValue(Type.listOf(Type.ANY),new ArrayList<>(getElements()));
        }

        @Override
        boolean isEqualTo(Value v) {
            return v instanceof StackSlice && ((StackSlice) v).stack==stack
                    && ((StackSlice) v).top == top &&((StackSlice) v).bottom == bottom;
        }

        @Override
        public int length() {
            return bottom - top;
        }

        @Override
        boolean notList() {
            return false;
        }
        @Override
        public List<Value> getElements() {
            return stack.subList(bottom,top);
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(index<0||index>=length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            return stack.get(bottom-(int)index);
        }
        @Override
        public void set(long index,Value value) throws ConcatRuntimeError {
            if(index<0||index>=length()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length());
            }
            stack.set(bottom -(int)index,value.castTo(type.content()));
        }
        @Override
        public Value getSlice(long off, long to) throws ConcatRuntimeError {
            if(off<0||to>length()||to<off){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+length());
            }
            return new StackSlice(stack,(int)(bottom -to),(int)(bottom -off));
        }

        @Override
        public void setSlice(long off, long to, Value value) throws ConcatRuntimeError {
            if(off<0||to>length()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+length());
            }
            stack.setSlice((int)(bottom-to),(int)(bottom -off),value.getElements());
            this.bottom +=value.length()-(to-off);
        }

        @Override
        public void fill(Value val, long off, long count) throws ConcatRuntimeError {
            if(off<0){
                throw new ConcatRuntimeError("Index out of bounds:"+off+" length:"+length());
            }
            if(count<0){
                throw new ConcatRuntimeError("Count has to be at least 0");
            }
            if(off+count>Integer.MAX_VALUE){
                throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
            }
            val=val.castTo(type.content());
            ensureCap(off+count);
            int set=(int)Math.min(length()-off,count);
            int add=(int)(count-set);
            for(int i=0;i<set;i++){
                stack.set(bottom -(i+(int)off),val);
            }
            for(int i=0;i<add;i++){
                stack.insert(top,val);
                bottom++;
            }
        }

        @Override
        public void ensureCap(long newCap){
            //ensure cap does nothing for stack slices
        }

        @Override
        public void push(Value value, boolean start) throws ConcatRuntimeError {
            if(start){
                stack.insert(bottom,value.castTo(type.content()));
            }else{
                stack.insert(top,value.castTo(type.content()));
            }
            bottom++;
        }
        @Override
        public void pushAll(Value value, boolean start) throws ConcatRuntimeError {
            if(value.type.isList()){
                try{
                    List<Value> push=value.getElements().stream().map(v->v.unsafeCastTo(type.content())).toList();
                    if(start){
                        stack.insertAll(bottom,push);
                    }else{
                        stack.insertAll(top,push);
                    }
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                throw new TypeError("Cannot concat "+type+" and "+value.type);
            }
            bottom +=value.length();
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.GENERIC_LIST||this.type.equals(type)){
                return this;
            }else if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, getElements().stream().map(v -> v.unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedConcatError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            if(inToString){
                return "[...]";
            }else{
                inToString=true;
                String ret=getElements().toString();
                inToString=false;
                return ret;
            }
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value asValue)|| asValue.notList()) return false;
            try {
                return Objects.equals(getElements(), asValue.getElements());
            } catch (TypeError e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public int hashCode() {
            return Objects.hash(getElements());
        }
    }

    public static Value createTuple(Type.Tuple type,Value[] elements) throws ConcatRuntimeError {
        if(elements.length!=type.elementCount()){
            throw new IllegalArgumentException("elements has to have the same length as types");
        }
        for(int i=0;i<elements.length;i++){
            elements[i]=elements[i].castTo(type.get(i));
        }
        return new TupleValue(type, elements);
    }
    private static class TupleValue extends Value{
        final Value[] elements;
        /**true when this value is currently used in toString (used to handle self containing tuples)*/
        private boolean inToString;

        private TupleValue(Type.Tuple type,Value[] elements){
            super(type);
            this.elements = elements;
        }

        @Override
        public long id() {
            return System.identityHashCode(elements);
        }

        @Override
        boolean isEqualTo(Value v) {
            return v instanceof TupleValue &&
                    ((TupleValue) v).elements==elements;//reference equality
        }

        @Override
        public Value clone(boolean deep) {
            Value[] newElements;
            if(deep){
                newElements=Arrays.stream(elements).map(v->v.clone(true)).toArray(Value[]::new);
            }else{
                newElements=elements.clone();
            }
            return new TupleValue((Type.Tuple) type,newElements);
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.GENERIC_TUPLE||this.type.equals(type)){
                return this;
            }else if(type instanceof Type.Tuple&&
                    ((Type.Tuple) type).elementCount()==((Type.Tuple)this.type).elementCount()){
                Value[] newValues=elements.clone();
                return createTuple((Type.Tuple)type,newValues);
            }else if(type.isList()){
                ArrayList<Value> newElements=new ArrayList<>(elements.length);
                for (Value element : elements) {
                    newElements.add(element.castTo(type.content()));
                }
                return new ListValue(type,newElements);
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public int length() throws TypeError {
            return elements.length;
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(index<0||index>=elements.length){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+elements.length);
            }
            return elements[(int)index];
        }
        @Override
        public void set(long index, Value value) throws ConcatRuntimeError {
            if(index<0||index>=elements.length){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+elements.length);
            }
            elements[(int)index]=value.castTo(((Type.Tuple)type).get((int)index));
        }

        @Override
        public String stringValue() {
            if(inToString){
                return "(...)";
            }else{
                inToString=true;
                StringBuilder res=new StringBuilder("(");
                for(int i=0;i<elements.length;i++){
                    if(i>0){
                        res.append(',');
                    }
                    res.append(elements[i]);
                }
                inToString=false;
                return res.append(")").toString();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TupleValue that = (TupleValue) o;
            return Arrays.equals(elements, that.elements);
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }
    }

    public static Value createProcedure(int startAddress, ArrayList<Interpreter.Token> tokens,
                                        Interpreter.ProcedureContext variableContext) {
        return new Procedure(startAddress,tokens,variableContext);
    }
    static class Procedure extends Value implements Interpreter.CodeSection {
        final int pos;
        final Interpreter.ProcedureContext context;
        final ArrayList<Interpreter.Token> tokens;
        private Procedure(int pos, ArrayList<Interpreter.Token> tokens, Interpreter.ProcedureContext context) {
            super(Type.PROCEDURE);
            this.pos = pos;
            this.tokens=tokens;
            this.context=context;
        }

        @Override
        public long id() {
            return System.identityHashCode(pos);
        }

        @Override
        public String stringValue() {
            return "@("+pos+")";
        }

        CurriedProcedure withCurried(Value[] curried){
            return new CurriedProcedure(this,curried);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Procedure that = (Procedure) o;
            return pos==that.pos;
        }
        @Override
        public int hashCode() {
            return Objects.hash(pos);
        }

        @Override
        public ArrayList<Interpreter.Token> tokens() {
            return tokens;
        }

        @Override
        public Interpreter.VariableContext context() {
            return context;
        }
    }
    static class CurriedProcedure extends Value implements Interpreter.CodeSection{
        final Procedure parent;
        final Value[] curried;
        public CurriedProcedure(Procedure parent, Value[] curried) {
            super(parent.type);
            this.parent=parent;
            this.curried=curried;
        }
        @Override
        public long id() {
            return parent.id();
        }

        @Override
        public String stringValue() {
            return parent.stringValue()+Arrays.toString(curried);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CurriedProcedure that = (CurriedProcedure) o;
            return parent==that.parent&& Arrays.equals(curried, that.curried);
        }
        @Override
        public int hashCode() {
            return parent.hashCode()+31*Arrays.hashCode(curried);
        }

        @Override
        public ArrayList<Interpreter.Token> tokens() {
            return parent.tokens;
        }

        @Override
        public Interpreter.VariableContext context() {
            return parent.context;
        }
    }

    public static Value ofFile(FileStream stream) {
        return new FileValue(stream);
    }
    private static class FileValue extends Value{
        final FileStream streamValue;
        private FileValue(FileStream streamValue) {
            super(Type.FILE);
            this.streamValue = streamValue;
        }

        @Override
        public long id() {
            return System.identityHashCode(streamValue);
        }

        @Override
        boolean isEqualTo(Value v) {
            return v == this;
        }

        @Override
        public Value clone(boolean deep) {
            throw new UnsupportedOperationException("cloning of "+type+" is currently not supported");
        }

        @Override
        public FileStream asStream(){
            return streamValue;
        }

        @Override
        public String stringValue() {
            return "["+type+"]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileValue that = (FileValue) o;
            return Objects.equals(streamValue, that.streamValue);
        }
        @Override
        public int hashCode() {
            return Objects.hash(streamValue);
        }
    }

    private static Value cmpToValue(int c, OperatorType opType) {
        switch (opType){
            case GT -> {
                return c > 0 ? TRUE : FALSE;
            }
            case GE -> {
                return c >= 0 ? TRUE : FALSE;
            }
            case EQ -> {
                return c == 0 ? TRUE : FALSE;
            }
            case NE -> {
                return c != 0 ? TRUE : FALSE;
            }
            case LE -> {
                return c <= 0 ? TRUE : FALSE;
            }
            case LT -> {
                return c < 0 ? TRUE : FALSE;
            }
            default ->
                    throw new RuntimeException(opType +" is no valid comparison operator");
        }
    }

    public static Value compare(Value a, OperatorType opType, Value b) throws TypeError {
        if(opType==OperatorType.REF_EQ){
            return a.isEqualTo(b)?TRUE:FALSE;
        }else if(opType==OperatorType.REF_NE){
            return a.isEqualTo(b)?FALSE:TRUE;
        }else if(opType==OperatorType.EQ){
            return a.equals(b)?TRUE:FALSE;
        }else if(opType==OperatorType.NE){
            return a.equals(b)?FALSE:TRUE;
        }else if(a.isString()&&b.isString()){
            int c=a.stringValue().compareTo(b.stringValue());
            return cmpToValue(c, opType);
        }else if(a instanceof CharValue &&b instanceof CharValue){
            return cmpToValue(Integer.compare(((CharValue) a).codePoint,((CharValue) b).codePoint), opType);
        }else if(a instanceof NumberValue&&b instanceof NumberValue){
            return mathOp(a,b,(x,y)->cmpToValue(x.compareTo(y),opType),(x,y)->cmpToValue(x.compareTo(y),opType));
        }else if(a instanceof TypeValue&&b instanceof TypeValue&&(opType==OperatorType.GE||opType==OperatorType.LE)){
            if(opType==OperatorType.LE){
                return a.asType().isSubtype(b.asType())?TRUE:FALSE;
            }else {
                return b.asType().isSubtype(a.asType())?TRUE:FALSE;
            }
        }else{
            throw new TypeError("cannot compare "+a.type+" and "+b.type);
        }
    }

    public static Value mathOp(Value a, Value b, BiFunction<Long,Long,Value> intOp, BiFunction<Double,Double,Value> floatOp) throws TypeError {
        if(a instanceof IntValue){
            if(b instanceof IntValue){
                return intOp.apply(((IntValue) a).intValue, ((IntValue) b).intValue);
            }else if(b instanceof FloatValue){
                return floatOp.apply((double)((IntValue) a).intValue, ((FloatValue) b).floatValue);
            }
        }else if(a instanceof FloatValue){
            if(b instanceof IntValue){
                return floatOp.apply((((FloatValue) a).floatValue),(double)((IntValue) b).intValue);
            }else if(b instanceof FloatValue){
                return floatOp.apply((((FloatValue) a).floatValue),((FloatValue) b).floatValue);
            }
        }
        throw new TypeError("invalid parameters for arithmetic operation:"+a.type+" "+b.type);
    }
    public static Value logicOp(Value a, Value b, BinaryOperator<Boolean> boolOp,BinaryOperator<Long> intOp) throws TypeError {
        if(a.type==Type.BOOL&&b.type==Type.BOOL){
            return boolOp.apply(a.asBool(),b.asBool())?TRUE:FALSE;
        }else{
            return intOp(a,b,intOp);
        }
    }
    public static Value intOp(Value a,Value b,BinaryOperator<Long> intOp) throws TypeError {
        if(a instanceof IntValue&&b instanceof IntValue){
                return ofInt(intOp.apply(((IntValue) a).intValue, ((IntValue) b).intValue));
        }
        throw new TypeError("invalid parameters for int operator:"+a.type+" "+b.type);
    }
}
