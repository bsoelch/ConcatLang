package bsoelch.concat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Compiler {
    static final HashMap<Type,String> primitives =new HashMap<>();

    static{
        primitives.put(Type.BOOL,"bool");
        for(Type.IntType t:Type.IntType.intTypes) {
            primitives.put(t, (t.signed?"int":"uint")+t.bits+"_t");
        }
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
    static final String STACK_FIELD_POINTER="ptr";
    static final String STACK_FIELD_CAPACITY="capacity";

    static final String DUP_VAR_NAME="dup_tmp";

    private static class CodeGeneratorImpl implements CodeGenerator {
        final BufferedWriter out;
        private int indent;

        /**true if in the previous line has not been finished*/
        private boolean unfinishedLine = false;

        private CodeGeneratorImpl(BufferedWriter out) {
            this.out = out;
        }

        @SuppressWarnings("SameParameterValue")
        void setIndent(int indent){
            this.indent=indent;
        }

        @Override
        public CodeGenerator indent() {
            indent++;
            return this;
        }
        @Override
        public CodeGenerator dedent() {
            indent--;
            return this;
        }

        @Override
        public CodeGenerator changeStackPointer(int k) throws IOException {
            startLine();
            if(k<0){
                out.write(STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-="+(-k));
            }else{
                out.write(STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"+="+k);
            }
            return endLine();
        }

        @Override
        public CodeGenerator lineComment(String str) throws IOException {
            if(unfinishedLine){
                endLine();
            }
            startLine();
            out.write("// "+str.replace('\n',' ').replace('\r',' '));
            newLine();
            return this;
        }
        @Override
        public CodeGenerator blockComment(String str) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("/*");
            out.write(str.replace("*/","* /"));
            out.write("*/");
            return this;
        }

        @Override
        public CodeGenerator startLine() throws IOException {
            if(unfinishedLine)
                endLine();
            unfinishedLine = true;
            writeIndent(out,indent);
            return this;
        }
        @Override
        public CodeGenerator pushPrimitive(Type target) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"++)->"+typeWrapperName(target)+" = ");
            return this;
        }
        @Override
        public CodeGenerator pushReference(Type target) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"++)->"+typeRefName(target)+" = ");
            return this;
        }
        @Override
        public CodeGenerator assignPrimitive(int offset, Type target) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("(("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")-"+offset+")->"+typeWrapperName(target)+" = ");
            return this;
        }
        @Override
        public CodeGenerator assignReference(int offset, Type target, boolean assignValue) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write((assignValue?"*":"")+
                    "(("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")-"+offset+")->"+typeRefName(target)+" = ");
            return this;
        }

        @Override
        public CodeGenerator newLine() throws IOException {
            if(unfinishedLine){
                out.newLine();
                unfinishedLine = false;
            }
            return this;
        }
        @Override
        public CodeGenerator endLine() throws IOException {
            if(unfinishedLine){
                out.write(";");
                out.newLine();
                unfinishedLine = false;
            }
            return this;
        }
        @Override
        public CodeGenerator popPrimitive(Type type) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("((--("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"))->"+typeWrapperName(type)+")");
            return this;
        }
        @Override
        public CodeGenerator getPrimitive(int offset, Type type) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("((("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")-"+offset+")->"+typeWrapperName(type)+")");
            return this;
        }
        @Override
        public CodeGenerator getReference(int offset, Type type) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("((("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")-"+offset+")->"+typeRefName(type)+")");
            return this;
        }

        @Override
        public CodeGenerator getPrimitiveAs(int offset, Type src, Type target) throws IOException {
            if(!unfinishedLine)
                startLine();
            //TODO check if C-cast is allowed
            out.write("(("+primitives.get(target)+")");
            getPrimitive(offset,src);
            out.write(")");
            return this;
        }

        @Override
        public CodeGenerator append(String s) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write(s);
            return this;
        }
    }

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
                    ", ."+STACK_FIELD_POINTER+" = "+STACK_FIELD_DATA+", ."+STACK_FIELD_CAPACITY+" = 100};");
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

    private static void writeLine(BufferedWriter writer,String line) throws IOException {
        writeLine(writer,0,line);
    }
    private static void writeComment(BufferedWriter writer,String comment) throws IOException {
        String[] parts=comment.split("[\n\r]");
        for(String line:parts){
            writer.write("// "+ line);
            writer.newLine();
        }
    }

    private static String typeWrapperName(Type primitive){
        return "as"+Character.toUpperCase(primitive.name.charAt(0))+primitive.name.substring(1);
    }
    private static String typeRefName(Type primitive){
        return typeWrapperName(primitive)+"Ref";
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
        writeLine(writer,"typedef struct{"); //addLater? replace cap with data+cap
        writeLine(writer,1,"size_t "+STACK_FIELD_CAPACITY+";");
        writeLine(writer,1,STACK_DATA_TYPE+"* "+STACK_FIELD_POINTER+";");
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
            writeLine(writer,1,e.getValue()+"* "+typeRefName(e.getKey())+";");
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
        CodeGeneratorImpl generator=new CodeGeneratorImpl(writer);
        for(Map.Entry<String, Parser.Declareable> dec:prog.rootContext().declareables()){
            if(dec.getValue().declarableType() == Parser.DeclareableType.PROCEDURE){
                writeComment(writer,"procedure "+dec.getKey()+" "+((Parser.Callable)dec.getValue()).type());
                writeLine(writer,CONCAT_PROC_OUT+ " " + PUBLIC_PROC_PREFIX +idOf(dec.getValue())
                        +CONCAT_PROC_NAMED_SIGNATURE+"{");
                generator.setIndent(1);
                compileCodeSection(generator,(Parser.CodeSection)dec.getValue());
                writeLine(writer,"}");
            }
        }
        for(Map.Entry<Parser.PrivateDefinitionId, Parser.Declareable> dec:prog.rootContext().localDeclareables()){
            if(dec.getValue().declarableType() == Parser.DeclareableType.PROCEDURE){
                writeComment(writer,"procedure "+dec.getKey().nameInFile()+" "+((Parser.Callable)dec.getValue()).type()+
                        " in "+dec.getKey().fileId());
                writeLine(writer,CONCAT_PROC_OUT+ " " + PRIVATE_PROC_PREFIX +idOf(dec.getValue())
                        +CONCAT_PROC_NAMED_SIGNATURE+"{");
                generator.setIndent(1);
                compileCodeSection(generator,(Parser.CodeSection)dec.getValue());
                writeLine(writer,"}");
            }
        }
        writer.newLine();
    }

    //addLater optimize code (merge consecutive push and pop operations)
    private static void compileCodeSection(CodeGenerator generator, Parser.CodeSection section) throws IOException {
        boolean hasDupTmpVar=false;
        for(Parser.Token next:section.tokens()){
            generator.lineComment(next.toString());
            try {
                switch (next.tokenType) {
                    case NOP, CONTEXT_OPEN, CONTEXT_CLOSE -> {}
                    case LAMBDA, VALUE, GLOBAL_VALUE -> {
                        assert next instanceof Parser.ValueToken;
                        Value value = ((Parser.ValueToken) next).value;
                        if(primitives.containsKey(value.type)){
                            if (value.type == Type.INT()) {
                                generator.pushPrimitive(value.type).append(value.asLong() + "LL").endLine();
                            }else if (value.type == Type.UINT()) {
                                generator.pushPrimitive(value.type).append(value.asLong() + "ULL").endLine();
                            }else if (value.type == Type.CODEPOINT()) {
                                generator.pushPrimitive(value.type).append("0x"+Long.toHexString(value.asLong())).endLine();
                            }else if (value.type == Type.BYTE()) {
                                generator.pushPrimitive(value.type).append("0x"+Integer.toHexString(value.asByte()&0xff)).endLine();
                            }else if (value.type == Type.BOOL) {
                                generator.pushPrimitive(value.type).append((value.asBool()?"true":"false")).endLine();
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
                        generator.append(called.isPublic? PUBLIC_PROC_PREFIX : PRIVATE_PROC_PREFIX +idOf(called)+
                                "("+STACK_ARG_NAME+", NULL)").endLine();
                    }
                    case STACK_DUP -> {
                        generator.startLine().append((hasDupTmpVar?"":STACK_DATA_TYPE+" ")+DUP_VAR_NAME+
                                " = *("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+
                                "-"+((Parser.StackModifierToken)next).args[0]+")").endLine()
                        .append("*("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"++) = "+DUP_VAR_NAME).endLine();
                        hasDupTmpVar=true;
                    }
                    case STACK_DROP -> {
                        int offset=((Parser.StackModifierToken)next).args[0];
                        int count=((Parser.StackModifierToken)next).args[1];
                        if(offset>0){
                            generator.append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+(offset+count)+","+
                                    STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+offset+"," +
                                    offset+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                        }
                        generator.changeStackPointer(-count);
                    }
                    case STACK_ROT -> {
                        int count=((Parser.StackModifierToken)next).args[0];
                        int steps=((Parser.StackModifierToken)next).args[1];
                        generator.append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+" ," +
                                STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+count+","+
                                steps+"*sizeof("+STACK_DATA_TYPE+"))").endLine()
                        .append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+count+"," +
                                STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+(count-steps)+
                                ","+count+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                    }
                    case DEBUG_PRINT ->{
                        Type t=((Parser.TypedToken)next).target;
                        if(t==Type.BYTE()){
                            generator.append("printf(\"'%1$c' (%1$\"PRIx8\")\\n\", ").popPrimitive(t).append(")").endLine();
                        }else if(t==Type.CODEPOINT()){
                            generator.append("printf(\"U+%\"PRIx32\"\\n\", ").popPrimitive(t).append(")").endLine();
                        }else if(t instanceof Type.IntType){
                            generator.append( "printf(\"%\"PRI"+(((Type.IntType) t).signed?"i":"u")+((Type.IntType) t).bits+
                                    "\"\\n\", ").popPrimitive(t).append(")").endLine();
                        }else if(t==Type.BOOL){
                            generator.append( "puts((").popPrimitive(t).append(") ? \"true\" : \"false\")").endLine();
                        }else{
                            System.err.println("unsupported type in debugPrint:"+t);
                            //TODO better output for debug print
                            generator.append("printf(\"%\"PRIx64\"\\n\", ").popPrimitive(Type.UINT()).append(")").endLine();
                        }
                    }
                    case BLOCK_TOKEN -> {
                        switch(((Parser.BlockToken)next).blockType){
                            case IF,_IF ->
                                generator.append("if(").popPrimitive(Type.BOOL).append("){").indent().newLine();
                            case IF_OPTIONAL,_IF_OPTIONAL ->
                                throw new UnsupportedOperationException("compiling IF_OPTIONAL  is currently not implemented");
                            case ELSE ->
                                generator.dedent().append("}else{").indent().newLine();
                            case END_IF ->{
                                int elseCount=((Parser.BlockToken) next).delta;
                                if(elseCount==0)
                                    elseCount=1;
                                while (elseCount-->0){
                                    generator.dedent().append("}").newLine();
                                }
                            }
                            case WHILE -> //concat while loops are best represented by do{ ... if(pop()) break; ... }while(true);
                                generator.append("do{").indent().newLine();
                            case DO ->
                                generator.dedent().append( "if(!").popPrimitive(Type.BOOL).append(") break; //exit while loop")
                                        .indent().newLine();
                            case DO_OPTIONAL ->
                                    throw new UnsupportedOperationException("compiling DO_OPTIONAL  is currently not implemented");
                            case END_WHILE ->
                                generator.dedent().append("}while(true)").endLine();
                            case DO_WHILE ->
                                generator.dedent().append("}while(").popPrimitive(Type.BOOL).append(")").endLine();
                            case END_CASE ->
                                throw new UnsupportedOperationException("compiling BREAK  is currently not implemented");
                            case FOR_ARRAY_PREPARE ->
                                throw new UnsupportedOperationException("compiling FOR_ARRAY_PREPARE  is currently not implemented");
                            case FOR_ARRAY_LOOP ->
                                throw new UnsupportedOperationException("compiling FOR_ARRAY_LOOP  is currently not implemented");
                            case FOR_ARRAY_END ->
                                throw new UnsupportedOperationException("compiling FOR_ARRAY_END  is currently not implemented");
                            case FOR_ITERATOR_LOOP ->
                                throw new UnsupportedOperationException("compiling FOR_ITERATOR_LOOP  is currently not implemented");
                            case FOR_ITERATOR_END ->
                                throw new UnsupportedOperationException("compiling FOR_ITERATOR_END  is currently not implemented");
                            case FOR,SWITCH,CASE,DEFAULT, ARRAY, END, UNION_TYPE,TUPLE_TYPE,PROC_TYPE,ARROW,END_TYPE ->
                                    throw new RuntimeException("blocks of type "+((Parser.BlockToken)next).blockType+
                                            " should be eliminated at compile time");
                        }
                    }
                    case CAST ->
                        compileCast(generator, ((Parser.CastToken)next).src,  ((Parser.CastToken)next).target, 1);
                    case CAST_ARG ->
                        compileCast(generator, ((Parser.ArgCastToken)next).src,  ((Parser.ArgCastToken)next).target,
                                ((Parser.ArgCastToken)next).offset);
                    case VARIABLE -> {
                        assert next instanceof Parser.VariableToken;
                        Parser.VariableToken asVar=(Parser.VariableToken) next;
                        String idName=asVar.variableType.name().toLowerCase()+"_var_"+asVar.id.level+"_"+asVar.id.id;
                        if(!primitives.containsKey(asVar.id.type)){
                            throw new UnsupportedEncodingException("variables of type "+asVar.id.type+" are currently not supported");
                        }
                        switch (asVar.accessType){
                            case DECLARE ->
                                generator.append(primitives.get(asVar.id.type)+" "+idName+" = ")
                                        .popPrimitive(asVar.id.type).endLine();
                            case READ ->
                                generator.pushPrimitive(asVar.id.type).append(idName).endLine();
                            case REFERENCE_TO ->
                                generator.pushReference(asVar.id.type).append("&"+idName).endLine();
                        }
                    }
                    case DEREFERENCE -> {
                        Type content=((Parser.TypedToken)next).target;
                        if(!primitives.containsKey(content)){
                            throw new UnsupportedEncodingException("dereferencing "+content+" is currently not implemented");
                        }
                        generator.assignPrimitive(1,content)
                                .append("*").getReference(1,content).endLine();
                    }
                    case ASSIGN -> {
                        Type content=((Parser.TypedToken)next).target;
                        if(!primitives.containsKey(content)){
                            throw new UnsupportedEncodingException("assigning to "+content+" is currently not implemented");
                        }
                        generator.assignReference(1,content,true)
                                .getPrimitive(2,content).endLine();
                        generator.changeStackPointer(-2);
                    }
                    case CURRIED_LAMBDA -> throw new UnsupportedOperationException("compiling CURRIED_LAMBDA  is currently not implemented");
                    case NEW -> throw new UnsupportedOperationException("compiling NEW  is currently not implemented");
                    case NEW_ARRAY -> throw new UnsupportedOperationException("compiling NEW_ARRAY  is currently not implemented");
                    case CALL_PTR -> throw new UnsupportedOperationException("compiling CALL_PTR  is currently not implemented");
                    case CALL_NATIVE_PROC -> throw new UnsupportedOperationException("compiling CALL_NATIVE_PROC  is currently not implemented");
                    case RETURN -> throw new UnsupportedOperationException("compiling RETURN  is currently not implemented");
                    case ASSERT -> throw new UnsupportedOperationException("compiling ASSERT  is currently not implemented");
                    case SWITCH -> throw new UnsupportedOperationException("compiling SWITCH  is currently not implemented");
                    case EXIT -> throw new UnsupportedOperationException("compiling EXIT  is currently not implemented");
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

    private static void compileCast(CodeGenerator generator, Type src, Type target, int offset) throws IOException {
        if(primitives.containsKey(src)&&primitives.containsKey(target)){
            //TODO check if direct C-cast is allowed
            generator.assignPrimitive(offset,target).getPrimitive(offset,src).endLine();
        }else{
            throw new UnsupportedEncodingException("casting from "+src+" to "+target+" is currently not supported");
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
