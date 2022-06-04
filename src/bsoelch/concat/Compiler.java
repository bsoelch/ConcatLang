package bsoelch.concat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Compiler {
    static final HashMap<Type,Integer> typeIds =new HashMap<>();

    static int ilog2(int i){
        int n=-1;
        while(i!=0){
            i>>>=1;
            n++;
        }
        return n;
    }
    static{
        typeIds.put(Type.BOOL,1);
        for(Type.IntType t:Type.IntType.intTypes) {
            typeIds.put(t,((ilog2(t.bits/8)+1)<<1)|(t.signed?1:0));
        }
        typeIds.put(Type.FLOAT,0b10000);
        typeIds.put(Type.TYPE,0b10001);
    }
    static final String STACK_DATA_TYPE="Value";
    public static final String CTYPE_TYPE = "Type";
    public static final String CTYPE_FLOAT = "float64_t";
    public static final String CTYPE_FPTR = "FPtr";
    public static final String CTYPE_OPTIONAL_I32 = "optionalI32";
    public static final String CTYPE_OPTIONAL_U32 = "optionalU32";

    static final String CONCAT_PROC_OUT="void";
    static final String CONCAT_PROC_SIGNATURE="(Stack*, "+STACK_DATA_TYPE+"*)";
    static final String STACK_ARG_NAME="stack";
    static final String CONCAT_PROC_NAMED_SIGNATURE="(Stack* "+STACK_ARG_NAME+", "+STACK_DATA_TYPE+"* curried)";
    public static final String PUBLIC_PROC_PREFIX = "concat_public_procedure_";
    public static final String PRIVATE_PROC_PREFIX = "concat_private_procedure_";
    public static final String CONST_ARRAY_PREFIX = "concat_const_array_";

    static final String STACK_FIELD_DATA="data";

    static final String STACK_FIELD_POINTER="ptr";
    static final String STACK_FIELD_CAPACITY="capacity";

    private static final class CodeGeneratorImpl implements CodeGenerator {
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
                out.write(STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+" -= "+(-k));
            }else{
                out.write(STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+" += "+k);
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
            out.write("/* ");
            out.write(str.replace("*/","* /"));
            out.write(" */");
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

        void pushRaw() throws IOException {
            if (!unfinishedLine)
                startLine();
            out.write("(" + STACK_ARG_NAME + "->" + STACK_FIELD_POINTER + "++)");
        }
        @Override
        public CodeGenerator pushPrimitive(BaseType.StackValue target) throws IOException {
            pushRaw();
            out.write("->"+typeWrapperName(target)+" = ");
            return this;
        }
        @Override
        public CodeGenerator pushPointer(Type target) throws IOException {
            pushRaw();
            out.write("->"+typeWrapperName(target.baseType().pointerTo())+" = ");
            return this;
        }

        @Override
        public CodeGenerator assignPrimitive(int offset, BaseType.StackValue target) throws IOException {
            getRaw(offset);
            out.write("->"+typeWrapperName(target)+" = ");
            return this;
        }
        @Override
        public CodeGenerator assignPointer(int offset, Type target, boolean assignValue) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write((assignValue?"*":""));
            getRaw(offset);
            out.write("->"+typeWrapperName(target.baseType().pointerTo())+" = ");
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
        void popRaw() throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("(--("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"))");
        }
        @Override
        public CodeGenerator popPrimitive(BaseType.StackValue type) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("(");
            popRaw();
            out.write("->"+typeWrapperName(type)+")");
            return this;
        }
        @Override
        public CodeGenerator getRaw(int offset) throws IOException {
            if(!unfinishedLine)
                startLine();
            if(offset<0){
                out.write("(("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")+"+(-offset)+")");
            }else if(offset>0){
                out.write("(("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")-"+offset+")");
            }else{
                out.write("("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+")");
            }
            return this;
        }
        @Override
        public CodeGenerator getPrimitive(int offset, BaseType.StackValue type) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("(");
            getRaw(offset);
            out.write("->"+typeWrapperName(type)+")");
            return this;
        }
        @Override
        public CodeGenerator getPointer(int offset, Type type) throws IOException {
            if(!unfinishedLine)
                startLine();
            out.write("(");
            getRaw(offset);
            out.write("->"+typeWrapperName(type.baseType().pointerTo())+")");
            return this;
        }
        @Override
        public CodeGenerator getPrimitiveAs(int offset, BaseType.StackValue src, BaseType.StackValue target) throws IOException {
            if(!unfinishedLine)
                startLine();
            //TODO check if C-cast is allowed
            out.write("(("+target.cType+")");
            getPrimitive(offset,src);
            out.write(")");
            return this;
        }

        @Override
        public CodeGenerator appendBool(boolean value) throws IOException {
            out.write(value?"true":"false");
            return this;
        }

        @Override
        public CodeGenerator appendInt(long value, int bits, boolean signed) throws IOException {
            out.write(signed? value +"LL": Long.toUnsignedString(value)+"ULL");
            return this;
        }

        @Override
        public CodeGenerator appendType(Type type) throws IOException {
            if(!type.isPrimitive()){
                throw new UnsupportedOperationException("currently only primitive types are supported");
            }
            int id=typeIds.get(type);
            out.write(""+id);
            blockComment(type.name);
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
            writeComment(writer,"constant arrays/global variables");
            for(Map.Entry<Value.ArrayLike, FilePosition> e:prog.rootContext().constArrays().entrySet()){
                Value.ArrayLike array=e.getKey();
                if(!array.contentType().isPrimitive()){
                    throw new UnsupportedOperationException("arrays of type "+array.contentType()+" are currently not supported");
                }
                String primType=((BaseType.StackValue)array.contentType().baseType()).cType;
                StringBuilder declaration=new StringBuilder(primType+ " " + CONST_ARRAY_PREFIX +idOf(e.getValue())+"[] = {");
                for(int i=0;i< array.length();i++){
                    if(i>0)
                        declaration.append(", ");
                    try {
                        Value elt = array.get(i);
                        //TODO merge with CodeGenerator append...
                        if (elt.type == Type.INT()) {
                            declaration.append(elt.asLong()).append("LL");
                        }else if (elt.type == Type.UINT()) {
                            declaration.append(Long.toUnsignedString(elt.asLong())).append("ULL");
                        }else if (elt.type == Type.CODEPOINT()) {
                            declaration.append("0x").append(Long.toHexString(elt.asLong()));
                        }else if (elt.type == Type.BYTE()) {
                            declaration.append("0x").append(Integer.toHexString(elt.asByte()&0xff));
                        }else if (elt.type == Type.BOOL) {
                            declaration.append(elt.asBool()?"true":"false");
                        }else if (elt.type == Type.TYPE) {
                            Type type=elt.asType();
                            if(!type.isPrimitive()){
                                throw new UnsupportedOperationException("currently only primitive types are supported");
                            }
                            int id=typeIds.get(type);
                            declaration.append(id).append("/* ").append(type.name).append(" */");
                        }else {
                            throw new UnsupportedOperationException("values of type " + array.contentType() + " are currently not supported");
                        }
                    } catch (ConcatRuntimeError ex) {
                        throw new RuntimeException(ex);
                    }
                }
                writeLine(writer, declaration.append("};").toString());
            }
            // addLater global variables
            writer.newLine();
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

    private static String typeWrapperName(BaseType base) {
        if(base instanceof BaseType.StackValue)
            return "as" + Character.toUpperCase(((BaseType.StackValue) base).name.charAt(0))
                    + ((BaseType.StackValue) base).name.substring(1);
        return "as" + BaseType.StackValue.PTR.cType;
    }

    private static void printFileHeader(BufferedWriter writer) throws IOException {
        //addLater only initialize primitives that are used in the program
        for(BaseType.PrimitiveType t:BaseType.PrimitiveType.values()){
            if(t!= BaseType.PrimitiveType.INTEGER)
                BaseType.Primitive.get(t);//initialize all non-integral primitive types
        }
        //include required library files
        writeLine(writer,"#include \"stdbool.h\"");
        writeLine(writer,"#include \"inttypes.h\"");
        writeLine(writer,"#include \"stdlib.h\"");
        writeLine(writer,"#include \"stdio.h\"");
        writeLine(writer,"#include \"string.h\"");
        writer.newLine();
        writeComment(writer,"internal types");
        writeLine(writer,"typedef union "+STACK_DATA_TYPE+"_Impl "+STACK_DATA_TYPE+";");
        writer.newLine();
        writeLine(writer,"typedef struct{"); //addLater? replace cap with data+cap
        writeLine(writer,1,"size_t "+STACK_FIELD_CAPACITY+";");
        writeLine(writer,1,STACK_DATA_TYPE+"* "+STACK_FIELD_POINTER+";");
        writeLine(writer,1,STACK_DATA_TYPE+"* "+STACK_FIELD_DATA+";");
        writeLine(writer,"}Stack;");
        writer.newLine();
        writeLine(writer, "typedef uint64_t " + CTYPE_TYPE + ";");
        writeLine(writer, "typedef double " + CTYPE_FLOAT + ";");
        writeLine(writer, "typedef "+CONCAT_PROC_OUT+ "(*" + CTYPE_FPTR + ")" +CONCAT_PROC_SIGNATURE+";");
        writeLine(writer, "typedef int32_t  " + CTYPE_OPTIONAL_I32 + "[2];");
        writeLine(writer, "typedef uint32_t " + CTYPE_OPTIONAL_U32 + "[2];");
        writer.newLine();
        writeLine(writer,"union "+STACK_DATA_TYPE+"_Impl {");
        List<BaseType.StackValue> values = new ArrayList<>(BaseType.StackValue.values());
        values.sort(Comparator.comparing(t->t.cType.toLowerCase()));
        for(BaseType.StackValue e: values){//addLater text alignment
            writeLine(writer,1,e.cType+"  "+typeWrapperName(e)+";");
        }
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
        return idOf(dec.declaredAt());
    }
    private static String idOf(FilePosition pos) {
        return toCIdentifier(pos.fileId) + "_" + pos.line + "_" + pos.posInLine;
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
        for(Map.Entry<FilePosition, Value.Procedure> dec:prog.rootContext().lambdas()){
            writeComment(writer,"lambda "+((Parser.Callable)dec.getValue()).type()+" at "+dec.getKey());
            writeLine(writer,CONCAT_PROC_OUT+ " " + PRIVATE_PROC_PREFIX+idOf(dec.getValue())
                    +CONCAT_PROC_NAMED_SIGNATURE+";");
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
        for(Map.Entry<FilePosition, Value.Procedure> dec:prog.rootContext().lambdas()){
            writeComment(writer,"lambda "+((Parser.Callable)dec.getValue()).type()+" at "+dec.getKey());
            writeLine(writer,CONCAT_PROC_OUT+ " " + PRIVATE_PROC_PREFIX +idOf(dec.getValue())
                    +CONCAT_PROC_NAMED_SIGNATURE+"{");
            generator.setIndent(1);
            compileCodeSection(generator, dec.getValue());
            writeLine(writer,"}");
        }
        writer.newLine();
    }

    //addLater optimize code (merge consecutive push and pop operations)
    private static void compileCodeSection(CodeGenerator generator, Parser.CodeSection section) throws IOException {
        /*amount of stack-values that should be dropped at the end of the current code-blocks*/
        ArrayDeque<Integer> dropOnEnd=new ArrayDeque<>();
        for(Parser.Token next:section.tokens()){
            generator.lineComment(next.toString());
            try {
                switch (next.tokenType) {
                    case NOP, CONTEXT_OPEN, CONTEXT_CLOSE -> {}
                    case LAMBDA, VALUE, GLOBAL_VALUE -> {
                        assert next instanceof Parser.ValueToken;
                        Value value = ((Parser.ValueToken) next).value;
                        pushValue(section, generator, value);
                    }
                    case CALL_PROC -> {
                        assert next instanceof Parser.CallToken;
                        Parser.CallToken callToken = (Parser.CallToken) next;
                        if(callToken.called instanceof Value.Procedure called){
                            generator.append(called.isPublic? PUBLIC_PROC_PREFIX : PRIVATE_PROC_PREFIX +idOf(called)+
                                    "("+STACK_ARG_NAME+", NULL)").endLine();
                        }else if(callToken.called instanceof Value.InternalProcedure iProc){
                            if(!iProc.canCompile()){
                                throw new UnsupportedOperationException("compilation of internal procedure "+iProc.name+
                                        " is not supported");
                            }
                            iProc.compile(generator).endLine();
                        }else{
                            throw new UnsupportedOperationException("compilation of procedures of type "+
                                    callToken.called.getClass()+" is not supported");
                        }
                    }
                    case CALL_PTR ->
                        generator.changeStackPointer(-2)
                                .getPrimitive(0, BaseType.StackValue.F_PTR).append("("+STACK_ARG_NAME+", ")
                                .getPointer(-1,Type.ANY).append(")").endLine();
                    case STACK_DUP -> {
                        int offset=((Parser.StackModifierToken)next).args[2];
                        int count=((Parser.StackModifierToken)next).args[3];
                        generator.append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+","+
                                STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+(offset+count)+"," +
                                count+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                        generator.changeStackPointer(count);
                    }
                    case STACK_DROP -> {
                        int offset=((Parser.StackModifierToken)next).args[2];
                        int count=((Parser.StackModifierToken)next).args[3];
                        if(offset>0){
                            generator.append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+(offset+count)+","+
                                    STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+offset+"," +
                                    offset+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                        }
                        generator.changeStackPointer(-count);
                    }
                    case STACK_ROT -> {
                        int count=((Parser.StackModifierToken)next).args[2];
                        int steps=((Parser.StackModifierToken)next).args[3];
                        generator.append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+" ," +
                                STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+count+","+
                                steps+"*sizeof("+STACK_DATA_TYPE+"))").endLine()
                        .append("memmove("+STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+count+"," +
                                STACK_ARG_NAME+"->"+STACK_FIELD_POINTER+"-"+(count-steps)+
                                ","+count+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                    }
                    case DEBUG_PRINT ->{
                        Type t=((Parser.TypedToken)next).target;
                        BaseType baseType=t.baseType();
                        generator.append("fputs(\""+t.name+" (\", stdout)").endLine();
                        generator.changeStackPointer(-baseType.blockCount()).endLine();
                        if(baseType instanceof BaseType.StackValue){
                            printPrimitive(generator,0,(BaseType.StackValue)baseType,false);
                        }else{
                            int i=0;
                            for(BaseType.StackValue s:baseType.blocks()){
                                printPrimitive(generator,i--,s,i!=0);
                            }
                        }
                        generator.append("puts(\")\")").endLine();
                    }
                    case BLOCK_TOKEN -> {
                        switch(((Parser.BlockToken)next).blockType){
                            case IF,_IF ->
                                generator.append("if(").popPrimitive(Type.BOOL).append("){").indent().newLine();
                            case IF_OPTIONAL,_IF_OPTIONAL ->{
                                assert next instanceof Parser.IfOrDoOptional;
                                Type type=((Parser.IfOrDoOptional) next).optionalType;
                                BaseType baseType=type.baseType();
                                if(baseType== BaseType.Primitive.OPTIONAL_I32||baseType== BaseType.Primitive.OPTIONAL_U32){
                                    generator.append("if(").getPrimitive(1,(BaseType.StackValue) baseType)
                                            .append("[1] != 0){").indent().newLine();
                                    dropOnEnd.push(1);
                                    type=type.content();
                                    if(type.isOptional()){
                                        generator.getPrimitive(1,(BaseType.StackValue) baseType)
                                                .append("[1]--").endLine();
                                    }else{
                                        generator.assignPrimitive(1,type)
                                                .getPrimitive(1,(BaseType.StackValue) baseType)
                                                .append("[0]").endLine();
                                    }
                                }else if(baseType instanceof BaseType.Composite){
                                    generator.append("if(").getPrimitive(1,Type.UINT()).append(" != 0){").indent().newLine();
                                    dropOnEnd.push(baseType.blockCount());
                                    type=type.content();
                                    if(type.isOptional()){
                                        generator.getPrimitive(1,Type.UINT()).append("--").endLine();
                                    }else{
                                        generator.changeStackPointer(-1).endLine();
                                    }
                                }else{
                                    throw new UnsupportedOperationException("compiling IF_OPTIONAL is currently " +
                                            "not supported for type:"+type);
                                }
                            }
                            case ELSE ->{
                                generator.dedent().append("}else{").indent().newLine();
                                if(dropOnEnd.size()>0){
                                    generator.changeStackPointer(-dropOnEnd.pop());
                                }
                            }
                            case END_IF ->{
                                int elseCount=((Parser.BlockToken) next).delta;
                                if(elseCount==0)
                                    elseCount=1;
                                while (elseCount-->0){
                                    generator.dedent().append("}").newLine();
                                    if(dropOnEnd.size()>0){
                                        generator.changeStackPointer(-dropOnEnd.pop());
                                    }
                                }
                            }
                            case WHILE -> //concat while loops are best represented by do{ ... if(pop()) break; ... }while(true);
                                generator.append("do{").indent().newLine();
                            case DO ->
                                generator.dedent().append( "if(!").popPrimitive(Type.BOOL).append(") break; //exit while loop")
                                        .indent().newLine();
                            case DO_OPTIONAL -> {
                                assert next instanceof Parser.IfOrDoOptional;
                                Type type=((Parser.IfOrDoOptional) next).optionalType;
                                BaseType baseType=type.baseType();
                                if(baseType== BaseType.Primitive.OPTIONAL_I32||baseType== BaseType.Primitive.OPTIONAL_U32){
                                    generator.append("if(").getPrimitive(1,(BaseType.StackValue) baseType)
                                            .append("[1] == 0) break; //exit while loop").indent().newLine();
                                    dropOnEnd.push(1);
                                    type=type.content();
                                    if(type.isOptional()){
                                        generator.getPrimitive(1,(BaseType.StackValue) baseType)
                                                .append("[1]--").endLine();
                                    }else{
                                        generator.assignPrimitive(1,type)
                                                .getPrimitive(1,(BaseType.StackValue) baseType)
                                                .append("[0]").endLine();
                                    }
                                }else if(baseType instanceof BaseType.Composite){
                                    generator.append("if(").getPrimitive(1,Type.UINT())
                                            .append(" == 0) break; //exit while loop").indent().newLine();
                                    dropOnEnd.push(baseType.blockCount());
                                    type=type.content();
                                    if(type.isOptional()){
                                        generator.getPrimitive(1,Type.UINT()).append("--").endLine();
                                    }else{
                                        generator.changeStackPointer(-1).endLine();
                                    }
                                }else{
                                    throw new UnsupportedOperationException("compiling IF_OPTIONAL is currently " +
                                            "not supported for type:"+type);
                                }
                            }
                            case END_WHILE ->{
                                generator.dedent().append("}while(true)").endLine();
                                if(dropOnEnd.size()>0){
                                    generator.changeStackPointer(-dropOnEnd.pop());
                                }
                            }
                            case DO_WHILE ->
                                generator.dedent().append("}while(").popPrimitive(Type.BOOL).append(")").endLine();
                            case END_CASE ->
                                throw new UnsupportedOperationException("compiling BREAK  is currently not implemented");
                            case FOR_ARRAY_PREPARE ->{
                                assert next instanceof Parser.ForArrayStart;
                                Type contentType=((Parser.ForArrayStart) next).arrayType.content();
                                if(!(((Parser.ForArrayStart) next).arrayType.isArray()&&contentType.isPrimitive())){
                                    throw new UnsupportedOperationException("compiling FOR_ARRAY_PREPARE for "+
                                            ((Parser.ForArrayStart) next).arrayType+" is currently not supported");
                                }
                                generator.assignPointer(1,contentType,false).
                                        getPointer(2,contentType).append("+")
                                        .getPrimitive(1,Type.UINT()).endLine();
                            }
                            case FOR_ARRAY_LOOP -> {
                                assert next instanceof Parser.ForArrayStart;
                                Type contentType = ((Parser.ForArrayStart) next).arrayType.content();
                                generator.append("for(; ").getPointer(2,contentType).append(" < ").getPointer(1,contentType)
                                        .append("; ").getPointer(2,contentType).append("++){").newLine().indent()
                                        .assignPrimitive(0,contentType).append("*").getPointer(2,contentType).endLine()
                                        .changeStackPointer(1).endLine();
                            }
                            case FOR_ARRAY_END ->
                                generator.dedent().append("}").newLine().changeStackPointer(-2).endLine();
                            case FOR_ITERATOR_PREPARE ->
                                    throw new UnsupportedOperationException("compiling FOR_ITERATOR_PREPARE  is currently not implemented");
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
                        BaseType baseType = asVar.id.type.baseType();
                        if(baseType instanceof BaseType.StackValue){
                            switch (asVar.accessType){
                                case DECLARE ->
                                        generator.append(((BaseType.StackValue) baseType).cType+" "+idName+" = ")
                                                .popPrimitive(asVar.id.type).endLine();
                                case READ ->
                                        generator.pushPrimitive((BaseType.StackValue)baseType).append(idName).endLine();
                                case REFERENCE_TO ->
                                        generator.pushPointer(asVar.id.type).append("&"+idName).endLine();
                            }
                        }else{
                            switch (asVar.accessType){
                                case DECLARE ->
                                        generator.changeStackPointer(-baseType.blockCount()).endLine()
                                                .append(STACK_DATA_TYPE+" "+idName+"["+baseType.blockCount()+"]").endLine()
                                                .append("memcpy("+idName+", ").getRaw(0)
                                                .append(", "+baseType.blockCount()+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                                case READ ->
                                        generator.append("memcpy(").getRaw(0)
                                                .append(", "+idName+", "+baseType.blockCount()+"*sizeof("+STACK_DATA_TYPE+"))").endLine()
                                                 .changeStackPointer(baseType.blockCount()).endLine();
                                case REFERENCE_TO ->
                                        generator.pushPointer(asVar.id.type).append(idName).endLine();
                            }
                        }
                    }
                    case DEREFERENCE -> {
                        Type content=((Parser.TypedToken)next).target;
                        BaseType baseType=content.baseType();
                        if(baseType instanceof BaseType.StackValue){
                            generator.assignPrimitive(1,content)
                                    .append("*").getPointer(1,content).endLine();
                        }else{
                            generator.append("memcpy(").getRaw(1).append(", ").getPointer(1,content)
                                    .append(", "+baseType.blockCount()+"*sizeof("+STACK_DATA_TYPE+"))").endLine()
                                    .changeStackPointer(baseType.blockCount()-1).endLine();
                        }
                    }
                    case ASSIGN -> {
                        Type content=((Parser.TypedToken)next).target;
                        BaseType baseType=content.baseType();
                        if(baseType instanceof BaseType.StackValue){
                            generator.assignPointer(1,content,true)
                                    .getPrimitive(2,(BaseType.StackValue) baseType).endLine();
                            generator.changeStackPointer(-2);
                        }else{
                            generator.append("memcpy(").getPointer(1,content).append(", ")
                                    .getRaw(baseType.blockCount()+1)
                                    .append(", "+baseType.blockCount()+"*sizeof("+STACK_DATA_TYPE+"))").endLine()
                                    .changeStackPointer(-(baseType.blockCount()+1)).endLine();
                        }
                    }
                    case RETURN ->
                            generator.startLine().append("return").endLine();
                    case EXIT ->
                            generator.startLine().append("exit((int)")
                                    .getPrimitive(1,Type.INT()).append(")").endLine();
                    case NEW -> {
                        assert next instanceof Parser.TypedToken;
                        Type type=((Parser.TypedToken)next).target;
                        BaseType base=type.baseType();
                        if(type instanceof Type.Tuple||type instanceof Type.Struct){
                            if(base == BaseType.StackValue.PTR){
                                throw new UnsupportedOperationException(type+" is currently not supported in NEW");
                            }//else do nothing
                        }else{
                            throw new UnsupportedOperationException(type+" is currently not supported in NEW");
                        }
                    }
                    case TUPLE_GET_INDEX -> {
                        assert next instanceof Parser.TupleElementAccess;
                        Type.TupleLike type=((Parser.TupleElementAccess) next).tupleType;
                        int index = ((Parser.TupleElementAccess) next).index;
                        if(type.baseType() == BaseType.StackValue.PTR){
                            throw new UnsupportedOperationException("tuple access is currently not supported for tuples of type "+type);
                        }
                        Type[] elements=type.getElements();
                        int pre=0,count=elements[index].blockCount(),post=0;
                        for(int i = 0; i< index; i++){
                            pre+=elements[i].blockCount();
                        }
                        for(int i = index+1; i< elements.length; i++){
                            post+=elements[i].blockCount();
                        }
                        generator.changeStackPointer(-(pre+post)).endLine();
                        if(index>0){
                            generator.append("memmove(").getRaw(count).append(", ").getRaw(count-pre)
                                    .append(", "+count+"*sizeof("+STACK_DATA_TYPE+"))").endLine();
                        }
                    }
                    case CURRIED_LAMBDA -> throw new UnsupportedOperationException("compiling CURRIED_LAMBDA  is currently not implemented");
                    case NEW_ARRAY -> throw new UnsupportedOperationException("compiling NEW_ARRAY  is currently not implemented");
                    case ASSERT -> throw new UnsupportedOperationException("compiling ASSERT  is currently not implemented");
                    case SWITCH -> throw new UnsupportedOperationException("compiling SWITCH  is currently not implemented");
                    case TUPLE_REFERENCE_TO -> throw new UnsupportedOperationException("compiling TUPLE_REFERENCE_TO  is currently not implemented");
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

    private static void pushValue(Parser.CodeSection section, CodeGenerator generator, Value value) throws IOException, TypeError {
        BaseType baseType = value.type.baseType();
        if(baseType instanceof BaseType.Primitive primitive){
            switch (primitive.type){
                case BOOL -> generator.pushPrimitive(primitive).appendBool(value.asBool()).endLine();
                case TYPE -> generator.pushPrimitive(primitive).appendType(value.asType()).endLine();
                case INTEGER -> {
                    BaseType.Primitive.Int asInt=(BaseType.Primitive.Int) primitive;
                    generator.pushPrimitive(primitive).appendInt(value.asLong(), asInt.bitCount,!asInt.unsigned).endLine();
                }
                case FLOAT ->
                        throw new UnsupportedEncodingException("float literals are currently not supported");
                case OPTIONAL_I32,OPTIONAL_U32 -> {
                    int depth=0;
                    while(value.type.isOptional()&&value.hasValue()){
                        try {
                            value=value.unwrap();
                        } catch (ConcatRuntimeError e) {
                            throw new RuntimeException(e);
                        }
                        depth++;
                    }
                    boolean signed= baseType== BaseType.Primitive.OPTIONAL_I32;
                    if(!value.type.isOptional()) {
                        generator.getPrimitive(0, (BaseType.StackValue) baseType).append("[0] = ")
                                .appendInt(value.asLong(), 32, signed).endLine();
                    }
                    generator.getPrimitive(0,(BaseType.StackValue) baseType).append("[1] = ")
                            .appendInt(depth, 32,signed).append("").endLine()
                            .changeStackPointer(1).endLine();
                }
            }
        }else if(value instanceof Value.Procedure proc){
            generator.pushPrimitive(BaseType.StackValue.F_PTR)
                    .append("&"+(proc.isPublic? PUBLIC_PROC_PREFIX : PRIVATE_PROC_PREFIX)+idOf(proc))
                    .endLine();
            generator.pushPointer(Type.ANY).append("NULL").endLine();
        }else if(value instanceof Value.ArrayLike){
            FilePosition firstDeclaration= section.context().root().constArrays().get(value);
            if(firstDeclaration==null){
                throw new UnsupportedOperationException("currently only constant arrays are supported");
            }
            generator.pushPointer(((Value.ArrayLike) value).contentType())
                    .append(CONST_ARRAY_PREFIX+idOf(firstDeclaration)).endLine();
            generator.pushPrimitive(Type.UINT())
                    .appendInt(value.length(),64,false).endLine();
        }else if(value.type instanceof Type.Tuple||value.type instanceof Type.Struct){
            if(baseType == BaseType.StackValue.PTR){
                throw new UnsupportedOperationException("tuples of type " + value.type + " are currently not supported");
            }
            for(Value v:value.getElements()){
                pushValue(section, generator, v);
            }
        }else if(value.type.isOptional()){
            if(!(baseType instanceof BaseType.Composite)){
                throw new UnsupportedOperationException("optionals of type " + value.type + " are currently not supported");
            }
            int depth=0;
            while(value.type.isOptional()&&value.hasValue()){
                try {
                    value=value.unwrap();
                } catch (ConcatRuntimeError e) {
                    throw new RuntimeException(e);
                }
                depth++;
            }
            int count= baseType.blockCount();
            if(value.type.isOptional()){
                generator.changeStackPointer(count-1).endLine();
            }else{
                try {
                    pushValue(section, generator, value);
                } catch (ConcatRuntimeError e) {
                    throw new RuntimeException(e);
                }
            }
            generator.pushPrimitive(Type.UINT()).appendInt(depth,64,false).endLine();
        }else{
            throw new UnsupportedOperationException("values of type " + value.type + " are currently not supported");
        }
    }

    private static void printPrimitive(CodeGenerator generator, int offset, BaseType.StackValue value, boolean padLeft) throws IOException {
        generator.append("printf(\""+(padLeft?" ":"")).append(value.cType);
        if(value instanceof BaseType.Primitive.Int asInt){
            if(asInt.isPtr){
                generator.append(": %p\"");
            }else{
                generator.append(": %\"PRI"+(asInt.unsigned ? "u" : "i")+asInt.bitCount);
            }
            generator.append(", ").getPrimitive(offset,value).append(")").endLine();
        }else if(value instanceof BaseType.Primitive primitive){
            if(primitive.isPtr){
                generator.append(": %p");
            }else{
                switch (primitive.type){
                    case BOOL -> {
                        generator.append(": %s\", ").getPrimitive(offset, value).append(" ? \"true\" : \"false\")").endLine();
                        return;
                    }
                    case FLOAT ->
                            generator.append(": %f");
                    case TYPE ->
                            generator.append(": %\"PRIx64\"");
                    case INTEGER ->
                        throw new RuntimeException("unexpected Integer primitive");
                    case OPTIONAL_I32 ->{
                        generator.append(": %\"PRIi32\" %\"PRIi32, ").getPrimitive(offset,value).append("[0], ")
                                .getPrimitive(offset,value).append("[1])").endLine();
                        return;
                    }
                    case OPTIONAL_U32 -> {
                        generator.append(": %\"PRIu32\" %\"PRIu32, ").getPrimitive(offset, value).append("[0], ")
                                .getPrimitive(offset, value).append("[1])").endLine();
                        return;
                    }
                }
            }
            generator.append("\", ").getPrimitive(offset,value).append(")").endLine();
        }else if(value == BaseType.StackValue.F_PTR|| value == BaseType.StackValue.PTR){
            generator.append(": %p\", ").getPrimitive(offset,value).append(")").endLine();
        }else{
            throw new UnsupportedOperationException("unexpected stackValue:"+ value.name);
        }
    }

    private static void compileCast(CodeGenerator generator, Type src, Type target, int offset) throws IOException {
        if(src instanceof Type.Procedure&&target instanceof Type.Procedure){
            assert src.canCastTo(target)!= Type.CastType.NONE;//casting between procedures only changes type-info
        }else{
            BaseType b1=src.baseType();
            BaseType b2=target.baseType();
            if(b1 instanceof BaseType.Primitive&&b2 instanceof BaseType.Primitive){
                if(((BaseType.Primitive) b1).isPtr||((BaseType.Primitive) b2).isPtr){
                    throw new UnsupportedOperationException("casting from "+src+" to "+target+" is currently not supported");
                }
                //TODO check if direct C-cast is allowed
                generator.assignPrimitive(offset,target).getPrimitive(offset,src).endLine();
            }else {
                throw new UnsupportedOperationException("casting from "+src+" to "+target+" is currently not supported");
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
