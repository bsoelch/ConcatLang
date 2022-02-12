package bsoelch.concat;

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

    //addLater remove untyped ... types
    public static final Type UNTYPED_LIST      = new Type("(list)", false);
    public static final Type UNTYPED_OPTIONAL = new Type("(optional)", false);
    public static final Type UNTYPED_PROCEDURE = new Type("*->*", false);

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

    public static Type commonSuperType(Type a, Type b) {
        if(a==ANY||b==null){
            return a;
        }else if(a==null||b==ANY){
            return b;
        }else {
            if(a.isSubtype(b)){
                return b;
            }else if(b.isSubtype(a)){
                return a;
            }else if((a==UINT||a==INT)&&(b==UINT||b==INT)){
                return a;
            }else if((a==FLOAT&&(b==UINT||b==INT))||(b==FLOAT&&(a==UINT||a==INT))){
                return FLOAT;
            }else{
                return ANY;
            }
        }
    }

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
    public Type content() {
        throw new UnsupportedOperationException();
    }
    Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
        return this;
    }


    public static Type listOf(Type contentType) throws ConcatRuntimeError {
        return WrapperType.create(WrapperType.LIST,contentType);
    }

    public static Type optionalOf(Type contentType) throws ConcatRuntimeError {
        return WrapperType.create(WrapperType.OPTIONAL,contentType);
    }

    private static class WrapperType extends Type {
        static final String LIST = "list";
        static final String OPTIONAL = "optional";

        static final WrapperType BYTES= new WrapperType(LIST,Type.BYTE);
        static final WrapperType UNICODE_STRING= new WrapperType(LIST,Type.CODEPOINT);

        final Type contentType;
        final String wrapperName;

        private static WrapperType create(String wrapperName, Type contentType) {
            if(LIST.equals(wrapperName)) {
                if (contentType == CODEPOINT) {
                    return UNICODE_STRING;
                } else if (contentType == BYTE) {
                    return BYTES;
                }
            }
            //addLater? caching
            return new WrapperType(wrapperName,contentType);
        }
        private WrapperType(String wrapperName, Type contentType){
            super(contentType.name+" "+ wrapperName, LIST.equals(wrapperName)&&
                    (contentType==BYTE||contentType==CODEPOINT));
            this.wrapperName = wrapperName;
            this.contentType = contentType;
        }

        @Override
        WrapperType replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type newContent = contentType.replaceGenerics(genericParams, genericArgs);
            return contentType==newContent?this:create(wrapperName, newContent);
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
        public boolean isSubtype(Type t) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().isSubtype(t.content());
            }else{
                return (wrapperName.equals(LIST)&&t==UNTYPED_LIST)||(wrapperName.equals(OPTIONAL)&&t== UNTYPED_OPTIONAL)||
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

    public static class Tuple extends Type implements Interpreter.NamedDeclareable{
        final FilePosition declaredAt;
        final Type[] elements;
        final boolean named;

        private static String getName(Type[] elements){
            StringBuilder sb=new StringBuilder("( ");
            for(Type t:elements){
                sb.append(t).append(" ");
            }
            return sb.append(")").toString();
        }
        public Tuple(String name, Type[] elements, FilePosition declaredAt){
            super(name==null?getName(elements):name, false);
            this.elements=elements;
            this.declaredAt = declaredAt;
            named=name!=null;
        }

        @Override
        Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newElements=new Type[elements.length];
            boolean isGeneric=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(genericParams, genericArgs);
                if(newElements[i]!=elements[i]){
                    isGeneric=true;
                }
            }
            return isGeneric?new Tuple(named?name:null,newElements,declaredAt):this;
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
        public String name() {
            return name;
        }
        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.TUPLE;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
    }
    public static class GenericTuple extends Tuple{
        final Type[] genericArgs;

        public static GenericTuple create(String name,GenericParameter[] genericParams,Type[] genericArgs,Type[] elements, FilePosition declaredAt) {
            //Unwrap generic arguments
            for(int i=0;i< elements.length;i++){
                elements[i]=elements[i].replaceGenerics(genericParams,genericArgs);
            }
            StringBuilder sb=new StringBuilder();
            for(Type t:genericArgs){
                sb.append(t.name).append(" ");
            }
            return new GenericTuple(sb.append(name).toString(), genericParams, elements, declaredAt);
        }
        private GenericTuple(String name, Type[] genericArgs,Type[] elements, FilePosition declaredAt) {
            super(name, elements, declaredAt);
            this.genericArgs=genericArgs;
        }

        @Override
        Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newElements=new Type[elements.length];
            boolean isGeneric=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(genericParams, genericArgs);
                if(newElements[i]!=elements[i]){
                    isGeneric=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(genericParams, genericArgs);
                if(newArgs[i]!=this.genericArgs[i]){
                    isGeneric=true;
                }
            }
            return isGeneric?new GenericTuple(name,newArgs,newElements,declaredAt):this;
        }
    }

    public static class Procedure extends Type{
        final Type[] inTypes;
        final Type[] outTypes;
        private final Value insValue,outsValue;

        public static Type create(Type[] inTypes,Type[] outTypes){
            StringBuilder name=new StringBuilder("( ");
            for(Type t:inTypes){
                name.append(t.name).append(' ');
            }
            name.append("=> ");
            for(Type t:outTypes){
                name.append(t.name).append(' ');
            }
            name.append(")");
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
        Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newIns=new Type[inTypes.length];
            boolean isGeneric=false;
            for(int i=0;i<inTypes.length;i++){
                newIns[i]=inTypes[i].replaceGenerics(genericParams, genericArgs);
                if(newIns[i]!=inTypes[i]){
                    isGeneric=true;
                }
            }
            Type[] newOuts=new Type[outTypes.length];
            for(int i=0;i<this.outTypes.length;i++){
                newOuts[i]=this.outTypes[i].replaceGenerics(genericParams, genericArgs);
                if(newOuts[i]!=this.outTypes[i]){
                    isGeneric=true;
                }
            }
            return isGeneric?Procedure.create(inTypes,outTypes):this;
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
            return t== UNTYPED_PROCEDURE ||super.isSubtype(t);
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


    public static class Enum extends Type implements Interpreter.NamedDeclareable {
        final FilePosition declaredAt;
        final String[] entryNames;
        final Value.EnumEntry[] entries;
        public Enum(String name, String[] entryNames, ArrayList<FilePosition> entryPositions,
                    FilePosition declaredAt) {
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
        public String name() {
            return name;
        }
        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.ENUM;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
    }

    public static class NativeType extends Type{
        public final Class<?> jClass;
        NativeType(String name, Class<?> jClass) {
            super(name, false);
            this.jClass = jClass;
        }
    }

    public static class GenericParameter extends Type implements Interpreter.Declareable {
        final int id;
        final FilePosition declaredAt;
        public GenericParameter(int id , FilePosition pos) {
            super("'"+id,false);
            this.id=id;
            this.declaredAt=pos;
        }

        @Override
        Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            for(int i=0;i<genericParams.length;i++){
                if(this==genericParams[i])
                    return genericArgs[i];
            }
            return this;
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.GENERIC;
        }

        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
    }
}