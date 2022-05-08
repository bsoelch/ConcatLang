package bsoelch.concat;

import java.io.IOException;

@SuppressWarnings("UnusedReturnValue")//all methods return this object for chaining
public interface CodeGenerator {
    CodeGenerator indent();
    CodeGenerator dedent();
    CodeGenerator changeStackPointer(int k) throws IOException;

    CodeGenerator lineComment(String str) throws IOException;
    CodeGenerator blockComment(String str) throws IOException;

    CodeGenerator startLine() throws IOException;
    CodeGenerator pushPrimitive(BaseType.StackValue target) throws IOException;
    default CodeGenerator pushPrimitive(Type target) throws IOException {
        if(!(target.baseType() instanceof BaseType.StackValue))
            throw new IllegalArgumentException("type "+target+" is not a primitive type");
        return pushPrimitive((BaseType.StackValue) target.baseType());
    }
    CodeGenerator pushPointer(Type target) throws IOException;
    CodeGenerator assignPrimitive(int offset, BaseType.StackValue target) throws IOException;
    default CodeGenerator assignPrimitive(int offset,Type target) throws IOException {
        if(!(target.baseType() instanceof BaseType.StackValue))
            throw new IllegalArgumentException("type "+target+" is not a primitive type");
        return assignPrimitive(offset,(BaseType.StackValue) target.baseType());
    }
    CodeGenerator assignPointer(int offset, Type target, boolean assignValue) throws IOException;

    CodeGenerator popPrimitive(BaseType.StackValue type) throws IOException;
    default CodeGenerator popPrimitive(Type target) throws IOException {
        if(!(target.baseType() instanceof BaseType.StackValue))
            throw new IllegalArgumentException("type "+target+" is not a primitive type");
        return popPrimitive((BaseType.StackValue) target.baseType());
    }

    CodeGenerator getRaw(int offset) throws IOException;
    CodeGenerator getPrimitive(int offset, BaseType.StackValue type) throws IOException;
    default CodeGenerator getPrimitive(int offset,Type target) throws IOException {
        if(!(target.baseType() instanceof BaseType.StackValue))
            throw new IllegalArgumentException("type "+target+" is not a primitive type");
        return getPrimitive(offset,(BaseType.StackValue) target.baseType());
    }
    CodeGenerator getPrimitiveAs(int offset, BaseType.StackValue src, BaseType.StackValue target) throws IOException;
    default CodeGenerator getPrimitiveAs(int offset,Type src,Type target) throws IOException {
        if(!(target.baseType() instanceof BaseType.StackValue))
            throw new IllegalArgumentException("type "+target+" is not a primitive type");
        if(!(src.baseType() instanceof BaseType.StackValue))
            throw new IllegalArgumentException("type "+src+" is not a primitive type");
        return getPrimitiveAs(offset,(BaseType.StackValue) src.baseType(),(BaseType.StackValue) target.baseType());
    }
    CodeGenerator getPointer(int offset, Type type) throws IOException;

    CodeGenerator appendInt(long value,int bits, boolean signed) throws IOException;
    CodeGenerator appendBool(boolean value) throws IOException;
    CodeGenerator appendType(Type value) throws IOException;

    CodeGenerator append(String s) throws IOException;

    CodeGenerator newLine() throws IOException;

    CodeGenerator endLine() throws IOException;
}
