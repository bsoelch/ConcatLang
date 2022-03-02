package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Type {
    public static final Type INT   = new Type("int",  true) {
        @Override
        public boolean canCastTo(Type t) {
            return t==UINT||t==CODEPOINT||t==BYTE||t==FLOAT||super.canCastTo(t);
        }
    };
    public static final Type UINT  = new Type("uint", true) {
        @Override
        public boolean canCastTo(Type t) {
            return t==INT||t==CODEPOINT||t==BYTE||t==FLOAT||super.canCastTo(t);
        }
    };
    public static final Type FLOAT = new Type("float",false) {
        @Override
        public boolean canCastTo(Type t) {
            return t==INT||t==UINT||super.canCastTo(t);
        }
    };
    public static final Type CODEPOINT = new Type("codepoint", true) {
        @Override
        public boolean canCastTo(Type t) {
            return t==INT||t==UINT||t==BYTE||super.canCastTo(t);
        }
    };
    public static final Type BYTE  = new Type("byte", true){
        @Override
        public boolean canCastTo(Type t) {
            return t==INT||t==UINT||t==CODEPOINT||super.canCastTo(t);
        }
    };
    public static final Type TYPE  = new Type("type", false);
    public static final Type BOOL  = new Type("bool", false);

    //addLater remove untyped ... types
    public static final Type UNTYPED_LIST      = new Type("(list)", false) {
        @Override
        public boolean isList() {
            return true;
        }
        @Override
        public Type content() {
            return ANY;
        }
    };
    public static final Type UNTYPED_OPTIONAL = new Type("(optional)", false) {
        @Override
        public boolean isOptional() {
            return true;
        }
        @Override
        public Type content() {
            return ANY;
        }
    };
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

    /**@return true if this type is a subtype of type t*/
    public boolean isSubtype(Type t){
        return equals(t)||t==ANY;
    }
    /**@return true if values of this type can be cast to type t*/
    public boolean canCastTo(Type t){//TODO casting of generic types
        return isSubtype(t)||t.isSubtype(this);
    }

    @Override
    public String toString() {
        return name;
    }
    Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
        return this;
    }

    public boolean isList() {
        return false;
    }
    public boolean isOptional() {
        return false;
    }
    public Type content() {//addLater make type-data getters return optional
        throw new UnsupportedOperationException();
    }
    public List<Type> inTypes(){
        throw new UnsupportedOperationException();
    }
    public List<Type> outTypes(){
        throw new UnsupportedOperationException();
    }
    public String typeName(){
        throw new UnsupportedOperationException();
    }
    public List<String> fields(){
        throw new UnsupportedOperationException();
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
        public boolean canCastTo(Type t) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().canCastTo(t.content());
            }else{
                return (wrapperName.equals(LIST)&&t==UNTYPED_LIST)||(wrapperName.equals(OPTIONAL)&&t== UNTYPED_OPTIONAL)||
                        super.canCastTo(t);
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
        public List<Type> outTypes() {
            return Arrays.asList(elements);
        }
        @Override
        public String typeName() {
            return name;
        }

        @Override
        Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(genericParams, genericArgs);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            return changed?new Tuple(named?name:null,newElements,declaredAt):this;
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
        @Override
        public boolean canCastTo(Type t) {
            if(t instanceof Tuple){
                int n=Math.min(elements.length,((Tuple) t).elements.length);
                for(int i=0;i<n;i++){
                    if(!elements[i].canCastTo(((Tuple) t).elements[i])){
                        return false;
                    }
                }
                return true;
            }else{
                return super.canCastTo(t);
            }
        }

        public int elementCount(){
            return elements.length;
        }
        public Type get(long i) throws ConcatRuntimeError {
            if(i<0||i>=elements.length){
                throw new ConcatRuntimeError("tuple index out of bounds: "+i+" length:"+elements.length);
            }
            return elements[(int) i];
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
        final GenericParameter[] params;
        final String tupleName;

        public static GenericTuple create(String name,GenericParameter[] genericParams,Type[] genericArgs,Type[] elements, FilePosition declaredAt) {
            //Unwrap generic arguments
            for(int i=0;i< elements.length;i++){
                elements[i]=elements[i].replaceGenerics(genericParams,genericArgs);
            }
            StringBuilder sb=new StringBuilder();
            for(Type t:genericArgs){
                sb.append(t.name).append(" ");
            }
            return new GenericTuple(name,sb.append(name).toString(),genericParams, genericArgs, elements, declaredAt);
        }
        private GenericTuple(String baseName,String name,GenericParameter[] genericParams,Type[] genericArgs,Type[] elements, FilePosition declaredAt) {
            super(name, elements, declaredAt);
            tupleName=baseName;
            this.params=genericParams;
            this.genericArgs=genericArgs;
        }

        @Override
        Type replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(genericParams, genericArgs);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(genericParams, genericArgs);
                if(newArgs[i]!=this.genericArgs[i]){
                    changed=true;
                }
            }
            return changed?create(tupleName,params,newArgs,newElements,declaredAt):this;
        }
    }

    public static class Procedure extends Type{
        final Type[] inTypes;
        final Type[] outTypes;


        static StringBuilder getName(Type[] inTypes, Type[] outTypes) {
            StringBuilder name=new StringBuilder("( ");
            for(Type t: inTypes){
                name.append(t.name).append(' ');
            }
            name.append("=> ");
            for(Type t: outTypes){
                name.append(t.name).append(' ');
            }
            name.append(")");
            return name;
        }
        public static Procedure create(Type[] inTypes,Type[] outTypes){
            StringBuilder name = getName(inTypes, outTypes);
            return new Procedure(name.toString(),inTypes,outTypes);
        }

        private Procedure(String name,Type[] inTypes,Type[] outTypes) {
            super(name, false);
            this.inTypes=inTypes;
            this.outTypes=outTypes;
        }

        @Override
        public List<Type> inTypes() {
            return Arrays.asList(inTypes);
        }
        @Override
        public List<Type> outTypes() {
            return Arrays.asList(outTypes);
        }

        @Override
        Procedure replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newIns=new Type[inTypes.length];
            boolean changed=false;
            for(int i=0;i<inTypes.length;i++){
                newIns[i]=inTypes[i].replaceGenerics(genericParams, genericArgs);
                if(newIns[i]!=inTypes[i]){
                    changed=true;
                }
            }
            Type[] newOuts=new Type[outTypes.length];
            for(int i=0;i<this.outTypes.length;i++){
                newOuts[i]=this.outTypes[i].replaceGenerics(genericParams, genericArgs);
                if(newOuts[i]!=this.outTypes[i]){
                    changed=true;
                }
            }
            return changed?Procedure.create(newIns,newOuts):this;
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
    public static class GenericProcedure extends Procedure {
        final GenericParameter[] params;
        final Type[] genericArgs;

        public static GenericProcedure create(GenericParameter[] genericParams,Type[] genericArgs,Type[] inTypes,Type[] outTypes) {
            //Unwrap generic arguments
            StringBuilder sb=new StringBuilder();
            for(Type t:genericArgs){
                sb.append(t.name).append(" ");
            }
            return new GenericProcedure(sb.append(getName(inTypes,outTypes)).toString(),genericParams,genericParams, inTypes,outTypes);
        }
        private GenericProcedure(String name,GenericParameter[] params,Type[] genericArgs,Type[] inTypes,Type[] outTypes) {
            super(name, inTypes,outTypes);
            this.params=params;
            this.genericArgs=genericArgs;
        }

        @Override
        Procedure replaceGenerics(GenericParameter[] genericParams, Type[] genericArgs) {
            Type[] newIn=new Type[inTypes.length];
            boolean changed=false;
            for(int i=0;i<inTypes.length;i++){
                newIn[i]=inTypes[i].replaceGenerics(genericParams, genericArgs);
                if(newIn[i]!=inTypes[i]){
                    changed=true;
                }
            }
            Type[] newOut=new Type[outTypes.length];
            for(int i=0;i<outTypes.length;i++){
                newOut[i]=outTypes[i].replaceGenerics(genericParams, genericArgs);
                if(newOut[i]!=outTypes[i]){
                    changed=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(genericParams, genericArgs);
                if(newArgs[i]!=this.genericArgs[i]){
                    changed=true;
                }
            }
            return changed?new GenericProcedure(name,params,newArgs,newIn,newOut):this;
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
        public boolean canCastTo(Type t) {
            return t==UINT||t==INT||super.canCastTo(t);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String typeName() {
            return name;
        }
        @Override
        public List<String> fields() {
            return Arrays.asList(entryNames);
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
        @Override
        public String typeName() {
            return name;
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