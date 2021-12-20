package bsoelch.concat;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public int  asChar() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to char");
    }
    public long asLong() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to int");
    }
    public double asDouble() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to float");
    }
    public ValueIterator asItr() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to itr");
    }

    public Type asType() throws TypeError {
        throw new TypeError("Cannot convert "+type+" to type");
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
    public void set(long index,Value value) throws TypeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public Value getSlice(long off, long to) throws TypeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public void setSlice(long off, long to,Value value) throws TypeError {
        throw new TypeError("Element access not supported for type "+type);
    }
    public Value iterator(boolean end) throws TypeError {
        throw new TypeError("Cannot iterate over "+type);
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

    public abstract String stringValue();
    /**formatted printing of values*/ //TODO flags unsigned,scientific, bracket-type,escaping of values
    public String stringValue(int precision, int base,boolean big,char plusChar) {
        return stringValue();
    }

    @Override
    public String toString() {
        return stringValue();
    }

    public Value castTo(Type type) throws TypeError {
        if(type==Type.ANY||this.type.equals(type)){
            return this;
        }else if(type.equals(Type.STRING())){
            return ofString(stringValue());
        }else{
            throw new TypeError("cannot cast from "+this.type+" to "+type);
        }
    }


    /**A wrapper for TypeError that can be thrown inside functional interfaces*/
    private static class WrappedTypeError extends RuntimeException {
        final TypeError wrapped;
        public WrappedTypeError(TypeError wrapped) {
            this.wrapped = wrapped;
        }
    }
    private int unsafeAsChar(){
        try {
            return asChar();
        } catch (TypeError e) {
            throw new WrappedTypeError(e);
        }
    }
    private Value unsafeCastTo(Type type) throws WrappedTypeError{
        try {
            return castTo(type);
        } catch (TypeError e) {
            throw new WrappedTypeError(e);
        }
    }

    private interface NumberValue{}

    public static Value ofInt(long intValue) {
        return new IntValue(intValue);
    }
    private static class IntValue extends Value implements NumberValue{
        final long intValue;
        private IntValue(long intValue) {
            super(Type.INT);
            this.intValue = intValue;
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
        public double asDouble() {
            return intValue;
        }
        @Override
        public long asLong() {
            return intValue;
        }

        @Override
        public Value castTo(Type type) throws TypeError {
            if(type==Type.CHAR){
                return Value.ofChar((int)intValue);
            }else if(type==Type.FLOAT){
                return ofFloat(intValue);
            }else{
                return super.castTo(type);
            }
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

    public static Value ofFloat(double d) {
        return new FloatValue(d);
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
        public String stringValue(int precision, int base,boolean big,char plusChar) {
            return Printf.toString(floatValue,precision,base,big,false,plusChar);
        }

        @Override
        public Value castTo(Type type) throws TypeError {
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
        public Value castTo(Type type) throws TypeError {
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
    //addLater allow iterators to modify the underlying objects
    interface ValueIterator{
        boolean hasNext();
        Value next();
        boolean hasPrev();
        Value prev();
    }
    public static Value ofString(String stringValue) {
        return new StringValue(stringValue);
    }
    private static class StringValue extends Value{
        private String stringValue;
        private StringValue(String stringValue) {
            super(Type.STRING());
            this.stringValue = stringValue;
        }

        @Override
        public int length() {
            return stringValue.length();
        }
        @Override
        public Value castTo(Type type) throws TypeError {
            if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, stringValue.codePoints().mapToObj(v -> ofChar(v).unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedTypeError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            return stringValue;
        }

        @Override
        public List<Value> elements() {
            return stringValue.codePoints().mapToObj(Value::ofChar).toList();
        }

        @Override
        public Value get(long index) {
            return ofChar(stringValue.codePoints().toArray()[(int)index]);
        }

        @Override
        public void set(long index, Value value) throws TypeError {
            int[] cps=stringValue.codePoints().toArray();
            cps[(int)index]=value.castTo(Type.CHAR).asChar();
            stringValue=IntStream.of(cps).mapToObj(c->String.valueOf(Character.toChars(c))).reduce("",(a,b)->a+b);
        }
        @Override
        public Value getSlice(long off, long to) {
            return ofString(IntStream.of(Arrays.copyOfRange(stringValue.codePoints().toArray(),(int)off,(int)to)).
                    mapToObj(c->String.valueOf(Character.toChars(c))).reduce("",(a,b)->a+b));
        }
        @Override
        public void setSlice(long off, long to, Value value) throws TypeError {
            ArrayList<Integer> asList=new ArrayList<>(stringValue.codePoints().boxed().toList());
            List<Integer> slice=asList.subList((int)off,(int)to);
            slice.clear();
            try {
                Type content=type.content();
                slice.addAll(value.elements().stream().map(e->e.unsafeCastTo(content)).map(Value::unsafeAsChar).toList());
            }catch (WrappedTypeError e){
                throw e.wrapped;
            }
            stringValue=asList.stream().map(c->String.valueOf(Character.toChars(c))).reduce("",(a, b)->a+b);
        }

        @Override
        public Value iterator(boolean end) {
            return new StringIterator(stringValue,end);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringValue that = (StringValue) o;
            return Objects.equals(stringValue, that.stringValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stringValue);
        }
    }
    private static class StringIterator extends Value implements ValueIterator{
        final int[] codePoints;
        int i;

        protected StringIterator(String stringValue,boolean end) {
            super(Type.iteratorOf(Type.CHAR));
            this.codePoints = stringValue.codePoints().toArray();
            i=end?codePoints.length:0;
        }

        @Override
        public ValueIterator asItr() {
            return this;
        }

        @Override
        public String stringValue() {
            return "Itr{\""+ IntStream.of(Arrays.copyOfRange(codePoints, 0, i))
                    .mapToObj(c -> String.valueOf(Character.toChars(c))).reduce("", (a, b) -> a + b)
                    +"\"^\""+ IntStream.of(Arrays.copyOfRange(codePoints, i,codePoints.length))
                    .mapToObj(c -> String.valueOf(Character.toChars(c))).reduce("", (a, b) -> a + b)+"\"}";
        }

        @Override
        public boolean hasNext() {
            return i<codePoints.length;
        }
        @Override
        public Value next() {
            return ofChar(codePoints[i++]);
        }
        @Override
        public boolean hasPrev() {
            return i>0;
        }
        @Override
        public Value prev() {
            return ofChar(codePoints[--i]);
        }
    }

    public static Value createList(Type type, ArrayList<Value> elements) throws TypeError {
        if(type.equals(Type.STRING())){
            try {
                return ofString(elements.stream().map(Value::unsafeAsChar).map(Character::toChars).map(String::valueOf).
                        reduce("", (a, b) -> a + b));
            }catch (WrappedTypeError e){
                throw e.wrapped;
            }
        }else{
            return new ListValue(type,elements);
        }
    }
    private static class ListValue extends Value{
        final ArrayList<Value> elements;
        private ListValue(Type type,ArrayList<Value> elements) {
            super(type);
            this.elements = elements;
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
        public Value get(long index) {
            return elements.get((int)index);
        }
        @Override
        public void set(long index,Value value) throws TypeError {
            elements.set((int)index,value.castTo(type.content()));
        }
        @Override
        public Value getSlice(long off, long to) throws TypeError {
            return createList(type,new ArrayList<>(elements.subList((int)off,(int)to)));
        }

        @Override
        public void setSlice(long off, long to, Value value) throws TypeError {
            List<Value> sublist=elements.subList((int)off,(int)to);
            sublist.clear();
            try {
                Type content=type.content();
                sublist.addAll(value.elements().stream().map(e->e.unsafeCastTo(content)).toList());
            }catch (WrappedTypeError e){
                throw e.wrapped;
            }
        }

        @Override
        public Value castTo(Type type) throws TypeError {
            if(type.isList()){
                Type c=type.content();
                try {
                    return createList(type, elements.stream().map(v -> v.unsafeCastTo(c))
                            .collect(Collectors.toCollection(ArrayList::new)));
                }catch (WrappedTypeError e){
                    throw e.wrapped;
                }
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            return elements.toString();
        }
        @Override
        public String stringValue(int precision, int base, boolean big, char plusChar) {
            return toString(elements,precision, base, big, plusChar);
        }

        static String toString(List<Value> elements,int precision, int base, boolean big, char plusChar) {
            StringBuilder str=new StringBuilder("[");
            for(Value v:elements){
                if(str.length()>1){
                    str.append(',');
                }
                str.append(v.stringValue(precision, base, big, plusChar));
            }
            return str.append(']').toString();
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
            super(type);
            this.elements = elements;
            i=end?elements.size():0;
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
        public String stringValue(int precision, int base, boolean big, char plusChar) {
            return "Itr{"+ListValue.toString(elements.subList(0,i),precision,base,big,plusChar)+"^"
                    +ListValue.toString(elements.subList(i,elements.size()),precision,base,big,plusChar)+"}";
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
        public String stringValue(int precision, int base, boolean big, char plusChar) {
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


    public static Value concat(Value a,Value b) throws TypeError {
        if(a.type.isList()&&b.type.isList()){
            if(a.type.content().canAssignFrom(b.type.content())){
                ArrayList<Value> elements=new ArrayList<>(a.elements());
                elements.addAll(b.elements());
                return createList(a.type,elements);
            }else if(b.type.content().canAssignFrom(a.type.content())){
                ArrayList<Value> elements=new ArrayList<>(a.elements());
                elements.addAll(b.elements());
                return createList(b.type,elements);
            }
        }
        throw new TypeError("Cannot concat "+a.type+" and "+b.type);
    }

    public static Value pushFirst(Value a,Value b) throws TypeError {
        if(b.type.isList()&&b.type.content().canAssignFrom(a.type)) {
            ArrayList<Value> elements = new ArrayList<>();
            elements.add(a);
            elements.addAll(b.elements());
            return createList(b.type, elements);
        }
        throw new TypeError("Cannot add "+a.type+" to "+b.type);
    }
    public static Value pushLast(Value a,Value b) throws TypeError {
        if(a.type.isList()&&a.type.content().canAssignFrom(b.type)) {
            ArrayList<Value> elements = new ArrayList<>(a.elements());
            elements.add(b);
            return createList(a.type, elements);
        }
        throw new TypeError("Cannot add "+b.type+" to "+a.type);
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
        if(a instanceof StringValue&&b instanceof StringValue){
            int c=a.stringValue().compareTo(b.stringValue());
            return cmpToValue(c, opType);
        }else if(a instanceof CharValue &&b instanceof CharValue){
            return cmpToValue(Integer.compare(((CharValue) a).codePoint,((CharValue) b).codePoint), opType);
        }else if(a instanceof NumberValue&&b instanceof NumberValue){
            return mathOp(a,b,(x,y)->cmpToValue(x.compareTo(y),opType),(x,y)->cmpToValue(x.compareTo(y),opType));
        }else if(opType==OperatorType.EQ){
            return a.equals(b)?TRUE:FALSE;
        }else if(opType==OperatorType.NE){
            return a.equals(b)?FALSE:TRUE;
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
