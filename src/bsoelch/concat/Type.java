package bsoelch.concat;

import java.util.Objects;

public class Type {
    public static final Type INT = new Type("int");
    public static final Type CHAR = new Type("char");
    public static final Type FLOAT = new Type("float");
    public static final Type TYPE = new Type("type");
    public static final Type BOOL = new Type("bool");
    public static final Type BYTE = new Type("byte");
    //TODO (optional) type signatures for procedures
    public static final Type PROCEDURE = new Type("*->*");
    public static final Type MODULE = new Type("(module)");

    public static final Type FILE = new Type("(file)");

    //TODO types for compressed lists (string -> compressed char list, bytes -> compressed byte list)
    public static final Type GENERIC_LIST  = new Type("(list)");
    public static final Type GENERIC_TUPLE = new Type("(tuple)");

    /**blank type that could contain any value*/
    public static final Type ANY = new Type("var") {};

    public static Type STRING() {
        return ListType.STRING;
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
        if (contentType == CHAR) {
            return ListType.STRING;
        } else {
            return new ListType(contentType);
        }
    }
    private static class ListType extends Type {
        static final Type STRING      = new ListType(Type.CHAR);

        final Type contentType;

        ListType(Type contentType) {
            super(contentType.name+" list");
            this.contentType = contentType;
        }

        public boolean isList() {
            return true;
        }

        @Override
        public boolean isSubtype(Type t) {
            if(t instanceof ListType){
                return content().isSubtype(t.content());
            }else{
                return t==GENERIC_LIST||super.isSubtype(t);
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
            ListType that = (ListType) o;
            return Objects.equals(contentType, that.contentType);
        }
        @Override
        public int hashCode() {
            return contentType.hashCode();
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
    }

}