package bsoelch.concat;

import java.util.Arrays;
import java.util.Objects;

public class Type {
    public static final Type INT = new Type("int");
    public static final Type CODEPOINT = new Type("codepoint");
    public static final Type FLOAT = new Type("float");
    public static final Type TYPE  = new Type("type");
    public static final Type BOOL  = new Type("bool");
    public static final Type BYTE  = new Type("byte");

    public static final Type FILE = new Type("(file)");

    public static final Type GENERIC_LIST      = new Type("(list)");
    public static final Type GENERIC_OPTIONAL  = new Type("(optional)");
    public static final Type GENERIC_TUPLE     = new Type("(tuple)");
    public static final Type GENERIC_PROCEDURE = new Type("*->*");

    /**blank type that could contain any value*/
    public static final Type ANY = new Type("var") {};

    public static Type UNICODE_STRING() {
        return WrapperType.UNICODE_STRING;
    }
    public static Type BYTES() {
        return WrapperType.BYTES;
    }

    public Type(String name) {
        this.name = name;
    }
    private final String name;


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
    public Type content() {
        throw new UnsupportedOperationException();
    }

    public static Type listOf(Type contentType) {
        if (contentType == CODEPOINT) {
            return WrapperType.UNICODE_STRING;
        } else if (contentType == BYTE) {
            return WrapperType.BYTES;
        } else {
            return new WrapperType(WrapperType.LIST,contentType);
        }
    }
    public static Type varArg(Type contentType) {
        return new WrapperType(WrapperType.VAR_ARG,contentType);
    }
    public static Type optionalOf(Type contentType) {
        return new WrapperType(WrapperType.OPTIONAL,contentType);
    }

    private static class WrapperType extends Type {
        static final String LIST = "list";
        static final String VAR_ARG = "...";
        static final String OPTIONAL = "optional";

        static final Type UNICODE_STRING = new WrapperType(LIST,Type.CODEPOINT);
        static final Type BYTES  = new WrapperType(LIST,Type.BYTE);


        final Type contentType;
        final String wrapperName;

        WrapperType(String wrapperName, Type contentType){
            super(contentType.name+" "+ wrapperName);
            this.wrapperName = wrapperName;
            this.contentType = contentType;
            //TODO VAR_ARG cannot be part of composite types
        }

        public boolean isList() {
            return true;
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

    public static class Tuple extends Type{
        public static Tuple create(Type[] elements){
            StringBuilder name=new StringBuilder();
            for(Type t:elements){
                name.append(t.name).append(' ');
            }
            return new Tuple(name.append(elements.length).append(" tuple").toString(),elements);
        }
        final Type[] elements;
        private Tuple(String name,Type[] elements) {
            super(name);
            this.elements=elements;
        }

        @Override
        public boolean isSubtype(Type t) {
            if(t instanceof Tuple&&((Tuple) t).elementCount()==elementCount()){
                for(int i=0;i<elements.length;i++){
                    if(!elements[i].isSubtype(((Tuple) t).elements[i])){
                        return false;
                    }
                }
                return true;
            }else{
                return t==GENERIC_TUPLE||super.isSubtype(t);
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
    }

    public static class Procedure extends Type{
        Type[] inTypes;
        Type[] outTypes;
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
            super(name);
            this.inTypes=inTypes;
            this.outTypes=outTypes;
        }
        //addLater? possibility to access elements


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

}