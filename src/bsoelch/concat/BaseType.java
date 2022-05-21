package bsoelch.concat;

import java.util.*;

public abstract class BaseType {
    public abstract int blockCount();
    public abstract StackValue[] blocks();
    public StackValue pointerTo() {
        throw new UnsupportedOperationException("pointerTo() is not supported for internal base types");
    }

    public enum PrimitiveType {BOOL, INTEGER, FLOAT, TYPE, OPTIONAL_I32,OPTIONAL_U32}

    public static abstract class StackValue extends BaseType{
        private static final ArrayList<StackValue> stackValues = new ArrayList<>();

        public static StackValue F_PTR = new StackValue("procPtr", Compiler.CTYPE_FPTR) {};
        public static StackValue PTR = new StackValue("any", Compiler.STACK_DATA_TYPE+"*") {
            @Override
            public StackValue pointerTo() {
                return this;
            }
        };


        public static List<StackValue> values(){
            return Collections.unmodifiableList(stackValues);
        }
        /**name of the base type*/
        final String name;
        /**name of the target-type in compiled code*/
        final String cType;
        StackValue(String name, String cType){
            this.name = name;
            this.cType = cType;
            stackValues.add(this);
        }

        @Override
        public final int blockCount() {
            return 1;
        }

        @Override
        public final StackValue[] blocks() {
            return new StackValue[]{this};
        }
    }
    record IntId(int bits,boolean unsigned){}
    public static class Primitive extends StackValue {
        private static final HashMap<PrimitiveType,Primitive> primitives = new HashMap<>();
        private static final HashMap<PrimitiveType,Primitive> primitivePointers = new HashMap<>();
        private static final HashMap<IntId,Primitive> integers = new HashMap<>();
        private static final HashMap<IntId,Primitive> integerPointers = new HashMap<>();

        static Primitive OPTIONAL_I32 = get(PrimitiveType.OPTIONAL_I32);
        static Primitive OPTIONAL_U32 = get(PrimitiveType.OPTIONAL_U32);


        public final PrimitiveType type;
        public final boolean isPtr;

        public static Primitive get(PrimitiveType type){
            Primitive prev=primitives.get(type);
            if(prev!=null)
                return prev;
            Primitive asPtr=null;
            switch (type){
                case INTEGER ->
                    throw new UnsupportedOperationException("use getInt for integral primitives");
                case BOOL -> {
                    asPtr=new Primitive(type, true, "bool", "bool");
                    prev=new Primitive(type, false, "bool", "bool");
                }
                case TYPE -> {
                    asPtr=new Primitive(type, true, "type", Compiler.CTYPE_TYPE);
                    prev=new Primitive(type, false, "type", Compiler.CTYPE_TYPE);
                }
                case FLOAT -> {
                   asPtr=new Primitive(type, true, "float", Compiler.CTYPE_FLOAT);
                   prev=new Primitive(type, false, "float", Compiler.CTYPE_FLOAT);
                }
                case OPTIONAL_I32 ->{
                    asPtr=new Primitive(type, true, "optionalI32", Compiler.CTYPE_OPTIONAL_I32);
                    prev=new Primitive(type, false, "optionalI32", Compiler.CTYPE_OPTIONAL_I32);
                }
                case OPTIONAL_U32 ->{
                    asPtr=new Primitive(type, true, "optionalU32", Compiler.CTYPE_OPTIONAL_U32);
                    prev=new Primitive(type, false, "optionalU32", Compiler.CTYPE_OPTIONAL_U32);
                }
            }
            primitives.put(type,prev);
            primitivePointers.put(type,asPtr);
            return prev;
        }
        public static Primitive getInt(int bits,boolean unsigned){
            IntId id=new IntId(bits, unsigned);
            Primitive prev=integers.get(id);
            if(prev!=null)
                return prev;
            prev=new Int(bits, unsigned,false);
            Primitive asPtr=new Int(bits, unsigned,true);
            integers.put(id,prev);
            integerPointers.put(id,asPtr);
            return prev;
        }

        private Primitive(PrimitiveType type, boolean isPtr,String name,String cType) {
            super(name+(isPtr?"Ptr":""), cType+(isPtr?"*":""));
            this.type = type;
            this.isPtr = isPtr;
        }
        public static class Int extends Primitive{
            public final int bitCount;
            public final boolean unsigned;
            private Int(int bitCount, boolean unsigned,boolean isPtr) {
                super(PrimitiveType.INTEGER, isPtr,(unsigned?"U":"I")+bitCount,(unsigned?"uint":"int")+bitCount+"_t");
                this.bitCount = bitCount;
                this.unsigned = unsigned;
            }

            @Override
            public StackValue pointerTo() {
                if(isPtr)
                    return super.pointerTo();
                return integerPointers.get(new IntId(bitCount,unsigned));
            }
        }

        @Override
        public StackValue pointerTo() {
            if(isPtr)
                return super.pointerTo();
            return primitivePointers.get(type);
        }
    }

    public static Composite arrayOf(BaseType content){
        return new BaseType.Composite(content.pointerTo(),BaseType.Primitive.getInt(64,true));// data,len
    }
    public static BaseType optionalOf(Type content){
        BaseType contentBase= content.baseType();
        if(contentBase instanceof Primitive.Int&&!((Primitive.Int) contentBase).isPtr&&((Primitive.Int) contentBase).bitCount<=32){
            return ((Primitive.Int) contentBase).unsigned?Primitive.OPTIONAL_U32:Primitive.OPTIONAL_I32;
        }else if(contentBase==Primitive.OPTIONAL_I32||contentBase==Primitive.OPTIONAL_U32) {
            return contentBase;//nested optionals
        }
        //TODO special handling for optionals of non-null pointers
        while(content.isOptional()&&contentBase!=StackValue.PTR){//don't unwrap if base type is (nullable) pointer
            content=content.content();
            contentBase=content.baseType();
        }
        ArrayList<StackValue> elements=new ArrayList<>(Arrays.asList(contentBase.blocks()));
        elements.add(BaseType.Primitive.getInt(64,true));
        return new BaseType.Composite(elements.toArray(StackValue[]::new));// data,optional-count
    }

    public static class Composite extends BaseType{
        private final StackValue[] blocks;

        public static final Composite PROC_POINTER =
                new BaseType.Composite(BaseType.StackValue.F_PTR,BaseType.StackValue.PTR);/*ptr,curried*/

        public Composite(StackValue... blocks) {
            this.blocks = blocks;
        }

        @Override
        public int blockCount() {
            return Arrays.stream(blocks).mapToInt(BaseType::blockCount).sum();
        }

        @Override
        public StackValue[] blocks() {
            return blocks;
        }

        @Override
        public StackValue pointerTo() {
            return StackValue.PTR;
        }
    }
}
