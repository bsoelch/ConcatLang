package bsoelch.concat;

import java.util.Objects;

public class Type {
    public static final Type INT = new Type("int");
    public static final Type CHAR = new Type("char");
    public static final Type FLOAT = new Type("float");
    public static final Type TYPE = new Type("type");
    public static final Type BOOL = new Type("bool");
    public static final Type BYTE = new Type("byte");
    public static final Type PROCEDURE = new Type("*->*");
    public static final Type STRUCT = new Type("(struct)");

    public static final Type GENERIC_LIST = new Type("(list)");

    /**blank type that could contain any value*/
    public static final Type ANY = new Type("var") {
        @Override
        public boolean canAssignFrom(Type source) {
            return true;
        }
    };

    public static Type STRING() {
        return ContainerType.STRING;
    }

    public Type(String name) {
        this.name = name;
    }
    private final String name;


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
    public boolean canAssignFrom(Type source){
        return this==source;
    }

    public static Type listOf(Type contentType) {
        if (contentType == CHAR) {
            return ContainerType.STRING;
        } else {
            return new ContainerType(ContainerType.LIST,contentType);
        }
    }

    public static Type streamOf(Type contentType) {
        if (contentType == CHAR) {
            return ContainerType.BYTE_STREAM;
        } else {
            return new ContainerType(ContainerType.STREAM,contentType);
        }
    }

    private static class ContainerType extends Type {
        public static final String LIST = "list";
        public static final String STREAM = "stream";
        static final Type STRING      = new ContainerType(LIST,Type.CHAR);
        static final Type BYTE_STREAM = new ContainerType(STREAM,Type.BYTE);

        final String containerType;
        final Type contentType;

        ContainerType(String containerType, Type contentType) {
            super(contentType.name + " "+containerType);
            this.containerType = containerType;
            this.contentType = contentType;
        }

        public boolean isList() {
            return true;
        }

        @Override
        public boolean canAssignFrom(Type source) {
            if(source instanceof ContainerType&&((ContainerType) source).containerType.equals(containerType)){
                return contentType.canAssignFrom(((ContainerType) source).contentType);
            }else{
                return super.canAssignFrom(source);
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
            ContainerType that = (ContainerType) o;
            return Objects.equals(containerType, that.containerType) && Objects.equals(contentType, that.contentType);
        }
        @Override
        public int hashCode() {
            return Objects.hash(containerType, contentType);
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

        public int elementCount(){
            return elements.length;
        }
        public Type get(int i) {
            return elements[i];
        }
    }

}