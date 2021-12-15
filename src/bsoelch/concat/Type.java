package bsoelch.concat;

import java.util.Arrays;
import java.util.Objects;

public class Type {

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
    public boolean isProcedure() {
        return false;
    }

    public static final Type INT = new Type("int");
    public static final Type CHAR = new Type("char");
    public static final Type FLOAT = new Type("float");
    public static final Type TYPE = new Type("type");
    public static final Type BOOL = new Type("bool");
    /*fallback type for procedures with unknown signature*/
    static final Type ANONYMOUS_PROCEDURE = new Type("? ? ->") ;

    public static Type STRING() {
        return ListType.STRING;
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

    public static Type procedureOf(Type[] ins,Type[] outs){
        return new ProcedureType(ins, outs);
    }
    private static class ProcedureType extends Type {
        final Type[] ins,outs;

        private static String getName(Type[] ins, Type[] outs) {
            return Arrays.stream(ins).map(Type::toString).reduce("",(s,t)->(s+" "+t))+" "+
                   Arrays.stream(outs).map(Type::toString).reduce("",(s,t)->(s+" "+t))+" "+
                    ins.length+" "+outs.length+" ->";
        }

        ProcedureType(Type[] ins, Type[] outs) {
            super(getName(ins,outs));
            this.ins = ins;
            this.outs = outs;
        }

        public boolean isProcedure() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcedureType that = (ProcedureType) o;
            return Arrays.equals(ins, that.ins) && Arrays.equals(outs, that.outs);
        }
        @Override
        public int hashCode() {
            int result = Arrays.hashCode(ins);
            result = 31 * result + Arrays.hashCode(outs);
            return result;
        }
    }
}