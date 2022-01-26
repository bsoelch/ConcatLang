package bsoelch.concat;

import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class Type {
    public static final Type INT   = new Type("int",  true);
    public static final Type UINT  = new Type("uint", true);
    public static final Type CODEPOINT = new Type("codepoint", true);
    public static final Type FLOAT = new Type("float",false);
    public static final Type TYPE  = new Type("type", false);
    public static final Type BOOL  = new Type("bool", false);
    public static final Type BYTE  = new Type("byte", true);

    public static final Type FILE = new Type("(file)", false);

    public static final Type GENERIC_LIST      = new Type("(list)", false);
    public static final Type GENERIC_OPTIONAL  = new Type("(optional)", false);
    public static final Type GENERIC_PROCEDURE = new Type("*->*", false);

    /**blank type that could contain any value*/
    public static final Type ANY = new Type("var", false) {};

    public static Type UNICODE_STRING() {
        return WrapperType.UNICODE_STRING;
    }
    public static Type RAW_STRING() {
        return WrapperType.BYTES;
    }

    private Type(String name, boolean switchable) {
        this.name = name;
        this.switchable = switchable;
    }

    final String name;
    final boolean switchable;

    /**@return true if values of this type can be assigned to type t*/
    public boolean isSubtype(Type t){
        return (t==this)||t==ANY;
    }

    @Override
    public String toString() {
        return name;
    }
    public boolean isList() {
        return false;
    }
    public boolean isOptional() {
        return false;
    }
    public boolean isVarArg() {
        return false;
    }
    public Type content() {
        throw new UnsupportedOperationException();
    }

    public static Type listOf(Type contentType) throws ConcatRuntimeError {
        //addLater? caching
        if (contentType == CODEPOINT) {
            return WrapperType.UNICODE_STRING;
        } else if (contentType == BYTE) {
            return WrapperType.BYTES;
        } else {
            return new WrapperType(WrapperType.LIST,contentType);
        }
    }
    public static Type varArg(Type contentType) throws ConcatRuntimeError {
        return new WrapperType(WrapperType.VAR_ARG,contentType);
    }
    public static Type optionalOf(Type contentType) throws ConcatRuntimeError {
        return new WrapperType(WrapperType.OPTIONAL,contentType);
    }

    private static class WrapperType extends Type {
        static final String LIST = "list";
        static final String VAR_ARG = "...";
        static final String OPTIONAL = "optional";

        static final Type BYTES;
        static final Type UNICODE_STRING;
        static {
            try {
                BYTES  = new WrapperType(LIST,Type.BYTE);
                UNICODE_STRING = new WrapperType(LIST,Type.CODEPOINT);
            } catch (ConcatRuntimeError e) {
                throw new RuntimeException(e);
            }
        }

        final Type contentType;
        final String wrapperName;

        private WrapperType(String wrapperName, Type contentType) throws ConcatRuntimeError {
            super(contentType.name+" "+ wrapperName, LIST.equals(wrapperName)&&
                    (contentType==BYTE||contentType==CODEPOINT));
            this.wrapperName = wrapperName;
            this.contentType = contentType;
            if(contentType.isVarArg()){
                throw new ConcatRuntimeError(contentType+" cannot be part of "+wrapperName);
            }
        }

        @Override
        public boolean isList() {
            return wrapperName.equals(LIST);
        }
        @Override
        public boolean isOptional() {
            return wrapperName.equals(OPTIONAL);
        }
        @Override
        public boolean isVarArg() {
            return wrapperName.equals(VAR_ARG);
        }

        @Override
        public boolean isSubtype(Type t) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().isSubtype(t.content());
            }else{
                return (wrapperName.equals(LIST)&&t==GENERIC_LIST)||(wrapperName.equals(OPTIONAL)&&t==GENERIC_OPTIONAL)||
                        super.isSubtype(t);
            }
        }

        @Override
        public Type content() {
            return contentType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WrapperType that = (WrapperType) o;
            return Objects.equals(contentType, that.contentType) && Objects.equals(wrapperName, that.wrapperName);
        }
        @Override
        public int hashCode() {
            return Objects.hash(contentType, wrapperName);
        }
    }

    public static class Tuple extends Type implements Interpreter.Declarable{
        final Interpreter.FilePosition declaredAt;
        final Type[] elements;
        public Tuple(String name, Type[] elements, Interpreter.FilePosition declaredAt) throws ConcatRuntimeError {
            super(name, false);
            for(Type t:elements){
                if(t.isVarArg()){
                    throw new ConcatRuntimeError(t+" cannot be part of tuples");
                }
            }
            this.elements=elements;
            this.declaredAt = declaredAt;
        }

        @Override
        public boolean isSubtype(Type t) {
            if(t instanceof Tuple&&((Tuple) t).elementCount()<=elementCount()){
                for(int i=0;i<((Tuple) t).elements.length;i++){
                    if(!elements[i].isSubtype(((Tuple) t).elements[i])){
                        return false;
                    }
                }
                return true;
            }else{
                return super.isSubtype(t);
            }
        }

        public int elementCount(){
            return elements.length;
        }
        public Type get(int i) {
            return elements[i];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tuple tuple = (Tuple) o;
            return Arrays.equals(elements, tuple.elements);
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }

        @Override
        public Interpreter.DeclarableType declarableType() {
            return Interpreter.DeclarableType.TUPLE;
        }
        @Override
        public Interpreter.FilePosition declaredAt() {
            return declaredAt;
        }
    }

    public static class Procedure extends Type{
        final Type[] inTypes;
        final Type[] outTypes;
        private final Value insValue,outsValue;

        public static Type create(Type[] inTypes,Type[] outTypes){
            StringBuilder name=new StringBuilder();
            for(Type t:inTypes){
                name.append(t.name).append(' ');
            }
            name.append(inTypes.length).append(' ');
            for(Type t:outTypes){
                name.append(t.name).append(' ');
            }
            name.append(outTypes.length).append(" ->");
            return new Procedure(name.toString(),inTypes,outTypes);
        }
        private Procedure(String name,Type[] inTypes,Type[] outTypes) {
            super(name, false);
            this.inTypes=inTypes;
            this.outTypes=outTypes;
            try {
                insValue  = Value.createList(listOf(TYPE), Arrays.stream(inTypes).map(Value::ofType).
                        collect(Collectors.toCollection(ArrayList::new)));
                outsValue  = Value.createList(listOf(TYPE), Arrays.stream(outTypes).map(Value::ofType).
                        collect(Collectors.toCollection(ArrayList::new)));
            } catch (ConcatRuntimeError e) {
                throw new RuntimeException(e);
            }
        }

        Value ins(){
            return insValue;
        }
        Value outs(){
            return outsValue;
        }

        @Override
        public boolean isSubtype(Type t) {
            if(t instanceof Procedure proc){
                if(proc.inTypes.length== inTypes.length&&proc.outTypes.length==outTypes.length){
                    for(int i=0;i< inTypes.length;i++){
                        if(!proc.inTypes[i].isSubtype(inTypes[i])){
                            return false;
                        }
                    }
                    for(int i=0;i< outTypes.length;i++){
                        if(!outTypes[i].isSubtype(proc.outTypes[i])){
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }
            return t== GENERIC_PROCEDURE ||super.isSubtype(t);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Procedure procedure = (Procedure) o;
            return Arrays.equals(inTypes, procedure.inTypes) && Arrays.equals(outTypes, procedure.outTypes);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(inTypes);
            result = 31 * result + Arrays.hashCode(outTypes);
            return result;
        }
    }

    public static class Enum extends Type implements Interpreter.Declarable {
        final Interpreter.FilePosition declaredAt;
        final String[] entryNames;
        final Value.EnumEntry[] entries;
        public Enum(String name, String[] entryNames, ArrayList<Interpreter.FilePosition> entryPositions,
                    Interpreter.FilePosition declaredAt) {
            super(name, true);
            this.entryNames =entryNames;
            entries=new Value.EnumEntry[entryNames.length];
            for(int i=0;i<entryNames.length;i++){
                entries[i]=new Value.EnumEntry(this,i,entryPositions.get(i));
            }
            this.declaredAt = declaredAt;
        }
        public int elementCount(){
            return entryNames.length;
        }
        public String get(int i) {
            return entryNames[i];
        }

        @Override
        public Interpreter.DeclarableType declarableType() {
            return Interpreter.DeclarableType.ENUM;
        }
        @Override
        public Interpreter.FilePosition declaredAt() {
            return declaredAt;
        }
    }
}