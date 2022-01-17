package bsoelch.concat;

import bsoelch.concat.streams.FileStream;
import bsoelch.concat.streams.RandomAccessFileStream;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Interpreter {
    public static final String DEFAULT_FILE_EXTENSION = ".concat";
    //use ## for end of file since it cannot appear in normal tokens
    public static final String END_OF_FILE = "##";
    //use ' as separator for modules, as it cannot be part of identifiers
    public static final String MODULE_SEPARATOR = "'";

    /**Context for running the program*/
    static class IOContext{
        final InputStream stdIn;
        final PrintStream stdOut;
        final PrintStream stdErr;

        final Value stdInValue;
        final Value stdOutValue;
        final Value stdErrValue;
        IOContext(InputStream stdIn, PrintStream stdOut, PrintStream stdErr){
            this.stdIn  = stdIn;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
            stdInValue = Value.ofFile(FileStream.of(stdIn ,false));
            stdOutValue = Value.ofFile(FileStream.of(stdOut,false));
            stdErrValue = Value.ofFile(FileStream.of(stdErr,false));
        }
    }
    static final IOContext defaultContext=new IOContext(System.in,System.out,System.err);

    //addLater enums
    /*
    <Name> enum
      <Name>
      <Name>
      <Name>
      <Name>
    end
    * */
    enum TokenType {
        VALUE,CURRIED_PROCEDURE,OPERATOR,
        STD_IN,STD_OUT,STD_ERR,
        DROP,DUP,
        DECLARE,CONST_DECLARE, IDENTIFIER,MACRO_EXPAND, PROC_CALL, PROC_ID,VAR_WRITE,//addLater option to free values/variables
        VARIABLE,
        PLACEHOLDER,//placeholder token for jumps
        CONTEXT_OPEN,CONTEXT_CLOSE,
        CALL_PROC, PUSH_PROC_PTR,CALL_PTR,
        RETURN,
        DEBUG_PRINT,
        SHORT_AND_JMP, SHORT_OR_JMP,JEQ,JNE,JMP,//jump commands only for internal representation
        SWITCH,
        EXIT
    }

    static class FilePosition{
        final String path;
        final long line;
        final int posInLine;
        final FilePosition expandedAt;
        FilePosition(String path, long line, int posInLine) {
            this.path=path;
            this.line=line;
            this.posInLine=posInLine;
            expandedAt=null;
        }
        FilePosition(FilePosition at,FilePosition expandedAt) {
            this.path=at.path;
            this.line=at.line;
            this.posInLine=at.posInLine;
            this.expandedAt=expandedAt;
        }

        @Override
        public String toString() {
            if(expandedAt!=null){
                return path+":"+line + ":" + posInLine+
                "\nexpanded at "+expandedAt;
            }else{
                return path+":"+line + ":" + posInLine;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FilePosition that = (FilePosition) o;
            return line == that.line && posInLine == that.posInLine &&
                    Objects.equals(path, that.path) && Objects.equals(expandedAt, that.expandedAt);
        }
        @Override
        public int hashCode() {
            return Objects.hash(path, line, posInLine, expandedAt);
        }
    }

    record StringWithPos(String str,FilePosition start){
        @Override
        public String toString() {
            return str + "at "+ start;
        }
    }
    record Macro(FilePosition pos, String name,
                 ArrayList<StringWithPos> content) {
        @Override
        public String toString() {
            return "macro " +name+":"+content;
        }
    }

    enum VariableType{
        GLOBAL,LOCAL,CURRIED
    }
    enum AccessType{
        READ,WRITE,DECLARE,CONST_DECLARE
    }

    static class Token {
        final TokenType tokenType;
        final FilePosition pos;
        Token(TokenType tokenType, FilePosition pos) {
            this.tokenType = tokenType;
            this.pos = pos;
        }
        @Override
        public String toString() {
            return tokenType.toString();
        }
    }
    static class IdentifierToken extends Token {
        final String name;
        IdentifierToken(TokenType type, String name,FilePosition pos) throws SyntaxError {
            super(type, pos);
            this.name = name;
            if(name.isEmpty()){
                throw new SyntaxError("empty variable name",pos);
            }
        }
        @Override
        public String toString() {
            return tokenType.toString()+": \""+ name +"\"";
        }
    }
    static class OperatorToken extends Token {
        final OperatorType opType;
        OperatorToken(OperatorType opType, FilePosition pos) {
            super(TokenType.OPERATOR, pos);
            this.opType=opType;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+opType;
        }
    }
    static class ValueToken extends Token {
        final Value value;
        final boolean cloneOnCreate;
        ValueToken(Value value, FilePosition pos, boolean cloneOnCreate) {
            super(TokenType.VALUE, pos);
            this.value=value;
            this.cloneOnCreate = cloneOnCreate;
        }
        ValueToken(TokenType type, Value value, FilePosition pos, boolean cloneOnCreate) {
            super(type, pos);
            this.value=value;
            this.cloneOnCreate = cloneOnCreate;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+value;
        }
    }
    static class StackModifierToken extends Token{
        final long off,count;
        StackModifierToken(boolean isDup,long off,long count,FilePosition pos) {
            super(isDup?TokenType.DUP:TokenType.DROP,pos);
            this.off = off;
            this.count = count;
        }
    }
    static class RelativeJump extends Token{
        final int delta;
        RelativeJump(TokenType tokenType, FilePosition pos, int delta) {
            super(tokenType, pos);
            this.delta = delta;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+(delta>0?"+":"")+delta;
        }
    }
    static class ContextOpen extends Token{
        final VariableContext context;
        ContextOpen(VariableContext context,FilePosition pos) {
            super(TokenType.CONTEXT_OPEN,pos);
            this.context = context;
        }
    }
    static class SwitchToken extends Token{
        final SwitchCaseBlock block;
        SwitchToken(SwitchCaseBlock block,FilePosition pos) {
            super(TokenType.SWITCH,pos);
            this.block = block;
        }
    }
    static class ProcedureToken extends Token {
        private Value.Procedure value;

        ProcedureToken(boolean isPtr,Value.Procedure value, FilePosition pos) {
            super(isPtr?TokenType.PUSH_PROC_PTR :TokenType.CALL_PROC, pos);
            this.value=value;
        }

        void declareProcedure(Value.Procedure value){
            if(this.value!=null){
                throw new RuntimeException("procedure can only be initialized once");
            }
            this.value=value;
        }

        Value.Procedure getProcedure() throws ConcatRuntimeError {
            if(value==null){
                throw new ConcatRuntimeError("missing procedure");
            }
            return value;
        }

        @Override
        public String toString() {
            return tokenType.toString()+": "+value;
        }
    }

    static class VariableToken extends Token{
        final VariableType variableType;
        final AccessType accessType;
        final VariableId id;
        final String variableName;
        VariableToken(FilePosition pos,String name,VariableId id,AccessType access,VariableContext currentContext) throws SyntaxError {
            super(TokenType.VARIABLE, pos);
            this.variableName=name;
            this.id=id;
            this.accessType=access;
            ProcedureContext procedureId=id.context.procedure();
            if(procedureId==null){
                variableType=VariableType.GLOBAL;
            }else if(procedureId==currentContext.procedure()){
                if(id instanceof CurriedVariable){
                    variableType=VariableType.CURRIED;
                    switch (accessType){
                        case READ -> {}
                        case WRITE ->
                                throw new SyntaxError("cannot write to curried variable "+name,pos);
                        case DECLARE,CONST_DECLARE ->
                                throw new RuntimeException("cannot declare to curried variables "+name);
                        default -> throw new RuntimeException("unreachable");
                    }
                }else{
                    variableType=VariableType.LOCAL;
                }
            }else{
                throw new RuntimeException("all variables (including curried ones) should be global, " +
                        "or part of the current context");
            }
            if(access==AccessType.WRITE){
                if(id.predeclared){
                    throw new SyntaxError("cannot write to predeclared variable "+name,pos);
                }else if(id.isConstant){
                    throw new SyntaxError("cannot write to constant variable "+name,pos);
                }
            }
        }
        @Override
        public String toString() {
            return variableType+"_"+accessType +":" +(variableType==VariableType.CURRIED?id:id.id)+" ("+variableName+")";
        }
    }


    private static void checkElementCount(long count) throws ConcatRuntimeError {
        if(count<0){
            throw new ConcatRuntimeError("the element count has to be at least 0");
        }else if(count >Integer.MAX_VALUE){
            throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
        }
    }

    enum BlockType{
        PROCEDURE,IF,WHILE,SHORT_AND,SHORT_OR,SWITCH_CASE,
    }
    static abstract class CodeBlock{
        final int start;
        final BlockType type;
        final FilePosition startPos;
        final VariableContext parentContext;
        CodeBlock(int start, BlockType type, FilePosition startPos, VariableContext parentContext) {
            this.start = start;
            this.type = type;
            this.startPos = startPos;
            this.parentContext = parentContext;
        }
        abstract VariableContext context();
    }
    static class ProcedureBlock extends CodeBlock{
        final String name;
        final ProcedureContext context;
        Type[] inTypes=null,outTypes=null;

        ProcedureBlock(String name, int startToken, FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.PROCEDURE,pos, parentContext);
            this.name = name;
            context=new ProcedureContext(parentContext);
        }
        private Type[] getSignature(List<Token> tokens) throws SyntaxError {
            RandomAccessStack<ValueToken> stack=new RandomAccessStack<>(tokens.size());
            for(Token t:tokens){
                //addLater generics
                if(t instanceof ValueToken){
                    stack.push(((ValueToken) t));
                }else{//list, optional, tuple, -> are evaluated in parser
                    throw new SyntaxError("Tokens of type "+t.tokenType+
                            " are not supported in procedure signatures",t.pos);
                }
            }
            Type[] types=new Type[stack.size()];
            int i=types.length;
            try {
                while(i>0){
                    ValueToken v=stack.pop();
                    if(v.value.type.isSubtype(Type.TYPE)){
                        types[--i]=v.value.asType();
                    }else{
                        throw new SyntaxError("Elements in procedure signature have to evaluate to types",v.pos);
                    }
                }
            } catch (TypeError|RandomAccessStack.StackUnderflow e) {
                throw new RuntimeException(e);
            }
            return types;
        }
        void addIns(List<Token> ins,FilePosition pos) throws SyntaxError {
            if(inTypes!=null){
                throw new SyntaxError("Procedure already has input arguments",pos);
            }
            inTypes = getSignature(ins);
            ins.clear();
        }
        void addOuts(List<Token> outs,FilePosition pos) throws SyntaxError {
            if(inTypes==null){
                throw new SyntaxError("procedure declares output arguments but no input arguments",pos);
            }else if(outTypes!=null){
                throw new SyntaxError("Procedure already has output arguments",pos);
            }
            outTypes = getSignature(outs);
            outs.clear();
        }
        @Override
        ProcedureContext context() {
            return context;
        }
    }
    private record IfBranch(int fork,int end){}
    static class IfBlock extends CodeBlock{
        ArrayList<IfBranch> branches=new ArrayList<>();
        int forkPos=-1;
        int elsePos=-1;
        VariableContext ifContext;
        VariableContext elseContext;
        IfBlock(int startToken,FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.IF,pos, parentContext);
            elseContext=parentContext;
        }
        void end(FilePosition pos) throws SyntaxError {
            if(forkPos==-1&&elsePos==-1){
                throw new SyntaxError("unexpected 'end' in if-statement 'end' can only appear after ':' or 'else'",pos);
            }
        }
        VariableContext fork(int tokenPos,FilePosition pos) throws SyntaxError {
            if(elsePos!=-1||forkPos!=-1){
                throw new SyntaxError("unexpected ':' in if-statement ':' can only appear after 'if' or 'elif'",pos);
            }
            forkPos=tokenPos;
            ifContext=new BlockContext(elseContext);
            return ifContext;
        }
        VariableContext newBranch(int tokenPos,FilePosition pos) throws SyntaxError{
            if(forkPos==-1||elsePos!=-1){
                throw new SyntaxError("unexpected 'if' or 'elif' in if-statement 'if'/'elif'" +
                        " can only appear after ':'",pos);
            }
            branches.add(new IfBranch(forkPos,tokenPos));
            forkPos=-1;
            if(elseContext==parentContext){
                elseContext=new BlockContext(parentContext);
                return elseContext;
            }
            return null;
        }
        VariableContext elseBranch(int tokenPos,FilePosition pos) throws SyntaxError {
            if(elsePos!=-1){
                throw new SyntaxError("duplicate 'else' in if-statement 'else' can appear at most once",pos);
            }else if(forkPos==-1){
                throw new SyntaxError("unexpected 'else' in if-statement 'else' can only appear after ':'",pos);
            }
            branches.add(new IfBranch(forkPos,tokenPos));
            forkPos=-1;
            elsePos=tokenPos;
            if(elseContext==parentContext){
                elseContext=new BlockContext(parentContext);
                return elseContext;
            }
            return null;
        }

        @Override
        VariableContext context() {
            return forkPos==-1?elseContext:ifContext;
        }
    }
    static class ShortCircuitBlock extends CodeBlock{
        int forkPos=-1;
        VariableContext context;
        ShortCircuitBlock(int startToken,FilePosition pos,boolean isAnd, VariableContext parentContext) {
            super(startToken, isAnd?BlockType.SHORT_AND:BlockType.SHORT_OR,pos, parentContext);
            context=parentContext;
        }
        void end(FilePosition pos) throws SyntaxError {
            if(forkPos==-1){
                throw new SyntaxError("unexpected 'end' in short-circuit-statement " +
                        "'end' can only appear after ':'",pos);
            }
        }
        VariableContext fork(int tokenPos,FilePosition pos) throws SyntaxError{
            if(forkPos!=-1){
                throw new SyntaxError("duplicate ':' in short-circuit-statement ':' " +
                        "can only appear after '&&' or '||'",pos);
            }
            forkPos=tokenPos;
            context=new BlockContext(context);
            return context;
        }

        @Override
        VariableContext context() {
            return context;
        }
    }
    static class WhileBlock extends CodeBlock{
        int forkPos=-1;
        VariableContext context;
        WhileBlock(int startToken,FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.WHILE,pos, parentContext);
            context=parentContext;
        }
        void end(FilePosition pos) throws SyntaxError {
            if(forkPos==-1){
                throw new SyntaxError("unexpected 'end' in while-statement " +
                        "'end' can only appear after ':'",pos);
            }
        }
        VariableContext fork(int tokenPos,FilePosition pos) throws SyntaxError {
            if(forkPos!=-1){
                throw new SyntaxError("duplicate ':' in while-statement ':' " +
                        "can only appear after 'while'",pos);
            }
            forkPos=tokenPos;
            context=new BlockContext(context);
            return context;
        }

        @Override
        VariableContext context() {
            return context;
        }
    }

    private static class SwitchCaseBlock extends CodeBlock{
        int sectionStart=-1;
        int blockStart =-1;

        int defaultJump=-1;

        HashMap<Value,Integer> blockJumps =new HashMap<>();
        ArrayDeque<Integer> blockEnds=new ArrayDeque<>();
        Type switchType;
        VariableContext context;

        SwitchCaseBlock(int start,FilePosition startPos, VariableContext parentContext) {
            super(start, BlockType.SWITCH_CASE, startPos, parentContext);
        }
        void newSection(int start, FilePosition pos) throws SyntaxError {
            context=null;
            if(sectionStart!=-1||defaultJump!=-1){
                throw new SyntaxError("unexpected 'end-case' statement",pos);
            }else{
                sectionStart=start;
                if(blockStart !=-1){
                    blockEnds.add(start-1);
                    blockStart =-1;
                }
            }
        }
        VariableContext caseBlock(List<Token> caseValues,FilePosition pos) throws SyntaxError {
            if(sectionStart==-1||defaultJump!=-1){
                throw new SyntaxError("unexpected 'case' statement",pos);
            }else if(caseValues.isEmpty()){
                throw new SyntaxError("empty 'case' statement",pos);
            }
            for (Token token : caseValues) {
                if (!(token instanceof ValueToken)) {
                    throw new SyntaxError("case values have to be constant values ", token.pos);
                }
                Value v = ((ValueToken) token).value;
                if (!v.type.switchable) {
                    throw new SyntaxError("values of type " + v.type +
                            " cannot be used in switch-case statements", token.pos);
                }
                if (switchType == null) {
                    switchType = v.type;
                } else if (!switchType.equals(v.type)) {
                    throw new SyntaxError("values of type " + v.type +
                            " cannot be used in "+switchType+" switch-case statements", token.pos);
                } //no else
                if (blockJumps.put(v, sectionStart-start) != null) {
                    throw new SyntaxError("duplicate entry in switch case " + v, token.pos);
                }
            }
            blockStart =sectionStart;
            sectionStart=-1;
            context=new BlockContext(parentContext);
            return parentContext;
        }
        VariableContext defaultBlock(int blockStart, FilePosition pos) throws SyntaxError {
            if(sectionStart==-1||blockStart!=sectionStart){
                throw new SyntaxError("unexpected 'default' statement",pos);
            }else if(defaultJump!=-1){
                throw new SyntaxError("this switch case already has a 'default' statement",pos);
            }
            defaultJump=blockStart-start;
            context=new BlockContext(parentContext);
            return context;
        }
        void end(int endPos,FilePosition pos) throws SyntaxError {
            if(blockStart !=-1||(defaultJump==-1&&endPos>sectionStart)){
                throw new SyntaxError("unfinished case-block",pos);
            }else if(switchType==null){
                throw new SyntaxError("switch-case does not contain any case block",pos);
            }
            if(defaultJump==-1) {
                defaultJump = endPos - start;
            }
            //TODO check enum-switch-case
        }


        @Override
        VariableContext context() {
            return context==null?parentContext:context;
        }
    }

    private Interpreter() {}

    static String libPath=System.getProperty("user.dir")+File.separator+"lib/";

    static final String DEC_DIGIT = "[0-9]";
    static final String BIN_DIGIT = "[01]";
    static final String HEX_DIGIT = "[0-9a-fA-F]";
    static final String UNSIGNED_POSTFIX = "[u|U]?";
    static final String BIN_PREFIX = "0b";
    static final String HEX_PREFIX = "0x";
    static final String DEC_INT_PATTERN = "-?"+DEC_DIGIT + "+";
    static final String BIN_INT_PATTERN = "-?"+BIN_PREFIX + BIN_DIGIT + "+";
    static final String HEX_INT_PATTERN = "-?"+HEX_PREFIX + HEX_DIGIT + "+";
    static final Pattern intDec=Pattern.compile(DEC_INT_PATTERN+UNSIGNED_POSTFIX);
    static final Pattern intBin=Pattern.compile(BIN_INT_PATTERN+UNSIGNED_POSTFIX);
    static final Pattern intHex=Pattern.compile(HEX_INT_PATTERN+UNSIGNED_POSTFIX);
    static final String DEC_FLOAT_MAGNITUDE = DEC_INT_PATTERN + "\\.?"+DEC_DIGIT+"*";
    static final String BIN_FLOAT_MAGNITUDE = BIN_INT_PATTERN + "\\.?"+BIN_DIGIT+"*";
    static final String HEX_FLOAT_MAGNITUDE = HEX_INT_PATTERN + "\\.?"+HEX_DIGIT+"*";
    static final Pattern floatDec=Pattern.compile("NaN|Infinity|("+
            DEC_FLOAT_MAGNITUDE+"([Ee][+-]?"+DEC_DIGIT+"+)?)");
    static final Pattern floatHex=Pattern.compile(HEX_FLOAT_MAGNITUDE+"([PpXx#][+-]?"+HEX_DIGIT+"+)?");
    static final Pattern floatBin=Pattern.compile(BIN_FLOAT_MAGNITUDE+"([EePpXx#][+-]?"+BIN_DIGIT+"+)?");

    enum WordState{
        ROOT,STRING,UNICODE_STRING,COMMENT,LINE_COMMENT
    }

    static class ParserReader{
        final Reader input;
        final StringBuilder buffer=new StringBuilder();

        int cached=-1;

        private final String path;
        private int line =1;
        private int posInLine =1;
        private FilePosition currentPos;
        private FilePosition nextPos;

        private ParserReader(String path) throws SyntaxError {
            this.path=path;
            try {
                this.input = new BufferedReader(new FileReader(path));
            } catch (FileNotFoundException e) {
                throw new SyntaxError("File not found",new FilePosition(path,0,0));
            }
        }

        int nextChar() throws IOException {
            int c;
            if(cached>=0){
                c=cached;
                cached = -1;
            }else{
                c=input.read();
            }
            posInLine++;
            if(c=='\n'){//addLater? support for \r line separator
                line++;
                posInLine=1;
            }
            return c;
        }
        int forceNextChar() throws IOException, SyntaxError {
            int c=nextChar();
            if (c < 0) {
                throw new SyntaxError("Unexpected end of File",currentPos());
            }
            return c;
        }
        FilePosition currentPos() {
            if(currentPos==null){
                currentPos=new FilePosition(path,line, posInLine);
            }
            return currentPos;
        }
        void nextToken() {
            currentPos=nextPos;
            buffer.setLength(0);
            nextPos=new FilePosition(path, line, posInLine);
        }
    }

    private static class VariableId{
        final boolean predeclared;
        final VariableContext context;
        final int level;
        int id;
        final boolean isConstant;
        final FilePosition declaredAt;
        FilePosition initializedAt;
        VariableId(VariableContext context,int level,int id,boolean predeclared,boolean isConstant,
                   FilePosition declaredAt,FilePosition initializedAt){
            this.predeclared=predeclared;
            this.context=context;
            this.id=id;
            this.level=level;
            this.isConstant=isConstant;
            this.declaredAt=declaredAt;
            this.initializedAt=initializedAt;
        }
        @Override
        public String toString() {
            return "@"+context+"."+level+"-"+id;
        }
    }
    private static class CurriedVariable extends VariableId{
        final VariableId source;
        CurriedVariable(VariableId source,VariableContext context, int id, FilePosition declaredAt) {
            super(context,0, id, false, true, declaredAt, declaredAt);
            this.source = source;
        }
        @Override
        public String toString() {
            return "@"+context+".curried"+id;
        }
    }

    record ModuleBlock(String[] path,ArrayList<String> imports,FilePosition declaredAt){
        @Override
        public String toString() {
            return Arrays.toString(path) +" declaredAt " + declaredAt;
        }
    }
    static abstract class VariableContext{
        final HashMap<String,VariableId> variables=new HashMap<>();
        VariableId declareVariable(String name,boolean isConstant,FilePosition pos,IOContext ioContext) throws SyntaxError {
            VariableId prev=variables.get(name);
            if(prev!=null){
                throw new SyntaxError("variable "+name+" already exists",pos);
            }
            VariableId id = new VariableId(this,level(), nextId(), false,isConstant, pos,pos);
            variables.put(name, id);
            return id;
        }
        int nextId() {
            return variables.size();
        }

        abstract VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError;
        abstract VariableId unsafeGetId(String name);
        /**returns the enclosing procedure or null if this variable is not enclosed in a procedure*/
        abstract ProcedureContext procedure();
        /**number of blocks (excluding procedures) this variable is contained in*/
        abstract int level();
    }

    /**File specific part of module parsing*/
    private static class FileContext{
        final ArrayList<String> globalImports=new ArrayList<>();
        final ArrayList<ModuleBlock> openModules=new ArrayList<>();
        final HashMap<String,VariableId> declaredVars =new HashMap<>();
        final HashMap<String,Macro> declaredMarcos =new HashMap<>();
    }
    record PredeclaredProc(FilePosition pos,ArrayDeque<ProcedureToken> listeners){}
    private static class RootContext extends VariableContext{
        RootContext(){}

        final HashMap<String,Macro> macros=new HashMap<>();

        final HashMap<String, Value.Procedure> procedures = new HashMap<>();
        final HashMap<String,PredeclaredProc> predeclaredProcs = new HashMap<>();

        ArrayDeque<FileContext> openFiles=new ArrayDeque<>();

        private FileContext file(){
            return openFiles.peekLast();
        }
        void startFile(){
            openFiles.addLast(new FileContext());
        }
        void startModule(String moduleName,FilePosition declaredAt){
            file().openModules.add(new ModuleBlock(moduleName.split(MODULE_SEPARATOR),new ArrayList<>(),declaredAt));
        }
        void addImport(String path){
            path+="'";
            if(file().openModules.size()>0){
                file().openModules.get(file().openModules.size()-1).imports.add(path);
            }else{
                file().globalImports.add(path);
            }
        }
        void endModule(FilePosition pos) throws SyntaxError {
            if(file().openModules.isEmpty()){
                throw new SyntaxError("Unexpected End of module",pos);
            }
            if(predeclaredProcs.size()>0){
                StringBuilder message=new StringBuilder("Syntax Error: missing procedures");
                for(Map.Entry<String, PredeclaredProc> p:predeclaredProcs.entrySet()){
                    message.append("\n- ").append(p.getKey()).append(" (called at ").append(p.getValue().pos).append(")");
                }
                throw new SyntaxError(message.toString(),pos);
            }
            file().openModules.remove(file().openModules.size() - 1);
        }
        void endFile(IOContext context){
            FileContext ctx=openFiles.removeLast();
            if(ctx.openModules.size()>0) {
                context.stdErr.println("unclosed modules at end of File:");
                while(ctx.openModules.size()>0){
                    context.stdErr.println(" - "+ctx.openModules.remove(ctx.openModules.size()-1));
                }
            }
        }
        private String inCurrentModule(String name){
            if(file().openModules.size()>0){
                StringBuilder path=new StringBuilder();
                for(ModuleBlock b:file().openModules){
                    for(String s:b.path){
                        path.append(s).append(MODULE_SEPARATOR);
                    }
                }
                return path.append(name).toString();
            }
            return name;
        }
        private ArrayDeque<String> currentPaths() {
            ArrayDeque<String> paths = new ArrayDeque<>(file().globalImports);
            StringBuilder path=new StringBuilder();
            for(ModuleBlock m:file().openModules){
                for(String s:m.path){
                    path.append(s).append(MODULE_SEPARATOR);
                    paths.add(path.toString());
                }
                String top=paths.removeLast();
                paths.addAll(m.imports);
                paths.add(top);//push imports below top path
            }
            return paths;
        }

        void declareProcedure(String name, Value.Procedure proc) {
            PredeclaredProc predeclared=predeclaredProcs.remove(name);
            if(predeclared!=null){
                for(ProcedureToken token:predeclared.listeners){
                    token.declareProcedure(proc);
                }
            }
            name=inCurrentModule(name);
            procedures.put(name,proc);
        }
        boolean hasProcedure(String name){
            if(predeclaredProcs.containsKey(name)){
                return true;
            }
            ArrayDeque<String> paths = currentPaths();
            while(paths.size()>0){//go through all modules
                if(procedures.containsKey(paths.removeLast()+name)){
                    return true;
                }
            }
            return procedures.containsKey(name);
        }
        /**@return the procedure with the given name or null if there is no such procedure*/
        Value.Procedure getProcedure(String name,FilePosition pos) throws SyntaxError {
            Value.Procedure p;
            ArrayDeque<String> paths = currentPaths();
            while(paths.size()>0){//go through all modules
                p=procedures.get(paths.removeLast()+name);
                if(p!=null){
                    return p;
                }
            }
            p=procedures.get(name);
            if(p==null&&!predeclaredProcs.containsKey(name)){
                throw new SyntaxError("procedure "+name+" does not exists",pos);
            }
            return p;
        }
        public void addProcDeclareListener(String name, ProcedureToken token) {
            PredeclaredProc predeclared=predeclaredProcs.get(name);
            if(predeclared==null){
                throw new RuntimeException(name+" is no predeclared procedure");
            }
            predeclared.listeners.add(token);
        }

        void declareMacro(Macro macro,IOContext ioContext) throws SyntaxError{
            String name=inCurrentModule(macro.name);
            if(variables.containsKey(name)){
                throw new SyntaxError("cannot declare macro "+macro.name+
                        ", the identifier is already used by a variable (declared at "+variables.get(name).declaredAt+")",
                        macro.pos);
            }else if(macros.put(name,macro)!=null){
                throw new SyntaxError("macro "+macro.name+" does already exists",macro.pos);
            }
            VariableId shadowedVar = file().declaredVars.get(macro.name);
            if(shadowedVar!=null){
                ioContext.stdErr.println("Warning: macro " + macro.name + " declared at " + macro.pos +
                        "\n     shadows existing " + (shadowedVar.isConstant ? "constant" : "variable") + " declared at "
                        + shadowedVar.declaredAt);
            }
            Macro shadowedMacro = file().declaredMarcos.get(macro.name);
            if(shadowedMacro!=null){
                ioContext.stdErr.println("Warning: macro " + macro.name + " declared at " + macro.pos +
                        "\n     shadows existing macro declared at "+ shadowedMacro.pos);
            }
            file().declaredMarcos.put(macro.name,macro);
        }
        Macro getMacro(String name, FilePosition pos) throws SyntaxError {
            Macro m;
            ArrayDeque<String> paths = currentPaths();
            while(paths.size()>0){//go through all modules
                m=macros.get(paths.removeLast()+name);
                if(m!=null){
                    return m;
                }
            }
            m= macros.get(name);
            if(m==null){
                throw new SyntaxError("macro "+name+" does not exists",pos);
            }
            return m;
        }
        boolean hasMacro(String name){
            ArrayDeque<String> paths = currentPaths();
            while(paths.size()>0){//go through all modules
                if(macros.containsKey(paths.removeLast()+name)){
                    return true;
                }
            }
            return macros.containsKey(name);
        }
        void removeMacro(String name, FilePosition pos) throws SyntaxError {
            if(macros.remove(inCurrentModule(name))==null){
                throw new SyntaxError("macro "+name+" does not exists in the current module",pos);
            }
        }

        @Override
        VariableId declareVariable(String name,boolean isConstant,FilePosition pos,IOContext ioContext) throws SyntaxError {
            String name0=name;
            name=inCurrentModule(name);
            PredeclaredProc predeclaredProc = predeclaredProcs.get(name);
            if(predeclaredProc!=null){//initialize predeclared variable
                throw new SyntaxError("cannot declare variable "+name+
                        ", it is already used for a predeclared procedure call (at "+predeclaredProc.pos+")",pos);
            }
            VariableId id = super.declareVariable(name, isConstant, pos,ioContext);
            VariableId shadowed = file().declaredVars.get(name0);
            if(shadowed!=null){
                ioContext.stdErr.println("Warning: variable " + name0 + " declared at " + pos +
                        "\n     shadows existing " + (shadowed.isConstant ? "constant" : "variable") + " declared at "
                        + shadowed.declaredAt);
            }
            file().declaredVars.put(name0,id);
            return id;
        }

        @Override
        VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError {
            ArrayDeque<String> paths = currentPaths();
            VariableId id;
            while(paths.size()>0){//go through all modules
                id=variables.get(paths.removeLast()+name);
                if(id!=null){
                    return id;
                }
            }
            id=variables.get(name);
            if(id==null){
                String localName=inCurrentModule(name);
                PredeclaredProc predeclared=predeclaredProcs.get(localName);
                if(predeclared==null){
                    if(callee.procedure()!=null){
                        predeclaredProcs.put(name,new PredeclaredProc(pos,new ArrayDeque<>()));
                        // file().declaredVars add label to macro
                    }else{
                        throw new SyntaxError("Variable "+name+" does not exist",pos);
                    }
                }
            }
            return id;
        }

        @Override
        VariableId unsafeGetId(String name) {
            String name0=name;
            name=inCurrentModule(name);
            VariableId id=variables.get(name);
            if(id==null){
                id= file().declaredVars.get(name0);
            }
            return id;
        }

        @Override
        ProcedureContext procedure() {
            return null;
        }

        @Override
        int level() {
            return 0;
        }
    }
    private static class BlockContext extends VariableContext {
        final VariableContext parent;

        public BlockContext(VariableContext parent) {
            this.parent = parent;
            assert parent!=null;
        }
        @Override
        VariableId declareVariable(String name, boolean isConstant, FilePosition pos,IOContext ioContext) throws SyntaxError {
            VariableId id = super.declareVariable(name, isConstant, pos,ioContext);
            VariableId shadowed = parent.unsafeGetId(name);
            if (shadowed != null) {//check for shadowing
                if (shadowed.initializedAt == null) {
                    throw new SyntaxError("unexpected initialization of predeclared constant " + name +
                            ", predeclared constants have to be initialized at global level " +
                            "\n  (" + name + " was declared at " + shadowed.declaredAt + ")", pos);
                }
                ioContext.stdErr.println("Warning: variable " + name + " declared at " + pos +
                        "\n     shadows existing " + (shadowed.isConstant ? "constant" : "variable") + " declared at "
                        + shadowed.declaredAt);
            }
            return id;
        }

        @Override
        VariableId unsafeGetId(String name) {
            VariableId id = variables.get(name);
            return id == null ? parent.unsafeGetId(name) : id;
        }
        @Override
        VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError {
            VariableId id=variables.get(name);
            if(id == null){
                id=parent.getId(name,pos,callee);
            }
            return id;
        }

        @Override
        ProcedureContext procedure() {
            return parent.procedure();
        }

        @Override
        int level() {
            return parent.level()+1;
        }
    }

    static class ProcedureContext extends BlockContext {
        ArrayList<CurriedVariable> curried=new ArrayList<>();
        ProcedureContext(VariableContext parent){
            super(parent);
            assert parent!=null;
        }

        @Override
        int nextId() {
            return super.nextId()-curried.size();
        }

        @Override
        VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError {
            VariableId id=variables.get(name);
            if(id == null){
                id=parent.getId(name,pos,callee);
                if(id!=null){
                    if(!id.isConstant){
                        throw new SyntaxError("external variable "+name+" is not constant",pos);
                    }else if(id.context.procedure()!=null){
                        id=new CurriedVariable(id,this, curried.size(), pos);
                        curried.add((CurriedVariable)id);//curry variable
                        variables.put(name,id);//add curried variable to variable list
                    }
                }
            }
            return id;
        }
        @Override
        ProcedureContext procedure() {
            return this;
        }

        @Override
        int level() {
            return 0;
        }
    }

    interface CodeSection{
        ArrayList<Token> tokens();
        VariableContext context();
    }
    record Program(ArrayList<Token> tokens, HashSet<String> files,RootContext rootContext,
                   HashMap<VariableId,Value> globalConstants) implements CodeSection{
        @Override
        public String toString() {
            return "Program{" +
                    "tokens=" + tokens +
                    ", files='" + files + '\'' +
                    '}';
        }
        @Override
        public VariableContext context() {
            return rootContext;
        }
    }

    public Program parse(File file, Program program, IOContext ioContext) throws IOException, SyntaxError {
        String fileName=file.getAbsolutePath();
        if(program==null){
            program=new Program(new ArrayList<>(),new HashSet<>(),new RootContext(),new HashMap<>());
        }else if(program.files.contains(file.getAbsolutePath())){
            return program;
        }else{//ensure that each file is included only once
            program.files.add(file.getAbsolutePath());
        }
        program.rootContext.startFile();
        ParserReader reader=new ParserReader(fileName);
        ArrayDeque<CodeBlock> openBlocks=new ArrayDeque<>();
        Macro[] currentMacroPtr=new Macro[1];
        int c;
        reader.nextToken();
        WordState state=WordState.ROOT;
        String current,next=null;
        while((c=reader.nextChar())>=0){
            switch(state){
                case ROOT:
                    if(Character.isWhitespace(c)){
                        current=next;
                        next=reader.buffer.toString();
                        if(current!=null){
                            finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),
                                    program,ioContext);
                        }
                        reader.nextToken();
                    }else{
                        switch (c) {
                            case '"', '\'' -> {
                                state = WordState.STRING;
                                if(reader.buffer.toString().equals("u")){
                                    state = WordState.UNICODE_STRING;
                                }else if(reader.buffer.length()>0) {
                                    throw new SyntaxError("Illegal string prefix:\"" + reader.buffer + "\"",
                                            reader.currentPos());
                                }
                                reader.buffer.append((char)c);
                            }
                            case '#' -> {
                                c = reader.forceNextChar();
                                if (c == '#') {
                                    state = WordState.LINE_COMMENT;
                                    current=next;
                                    next=reader.buffer.toString();
                                    if(current!=null){
                                        finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),
                                                program,ioContext);
                                    }
                                    reader.nextToken();
                                } else if (c == '_') {
                                    state = WordState.COMMENT;
                                    current=next;
                                    next=reader.buffer.toString();
                                    if(current!=null){
                                        finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),
                                                program,ioContext);
                                    }
                                    reader.nextToken();
                                } else {
                                    reader.buffer.append('#').append((char) c);
                                }
                            }
                            default -> reader.buffer.append((char) c);
                        }
                    }
                    break;
                case STRING,UNICODE_STRING:
                    if(c==reader.buffer.charAt(state==WordState.STRING?0:1)){
                        current=next;
                        next=reader.buffer.toString();
                        if(current!=null){
                            finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),
                                    program,ioContext);
                        }
                        reader.nextToken();
                        state=WordState.ROOT;
                    }else{
                        if(c=='\\'){
                            c =  reader.forceNextChar();
                            switch (c) {
                                case '\\', '\'', '"' -> reader.buffer.append((char) c);
                                case 'n' -> reader.buffer.append('\n');
                                case 't' -> reader.buffer.append('\t');
                                case 'r' -> reader.buffer.append('\r');
                                case 'b' -> reader.buffer.append('\b');
                                case 'f' -> reader.buffer.append('\f');
                                case '0' -> reader.buffer.append('\0');
                                case 'u', 'U' -> {
                                    int l = c == 'u' ? 4 : 6;
                                    StringBuilder tmp = new StringBuilder(l);
                                    for (int i = 0; i < l; i++) {
                                        tmp.append((char)  reader.forceNextChar());
                                    }
                                    reader.buffer.append(Character.toChars(Integer.parseInt(tmp.toString(), 16)));
                                }
                                default -> throw new IllegalArgumentException("The escape sequence: '\\" + c + "' is not supported");
                            }
                        }else{
                            reader.buffer.append((char)c);
                        }
                    }
                    break;
                case COMMENT:
                    if(c=='_'){
                        c = reader.forceNextChar();
                        if(c=='#'){
                            state=WordState.ROOT;
                        }
                    }
                    break;
                case LINE_COMMENT:
                    if(c=='\n'||c=='\r'){
                        state=WordState.ROOT;
                    }
                    break;
            }
        }
        switch (state){
            case ROOT->{
                current=next;
                next=reader.buffer.toString();
                if(current!=null){
                    finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program,ioContext);
                }
                reader.nextToken();
            }
            case LINE_COMMENT ->{} //do nothing
            case STRING,UNICODE_STRING ->throw new SyntaxError("unfinished string", reader.currentPos());
            case COMMENT -> throw new SyntaxError("unfinished comment", reader.currentPos());
        }
        //finish parsing of all elements
        finishWord(next,END_OF_FILE,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program,ioContext);
        finishWord(END_OF_FILE,END_OF_FILE,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program,ioContext);

        if(openBlocks.size()>0){
            throw new SyntaxError("unclosed block: "+openBlocks.getLast(),openBlocks.getLast().startPos);
        }
        if((program.rootContext).predeclaredProcs.size()>0){
            StringBuilder message=new StringBuilder("Syntax Error: File "+fileName+" contains missing variables/procedures");
            for(Map.Entry<String, PredeclaredProc> p:(program.rootContext).predeclaredProcs.entrySet()){
                message.append("\n- ").append(p.getKey()).append(" (at ").append(p.getValue().pos).append(")");
            }
            throw new SyntaxError(message.toString(),reader.currentPos);
        }
        program.rootContext.endFile(ioContext);
        return program;
    }

    /**@return false if the value was an integer otherwise true*/
    private boolean tryParseInt(ArrayList<Token> tokens, String str,FilePosition pos) throws SyntaxError {
        try {
            if(intDec.matcher(str).matches()){//dez-Int
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 10)), pos, false));
                return false;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 2)),  pos, false));
                return false;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 16)), pos, false));
                return false;
            }
        } catch (NumberFormatException nfeL) {
            throw new SyntaxError("Number out of Range:"+str,pos);
        }
        return true;
    }

    private void finishWord(String str,String next, ArrayList<Token> tokens, ArrayDeque<CodeBlock> openBlocks,
                            Macro[] currentMacroPtr, FilePosition pos, Program program,IOContext ioContext) throws SyntaxError, IOException {
        if (str.length() > 0) {
            if(currentMacroPtr[0]!=null){//handle macros
                if(str.equals("#end")){
                    program.rootContext.declareMacro(currentMacroPtr[0],ioContext);
                    currentMacroPtr[0]=null;
                }else if(str.startsWith("#")){
                    throw new SyntaxError(str+" is not allowed in macros",pos);
                }else{
                    currentMacroPtr[0].content.add(new StringWithPos(str,pos));
                }
                return;
            }
            {//preprocessor
                Token prev=tokens.size()>0?tokens.get(tokens.size()-1):null;
                String prevId=(prev!=null&&prev.tokenType==TokenType.IDENTIFIER)?
                        ((IdentifierToken)prev).name:null;
                switch (str){
                    case "#define"->{
                        if(openBlocks.size()>0){
                            throw new SyntaxError("macros can only be defined at root-level",pos);
                        }
                        if(prevId!=null){
                            tokens.remove(tokens.size()-1);
                            currentMacroPtr[0]=new Macro(pos,prevId,new ArrayList<>());
                        }else{
                            throw new SyntaxError("invalid token preceding #define: "+prev+" expected identifier",pos);
                        }
                        return;
                    }
                    case "#undef"->{
                        if(openBlocks.size()>0){
                            throw new SyntaxError("macros can only be undefined at root-level",pos);
                        }
                        if(prev!=null&&prev.tokenType==TokenType.MACRO_EXPAND){
                            tokens.remove(tokens.size()-1);
                            assert prev instanceof IdentifierToken;
                            program.rootContext.removeMacro(((IdentifierToken)prev).name,pos);
                        }else{
                            throw new SyntaxError("invalid token preceding #undef: "+prev+" expected macro-name",pos);
                        }
                        return;
                    }
                    case "#module"-> {
                        if(openBlocks.size()>0){
                            throw new SyntaxError("modules can only be declared at root-level",pos);
                        }
                        if(prevId != null){
                            tokens.remove(tokens.size()-1);
                            program.rootContext.startModule(prevId,pos);
                        }else{
                            throw new SyntaxError("module name has to be an identifier",pos);
                        }
                        return;
                    }
                    case "#end"-> {
                        if(openBlocks.size()>0){
                            throw new SyntaxError("modules can only be closed at root-level",pos);
                        }else{
                            //finish all words
                            finishWord("##","##",tokens,openBlocks,currentMacroPtr,pos,program,ioContext);
                            program.rootContext.endModule(pos);
                        }
                        return;
                    }
                    case "#import"-> {
                        if(openBlocks.size()>0){
                            throw new SyntaxError("imports are can only allowed at root-level",pos);
                        }
                        if(prevId != null){
                            tokens.remove(tokens.size()-1);
                            program.rootContext.addImport(prevId);
                        }else{
                            throw new SyntaxError("module name has to be an identifier",pos);
                        }
                        return;
                    }
                    case "#include" -> {
                        if(openBlocks.size()>0){
                            throw new SyntaxError("includes are can only allowed at root-level",pos);
                        }
                        if(prev instanceof ValueToken){
                            tokens.remove(tokens.size()-1);
                            String name=((ValueToken) prev).value.stringValue();
                            File file=new File(name);
                            if(file.exists()){
                                parse(file,program,ioContext);
                            }else{
                                throw new SyntaxError("File "+name+" does not exist",pos);
                            }
                        }else if(prevId != null){
                            tokens.remove(tokens.size()-1);
                            String path=libPath+File.separator+ prevId +DEFAULT_FILE_EXTENSION;
                            File file=new File(path);
                            if(file.exists()){
                                parse(file,program,ioContext);
                            }else{
                                throw new SyntaxError(prevId+" is not part of the standard library",pos);
                            }
                        }else{
                            throw new SyntaxError("include path has to be a string literal or identifier",pos);
                        }
                        return;
                    }
                    case "="->{
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '=' modifier",pos);
                        }else if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_INDEX){
                            //<array> <val> <index> [] =
                            tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_INDEX,prev.pos));
                        }else if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_SLICE){
                            //<array> <val> <off> <to> [:] =
                            tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_SLICE,prev.pos));
                        }else if(prev instanceof IdentifierToken){
                            if(prev.tokenType == TokenType.IDENTIFIER){
                                prev=new IdentifierToken(TokenType.VAR_WRITE,((IdentifierToken) prev).name,prev.pos);
                            }else{
                                throw new SyntaxError("invalid token for '=' modifier: "+prev,prev.pos);
                            }
                            tokens.set(tokens.size()-1,prev);
                        }else{
                            throw new SyntaxError("invalid token for '=' modifier: "+prev,prev.pos);
                        }
                        return;
                    }
                    case "."->{
                        if(prev==null||tokens.size()<2){
                            throw new SyntaxError("not enough tokens tokens for '.' modifier",pos);
                        }else if(prev.tokenType==TokenType.IDENTIFIER||
                                prev.tokenType==TokenType.MACRO_EXPAND||prev.tokenType==TokenType.PROC_CALL){
                            Token prePrev=tokens.get(tokens.size()-2);
                            if(prePrev.tokenType!=TokenType.IDENTIFIER&&
                                    prePrev.tokenType!=TokenType.MACRO_EXPAND&&prePrev.tokenType!=TokenType.PROC_CALL){
                                throw new SyntaxError("invalid token for '.' modifier: "+prePrev,prePrev.pos);
                            }
                            String newName=((IdentifierToken)prePrev).name+ MODULE_SEPARATOR +((IdentifierToken)prev).name;
                            tokens.remove(tokens.size()-1);
                            prev=new IdentifierToken(
                                    program.rootContext.hasMacro(newName)?TokenType.MACRO_EXPAND:
                                            program.rootContext.hasProcedure(newName)?TokenType.PROC_CALL :TokenType.IDENTIFIER,
                                    newName,pos);
                        }else{
                            throw new SyntaxError("invalid token for '.' modifier: "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=:"->{
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '=:' modifier",pos);
                        }else if(prevId!=null){
                            prev=new IdentifierToken(TokenType.DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=:' modifier "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=$"->{
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '=$' modifier",pos);
                        }else if(prevId!=null){
                            prev=new IdentifierToken(TokenType.CONST_DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=$' modifier: "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "@()"->{
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '@()' modifier",pos);
                        }else if(prev.tokenType==TokenType.PROC_CALL){
                            tokens.set(tokens.size()-1,new IdentifierToken(TokenType.PROC_ID,
                                    ((IdentifierToken)prev).name,prev.pos));
                        }else{
                            throw new SyntaxError("invalid token for '@()' modifier: "+prev,prev.pos);
                        }
                        return;
                    }
                }
                if(prev!=null){
                    if(prev.tokenType==TokenType.MACRO_EXPAND) {
                        tokens.remove(tokens.size() - 1);//remove prev
                        assert prev instanceof IdentifierToken;
                        Macro m = program.rootContext.getMacro(((IdentifierToken) prev).name,prev.pos);
                        for(int i=0;i<m.content.size();i++){
                            StringWithPos s=m.content.get(i);
                            finishWord(s.str,i+1<m.content.size()?m.content.get(i+1).str:"##"
                                    ,tokens, openBlocks, currentMacroPtr, new FilePosition(s.start, pos), program,ioContext);
                        }
                        //update identifiers at end of macro
                        finishWord("##",next,tokens, openBlocks, currentMacroPtr, pos, program,ioContext);
                    }else if((!(next.equals(".")||str.equals("proc")||str.equals("procedure")))&&
                            prev instanceof IdentifierToken identifier){
                        int index=tokens.size()-1;
                        VariableContext context=getContext(openBlocks.peekLast(),program.rootContext);
                        //update variables
                        switch (identifier.tokenType){
                            case DECLARE,CONST_DECLARE -> {
                                VariableId id=context.declareVariable(
                                        identifier.name, identifier.tokenType == TokenType.CONST_DECLARE,
                                        identifier.pos,ioContext);
                                AccessType accessType = identifier.tokenType == TokenType.CONST_DECLARE ?
                                                AccessType.CONST_DECLARE : AccessType.DECLARE;
                                if(index>0&&(prev=tokens.get(index-1)) instanceof ValueToken){
                                    try {//remember constant declarations
                                        Type type=((ValueToken)prev).value.asType();
                                        //addLater remember variable types
                                        if(id.isConstant&&id.context.procedure()==null&&//only remember root-level constants
                                                index>1&&(prev=tokens.get(index-2)) instanceof ValueToken){
                                            program.globalConstants.put(id,((ValueToken) prev).value.clone(true).castTo(type));
                                            tokens.subList(index-2, tokens.size()).clear();
                                            break;//don't add token to code
                                        }
                                    } catch (ConcatRuntimeError e) {
                                        throw new SyntaxError(e.getMessage(),prev.pos);
                                    }
                                }
                                tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                        accessType,context));
                            }
                            case IDENTIFIER -> {
                                VariableId id=context.getId(identifier.name, identifier.pos,context);
                                if(id!=null){
                                    Value constValue=program.globalConstants.get(id);
                                    if(constValue!=null){
                                        tokens.set(index,new ValueToken(constValue,identifier.pos, false));
                                    }else{
                                        tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                                AccessType.READ,context));
                                    }
                                }else{//first use of pre-declaration
                                    Value.Procedure proc=program.rootContext.getProcedure(identifier.name,pos);
                                    ProcedureToken token=new ProcedureToken(false,proc,pos);
                                    assert proc==null;
                                    program.rootContext.addProcDeclareListener(identifier.name,token);
                                    tokens.set(index,token);
                                }
                            }
                            case VAR_WRITE -> {
                                VariableId id=context.getId(identifier.name, identifier.pos,context);
                                assert !program.globalConstants.containsKey(id);
                                tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                        AccessType.WRITE,context));
                            }
                            case PROC_CALL, PROC_ID -> {
                                Value.Procedure proc=program.rootContext.getProcedure(identifier.name,pos);
                                ProcedureToken token=new ProcedureToken(identifier.tokenType==TokenType.PROC_ID
                                        ,proc,pos);
                                if(proc==null){
                                    program.rootContext.addProcDeclareListener(identifier.name,token);
                                }
                                tokens.set(index,token);
                            }
                            default -> throw new RuntimeException("unexpected type of IdentifierToken:"+ identifier.tokenType);
                        }
                    }
                }
            }
            if(str.charAt(0)=='\''){//unicode char literal
                str=str.substring(1);
                if(str.codePoints().count()==1){
                    int codePoint = str.codePointAt(0);
                    if(codePoint<0x7f){
                        tokens.add(new ValueToken(Value.ofByte((byte)codePoint), pos, false));
                    }else{
                        throw new SyntaxError("codePoint "+codePoint+
                                "does not fit in one byte " +
                                "(if you want to use unicode-characters prefix the char-literal with u)", pos);
                    }
                }else{
                    throw new SyntaxError("A char-literal must contain exactly one character", pos);
                }
                return;
            }else if(str.startsWith("u'")){//unicode char literal
                str=str.substring(2);
                if(str.codePoints().count()==1){
                    int codePoint = str.codePointAt(0);
                    tokens.add(new ValueToken(Value.ofChar(codePoint), pos, false));
                }else{
                    throw new SyntaxError("A char-literal must contain exactly one codepoint", pos);
                }
                return;
            }else if(str.charAt(0)=='"'){
                str=str.substring(1);
                tokens.add(new ValueToken(Value.ofString(str,false),  pos, true));
                return;
            }else if(str.startsWith("u\"")){
                str=str.substring(2);
                tokens.add(new ValueToken(Value.ofString(str,true),  pos, true));
                return;
            }
            try{
                if (tryParseInt(tokens, str,pos)) {
                    if(floatDec.matcher(str).matches()){
                        //dez-Float
                        double d = Double.parseDouble(str);
                        tokens.add(new ValueToken(Value.ofFloat(d), pos, false));
                    }else if(floatBin.matcher(str).matches()){
                        //bin-Float
                        double d= Value.parseFloat(str.substring(BIN_PREFIX.length()),2);
                        tokens.add(new ValueToken(Value.ofFloat(d), pos, false));
                    }else if(floatHex.matcher(str).matches()){
                        //hex-Float
                        double d=Value.parseFloat(str.substring(BIN_PREFIX.length()),16);
                        tokens.add(new ValueToken(Value.ofFloat(d), pos, false));
                    }else {
                        addWord(str,tokens,openBlocks,pos,program.rootContext);
                    }
                }
            }catch(SyntaxError e){
                if(e.pos.equals(pos)){
                    throw e;//avoid duplicate positions in stack trace
                }else {
                    throw new SyntaxError(e, pos);
                }
            }catch(ConcatRuntimeError|NumberFormatException e){
                throw new SyntaxError(e, pos);
            }
        }
    }

    VariableContext getContext(CodeBlock currentBlock,VariableContext root){
        return currentBlock!=null?currentBlock.context():root;
    }

    private void addWord(String str, ArrayList<Token> tokens, ArrayDeque<CodeBlock> openBlocks,
                         FilePosition pos,RootContext rootContext) throws SyntaxError {
        Token prev;
        switch (str) {
            case END_OF_FILE -> {} //## string can only be passed to the method on end of file
            case "true"  -> tokens.add(new ValueToken(Value.TRUE,    pos, false));
            case "false" -> tokens.add(new ValueToken(Value.FALSE,   pos, false));

            case "bool"       -> tokens.add(new ValueToken(Value.ofType(Type.BOOL),              pos, false));
            case "byte"       -> tokens.add(new ValueToken(Value.ofType(Type.BYTE),              pos, false));
            case "int"        -> tokens.add(new ValueToken(Value.ofType(Type.INT),               pos, false));
            case "codepoint"  -> tokens.add(new ValueToken(Value.ofType(Type.CODEPOINT),         pos, false));
            case "float"      -> tokens.add(new ValueToken(Value.ofType(Type.FLOAT),             pos, false));
            case "string"     -> tokens.add(new ValueToken(Value.ofType(Type.BYTES()),           pos, false));
            case "ustring"    -> tokens.add(new ValueToken(Value.ofType(Type.UNICODE_STRING()),  pos, false));
            case "type"       -> tokens.add(new ValueToken(Value.ofType(Type.TYPE),              pos, false));
            case "*->*"       -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_PROCEDURE), pos, false));
            case "var"        -> tokens.add(new ValueToken(Value.ofType(Type.ANY),               pos, false));
            case "(list)"     -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_LIST),      pos, false));
            case "(optional)" -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_OPTIONAL),  pos, false));
            case "(tuple)"    -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_TUPLE),     pos, false));
            case "(file)"     -> tokens.add(new ValueToken(Value.ofType(Type.FILE),              pos, false));

            case "list" -> {
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        tokens.set(tokens.size()-1,
                                new ValueToken(Value.ofType(Type.listOf(((ValueToken)prev).value.asType())),pos, false));
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new OperatorToken(OperatorType.LIST_OF, pos));
                }
            }
            case "optional" -> {
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        tokens.set(tokens.size()-1,
                                new ValueToken(Value.ofType(Type.optionalOf(((ValueToken)prev).value.asType())),pos, false));
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new OperatorToken(OperatorType.OPTIONAL_OF, pos));
                }
            }
            case "..."->{
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        tokens.set(tokens.size()-1,
                                new ValueToken(Value.ofType(Type.varArg(((ValueToken)prev).value.asType())),pos, false));
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new OperatorToken(OperatorType.VAR_ARG, pos));
                }
            }
            case "content" -> {
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        tokens.set(tokens.size()-1,
                                new ValueToken(Value.ofType(((ValueToken)prev).value.asType().content()),pos, false));
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new OperatorToken(OperatorType.CONTENT, pos));
                }
            }
            case "isVarArg"->{
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        tokens.set(tokens.size()-1,
                                new ValueToken(((ValueToken)prev).value.asType().isVarArg()?Value.TRUE:Value.FALSE
                                        ,pos, false));
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new OperatorToken(OperatorType.IS_VAR_ARG,pos));
                }
            }
            case "tuple" -> {
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken) {
                    try {
                        long c = ((ValueToken)prev).value.asLong();
                        checkElementCount(c);
                        int iMin=tokens.size()-1-(int)c;
                        if(iMin>=0){
                            Type[] types=new Type[(int)c];
                            for(int i=0;i<c;i++){
                                prev=tokens.get(iMin+i);
                                if(prev instanceof ValueToken){
                                    types[i]=((ValueToken) prev).value.asType();
                                }else{
                                    break;
                                }
                            }
                            if(c==0||types[types.length-1]!=null){//all types resolved successfully
                                tokens.subList(iMin, tokens.size()).clear();
                                tokens.add(new ValueToken(Value.ofType(Type.Tuple.create(types)),pos, false));
                                break;
                            }
                        }
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e.getMessage(), prev.pos);
                    }
                }
                tokens.add(new OperatorToken(OperatorType.TUPLE, pos));
            }
            case "->" ->{
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken) {
                    try {
                        long outCount = ((ValueToken)prev).value.asLong();
                        checkElementCount(outCount);
                        int iMin=tokens.size()-1-(int)outCount;
                        if(iMin>0&&(prev=tokens.get(iMin-1)) instanceof ValueToken){
                            long inCount = ((ValueToken)prev).value.asLong();
                            checkElementCount(inCount);
                            Type[] outTypes=new Type[(int)outCount];
                            for(int i=0;i<outCount;i++){
                                prev=tokens.get(iMin+i);
                                if(prev instanceof ValueToken){
                                    outTypes[i]=((ValueToken) prev).value.asType();
                                }else{
                                    break;
                                }
                            }
                            if(outCount==0||outTypes[outTypes.length-1]!=null){//all out-types resolved successfully
                                Type[] inTypes=new Type[(int)inCount];
                                iMin=iMin-1-(int)inCount;
                                for(int i=0;i<inCount;i++){
                                    prev=tokens.get(iMin+i);
                                    if(prev instanceof ValueToken){
                                        inTypes[i]=((ValueToken) prev).value.asType();
                                    }else{
                                        break;
                                    }
                                }
                                if(inCount==0||inTypes[inTypes.length-1]!=null){
                                    tokens.subList(iMin, tokens.size()).clear();
                                    tokens.add(new ValueToken(Value.ofType(Type.Procedure.create(inTypes,outTypes)),pos, false));
                                    break;
                                }
                            }
                        }
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e.getMessage(), prev.pos);
                    }
                }
                tokens.add(new OperatorToken(OperatorType.NEW_PROC_TYPE, pos));
            }

            case "cast"   ->  tokens.add(new OperatorToken(OperatorType.CAST,    pos));
            case "typeof" ->  {
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    tokens.set(tokens.size()-1,
                            new ValueToken(Value.ofType(((ValueToken)prev).value.type),pos, false));
                }else{
                    tokens.add(new OperatorToken(OperatorType.TYPE_OF, pos));
                }
            }

            /*<off> <count> $dup*/
            /*<off> <count> $drop*/
            case "$drop","$dup"  ->{
                if(tokens.size()<2){
                    throw new SyntaxError("not enough arguments for "+str,pos);
                }
                Token count=tokens.remove(tokens.size()-1);
                Token off=tokens.remove(tokens.size()-1);
                if(count instanceof ValueToken &&off instanceof ValueToken){
                    try {
                        long c=((ValueToken) count).value.asLong();
                        if(c<0){
                            throw new SyntaxError(str+": count has to be greater than of equal to 0",pos);
                        }
                        long o=((ValueToken) off).value.asLong();
                        if(o<0){
                            throw new SyntaxError(str+":offset has to be greater than of equal to 0",pos);
                        }
                        tokens.add(new StackModifierToken(str.equals("$dup"),o,c,pos));
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    throw new SyntaxError("the arguments of "+str+" have to be compile time constants",pos);
                }
            }

            case "refId"  -> tokens.add(new OperatorToken(OperatorType.REF_ID,     pos));

            case "clone"  -> tokens.add(new OperatorToken(OperatorType.CLONE,      pos));
            case "clone!" -> tokens.add(new OperatorToken(OperatorType.DEEP_CLONE, pos));

            case "debugPrint"    -> tokens.add(new Token(TokenType.DEBUG_PRINT, pos));

            //addLater? change to float To/from bytes
            case "intAsFloat"   -> tokens.add(new OperatorToken(OperatorType.INT_AS_FLOAT, pos));
            case "floatAsInt"   -> tokens.add(new OperatorToken(OperatorType.FLOAT_AS_INT, pos));

            case "proc","procedure" ->{
                if(openBlocks.size()>0){
                    throw new SyntaxError("procedures can only be declared at root level",pos);
                }
                if(tokens.size()==0){
                    throw new SyntaxError("missing procedure name before proc",pos);
                }
                prev=tokens.remove(tokens.size()-1);
                String name = ((IdentifierToken) prev).name;
                if(prev.tokenType==TokenType.PROC_CALL){
                    if(!rootContext.predeclaredProcs.containsKey(name)){
                        Value.Procedure proc=rootContext.getProcedure(name,pos);
                        throw new SyntaxError("procedure "+ name +" does already exist" +
                                " (declared at "+proc.declaredAt+")",pos);
                    }
                }else if(prev.tokenType!=TokenType.IDENTIFIER){
                    throw new SyntaxError("token before proc has to be an identifier",pos);
                }
                openBlocks.add(new ProcedureBlock(name, tokens.size(),pos,rootContext));
            }
            case "=>" ->{
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof ProcedureBlock proc)){
                    throw new SyntaxError("elif can only be used in if-blocks",pos);
                }
                proc.addIns(tokens.subList(proc.start,tokens.size()),pos);
            }
            case "lambda","λ" ->
                    openBlocks.add(new ProcedureBlock(null, tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "while" -> openBlocks.add(new WhileBlock(tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "&&" -> openBlocks.addLast(new ShortCircuitBlock(tokens.size(),pos,true,
                    getContext(openBlocks.peekLast(),rootContext)));
            case "||" -> openBlocks.addLast(new ShortCircuitBlock(tokens.size(),pos,false,
                    getContext(openBlocks.peekLast(),rootContext)));
            case "if" -> openBlocks.addLast(new IfBlock(tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "switch"->{
                SwitchCaseBlock switchBlock=new SwitchCaseBlock(tokens.size(), pos,getContext(openBlocks.peekLast(), rootContext));
                openBlocks.addLast(switchBlock);
                //handle switch-case separately since : does not change context and produces special jump
                tokens.add(new SwitchToken(switchBlock,pos));
                switchBlock.newSection(tokens.size(),pos);
            }
            case "elif" -> {
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof IfBlock ifBlock)){
                    throw new SyntaxError("elif can only be used in if-blocks",pos);
                }
                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                VariableContext context=ifBlock.newBranch(tokens.size(),pos);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                if(context!=null){
                    tokens.add(new ContextOpen(context,pos));
                }
            }
            case "else" -> {
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof IfBlock ifBlock)){
                    throw new SyntaxError("elif can only be used in if-blocks",pos);
                }
                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                VariableContext context=ifBlock.elseBranch(tokens.size(),pos);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                if(context!=null){
                    tokens.add(new ContextOpen(context,pos));
                }
            }
            case ":" -> {
                CodeBlock block=openBlocks.peekLast();
                if(block==null){
                    throw new SyntaxError(": can only be used in if-, while-, &&- and  ||- blocks",pos);
                }else if(block.type==BlockType.PROCEDURE){
                    //handle procedure separately since : does not change context of produce a jump
                    ProcedureBlock proc=(ProcedureBlock) block;
                    proc.addOuts(tokens.subList(proc.start,tokens.size()),pos);
                }else{
                    int forkPos=tokens.size();
                    tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                    VariableContext newContext;
                    switch (block.type){
                        case IF -> newContext=((IfBlock)block).fork(forkPos,pos);
                        case SHORT_AND,SHORT_OR -> newContext=((ShortCircuitBlock)block).fork(forkPos,pos);
                        case WHILE -> newContext=((WhileBlock)block).fork(forkPos,pos);
                        default ->
                                throw new SyntaxError(": can only be used in if-, while-, &&- and  ||- blocks",pos);
                    }
                    tokens.add(new ContextOpen(newContext,pos));
                }
            }
            case "case" ->{
                CodeBlock block=openBlocks.peekLast();
                if(!(block instanceof SwitchCaseBlock switchBlock)){
                    throw new SyntaxError("case can only be used in switch-case-blocks",pos);
                }
                int start=switchBlock.sectionStart;
                if(start==-1){
                    throw new SyntaxError("unexpected case statement",pos);
                }
                List<Token> caseValues=tokens.subList(start,tokens.size());
                VariableContext context=switchBlock.caseBlock(caseValues,pos);
                caseValues.clear();
                tokens.add(new ContextOpen(context,pos));
            }
            case "default" ->{
                CodeBlock block=openBlocks.peekLast();
                if(!(block instanceof SwitchCaseBlock switchBlock)){
                    throw new SyntaxError("default can only be used in switch-case-blocks",pos);
                }
                VariableContext context=switchBlock.defaultBlock(tokens.size(),pos);
                tokens.add(new ContextOpen(context,pos));
            }
            case "end-case" ->{
                CodeBlock block=openBlocks.peekLast();
                if(!(block instanceof SwitchCaseBlock switchBlock)){
                    throw new SyntaxError("end-case can only be used in switch-case-blocks",pos);
                }
                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                tokens.add(new Token(TokenType.PLACEHOLDER,pos));
                switchBlock.newSection(tokens.size(),pos);
            }
            case "end" ->{
                CodeBlock block=openBlocks.pollLast();
                if(block==null){
                    throw new SyntaxError("unexpected end statement",pos);
                }else {
                    Token tmp;
                    switch (block.type) {
                        case PROCEDURE -> {
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            ArrayList<Token> content=new ArrayList<>(subList);
                            subList.clear();
                            ProcedureContext context = ((ProcedureBlock) block).context();
                            Type[] ins=((ProcedureBlock) block).inTypes;
                            Type[] outs=((ProcedureBlock) block).outTypes;
                            Type procType;
                            if(ins!=null) {
                                if (outs == null) {
                                    throw new SyntaxError("procedure supplies inTypes but no outTypes", pos);
                                } else {
                                    procType=Type.Procedure.create(ins,outs);
                                }
                            }else{
                                if(((ProcedureBlock) block).name!=null){
                                    //ensure that all named procedures have a signature
                                    throw new SyntaxError("named procedure "+((ProcedureBlock) block).name+
                                            " does not have a signature",block.startPos);
                                }
                                procType=Type.GENERIC_PROCEDURE;
                            }
                            Value.Procedure proc=Value.createProcedure(procType,block.startPos, content, context);
                            if (context.curried.isEmpty()) {
                                if(((ProcedureBlock) block).name!=null){
                                    rootContext.declareProcedure(((ProcedureBlock) block).name,proc);
                                }else{
                                    tokens.add(new ValueToken(proc, pos, false));
                                }
                            } else {
                                if(((ProcedureBlock) block).name!=null){
                                    throw new RuntimeException("named procedures cannot be curried");
                                }
                                tokens.add(new ValueToken(TokenType.CURRIED_PROCEDURE,proc, pos, false));
                            }
                        }
                        case IF -> {
                            ((IfBlock)block).end(pos);
                            if(((IfBlock) block).forkPos!=-1){
                                tmp=tokens.get(((IfBlock) block).forkPos);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                                tokens.set(((IfBlock) block).forkPos,new RelativeJump(TokenType.JNE,tmp.pos,
                                        (tokens.size())-((IfBlock) block).forkPos));
                                //when there is no else then the last branch has to jump onto the close operation
                            }
                            for(IfBranch branch:((IfBlock) block).branches){
                                tmp=tokens.get(branch.fork);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(branch.fork,new RelativeJump(TokenType.JNE,tmp.pos,branch.end-branch.fork+1));
                                tmp=tokens.get(branch.end);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(branch.end,new RelativeJump(TokenType.JMP,tmp.pos,tokens.size()-branch.end));
                            }
                            if(((IfBlock) block).branches.size()>0){
                                //add close context for elseContext (root context for all branches after the first if)
                                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                                //update jump on (first) if-branch to skip the close
                                IfBranch branch=((IfBlock) block).branches.get(0);
                                tmp=tokens.get(branch.end);
                                tokens.set(branch.end,new RelativeJump(TokenType.JMP,tmp.pos,tokens.size()-branch.end));
                            }
                        }
                        case WHILE -> {
                            ((WhileBlock)block).end(pos);
                            tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            tmp=tokens.get(((WhileBlock) block).forkPos);
                            assert tmp.tokenType==TokenType.PLACEHOLDER;
                            if(((WhileBlock) block).forkPos==tokens.size()-1){
                                //empty body => the jumps can be merged
                                tokens.set(((WhileBlock) block).forkPos,new RelativeJump(TokenType.JEQ,tmp.pos,
                                        block.start - tokens.size()));
                            }else{
                                tokens.add(new RelativeJump(TokenType.JMP,pos, block.start - tokens.size()));
                                tokens.set(((WhileBlock) block).forkPos,new RelativeJump(TokenType.JNE,tmp.pos,
                                        tokens.size()-((WhileBlock) block).forkPos));
                            }
                        }
                        case SHORT_AND -> {
                            ((ShortCircuitBlock)block).end(pos);
                            tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            tmp=tokens.get(((ShortCircuitBlock) block).forkPos);
                            assert tmp.tokenType==TokenType.PLACEHOLDER;
                            tokens.set(((ShortCircuitBlock) block).forkPos,new RelativeJump(TokenType.SHORT_AND_JMP,tmp.pos,
                                    tokens.size()-((ShortCircuitBlock) block).forkPos));
                        }
                        case SHORT_OR -> {
                            ((ShortCircuitBlock)block).end(pos);
                            tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            tmp=tokens.get(((ShortCircuitBlock) block).forkPos);
                            assert tmp.tokenType==TokenType.PLACEHOLDER;
                            tokens.set(((ShortCircuitBlock) block).forkPos,new RelativeJump(TokenType.SHORT_OR_JMP,tmp.pos,
                                    tokens.size()-((ShortCircuitBlock) block).forkPos));
                        }
                        case SWITCH_CASE -> {
                            if(((SwitchCaseBlock)block).defaultJump!=-1){
                                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            }
                            ((SwitchCaseBlock)block).end(tokens.size(),pos);
                            for(Integer i:((SwitchCaseBlock) block).blockEnds){
                                tmp=tokens.get(i);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(i,new RelativeJump(TokenType.JMP,tmp.pos,tokens.size()-i));
                            }
                        }
                    }
                }
            }
            case "return" -> tokens.add(new Token(TokenType.RETURN,  pos));
            case "exit"   -> tokens.add(new Token(TokenType.EXIT,  pos));

            //addLater constant folding
            case "+"   -> tokens.add(new OperatorToken(OperatorType.PLUS,          pos));
            case "-"   -> tokens.add(new OperatorToken(OperatorType.MINUS,         pos));
            case "-_"  -> tokens.add(new OperatorToken(OperatorType.NEGATE,        pos));
            case "/_"  -> tokens.add(new OperatorToken(OperatorType.INVERT,        pos));
            case "*"   -> tokens.add(new OperatorToken(OperatorType.MULTIPLY,      pos));
            case "/"   -> tokens.add(new OperatorToken(OperatorType.DIV,           pos));
            case "%"   -> tokens.add(new OperatorToken(OperatorType.MOD,           pos));
            case "u/"  -> tokens.add(new OperatorToken(OperatorType.UNSIGNED_DIV,  pos));
            case "u%"  -> tokens.add(new OperatorToken(OperatorType.UNSIGNED_MOD,  pos));
            case "**"  -> tokens.add(new OperatorToken(OperatorType.POW,           pos));
            case "!"   -> tokens.add(new OperatorToken(OperatorType.NOT,           pos));
            case "~"   -> tokens.add(new OperatorToken(OperatorType.FLIP,          pos));
            case "&"   -> tokens.add(new OperatorToken(OperatorType.AND,           pos));
            case "|"   -> tokens.add(new OperatorToken(OperatorType.OR,            pos));
            case "xor" -> tokens.add(new OperatorToken(OperatorType.XOR,           pos));
            case "<"   -> tokens.add(new OperatorToken(OperatorType.LT,            pos));
            case "<="  -> tokens.add(new OperatorToken(OperatorType.LE,            pos));
            case "=="  -> tokens.add(new OperatorToken(OperatorType.EQ,            pos));
            case "!="  -> tokens.add(new OperatorToken(OperatorType.NE,            pos));
            case "===" -> tokens.add(new OperatorToken(OperatorType.REF_EQ,        pos));
            case "=!=" -> tokens.add(new OperatorToken(OperatorType.REF_NE,        pos));
            case ">="  -> tokens.add(new OperatorToken(OperatorType.GE,            pos));
            case ">"   -> tokens.add(new OperatorToken(OperatorType.GT,            pos));
            //addLater compare unsigned

            case ">>"  -> tokens.add(new OperatorToken(OperatorType.RSHIFT,  pos));
            case ".>>" -> tokens.add(new OperatorToken(OperatorType.SRSHIFT, pos));
            case "<<"  -> tokens.add(new OperatorToken(OperatorType.LSHIFT,  pos));
            case ".<<" -> tokens.add(new OperatorToken(OperatorType.SLSHIFT, pos));

            case "log"   -> tokens.add(new OperatorToken(OperatorType.LOG, pos));
            case "floor" -> tokens.add(new OperatorToken(OperatorType.FLOOR, pos));
            case "ceil"  -> tokens.add(new OperatorToken(OperatorType.CEIL, pos));

            case ">>:"   -> tokens.add(new OperatorToken(OperatorType.PUSH_FIRST,     pos));
            case ":<<"   -> tokens.add(new OperatorToken(OperatorType.PUSH_LAST,      pos));
            case "+:"    -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_FIRST, pos));
            case ":+"    -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_LAST,  pos));
            case "clear" -> tokens.add(new OperatorToken(OperatorType.CLEAR,          pos));
            //<array> <index> []
            case "[]"    -> tokens.add(new OperatorToken(OperatorType.GET_INDEX,      pos));
            //<array> <off> <to> [:]
            case "[:]"   -> tokens.add(new OperatorToken(OperatorType.GET_SLICE,      pos));

            //<e0> ... <eN> <N> {}
            case "{}"     -> tokens.add(new OperatorToken(OperatorType.NEW_LIST, pos));
            case "length" -> tokens.add(new OperatorToken(OperatorType.LENGTH,   pos));

            case "()"     -> tokens.add(new Token(TokenType.CALL_PTR, pos));

            case "wrap"   -> tokens.add(new OperatorToken(OperatorType.WRAP,          pos));
            case "unwrap" -> tokens.add(new OperatorToken(OperatorType.UNWRAP,         pos));
            case "??"     -> tokens.add(new OperatorToken(OperatorType.HAS_VALUE,      pos));
            case "empty"  -> tokens.add(new OperatorToken(OperatorType.EMPTY_OPTIONAL, pos));

            case "new"       -> tokens.add(new OperatorToken(OperatorType.NEW,        pos));
            case "ensureCap" -> tokens.add(new OperatorToken(OperatorType.ENSURE_CAP, pos));
            case "fill"      -> tokens.add(new OperatorToken(OperatorType.FILL,       pos));

            case "open"     -> tokens.add(new OperatorToken(OperatorType.OPEN,     pos));
            case "close"    -> tokens.add(new OperatorToken(OperatorType.CLOSE,    pos));
            case "size"     -> tokens.add(new OperatorToken(OperatorType.SIZE,     pos));
            case "pos"      -> tokens.add(new OperatorToken(OperatorType.POS,      pos));
            case "read"     -> tokens.add(new OperatorToken(OperatorType.READ,     pos));
            case "seek"     -> tokens.add(new OperatorToken(OperatorType.SEEK,     pos));
            case "seekEnd"  -> tokens.add(new OperatorToken(OperatorType.SEEK_END, pos));
            case "write"    -> tokens.add(new OperatorToken(OperatorType.WRITE,    pos));
            case "truncate" -> tokens.add(new OperatorToken(OperatorType.TRUNCATE, pos));

            case "stdin"  -> tokens.add(new Token(TokenType.STD_IN,  pos));
            case "stdout" -> tokens.add(new Token(TokenType.STD_OUT, pos));
            case "stderr" -> tokens.add(new Token(TokenType.STD_ERR, pos));

            default -> tokens.add(new IdentifierToken(rootContext.hasMacro(str)?TokenType.MACRO_EXPAND:
                rootContext.hasProcedure(str)?TokenType.PROC_CALL :TokenType.IDENTIFIER,
                    str, pos));
        }
    }

    static class Variable{
        final boolean isConst;
        final Type type;
        private Value value;

        Variable(Type type, boolean isConst, Value value) throws ConcatRuntimeError {
            this.type = type;
            this.isConst = isConst;
            setValue(value);
        }

        public Value getValue() {
            return value;
        }

        public void setValue(Value newValue) throws ConcatRuntimeError {
            value=newValue.castTo(type);
        }
    }

    enum ExitType{
        NORMAL,FORCED,ERROR
    }


    public RandomAccessStack<Value> run(Program program, IOContext context){
        RandomAccessStack<Value> stack=new RandomAccessStack<>(16);
        recursiveRun(stack,program,null,null,context);
        return stack;
    }

    private ExitType recursiveRun(RandomAccessStack<Value> stack, CodeSection program,
                                  ArrayList<Variable[]> globalVariables, Value[] curried, IOContext context){
        ArrayList<Variable[]> variables=new ArrayList<>();
        variables.add(new Variable[program.context().variables.size()]);
        int ip=0;
        ArrayList<Token> tokens=program.tokens();
        while(ip<tokens.size()){
            Token next=tokens.get(ip);
            boolean incIp=true;
            try {
                switch (next.tokenType) {
                    case VALUE -> {
                        ValueToken value = (ValueToken) next;
                        if(value.cloneOnCreate){
                            stack.push(value.value.clone(true));
                        }else{
                            stack.push(value.value);
                        }
                    }
                    case CURRIED_PROCEDURE -> {
                        if(globalVariables==null){
                            throw new RuntimeException("curried procedures should only exist inside of procedures");
                        }
                        Value.Procedure proc=(Value.Procedure)((ValueToken) next).value;
                        Value[] curried2=new Value[proc.context.curried.size()];
                        for(int i=0;i<proc.context.curried.size();i++){
                            VariableId id=proc.context.curried.get(i).source;
                            if(id instanceof CurriedVariable){
                                curried2[i]=curried[id.id];
                            }else if(id.context.procedure()!=null){
                                curried2[i]=variables.get(id.level)[id.id].value;
                            }else{
                                throw new RuntimeException("global variables should only be curried");
                            }
                        }
                        stack.push(proc.withCurried(curried2));
                    }
                    case OPERATOR -> {
                        switch (((OperatorToken) next).opType) {
                            case REF_ID -> stack.push(Value.ofInt(stack.pop().id()));
                            case CLONE -> {
                                Value t = stack.peek();
                                stack.push(t.clone(false));
                            }
                            case DEEP_CLONE -> {
                                Value t = stack.peek();
                                stack.push(t.clone(true));
                            }
                            case NEGATE -> {
                                Value v = stack.pop();
                                stack.push(v.negate());
                            }
                            case INVERT -> {
                                Value v = stack.pop();
                                stack.push(v.invert());
                            }
                            case NOT -> {
                                Value v = stack.pop();
                                stack.push(v.asBool() ? Value.FALSE : Value.TRUE);
                            }
                            case FLIP -> {
                                Value v = stack.pop();
                                stack.push(v.flip());
                            }
                            case PLUS -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x + y), (x, y) -> Value.ofFloat(x + y)));
                            }
                            case MINUS -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x - y), (x, y) -> Value.ofFloat(x - y)));
                            }
                            case MULTIPLY -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x * y), (x, y) -> Value.ofFloat(x * y)));
                            }
                            case DIV -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x / y), (x, y) -> Value.ofFloat(x / y)));
                            }
                            case MOD -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x % y), (x, y) -> Value.ofFloat(x % y)));
                            }
                            case UNSIGNED_DIV -> {
                                long b = stack.pop().asLong();
                                long a = stack.pop().asLong();
                                stack.push(Value.ofInt(Long.divideUnsigned(a,b)));
                            }
                            case UNSIGNED_MOD -> {
                                long b = stack.pop().asLong();
                                long a = stack.pop().asLong();
                                stack.push(Value.ofInt(Long.remainderUnsigned(a,b)));
                            }
                            case POW -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(longPow(x, y)),
                                        (x, y) -> Value.ofFloat(Math.pow(x, y))));
                            }
                            case EQ, NE, GT, GE, LE, LT,REF_EQ,REF_NE -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.compare(a, ((OperatorToken) next).opType, b));
                            }
                            case AND -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.logicOp(a, b, (x, y) -> x && y, (x, y) -> x & y));
                            }
                            case OR -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.logicOp(a, b, (x, y) -> x || y, (x, y) -> x | y));
                            }
                            case XOR -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.logicOp(a, b, (x, y) -> x ^ y, (x, y) -> x ^ y));
                            }
                            case LOG   -> stack.push(Value.ofFloat(Math.log(stack.pop().asDouble())));
                            case FLOOR -> stack.push(Value.ofFloat(Math.floor(stack.pop().asDouble())));
                            case CEIL  -> stack.push(Value.ofFloat(Math.ceil(stack.pop().asDouble())));
                            case LSHIFT -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.intOp(a, b, (x, y) -> y < 0 ? x >>> -y : x << y));
                            }
                            case RSHIFT -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.intOp(a, b, (x, y) -> y < 0 ? x << -y : x >>> y));
                            }
                            case SLSHIFT -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.intOp(a, b, (x, y) -> y < 0 ? x >> -y : signedLeftShift(x, y)));
                            }
                            case SRSHIFT -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.intOp(a, b, (x, y) -> y < 0 ? signedLeftShift(x, -y) : x >> y));
                            }
                            case LIST_OF -> {
                                Type contentType = stack.pop().asType();
                                stack.push(Value.ofType(Type.listOf(contentType)));
                            }
                            case CONTENT -> {
                                Type wrappedType = stack.pop().asType();
                                stack.push(Value.ofType(wrappedType.content()));
                            }
                            case NEW_LIST -> {//e1 e2 ... eN count type {}
                                Type type = stack.pop().asType();
                                //count after type to make signature consistent with var-args procedures
                                long count = stack.pop().asLong();
                                checkElementCount(count);
                                ArrayDeque<Value> tmp = new ArrayDeque<>((int) count);
                                while (count > 0) {
                                    tmp.addFirst(stack.pop().castTo(type));
                                    count--;
                                }
                                ArrayList<Value> list = new ArrayList<>(tmp);
                                stack.push(Value.createList(Type.listOf(type), list));
                            }
                            case CAST -> {
                                Type type = stack.pop().asType();
                                Value val = stack.pop();
                                stack.push(val.castTo(type));
                            }
                            case TYPE_OF -> {
                                Value val = stack.pop();
                                stack.push(Value.ofType(val.type));
                            }
                            case LENGTH -> {
                                Value val = stack.pop();
                                stack.push(Value.ofInt(val.length()));
                            }
                            case CLEAR ->
                                    stack.pop().clear();
                            case ENSURE_CAP -> {
                                long newCap=stack.pop().asLong();
                                stack.peek().ensureCap(newCap);
                            }
                            case FILL -> {
                                Value val   = stack.pop();
                                long  count = stack.pop().asLong();
                                long  off   = stack.pop().asLong();
                                Value list  = stack.pop();
                                list.fill(val,off,count);
                            }
                            case GET_INDEX -> {//array index []
                                long index = stack.pop().asLong();
                                Value list = stack.pop();
                                stack.push(list.get(index));
                            }
                            case SET_INDEX -> {//array value index [] =
                                long index = stack.pop().asLong();
                                Value val = stack.pop();
                                Value list = stack.pop();
                                list.set(index,val);
                            }
                            case GET_SLICE -> {
                                long to = stack.pop().asLong();
                                long off = stack.pop().asLong();
                                Value list = stack.pop();
                                stack.push(list.getSlice(off, to));
                            }
                            case SET_SLICE -> {
                                long to = stack.pop().asLong();
                                long off = stack.pop().asLong();
                                Value val = stack.pop();
                                Value list = stack.pop();
                                list.setSlice(off,to,val);
                            }
                            case PUSH_FIRST -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                b.push(a,true);
                                stack.push(b);
                            }
                            case PUSH_LAST -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                a.push(b,false);
                                stack.push(a);
                            }
                            case PUSH_ALL_FIRST -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                b.pushAll(a,true);
                                stack.push(b);
                            }
                            case PUSH_ALL_LAST -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                a.pushAll(b,false);
                                stack.push(a);
                            }
                            case TUPLE -> {
                                long count=stack.pop().asLong();
                                checkElementCount(count);
                                Type[] types=new Type[(int)count];
                                for(int i=1;i<=count;i++){
                                    types[types.length-i]=stack.pop().asType();
                                }
                                stack.push(Value.ofType(Type.Tuple.create(types)));
                            }
                            case NEW -> {
                                Type type=stack.pop().asType();
                                if(type instanceof Type.Tuple){
                                    int count=((Type.Tuple)type).elementCount();
                                    Value[] values=new Value[count];
                                    for(int i=1;i<= values.length;i++){
                                        values[count-i]=stack.pop().castTo(((Type.Tuple) type).get(count-i));
                                    }
                                    stack.push(Value.createTuple((Type.Tuple)type,values));
                                }else if(type.isList()){
                                    long initCap=stack.pop().asLong();
                                    stack.push(Value.createList(type,initCap));
                                }else{
                                    throw new ConcatRuntimeError("new only supports tuples and lists");
                                }
                            }
                            case INT_AS_FLOAT -> stack.push(Value.ofFloat(Double.longBitsToDouble(stack.pop().asLong())));
                            case FLOAT_AS_INT -> stack.push(Value.ofInt(Double.doubleToRawLongBits(stack.pop().asDouble())));
                            case OPEN -> {
                                String options = stack.pop().stringValue();
                                String path    = stack.pop().stringValue();
                                stack.push(Value.ofFile(new RandomAccessFileStream(path,options)));
                            }
                            case CLOSE -> {
                                FileStream stream = stack.pop().asStream();
                                stack.push(stream.close()?Value.TRUE:Value.FALSE);
                                //??? keep stream
                            }
                            case READ -> {//<file> <buff> <off> <count> read => <nRead>
                                long count  = stack.pop().asLong();
                                long off    = stack.pop().asLong();
                                Value buff  = stack.pop();
                                FileStream stream = stack.pop().asStream();
                                stack.push(Value.ofInt(stream.read(buff.asByteList(),off,count)));
                            }
                            case WRITE -> {//<file> <buff> <off> <count> write => <isOk>
                                long count  = stack.pop().asLong();
                                long off    = stack.pop().asLong();
                                Value buff  = stack.pop();
                                FileStream stream = stack.pop().asStream();
                                stack.push(stream.write(buff.toByteList(),off,count)?Value.TRUE:Value.FALSE);
                            }
                            case SIZE -> {
                                FileStream stream  = stack.pop().asStream();
                                stack.push(Value.ofInt(stream.size()));
                            }
                            case POS -> {
                                FileStream stream  = stack.pop().asStream();
                                stack.push(Value.ofInt(stream.pos()));
                            }
                            case TRUNCATE -> {
                                FileStream stream = stack.pop().asStream();
                                stack.push(stream.truncate()?Value.TRUE:Value.FALSE);
                            }
                            case SEEK -> {
                                long pos = stack.pop().asLong();
                                FileStream stream = stack.pop().asStream();
                                stack.push(stream.seek(pos)?Value.TRUE:Value.FALSE);
                            }
                            case SEEK_END -> {
                                FileStream stream = stack.pop().asStream();
                                stack.push(stream.seekEnd()?Value.TRUE:Value.FALSE);
                            }
                            case NEW_PROC_TYPE -> {
                                long count=stack.pop().asLong();
                                checkElementCount(count);
                                Type[] outTypes=new Type[(int)count];
                                for(int i=1;i<=count;i++){
                                    outTypes[outTypes.length-i]=stack.pop().asType();
                                }
                                count=stack.pop().asLong();
                                checkElementCount(count);
                                Type[] inTypes=new Type[(int)count];
                                for(int i=1;i<=count;i++){
                                    inTypes[inTypes.length-i]=stack.pop().asType();
                                }
                                stack.push(Value.ofType(Type.Procedure.create(inTypes,outTypes)));
                            }
                            case IS_VAR_ARG -> {
                                Type type = stack.pop().asType();
                                stack.push(type.isVarArg()?Value.TRUE:Value.FALSE);
                            }
                            case OPTIONAL_OF -> {
                                Type contentType = stack.pop().asType();
                                stack.push(Value.ofType(Type.optionalOf(contentType)));
                            }
                            case VAR_ARG ->{
                                Type contentType = stack.pop().asType();
                                stack.push(Value.ofType(Type.varArg(contentType)));
                            }
                            case EMPTY_OPTIONAL -> {
                                Type t=stack.pop().asType();
                                stack.push(Value.emptyOptional(t));
                            }
                            case WRAP -> {
                                Value value=stack.pop();
                                stack.push(Value.wrap(value));
                            }
                            case UNWRAP -> {
                                Value value=stack.pop();
                                stack.push(value.unwrap());
                            }
                            case HAS_VALUE -> {
                                Value value=stack.peek();
                                stack.push(value.hasValue()?Value.TRUE:Value.FALSE);
                            }
                        }
                    }
                    case DEBUG_PRINT -> context.stdOut.println(stack.pop().stringValue());
                    case STD_IN ->
                        stack.push(context.stdInValue);
                    case STD_OUT ->
                        stack.push(context.stdOutValue);
                    case STD_ERR ->
                        stack.push(context.stdErrValue);
                    case DROP ->
                            stack.dropAll(((StackModifierToken)next).off,((StackModifierToken)next).count);
                    case DUP ->
                        stack.dupAll(((StackModifierToken)next).off,((StackModifierToken)next).count);
                    case VARIABLE -> {
                        VariableToken asVar=(VariableToken) next;
                        switch (asVar.accessType){
                            case READ -> {
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            stack.push((globalVariables==null?variables:globalVariables)
                                                    .get(asVar.id.level)[asVar.id.id].value);
                                    case LOCAL -> {
                                        if (globalVariables != null) {
                                            stack.push(variables.get(asVar.id.level)[asVar.id.id].value);
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            stack.push(curried[asVar.id.id]);
                                }
                            }
                            case WRITE -> {
                                Value newValue=stack.pop();
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            (globalVariables==null?variables:globalVariables).get(asVar.id.level)
                                                    [asVar.id.id].setValue(newValue);
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables.get(asVar.id.level)[asVar.id.id].setValue(newValue);
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            throw new ConcatRuntimeError("curried variables are currently unimplemented");
                                }
                            }
                            case CONST_DECLARE,DECLARE -> {
                                Type  type=stack.pop().asType();
                                Value initValue=stack.pop();
                                boolean isConst=asVar.accessType==AccessType.CONST_DECLARE;
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            (globalVariables==null?variables:globalVariables).get(asVar.id.level)
                                                    [asVar.id.id] = new Variable(type, isConst, initValue);
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables.get(asVar.id.level)[asVar.id.id]
                                                    = new Variable(type, isConst, initValue);
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            throw new ConcatRuntimeError("curried variables are currently unimplemented");
                                }
                            }
                        }
                    }
                    case SHORT_AND_JMP -> {
                        Value c = stack.peek();
                        if (c.asBool()) {
                            stack.pop();// remove and evaluate branch
                        }else{
                            ip+=((RelativeJump) next).delta;
                            incIp = false;
                        }
                    }
                    case SHORT_OR_JMP -> {
                        Value c = stack.peek();
                        if (c.asBool()) {//  remove and evaluate branch
                            ip+=((RelativeJump) next).delta;
                            incIp = false;
                        }else{
                            stack.pop();// remove token
                        }
                    }
                    case DECLARE, CONST_DECLARE,IDENTIFIER,VAR_WRITE,PROC_CALL, PROC_ID,MACRO_EXPAND, PLACEHOLDER ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case CONTEXT_OPEN ->
                            variables.add(new Variable[((ContextOpen)next).context.variables.size()]);
                    case CONTEXT_CLOSE -> {
                        if(variables.size()<=1){
                            throw new RuntimeException("unexpected CONTEXT_CLOSE operation");
                        }
                        variables.remove(variables.size()-1);
                    }
                    case PUSH_PROC_PTR -> {
                        ProcedureToken token=(ProcedureToken) next;
                        stack.push(token.getProcedure());
                    }
                    case CALL_PROC, CALL_PTR -> {
                        Value called;
                        if(next.tokenType==TokenType.CALL_PROC){
                            called=((ProcedureToken) next).getProcedure();
                        }else{
                            called = stack.pop();
                        }
                        if(called instanceof Value.Procedure procedure){
                            assert ((Value.Procedure) called).context.curried.isEmpty();
                            ExitType e=recursiveRun(stack,procedure,globalVariables==null?variables:globalVariables,
                                    null,context);
                            if(e!=ExitType.NORMAL){
                                if(e==ExitType.ERROR) {
                                    context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                                }
                                return e;
                            }
                        }else if(called instanceof Value.CurriedProcedure procedure){
                            ExitType e=recursiveRun(stack,procedure,globalVariables==null?variables:globalVariables,
                                    procedure.curried,context);
                            if(e!=ExitType.NORMAL){
                                if(e==ExitType.ERROR) {
                                    context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                                }
                                return e;
                            }
                        }else{
                            throw new ConcatRuntimeError("cannot call objects of type "+called.type);
                        }
                    }
                    case RETURN -> {
                        return ExitType.NORMAL;
                    }
                    case JMP -> {
                        ip+=((RelativeJump) next).delta;
                        incIp = false;
                    }
                    case JEQ -> {
                        Value c = stack.pop();
                        if (c.asBool()) {
                            ip+=((RelativeJump) next).delta;
                            incIp = false;
                        }
                    }
                    case JNE -> {
                        Value c = stack.pop();
                        if (!c.asBool()) {
                            ip+=((RelativeJump) next).delta;
                            incIp = false;
                        }
                    }
                    case SWITCH -> {
                        Value v= stack.pop();
                        Integer jumpTo=((SwitchToken)next).block.blockJumps.get(v);
                        if(jumpTo==null){
                            ip+=((SwitchToken)next).block.defaultJump;
                        }else{
                            ip+=jumpTo;
                        }
                        incIp=false;
                    }
                    case EXIT -> {
                        long exitCode=stack.pop().asLong();
                        context.stdErr.println("exited with exit code:"+exitCode);
                        return ExitType.FORCED;
                    }
                }
            }catch(ConcatRuntimeError|RandomAccessStack.StackUnderflow  e){
                context.stdErr.println(e.getMessage());
                Token token = tokens.get(ip);
                context.stdErr.printf("  while executing %-20s\n   at %s\n",token,token.pos);
                return ExitType.ERROR;
            }catch(Throwable  t){//show expression in source code that crashed the interpreter
                Token token = tokens.get(ip);
                try {
                    context.stdErr.printf("  while executing %-20s\n   at %s\n", token, token.pos);
                }catch (Throwable ignored){}//ignore exceptions while printing
                throw t;
            }
            if(incIp){
                ip++;
            }
        }
        return ExitType.NORMAL;
    }

    private long signedLeftShift(long x, long y) {
        return x&0x8000000000000000L|x<<y;
    }

    private long longPow(Long x, Long y) {
        long pow=1;
        if(y<0){
            return 0;
        }else{
            while(y>0){
                if((y&1)==1){
                    pow*=x;
                }
                x*=x;
                y>>=1;
            }
            return pow;
        }
    }

    static void compileAndRun(String path, IOContext context) throws IOException {
        Interpreter ip = new Interpreter();
        Program program;
        try {
            program = ip.parse(new File(path),null,context);
        }catch (SyntaxError e){
            SyntaxError s = e;
            context.stdErr.println(s.getMessage());
            context.stdErr.println("  at "+ s.pos);
            while(s.getCause() instanceof SyntaxError){
                s =(SyntaxError) s.getCause();
                context.stdErr.println("  at "+ s.pos);
            }
            return;
        }
        RandomAccessStack<Value> stack = ip.run(program,context);
        context.stdOut.println("\nStack:");
        context.stdOut.println(stack);
    }

    public static void main(String[] args) throws IOException {
        if(args.length==0){
            System.out.println("usage: <pathToFile> (-lib <libPath>)");
            return;
        }
        String path=args[0];
        if(args.length>1&&args[1].equals("-lib")){
            if(args.length<3){
                System.out.println("missing parameter for -lib");
                return;
            }
            libPath=args[2];
        }
        File libDir=new File(libPath);
        if(!(libDir.exists()||libDir.mkdirs())){
            System.out.println(libPath+"  is no valid library path");
            return;
        }
        compileAndRun(path,defaultContext);
    }

}
