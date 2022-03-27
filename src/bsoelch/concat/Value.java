package bsoelch.concat;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
        if(this.type.canAssignTo(type)){
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
        return type.isRawString()||type.isUnicodeString();
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
        }else if(source.startsWith("+")){
            i++;
        }
        long res=0;
        long max=unsigned?Long.divideUnsigned(-1,base):(Long.MAX_VALUE/base+(sgn?1:0));
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
    //maximum powers such that base^-pow > 0
    static final int[] maxSafePowers = {0,0, 1074, 678, 537, 462, 415, 382, 358, 339, 323, 310, 299, 290, 282, 275, 268, 262, 257, 253,
            248, 244, 241, 237, 234, 231, 228, 226, 223, 221, 219, 216, 214, 213, 211, 209, 207, 206, 204, 203, 201, 200,
            199, 198, 196, 195, 194, 193, 192, 191, 190, 189, 188, 187, 186, 185, 185, 184, 183, 182, 181, 181, 180, 179, 179};
    public static double parseFloat(String str, int base) throws ConcatRuntimeError {
        if(base<2||base>62){
            throw new ConcatRuntimeError("base out of range:"+base+" base has to be between 2 and 62");
        }
        int i=0;
        boolean sgn=str.startsWith("-");
        if(sgn){
            i++;
        }else if(str.startsWith("+")){
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
        //pow may underflow -> calculate power in steps to ensure that it will not underflow
        double res=(sgn?-val:val);
        if(c> maxSafePowers[base]){
            res*=Math.pow(base,-maxSafePowers[base]);
            c-= maxSafePowers[base];
        }
        return res*Math.pow(base,-c);
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
        public long asLong() {
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
        if(!(listType.isList()||listType.isArray()||listType.isMemory())){
            throw new IllegalArgumentException(listType+" is no valid list-type");
        }
        if(listType.isRawString()){
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
        if(type.isRawString()){
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
        /**value is assumed to have the correct type*/
        @Override
        public void set(long index,Value value) throws ConcatRuntimeError {
            if(index<0||index>=elements.size()){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+elements.size());
            }
            elements.set((int)index,value);
        }
        @Override
        public Value getSlice(long off, long to) throws ConcatRuntimeError {
            if(off<0||to>elements.size()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+elements.size());
            }
            return new ListSlice(this,(int)off,(int)to);
        }

        /**value is assumed to have the correct type*/
        @Override
        public void setSlice(long off, long to, Value value) throws ConcatRuntimeError {
            if(off<0||to>elements.size()||off>to){
                throw new ConcatRuntimeError("invalid slice: "+off+":"+to+" length:"+elements.size());
            }
            List<Value> sublist=elements.subList((int)off,(int)to);
            List<Value> add=value.getElements().stream().toList();
            sublist.clear();
            sublist.addAll(add);
        }

        /**val is assumed to have the correct type*/
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

        /**value is assumed to have the correct type*/
        @Override
        public void push(Value value, boolean start){
            if(start){
                elements.add(0,value);
            }else{
                elements.add(value);
            }
        }
        /**value is assumed to have the correct type*/
        @Override
        public void pushAll(Value value, boolean start) throws ConcatRuntimeError {
            try{
                List<Value> push=value.getElements().stream().toList();
                if(start){
                    elements.addAll(0,push);
                }else{
                    elements.addAll(push);
                }
            }catch (WrappedConcatError e){
                throw e.wrapped;
            }
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(this.type.canAssignTo(type)){
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
            if(isString()){
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
        public void push(Value value, boolean start) {
            if(start){
                list.elements.add(off,value);
            }else{
                list.elements.add(to,value);
            }
            to++;
        }
        @Override
        public void pushAll(Value value, boolean start) throws ConcatRuntimeError {
            if(value.type.isList()){
                try{
                    List<Value> push=value.getElements().stream().toList();
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
            if(this.type.canAssignTo(type)){
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
            if(this.type.canAssignTo(type)){
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
            if(this.type.canAssignTo(type)){
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


    public static Value createMemory(Type type,long initCap) throws ConcatRuntimeError {
        if(initCap<0){
            throw new ConcatRuntimeError("initial capacity has to be at least 0");
        }else if(initCap>Integer.MAX_VALUE){
            throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
        }
        return new ArrayValue(type,(int)initCap);
    }
    public static Value createArray(Type type,Value content,long initCap) throws ConcatRuntimeError {
        if(initCap<0){
            throw new ConcatRuntimeError("initial capacity has to be at least 0");
        }else if(initCap>Integer.MAX_VALUE){
            throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
        }
        return new ArrayValue(type,content,(int)initCap);
    }
    //addLater ByteArray,  other primitive arrays?
    interface ArrayLike{
        Value[] elements();
        int length();
        int capacity();//addLater rename capacity (capacity does not really return the capacity)
        int offset();
        Value get(long index) throws ConcatRuntimeError ;
        void set(long index,Value value) throws ConcatRuntimeError ;
        void append(Value val) throws ConcatRuntimeError;
        void prepend(Value val) throws ConcatRuntimeError;
        void copyFrom(long offset,Value[] src,long srcOff,long length) throws ConcatRuntimeError;
        void insertAll(long offset,Value[] src,long srcOff,long length) throws ConcatRuntimeError;
        void fill(Value val,long offset,long count) throws ConcatRuntimeError;
        void reallocate(long newSize) throws ConcatRuntimeError;
        void setOffset(long newOffset) throws ConcatRuntimeError;
    }
    private static class ArrayValue extends Value implements ArrayLike{
        Value[] data;
        int offset;
        int length;

        /**create and empty array with the given capacity*/
        protected ArrayValue(Type type,int capacity) {
            super(type);
            data=new Value[capacity];
            offset=0;
            length=0;
        }
        /**creates an array with the given lengths and fills it with the given initial value*/
        protected ArrayValue(Type type,Value content,int capacity) {
            super(type);
            data=new Value[capacity];
            Arrays.fill(data,content);
            offset=0;
            length=capacity;
        }

        @Override
        public Value[] elements(){
            return Arrays.copyOfRange(data,offset,offset+length);
        }

        @Override
        public int length(){
            return length;
        }

        @Override
        public int capacity() {
            if(!type.isMemory()){
                throw new RuntimeException("capacity is only supported for memories");
            }
            return data.length-offset;
        }
        @Override
        public int offset() {
            if(!type.isMemory()){
                throw new RuntimeException("offset is only supported for memories");
            }
            return offset;
        }

        @Override
        public Value get(long index) throws ConcatRuntimeError {
            if(index<0||index>= length){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length);
            }
            return data[offset+(int)index];
        }
        @Override
        public void set(long index,Value val) throws ConcatRuntimeError {
            if(index<0||index>= length){
                throw new ConcatRuntimeError("Index out of bounds:"+index+" length:"+length);
            }
            data[offset+(int)index]=val;
        }
        @Override
        public void append(Value val) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("append is only supported for memories");
            }
            if(offset+length>=data.length){
                throw new ConcatRuntimeError("cannot append value, array reached upper boundary of memory");
            }
            data[offset+(length++)]=val;
        }

        @Override
        public void prepend(Value val) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("prepend is only supported for memories");
            }
            if(offset==0){
                throw new ConcatRuntimeError("cannot prepend value, array reached lower boundary of memory");
            }
            data[--offset]=val;
            length++;
        }

        @Override
        public void copyFrom(long offset,Value[] src, long srcOff,long count) throws ConcatRuntimeError {
            if(srcOff<0||srcOff+count>src.length){
                throw new ConcatRuntimeError("invalid source offset for copy: "+srcOff+" offset has to be between "+0
                        +" and "+(src.length-count));
            }
            if(type.isMemory()){
                if(offset<-this.offset||offset+count>data.length){
                    throw new ConcatRuntimeError("invalid offset for copy: "+offset+" offset has to be between "+(-this.offset)
                            +" and "+(data.length-count)+" to fit the array into the allocated region");
                }//no else
                if(length>0){//ensure there are no gaps in initialized memory
                    if(offset+count<0||offset>length){
                        throw new ConcatRuntimeError("invalid offset for copy: "+offset+" offset has to be between "+(-count)
                                +" and "+length+" to ensure a continuous section of initialized memory");
                    }
                }
            }else{
                if(offset<0||offset+count>length){
                    throw new ConcatRuntimeError("invalid offset for copy: "+offset+" offset has to be between "+0
                            +" and "+(length-count));
                }
            }
            System.arraycopy(src,(int)srcOff,data,this.offset+(int)offset,(int)count);
            if(type.isMemory()){
                int prevOffset = this.offset;
                this.offset=Math.min(prevOffset,this.offset+(int)offset);
                this.length=Math.max(prevOffset+length,this.offset+(int)(offset+count))-this.offset;
            }
        }
        /*implement insert natively to allow leaving the memory section that will be overwritten by the inserted values uninitialized*/
        /**inserts all elements in src into data (*/
        @Override
        public void insertAll(long index,Value[] src, long srcOff,long count) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("prepend is only supported for memories");
            }
            if(srcOff<0||srcOff+count>src.length){
                throw new ConcatRuntimeError("invalid source offset for insert: "+srcOff+" offset has to be between "+0
                        +" and "+(src.length-count));
            }
            if(index<0||index>length){
                throw new ConcatRuntimeError("invalid index for insert: "+index+" index has to be between "+0
                        +" and "+length);
            }//no else
            if(offset+length+count>data.length&&count>offset){
                throw new ConcatRuntimeError("invalid array length: "+count+
                        " does not fit into available space: "+(data.length-(offset+length)));
            }
            if(index<length/2){
                if(offset>=count){
                    System.arraycopy(data,offset,data,offset-(int)count,(int)index);
                    offset-=count;
                    System.arraycopy(src,(int)srcOff,data,this.offset+(int)index,(int)count);
                }else{
                    System.arraycopy(data,offset+(int)index,data,offset+(int)(index+count),length-(int)index);
                    System.arraycopy(src,(int)srcOff,data,this.offset+(int)index,(int)count);
                }
            }else{
                if(offset+length+count<=data.length){
                    System.arraycopy(data,offset+(int)index,data,offset+(int)(index+count),length-(int)index);
                    System.arraycopy(src,(int)srcOff,data,this.offset+(int)index,(int)count);
                }else{
                    System.arraycopy(data,offset,data,offset-(int)count,(int)index);
                    offset-=count;
                    System.arraycopy(src,(int)srcOff,data,this.offset+(int)index,(int)count);
                }
            }
            this.length+=count;
        }

        @Override
        public void fill(Value val, long offset, long count) throws ConcatRuntimeError {
            if(type.isMemory()){
                if(offset<-this.offset||offset+count>data.length){
                    throw new ConcatRuntimeError("invalid offset for copy: "+offset+" offset has to be between "+(-this.offset)
                            +" and "+(data.length-count)+" to fit the array into the allocated region");
                }//no else
                if(length>0){//ensure there are no gaps in initialized memory
                    if(offset+count<0||offset>length){
                        throw new ConcatRuntimeError("invalid offset for copy: "+offset+" offset has to be between "+(-count)
                                +" and "+length+" to ensure a continuous section of initialized memory");
                    }
                }
            }else{
                if(offset<0||offset+count>length){
                    throw new ConcatRuntimeError("invalid offset for copy: "+offset+" offset has to be between "+0
                            +" and "+(length-count));
                }
            }
            int fromIndex = this.offset + (int) offset;
            Arrays.fill(data, fromIndex,fromIndex+(int)count,val);
            if(type.isMemory()){
                int prevOffset = this.offset;
                this.offset=Math.min(prevOffset,this.offset+(int)offset);
                this.length=Math.max(prevOffset+this.length,this.offset+(int)(offset+count))-this.offset;
            }
        }

        @Override
        public void reallocate(long newSize) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("reallocate is only supported for memories");
            }
            if(newSize<offset+length||newSize>Integer.MAX_VALUE){
                throw new ConcatRuntimeError("newSize "+newSize+" outside allowed range: "+
                        (offset+length)+" to "+Integer.MAX_VALUE);
            }
            Value[] newData=new Value[(int)newSize];
            System.arraycopy(data,offset,newData,offset,length);
            data=newData;
        }
        @Override
        public void setOffset(long newOffset) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("move is only supported for memories");
            }
            if(newOffset<0||newOffset+length> data.length){
                throw new ConcatRuntimeError("offset "+newOffset+" outside allowed range: 0 to "+(data.length-length));
            }
            System.arraycopy(data,offset,data,(int)newOffset,length);
            offset=(int)newOffset;
        }

        @Override
        public String stringValue() {
            return Arrays.toString(elements());
        }
    }

    public static Value createTuple(Type.Tuple type, Value[] elements) throws ConcatRuntimeError {
        if(elements.length!=type.elementCount()){
            throw new IllegalArgumentException("elements has to have the same length as types");
        }
        //assume that elements have correct types
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
            elements[(int)index]=value;//it is assumed that value has the correct type
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

    public static Procedure createProcedure(String name,boolean isPublic,Type.Procedure procType, ArrayList<Interpreter.Token> tokens, FilePosition declaredAt,
                                            FilePosition endPos, Interpreter.ProcedureContext variableContext) {
        return new Procedure(name, isPublic, procType, tokens, null,
                new IdentityHashMap<>(), variableContext, declaredAt, endPos,TypeCheckState.UNCHECKED);
    }
    enum TypeCheckState{UNCHECKED,CHECKING,CHECKED}
    static class Procedure extends Value implements Interpreter.CodeSection, Interpreter.Callable {
        final String name;
        final boolean isPublic;
        final FilePosition declaredAt;
        //for position reporting in type-checker
        final FilePosition endPos;

        final Interpreter.ProcedureContext context;
        ArrayList<Interpreter.Token> tokens;//not final, to make two-step compilation easier
        TypeCheckState typeCheckState;

        final Value[] curriedArgs;
        final IdentityHashMap<Type.GenericParameter,Type> genericArgs;

        private Procedure(String name, boolean isPublic, Type procType, ArrayList<Interpreter.Token> tokens, Value[] curriedArgs,
                          IdentityHashMap<Type.GenericParameter, Type> genericArgs, Interpreter.ProcedureContext context,
                          FilePosition declaredAt, FilePosition endPos,TypeCheckState typeCheckState) {
            super(procType);
            this.name = name;
            this.isPublic = isPublic;
            this.curriedArgs = curriedArgs;
            this.genericArgs = genericArgs;
            this.declaredAt = declaredAt;
            this.tokens=tokens;
            this.context=context;
            this.endPos = endPos;
            this.typeCheckState =typeCheckState;
        }

        @Override
        public long id() {
            return declaredAt.hashCode();
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type instanceof Type.Procedure&&this.type.canCastTo(type)){
                return new Procedure(name, isPublic, type, tokens, curriedArgs, genericArgs,
                        context, declaredAt, endPos,typeCheckState);
            }
            return super.castTo(type);
        }

        @Override
        public String stringValue() {
            return "@("+ declaredAt +")";
        }

        Value.Procedure withCurried(Value[] curried){
            return new Procedure(name, isPublic, type, tokens, curried, genericArgs, context, declaredAt, endPos,typeCheckState);
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
            if(typeCheckState !=TypeCheckState.CHECKED){
                throw new RuntimeException("tokens() of Procedure should only be called after type checking");
            }
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
        @Override
        public String name() {
            return name;
        }
        @Override
        public boolean isPublic() {
            return isPublic;
        }
        @Override
        public Type.Procedure type() {
            return (Type.Procedure) type;
        }

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }

    public static Value wrap(Value v) throws ConcatRuntimeError {
        return new OptionalValue(v);
    }
    public static Value emptyOptional(Type t){
        return new OptionalValue(t);
    }
    static class OptionalValue extends Value{
        final Value wrapped;
        private OptionalValue(Value wrapped) throws ConcatRuntimeError {
            super(Type.optionalOf(wrapped.type));
            this.wrapped = wrapped;
        }
        private OptionalValue(Type t){
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
            if(this.type.canAssignTo(type)){
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
        public boolean asBool(){
            return hasValue();
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
    static class EnumEntry extends Value{
        final int index;
        EnumEntry(Type.Enum type, int index) {
            super(type);
            this.index = index;
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnumEntry enumEntry = (EnumEntry) o;
            return type.equals(enumEntry.type) && index == enumEntry.index;
        }
        @Override
        public int hashCode() {
            return Objects.hash(type,index);
        }
    }

    static abstract class NativeProcedure extends Value implements Interpreter.NamedDeclareable, Interpreter.Callable {
        final String name;
        final FilePosition declaredAt;
        protected NativeProcedure(Type.Procedure type, String name, FilePosition declaredAt) {
            super(type);
            this.name = name;
            this.declaredAt = declaredAt;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Type.Procedure type() {
            return (Type.Procedure) type;
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.NATIVE_PROC;
        }

        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }

        int argCount() {
            return ((Type.Procedure) type).inTypes.length;
        }

        abstract Value[] callWith(Value[] values) throws ConcatRuntimeError;

        @Override
        public abstract String stringValue();

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }
    public abstract static class InternalProcedure extends NativeProcedure{
        public static final FilePosition POSITION = new FilePosition("internal", 0, 0);

        protected InternalProcedure(Type[] inTypes, Type[] outTypes, String name) {
            super(Type.Procedure.create(inTypes, outTypes), name, POSITION);
        }
        protected InternalProcedure(Type.GenericParameter[] generics,Type[] inTypes, Type[] outTypes, String name) {
            super(Type.GenericProcedureType.create(generics,inTypes, outTypes), name, POSITION);
        }
        @Override
        abstract Value[] callWith(Value[] values) throws ConcatRuntimeError;

        @Override
        public String stringValue() {
            return name;
        }

        @Override
        public boolean isPublic() {
            return true;
        }
    }

    static ArrayList<InternalProcedure> internalProcedures(){
        ArrayList<InternalProcedure> procs=new ArrayList<>();
        procs.add(new InternalProcedure(new Type[]{Type.ANY},new Type[]{Type.UINT},"refId") {
            @Override
            Value[] callWith(Value[] values){
                return new Value[]{Value.ofInt(values[0].id(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.ANY,Type.ANY},new Type[]{Type.BOOL},"===") {
            @Override
            Value[] callWith(Value[] values){
                return new Value[]{values[0].isEqualTo(values[1])?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.ANY,Type.ANY},new Type[]{Type.BOOL},"=!=") {
            @Override
            Value[] callWith(Value[] values){
                return new Value[]{values[0].isEqualTo(values[1])?FALSE:TRUE};
            }
        });
        //addLater? implement equals in standard library
        procs.add(new InternalProcedure(new Type[]{Type.ANY,Type.ANY},new Type[]{Type.BOOL},"==") {
            @Override
            Value[] callWith(Value[] values){
                return new Value[]{values[0].equals(values[1])?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.ANY,Type.ANY},new Type[]{Type.BOOL},"!=") {
            @Override
            Value[] callWith(Value[] values){
                return new Value[]{values[0].equals(values[1])?FALSE:TRUE};
            }
        });
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a},new Type[]{a},"clone") {
                @Override
                Value[] callWith(Value[] values){
                    return new Value[]{values[0].clone(false)};
                }
            });
        }
        {//cloning an immutable lists creates a mutable copy
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.listOf(a)},
                    new Type[]{Type.mutableListOf(a)},"clone") {
                @Override
                Value[] callWith(Value[] values){
                    return new Value[]{values[0].clone(false)};
                }
            });
        }
        {//cloning an immutable copy of a mutable list
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.mutableListOf(a)},
                    new Type[]{Type.listOf(a)},"clone-mut~") {//addLater better name
                @Override
                Value[] callWith(Value[] values){
                    return new Value[]{values[0].clone(false)};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a},new Type[]{a},"clone!") {
                @Override
                Value[] callWith(Value[] values){//addLater? implement deep clone in standard library
                    return new Value[]{values[0].clone(true)};
                }
            });
        }

        Type unsigned = Type.UnionType.create(new Type[]{Type.BYTE,Type.CODEPOINT,Type.UINT});
        Type integer = Type.UnionType.create(new Type[]{unsigned,Type.INT});
        Type number  = Type.UnionType.create(new Type[]{integer,Type.FLOAT});

        procs.add(new InternalProcedure(new Type[]{Type.BOOL},new Type[]{Type.BOOL},"!") {
            @Override
            Value[] callWith(Value[] values) throws TypeError {
                return new Value[]{values[0].asBool()?FALSE:TRUE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT},new Type[]{Type.INT},"~") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(~values[0].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.UINT},new Type[]{Type.UINT},"~") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(~values[0].asLong(),true)};
            }
        });

        procs.add(new InternalProcedure(new Type[]{Type.BOOL,Type.BOOL},new Type[]{Type.BOOL},"&") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{values[0].asBool()&&values[1].asBool()?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.BOOL,Type.BOOL},new Type[]{Type.BOOL},"|") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{values[0].asBool()||values[1].asBool()?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.BOOL,Type.BOOL},new Type[]{Type.BOOL},"xor") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{values[0].asBool()^values[1].asBool()?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"&") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()&values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"&") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()&values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"|") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()|values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"|") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()|values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"xor") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()^values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"xor") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()^values[1].asLong(),false)};
            }
        });

        procs.add(new InternalProcedure(new Type[]{Type.UINT,integer},new Type[]{Type.UINT},"<<") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()<<values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"<<") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(Value.signedLeftShift(values[0].asLong(),values[1].asLong()),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.UINT,integer},new Type[]{Type.UINT},">>") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()>>>values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},">>") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()>>values[1].asLong(),false)};
            }
        });

        procs.add(new InternalProcedure(new Type[]{Type.INT},new Type[]{Type.INT},"-_") {
            @Override
            Value[] callWith(Value[] values) throws TypeError {
                return new Value[]{Value.ofInt(-(values[0].asLong()),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.FLOAT},new Type[]{Type.FLOAT},"-_") {
            @Override
            Value[] callWith(Value[] values) throws TypeError {
                return new Value[]{Value.ofFloat(-(values[0].asDouble()))};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.FLOAT},new Type[]{Type.FLOAT},"/_") {
            @Override
            Value[] callWith(Value[] values) throws TypeError {
                return new Value[]{Value.ofFloat(1.0/values[0].asDouble())};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"+") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()+values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"+") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()+values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"+") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofFloat(values[0].asDouble()+values[1].asDouble())};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.UINT,integer},new Type[]{Type.UINT},"-") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()-values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{integer,integer},new Type[]{Type.INT},"-") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()-values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"-") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofFloat(values[0].asDouble()-values[1].asDouble())};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"*") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()*values[1].asLong(),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"*") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()*values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"*") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofFloat(values[0].asDouble()*values[1].asDouble())};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"/") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(Long.divideUnsigned(values[0].asLong(),values[1].asLong()),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"/") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()/values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"/") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofFloat(values[0].asDouble()/values[1].asDouble())};
            }
        });
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT},"%") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(Long.remainderUnsigned(values[0].asLong(),values[1].asLong()),true)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.INT,integer},new Type[]{Type.INT},"%") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofInt(values[0].asLong()%values[1].asLong(),false)};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"%") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{ofFloat(values[0].asDouble()%values[1].asDouble())};
            }
        });

        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},">") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{compareNumbers(values[0],values[1])>0?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},">=") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{compareNumbers(values[0],values[1])>=0?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"<") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{compareNumbers(values[0],values[1])<0?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"<=") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{compareNumbers(values[0],values[1])<=0?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"==") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{compareNumbers(values[0],values[1])==0?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"!=") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{compareNumbers(values[0],values[1])!=0?TRUE:FALSE};
            }
        });

        procs.add(new InternalProcedure(new Type[]{Type.TYPE,Type.TYPE},new Type[]{Type.BOOL},"<=") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{values[0].asType().canAssignTo(values[1].asType())?TRUE:FALSE};
            }
        });
        procs.add(new InternalProcedure(new Type[]{Type.TYPE,Type.TYPE},new Type[]{Type.BOOL},">=") {
            @Override
            Value[] callWith(Value[] values) throws ConcatRuntimeError {
                return new Value[]{values[1].asType().canAssignTo(values[0].asType())?TRUE:FALSE};
            }
        });

        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.mutableListOf(a),Type.UINT},
                    new Type[]{},"ensureCap") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    values[0].ensureCap(values[1].asLong());
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},
                    new Type[]{Type.mutableListOf(a),Type.UINT,Type.UINT,a},new Type[]{},"fill") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list off count val
                    values[0].fill(values[3],values[1].asLong(),values[2].asLong());
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},
                    new Type[]{Type.mutableListOf(a)},new Type[]{},"clear") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    values[0].clear();
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type list = Type.mutableListOf(a);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{list,a},new Type[]{list},"<<") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list val
                    values[0].push(values[1],false);
                    return new Value[]{values[0]};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type list = Type.mutableListOf(a);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,list},new Type[]{list},">>") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val list
                    values[1].push(values[0],true);
                    return new Value[]{values[1]};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type mutList = Type.mutableListOf(a);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{mutList,Type.maybeMutableListOf(a)},
                    new Type[]{mutList},"<<*") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list val
                    values[0].pushAll(values[1],false);
                    return new Value[]{values[0]};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type mutList = Type.mutableListOf(a);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.maybeMutableListOf(a),mutList},
                    new Type[]{mutList},"*>>") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val list
                    values[1].pushAll(values[0],true);
                    return new Value[]{values[1]};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.maybeMutableListOf(a),Type.UINT},
                    new Type[]{a},"[]") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list index
                    return new Value[]{values[0].get(values[1].asLong())};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),Type.UINT},
                    new Type[]{a},"[]") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list index
                    return new Value[]{((ArrayLike)values[0]).get(values[1].asLong())};
                }
            });
        }
        {//untyped tuple element access
            procs.add(new InternalProcedure(new Type[]{Type.Tuple.EMPTY_TUPLE,Type.UINT},new Type[]{Type.ANY},"[]") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list index
                    return new Value[]{values[0].get(values[1].asLong())};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,Type.mutableListOf(a),Type.UINT},
                    new Type[]{},"[]=") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val list index
                    values[1].set(values[2].asLong(),values[0]);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,Type.arrayOf(a).mutable(),Type.UINT},
                    new Type[]{},"[]=") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val list index
                    ((ArrayLike)values[1]).set(values[2].asLong(),values[0]);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.listOf(a),Type.UINT,Type.UINT},
                    new Type[]{Type.listOf(a)},"[:]") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list off to
                    return new Value[]{values[0].getSlice(values[1].asLong(),values[2].asLong())};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.mutableListOf(a),Type.UINT,Type.UINT},
                    new Type[]{Type.mutableListOf(a)},"[:]") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list off to
                    return new Value[]{values[0].getSlice(values[1].asLong(),values[2].asLong())};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.maybeMutableListOf(a),Type.UINT,Type.UINT},
                    new Type[]{Type.maybeMutableListOf(a)},"[:]") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list off to
                    return new Value[]{values[0].getSlice(values[1].asLong(),values[2].asLong())};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.mutableListOf(a),
                    Type.maybeMutableListOf(a), Type.UINT,Type.UINT}, new Type[]{},"[:]=") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list val off to
                    values[1].setSlice(values[2].asLong(),values[3].asLong(),values[0]);
                    return new Value[0];
                }
            });
        }


        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a},new Type[]{Type.optionalOf(a)},"wrap") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{wrap(values[0])};
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.optionalOf(a)},new Type[]{Type.BOOL},"!") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{values[0].hasValue()?FALSE:TRUE};
                }
            });
        }

        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type list = Type.memoryOf(a).mutable();
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{list,a},new Type[]{},"[]^=") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //list val
                    ((ArrayLike)values[0]).append(values[1]);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type list = Type.memoryOf(a).mutable();
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,list},new Type[]{},"^[]=") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val list
                    ((ArrayLike)values[1]).prepend(values[0]);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),
                    Type.UINT,Type.memoryOf(a).mutable(), Type.INT,Type.UINT}, new Type[]{},"copy") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //src srcOff target targetOff count
                    ArrayLike src=(ArrayLike)values[0];
                    long srcOff=values[1].asLong();
                    ArrayLike target=(ArrayLike)values[2];
                    long off=values[3].asLong();
                    long count=values[4].asLong();
                    target.copyFrom(off,src.elements(),srcOff,count);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),
                    Type.UINT,Type.arrayOf(a).mutable(), Type.UINT,Type.UINT}, new Type[]{},"copy") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //src srcOff target targetOff count
                    ArrayLike src=(ArrayLike)values[0];
                    long srcOff=values[1].asLong();
                    ArrayLike target=(ArrayLike)values[2];
                    long off=values[3].asLong();
                    long count=values[4].asLong();
                    target.copyFrom(off,src.elements(),srcOff,count);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),
                    Type.memoryOf(a).mutable(), Type.INT}, new Type[]{},"copy_no-replace") {//addLater better name
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //src srcOff target targetOff count
                    ArrayLike src=(ArrayLike)values[0];
                    long srcOff=values[1].asLong();
                    ArrayLike target=(ArrayLike)values[2];
                    long off=values[3].asLong();
                    long count=values[4].asLong();
                    target.insertAll(off,src.elements(),srcOff,count);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,Type.memoryOf(a).mutable(),
                    Type.INT,Type.UINT}, new Type[]{},"fill") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val target off count
                    Value val=values[0];
                    ArrayLike target=(ArrayLike)values[1];
                    long off=values[2].asLong();
                    long count=values[3].asLong();
                    target.fill(val,off,count);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,Type.arrayOf(a).mutable(),
                    Type.INT,Type.UINT}, new Type[]{},"fill") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //val target off count
                    Value val=values[0];
                    ArrayLike target=(ArrayLike)values[1];
                    long off=values[2].asLong();
                    long count=values[3].asLong();
                    target.fill(val,off,count);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.memoryOf(a).mutable(),
                    Type.UINT}, new Type[]{},"realloc") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //memory newSize
                    ArrayLike mem=(ArrayLike)values[0];
                    long newSize=values[1].asLong();
                    mem.reallocate(newSize);
                    return new Value[0];
                }
            });
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.memoryOf(a).mutable(),
                    Type.INT}, new Type[]{},"setOffset") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    //memory newOffset
                    ArrayLike mem=(ArrayLike)values[0];
                    long newOffset=values[1].asLong();
                    mem.setOffset(newOffset);
                    return new Value[0];
                }
            });
        }

        return procs;
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
            }else if(type.isRawString()){
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
                return new ExternalValue((Type.NativeType)type,((Type.NativeType) type).jClass.cast(jValue));
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
        }else if(t.isRawString()){
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
    public static ExternalProcedure createExternalProcedure(String name, boolean isPublic, Type.Procedure procType, FilePosition declaredAt) throws SyntaxError {
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
            return new ExternalProcedure(name, isPublic, procType,m, declaredAt);
        } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | TypeError e) {
            throw new SyntaxError("Error while loading native procedure "+name+": "+e,declaredAt);
        }
    }
    static class ExternalValue extends Value{
        final Object nativeValue;
        protected ExternalValue(Type.NativeType type, Object nativeValue) {
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
    static class ExternalProcedure extends NativeProcedure {
        final boolean isPublic;
        final Method nativeMethod;
        private ExternalProcedure(String name, boolean isPublic, Type.Procedure type, Method nativeMethod, FilePosition declaredAt) {
            super(type, name, declaredAt);
            this.isPublic = isPublic;
            this.nativeMethod = nativeMethod;
        }
        @Override
        Value[] callWith(Value[] values) throws ConcatRuntimeError {
            Object[] nativeArgs=new Object[values.length];
            for(int i=0;i<values.length;i++){
                nativeArgs[i]=values[i].rawData();
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
            return name+(isPublic?" public ":"")+" native proc ";
        }

        @Override
        public boolean isPublic() {
            return isPublic;
        }
    }

    static int compareNumbers(Value n1,Value n2) throws ConcatRuntimeError{
        if(n1 instanceof ByteValue||n1 instanceof CodepointValue||n1 instanceof IntValue){
            if(n2 instanceof ByteValue||n2 instanceof CodepointValue||n2 instanceof IntValue){
                if(n1.type==Type.UINT){
                    if(n2.type==Type.UINT){
                        return Long.compareUnsigned(n1.asLong(),n2.asLong());
                    }else{
                        return n2.asLong()<0?1:Long.compareUnsigned(n1.asLong(),n2.asLong());
                    }
                }else{
                    if(n2.type==Type.UINT){
                        return n1.asLong()<0?-1:Long.compareUnsigned(n1.asLong(),n2.asLong());
                    }else{
                        return Long.compare(n1.asLong(),n2.asLong());
                    }
                }
            }else if(n2 instanceof FloatValue){
                return Double.compare(n1.asDouble(),n2.asDouble());
            }
        }else if(n1 instanceof FloatValue){
            if(n2 instanceof ByteValue||n2 instanceof CodepointValue||n2 instanceof IntValue||n2 instanceof FloatValue){
                return Double.compare(n1.asDouble(),n2.asDouble());
            }
        }
        throw new ConcatRuntimeError("cannot compare "+n1.type+" and "+n2.type);
    }

    private static long signedLeftShift(long x, long y) {
        return x&0x8000000000000000L|(x<<y&0x7fffffffffffffffL);
    }
}
