package bsoelch.concat;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        Object rawData(Type argType) {
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
        Object rawData(Type argType) {
            return true;
        }
    };
    public static Value ofBool(boolean bool){
        return bool?TRUE:FALSE;
    }

    final Type type;
    protected Value(Type type) {
        this.type = type;
    }


    public long id() {
        return System.identityHashCode(this);
    }

    /**type of this value, ignoring wrapping in traits*/
    public Type valueType() {
        return type;
    }

    /*raw data of this Value as a standard java Object*/
    Object rawData(Type argType) throws TypeError {
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
    /**checks if this and v are the same object (reference)
     * unlike equals this method distinguishes mutable objects with different ids but the same elements*/
    boolean isEqualTo(Value v){
        return equals(v);
    }

    public int length() throws TypeError {
        throw new TypeError(type+" does not have a length");
    }
    public Value[] getElements() throws TypeError {
        throw new TypeError("getElements is not supported for type "+type);
    }
    public Value getField(long index) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void set(long index,Value value) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public Value unwrap() throws ConcatRuntimeError {
        throw new TypeError("Cannot unwrap values of type "+type);
    }
    public boolean hasValue() throws TypeError {
        throw new TypeError("Cannot unwrap values of type "+type);
    }

    public Value clone(boolean deep,Type targetType) {
        return this;
    }
    public Value castTo(Type newType) throws ConcatRuntimeError {
        if(type.canAssignTo(newType)||newType==Type.ANY){
            return this;
        }else if(newType instanceof Type.Trait&&type.hasTrait((Type.Trait) newType)){
            return new TraitValue((Type.Trait) newType,this);
        }else{
            throw new TypeError("cannot cast from "+type+" to "+newType);
        }
    }
    public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
        return this;
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
            super(unsigned?Type.UINT():Type.INT());
            this.intValue = intValue;
        }

        @Override
        public long id() {
            return intValue;
        }

        @Override
        public double asDouble() {
            return type==Type.UINT()?((intValue>>>1)*2.0):intValue;
        }
        @Override
        public long asLong() {
            return intValue;
        }
        @Override
        Object rawData(Type argType) {
            return intValue;
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(newType ==Type.INT()){
                if(this.type==Type.INT()){
                    return this;
                }else{
                    return ofInt(intValue,false);
                }
            }else if(newType ==Type.UINT()){
                if(this.type==Type.UINT()){
                    return this;
                }else{
                    return ofInt(intValue,true);
                }
            }else if(newType ==Type.BYTE()){
                return Value.ofByte((byte)intValue);
            }else if(newType ==Type.CODEPOINT()){
                return Value.ofChar((int)intValue);
            }else if(newType ==Type.FLOAT){
                return ofFloat(intValue);
            }else{
                return super.castTo(newType);
            }
        }

        @Override
        public String stringValue() {
            return type==Type.UINT()?Long.toUnsignedString(intValue):Long.toString(intValue);
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
        Object rawData(Type argType) {
            return floatValue;
        }

        @Override
        public String stringValue() {
            return Double.toString(floatValue);
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(newType ==Type.INT()){
                return ofInt((long)floatValue,false);
            }else if(newType ==Type.UINT()){
                return ofInt(floatValue<0 ? 0 :
                            floatValue>=18446744073709551615.0 ? -1 :
                                    ((long)(floatValue/2))<<1,true);
            }else{
                return super.castTo(newType);
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
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Type newType=typeValue.replaceGenerics(genericParams);
            if(newType!=typeValue){
                return new TypeValue(newType);
            }
            return this;
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
            super(Type.CODEPOINT());
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
        }@Override
        public double asDouble(){
            return codePoint;
        }


        @Override
        Object rawData(Type argType) {
            return codePoint;
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(newType ==Type.BYTE()){
                return ofByte((byte)codePoint);
            }else if(newType ==Type.INT()){
                return ofInt(codePoint,false);
            }else if(newType ==Type.UINT()){
                return ofInt(codePoint,true);
            }else if(newType ==Type.FLOAT){
                return ofFloat(codePoint);
            }else{
                return super.castTo(newType);
            }
        }

        @Override
        public String stringValue() {
            return String.valueOf(codePoint>=0&&codePoint<Character.MAX_CODE_POINT?
                Character.toChars(codePoint):new char[]{(char)-1});
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
            super(Type.BYTE());
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
            return byteValue;
        }

        @Override
        public double asDouble(){
            return byteValue;
        }

        @Override
        Object rawData(Type argType) {
            return byteValue;
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(newType ==Type.INT()){
                return ofInt(byteValue,false);
            }else if(newType ==Type.UINT()){
                return ofInt(byteValue,true);
            }else if(newType ==Type.CODEPOINT()){
                return ofChar(byteValue);
            }else if(newType ==Type.FLOAT){
                return ofFloat(byteValue);
            }else{
                return super.castTo(newType);
            }
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
            return createArray(Type.UNICODE_STRING(),stringValue.codePoints().mapToObj(Value::ofChar)
                    .toArray(Value[]::new));
        }else{
            byte[] bytes = stringValue.getBytes(StandardCharsets.UTF_8);
            Value[] wrapped=wrapBytes(bytes);
            return createArray(Type.RAW_STRING(),wrapped);
        }
    }

    private static Value[] wrapBytes(byte[] bytes) {
        Value[] wrapped=new Value[bytes.length];
        for(int i = 0; i< bytes.length; i++){
            wrapped[i]=ofByte(bytes[i]);
        }
        return wrapped;
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
    public static ArrayValue createArray(Type type,Value[] data){
        return new ArrayValue(type,data);
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
        void copyToSlice(long sliceStart,long sliceEnd,Value[] src, long srcOff, long length) throws ConcatRuntimeError;
        void fill(Value val,long offset,long count) throws ConcatRuntimeError;
        void clearSlice(long sliceStart,long sliceEnd) throws ConcatRuntimeError;
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
        /**create an array with a specific data as values,
         * it is assumed, that all elements of data are non-null*/
        protected ArrayValue(Type type,Value[] data) {
            this(type,data,0,data.length);
        }
        private ArrayValue(Type type,Value[] data,int offset,int length) {
            super(type);
            this.data=data;
            this.offset=offset;
            this.length=length;
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
        boolean isEqualTo(Value v) {
            return this==v;
        }

        @Override
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Type newType=type.replaceGenerics(genericParams);
            boolean changed=newType!=type;
            Value[] newData=new Value[data.length];
            for(int i=0;i<length;i++){
                newData[i+offset]=data[i+offset].replaceGenerics(genericParams);
                changed|=data[i+offset]!=newData[i+offset];
            }
            return changed?this:new ArrayValue(newType,newData,offset,length);
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if (this.type.canAssignTo(newType)) {
                return this;
            }else if((newType.isArray()|| newType.isMemory())&&(this.type.canCastTo(newType)!= Type.CastType.NONE)){
                Type newContent= newType.content();//addLater keep current capacity?
                Value[] newValues=new Value[length];
                for(int i=0;i<length;i++){
                    newValues[i]=data[offset+i].castTo(newContent);
                }
                return new ArrayValue(newType,newValues);
            }
            return super.castTo(newType);
        }
        @Override
        public Value clone(boolean deep,Type targetType) {
            if(targetType==null)
                targetType=type;
            Value[] newData=data.clone();
            if(deep){
                for(int i=offset;i<offset+length;i++){
                    newData[i]=data[i].clone(true,targetType.content());
                }
            }
            return new ArrayValue(targetType,newData,offset,length);
        }

        /*raw data of this Value as a standard java Object*/
        Object rawData(Type argType) throws TypeError {
            if(argType.isArray()&&argType.content()==Type.BYTE()){
                byte[] unpacked=new byte[length];
                for(int i=0;i<length;i++){
                    unpacked[i]=data[offset+i].asByte();
                }
                return unpacked;
            }else if(argType.isMemory()&&argType.content()==Type.BYTE()){
                byte[] unpacked=new byte[data.length];
                for(int i=0;i<length;i++){
                    unpacked[offset+i]=data[offset+i].asByte();
                }
                return new Object[]{unpacked,offset,length};
            }
            return super.rawData(argType);
        }
        @Override
        void updateFrom(Object nativeArg) throws ConcatRuntimeError {
            if(nativeArg instanceof byte[] unpacked && (type.isArray()||type.isMemory())&&type.content()==Type.BYTE()){
                for(int i=0;i<length;i++){
                    data[offset+i]=ofByte(unpacked[i]);
                }
            }else if(nativeArg instanceof Object[] nativeArgs && type.isMemory()&&type.content()==Type.BYTE()){
                byte[] unpacked=(byte[])nativeArgs[0];
                offset=(int)nativeArgs[1];
                length=(int)nativeArgs[2];
                for(int i=0;i<length;i++){
                    data[offset+i]=ofByte(unpacked[i]);
                }
            }else{
                super.updateFrom(nativeArg);
            }
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
        /**inserts all elements in src into data overwriting (only) the elements between
         * index and index+sliceLength */
        @Override
        public void copyToSlice(long sliceStart, long sliceEnd, Value[] src, long srcOff, long count) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("copyToSlice is only supported for memories");
            }
            if(sliceStart <0||sliceEnd< sliceStart ||sliceEnd>length){
                throw new ConcatRuntimeError("invalid target slice for copyToSlice: "+ sliceStart +":"+sliceEnd+" length:"+length);
            }//no else
            long sliceLength=sliceEnd- sliceStart;
            if(srcOff<0||srcOff+count-sliceLength>src.length){
                throw new ConcatRuntimeError("invalid source offset for copyToSlice: "+srcOff+" offset has to be between "+0
                        +" and "+(src.length-count+sliceLength));
            }//no else
            if(offset+length+count-sliceLength>data.length&&count-sliceLength>offset){
                throw new ConcatRuntimeError("invalid array length: "+count+
                        " does not fit into available space: "+Math.max(data.length+sliceLength-(offset+length),offset+sliceLength));
            }
            if(sliceStart < length-(int)sliceEnd){
                if(offset>=count){
                    System.arraycopy(data,offset,data,offset-(int)count+(int)sliceLength,(int) sliceStart);
                    offset+=sliceLength-count;
                }else{
                    System.arraycopy(data,offset+(int) sliceEnd,data,
                            offset+(int)(sliceStart +count),length-(int)sliceEnd);
                }
            }else{
                if(offset+length+count-sliceLength<=data.length){
                    System.arraycopy(data,offset+(int) sliceEnd,data,
                            offset+(int)(sliceStart +count),length-(int)sliceEnd);
                }else{
                    System.arraycopy(data,offset,data,offset-(int)count+(int)sliceLength,(int) sliceStart);
                    offset+=sliceLength-count;
                }
            }
            System.arraycopy(src,(int)srcOff,data,this.offset+(int) sliceStart,(int)count);
            this.length+=count-sliceLength;
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
        public void clearSlice(long sliceStart, long sliceEnd) throws ConcatRuntimeError {
            if(!type.isMemory()){
                throw new RuntimeException("clear is only supported for memories");
            }
            if(sliceStart <0||sliceEnd< sliceStart ||sliceEnd>length){
                throw new ConcatRuntimeError("invalid target slice for clear: "+ sliceStart +":"+sliceEnd+" length:"+length);
            }//no else
            long sliceLength=sliceEnd- sliceStart;
            if(sliceStart < length-(int)sliceEnd){
                System.arraycopy(data,offset,data,offset+(int)sliceLength,(int) sliceStart);
                offset+=sliceLength;
            }else{
                System.arraycopy(data,offset+(int) sliceEnd,data,offset+(int)sliceStart,
                        length-(int)sliceEnd);
            }
            this.length-=sliceLength;
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
            if(type.content()==Type.BYTE()){
                try {
                    return new String((byte[])rawData(Type.RAW_STRING()),StandardCharsets.UTF_8);
                } catch (TypeError e) {
                    throw new RuntimeException(e);
                }
            }else if(type.content()==Type.CODEPOINT()){
                StringBuilder str=new StringBuilder();
                for(int i=offset;i<offset+length;i++){
                    int aChar = ((CodepointValue) data[i]).getChar();
                    if(aChar>=0&&aChar<Character.MAX_CODE_POINT){
                        str.append(Character.toChars(aChar));
                    }else{
                        str.append((char)-1);
                    }
                }
                return str.toString();
            }
            return Arrays.toString(elements());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayValue that = (ArrayValue) o;
            return length == that.length && Arrays.equals(elements(), that.elements());
        }

        @Override
        public int hashCode() {
            int result = length;
            result = 31 * result + Arrays.hashCode(elements());
            return result;
        }
    }

    public static Value createTuple(Type.TupleLike type, Value[] elements) throws ConcatRuntimeError {
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

        private TupleValue(Type.TupleLike type,Value[] elements){
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
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Type.TupleLike newType=(Type.TupleLike)type.replaceGenerics(genericParams);
            boolean changed=newType!=type;
            Value[] newData=new Value[elements.length];
            for(int i=0;i<elements.length;i++){
                newData[i]=elements[i].replaceGenerics(genericParams);
                changed|=elements[i]!=newData[i];
            }
            return changed?this:new TupleValue(newType,newData);
        }

        @Override
        public Value clone(boolean deep,Type targetType) {
            if(targetType==null)
                targetType=type;
            Value[] newElements=elements.clone();
            if(deep){
                for(int i=0;i<elements.length;i++){
                    newElements[i]=elements[i].clone(true,((Type.TupleLike)targetType).getElement(i));
                }
            }
            return new TupleValue((Type.TupleLike) type,newElements);
        }

        @Override
        public int length() throws TypeError {
            return elements.length;
        }

        @Override
        public Value[] getElements() {
            return elements.clone();
        }

        @Override
        public Value getField(long index) throws ConcatRuntimeError {
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

    public static Procedure createProcedure(String name, boolean isPublic, Type.Procedure procType, ArrayList<Parser.Token> tokens, FilePosition declaredAt,
                                            FilePosition endPos, Parser.ProcedureContext variableContext) {
        return new Procedure(name, isPublic, procType, tokens, null,
                variableContext, declaredAt, endPos,TypeCheckState.UNCHECKED);
    }
    enum TypeCheckState{UNCHECKED,CHECKING,CHECKED}
    static class Procedure extends Value implements Parser.CodeSection, Parser.Callable {
        final String name;
        final boolean isPublic;
        final FilePosition declaredAt;
        //for position reporting in type-checker
        final FilePosition endPos;

        final Parser.ProcedureContext context;
        ArrayList<Parser.Token> tokens;//not final, to make two-step compilation easier
        TypeCheckState typeCheckState;

        final Value[] curriedArgs;

        private Procedure(String name, boolean isPublic, Type procType, ArrayList<Parser.Token> tokens, Value[] curriedArgs,
                          Parser.ProcedureContext context,
                          FilePosition declaredAt, FilePosition endPos, TypeCheckState typeCheckState) {
            super(procType);
            this.name = name;
            this.isPublic = isPublic;
            this.curriedArgs = curriedArgs;
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
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Type.Procedure newType= (Type.Procedure) type.replaceGenerics(genericParams);
            boolean changed=newType!=type;
            ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens);
            return changed?new Procedure(name,isPublic,newType,newTokens,curriedArgs,
                    context.replaceGenerics(genericParams),declaredAt,endPos,typeCheckState):this;
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(newType instanceof Type.Procedure&&(this.type.canCastTo(newType) != Type.CastType.NONE)){
                return new Procedure(name, isPublic, newType, tokens, curriedArgs,
                        context, declaredAt, endPos,typeCheckState);
            }
            return super.castTo(newType);
        }

        @Override
        public String stringValue() {
            return "@("+ declaredAt +")";
        }

        Value.Procedure withCurried(Value[] curried){
            return new Procedure(name, isPublic, type, tokens, curried,  context, declaredAt, endPos,typeCheckState);
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
        public ArrayList<Parser.Token> tokens() {
            if(typeCheckState !=TypeCheckState.CHECKED){
                throw new RuntimeException("tokens() of Procedure should only be called after type checking");
            }
            return tokens;
        }

        @Override
        public Parser.VariableContext context() {
            return context;
        }

        @Override
        public Parser.DeclareableType declarableType() {
            return Parser.DeclareableType.PROCEDURE;
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
        private OptionalValue(Value wrapped){
            super(Type.optionalOf(wrapped.type));
            this.wrapped = wrapped;
        }
        private OptionalValue(Type t){
            super(Type.optionalOf(t));
            this.wrapped = null;
        }
        @Override
        Object rawData(Type argType) throws TypeError {
            Value.jClass(type.content());//check type
            return wrapped==null?Optional.empty():Optional.of(wrapped.rawData(argType.content()));
        }

        @Override
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Value newWrap=wrapped.replaceGenerics(genericParams);
            if(newWrap!=wrapped)
                return new OptionalValue(newWrap);
            return this;
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(this.type.canAssignTo(newType)){
                return this;
            }else if(newType.isOptional()){
                if(wrapped==null){
                    if(this.type.canCastTo(newType) != Type.CastType.NONE){
                        return new OptionalValue(newType.content());
                    }
                }else{
                    return new OptionalValue(wrapped.castTo(newType.content()));
                }
            }
            return super.castTo(newType);
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
        public Value clone(boolean deep,Type targetType) {
            if(targetType==null)
                targetType=type;
            if(wrapped!=null&&deep){
                return new OptionalValue(wrapped.clone(true,targetType.content()));
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
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(newType ==Type.INT()){
                return ofInt(index,false);
            }else if(newType ==Type.UINT()){
                return ofInt(index,true);
            }else{
                return super.castTo(newType);
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

    static abstract class NativeProcedure extends Value implements Parser.NamedDeclareable, Parser.Callable {
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
        public Parser.DeclareableType declarableType() {
            return Parser.DeclareableType.NATIVE_PROC;
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
    public static final class InternalProcedure extends NativeProcedure{
        public static final FilePosition POSITION = new FilePosition("internal","internal", 0, 0);

        final ThrowingFunction<Value[],Value[],ConcatRuntimeError> action;
        final boolean compileTime;

        InternalProcedure(Type[] inTypes, Type[] outTypes, String name,
                          ThrowingFunction<Value[], Value[],ConcatRuntimeError> action,boolean compileTime) {
            this(Type.Procedure.create(inTypes, outTypes,POSITION), name, action,compileTime);
        }
        InternalProcedure(Type.GenericParameter[] generics, Type[] inTypes, Type[] outTypes, String name,
                          ThrowingFunction<Value[], Value[],ConcatRuntimeError> action,boolean compileTime) {
            this(Type.GenericProcedureType.create(generics,inTypes, outTypes,POSITION), name, action,compileTime);
        }
        private InternalProcedure(Type.Procedure type, String name, ThrowingFunction<Value[], Value[],ConcatRuntimeError> action,
                                  boolean compileTime) {
            super(type, name, POSITION);
            this.action = action;
            this.compileTime = compileTime;
        }

        @Override
        public boolean compileTime() {
            return compileTime;
        }

        @Override
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Type newType=type.replaceGenerics(genericParams);
            if(newType!=type){
                return new InternalProcedure((Type.Procedure) newType,name,action, false);
            }
            return this;
        }

        @Override
        Value[] callWith(Value[] values) throws ConcatRuntimeError {
            return action.apply(values);
        }

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
        procs.add(new InternalProcedure(new Type[]{Type.ANY},new Type[]{Type.UINT()},"refId",
                (values) -> new Value[]{Value.ofInt(values[0].id(),true)},false));
        procs.add(new InternalProcedure(new Type[]{Type.ANY,Type.ANY},new Type[]{Type.BOOL},"===",
                (values) -> new Value[]{values[0].isEqualTo(values[1])?TRUE:FALSE},false));
        procs.add(new InternalProcedure(new Type[]{Type.ANY,Type.ANY},new Type[]{Type.BOOL},"=!=",
                (values) -> new Value[]{values[0].isEqualTo(values[1])?FALSE:TRUE},false));
        //addLater? implement equals in standard library

        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type[]{a,a},new Type[]{Type.BOOL},"==",
                    (values) -> new Value[]{values[0].equals(values[1])?TRUE:FALSE},false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type[]{a,a},new Type[]{Type.BOOL},"!=",
                    (values) -> new Value[]{values[0].equals(values[1])?FALSE:TRUE},false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a},new Type[]{a},"clone",
                    (values) -> new Value[]{values[0].clone(false,null)},false));
        }
        {//cloning an immutable array creates a mutable copy
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a)},
                    new Type[]{Type.arrayOf(a).mutable()},"clone",
                    (values) -> new Value[]{values[0].clone(false,values[0].type.mutable())},false));
        }
        {//cloning an immutable array creates a mutable copy
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable()},
                    new Type[]{Type.arrayOf(a).mutable()},"clone",
                    (values) -> new Value[]{values[0].clone(false,values[0].type.mutable())},false));
        }
        {//cloning an immutable copy of a mutable array
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable()},
                    new Type[]{Type.arrayOf(a)},"clone-mut~",
                    (values) -> new Value[]{values[0].clone(false,values[0].type.asArray().immutable())},false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a},new Type[]{a},"clone!",
                    (values) -> new Value[]{values[0].clone(true,null)},false));
        }

        Type unsigned = Type.UnionType.create(new Type[]{Type.UINT()});
        Type integer = Type.UnionType.create(new Type[]{unsigned,Type.BYTE(),Type.CODEPOINT(),Type.INT()});
        Type number  = Type.UnionType.create(new Type[]{integer,Type.FLOAT});

        procs.add(new InternalProcedure(new Type[]{Type.BOOL},new Type[]{Type.BOOL},"!",
                (values) -> new Value[]{values[0].asBool()?FALSE:TRUE},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT()},new Type[]{Type.INT()},"~",
                (values) -> new Value[]{ofInt(~values[0].asLong(),false)},true));
        procs.add(new InternalProcedure(new Type[]{Type.UINT()},new Type[]{Type.UINT()},"~",
                (values) -> new Value[]{ofInt(~values[0].asLong(),true)},true));

        procs.add(new InternalProcedure(new Type[]{Type.BOOL,Type.BOOL},new Type[]{Type.BOOL},"&",
                (values) -> new Value[]{values[0].asBool()&&values[1].asBool()?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{Type.BOOL,Type.BOOL},new Type[]{Type.BOOL},"|",
                (values) -> new Value[]{values[0].asBool()||values[1].asBool()?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{Type.BOOL,Type.BOOL},new Type[]{Type.BOOL},"xor",
                (values) -> new Value[]{values[0].asBool()^values[1].asBool()?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT()},"&",
                (values) -> new Value[]{ofInt(values[0].asLong()&values[1].asLong(),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"&",
                (values) -> new Value[]{ofInt(values[0].asLong()&values[1].asLong(),false)},true));
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT()},"|",
                (values) -> new Value[]{ofInt(values[0].asLong()|values[1].asLong(),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"|",
                (values) -> new Value[]{ofInt(values[0].asLong()|values[1].asLong(),false)},true));
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT()},"xor",
                (values) -> new Value[]{ofInt(values[0].asLong()^values[1].asLong(),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"xor",
                (values) -> new Value[]{ofInt(values[0].asLong()^values[1].asLong(),false)},true));

        procs.add(new InternalProcedure(new Type[]{Type.UINT(),integer},new Type[]{Type.UINT()},"<<",
                (values) -> new Value[]{ofInt(values[0].asLong()<<values[1].asLong(),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"<<",
                (values) -> new Value[]{ofInt(signedLeftShift(values[0].asLong(),values[1].asLong()),false)},true));
        procs.add(new InternalProcedure(new Type[]{Type.UINT(),integer},new Type[]{Type.UINT()},">>",
                (values) -> new Value[]{ofInt(values[0].asLong()>>>values[1].asLong(),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},">>",
                (values) -> new Value[]{ofInt(values[0].asLong()>>values[1].asLong(),false)},true));

        procs.add(new InternalProcedure(new Type[]{Type.FLOAT},new Type[]{Type.FLOAT},"-_",
                (values) -> new Value[]{Value.ofFloat(-(values[0].asDouble()))},true));
        procs.add(new InternalProcedure(new Type[]{Type.FLOAT},new Type[]{Type.FLOAT},"/_",
                (values) -> new Value[]{Value.ofFloat(1.0/values[0].asDouble())},true));

        for(Type.IntType aInt: Type.IntType.intTypes){
            if(aInt.signed){
                procs.add(new InternalProcedure(new Type[]{aInt},new Type[]{aInt},"-_",
                        (values) -> new Value[]{Value.ofInt(-(values[0].asLong()),false).castTo(aInt)},true));
            }
            for(Type.IntType bInt: Type.IntType.intTypes){
                Type.IntType target= Type.IntType.commonSuperType(aInt,bInt).orElse(aInt.signed?Type.IntType.INT:Type.IntType.UINT);
                procs.add(new InternalProcedure(new Type[]{aInt,bInt},new Type[]{target},"+",
                        (values) -> new Value[]{ofInt(values[0].asLong()+values[1].asLong(),
                                !target.signed).castTo(target)},
                        true));
                procs.add(new InternalProcedure(new Type[]{aInt,bInt},new Type[]{target},"-",
                        (values) -> new Value[]{ofInt(values[0].asLong()-values[1].asLong(),
                                !target.signed).castTo(target)},
                        true));
            }
        }
        procs.add(new InternalProcedure(new Type[]{Type.FLOAT,Type.FLOAT},new Type[]{Type.FLOAT},"+",
                (values) -> new Value[]{ofFloat(values[0].asDouble()+values[1].asDouble())},true));
        procs.add(new InternalProcedure(new Type[]{Type.FLOAT,Type.FLOAT},new Type[]{Type.FLOAT},"-",
                (values) ->  new Value[]{ofFloat(values[0].asDouble()-values[1].asDouble())},true));

        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT()},"*",
                (values) ->  new Value[]{ofInt(values[0].asLong()*values[1].asLong(),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"*",
                (values) ->  new Value[]{ofInt(values[0].asLong()*values[1].asLong(),false)},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"*",
                (values) ->  new Value[]{ofFloat(values[0].asDouble()*values[1].asDouble())},true));
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT()},"/",
                (values) ->  new Value[]{ofInt(Long.divideUnsigned(values[0].asLong(),values[1].asLong()),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"/",
                (values) ->  new Value[]{ofInt(values[0].asLong()/values[1].asLong(),false)},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"/",
                (values) ->  new Value[]{ofFloat(values[0].asDouble()/values[1].asDouble())},true));
        procs.add(new InternalProcedure(new Type[]{unsigned,integer},new Type[]{Type.UINT()},"%",
                (values) ->  new Value[]{ofInt(Long.remainderUnsigned(values[0].asLong(),values[1].asLong()),true)},true));
        procs.add(new InternalProcedure(new Type[]{Type.INT(),integer},new Type[]{Type.INT()},"%",
                (values) ->  new Value[]{ofInt(values[0].asLong()%values[1].asLong(),false)},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.FLOAT},"%",
                (values) ->  new Value[]{ofFloat(values[0].asDouble()%values[1].asDouble())},true));

        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},">",
                (values) ->  new Value[]{compareNumbers(values[0],values[1])>0?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},">=",
                (values) ->  new Value[]{compareNumbers(values[0],values[1])>=0?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"<",
                (values) ->  new Value[]{compareNumbers(values[0],values[1])<0?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"<=",
                (values) ->  new Value[]{compareNumbers(values[0],values[1])<=0?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"==",
                (values) ->  new Value[]{compareNumbers(values[0],values[1])==0?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{number,number},new Type[]{Type.BOOL},"!=",
                (values) ->  new Value[]{compareNumbers(values[0],values[1])!=0?TRUE:FALSE},true));

        procs.add(new InternalProcedure(new Type[]{Type.TYPE,Type.TYPE},new Type[]{Type.BOOL},"<=",
                (values) ->  new Value[]{values[0].asType().canConvertTo(values[1].asType())?TRUE:FALSE},true));
        procs.add(new InternalProcedure(new Type[]{Type.TYPE,Type.TYPE},new Type[]{Type.BOOL},">=",
                (values) ->  new Value[]{values[1].asType().canConvertTo(values[0].asType())?TRUE:FALSE},true));

        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).mutable(),Type.UINT()},
                    new Type[]{Type.referenceTo(a).mutable()},"[]",
                    (values) ->  {
                        long index=values[1].asLong();
                        ArrayLike array=((ArrayLike)values[0]);
                        return new Value[]{new ReferenceValue(values[0].type.content(),()->array.get(index),
                                val->array.set(index,val))};
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),Type.UINT()},
                    new Type[]{a},"[]",
                    (values) ->  new Value[]{((ArrayLike)values[0]).get(values[1].asLong())},false));
        }

        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a},new Type[]{Type.optionalOf(a)},
                    "wrap",
                    (values) ->  new Value[]{wrap(values[0])},true));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.optionalOf(a)},
                    new Type[]{Type.BOOL},"!",
                    (values) ->  new Value[]{values[0].hasValue()?FALSE:TRUE},true));
        }

        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type list = Type.memoryOf(a).mutable();
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{list,a},new Type[]{},"[]^=",
                    (values) ->   {
                        //list val
                        ((ArrayLike)values[0]).append(values[1]);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            Type list = Type.memoryOf(a).mutable();
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,list},new Type[]{},"^[]=",
                    (values) ->  {
                        //val list
                        ((ArrayLike)values[1]).prepend(values[0]);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),
                    Type.UINT(),Type.memoryOf(a).mutable(), Type.INT(),Type.UINT()}, new Type[]{},"copy",
                    (values) ->   {
                        //src srcOff target targetOff count
                        ArrayLike src=(ArrayLike)values[0];
                        long srcOff=values[1].asLong();
                        ArrayLike target=(ArrayLike)values[2];
                        long off=values[3].asLong();
                        long count=values[4].asLong();
                        target.copyFrom(off,src.elements(),srcOff,count);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),
                    Type.UINT(),Type.arrayOf(a).mutable(), Type.UINT(),Type.UINT()}, new Type[]{},"copy",
                    (values) ->  {
                        //src srcOff target targetOff count
                        ArrayLike src=(ArrayLike)values[0];
                        long srcOff=values[1].asLong();
                        ArrayLike target=(ArrayLike)values[2];
                        long off=values[3].asLong();
                        long count=values[4].asLong();
                        target.copyFrom(off,src.elements(),srcOff,count);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.arrayOf(a).maybeMutable(),
                    Type.UINT(),Type.memoryOf(a).mutable(), Type.UINT(),Type.UINT(),Type.UINT()},
                    new Type[]{},"copyToSlice",
                    (values) ->  {
                        //src srcOff target targetOff count
                        ArrayLike src=(ArrayLike)values[0];
                        long srcOff=values[1].asLong();
                        ArrayLike target=(ArrayLike)values[2];
                        long sliceStart=values[3].asLong();
                        long sliceEnd=values[4].asLong();
                        long count=values[5].asLong();
                        target.copyToSlice(sliceStart,sliceEnd,src.elements(),srcOff,count);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,Type.memoryOf(a).mutable(),
                    Type.INT(),Type.UINT()}, new Type[]{},"fill",
                    (values) ->   {
                        //val target off count
                        Value val=values[0];
                        ArrayLike target=(ArrayLike)values[1];
                        long off=values[2].asLong();
                        long count=values[3].asLong();
                        target.fill(val,off,count);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{a,Type.arrayOf(a).mutable(),
                    Type.INT(),Type.UINT()}, new Type[]{},"fill",
                    (values) ->   {
                        //val target off count
                        Value val=values[0];
                        ArrayLike target=(ArrayLike)values[1];
                        long off=values[2].asLong();
                        long count=values[3].asLong();
                        target.fill(val,off,count);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},
                    new Type[]{Type.memoryOf(a).mutable(),Type.UINT(),Type.UINT()}, new Type[]{},"clearSlice",
                    (values) ->  {
                        //memory off to
                        ArrayLike mem=(ArrayLike)values[0];
                        long off=values[1].asLong();
                        long to=values[2].asLong();
                        mem.clearSlice(off,to);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.memoryOf(a).mutable(),
                    Type.UINT()}, new Type[]{},"realloc",
                    (values) ->   {
                        //memory newSize
                        ArrayLike mem=(ArrayLike)values[0];
                        long newSize=values[1].asLong();
                        mem.reallocate(newSize);
                        return new Value[0];
                    },false));
        }
        {
            Type.GenericParameter a=new Type.GenericParameter("A", 0,true,InternalProcedure.POSITION);
            procs.add(new InternalProcedure(new Type.GenericParameter[]{a},new Type[]{Type.memoryOf(a).mutable(),
                    Type.INT()}, new Type[]{},"setOffset",
                    (values) ->  {
                        //memory newOffset
                        ArrayLike mem=(ArrayLike)values[0];
                        long newOffset=values[1].asLong();
                        mem.setOffset(newOffset);
                        return new Value[0];
                    },false));
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
            }else if(type==Type.BYTE()){
                return ofByte((Byte)jValue);
            }else if(type == Type.CODEPOINT()){
                return ofChar((Integer)jValue);
            }else if(type==Type.INT()||type==Type.UINT()){
                return ofInt((Long) jValue,type==Type.UINT());
            }else if(type==Type.FLOAT){
                return ofFloat((Double)jValue);
            }else if(type.isArray()&&type.content()==Type.BYTE()){
                return new ArrayValue(type,wrapBytes((byte[])jValue));
            }else if(type.isMemory()&&type.content()==Type.BYTE()){
                Object[] parts=(Object[])jValue;
                return new ArrayValue(type,wrapBytes((byte[])parts[0]),(int)parts[1],(int)parts[2]);
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
        }else if(t == Type.BYTE()){
            return byte.class;
        }else if(t == Type.CODEPOINT()){
            return int.class;
        }else if(t == Type.INT()||t == Type.UINT()){
            return long.class;
        }else if(t == Type.FLOAT){
            return double.class;
        }else if(t == Type.ANY){
            return Object.class;
        }else if(t.isArray()&&t.content()==Type.BYTE()){
            return byte[].class;
        }else if(t.isMemory()&&t.content()==Type.BYTE()){
            return Object[].class;//bytes,off,length
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
        Object rawData(Type argType){
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
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            Type.Procedure newType = (Type.Procedure) type.replaceGenerics(genericParams);
            return newType!=type?new ExternalProcedure(name,isPublic, newType,nativeMethod,declaredAt):this;
        }

        @Override
        Value[] callWith(Value[] values) throws ConcatRuntimeError {
            Object[] nativeArgs=new Object[values.length];
            for(int i=0;i<values.length;i++){
                nativeArgs[i]=values[i].rawData(type.inTypes().get(i));
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

    static class TraitValue extends Value{
        final Value wrapped;

        protected TraitValue(Type.Trait type,Value wrapped) {
            super(type);
            this.wrapped=wrapped;
        }

        @Override
        public Type valueType() {
            return wrapped.valueType();
        }

        //addLater isEqualTo, rawData,updateFrom,equals

        @Override
        public Value clone(boolean deep, Type targetType) {//addLater handle targetType
            return new TraitValue((Type.Trait)type,wrapped.clone(deep, targetType));
        }

        @Override
        public Value replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
            return new TraitValue((Type.Trait) type.replaceGenerics(genericParams),wrapped.replaceGenerics(genericParams));
        }

        @Override
        public long id() {
            return wrapped.id();
        }
        @Override
        public boolean asBool() throws TypeError {
            return wrapped.asBool();
        }
        @Override
        public byte asByte() throws TypeError {
            return wrapped.asByte();
        }
        @Override
        public long asLong() throws TypeError {
            return wrapped.asLong();
        }
        @Override
        public double asDouble() throws TypeError {
            return wrapped.asDouble();
        }
        @Override
        public Type asType() throws TypeError {
            return wrapped.asType();
        }
        @Override
        public boolean isString() {
            return wrapped.isString();
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(type.canAssignTo(newType)){
                return this;
            }else{
                return wrapped.castTo(newType);
            }
        }

        @Override
        public String toString() {
            return wrapped.type+" as "+type+":"+stringValue();
        }

        @Override
        public String stringValue() {
            return wrapped.stringValue();
        }
    }
    static class ReferenceValue extends Value{
        private final ThrowingSupplier<Value,ConcatRuntimeError> get;
        private final ThrowingConsumer<Value,ConcatRuntimeError> set;
        ReferenceValue(Type contentType, ThrowingSupplier<Value,ConcatRuntimeError> get,
                       ThrowingConsumer<Value,ConcatRuntimeError> set) {
            super(Type.referenceTo(contentType));
            this.get = get;
            this.set = set;
        }

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof ReferenceValue))
                return false;
            try {
                return get.get().equals(((ReferenceValue) obj).get.get());
            } catch (ConcatRuntimeError e) {
                throw new RuntimeException(e);
            }
        }
        public Value get() throws ConcatRuntimeError {
            return get.get();
        }
        public void set(Value newVal) throws ConcatRuntimeError {
            set.accept(newVal);
        }

        @Override
        public Value castTo(Type newType) throws ConcatRuntimeError {
            if(type.canAssignTo(newType))
                return this;
            if(type.content().canCastTo(newType) != Type.CastType.NONE)
                return get.get().castTo(newType);
            return super.castTo(newType);
        }

        @Override
        public String stringValue() {
            try {
                return type+"("+get.get()+")";
            } catch (ConcatRuntimeError e) {
                throw new RuntimeException(e);
            }
        }

    }

    static int compareNumbers(Value n1,Value n2) throws ConcatRuntimeError{
        if(n1 instanceof ByteValue||n1 instanceof CodepointValue||n1 instanceof IntValue){
            if(n2 instanceof ByteValue||n2 instanceof CodepointValue||n2 instanceof IntValue){
                if(n1.type==Type.UINT()){
                    if(n2.type==Type.UINT()){
                        return Long.compareUnsigned(n1.asLong(),n2.asLong());
                    }else{
                        return n2.asLong()<0?1:Long.compareUnsigned(n1.asLong(),n2.asLong());
                    }
                }else{
                    if(n2.type==Type.UINT()){
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
