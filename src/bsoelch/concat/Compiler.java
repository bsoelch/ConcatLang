package bsoelch.concat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Compiler {
    static final HashMap<Type,String> primitives =new HashMap<>();

    static{
        primitives.put(Type.BOOL,"bool");
        primitives.put(Type.BYTE,"uint8_t");
        primitives.put(Type.CODEPOINT,"uint32_t");
        primitives.put(Type.INT,"int64_t");
        primitives.put(Type.UINT,"uint64_t");
        primitives.put(Type.FLOAT,"float64_t");
        primitives.put(Type.TYPE,"type_t");
    }
    static final String CONCAT_PROC_OUT="void";
    static final String CONCAT_PROC_SIGNATURE="(Stack*, value_t*)";
    static final String STACK_ARG_NAME="stack";
    static final String CONCAT_PROC_NAMED_SIGNATURE="(Stack* "+STACK_ARG_NAME+", value_t* curried)";
    public static final String PUBLIC_PROC_PREFIX = "concat_public_procedure_";
    public static final String PRIVATE_PROC_PREFIX = "concat_private_procedure_";

    static final String STACK_FIELD_DATA="data";
    static final String STACK_DATA_TYPE="value_t";
    static final String STACK_FIELD_SIZE="size";
    static final String STACK_FIELD_CAPACITY="capacity";

    static final String DUP_VAR_NAME="dup_tmp";


    public static void compile(Parser.Program prog, File out) throws IOException {
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(out))){
            writeComment(writer,"compiled concat file");
            writer.newLine();
            printFileHeader(writer);//addLater import native procedures
            writeComment(writer,"type definitions");
            writer.newLine();
            // addLater type definitions
            writeComment(writer,"procedure definitions");
            writer.newLine();
            printProcedureDefinitions(writer,prog);
            writeComment(writer,"global variables");
            writer.newLine();
            // addLater global variables
            writeComment(writer,"procedure bodies");
            writer.newLine();
            printProcedureBodies(writer,prog);

            writer.newLine();
            writeLine(writer,"int main(){");
            //TODO determine stack capacity, execute global code
            writeLine(writer,1,STACK_DATA_TYPE+" "+STACK_FIELD_DATA+"[100];");//? allocate in heap
            writeLine(writer,1,"Stack "+STACK_ARG_NAME+" = {."+STACK_FIELD_DATA+" = "+STACK_FIELD_DATA+
                    ", ."+STACK_FIELD_SIZE+" =0, ."+STACK_FIELD_CAPACITY+" = 100};");
            Parser.Declareable main=prog.rootContext().getElement("main",true);
            writeLine(writer,1,PUBLIC_PROC_PREFIX+idOf(main)+"(&"+STACK_ARG_NAME+", NULL);");
            writeLine(writer,"}");
        }
    }

    private static void writeIndent(BufferedWriter writer, int indent) throws IOException {
        if(indent >0){
            writer.write("  ".repeat(indent));
        }
    }
    private static void writeLine(BufferedWriter writer,int indent,String line) throws IOException {
        writeIndent(writer, indent);
        writer.write(line);
        writer.newLine();
    }
    private static void writeComment(BufferedWriter writer,int indent,String comment) throws IOException {
        String[] parts=comment.split("[\n\r]");
        for(String line:parts){
            writeIndent(writer, indent);
            writer.write("// "+ line);
            writer.newLine();
        }
    }

    private static void writeLine(BufferedWriter writer,String line) throws IOException {
        writeLine(writer,0,line);
    }
    private static void writeComment(BufferedWriter writer,String line) throws IOException {
        writeComment(writer,0,line);
    }

    private static String typeWrapperName(Type primitive){
        return "as"+Character.toUpperCase(primitive.name.charAt(0))+primitive.name.substring(1);
    }
    private static void printFileHeader(BufferedWriter writer) throws IOException {
        //include required library files
        writeLine(writer,"#include \"stdbool.h\"");
        writeLine(writer,"#include \"inttypes.h\"");
        writeLine(writer,"#include \"stdlib.h\"");
        writeLine(writer,"#include \"stdio.h\"");
        writeLine(writer,"#include \"string.h\"");
        writer.newLine();
        writeLine(writer,"typedef union "+STACK_DATA_TYPE+"_Impl "+STACK_DATA_TYPE+";");
        writer.newLine();
        writeLine(writer,"typedef struct{");
        writeLine(writer,1,"size_t "+STACK_FIELD_CAPACITY+";");
        writeLine(writer,1,"size_t "+STACK_FIELD_SIZE+";");
        writeLine(writer,1,STACK_DATA_TYPE+"* "+STACK_FIELD_DATA+";");
        writeLine(writer,"}Stack;");
        writer.newLine();
        writeLine(writer,"typedef uint64_t type_t;");
        writeLine(writer,"typedef double float64_t;");
        writeLine(writer,"typedef "+CONCAT_PROC_OUT+"(*fptr_t)"+CONCAT_PROC_SIGNATURE+";");
        writeLine(writer,"typedef void* ptr_t;");
        writer.newLine();
        writeLine(writer,"union "+STACK_DATA_TYPE+"_Impl {");
        for(Map.Entry<Type, String> e:primitives.entrySet()){//addLater text alignment
            writeLine(writer,1,e.getValue()+"  "+typeWrapperName(e.getKey())+";");
            writeLine(writer,1,e.getValue()+"* "+typeWrapperName(e.getKey())+"Ref;");
        }
        writeLine(writer,1,"fptr_t  asFPtr;");
        writeLine(writer,1,"fptr_t* asFPtrRef;");
        writeLine(writer,1,"ptr_t   asPtr;");
        writeLine(writer,"};");
        writer.newLine();
    }

    private static String toCIdentifier(String name) {
        StringBuilder id=new StringBuilder(name.length());
        for(byte b:name.getBytes(StandardCharsets.UTF_8)){
            if((b>='A'&&b<='Z')||(b>='a'&&b<='z')||(b>='0'&&b<='9')){
                id.append((char)b);
            }else if(b=='_'){
                id.append("__");
            }else{
                id.append("0x").append(Integer.toHexString(b).toUpperCase()).append("_");
            }
        }
        return id.toString();
    }
    private static String idOf(Parser.Declareable dec) {
        FilePosition pos=dec.declaredAt();
        return toCIdentifier(pos.fileId)+"_"+pos.line+"_"+pos.posInLine;
    }

    //TODO overloaded procedures
    private static void printProcedureDefinitions(BufferedWriter writer, Parser.Program prog) throws IOException {
        for(Map.Entry<String, Parser.Declareable> dec:prog.rootContext().declareables()){
            if(dec.getValue().declarableType() == Parser.DeclareableType.PROCEDURE){
                writeComment(writer,"procedure "+dec.getKey()+" "+((Parser.Callable)dec.getValue()).type());
                writeLine(writer,CONCAT_PROC_OUT+ " " + PUBLIC_PROC_PREFIX +idOf(dec.getValue())
                        +CONCAT_PROC_NAMED_SIGNATURE+";");
            }
        }
        for(Map.Entry<Parser.PrivateDefinitionId, Parser.Declareable> dec:prog.rootContext().localDeclareables()){
            if(dec.getValue().declarableType() == Parser.DeclareableType.PROCEDURE){
                writeComment(writer,"procedure "+dec.getKey().nameInFile()+" "+((Parser.Callable)dec.getValue()).type()+
                        " in "+dec.getKey().fileId());
                writeLine(writer,CONCAT_PROC_OUT+ " " + PRIVATE_PROC_PREFIX +idOf(dec.getValue())
                        +CONCAT_PROC_NAMED_SIGNATURE+";");
            }
        }
        writer.newLine();
    }

    private static void printProcedureBodies(BufferedWriter writer, Parser.Program prog) throws IOException {
        for(Map.Entry<String, Parser.Declareable> dec:prog.rootContext().declareables()){
            if(dec.getValue().declarableType() == Parser.DeclareableType.PROCEDURE){
                writeComment(writer,"procedure "+dec.getKey()+" "+((Parser.Callable)dec.getValue()).type());
                writeLine(writer,CONCAT_PROC_OUT+ " " + PUBLIC_PROC_PREFIX +idOf(dec.getValue())
                        +CONCAT_PROC_NAMED_SIGNATURE+"{");
                compileCodeSection(writer,(Parser.CodeSection)dec.getValue());
                writeLine(writer,"}");
            }
        }
        for(Map.Entry<Parser.PrivateDefinitionId, Parser.Declareable> dec:prog.rootContext().localDeclareables()){
            if(dec.getValue().declarableType() == Parser.DeclareableType.PROCEDURE){
                writeComment(writer,"procedure "+dec.getKey().nameInFile()+" "+((Parser.Callable)dec.getValue()).type()+
                        " in "+dec.getKey().fileId());
                writeLine(writer,CONCAT_PROC_OUT+ " " + PRIVATE_PROC_PREFIX +idOf(dec.getValue())
                        +CONCAT_PROC_NAMED_SIGNATURE+"{");
                compileCodeSection(writer,(Parser.CodeSection)dec.getValue());
                writeLine(writer,"}");
            }
        }
        writer.newLine();
    }

    private static void compileCodeSection(BufferedWriter writer, Parser.CodeSection section) throws IOException {
        int level=1;
        boolean hasDupTmpVar=false;
        //TODO initialize local variables
        for(Parser.Token next:section.tokens()){
            writeComment(writer,level,next.toString());
            try {
                switch (next.tokenType) {
                    case NOP -> {
                    }
                    case LAMBDA, VALUE, GLOBAL_VALUE -> {
                        assert next instanceof Parser.ValueToken;
                        Value value = ((Parser.ValueToken) next).value;
                        if(primitives.containsKey(value.type)){
                            String prefix=STACK_ARG_NAME+"->"+STACK_FIELD_DATA+
                                    "[("+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+")++]."+typeWrapperName(value.type)+" = ";
                            if (value.type == Type.INT) {
                                writeLine(writer, level,prefix+value.asLong() + "LL;");
                            }else if (value.type == Type.UINT) {
                                writeLine(writer, level,prefix+value.asLong() + "ULL;");
                            }else if (value.type == Type.CODEPOINT) {
                                writeLine(writer, level,prefix+"0x"+Long.toHexString(value.asLong()) + ";");
                            }else if (value.type == Type.BYTE) {
                                writeLine(writer, level,prefix+"0x"+Integer.toHexString(value.asByte()) + ";");
                            }else {
                                throw new UnsupportedOperationException("values of type " + value.type + " are currently not supported");
                            }
                        }else {
                            throw new UnsupportedOperationException("values of type " + value.type + " are currently not supported");
                        }
                    }
                    case CALL_PROC -> {
                        assert next instanceof Parser.CallToken;
                        Value.Procedure called=(Value.Procedure) ((Parser.CallToken) next).called;
                        writeLine(writer, level,called.isPublic? PUBLIC_PROC_PREFIX : PRIVATE_PROC_PREFIX +idOf(called)+
                                "("+STACK_ARG_NAME+", NULL);");
                    }
                    case STACK_DUP -> {
                        writeLine(writer, level,(hasDupTmpVar?"":STACK_DATA_TYPE+" ")+DUP_VAR_NAME+
                                " = "+STACK_ARG_NAME+"->"+STACK_FIELD_DATA+"["+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+
                                "-"+((Parser.StackModifierToken)next).args[0]+"];");
                        writeLine(writer, level,
                                STACK_ARG_NAME+"->"+STACK_FIELD_DATA+"["+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"++] = "
                                        +DUP_VAR_NAME+";");
                        hasDupTmpVar=true;
                    }
                    case STACK_DROP -> {
                        int offset=((Parser.StackModifierToken)next).args[0];
                        int count=((Parser.StackModifierToken)next).args[1];
                        if(offset>0){
                            writeLine(writer, level,"memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_DATA+"+"+
                                    STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"-"+(offset+count)+","+
                                    STACK_ARG_NAME+"->"+STACK_FIELD_DATA+"+"+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"-"+offset+"," +
                                    offset+"*sizeof("+STACK_DATA_TYPE+"));");
                        }
                        writeLine(writer, level,STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"-="+count+";");
                    }
                    case STACK_ROT -> {
                        int count=((Parser.StackModifierToken)next).args[0];
                        int steps=((Parser.StackModifierToken)next).args[1];
                        writeLine(writer, level,"memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_DATA+
                                "+"+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+" ," +
                                STACK_ARG_NAME+"->"+STACK_FIELD_DATA+
                                "+"+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"-"+count+","+
                                steps+"*sizeof("+STACK_DATA_TYPE+"));");
                        writeLine(writer, level,"memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_DATA+
                                "+"+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"-"+count+"," +
                                STACK_ARG_NAME+"->"+STACK_FIELD_DATA+
                                "+"+STACK_ARG_NAME+"->"+STACK_FIELD_SIZE+"-"+(count-steps)+
                                ","+count+"*sizeof("+STACK_DATA_TYPE+"));");
                    }
                    case DEBUG_PRINT ->{
                        Type t=((Parser.TypedToken)next).target;
                        String popElement = STACK_ARG_NAME + "->" +STACK_FIELD_DATA +
                                "[--(" + STACK_ARG_NAME + "->" + STACK_FIELD_SIZE + ")]";
                        if(t==Type.INT){
                            writeLine(writer, level, "printf(\"%\"PRIi64\"\\n\", " + popElement + "." + typeWrapperName(t) + ");");
                        }else if(t==Type.UINT){
                            writeLine(writer, level, "printf(\"%\"PRIu64\"\\n\", " + popElement + "." + typeWrapperName(t) + ");");
                        }else if(t==Type.CODEPOINT){
                            writeLine(writer, level, "printf(\"%\"PRIx32\"\\n\", " + popElement + "." + typeWrapperName(t) + ");");
                        }else if(t==Type.BYTE){
                            writeLine(writer, level, "printf(\"'%1$c' (%1$\"PRIx8\")\\n\", " + popElement + "." + typeWrapperName(t) + ");");
                        }else{
                            System.err.println("unsupported type in debugPrint:"+t);
                            //TODO better output for debug print
                            writeLine(writer, level,"printf(\"%\"PRIx64\"\\n\", "+popElement + "." +typeWrapperName(Type.UINT)+");");
                        }
                    }
                    case CURRIED_LAMBDA -> throw new UnsupportedOperationException("compiling CURRIED_LAMBDA  is currently not implemented");
                    case CAST -> throw new UnsupportedOperationException("compiling CAST  is currently not implemented");
                    case NEW -> throw new UnsupportedOperationException("compiling NEW  is currently not implemented");
                    case NEW_ARRAY -> throw new UnsupportedOperationException("compiling NEW_ARRAY  is currently not implemented");
                    case DEREFERENCE -> throw new UnsupportedOperationException("compiling DEREFERENCE  is currently not implemented");
                    case ASSIGN -> throw new UnsupportedOperationException("compiling ASSIGN  is currently not implemented");
                    case VARIABLE -> throw new UnsupportedOperationException("compiling VARIABLE  is currently not implemented");
                    case CONTEXT_OPEN -> throw new UnsupportedOperationException("compiling CONTEXT_OPEN  is currently not implemented");
                    case CONTEXT_CLOSE -> throw new UnsupportedOperationException("compiling CONTEXT_CLOSE  is currently not implemented");
                    case CALL_PTR -> throw new UnsupportedOperationException("compiling CALL_PTR  is currently not implemented");
                    case CALL_NATIVE_PROC -> throw new UnsupportedOperationException("compiling CALL_NATIVE_PROC  is currently not implemented");
                    case RETURN -> throw new UnsupportedOperationException("compiling RETURN  is currently not implemented");
                    case ASSERT -> throw new UnsupportedOperationException("compiling ASSERT  is currently not implemented");
                    case BLOCK_TOKEN -> throw new UnsupportedOperationException("compiling BLOCK_TOKEN  is currently not implemented");
                    case SWITCH -> throw new UnsupportedOperationException("compiling SWITCH  is currently not implemented");
                    case EXIT -> throw new UnsupportedOperationException("compiling EXIT  is currently not implemented");
                    case CAST_ARG -> throw new UnsupportedOperationException("compiling CAST_ARG  is currently not implemented");
                    case TUPLE_GET_INDEX -> throw new UnsupportedOperationException("compiling TUPLE_GET_INDEX  is currently not implemented");
                    case TUPLE_REFERENCE_TO -> throw new UnsupportedOperationException("compiling TUPLE_REFERENCE_TO  is currently not implemented");
                    case TUPLE_SET_INDEX -> throw new UnsupportedOperationException("compiling TUPLE_SET_INDEX  is currently not implemented");
                    case TRAIT_FIELD_ACCESS -> throw new UnsupportedOperationException("compiling TRAIT_FIELD_ACCESS  is currently not implemented");
                    case DECLARE_LAMBDA, IDENTIFIER, REFERENCE_TO, OPTIONAL_OF, EMPTY_OPTIONAL, UNREACHABLE, OVERLOADED_PROC_PTR,
                            MARK_MUTABLE, MARK_MAYBE_MUTABLE, MARK_IMMUTABLE, MARK_INHERIT_MUTABILITY, ARRAY_OF, MEMORY_OF, STACK_SIZE ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                }
            }catch (ConcatRuntimeError e){
                throw new RuntimeException("while executing "+next.pos,e);
            }
        }
    }


    public static void main(String[] args) throws IOException {
        File in=new File(System.getProperty("user.dir")+"/compiler.concat/test_compilerJ.concat");
        File out=new File(System.getProperty("user.dir")+"/compiler.concat/compilerJ_out.c");

        try {
            compile(Parser.parse(in,null,Interpreter.defaultContext),out);
        }catch (SyntaxError e){
            SyntaxError s = e;
            System.err.println(s.getMessage());
            System.err.println("  at "+ s.pos);
            while(s.getCause() instanceof SyntaxError){
                s =(SyntaxError) s.getCause();
                System.err.println("  at "+ s.pos);
            }
        }
    }
}
