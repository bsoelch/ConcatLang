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
    CodeGenerator pushPrimitive(Type target) throws IOException;
    CodeGenerator pushReference(Type target) throws IOException;
    CodeGenerator pushPtr() throws IOException;
    CodeGenerator pushFPtr() throws IOException;
    CodeGenerator assignPrimitive(int offset, Type target) throws IOException;
    CodeGenerator assignReference(int offset, Type target, boolean assignValue) throws IOException;

    CodeGenerator popPrimitive(Type type) throws IOException;
    CodeGenerator popPtr() throws IOException;
    CodeGenerator popFPtr() throws IOException;

    CodeGenerator getRaw(int offset) throws IOException;
    CodeGenerator getPrimitive(int offset, Type type) throws IOException;
    CodeGenerator getReference(int offset, Type type) throws IOException;
    CodeGenerator getPrimitiveAs(int offset, Type src, Type target) throws IOException;
    CodeGenerator getFPtr(int offset) throws IOException;
    CodeGenerator getPtr(int offset) throws IOException;
    CodeGenerator appendInt(long value,boolean signed) throws IOException;
    CodeGenerator appendCodepoint(int value) throws IOException;
    CodeGenerator appendByte(byte value) throws IOException;
    CodeGenerator appendBool(boolean value) throws IOException;
    CodeGenerator appendType(Type value) throws IOException;
    CodeGenerator append(String s) throws IOException;

    CodeGenerator newLine() throws IOException;

    CodeGenerator endLine() throws IOException;
}
