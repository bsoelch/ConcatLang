package bsoelch.concat;

import java.util.Objects;

public class Type {
    public static final Type INT = new Type("int");
    public static final Type CHAR = new Type("char");
    public static final Type FLOAT = new Type("float");
    public static final Type TYPE = new Type("type");
    public static final Type BOOL = new Type("bool");
    /**blank type that could contain any value*/
    private static final Type ANY = new Type("var");
    /**fallback type for procedures with unknown signature*/
    public static final Type PROCEDURE = new Type("*->*");

    public static Type STRING() {
        return ListType.STRING;
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


    public static Type listOf(Type contentType) {
        if (contentType == CHAR) {
            return ListType.STRING;
        } else {
            return new ListType(contentType);
        }
    }

    private static class ListType extends Type {
        static final Type STRING = new ListType(Type.CHAR);

        final Type contentType;

        ListType(Type contentType) {
            super(contentType.name + " list");
            this.contentType = contentType;
        }

        public boolean isList() {
            return true;
        }

        @Override
        public Type content() {
            return contentType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListType listType = (ListType) o;
            return Objects.equals(contentType, listType.contentType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contentType);
        }
    }
}