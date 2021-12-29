package bsoelch.concat;

import bsoelch.concat.streams.ListStream;
import bsoelch.concat.streams.ValueStream;

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

    public boolean asBool() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to bool");
    }
    public byte asByte() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to byte");
    }
    public int  asChar() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to char");
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
    public ValueIterator asItr() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to itr");
    }
    //TODO string streams
    public ValueStream asStream() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to stream");
    }
    public List<Byte> asByteArray() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to byte list");
    }
    //TODO? implement bytes-method all (primitive) Values
    public Value bytes(boolean bigEndian) throws ConcatRuntimeError {
        throw new TypeError("Converting "+type+" to raw-bytes is not supported");
    }
    /**checks if this and v are the same object (reference)
     * unlike equals this method distinguishes mutable objects with different ids but the same elements*/
    boolean isEqualTo(Value v){
        return equals(v);
    }

    public Interpreter.TokenPosition asProcedure() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to procedure");
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
    public List<Value> elements() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to list");
    }
    public Value get(long index) throws TypeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void set(long index,Value value) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void ensureCap(long newCap) throws TypeError {
        throw new TypeError("changing capacity is not supported for type "+type);
    }
    public void push(Value value,boolean start) throws ConcatRuntimeError {
        throw new TypeError("adding elements is not supported for type "+type);
    }
    public void pushAll(Value value,boolean start) throws ConcatRuntimeError {
        throw new TypeError("adding elements is not supported for type "+type);
    }
    public Value getSlice(long off, long to) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void setSlice(long off, long to,Value value) throws ConcatRuntimeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public Value iterator(boolean end) throws TypeError {
        throw new TypeError("Cannot iterate over "+type);
    }
    public Value stream(boolean reversed) throws TypeError {
        throw new TypeError("Cannot stream "+type);
    }
    public Value clone(boolean deep) {
        return this;
    }

    public Value hasField(String name) throws TypeError {
        throw new TypeError("Field access not supported for type "+type);
    }
    public Value getField(String name) throws ConcatRuntimeError {
        throw new TypeError("Field access not supported for type "+type);
    }
    public void setField(String name, Value newValue) throws ConcatRuntimeError {
        throw new TypeError("Field access not supported for type "+type);
    }
    public void importTo(Interpreter.ProgramState context,boolean allowMutable) throws ConcatRuntimeError {
        throw new TypeError("Field access not supported for type "+type);
    }

    public boolean isString(){
        return Type.STRING().equals(type);
    }
    public abstract String stringValue();
    /**formatted printing of values*/ //TODO flags unsigned,scientific, bracket-type,escaping of values
    public String stringValue(int precision, int base,boolean big,char plusChar) throws ConcatRuntimeError {
        return stringValue();
    }

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
    /**converts a byte[] to an int, assumes little-endian byte-order*/
    public static long intFromBytes(List<Byte> bytes) throws ConcatRuntimeError {
        if(bytes.size()>8){
            throw new ConcatRuntimeError("too much bytes from convert to int:"+bytes.size()+" maximum: 8");
        }
        long val=0;
        if(bytes.size()>0){
            int shift=0;
            for(Byte b:bytes){
                val|=(b &0xffL)<<shift;
                shift+=8;
            }
            if(bytes.size()<8&&((bytes.get(bytes.size()-1)&0x80)!=0)){
                val|=-1L<<shift;
            }
        }
        return val;
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
        public Value bytes(boolean bigEndian) throws ConcatRuntimeError {
            ArrayList<Value> bytes=new ArrayList<>(8);
            long tmp=intValue;
            for(int i=0;i<8;i++){
                bytes.add(ofByte((byte)(tmp&0xff)));
                tmp>>>=8;
            }
            if(bigEndian){
                Collections.reverse(bytes);
            }
            return createList(Type.listOf(Type.BYTE),bytes);
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
        public String stringValue(int precision, int base,boolean big,char plusChar) {
            return Printf.toString(false,intValue,base,big,plusChar);
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
        public double asDouble() {
            return floatValue;
        }
        @Override
        public Value bytes(boolean bigEndian) throws ConcatRuntimeError {
            ArrayList<Value> bytes=new ArrayList<>(8);
            long tmp=Double.doubleToRawLongBits(floatValue);
            for(int i=0;i<8;i++){
                bytes.add(ofByte((byte)(tmp&0xff)));
                tmp>>>=8;
            }
            if(bigEndian){
                Collections.reverse(bytes);
            }
            return createList(Type.listOf(Type.BYTE),bytes);
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
        public String stringValue(int precision, int base,boolean big,char plusChar) throws ConcatRuntimeError {
            return Printf.toString(floatValue,precision,base,big,false,plusChar);
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
        public Type asType() {
            return typeValue;
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
        public int asChar() {
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
        public byte asByte(){
            return byteValue;
        }

        @Override
        public Value castTo(Type type) throws ConcatRuntimeError {
            if(type==Type.INT){
                return ofInt(byteValue);
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public Value bytes(boolean bigEndian) throws ConcatRuntimeError {
            return createList(Type.listOf(Type.BYTE),
                    new ArrayList<>(Collections.singletonList(ofByte(byteValue))));
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
            return Objects.hash(byteValue);
        }
    }

    //addLater? allow iterators to modify the underlying objects
    interface ValueIterator{
        boolean hasNext();
        Value next();
        boolean hasPrev();
        Value prev();
    }

    public static Value ofString(String stringValue) {
        return new ListValue(Type.STRING(),stringValue.codePoints().mapToObj(Value::ofChar)
                .collect(Collectors.toCollection(ArrayList::new)));
    }
    public static Value createList(Type type, ArrayList<Value> elements) throws ConcatRuntimeError {
        return new ListValue(type,elements);
    }
    public static Value createList(Type type, long initCap) {
        return new ListValue(type,new ArrayList<>((int)Math.min(initCap,Integer.MAX_VALUE)));
    }

    private static class ListValue extends Value{
        final ArrayList<Value> elements;
        private ListValue(Type type,ArrayList<Value> elements) {
            super(type);
            this.elements = elements;
        }

        @Override
        boolean isEqualTo(Value v) {
            return v instanceof ListValue &&
                    ((ListValue) v).elements==elements;//check reference equality
        }

        @Override
        public Value clone(boolean deep) {
            ArrayList<Value> newElements;
            if(deep){
                newElements=elements.stream().map(v->v.clone(true)).collect(Collectors.toCollection(ArrayList::new));
            }else{
                newElements=new ArrayList<>(elements);
            }
            return new ListValue(type,newElements);
        }

        @Override
        public List<Byte> asByteArray() throws TypeError {
            if(type.content()==Type.BYTE){
                return elements.stream().map(t->((ByteValue)t).byteValue).toList();
            }
            return super.asByteArray();
        }

        @Override
        public int length() {
            return elements.size();
        }

        @Override
        public List<Value> elements() {
            return new ArrayList<>(elements);
        }

        @Override
        public Value iterator(boolean end) {
            return new ListIterator(Type.iteratorOf(type.content()),elements,end);
        }

        @Override
        public Value stream(boolean reversed){
            if(reversed){
                return ofStream(ListStream.create(type.content(),ReversedList.reverse(elements)));
            }else{
                return ofStream(ListStream.create(type.content(),elements));
            }
        }

        @Override
        public Value get(long index) {
            return elements.get((int)index);
        }
        @Override
        public void set(long index,Value value) throws ConcatRuntimeError {
            elements.set((int)index,value.castTo(type.content()));
        }
        @Override
        public Value getSlice(long off, long to) throws ConcatRuntimeError {
            return createList(type,new ArrayList<>(elements.subList((int)off,(int)to)));
        }

        @Override
        public void setSlice(long off, long to, Value value) throws ConcatRuntimeError {
            List<Value> sublist=elements.subList((int)off,(int)to);
            sublist.clear();
            try {
                Type content=type.content();
                sublist.addAll(value.elements().stream().map(e->e.unsafeCastTo(content)).toList());
            }catch (WrappedConcatError e){
                throw e.wrapped;
            }
        }

        @Override
        public void ensureCap(long newCap){
            elements.ensureCapacity((int)Math.min(newCap,Integer.MAX_VALUE));
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
                    List<Value> push=value.elements().stream().map(v->v.unsafeCastTo(type.content())).toList();
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
            if(type.isList()){
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
                    str.append(Character.toChars(((CharValue)v).asChar()));
                }
                return str.toString();
            }else{
                return elements.toString();
            }
        }
        @Override
        public String stringValue(int precision, int base, boolean big, char plusChar) throws ConcatRuntimeError {
            return toString(type.content(),elements,precision, base, big, plusChar);
        }

        static String toString(Type content,List<Value> elements,int precision, int base, boolean big, char plusChar) throws ConcatRuntimeError {
            if(Type.CHAR.equals(content)){
                StringBuilder str=new StringBuilder("\"");
                for(Value v:elements){
                    str.append(Character.toChars(v.asChar()));
                }
                return str.append("\"").toString();
            }else{
                StringBuilder str=new StringBuilder("[");
                for(Value v:elements){
                    if(str.length()>1){
                        str.append(',');
                    }
                    str.append(v.stringValue(precision, base, big, plusChar));
                }
                return str.append(']').toString();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListValue listValue = (ListValue) o;
            return Objects.equals(elements, listValue.elements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elements);
        }
    }
    private static class ListIterator extends Value implements ValueIterator{
        final ArrayList<Value> elements;
        int i;

        protected ListIterator(Type type, ArrayList<Value> elements,boolean end) {
            this(type,elements,end?elements.size():0);
        }
        private ListIterator(Type type, ArrayList<Value> elements, int index) {
            super(type);
            this.elements = elements;
            i=index;
        }

        @Override
        boolean isEqualTo(Value v) {
            return this == v;
        }

        @Override
        public Value clone(boolean deep) {
            ArrayList<Value> newElements;
            if(deep){
                newElements=elements.stream().map(v->v.clone(true)).collect(Collectors.toCollection(ArrayList::new));
            }else{
                newElements=new ArrayList<>(elements);
            }
            return new ListIterator(type,newElements,i);
        }

        @Override
        public ValueIterator asItr() {
            return this;
        }
        @Override
        public String stringValue() {
            return "Itr{"+elements.subList(0,i)+"^"+elements.subList(i,elements.size())+"}";
        }
        @Override
        public String stringValue(int precision, int base, boolean big, char plusChar) throws ConcatRuntimeError {
            return "Itr{"+ListValue.toString(type.content(),elements.subList(0,i),precision,base,big,plusChar)+"^"
                    +ListValue.toString(type.content(),elements.subList(i,elements.size()),precision,base,big,plusChar)+"}";
        }

        @Override
        public boolean hasNext() {
            return i<elements.size();
        }
        @Override
        public Value next() {
            return elements.get(i++);
        }
        @Override
        public boolean hasPrev() {
            return i>0;
        }
        @Override
        public Value prev() {
            return elements.get(--i);
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

        private TupleValue(Type.Tuple type,Value[] elements){
            super(type);
            this.elements = elements;
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
            if(type instanceof Type.Tuple&&
                    ((Type.Tuple) type).elementCount()==((Type.Tuple)this.type).elementCount()){
                Value[] newValues=new Value[elements.length];
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
        public Value get(long index){
            return elements[(int)index];
        }
        @Override
        public void set(long index, Value value) throws ConcatRuntimeError {
            elements[(int)index]=value.castTo(((Type.Tuple)type).get((int)index));
        }

        @Override
        public String stringValue() {
            StringBuilder res=new StringBuilder("(");
            for(int i=0;i<elements.length;i++){
                if(i>0){
                    res.append(',');
                }
                res.append(elements[i]);
            }
            return res.append(")").toString();
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

    public static Value ofProcedureId(Interpreter.TokenPosition pos) {
        return new ProcedureValue(pos);
    }
    private static class ProcedureValue extends Value{
        final Interpreter.TokenPosition pos;
        private ProcedureValue(Interpreter.TokenPosition pos) {
            super(Type.PROCEDURE);
            this.pos = pos;
        }

        @Override
        public Interpreter.TokenPosition asProcedure() {
            return pos;
        }

        @Override
        public String stringValue() {
            return "@("+pos+")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcedureValue that = (ProcedureValue) o;
            return pos.equals(that.pos);
        }
        @Override
        public int hashCode() {
            return Objects.hash(pos);
        }
    }

    public static Value newStruct(HashMap<String, Interpreter.Variable> variables) {
        return new StructValue(variables);
    }
    private static class StructValue extends Value{
        final HashMap<String, Interpreter.Variable> variables;
        private StructValue(HashMap<String, Interpreter.Variable> variables) {
            super(Type.STRUCT);
            this.variables = variables;
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
        public Value hasField(String name){
            return variables.containsKey(name)?TRUE:FALSE;
        }
        @Override
        public Value getField(String name) throws ConcatRuntimeError {
            Interpreter.Variable var = variables.get(name);
            if (var == null) {
                throw new ConcatRuntimeError("struct "+this+" does not have a field " + name);
            }
            return var.getValue();
        }
        @Override
        public void setField(String name, Value newValue) throws ConcatRuntimeError {
            Interpreter.Variable var = variables.get(name);
            if (var == null) {
                throw new ConcatRuntimeError("struct "+this+" does not have a field " + name);
            }else if (var.isConst) {
                throw new ConcatRuntimeError("Tried to modify constant field " + name);
            }
            var.setValue(newValue);
        }

        @Override
        public void importTo(Interpreter.ProgramState context,boolean allowMutable) throws ConcatRuntimeError {
            for(Map.Entry<String, Interpreter.Variable> e:variables.entrySet()) {
                if (allowMutable ||e.getValue().isConst) {
                    Interpreter.Variable prev = context.getVariable(e.getKey());
                    if (prev != null) {
                        if (prev.isConst) {
                            throw new ConcatRuntimeError("Tried to overwrite constant variable " + e.getKey());
                        } else if (e.getValue().isConst) {
                            throw new ConcatRuntimeError("Constant field " + e.getKey() + " cannot overwrite an existing variable");
                        }
                    }
                    context.variables.put(e.getKey(), e.getValue());
                }
            }
        }

        @Override
        public String stringValue() {
            StringBuilder str=new StringBuilder("{");
            for(Map.Entry<String, Interpreter.Variable> e:variables.entrySet()){
                if(str.length()>1){
                    str.append(", ");
                }
                str.append('.').append(e.getKey()).append("=").append(e.getValue().getValue().stringValue());
            }
            return str.append("}").toString();
        }
        @Override
        public String stringValue(int precision, int base, boolean big, char plusChar) throws ConcatRuntimeError {
            StringBuilder str=new StringBuilder("{");
            for(Map.Entry<String, Interpreter.Variable> e:variables.entrySet()){
                if(str.length()>1){
                    str.append(", ");
                }
                str.append('.').append(e.getKey()).append("=").append(e.getValue().getValue()
                        .stringValue(precision, base, big, plusChar));
            }
            return str.append("}").toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StructValue that = (StructValue) o;
            return Objects.equals(variables, that.variables);
        }
        @Override
        public int hashCode() {
            return Objects.hash(variables);
        }
    }

    public static Value ofStream(ValueStream stream) {
        return new StreamValue(Type.streamOf(stream.contentType()),stream);
    }
    private static class StreamValue extends Value{
        final ValueStream streamValue;
        private StreamValue(Type type,ValueStream streamValue) {
            super(type);
            this.streamValue = streamValue;
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
        public ValueStream asStream(){
            return streamValue;
        }

        @Override
        public Value stream(boolean reversed){
            if(reversed){
                //addLater reversible streams
                throw new UnsupportedOperationException("unimplemented");
            }else{
                return this;
            }
        }

        @Override
        public String stringValue() {
            return "["+type+"]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StreamValue that = (StreamValue) o;
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
