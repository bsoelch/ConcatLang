package bsoelch.concat;

import java.util.ArrayList;
import java.util.Objects;
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



    public boolean asBool() {
        throw new TypeError("Cannot convert "+type+" to bool");
    }
    public int  asChar() {
        throw new TypeError("Cannot convert "+type+" to char");
    }
    public long asLong() {
        throw new TypeError("Cannot convert "+type+" to long");
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
    public Value get(long index) {
        throw new TypeError("Element access not supported for type "+type);
    }
    public abstract String stringValue();

    @Override
    public String toString() {
        return stringValue();
    }

    public Value castTo(Type type) {
        if(this.type.equals(type)){
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
        public Value get(long index) {
            return ofChar(stringValue.codePoints().toArray()[(int)index]);
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
        public Value get(long index) {
            return elements.get((int)index);
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
    public static Value ofProcedureId(int id) {
        return new ProcedureValue(id,Type.ANONYMOUS_PROCEDURE);
    }
    static Value ofProcedureId(int id,Type procedureType) {
        return new ProcedureValue(id,procedureType);
    }
    private static class ProcedureValue extends Value{
        final int id;
        private ProcedureValue(int id,Type type) {
            super(type);
            this.id = id;
        }

        @Override
        public int asProcedure() {
            return id;
        }

        @Override
        public Value castTo(Type type) {
            if(type.isProcedure()){
                if(this.type==Type.ANONYMOUS_PROCEDURE){
                    return ofProcedureId(id,type);
                }else{
                    throw new UnsupportedOperationException("casting between procedure types is currently not implemented");
                }
            }
            return super.castTo(type);
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

    public static Value plus(Value a,Value b) {
        if(a instanceof StringValue||b instanceof StringValue){
            return ofString(a.stringValue()+b.stringValue());
        }else{
            return mathOp(a,b, (x,y)-> ofInt(x+y), (x, y)-> ofFloat(x+y));
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
            case NEGATE,PLUS,MINUS,INVERT,MULT,DIV,MOD,POW,NOT,FLIP,AND,OR,XOR,
                    LSHIFT,SLSHIFT,RSHIFT,SRSHIFT,AT_INDEX,NEW_LIST,LIST_OF,CAST,TYPE_OF,CALL,TO ->
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
        throw new SyntaxError("invalid parameters for math Op:"+a.type+" "+b.type);
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
        throw new UnsupportedOperationException("unimplemented");
    }
}
