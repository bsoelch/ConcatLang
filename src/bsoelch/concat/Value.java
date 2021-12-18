package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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



    public boolean asBool() {
        throw new TypeError("Cannot convert "+type+" to bool");
    }
    public int  asChar() {
        throw new TypeError("Cannot convert "+type+" to char");
    }
    public long asLong() {
        throw new TypeError("Cannot convert "+type+" to int");
    }
    public double asDouble() {
        throw new TypeError("Cannot convert "+type+" to float");
    }
    public ValueIterator asItr() {
        throw new TypeError("Cannot convert "+type+" to itr");
    }

    public Type asType() {
        throw new TypeError("Cannot convert "+type+" to type");
    }
    public int asProcedure() {
        throw new TypeError("Cannot convert "+type+" to procedure");
    }
    public Value negate() {
        throw new TypeError("Cannot negate values of type "+type);
    }
    public Value invert() {
        throw new TypeError("Cannot invert values of type "+type);
    }
    public Value flip() {
        throw new TypeError("Cannot invert values of type "+type);
    }
    public int length() {
        throw new TypeError(type+" does not have a length");
    }
    public List<Value> elements() {
        throw new TypeError("Cannot convert "+type+" to list");
    }
    public Value get(long index) {
        throw new TypeError("Element access not supported for type "+type);
    }
    public Value slice(long off,long to) {
        throw new TypeError("Element access not supported for type "+type);
    }

    public Value iterator(boolean end) {
        throw new TypeError("Cannot iterate over "+type);
    }

    public abstract String stringValue();

    @Override
    public String toString() {
        return stringValue();
    }

    public Value castTo(Type type) {
        if(type==Type.ANY||this.type.equals(type)){
            return this;
        }else if(type.equals(Type.STRING())){
            return ofString(stringValue());
        }else{
            throw new SyntaxError("cannot cast from "+this.type+" to "+type);
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
        public Value castTo(Type type) {
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
        public Value castTo(Type type) {
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
        public Value castTo(Type type) {
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
        final String stringValue;
        private StringValue(String stringValue) {
            super(Type.STRING());
            this.stringValue = stringValue;
        }

        @Override
        public int length() {
            return stringValue.length();
        }
        @Override
        public Value castTo(Type type) {
            if(type.isList()){
                Type c=type.content();
                return createList(type,stringValue.codePoints().mapToObj(v->ofChar(v).castTo(c))
                        .collect(Collectors.toCollection(ArrayList::new)));
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
        public Value slice(long off,long to) {
            return ofString(stringValue.substring((int)off,(int)to));
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

    public static Value createList(Type type, ArrayList<Value> elements) {
        if(type.equals(Type.STRING())){
            return ofString(elements.stream().map(Value::asChar).map(Character::toChars).map(String::valueOf).
                    reduce("", (a,b)->a+b));
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
        public Value slice(long off,long to) {
            return createList(type,new ArrayList<>(elements.subList((int)off,(int)to)));
        }

        @Override
        public Value castTo(Type type) {
            if(type.isList()){
                Type c=type.content();
                return createList(type,elements.stream().map(v->v.castTo(c))
                        .collect(Collectors.toCollection(ArrayList::new)));
            }else{
                return super.castTo(type);
            }
        }

        @Override
        public String stringValue() {
            return elements.toString();
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

    public static Value ofProcedureId(int id) {
        return new ProcedureValue(id);
    }
    private static class ProcedureValue extends Value{
        final int id;
        private ProcedureValue(int id) {
            super(Type.PROCEDURE);
            this.id = id;
        }

        @Override
        public int asProcedure() {
            return id;
        }

        @Override
        public String stringValue() {
            return "@"+id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcedureValue that = (ProcedureValue) o;
            return id == that.id;
        }
        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
    public static Value concat(Value a,Value b) {
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
        throw new SyntaxError("Cannot concat "+a.type+" and "+b.type);
    }

    public static Value pushFirst(Value a,Value b) {
        if(b.type.isList()&&b.type.content().canAssignFrom(a.type)) {
            ArrayList<Value> elements = new ArrayList<>();
            elements.add(a);
            elements.addAll(b.elements());
            return createList(b.type, elements);
        }
        throw new SyntaxError("Cannot add "+a.type+" to "+b.type);
    }
    public static Value pushLast(Value a,Value b) {
        if(a.type.isList()&&a.type.content().canAssignFrom(b.type)) {
            ArrayList<Value> elements = new ArrayList<>(a.elements());
            elements.add(b);
            return createList(a.type, elements);
        }
        throw new SyntaxError("Cannot add "+b.type+" to "+a.type);
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
            case NEGATE,PLUS,MINUS,INVERT,MULT,DIV,MOD,POW,NOT,FLIP,AND,OR,XOR,LSHIFT,SLSHIFT,RSHIFT,SRSHIFT,
                    NEW_LIST,LIST_OF,UNWRAP,LENGTH,AT_INDEX,SLICE,PUSH_FIRST,CONCAT,PUSH_LAST,ITR_OF,ITR_START,ITR_END,ITR_NEXT,ITR_PREV,
                    CAST,TYPE_OF,CALL ->
                    throw new SyntaxError(opType +" is no valid comparison operator");
        }
        throw new RuntimeException("unreachable");
    }

    public static Value compare(Value a, OperatorType opType, Value b) {
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
            throw new SyntaxError("cannot compare "+a.type+" and "+b.type);
        }
    }

    public static Value mathOp(Value a, Value b, BiFunction<Long,Long,Value> intOp, BiFunction<Double,Double,Value> floatOp) {
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
        throw new SyntaxError("invalid parameters for arithmetic operation:"+a.type+" "+b.type);
    }
    public static Value logicOp(Value a, Value b, BinaryOperator<Boolean> boolOp,BinaryOperator<Long> intOp) {
        if(a.type==Type.BOOL&&b.type==Type.BOOL){
            return boolOp.apply(a.asBool(),b.asBool())?TRUE:FALSE;
        }else{
            return intOp(a,b,intOp);
        }
    }
    public static Value intOp(Value a,Value b,BinaryOperator<Long> intOp) {
        if(a instanceof IntValue&&b instanceof IntValue){
                return ofInt(intOp.apply(((IntValue) a).intValue, ((IntValue) b).intValue));
        }
        throw new SyntaxError("invalid parameters for int operator:"+a.type+" "+b.type);
    }
}
