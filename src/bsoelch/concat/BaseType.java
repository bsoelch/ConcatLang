package bsoelch.concat;

import java.util.*;

public abstract class BaseType {
    public abstract int blockCount();
    public abstract BaseType[] blocks();
    public BaseType pointerTo() {
        throw new UnsupportedOperationException("pointerTo() is not supported for internal base types");
    }

    public enum PrimitiveType {BOOL, I8, U8, I16, U16, I32, U32, I64, U64, FLOAT, TYPE}

    public static abstract class StackValue extends BaseType{
        private static final ArrayList<StackValue> stackValues = new ArrayList<>();

        public static StackValue F_PTR = new StackValue("procPtr") {};
        public static StackValue PTR = new StackValue("any") {
            @Override
            public BaseType pointerTo() {
                return this;
            }
        };


        public static List<StackValue> values(){
            return Collections.unmodifiableList(stackValues);
        }
        final String name;
        StackValue(String name){
            this.name = name;
            stackValues.add(this);
        }

        @Override
        public final int blockCount() {
            return 1;
        }

        @Override
        public final BaseType[] blocks() {
            return new BaseType[]{this};
        }
    }
    public static class Primitive extends StackValue {
        private static final HashMap<PrimitiveType,Primitive> primitives = new HashMap<>();
        private static final HashMap<PrimitiveType,Primitive> primitivePointers = new HashMap<>();

        public final PrimitiveType type;
        public final boolean isPtr;

        public static Primitive get(PrimitiveType type){
            Primitive prev=primitives.get(type);
            if(prev!=null)
                return prev;
            Primitive asPtr=null;
            switch (type){
                case I8  -> {
                    asPtr=new Int(type,true,8,false);
                    prev =new Int(type,false,8,false);
                }
                case U8  -> {
                    asPtr=new Int(type,true,8,true);
                    prev =new Int(type,false,8,true);
                }
                case I16  -> {
                    asPtr=new Int(type,true,16,false);
                    prev =new Int(type,false,16,false);
                }
                case U16  -> {
                    asPtr=new Int(type,true,16,true);
                    prev =new Int(type,false,16,true);
                }
                case I32  -> {
                    asPtr=new Int(type,true,32,false);
                    prev =new Int(type,false,32,false);
                }
                case U32  -> {
                    asPtr=new Int(type,true,32,true);
                    prev =new Int(type,false,32,true);
                }
                case I64  -> {
                    asPtr=new Int(type,true,64,false);
                    prev =new Int(type,false,64,false);
                }
                case U64  -> {
                    asPtr=new Int(type,true,64,true);
                    prev =new Int(type,false,64,true);
                }
                case BOOL,TYPE,FLOAT -> {
                   asPtr=new Primitive(type, true);
                   prev=new Primitive(type, false);
                }
            }
            primitives.put(type,prev);
            primitivePointers.put(type,asPtr);
            return prev;
        }
        private Primitive(PrimitiveType type, boolean isPtr) {
            super(type.name().toLowerCase()+(isPtr?"Ptr":""));
            this.type = type;
            this.isPtr = isPtr;
        }
        public static class Int extends Primitive{
            public final int bitCount;
            public final boolean unsigned;
            private Int(PrimitiveType type, boolean isPtr, int bitCount, boolean unsigned) {
                super(type, isPtr);
                this.bitCount = bitCount;
                this.unsigned = unsigned;
            }
        }

        @Override
        public BaseType pointerTo() {
            if(isPtr)
                return super.pointerTo();
            return primitivePointers.get(type);
        }
    }

    public static Composite arrayOf(BaseType content){
        return new BaseType.Composite(content.pointerTo(),BaseType.Primitive.get(BaseType.PrimitiveType.U64));// data,len
    }
    public static BaseType optionalOf(BaseType content){
        return StackValue.PTR;//TODO special handling for optionals
    }

    public static class Composite extends BaseType{
        private final BaseType[] blocks;

        public static final Composite PROC_POINTER =
                new BaseType.Composite(BaseType.StackValue.F_PTR,BaseType.StackValue.PTR);/*ptr,curried*/

        public Composite(BaseType... blocks) {
            this.blocks = blocks;
        }

        @Override
        public int blockCount() {
            return Arrays.stream(blocks).mapToInt(BaseType::blockCount).sum();
        }

        @Override
        public BaseType[] blocks() {
            return blocks;
        }

        @Override
        public BaseType pointerTo() {
            //TODO BaseType.pointerTo
            return this;
        }
    }
}
