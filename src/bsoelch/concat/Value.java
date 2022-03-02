package bsoelch.concat;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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
        @Override
        Object rawData() {
            return false;
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
        @Override
        Object rawData() {
            return true;
        }
    };

    final Type type;
    protected Value(Type type) {
        this.type = type;
    }


    public long id() {
        return System.identityHashCode(this);
    }

    /*raw data of this Value as a standard java Object*/
    Object rawData() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to native value");
    }
    /*updates mutable values form their raw data representation*/
    void updateFrom(Object nativeArg) throws ConcatRuntimeError {}

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
    public ByteList toByteList() throws ConcatRuntimeError {
        throw new TypeError("Converting "+type+" to raw-bytes is not supported");
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
    public void clear() throws ConcatRuntimeError {
        throw new TypeError(type+" does not support clear");
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
    //addLater add instructions to insert/remove arbitrary elements of lists
    public void push(Value value,boolean start) throws ConcatRuntimeError {
        throw new TypeError("adding elements is not supported for type "+type);
    }
    public void pushAll(Value value,boolean start) throws ConcatRuntimeError {
        throw new TypeError("adding elements is not supported for type "+type);
    }
    public Value unwrap() throws ConcatRuntimeError {
        throw new TypeError("Cannot unwrap values of type "+type);
    }
    public boolean hasValue() throws TypeError {
        throw new TypeError("Cannot unwrap values of type "+type);
    }

    public Value clone(boolean deep) {
        return this;
    }
    public Value castTo(Type type) throws ConcatRuntimeError {
        if(this.type.isSubtype(type)){
            return this;
        }else{
            throw new TypeError("cannot cast from "+this.type+" to "+type);
        }
    }

    private Value unsafeCastTo(Type type) throws WrappedConcatError {
        try {
            return castTo(type);
        } catch (ConcatRuntimeError e) {
            throw new WrappedConcatError(e);
        }
    }

    public boolean isString(){
        return Type.RAW_STRING().equals(type)||Type.UNICODE_STRING().equals(type);
    }
    public abstract String stringValue();

    @Override
    public String toString() {
        return type+":"+stringValue();
    }


    private interface NumberValue{}
    private static int valueOf(char digit,int base) throws ConcatRuntimeError {
        int val;
        if(digit>='0'&&digit<='9'){
            val=digit-'0';
        }else if(digit>='A'&&digit<='Z'){
            val=digit-'A'+10;
        }else if(digit>='a'&&digit<='z'){
            val=digit-'a'+(base<37?10:36);
        }else{
            throw new ConcatRuntimeError("invalid digit for base "+base+" number '"+digit+"'");
        }
        if(val>=base){
            throw new ConcatRuntimeError("invalid digit for base "+base+" number '"+digit+"'");
        }
        return val;
    }

    public static Value ofInt(long intValue,boolean unsigned) {
        return new IntValue(intValue,unsigned);
    }
    public static long parseInt(String source,int base,boolean unsigned) throws ConcatRuntimeError {
        if(base<2||base>62){
            throw new ConcatRuntimeError("base out of range:"+base+" base has to be between 2 and 62");
        }
        int i=0;
        boolean sgn=source.startsWith("-");
        if(sgn){
            i++;
        }
        long res=0;
        long max=unsigned?Long.divideUnsigned(-1,base):Long.MAX_VALUE/base;
        for(;i<source.length();i++){
            if(res>max){//signed compare works here, since sign bit is 0 in all cases
                throw new ConcatRuntimeError("invalid string-format for int \""+source+"\" (overflow)");
            }
            res*=base;
            res+=valueOf(source.charAt(i),base);
        }
        return sgn?-res:res;
    }
    private static class IntValue extends Value implements NumberValue{
        final long intValue;
        private IntValue(long intValue, boolean unsigned) {
            super(unsigned?Type.UINT:Type.INT);
            this.intValue = intValue;
        }

        @Override
        public long id() {
            return intValue;
        }

        @Override
        public double asDouble() {
            return type==Type.UINT?((intValue>>>1)*2.0):intValue;
        }
        @Override
        public long asLong() {
            return intValue;
        }
        @Override
        Object rawData() {
            return intValue;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                if(this.type==Type.INT){
                    return this;
                }else{
                    return ofInt(intValue,false);
                }
            }else if(type==Type.UINT){
                if(this.type==Type.UINT){
                    return this;
                }else{
                    return ofInt(intValue,true);
                }
            }else if(type==Type.BYTE){
                if(intValue<0||intValue>0xff){
                    throw new ConcatRuntimeError("cannot cast 0x"+Long.toHexString(intValue)+" to byte");
                }
                return Value.ofByte((byte)intValue);

            }else if(type==Type.CODEPOINT){
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
            return ofInt(-intValue,false);
        }
        public Value flip(){
            return ofInt(~intValue,type==Type.UINT);
        }
        public Value invert(){
            return ofFloat(1.0/intValue);
        }

        @Override
        public String stringValue() {
            return type==Type.UINT?Long.toUnsignedString(intValue):Long.toString(intValue);
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
            c-=(int)parseInt(str.substring(e),base,false);
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
        @Override
        Object rawData() {
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
                return ofInt((long)floatValue,false);
            }else if(type==Type.UINT){
                return ofInt(floatValue<0 ? 0 :
                            floatValue>=18446744073709551615.0 ? -1 :
                                    ((long)floatValue/2)<<1,true);
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
        public Value castTo(Type type) throws ConcatRuntimeError {
            if((typeValue instanceof Type.Enum||typeValue instanceof Type.Tuple)&&type==Type.RAW_STRING()){
                return ofString(typeValue.name,false);
            }
            return super.castTo(type);
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
            if(typeValue instanceof Type.Enum){
                return ((Type.Enum) typeValue).elementCount();
            }else{
                return super.length();
            }
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(typeValue instanceof Type.Enum){
                if(index<0||index>=((Type.Enum) typeValue).elementCount()){
                    throw new ConcatRuntimeError("index out of bounds:"+index+" size:"
                            +((Type.Enum) typeValue).elementCount());
                }
                return ofString(((Type.Enum) typeValue).get((int)index),false);
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
        return new CodepointValue(codePoint);
    }
    private static class CodepointValue extends Value{
        final int codePoint;
        private CodepointValue(int codePoint) {
            super(Type.CODEPOINT);
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
        Object rawData() {
            return codePoint;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.BYTE){
                return ofByte((byte)codePoint);
            }else if(type==Type.INT){
                return ofInt(codePoint,false);
            }else if(type==Type.UINT){
                return ofInt(codePoint,true);
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
            CodepointValue aChar = (CodepointValue) o;
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
        public long asLong(){
            return byteValue&0xff;
        }

        @Override
        Object rawData() {
            return byteValue;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                return ofInt(byteValue&0xff,false);
            }else if(type==Type.UINT){
                return ofInt(byteValue&0xff,true);
            }else if(type==Type.CODEPOINT){
                return ofChar(byteValue);
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
            return ""+(char)byteValue;
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

    public static Value ofString(String stringValue,boolean unicodeMode) {
        if(unicodeMode){
            return new ListValue(Type.UNICODE_STRING(),stringValue.codePoints().mapToObj(Value::ofChar)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }else{
            byte[] bytes = stringValue.getBytes(StandardCharsets.UTF_8);
            return new ByteListImpl(bytes.length,bytes);
        }
    }
    public static Value createList(Type listType, ArrayList<Value> elements) throws ConcatRuntimeError {
        if(!listType.isList()){
            throw new IllegalArgumentException(listType+" is no valid list-type");
        }
        if(listType==Type.RAW_STRING()){
            byte[] bytes=new byte[elements.size()];
            for(int i=0;i<elements.size();i++){
                bytes[i]=elements.get(i).asByte();
            }
            return new ByteListImpl(bytes.length,bytes);
        }else{
            return new ListValue(listType,elements);
        }
    }
    public static Value createList(Type type, long initCap) throws ConcatRuntimeError {
        if(initCap<0){
            throw new ConcatRuntimeError("initial capacity has to be at least 0");
        }else if(initCap>Integer.MAX_VALUE){
            throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
        }
        if(type==Type.RAW_STRING()){
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
        public void clear() {
            elements.clear();
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
            if(type==Type.UNTYPED_LIST||this.type.equals(type)){
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
            if(Type.CODEPOINT.equals(type.content())){
                StringBuilder str=new StringBuilder();
                for(Value v:elements){
                    str.append(Character.toChars(((CodepointValue)v).getChar()));
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
        public void clear() throws ConcatRuntimeError {
            if(to > list.elements.size()){
                throw new ConcatRuntimeError("Index out of bounds:"+to+" length:"+length());
            }
            list.elements.subList(off,to).clear();
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
            if(type==Type.UNTYPED_LIST||this.type.equals(type)){
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
            if(Type.CODEPOINT.equals(type.content())){
                StringBuilder str=new StringBuilder();
                for(Value v: getElements()){
                    str.append(Character.toChars(((CodepointValue)v).getChar()));
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
            super(Type.RAW_STRING());
        }
        public abstract int length();
        @Override
        public ByteList toByteList(){
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
            return new String(bytes,StandardCharsets.UTF_8);
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value asValue)|| asValue.notList()) return false;
            try {
                return Objects.equals(getElements(), asValue.getElements());
            } catch (TypeError e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public final int hashCode() {
            return getElements().hashCode();
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
        //rawData: bytes,off,len,size
        @Override
        Object rawData() {
            return new Object[]{elements,0,size,size};
        }
        @Override
        void updateFrom(Object nativeValue) throws ConcatRuntimeError {
            try {
                this.elements = (byte[]) ((Object[])nativeValue)[0];
                this.size     = (int) ((Object[])nativeValue)[3];
            }catch (ClassCastException cce){
                throw new ConcatRuntimeError(cce.toString());
            }
        }

        @Override
        public void clear(){
            size=0;
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
            if(type==Type.UNTYPED_LIST||this.type.equals(type)){
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
        //rawData: bytes,off,len,size
        @Override
        Object rawData() {
            return new Object[]{list.elements,off,to-off,list.size};
        }
        @Override
        void updateFrom(Object nativeArg) throws ConcatRuntimeError {
            list.updateFrom(nativeArg);
            try{
                if(off==(int)((Object[])nativeArg)[1]){
                    throw new ConcatRuntimeError("native procedure modified immutable value off");
                }
                to=off+(int)((Object[])nativeArg)[2];
            }catch (ClassCastException cce){
                throw new ConcatRuntimeError(cce.toString());
            }
        }

        @Override
        public void clear() throws ConcatRuntimeError {
            if(to>list.size){
                throw new ConcatRuntimeError("index out of bounds:"+to+" length:"+length());
            }
            System.arraycopy(list.elements,to,list.elements,off,list.size-to);
            list.size-=to-off;
            to=off;
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
            if(type==Type.UNTYPED_LIST||this.type.equals(type)){
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
    }

    public static Value createTuple(Type.Tuple type, Value[] elements) throws ConcatRuntimeError {
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

    public static Procedure createProcedure(Type procType, ArrayList<Interpreter.Token> tokens,FilePosition declaredAt,
                                            Interpreter.ProcedureContext variableContext) {
        if(procType instanceof Type.Procedure||procType==Type.UNTYPED_PROCEDURE){
            return new Procedure(procType, tokens,  declaredAt, variableContext);
        }else{
            throw new IllegalArgumentException(procType+" is no valid procedure Type");
        }
    }
    static class Procedure extends Value implements Interpreter.CodeSection, Interpreter.Declareable {
        final FilePosition declaredAt;

        final Interpreter.ProcedureContext context;
        ArrayList<Interpreter.Token> tokens;

        private Procedure(Type procType, ArrayList<Interpreter.Token> tokens,  FilePosition declaredAt,
                          Interpreter.ProcedureContext context) {
            super(procType);
            this.declaredAt = declaredAt;
            this.tokens=tokens;
            this.context=context;
        }

        @Override
        public long id() {
            return declaredAt.hashCode();
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(this.type==Type.UNTYPED_PROCEDURE &&(type instanceof Type.Procedure)){
                //addLater type-check body
                return new Procedure(type, tokens, declaredAt, context);
            }
            return super.castTo(type);
        }

        @Override
        public String stringValue() {
            return "@("+ declaredAt +")";
        }

        CurriedProcedure withCurried(Value[] curried){
            return new CurriedProcedure(this,curried);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Procedure that = (Procedure) o;
            return declaredAt.equals(that.declaredAt);
        }
        @Override
        public int hashCode() {
            return Objects.hash(declaredAt);
        }

        @Override
        public ArrayList<Interpreter.Token> tokens() {
            return tokens;
        }

        @Override
        public Interpreter.VariableContext context() {
            return context;
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.PROCEDURE;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
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

    public static Value wrap(Value v) throws ConcatRuntimeError {
        return new OptionalValue(v);
    }
    public static Value emptyOptional(Type t) throws ConcatRuntimeError {
        return new OptionalValue(t);
    }
    static class OptionalValue extends Value{
        final Value wrapped;
        private OptionalValue(Value wrapped) throws ConcatRuntimeError {
            super(Type.optionalOf(wrapped.type));
            this.wrapped = wrapped;
        }
        private OptionalValue(Type t) throws ConcatRuntimeError {
            super(Type.optionalOf(t));
            this.wrapped = null;
        }
        @Override
        Object rawData() throws TypeError {
            Value.jClass(type.content());//check type
            return wrapped==null?Optional.empty():Optional.of(wrapped.rawData());
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(this.type.isSubtype(type)){
                return this;
            }else if(type.isOptional()){
                if(wrapped==null){
                    if(this.type.canCastTo(type)){
                        return new OptionalValue(type.content());
                    }
                }else{
                    return new OptionalValue(wrapped.castTo(type.content()));
                }
            }
            return super.castTo(type);
        }

        @Override
        public boolean hasValue() {
            return wrapped!=null;
        }
        @Override
        public Value unwrap() throws ConcatRuntimeError {
            if(wrapped==null){
                throw new ConcatRuntimeError("cannot unwrap empty optionals");
            }
            return wrapped;
        }

        @Override
        public Value clone(boolean deep) {
            if(wrapped!=null&&deep){
                try {
                    return new OptionalValue(wrapped.clone(true));
                } catch (ConcatRuntimeError e) {
                    throw new RuntimeException(e);
                }
            }else{
                return this;
            }
        }

        @Override
        public String stringValue() {
            if(wrapped!=null){
                return wrapped+" wrap";
            }else{
                return type.content()+" empty";
            }
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OptionalValue that = (OptionalValue) o;
            return Objects.equals(wrapped, that.wrapped);
        }
        @Override
        public int hashCode() {
            return Objects.hash(wrapped);
        }
    }
    static class EnumEntry extends Value implements Interpreter.Declareable {
        final FilePosition declaredAt;
        final int index;
        EnumEntry(Type.Enum type, int index, FilePosition declaredAt) {
            super(type);
            this.index = index;
            this.declaredAt = declaredAt;
        }

        @Override
        public long id() {
            return index;
        }
        @Override
        public long asLong(){
            return index;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                return ofInt(index,false);
            }else if(type==Type.UINT){
                return ofInt(index,true);
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            return ((Type.Enum)type).entryNames[index];
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.ENUM_ENTRY;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnumEntry enumEntry = (EnumEntry) o;
            return index == enumEntry.index && Objects.equals(declaredAt, enumEntry.declaredAt);
        }
        @Override
        public int hashCode() {
            return Objects.hash(declaredAt, index);
        }
    }

    static final HashMap<String,URLClassLoader> classLoaders=new HashMap<>();
    static URLClassLoader getLoader(String path) throws MalformedURLException {
        File file = new File(path+"native.jar");
        if(classLoaders.containsKey(file.getAbsolutePath())){
            return classLoaders.get(file.getAbsolutePath());
        }else {
            URLClassLoader loader = new URLClassLoader(new URL[]{file.toURI().toURL()});
            classLoaders.put(file.getAbsolutePath(),loader);
            return loader;
        }
    }

    private static Value fromJValue(Type type,Object jValue) throws ConcatRuntimeError {
        try{
            if(jValue==null){
                if(type.isOptional()){
                    return emptyOptional(type);
                }else{
                    throw new ConcatRuntimeError("null is not a valid native value of type "+type);
                }
            }else if(type==Type.BOOL){
                return ((Boolean)jValue)?TRUE:FALSE;
            }else if(type==Type.BYTE){
                return ofByte((Byte)jValue);
            }else if(type == Type.CODEPOINT){
                return ofChar((Integer)jValue);
            }else if(type==Type.INT||type==Type.UINT){
                return ofInt((Long) jValue,type==Type.UINT);
            }else if(type==Type.FLOAT){
                return ofFloat((Double)jValue);
            }else if(type==Type.RAW_STRING()){
                Object[] parts=(Object[])jValue;
                byte[] bytes=(byte[])parts[0];
                int off  = (int)parts[1];
                int len  = (int)parts[2];
                int init = (int)parts[3];
                return new ByteListImpl(init,bytes).getSlice(off,off+len);
            }else if(type.isOptional()){
                Optional<?> o=(Optional<?>)jValue;
                if(o.isEmpty()){
                    return emptyOptional(type);
                }else{
                    return wrap(fromJValue(type.content(),o.get()));
                }
            }else if(type instanceof Type.NativeType){
                return new NativeValue((Type.NativeType)type,((Type.NativeType) type).jClass.cast(jValue));
            }else{
                throw new ConcatRuntimeError("type "+type+" is not supported for native values");
            }
        }catch (ClassCastException cce){
            assert jValue!=null;
            throw new ConcatRuntimeError(jValue.getClass()+":"+jValue+" is not a valid native value of type "+type);
        }
    }
    private static Class<?> jClass(Type t) throws TypeError {
        if(t == Type.BOOL){
            return boolean.class;
        }else if(t == Type.BYTE){
            return byte.class;
        }else if(t == Type.CODEPOINT){
            return int.class;
        }else if(t == Type.INT||t == Type.UINT){
            return long.class;
        }else if(t == Type.FLOAT){
            return double.class;
        }else if(t == Type.ANY){
            return Object.class;
        }else if(t==Type.RAW_STRING()){
            return Object[].class;//bytes,off,len,init
        }else if(t.isOptional()){
            jClass(t.content());//check content Type
            return Optional.class;
        }else if(t instanceof Type.NativeType){
            return ((Type.NativeType)t).jClass;
        }else{
            throw new TypeError("type "+t+" is not supported for native procedures");
        }
    }
    public static Value loadNativeConstant(Type type, String name, FilePosition pos) throws SyntaxError {
        try {
            String path=pos.path;
            String dir=path.substring(0,path.lastIndexOf('/')+1);
            String className=path.substring(path.lastIndexOf('/')+1);
            className=className.substring(0,className.lastIndexOf('.'));
            ClassLoader loader=getLoader(dir);
            Class<?> cls=loader.loadClass(className);//TODO ensure names are valid java-identifiers
            Object val=cls.getField("nativeImpl_"+name).get(null);
            if(type==Type.TYPE){
                return ofType(new Type.NativeType(name, (Class<?>) val));
            }else{
                return fromJValue(type,val);
            }
        } catch (MalformedURLException | ClassNotFoundException | NoSuchFieldException |
                IllegalAccessException|ConcatRuntimeError e) {
            throw new SyntaxError("Error while loading native value "+name+": "+e,pos);
        }
    }
    public static NativeProcedure createNativeProcedure(Type.Procedure procType, FilePosition declaredAt,
                                                  String name) throws SyntaxError {
        try {
            String path = declaredAt.path;
            String dir = path.substring(0, path.lastIndexOf('/') + 1);
            String className = path.substring(path.lastIndexOf('/') + 1);
            className = className.substring(0, className.lastIndexOf('.'));
            ClassLoader loader = getLoader(dir);
            Class<?> cls = loader.loadClass(className);
            Class<?> [] signature=new Class[procType.inTypes.length];
            for(int i=0;i<signature.length;i++){
                signature[i]=jClass(procType.inTypes[i]);
            }
            Method m=cls.getMethod("nativeImpl_"+name,signature);
            return new NativeProcedure(procType,m,name,declaredAt);
        } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | TypeError e) {
            throw new SyntaxError("Error while loading native procedure "+name+": "+e,declaredAt);
        }
    }
    static class NativeValue extends Value{
        final Object nativeValue;
        protected NativeValue(Type.NativeType type, Object nativeValue) {
            super(type);
            this.nativeValue = nativeValue;
        }

        @Override
        Object rawData(){
            return nativeValue;
        }

        @Override
        public String stringValue() {
            return "native value @"+System.identityHashCode(nativeValue);
        }
    }
    static class NativeProcedure extends Value implements Interpreter.NamedDeclareable {
        final Method nativeMethod;
        final String name;
        final FilePosition declaredAt;

        protected NativeProcedure(Type.Procedure type, Method nativeMethod, String name, FilePosition declaredAt) {
            super(type);
            this.nativeMethod = nativeMethod;
            this.name = name;
            this.declaredAt = declaredAt;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.NATIVE_PROC;
        }

        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }

        int argCount(){
            return ((Type.Procedure)type).inTypes.length;
        }
        Value[] callWith(Value[] values) throws ConcatRuntimeError {
            Object[] nativeArgs=new Object[values.length];
            for(int i=0;i<values.length;i++){
                nativeArgs[i]=values[i].castTo( ((Type.Procedure)type).inTypes[i]).rawData();
            }
            try {
                Object res=nativeMethod.invoke(null,nativeArgs);
                for(int i=0;i<values.length;i++){
                    values[i].updateFrom(nativeArgs[i]);
                }
                if(((Type.Procedure)type).outTypes.length==0){
                    return new Value[0];
                }else if(((Type.Procedure)type).outTypes.length==1){
                    return new Value[]{Value.fromJValue(((Type.Procedure)type).outTypes[0],res)};
                }else{
                    //addLater implement handling of multiple output arguments
                    throw new UnsupportedOperationException("native functions with multiple output arguments are not supported");
                }
            } catch (IllegalAccessException e) {
                throw new ConcatRuntimeError(e.toString());
            } catch (InvocationTargetException e) {
                throw new ConcatRuntimeError(e.getCause().toString());
            }
        }

        @Override
        public String stringValue() {
            return name+" native proc";
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
        }else if(a instanceof ByteValue &&b instanceof ByteValue){
            return cmpToValue(Integer.compare(((ByteValue) a).byteValue&0xff,((ByteValue) b).byteValue&0xff), opType);
        }else if(a instanceof CodepointValue &&b instanceof CodepointValue){
            return cmpToValue(Integer.compare(((CodepointValue) a).codePoint,((CodepointValue) b).codePoint), opType);
        }else if(a instanceof NumberValue&&b instanceof NumberValue){
            return mathOp(a,b,
                    (x,y)->cmpToValue(x.compareTo(y),opType),
                    (x,y)->cmpToValue(Long.compareUnsigned(x,y),opType),
                    (x,y)->cmpToValue(x.compareTo(y),opType));
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

    public static Value mathOp(Value a, Value b, BiFunction<Long,Long,Value> intOp,  BiFunction<Long,Long,Value> uintOp,
                               BiFunction<Double,Double,Value> floatOp) throws TypeError {
        if(a instanceof IntValue||a.type==Type.BYTE){//addLater? isInt/isUInt functions
            if(b instanceof IntValue||b.type==Type.BYTE){
                return (a.type==Type.UINT?uintOp:intOp).apply(a.asLong(), b.asLong());
            }else if(b instanceof FloatValue){
                return floatOp.apply(a.asDouble(), b.asDouble());
            }
        }else if(a instanceof FloatValue){
            if(b instanceof IntValue||b.type==Type.BYTE){
                return floatOp.apply(a.asDouble(),b.asDouble());
            }else if(b instanceof FloatValue){
                return floatOp.apply(a.asDouble(),b.asDouble());
            }
        }
        throw new TypeError("invalid parameters for arithmetic operation:"+a.type+" "+b.type);
    }
    public static Value logicOp(Value a, Value b, BinaryOperator<Boolean> boolOp,BinaryOperator<Long> intOp) throws TypeError {
        if(a.type==Type.BOOL&&b.type==Type.BOOL){
            return boolOp.apply(a.asBool(),b.asBool())?TRUE:FALSE;
        }else{
            if((a instanceof IntValue||a.type==Type.BYTE)&&(b instanceof IntValue||b.type==Type.BYTE)){
                return ofInt(intOp.apply(a.asLong(), b.asLong()),a.type==Type.UINT);
            }
            throw new TypeError("invalid parameters for int operator:"+a.type+" "+b.type);
        }
    }

    private static long signedLeftShift(long x, long y) {
        return x&0x8000000000000000L|(x<<y&0x7fffffffffffffffL);
    }
    public static Value shift(Value a,Value b,boolean left) throws TypeError {
        if(a instanceof IntValue&&b instanceof IntValue){
            long x=((IntValue) a).intValue,y=((IntValue) b).intValue;
            if(y<0&&b.type==Type.INT){
                left=!left;//negative b => flip direction
                y=-y;
            }
            if(left){
                if(a.type==Type.UINT){
                    return ofInt(x<<y,true);
                }else{
                    return ofInt(signedLeftShift(x,y),false);
                }
            }else{
                if(a.type==Type.UINT){
                    return ofInt(x>>>y,true);
                }else{
                    return ofInt(x>>y,false);
                }
            }
        }
        throw new TypeError("invalid parameters for int operator:"+a.type+" "+b.type);
    }
}
