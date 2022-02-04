package bsoelch.concat;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Interpreter {
    public static final String DEFAULT_FILE_EXTENSION = ".concat";
    //use ## for end of file since it cannot appear in normal tokens
    public static final String END_OF_FILE = "##";
    //use ' as separator for modules, as it cannot be part of identifiers
    public static final String MODULE_SEPARATOR = "'";

    /**
     * Context for running the program
     */
    record IOContext(InputStream stdIn, PrintStream stdOut, PrintStream stdErr) {}
    static final IOContext defaultContext=new IOContext(System.in,System.out,System.err);

    enum TokenType {
        VALUE,CURRIED_PROCEDURE,OPERATOR,CAST,NEW,NEW_LIST,
        DROP,DUP,
        IDENTIFIER,//addLater option to free values/variables
        VARIABLE,
        PLACEHOLDER,//placeholder token for jumps
        CONTEXT_OPEN,CONTEXT_CLOSE,
        CALL_PROC, PUSH_PROC_PTR,CALL_PTR,
        CALL_NATIVE_PROC,PUSH_NATIVE_PROC_PTR,
        RETURN,
        DEBUG_PRINT,ASSERT,//debug time operations, may be replaced with drop in compiled code
        JEQ,JNE,JMP,//jump commands only for internal representation
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
    enum IdentifierType{
        DECLARE,CONST_DECLARE,UNMODIFIED,PROC_ID,VAR_WRITE,NATIVE, NEW_GENERIC, NATIVE_DECLARE
    }
    static class IdentifierToken extends Token {
        final IdentifierType type;
        final String name;
        IdentifierToken(IdentifierType type, String name,FilePosition pos) throws SyntaxError {
            super(TokenType.IDENTIFIER, pos);
            this.name = name;
            this.type=type;
            if(name.isEmpty()){
                throw new SyntaxError("empty variable name",pos);
            }
        }
        @Override
        public String toString() {
            return type.toString()+": \""+ name +"\"";
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
    static class AssertToken extends Token{
        final String message;
        AssertToken(String message,FilePosition pos) {
            super(TokenType.ASSERT,pos);
            this.message = message;
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
    static class NativeProcedureToken extends Token {
        final Value.NativeProcedure value;
        NativeProcedureToken(boolean isPtr,Value.NativeProcedure value, FilePosition pos) {
            super(isPtr?TokenType.PUSH_NATIVE_PROC_PTR :TokenType.CALL_NATIVE_PROC, pos);
            this.value=value;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+value;
        }
    }
    static class TypedToken extends Token {
        final Type target;
        TypedToken(TokenType tokenType,Type target, FilePosition pos) {
            super(tokenType, pos);
            this.target=target;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+target;
        }
    }
    static class ListCreatorToken extends Token implements CodeSection {
        final ArrayList<Token> tokens;
        ListCreatorToken(ArrayList<Token> tokens, FilePosition pos) {
            super(TokenType.NEW_LIST, pos);
            this.tokens=tokens;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+tokens;
        }

        @Override
        public ArrayList<Token> tokens() {
            return tokens;
        }

        @Override
        public VariableContext context() {
            return null;
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
            ProcedureContext procedureId=id.context.procedureContext();
            if(procedureId==null){
                variableType=VariableType.GLOBAL;
            }else if(procedureId==currentContext.procedureContext()){
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
                if(id.isConstant){
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
        PROCEDURE,IF,WHILE,SWITCH_CASE,ENUM,TUPLE,ANONYMOUS_TUPLE,PROC_TYPE,CONST_LIST
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
        final boolean isNative;

        ProcedureBlock(String name, int startToken, FilePosition pos, VariableContext parentContext, boolean isNative) {
            super(startToken, BlockType.PROCEDURE,pos, parentContext);
            this.name = name;
            context=new ProcedureContext(parentContext);
            this.isNative=isNative;
        }
        static Type[] getSignature(List<Token> tokens,boolean tupleMode) throws SyntaxError {
            RandomAccessStack<ValueToken> stack=new RandomAccessStack<>(tokens.size());
            for(Token t:tokens){
                if(t instanceof ValueToken){
                    stack.push(((ValueToken) t));
                }else{//list, optional, tuple, -> are evaluated in parser
                    //TODO! report predeclared procedures as missing variables
                    throw new SyntaxError("Tokens of type "+t.tokenType+
                            " are not supported in "+(tupleMode?"tuple":"procedure")+" signatures",t.pos);
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
                        throw new SyntaxError("Elements in "+(tupleMode?"tuple":"procedure")+" signature have to evaluate to types",v.pos);
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
            inTypes = getSignature(ins,false);
            ins.clear();
        }
        void addOuts(List<Token> outs,FilePosition pos) throws SyntaxError {
            if(inTypes==null){
                throw new SyntaxError("procedure declares output arguments but no input arguments",pos);
            }else if(outTypes!=null){
                throw new SyntaxError("Procedure already has output arguments",pos);
            }
            outTypes = getSignature(outs,false);
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
        int forkPos;

        VariableContext ifContext;
        VariableContext elseContext;
        IfBlock(int startToken,FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.IF,pos, parentContext);
            forkPos=start;
            ifContext=new BlockContext(parentContext);
            elseContext=new BlockContext(parentContext);
        }

        VariableContext newBranch(int tokenPos,FilePosition pos) throws SyntaxError{
            if(forkPos!=-1){
                throw new SyntaxError("unexpected '_if' in if-statement '_if' can only appear after 'else'",pos);
            }
            forkPos=tokenPos;
            ifContext=new BlockContext(elseContext);
            return ifContext;
        }
        VariableContext elseBranch(int tokenPos,FilePosition pos) throws SyntaxError {
            if(forkPos==-1){
                throw new SyntaxError("unexpected 'else' in if-statement 'else' can only appear after 'if' or 'if_",pos);
            }
            branches.add(new IfBranch(forkPos,tokenPos));
            forkPos=-1;
            return elseContext;
        }

        @Override
        VariableContext context() {
            return forkPos!=-1?ifContext:elseContext;
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
                        "'end' can only appear after 'do'",pos);
            }
        }
        VariableContext fork(int tokenPos,FilePosition pos) throws SyntaxError {
            if(forkPos!=-1){
                throw new SyntaxError("duplicate 'do' in while-statement 'do' " +
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
        FilePosition defaultStart=null;

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
                    throw new SyntaxError("case values have to be constant values (got "+
                            token.tokenType+")", token.pos);
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
            return context;
        }
        VariableContext defaultBlock(int blockStart, FilePosition pos) throws SyntaxError {
            if(sectionStart==-1||blockStart!=sectionStart){
                throw new SyntaxError("unexpected 'default' statement",pos);
            }else if(defaultJump!=-1){
                throw new SyntaxError("this switch case already has a 'default' statement",pos);
            }
            defaultJump=blockStart-start;
            defaultStart=pos;
            context=new BlockContext(parentContext);
            return context;
        }
        void end(int endPos,FilePosition pos,IOContext ioContext) throws SyntaxError {
            if(blockStart !=-1||(defaultJump==-1&&endPos>sectionStart)){
                throw new SyntaxError("unfinished case-block",pos);
            }else if(switchType==null){
                throw new SyntaxError("switch-case does not contain any case block",pos);
            }
            if(defaultStart==null) {
                defaultJump = endPos - start;
            }
            if(switchType instanceof Type.Enum){//special checks for enum switch-case
                if(defaultStart!=null){
                    if(blockJumps.size()<((Type.Enum) switchType).elementCount()){
                        ioContext.stdErr.println("Warning: enum switch-case at "+startPos+
                                " contains a default statement (at "+defaultStart+")");
                    }else{
                        throw new SyntaxError("enum switch-case at "+startPos+
                                " contains a unreachable default statement",defaultStart);
                    }
                }else if(blockJumps.size()<((Type.Enum) switchType).elementCount()){
                    //addLater print missing cases
                    throw new SyntaxError("enum switch-case does not cover all cases",pos);
                }
            }
        }

        @Override
        VariableContext context() {
            return context==null?parentContext:context;
        }
    }
    private static class EnumBlock extends CodeBlock{
        final String name;
        final ArrayList<String> elements=new ArrayList<>();
        final ArrayList<FilePosition> elementPositions=new ArrayList<>();
        EnumBlock(String name,int start, FilePosition startPos, VariableContext parentContext) {
            super(start, BlockType.ENUM, startPos, parentContext);
            this.name=name;
        }

        @Override
        VariableContext context() {
            return parentContext;
        }

        public void add(String str, FilePosition pos) throws SyntaxError {
            int prev=elements.indexOf(str);
            if(prev!=-1){
                throw new SyntaxError("duplicate enum entry "+str+
                        " (previously declaration at "+elementPositions.get(prev)+")",pos);
            }
            elements.add(str);
            elementPositions.add(pos);
        }
    }
    private static class TupleBlock extends CodeBlock{
        final String name;
        final GenericContext context;

        TupleBlock(String name,int start, FilePosition startPos, VariableContext parentContext) {
            super(start, BlockType.TUPLE, startPos, parentContext);
            this.name=name;
            context=new GenericContext(parentContext);
        }

        @Override
        VariableContext context() {
            return context;
        }
    }
    private static class ListBlock extends CodeBlock{
        ListBlock(int start, BlockType type, FilePosition startPos, VariableContext parentContext) {
            super(start, type, startPos, parentContext);
        }
        @Override
        VariableContext context() {
            return parentContext;
        }
    }
    private static class ProcTypeBlock extends CodeBlock{
        final int separatorPos;
        ProcTypeBlock(ListBlock start,int separatorPos) {
            super(start.start,BlockType.PROC_TYPE,start.startPos, start.parentContext);
            this.separatorPos=separatorPos;
        }
        @Override
        VariableContext context() {
            return parentContext;
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
            updateNextPos();
        }
        void updateNextPos() {
            nextPos=new FilePosition(path, line, posInLine);
        }
    }

    enum DeclareableType{
        VARIABLE,CONSTANT,CURRIED_VARIABLE,MACRO,PROCEDURE,PREDECLARED_PROCEDURE,ENUM, TUPLE, ENUM_ENTRY, GENERIC, NATIVE_PROC,
        GENERIC_TUPLE,
    }
    static String declarableName(DeclareableType t, boolean a){
        switch (t){
            case VARIABLE -> {
                return a?"a variable":"variable";
            }
            case CONSTANT -> {
                return a?"a constant":"constant";
            }
            case CURRIED_VARIABLE -> {
                return a?"a curried variable":"curried variable";
            }
            case MACRO -> {
                return a?"a macro":"macro";
            }
            case PROCEDURE -> {
                return a?"a procedure":"procedure";
            }
            case PREDECLARED_PROCEDURE -> {
                return a?"a predeclared procedure":"predeclared procedure";
            }
            case ENUM -> {
                return a?"an enum":"enum";
            }
            case ENUM_ENTRY -> {
                return a?"an enum entry":"enum entry";
            }
            case GENERIC_TUPLE, TUPLE -> {
                return a?"a tuple":"tuple";
            }
            case GENERIC -> {
                return a?"a generic type":"generic type";
            }
            case NATIVE_PROC -> {
                return a?"a native procedure":"native procedure";
            }
        }
        throw new RuntimeException("unreachable");
    }
    interface Declareable{
        DeclareableType declarableType();
        FilePosition declaredAt();
    }
    interface NamedDeclareable extends Declareable{
        String name();
    }
    record Macro(FilePosition pos, String name,
                 ArrayList<StringWithPos> content) implements NamedDeclareable{
        @Override
        public String toString() {
            return "macro " +name+":"+content;
        }
        @Override
        public DeclareableType declarableType() {
            return DeclareableType.MACRO;
        }
        @Override
        public FilePosition declaredAt() {
            return pos;
        }
    }
    private static class VariableId implements Declareable{
        final VariableContext context;
        final int level;
        final Type type;
        int id;
        final boolean isConstant;
        final FilePosition declaredAt;
        VariableId(VariableContext context, int level, int id, Type type, boolean isConstant, FilePosition declaredAt){
            this.context=context;
            this.id=id;
            this.level=level;
            this.type=type;
            this.isConstant=isConstant;
            this.declaredAt=declaredAt;
        }
        @Override
        public String toString() {
            return "@"+context+"."+level+"-"+id;
        }

        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
        @Override
        public DeclareableType declarableType() {
            return isConstant?DeclareableType.CONSTANT:DeclareableType.VARIABLE;
        }
    }
    private static class CurriedVariable extends VariableId{
        final VariableId source;
        CurriedVariable(VariableId source,VariableContext context, int id, FilePosition declaredAt) {
            super(context,0, id, source.type, true, declaredAt);
            this.source = source;
        }
        @Override
        public String toString() {
            return "@"+context+".curried"+id;
        }

        @Override
        public DeclareableType declarableType() {
            return DeclareableType.CURRIED_VARIABLE;
        }
    }

    private record GenericTuple(String name, Type.GenericParameter[] params,
                                Type[] types,
                                FilePosition declaredAt) implements NamedDeclareable {
        @Override
        public DeclareableType declarableType() {
            return DeclareableType.GENERIC_TUPLE;
        }
    }

    static abstract class VariableContext{
        final HashMap<String,Declareable> elements =new HashMap<>();
        int variables=0;
        void ensureDeclareable(String name, DeclareableType type, FilePosition pos) throws SyntaxError {
            Declareable prev= elements.get(name);
            if(prev!=null){
                throw new SyntaxError("cannot declare " + declarableName(type,false) + " "+name+
                        ", the identifier is already used by " + declarableName(prev.declarableType(), true)
                        + " (declared at " + prev.declaredAt() + ")",pos);
            }
        }
        VariableId declareVariable(String name, Type type, boolean isConstant, FilePosition pos, IOContext ioContext) throws SyntaxError {
            ensureDeclareable(name,isConstant?DeclareableType.CONSTANT:DeclareableType.VARIABLE,pos);
            VariableId id = new VariableId(this,level(), variables++, type,isConstant, pos);
            elements.put(name, id);
            return id;
        }
        abstract VariableId wrapCurried(String name,VariableId id,FilePosition pos) throws SyntaxError;
        abstract Declareable getDeclareable(String name);
        abstract Declareable unsafeGetDeclared(String name);
        /**returns the enclosing procedure or null if this variable is not enclosed in a procedure*/
        abstract ProcedureContext procedureContext();
        /**number of blocks (excluding procedures) this variable is contained in*/
        abstract int level();
    }

    record ModuleBlock(String[] path,ArrayList<String> imports,FilePosition declaredAt,
                       HashMap<String,PredeclaredProc> predeclaredProcs){
        @Override
        public String toString() {
            return Arrays.toString(path) +" declaredAt " + declaredAt;
        }
    }
    /**File specific part of module parsing*/
    private static class FileContext{
        final ArrayList<String> globalImports=new ArrayList<>();
        final HashMap<String,PredeclaredProc> globalPredeclared = new HashMap<>();
        final ArrayList<ModuleBlock> openModules=new ArrayList<>();
        final HashMap<String,Declareable> declared =new HashMap<>();
    }
    record PredeclaredProc(FilePosition pos,ArrayDeque<ProcedureToken> listeners) implements Declareable{
        @Override
        public DeclareableType declarableType() {
            return DeclareableType.PREDECLARED_PROCEDURE;
        }
        @Override
        public FilePosition declaredAt() {
            return pos;
        }
    }
    private static class RootContext extends VariableContext{
        RootContext(){}
        HashSet<String> modules=new HashSet<>();
        ArrayDeque<FileContext> openFiles=new ArrayDeque<>();

        private FileContext file(){
            return openFiles.peekLast();
        }
        void startFile(){
            openFiles.addLast(new FileContext());
        }
        void startModule(String moduleName,FilePosition declaredAt) throws SyntaxError {
            Declareable d=elements.get(inCurrentModule(moduleName));
            if(d!=null){
                throw new SyntaxError("cannot declare module "+moduleName+
                        ", the identifier is already used by " + declarableName(d.declarableType(), true)
                        + " (declared at " + d.declaredAt() + ")",declaredAt);
            }
            StringBuilder fullPath=new StringBuilder();
            for(ModuleBlock m:file().openModules){
                for(String s:m.path){
                    fullPath.append(s).append(MODULE_SEPARATOR);
                }
            }
            String[] path = moduleName.split(MODULE_SEPARATOR);
            for(String s:path){
                fullPath.append(s);
                modules.add(fullPath.toString());
                fullPath.append(MODULE_SEPARATOR);
            }
            file().openModules.add(new ModuleBlock(path,new ArrayList<>(),declaredAt,new HashMap<>()));
        }
        void addImport(String path,FilePosition pos) throws SyntaxError {
            if(modules.contains(path)){
                path+="'";
                if(file().openModules.size()>0){
                    file().openModules.get(file().openModules.size()-1).imports.add(path);
                }else{
                    file().globalImports.add(path);
                }
            }else if(elements.containsKey(path)){
                //addLater static imports
                throw new UnsupportedOperationException("static imports are currently unimplemented");
            }else{
                //addLater formatted printing of module paths
                throw new SyntaxError("module "+path+" does not exist",pos);
            }
        }
        void endModule(FilePosition pos) throws SyntaxError {
            if(file().openModules.isEmpty()){
                throw new SyntaxError("Unexpected End of module",pos);
            }
            ModuleBlock removed = file().openModules.remove(file().openModules.size() - 1);
            if(removed.predeclaredProcs.size()>0){
                StringBuilder message=new StringBuilder("Syntax Error: missing variables/procedures");
                for(Map.Entry<String, PredeclaredProc> p:removed.predeclaredProcs.entrySet()){
                    message.append("\n- ").append(p.getKey()).append(" (at ").append(p.getValue().pos).append(")");
                }
                throw new SyntaxError(message.toString(),pos);
            }
        }
        void endFile(FilePosition pos,IOContext context) throws SyntaxError {
            FileContext ctx=openFiles.removeLast();
            if(ctx.openModules.size()>0) {
                context.stdErr.println("unclosed modules at end of File:");
                while(ctx.openModules.size()>0){
                    ModuleBlock removed=ctx.openModules.remove(ctx.openModules.size()-1);
                    if(removed.predeclaredProcs.size()>0){
                        StringBuilder message=new StringBuilder("Syntax Error: missing variables/procedures");
                        for(Map.Entry<String, PredeclaredProc> p:removed.predeclaredProcs.entrySet()){
                            message.append("\n- ").append(p.getKey()).append(" (at ").append(p.getValue().pos).append(")");
                        }
                        throw new SyntaxError(message.toString(),pos);
                    }
                    context.stdErr.println(" - "+removed);
                }
            }
            if(ctx.globalPredeclared.size()>0){
                StringBuilder message=new StringBuilder("Syntax Error: missing variables/procedures");
                for(Map.Entry<String, PredeclaredProc> p:ctx.globalPredeclared.entrySet()){
                    message.append("\n- ").append(p.getKey()).append(" (at ").append(p.getValue().pos).append(")");
                }
                throw new SyntaxError(message.toString(),pos);
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

        Declareable getDeclareable(String name){
            Declareable d;
            ArrayDeque<String> paths = currentPaths();
            while(paths.size()>0){//go through all modules
                d=elements.get(paths.removeLast()+name);
                if(d!=null){
                    return d;
                }
            }
            return elements.get(name);
        }
        void checkShadowed(Declareable declared,String name,FilePosition pos,IOContext ioContext){
            Declareable shadowed = file().declared.get(name);
            if(shadowed!=null){
                ioContext.stdErr.println("Warning: "+declarableName(declared.declarableType(),false)+" " + name
                        + " declared at " + pos +"\n     shadows existing " +
                        declarableName(shadowed.declarableType(),false)+ " declared at "+ shadowed.declaredAt());
            }
            file().declared.put(name,declared);
        }

        HashMap<String,PredeclaredProc> predeclaredProcs(){
            if(file().openModules.size()>0){
                return file().openModules.get(file().openModules.size()-1).predeclaredProcs;
            }else{
                return file().globalPredeclared;
            }
        }

        void declareProcedure(String name, Value.Procedure proc,IOContext ioContext) throws SyntaxError {
            String name0=name;
            name=inCurrentModule(name);
            PredeclaredProc predeclared=predeclaredProcs().remove(name);
            if(predeclared!=null){
                for(ProcedureToken token:predeclared.listeners){
                    token.declareProcedure(proc);
                }
                Declareable prev=elements.remove(name);
                assert prev==predeclared;
            }
            ensureDeclareable(name,DeclareableType.PROCEDURE,proc.declaredAt);
            checkShadowed(proc,name0,proc.declaredAt,ioContext);
            elements.put(name,proc);
        }
        PredeclaredProc predeclareProcedure(String name,VariableContext callee,FilePosition pos) throws SyntaxError {
            String localName=inCurrentModule(name);
            PredeclaredProc predeclared=predeclaredProcs().get(localName);
            if(predeclared==null){
                if(callee.procedureContext()!=null){
                    predeclared = new PredeclaredProc(pos, new ArrayDeque<>());
                    predeclaredProcs().put(localName, predeclared);
                    ensureDeclareable(localName,DeclareableType.PREDECLARED_PROCEDURE,pos);
                    elements.put(localName,predeclared);//add to elements for better handling of shadowing
                }else{
                    throw new SyntaxError("variable "+name+" does not exist",pos);
                }
            }
            return predeclared;
        }

        void removeMacro(String name, FilePosition pos) throws SyntaxError {
            Declareable removed=elements.remove(inCurrentModule(name));
            if(removed==null){
                throw new SyntaxError("macro "+name+" does not exists in the current module",pos);
            }else if(removed.declarableType()!=DeclareableType.MACRO){
                throw new SyntaxError("macro "+name+" does not exists, or is" +
                        " shadowed by "+declarableName(removed.declarableType(),false)
                        +" (declared at "+removed.declaredAt()+")",pos);
            }
        }
        void declareEnum(EnumBlock source, IOContext ioContext) throws SyntaxError {
            String localName=inCurrentModule(source.name);
            Type.Enum anEnum=new Type.Enum(source.name,source.elements.toArray(new String[0]),
                    source.elementPositions,source.startPos);
            ensureDeclareable(localName,DeclareableType.ENUM,anEnum.declaredAt);
            checkShadowed(anEnum,anEnum.name,anEnum.declaredAt,ioContext);
            elements.put(localName,anEnum);
            for(int i = 0; i<anEnum.entryNames.length; i++){
                String fieldName = anEnum.entryNames[i];
                String path = localName + MODULE_SEPARATOR + fieldName;
                Value.EnumEntry entry = anEnum.entries[i];
                ensureDeclareable(path,DeclareableType.ENUM,entry.declaredAt);
                checkShadowed(entry, fieldName, entry.declaredAt,ioContext);
                elements.put(path,entry);
            }
        }
        void declareNamedDeclareable(NamedDeclareable declareable, IOContext ioContext) throws SyntaxError {
            String localName=inCurrentModule(declareable.name());
            ensureDeclareable(localName,declareable.declarableType(),declareable.declaredAt());
            checkShadowed(declareable,declareable.name(),declareable.declaredAt(),ioContext);
            elements.put(localName,declareable);
        }

        @Override
        VariableId declareVariable(String name, Type type, boolean isConstant, FilePosition pos, IOContext ioContext) throws SyntaxError {
            String name0=name;
            name=inCurrentModule(name);
            VariableId id = super.declareVariable(name, type, isConstant, pos,ioContext);
            checkShadowed(id,name0,pos,ioContext);
            return id;
        }
        @Override
        VariableId wrapCurried(String name, VariableId id, FilePosition pos){
            return id;
        }

        @Override
        Declareable unsafeGetDeclared(String name) {
            String name0=name;
            name=inCurrentModule(name);
            Declareable id= elements.get(name);
            if(id==null){
                id= file().declared.get(name0);
            }
            return id;
        }

        @Override
        ProcedureContext procedureContext() {
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
        VariableId declareVariable(String name, Type type, boolean isConstant, FilePosition pos, IOContext ioContext) throws SyntaxError {
            VariableId id = super.declareVariable(name, type, isConstant, pos,ioContext);
            Declareable shadowed = parent.unsafeGetDeclared(name);
            if (shadowed != null) {//check for shadowing
                ioContext.stdErr.println("Warning: variable " + name + " declared at " + pos +
                        "\n     shadows existing " + declarableName(shadowed.declarableType(),false)
                        + " declared at "  + shadowed.declaredAt());
            }
            return id;
        }

        @Override
        Declareable getDeclareable(String name) {
            Declareable id = elements.get(name);
            return id == null ? parent.getDeclareable(name) : id;
        }
        @Override
        Declareable unsafeGetDeclared(String name) {
            Declareable id = elements.get(name);
            return id == null ? parent.unsafeGetDeclared(name) : id;
        }
        @Override
        VariableId wrapCurried(String name, VariableId id, FilePosition pos) throws SyntaxError {
            return parent.wrapCurried(name, id, pos);
        }

        @Override
        ProcedureContext procedureContext() {
            return parent.procedureContext();
        }

        @Override
        int level() {
            return parent.level()+1;
        }
    }
    static class GenericContext extends BlockContext{
        ArrayList<Type.GenericParameter> generics=new ArrayList<>();

        public GenericContext(VariableContext parent) {
            super(parent);
        }
        void declareGeneric(String name, FilePosition pos, IOContext ioContext) throws SyntaxError {
            Type.GenericParameter generic;
            ensureDeclareable(name,DeclareableType.GENERIC,pos);
            generic = new Type.GenericParameter(generics.size(), pos);
            generics.add(generic);
            elements.put(name, generic);
            Declareable shadowed = parent.unsafeGetDeclared(name);
            if (shadowed != null) {//check for shadowing
                ioContext.stdErr.println("Warning: variable " + name + " declared at " + pos +
                        "\n     shadows existing " + declarableName(shadowed.declarableType(),false)
                        + " declared at "  + shadowed.declaredAt());
            }
        }
    }

    //addLater only allow generics in in-signature
    static class ProcedureContext extends GenericContext {
        ArrayList<CurriedVariable> curried=new ArrayList<>();
        ProcedureContext(VariableContext parent){
            super(parent);
            assert parent!=null;
        }

        VariableId wrapCurried(String name,VariableId id,FilePosition pos) throws SyntaxError {
            ProcedureContext procedure = id.context.procedureContext();
            if(procedure !=this){
                id=parent.wrapCurried(name, id, pos);//curry through parent
                if(!id.isConstant){
                    throw new SyntaxError("external variable "+name+" is not constant",pos);
                }else if(procedure !=null){
                    id=new CurriedVariable(id,this, curried.size(), pos);
                    curried.add((CurriedVariable)id);//curry variable
                    elements.put(name,id);//add curried variable to variable list
                }
            }
            return id;
        }
        @Override
        ProcedureContext procedureContext() {
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
                        if(reader.buffer.length()>0){
                            current=next;
                            next=reader.buffer.toString();
                            if(current!=null){
                                finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),
                                        program,ioContext);
                            }
                            reader.nextToken();
                        }
                        reader.updateNextPos();
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
                                } else if (c == '+') {
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
                                default ->
                                        throw new IllegalArgumentException("The escape sequence: '\\" + c + "' is not supported");
                            }
                        }else{
                            reader.buffer.append((char)c);
                        }
                    }
                    break;
                case COMMENT:
                    if(c=='+'){
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
        program.rootContext.endFile(reader.currentPos,ioContext);
        return program;
    }

    /**@return false if the value was an integer otherwise true*/
    private boolean tryParseInt(ArrayList<Token> tokens, String str0,FilePosition pos) throws SyntaxError {
        try {
            String str=str0;
            boolean unsigned=false;
            if(str.toLowerCase(Locale.ROOT).endsWith("u")){//unsigned
                str=str.substring(0,str.length()-1);
                unsigned=true;
            }
            if(intDec.matcher(str).matches()){//dez-Int
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,10,unsigned), unsigned), pos, false));
                return false;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,2,unsigned), unsigned), pos, false));
                return false;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,16,unsigned), unsigned), pos, false));
                return false;
            }
        } catch (ConcatRuntimeError nfeL) {
            throw new SyntaxError("Number out of Range: "+str0,pos);
        }
        return true;
    }

    private void finishWord(String str,String next, ArrayList<Token> tokens, ArrayDeque<CodeBlock> openBlocks,
                            Macro[] currentMacroPtr, FilePosition pos, Program program,IOContext ioContext) throws SyntaxError, IOException {
        if (str.length() > 0) {
            if(currentMacroPtr[0]!=null){//handle macros
                if(str.equals("#end")){
                    program.rootContext.declareNamedDeclareable(currentMacroPtr[0],ioContext);
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
                String prevId=(prev instanceof IdentifierToken &&((IdentifierToken) prev).type == IdentifierType.UNMODIFIED)?
                        ((IdentifierToken)prev).name:null;
                VariableContext context=getContext(openBlocks.peekLast(),program.rootContext);
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
                        if(prevId!=null){
                            tokens.remove(tokens.size()-1);
                            program.rootContext.removeMacro(prevId,pos);
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
                            program.rootContext.addImport(prevId,pos);
                        }else{
                            throw new SyntaxError("imported module name has to be an identifier",pos);
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
                            if(((IdentifierToken) prev).type == IdentifierType.UNMODIFIED){
                                prev=new IdentifierToken(IdentifierType.VAR_WRITE,((IdentifierToken) prev).name,prev.pos);
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
                        }else if(prevId!=null){
                            Token prePrev=tokens.get(tokens.size()-2);
                            if(!(prePrev instanceof IdentifierToken && ((IdentifierToken) prePrev).type==IdentifierType.UNMODIFIED)){
                                throw new SyntaxError("invalid token for '.' modifier: "+prePrev,prePrev.pos);
                            }
                            String newName=((IdentifierToken)prePrev).name+ MODULE_SEPARATOR +prevId;
                            tokens.remove(tokens.size()-1);
                            prev=new IdentifierToken(IdentifierType.UNMODIFIED, newName,pos);
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
                            prev=new IdentifierToken(IdentifierType.DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=:' modifier "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=$" -> {
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '=$' modifier",pos);
                        }else if(prevId!=null){
                            prev=new IdentifierToken(IdentifierType.CONST_DECLARE,prevId,prev.pos);
                        }else if(prev instanceof IdentifierToken&&((IdentifierToken) prev).type==IdentifierType.NATIVE){
                            prev=new IdentifierToken(IdentifierType.NATIVE_DECLARE,((IdentifierToken) prev).name,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=$' modifier: "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "native" -> {
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for 'native' modifier",pos);
                        }else if(prevId!=null){
                            prev=new IdentifierToken(IdentifierType.NATIVE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for 'native' modifier: "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "@()"->{
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '@()' modifier",pos);
                        }else if(prevId!=null){
                            tokens.set(tokens.size()-1,new IdentifierToken(IdentifierType.PROC_ID,prevId,prev.pos));
                        }else{
                            throw new SyntaxError("invalid token for '@()' modifier: "+prev,prev.pos);
                        }
                        return;
                    }
                    case "<>" ->{
                        if(prev==null){
                            throw new SyntaxError("not enough tokens tokens for '<>' modifier",pos);
                        }else if(prevId!=null){
                            tokens.set(tokens.size()-1,new IdentifierToken(IdentifierType.NEW_GENERIC,prevId,prev.pos));
                        }else{
                            throw new SyntaxError("invalid token for '<>' modifier: "+prev,prev.pos);
                        }
                        return;
                    }
                }
                if(prev!=null){
                    if((!(next.equals(".")||str.equals("proc")||str.equals("procedure")||str.equals("enum")
                            ||(str.equals("tuple")&&openBlocks.size()==0)))&&
                            prev instanceof IdentifierToken identifier){
                        int index=tokens.size()-1;
                        //update variables
                        switch (identifier.type){
                            case NATIVE ->
                                throw new RuntimeException("unreachable");
                            case NEW_GENERIC -> {
                                if(!(context instanceof GenericContext)){
                                    throw new SyntaxError("generics can only be declared in tuple and procedure signatures",prev.pos);
                                }
                                ((GenericContext) context).declareGeneric( identifier.name,identifier.pos,ioContext);
                                tokens.remove(index);
                            }
                            case DECLARE,CONST_DECLARE,NATIVE_DECLARE -> {
                                if(index>=1&&(prev=tokens.get(index-1)) instanceof ValueToken){
                                    try {//remember constant declarations
                                        Type type=((ValueToken)prev).value.asType();
                                        VariableId id=context.declareVariable(
                                                identifier.name,type, identifier.type != IdentifierType.DECLARE,
                                                identifier.pos,ioContext);
                                        AccessType accessType = identifier.type == IdentifierType.DECLARE ?
                                                AccessType.DECLARE : AccessType.CONST_DECLARE;
                                        //only remember root-level constants
                                        if(identifier.type==IdentifierType.NATIVE_DECLARE){
                                            tokens.subList(index-1,tokens.size()).clear();
                                            program.globalConstants.put(id,Value.loadNativeConstant(type,identifier.name,pos));
                                            break;
                                        }else if(id.isConstant && id.context.procedureContext() == null
                                                && (prev = tokens.get(index - 2)) instanceof ValueToken){
                                            program.globalConstants.put(id,((ValueToken) prev).value.clone(true).castTo(type));
                                            tokens.subList(index-2, tokens.size()).clear();
                                            break;//don't add token to code
                                        }
                                        tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                                accessType,context));
                                    } catch (ConcatRuntimeError e) {
                                        throw new SyntaxError(e.getMessage(),prev.pos);
                                    }
                                }else{
                                    throw new SyntaxError("Token before declaration has to be a type",identifier.pos);
                                }
                            }
                            case UNMODIFIED -> {
                                Declareable d=context.getDeclareable(identifier.name);
                                DeclareableType type=d==null?DeclareableType.PREDECLARED_PROCEDURE:d.declarableType();
                                switch (type){
                                    case PROCEDURE -> {
                                        Value.Procedure proc=(Value.Procedure)d;
                                        ProcedureToken token=new ProcedureToken(false,proc,identifier.pos);
                                        tokens.set(index,token);
                                    }
                                    case NATIVE_PROC -> {
                                        Value.NativeProcedure proc=(Value.NativeProcedure)d;
                                        NativeProcedureToken token=new NativeProcedureToken(false,proc,identifier.pos);
                                        tokens.set(index,token);
                                    }
                                    case PREDECLARED_PROCEDURE -> {
                                        PredeclaredProc proc;
                                        if(d!=null){
                                            proc=(PredeclaredProc) d;
                                        }else{
                                            proc=program.rootContext.predeclareProcedure(identifier.name,context,identifier.pos);
                                        }
                                        ProcedureToken token=new ProcedureToken(false,null,identifier.pos);
                                        proc.listeners.add(token);
                                        tokens.set(index,token);
                                    }
                                    case VARIABLE,CONSTANT,CURRIED_VARIABLE -> {
                                        VariableId id=(VariableId)d;
                                        id=context.wrapCurried(identifier.name,id,identifier.pos);
                                        Value constValue=program.globalConstants.get(id);
                                        if(constValue!=null){
                                            tokens.set(index,new ValueToken(constValue,identifier.pos, false));
                                        }else{
                                            tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                                    AccessType.READ,context));
                                        }
                                    }
                                    case MACRO -> {
                                        Macro m=(Macro) d;
                                        tokens.remove(index);//remove prev
                                        for(int i=0;i<m.content.size();i++){
                                            StringWithPos s=m.content.get(i);
                                            finishWord(s.str,i+1<m.content.size()?m.content.get(i+1).str:"##",
                                                    tokens, openBlocks, currentMacroPtr, new FilePosition(s.start, identifier.pos),
                                                    program,ioContext);
                                        }
                                        //update identifiers at end of macro
                                        finishWord(END_OF_FILE,next,tokens, openBlocks, currentMacroPtr, identifier.pos, program,ioContext);
                                    }
                                    case TUPLE,ENUM,GENERIC ->
                                        tokens.set(index,
                                                new ValueToken(Value.ofType((Type)d), identifier.pos, false));
                                    case ENUM_ENTRY ->
                                        tokens.set(index,
                                                new ValueToken((Value.EnumEntry)d, identifier.pos, false));
                                    case GENERIC_TUPLE ->{
                                        tokens.remove(index);
                                        GenericTuple g = (GenericTuple) d;
                                        Type[] genArgs=new Type[g.params.length];
                                        for(int i= genArgs.length-1;i>=0;i--){
                                            if(index<=0){
                                                throw new SyntaxError("Not enough arguments for "+
                                                        declarableName(DeclareableType.GENERIC_TUPLE,false)+" "+((GenericTuple) d).name,pos);
                                            }
                                            prev=tokens.remove(--index);
                                            if(!(prev instanceof ValueToken)){
                                                throw new SyntaxError("invalid token for type-parameter:"+prev,prev.pos);
                                            }
                                            try {
                                                genArgs[i]=((ValueToken)prev).value.asType();
                                            } catch (TypeError e) {
                                                throw new SyntaxError(e.getMessage(),prev.pos);
                                            }
                                        }
                                        tokens.add(new ValueToken(Value.ofType(
                                                Type.GenericTuple.create(g.name,g.params.clone(),genArgs,g.types.clone(),g.declaredAt)),
                                                identifier.pos, false));
                                    }
                                }
                            }
                            case VAR_WRITE -> {
                                Declareable d=context.getDeclareable(identifier.name);
                                if(d==null){
                                    throw new SyntaxError("variable "+identifier.name+" does not exist",pos);
                                }else if(d.declarableType()==DeclareableType.VARIABLE){
                                    VariableId id=(VariableId) d;
                                    context.wrapCurried(identifier.name,id,identifier.pos);
                                    assert !program.globalConstants.containsKey(id);
                                    tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                            AccessType.WRITE,context));
                                }else{
                                    throw new SyntaxError(declarableName(d.declarableType(),false)+" "+
                                            identifier.name+" (declared at "+d.declaredAt()+") is not a variable",pos);
                                }
                            }
                            case PROC_ID -> {
                                Declareable d=context.getDeclareable(identifier.name);
                                if(d instanceof Value.NativeProcedure proc){
                                    NativeProcedureToken token=new NativeProcedureToken(true,proc,pos);
                                    tokens.set(index,token);
                                }else if(d instanceof Value.Procedure proc){
                                    ProcedureToken token=new ProcedureToken(true,proc,pos);
                                    tokens.set(index,token);
                                }else if(d==null||d instanceof PredeclaredProc){
                                    ProcedureToken token=new ProcedureToken(true,null,pos);
                                    PredeclaredProc proc;
                                    if(d!=null){
                                        proc=(PredeclaredProc) d;
                                    }else{
                                        proc=program.rootContext.predeclareProcedure(identifier.name,context,pos);
                                    }
                                    proc.listeners.add(token);
                                    tokens.set(index,token);
                                }else{
                                    throw new SyntaxError(declarableName(d.declarableType(),false)+" "+
                                            identifier.name+" (declared at "+d.declaredAt()+") is not a procedure",pos);
                                }
                            }
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
                        addWord(str,tokens,openBlocks,pos,program.rootContext,ioContext);
                    }
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
                         FilePosition pos,RootContext rootContext,IOContext ioContext) throws SyntaxError {
        Token prev;
        switch (str) {
            case END_OF_FILE -> {} //## string can only be passed to the method on end of file
            case "true"  -> tokens.add(new ValueToken(Value.TRUE,    pos, false));
            case "false" -> tokens.add(new ValueToken(Value.FALSE,   pos, false));

            case "bool"       -> tokens.add(new ValueToken(Value.ofType(Type.BOOL),              pos, false));
            case "byte"       -> tokens.add(new ValueToken(Value.ofType(Type.BYTE),              pos, false));
            case "int"        -> tokens.add(new ValueToken(Value.ofType(Type.INT),               pos, false));
            case "uint"       -> tokens.add(new ValueToken(Value.ofType(Type.UINT),              pos, false));
            case "codepoint"  -> tokens.add(new ValueToken(Value.ofType(Type.CODEPOINT),         pos, false));
            case "float"      -> tokens.add(new ValueToken(Value.ofType(Type.FLOAT),             pos, false));
//addLater define string/ustring in concat
            case "string"     -> tokens.add(new ValueToken(Value.ofType(Type.RAW_STRING()),      pos, false));
            case "ustring"    -> tokens.add(new ValueToken(Value.ofType(Type.UNICODE_STRING()),  pos, false));
            case "type"       -> tokens.add(new ValueToken(Value.ofType(Type.TYPE),              pos, false));
            case "*->*"       -> tokens.add(new ValueToken(Value.ofType(Type.UNTYPED_PROCEDURE), pos, false));
            case "var"        -> tokens.add(new ValueToken(Value.ofType(Type.ANY),               pos, false));
            case "(list)"     -> tokens.add(new ValueToken(Value.ofType(Type.UNTYPED_LIST),      pos, false));
            case "(optional)" -> tokens.add(new ValueToken(Value.ofType(Type.UNTYPED_OPTIONAL),  pos, false));

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
            case "isEnum"->{
                if(tokens.size()>0&&(prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        tokens.set(tokens.size()-1,
                                new ValueToken(((ValueToken)prev).value.asType() instanceof Type.Enum ?Value.TRUE:Value.FALSE,
                                        pos, false));
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new OperatorToken(OperatorType.IS_ENUM,pos));
                }
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
                            for(int i=(int)outCount-1;i>=0;i--){
                                prev=tokens.get(iMin+i);
                                if(prev instanceof ValueToken){
                                    outTypes[i]=((ValueToken) prev).value.asType();
                                }else{
                                    throw new SyntaxError("illegal token in proc signature "+prev,prev.pos);
                                }
                            }
                            Type[] inTypes=new Type[(int)inCount];
                            iMin=iMin-1-(int)inCount;
                            for(int i=(int)inCount-1;i>=0;i--){
                                prev=tokens.get(iMin+i);
                                if(prev instanceof ValueToken){
                                    inTypes[i]=((ValueToken) prev).value.asType();
                                }else{
                                    throw new SyntaxError("illegal token in proc signature "+prev,prev.pos);
                                }
                            }
                            tokens.subList(iMin, tokens.size()).clear();
                            tokens.add(new ValueToken(Value.ofType(Type.Procedure.create(inTypes,outTypes)),pos, false));
                        }
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e.getMessage(), prev.pos);
                    }
                }else{
                    throw new SyntaxError("not enough arguments for operator '->'",pos);
                }
            }

            case "cast"   ->  {
                if(tokens.size()<1||!(tokens.get(tokens.size()-1) instanceof ValueToken)){
                    tokens.add(new TypedToken(TokenType.CAST,null,pos));
                }else{
                    try {
                        Type type= ((ValueToken) tokens.remove(tokens.size()-1)).value.asType();
                        tokens.add(new TypedToken(TokenType.CAST,type,pos));
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }
            }
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
            case "assert"    -> {
                if(tokens.size()<2){
                    throw new SyntaxError("not enough tokens for 'assert'",pos);
                }
                prev=tokens.remove(tokens.size()-1);
                if(!(prev instanceof ValueToken&&((ValueToken) prev).value.isString())){
                    throw new SyntaxError("tokens directly preceding 'assert' has to be a string-constant",pos);
                }
                String message=((ValueToken) prev).value.stringValue();
                if((prev=tokens.get(tokens.size()-1)) instanceof ValueToken){
                    try {
                        if(!((ValueToken) prev).value.asBool()){
                            throw new SyntaxError("assertion failed: "+message,pos);
                        }
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    tokens.add(new AssertToken(message, pos));
                }
            }

            case "tuple" -> {
                String name;
                if(openBlocks.size()>0){//addLater?  anonymous tuple (declarable at non-root level)
                    throw new SyntaxError("tuples can only be declared at root level",pos);
                }
                if(tokens.size()==0){
                    throw new SyntaxError("missing enum name",pos);
                }
                prev=tokens.remove(tokens.size()-1);
                if(!(prev instanceof IdentifierToken)){
                    throw new SyntaxError("token before root level tuple has to be an identifier",pos);
                }else if(((IdentifierToken) prev).type!=IdentifierType.UNMODIFIED){
                    throw new SyntaxError("token before root level tuple has to be an unmodified identifier",pos);
                }
                name = ((IdentifierToken) prev).name;
                openBlocks.add(new TupleBlock(name, tokens.size(),pos,rootContext));
            }
            case "enum" ->{
                if(openBlocks.size()>0){
                    throw new SyntaxError("enums can only be declared at root level",pos);
                }
                if(tokens.size()==0){
                    throw new SyntaxError("missing enum name",pos);
                }
                prev=tokens.remove(tokens.size()-1);
                if(!(prev instanceof IdentifierToken)){
                    throw new SyntaxError("token before 'enum' has to be an identifier",pos);
                }else if(((IdentifierToken) prev).type!=IdentifierType.UNMODIFIED){
                    throw new SyntaxError("token before 'enum' has to be an unmodified identifier",pos);
                }
                String name = ((IdentifierToken) prev).name;
                openBlocks.add(new EnumBlock(name, tokens.size(),pos,rootContext));
            }
            case "proc","procedure" ->{
                if(openBlocks.size()>0){
                    throw new SyntaxError("procedures can only be declared at root level",pos);
                }
                if(tokens.size()==0){
                    throw new SyntaxError("missing procedure name",pos);
                }
                prev=tokens.remove(tokens.size()-1);
                boolean isNative=false;
                if(!(prev instanceof IdentifierToken)){
                    throw new SyntaxError("token before 'proc' has to be an identifier",pos);
                }else if(((IdentifierToken) prev).type==IdentifierType.NATIVE){
                    isNative=true;
                }else if(((IdentifierToken) prev).type!=IdentifierType.UNMODIFIED){
                    throw new SyntaxError("token before 'proc' has to be an unmodified identifier",pos);
                }
                String name = ((IdentifierToken) prev).name;
                openBlocks.add(new ProcedureBlock(name, tokens.size(),pos,rootContext,isNative));
            }
            case "=>" ->{
                CodeBlock block = openBlocks.peekLast();
                if(block instanceof ProcedureBlock proc) {
                    proc.addIns(tokens.subList(proc.start,tokens.size()),pos);
                }else if(block!=null&&block.type==BlockType.ANONYMOUS_TUPLE){
                    openBlocks.removeLast();
                    openBlocks.addLast(new ProcTypeBlock((ListBlock)block,tokens.size()));
                }else{
                    throw new SyntaxError("'=>' can only be used in proc- or proc-type blocks ",pos);
                }
            }
            case "lambda","λ" ->
                    openBlocks.add(new ProcedureBlock(null, tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext), false));
            case ":" -> {
                CodeBlock block=openBlocks.peekLast();
                if(block==null){
                    throw new SyntaxError(": can only be used in proc- and lambda- blocks",pos);
                }else if(block.type==BlockType.PROCEDURE){
                    //handle procedure separately since : does not change context of produce a jump
                    ProcedureBlock proc=(ProcedureBlock) block;
                    proc.addOuts(tokens.subList(proc.start,tokens.size()),pos);
                }else{
                    throw new SyntaxError(": can only be used in proc- and lambda- blocks", pos);
                }
            }
            case "while" -> openBlocks.add(new WhileBlock(tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "do" -> {
                CodeBlock block=openBlocks.peekLast();
                if(block==null){
                    throw new SyntaxError("do can only be used in while- blocks",pos);
                }else{
                    int forkPos=tokens.size();
                    tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                    VariableContext newContext;
                    if (block.type == BlockType.WHILE) {
                        newContext = ((WhileBlock) block).fork(forkPos, pos);
                    } else {
                        throw new SyntaxError(": can only be used in while- blocks", pos);
                    }
                    tokens.add(new ContextOpen(newContext,pos));
                }
            }
            case "switch"->{
                SwitchCaseBlock switchBlock=new SwitchCaseBlock(tokens.size(), pos,getContext(openBlocks.peekLast(), rootContext));
                openBlocks.addLast(switchBlock);
                //handle switch-case separately since : does not change context and produces special jump
                tokens.add(new SwitchToken(switchBlock,pos));
                switchBlock.newSection(tokens.size(),pos);
            }
            case "if" ->{
                IfBlock ifBlock = new IfBlock(tokens.size(), pos, getContext(openBlocks.peekLast(), rootContext));
                openBlocks.addLast(ifBlock);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                tokens.add(new ContextOpen(ifBlock.context(),pos));
            }
            case "_if" -> {
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof IfBlock ifBlock)){
                    throw new SyntaxError("_if can only be used in if-blocks",pos);
                }
                VariableContext context=ifBlock.newBranch(tokens.size(),pos);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                tokens.add(new ContextOpen(context,pos));
            }
            case "else" -> {
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof IfBlock ifBlock)){
                    throw new SyntaxError("elif can only be used in if-blocks",pos);
                }
                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                VariableContext context=ifBlock.elseBranch(tokens.size(),pos);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
                if(ifBlock.branches.size()<2){//start else-context after first else
                    tokens.add(new ContextOpen(context,pos));
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
                            ArrayList<Type.GenericParameter> generics=((ProcedureBlock) block).context.generics;
                            if(generics.size()>0){
                                throw new UnsupportedOperationException("generic procedures are currently not supported");
                            }
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
                                procType=Type.UNTYPED_PROCEDURE;
                            }
                            if(((ProcedureBlock) block).isNative){
                                assert ((ProcedureBlock) block).name!=null;
                                if(content.size()>0){
                                    throw new SyntaxError("unexpected token: "+subList.get(0)+
                                            " (at "+subList.get(0).pos+") native procedures have to have an empty body",pos);
                                }
                                Value.NativeProcedure proc=Value.createNativeProcedure((Type.Procedure) procType,block.startPos,
                                        ((ProcedureBlock) block).name);
                                rootContext.declareNamedDeclareable(proc,ioContext);
                            }else{
                                Value.Procedure proc=Value.createProcedure(procType, content,
                                        generics.toArray(Type.GenericParameter[]::new), block.startPos, context);
                                if (context.curried.isEmpty()) {
                                    if(((ProcedureBlock) block).name!=null){
                                        rootContext.declareProcedure(((ProcedureBlock) block).name,proc,ioContext);
                                    }else{
                                        tokens.add(new ValueToken(proc, block.startPos, false));
                                    }
                                } else {
                                    if(((ProcedureBlock) block).name!=null){
                                        throw new RuntimeException("named procedures cannot be curried");
                                    }
                                    tokens.add(new ValueToken(TokenType.CURRIED_PROCEDURE,proc, block.startPos, false));
                                }
                            }
                        }
                        case IF -> {
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
                        case SWITCH_CASE -> {
                            if(((SwitchCaseBlock)block).defaultJump!=-1){
                                tokens.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            }
                            ((SwitchCaseBlock)block).end(tokens.size(),pos,ioContext);
                            for(Integer i:((SwitchCaseBlock) block).blockEnds){
                                tmp=tokens.get(i);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(i,new RelativeJump(TokenType.JMP,tmp.pos,tokens.size()-i));
                            }
                        }
                        case ENUM -> {
                            if(tokens.size()>block.start){
                                tmp=tokens.get(block.start);
                                throw new SyntaxError("Invalid token in enum:"+tmp,tmp.pos);
                            }
                            rootContext.declareEnum(((EnumBlock) block),ioContext);
                        }
                        case TUPLE -> {
                            ArrayList<Type.GenericParameter> generics=((TupleBlock) block).context.generics;
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            Type[] types=ProcedureBlock.getSignature(subList, true);
                            subList.clear();
                            if(generics.size()>0){
                                GenericTuple tuple=new GenericTuple(((TupleBlock) block).name,
                                        generics.toArray(Type.GenericParameter[]::new),types,block.startPos);
                                rootContext.declareNamedDeclareable(tuple,ioContext);
                            }else{
                                Type.Tuple tuple=new Type.Tuple(((TupleBlock) block).name,types,block.startPos);
                                rootContext.declareNamedDeclareable(tuple,ioContext);
                            }
                        }
                        case ANONYMOUS_TUPLE,PROC_TYPE,CONST_LIST ->
                            throw new SyntaxError("unexpected end-statement",pos);
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

            case ">>"  -> tokens.add(new OperatorToken(OperatorType.RSHIFT,  pos));
            case "<<"  -> tokens.add(new OperatorToken(OperatorType.LSHIFT,  pos));

            //TODO make non-primitive floating-point operations to native functions
            case "**"    -> tokens.add(new OperatorToken(OperatorType.POW,   pos));

            case ">>:"   -> tokens.add(new OperatorToken(OperatorType.PUSH_FIRST,     pos));
            case ":<<"   -> tokens.add(new OperatorToken(OperatorType.PUSH_LAST,      pos));
            case "+:"    -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_FIRST, pos));
            case ":+"    -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_LAST,  pos));
            case "clear" -> tokens.add(new OperatorToken(OperatorType.CLEAR,          pos));
            //<array> <index> []
            case "[]"    -> tokens.add(new OperatorToken(OperatorType.GET_INDEX,      pos));
            //<array> <off> <to> [:]
            case "[:]"   -> tokens.add(new OperatorToken(OperatorType.GET_SLICE,      pos));

            case "(" ->
                    openBlocks.add(new ListBlock(tokens.size(),BlockType.ANONYMOUS_TUPLE,pos,
                            getContext(openBlocks.peekLast(),rootContext)));
            case ")" -> {
                CodeBlock block=openBlocks.pollLast();
                if(block==null||(block.type!=BlockType.ANONYMOUS_TUPLE&&block.type!=BlockType.PROC_TYPE)){
                    throw new SyntaxError("unexpected ')' statement ",pos);
                }
                if(block.type==BlockType.PROC_TYPE){
                    List<Token> subList=tokens.subList(block.start, ((ProcTypeBlock)block).separatorPos);
                    Type[] inTypes=ProcedureBlock.getSignature(subList,false);
                    subList=tokens.subList(((ProcTypeBlock)block).separatorPos, tokens.size());
                    Type[] outTypes=ProcedureBlock.getSignature(subList,false);
                    subList=tokens.subList(block.start,tokens.size());
                    subList.clear();
                    tokens.add(new ValueToken(Value.ofType(Type.Procedure.create(inTypes,outTypes)),pos,false));
                }else {
                    List<Token> subList=tokens.subList(block.start, tokens.size());
                    Type[] tupleTypes=ProcedureBlock.getSignature(subList,true);
                    subList.clear();
                    tokens.add(new ValueToken(Value.ofType(new Type.Tuple(null,tupleTypes,pos)),pos,false));
                }
            }
            case "{" ->
                    openBlocks.add(new ListBlock(tokens.size(),BlockType.CONST_LIST,pos,
                            getContext(openBlocks.peekLast(),rootContext)));
            case "}" -> {
                CodeBlock block=openBlocks.pollLast();
                if(block==null||block.type!=BlockType.CONST_LIST){
                    throw new SyntaxError("unexpected '}' statement ",pos);
                }
                List<Token> subList = tokens.subList(block.start, tokens.size());
                ArrayList<Value> values=new ArrayList<>(subList.size());
                boolean constant=true;
                Type type=null;
                for(Token t:subList){
                    if(t instanceof ValueToken){
                        type=Type.commonSuperType(type,((ValueToken) t).value.type);
                        values.add(((ValueToken) t).value);
                    }else{
                        values.clear();
                        constant=false;
                        break;
                    }
                }
                if(constant){
                    subList.clear();
                    try {
                        for(int i=0;i< values.size();i++){
                            values.set(i,values.get(i).castTo(type));
                        }
                        tokens.add(new ValueToken(Value.createList(Type.listOf(type),values),block.startPos,true));
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }else{
                    ArrayList<Token> listTokens=new ArrayList<>(subList);
                    subList.clear();
                    tokens.add(new ListCreatorToken(listTokens,pos));
                }
            }
            case "length" -> tokens.add(new OperatorToken(OperatorType.LENGTH,   pos));

            case "()"     -> tokens.add(new Token(TokenType.CALL_PTR, pos));

            case "wrap"   -> tokens.add(new OperatorToken(OperatorType.WRAP,          pos));
            case "unwrap" -> tokens.add(new OperatorToken(OperatorType.UNWRAP,         pos));
            case "??"     -> tokens.add(new OperatorToken(OperatorType.HAS_VALUE,      pos));
            case "empty"  ->  {
                if(tokens.size()<1||!(tokens.get(tokens.size()-1) instanceof ValueToken)){
                    tokens.add(new OperatorToken(OperatorType.EMPTY_OPTIONAL,pos));
                }else{
                    try {
                        Type type= ((ValueToken) tokens.remove(tokens.size()-1)).value.asType();
                        tokens.add(new ValueToken(Value.emptyOptional(type), pos, false));
                    } catch (ConcatRuntimeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }
            }

            case "new"       -> {
                if(tokens.size()<1||!(tokens.get(tokens.size()-1) instanceof ValueToken)){
                    throw new SyntaxError("token before of new has to be a type",pos);
                }
                prev=tokens.remove(tokens.size()-1);
                try {
                    Type t = ((ValueToken)prev).value.asType();
                    if(t instanceof Type.Tuple){
                        int c=((Type.Tuple) t).elementCount();
                        checkElementCount(c);
                        int iMin=tokens.size()-c;
                        if(iMin>=0){
                            Value[] values=new Value[c];
                            for(int i=c-1;i>=0;i--){
                                prev=tokens.get(iMin+i);
                                if(prev instanceof ValueToken){
                                    values[i]=((ValueToken) prev).value.castTo(((Type.Tuple) t).get(i));
                                }else{
                                    break;
                                }
                            }
                            if(c==0||values[0]!=null){//all types resolved successfully
                                tokens.subList(iMin, tokens.size()).clear();
                                tokens.add(new ValueToken(Value.createTuple((Type.Tuple)t,values),pos, false));
                                break;
                            }
                        }
                    }//TODO support list in pre-evaluation
                    tokens.add(new TypedToken(TokenType.NEW,t, pos));
                } catch (ConcatRuntimeError e) {
                    throw new SyntaxError(e.getMessage(), prev.pos);
                }
            }
            case "ensureCap" -> tokens.add(new OperatorToken(OperatorType.ENSURE_CAP, pos));
            case "fill"      -> tokens.add(new OperatorToken(OperatorType.FILL,       pos));

            default -> {
                CodeBlock last= openBlocks.peekLast();
                if(last instanceof EnumBlock){
                    ((EnumBlock) last).add(str,pos);
                }else{
                    tokens.add(new IdentifierToken(IdentifierType.UNMODIFIED, str, pos));
                }
            }
        }
    }

    static class Variable{
        final Type type;
        private Value value;

        Variable(Type type, Value value) throws ConcatRuntimeError {
            this.type = type;
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

    @SuppressWarnings("unused")
    public RandomAccessStack<Type> typeCheckProgram(Program program, IOContext context) throws SyntaxError {
        RandomAccessStack<Type> stack=new RandomAccessStack<>(16);
        Declareable main=program.rootContext.elements.get("main");
        if(main==null){
            recursiveTypeCheck(stack,program,context);
        }else{
            if(program.tokens.size()>0){
                context.stdErr.println("programs with main procedure cannot contain code at top level "+
                        program.tokens.get(0).pos);
            }
            if(main.declarableType()!=DeclareableType.PROCEDURE){
                context.stdErr.println("main is not a procedure but "+
                        declarableName(main.declarableType(),true)+" declared at "+main.declaredAt());
            }else{
                Type.Procedure type=(Type.Procedure) ((Value.Procedure)main).type;
                if(type.outTypes.length>0){
                    context.stdErr.println("illegal signature for main "+type+", main procedure cannot return values at "
                            +main.declaredAt());
                }else if(type.inTypes.length>0){
                    if(type.inTypes.length>1){
                        context.stdErr.println("illegal signature for main "+type+
                                ", main procedure can accept at most one argument"+main.declaredAt());
                    }else if(type.inTypes[0].isList()&&type.inTypes[0].content().equals(Type.RAW_STRING())){//string list
                        try {
                            stack.push(Type.listOf(Type.RAW_STRING()));
                        } catch (ConcatRuntimeError e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                recursiveTypeCheck(stack,(Value.Procedure)main,context);
            }
        }
        return stack;
    }

    String opName(OperatorType type){
        switch (type){
            case REF_ID -> { return "refId"; }
            case CLONE -> {  return "clone";}
            case DEEP_CLONE -> {return "clone!";}
            case NEGATE -> {return "-_"; }
            case PLUS -> { return "+";}
            case MINUS -> { return "-";}
            case INVERT -> {return "/_";}
            case MULTIPLY -> {return "*"; }
            case DIV -> { return "/"; }
            case MOD -> {return "%"; }
            case POW -> { return "**"; }
            case NOT -> { return "!"; }
            case FLIP -> { return "~"; }
            case AND -> { return "&"; }
            case OR -> { return "|"; }
            case XOR -> { return "xor";}
            case LSHIFT -> { return "<<"; }
            case RSHIFT -> { return ">>";}
            case GT -> { return ">"; }
            case GE -> { return ">="; }
            case EQ -> { return "==";}
            case NE -> { return "!=";}
            case LE -> { return "<=";}
            case LT -> { return "<";}
            case REF_EQ -> { return "===";}
            case REF_NE -> { return "!=";}
            case TYPE_OF -> { return "typeOf";}
            case LIST_OF -> { return "list";}
            case CONTENT -> { return "content";}
            case LENGTH -> { return "length";}
            case ENSURE_CAP -> { return "ensureCap";}
            case FILL -> { return "fill";}
            case GET_INDEX -> { return "[]";}
            case SET_INDEX -> { return "[] =";}
            case GET_SLICE -> { return "[:]";}
            case SET_SLICE -> { return "[:] =";}
            case PUSH_FIRST -> { return ">>:";}
            case PUSH_ALL_FIRST -> { return "+:";}
            case PUSH_LAST -> { return ":<<";}
            case PUSH_ALL_LAST -> { return ":+";}
            case IS_ENUM -> { return "isEnum";}
            case OPTIONAL_OF -> { return "optional";}
            case WRAP -> { return "wrap"; }
            case UNWRAP -> { return "unwrap";}
            case HAS_VALUE -> { return "??";}
            case EMPTY_OPTIONAL -> { return "empty"; }
            case CLEAR -> { return "clear";}
        }
        throw new RuntimeException("unreachable");
    }

    private void typeCheckOperator(OperatorToken op, RandomAccessStack<Type> stack,IOContext ioContext)
            throws RandomAccessStack.StackUnderflow, SyntaxError {
        switch (op.opType) {
            case REF_ID ->{
                    stack.pop();
                    stack.push(Type.UINT);
            }
            case CLONE,DEEP_CLONE -> {}
            case NEGATE -> {
                Type t = stack.peek();
                if(t!=Type.INT&&t!=Type.FLOAT){
                    if(t==Type.UINT){
                        ioContext.stdErr.println("Waring: negation of unsigned int (at "+op.pos+")");
                    }else{
                        throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                    }
                }
            }
            case INVERT -> {
                Type t = stack.peek();
                if(t!=Type.FLOAT){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
            }
            case NOT -> {
                Type t = stack.peek();
                if(t!=Type.BOOL){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
            }
            case FLIP -> {
                Type t = stack.peek();
                if(t!=Type.INT&&t!=Type.UINT){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
            }
            case PLUS,MINUS,MULTIPLY,DIV,MOD,POW -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if(a ==Type.UINT&& b ==Type.UINT){
                    stack.push(Type.UINT);
                }else if(a ==Type.INT|| a ==Type.UINT || a ==Type.BYTE){//addLater? isInt/isUInt functions
                    if(b ==Type.INT|| b ==Type.UINT || b ==Type.BYTE){
                        stack.push(Type.INT);
                    }else if(b ==Type.FLOAT){
                        stack.push(Type.FLOAT);
                    }else{
                        throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+ a +" and "+ b, op.pos);
                    }
                }else if(a ==Type.FLOAT){
                    if(b ==Type.FLOAT|| b ==Type.INT|| b ==Type.UINT || b ==Type.BYTE){
                        stack.push(Type.FLOAT);
                    }else{
                        throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+ a +" and "+ b, op.pos);
                    }
                }else{
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+ a +" and "+ b, op.pos);
                }
            }
            case EQ,NE,REF_EQ,REF_NE ->{
                Type b = stack.pop();
                Type a = stack.pop();
                if(!(a.isSubtype(b)||b.isSubtype(a))){
                    ioContext.stdErr.println("Warning: equality check for incompatible types:"+a+" and "+b+" (at "+op.pos+")");
                }
                stack.push(Type.BOOL);
            }
            case GT, GE, LE, LT -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if((a.equals(Type.RAW_STRING())||a.equals(Type.UNICODE_STRING()))&&
                        (b.equals(Type.RAW_STRING())||b.equals(Type.UNICODE_STRING()))){
                    stack.push(Type.BOOL);
                }else if(a==Type.BYTE&&b==Type.BYTE){
                    stack.push(Type.BOOL);
                }else if(a==Type.CODEPOINT&&b==Type.CODEPOINT){
                    stack.push(Type.BOOL);
                }else if((a==Type.INT||a==Type.UINT||a==Type.FLOAT)&&(b==Type.INT||b==Type.UINT||b==Type.FLOAT)){
                    stack.push(Type.BOOL);
                }else if(a==Type.TYPE&&b==Type.TYPE&&(op.opType==OperatorType.LE||op.opType==OperatorType.GE)){
                    stack.push(Type.BOOL);
                }else{
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+ a +" and "+ b, op.pos);
                }
            }
            case AND,OR,XOR -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if(a==Type.BOOL&&b==Type.BOOL){
                    stack.push(Type.BOOL);
                }else if(a==Type.UINT&&b==Type.UINT){
                    stack.push(Type.UINT);
                }else if((a==Type.INT||a==Type.UINT||a==Type.BYTE)&&(b==Type.INT||b==Type.UINT||b==Type.BYTE)){
                    stack.push(Type.INT);
                }else{
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+ a +" and "+ b, op.pos);
                }
            }
            case LSHIFT,RSHIFT -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if((a==Type.UINT||a==Type.INT)&&(b==Type.UINT||b==Type.INT)){
                    stack.push(a);
                }else{
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+ a +" and "+ b, op.pos);
                }
            }
            case LIST_OF, CONTENT, OPTIONAL_OF -> {
                Type t = stack.peek();
                if(t!=Type.TYPE){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
            }
            case TYPE_OF -> {
                stack.pop();
                stack.push(Type.TYPE);
            }
            case LENGTH -> {
                Type t = stack.pop();//TODO distinguish between (tuple/enum (index-able) and other types)
                if(!(t.isSubtype(Type.UNTYPED_LIST)||t instanceof Type.Tuple||t==Type.TYPE)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
                stack.push(Type.UINT);
            }
            case EMPTY_OPTIONAL ->
                    //TODO find solution for dynamically typed values (?generics)
                throw new UnsupportedOperationException("dynamic-empty-optional unimplemented");
            case CLEAR ->{
                Type t = stack.pop();
                if(!t.isSubtype(Type.UNTYPED_LIST)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
            }
            case ENSURE_CAP -> {
                Type t = stack.peek();
                if(!t.isSubtype(Type.UNTYPED_LIST)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+t,op.pos);
                }
            }
            case FILL -> {
                Type val   = stack.pop();
                Type count = stack.pop();
                Type off   = stack.pop();
                Type list  = stack.pop();
                if((count!=Type.INT&&count!=Type.UINT)||(off!=Type.INT&&off!=Type.UINT)||
                        !list.isSubtype(Type.UNTYPED_LIST)||!val.isSubtype(list.content())){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+list+" "+off+" "+count+" "+val,op.pos);
                }
            }
            case GET_INDEX -> {
                Type index = stack.pop();
                Type list = stack.pop();
                if((index!=Type.INT&&index!=Type.UINT)||
                        (!(list.isSubtype(Type.UNTYPED_LIST)||list instanceof Type.Tuple||list==Type.TYPE))){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+list+" "+index,op.pos);
                }
                stack.push(list.content());
            }
            case SET_INDEX -> {
                Type index = stack.pop();
                Type val = stack.pop();
                Type list = stack.pop();
                if((index!=Type.INT&&index!=Type.UINT)||
                        (!(list.isSubtype(Type.UNTYPED_LIST)||list instanceof Type.Tuple||list==Type.TYPE))||
                        !val.isSubtype(list.content())){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+list+" "+val+" "+index,op.pos);
                }
            }
            case GET_SLICE -> {
                Type to = stack.pop();
                Type off   = stack.pop();
                Type list  = stack.pop();
                if((off!=Type.INT&&off!=Type.UINT)||(to!=Type.INT&&to!=Type.UINT)||
                        !list.isSubtype(Type.UNTYPED_LIST)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+list+" "+off+" "+to,op.pos);
                }
                stack.push(list);
            }
            case SET_SLICE -> {
                Type to = stack.pop();
                Type off   = stack.pop();
                Type val  = stack.pop();
                Type list  = stack.pop();
                if((off!=Type.INT&&off!=Type.UINT)||(to!=Type.INT&&to!=Type.UINT)||
                        !list.isSubtype(Type.UNTYPED_LIST)||!val.isSubtype(list)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+list+" "+val+" "+off+" "+to,op.pos);
                }
            }
            case PUSH_FIRST -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if(!b.isSubtype(Type.UNTYPED_LIST)||!a.isSubtype(b.content())){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+a+" "+b,op.pos);
                }
                stack.push(b);
            }
            case PUSH_LAST -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if(!a.isSubtype(Type.UNTYPED_LIST)||!b.isSubtype(a.content())){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+a+" "+b,op.pos);
                }
                stack.push(a);
            }
            case PUSH_ALL_FIRST -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if(!b.isSubtype(Type.UNTYPED_LIST)||!a.isSubtype(b)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+a+" "+b,op.pos);
                }
                stack.push(b);
            }
            case PUSH_ALL_LAST -> {
                Type b = stack.pop();
                Type a = stack.pop();
                if(!a.isSubtype(Type.UNTYPED_LIST)||!b.isSubtype(a)){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+a+" "+b,op.pos);
                }
                stack.push(a);
            }
            case IS_ENUM -> {
                Type type = stack.pop();
                if(type!=Type.TYPE){
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+type,op.pos);
                }
                stack.push(Type.BOOL);
            }
            case WRAP -> {
                Type value= stack.pop();
                try {
                    stack.push(Type.optionalOf(value));
                } catch (ConcatRuntimeError e) {
                    throw new SyntaxError(e,op.pos);
                }
            }
            case UNWRAP -> {
                Type value= stack.pop();
                if(value.isOptional()){
                    stack.push(value.content());
                }else{
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+value,op.pos);
                }
            }
            case HAS_VALUE -> {
                Type value= stack.peek();
                if(value.isOptional()){
                    stack.push(Type.BOOL);
                }else{
                    throw new SyntaxError("Cannot apply '"+opName(op.opType)+"' to "+value,op.pos);
                }
            }
        }
    }

    private void recursiveTypeCheck(RandomAccessStack<Type> stack,CodeSection program,IOContext ioContext) throws SyntaxError {
        int ip=0;
        ArrayList<Token> tokens=program.tokens();
        while(ip<tokens.size()){
            Token next=tokens.get(ip);
            try {
                switch (next.tokenType) {
                    case VALUE -> {
                        ValueToken value = (ValueToken) next;
                        if(value.value.type==Type.TYPE){
                            Type type = value.value.asType();
                            if(type instanceof Type.GenericParameter){
                                throw new UnsupportedOperationException("type-checking generics is currently unimplemented");
                            }
                        }
                        stack.push(value.value.type);
                    }
                    case CURRIED_PROCEDURE -> {
                        if(program instanceof Program){
                            throw new RuntimeException("curried procedures should only exist inside of procedures");
                        }
                        throw new UnsupportedOperationException("unimplemented");
                    }
                    case OPERATOR ->
                            typeCheckOperator((OperatorToken) next, stack,ioContext);
                    case DEBUG_PRINT -> stack.pop();
                    case CAST -> {
                        Type target=((TypedToken)next).target;
                        if(target==null){
                            throw new UnsupportedOperationException("unimplemented");
                        }
                        stack.pop();
                        //TODO check if cast-able
                        stack.push(target);
                    }
                    case NEW ->
                            //TODO new
                        throw new UnsupportedOperationException("new unimplemented");

                    case NEW_LIST ->
                            //TODO newList
                        throw new UnsupportedOperationException("new-list unimplemented");
                    case DROP ->
                            stack.dropAll(((StackModifierToken)next).off,((StackModifierToken)next).count);
                    case DUP ->
                            stack.dupAll(((StackModifierToken)next).off,((StackModifierToken)next).count);
                    case VARIABLE -> {
                        VariableToken asVar=(VariableToken) next;
                        switch (asVar.accessType){
                            case READ ->
                                stack.push(asVar.id.type);
                            case WRITE,CONST_DECLARE,DECLARE -> {
                                Type newValue=stack.pop();
                                if(!newValue.isSubtype(asVar.id.type)){
                                    throw new SyntaxError("cannot assign "+newValue+" to variable of type "+
                                            asVar.id.type,next.pos);
                                }
                            }
                        }
                    }
                    case ASSERT -> {
                        Type t=stack.pop();
                        if(t!=Type.BOOL){
                            throw new SyntaxError("parameter of assertion has to be a bool ",next.pos);
                        }
                    }
                    case IDENTIFIER, PLACEHOLDER ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case CONTEXT_OPEN,CONTEXT_CLOSE -> {

                    }
                    case PUSH_PROC_PTR -> {
                        ProcedureToken token=(ProcedureToken) next;
                        stack.push(token.getProcedure().type);
                    }
                    case PUSH_NATIVE_PROC_PTR -> {
                        NativeProcedureToken token=(NativeProcedureToken) next;
                        stack.push(token.value.type);
                    }
                    case CALL_NATIVE_PROC ,CALL_PROC, CALL_PTR -> {
                        Type called;
                        if(next.tokenType==TokenType.CALL_PROC){
                            called=((ProcedureToken) next).getProcedure().type;
                        }else if(next.tokenType==TokenType.CALL_NATIVE_PROC){
                            called=((NativeProcedureToken) next).value.type;
                        }else{
                            called = stack.pop();
                            //TODO!! handle lambda-signatures
                        }
                        if(called instanceof Type.Procedure){
                            //TODO procedure
                            throw new UnsupportedOperationException("unimplemented");
                        }else{
                            throw new ConcatRuntimeError("cannot call objects of type "+called);
                        }
                    }
                    case RETURN,EXIT ->
                        //TODO return
                        throw new UnsupportedOperationException("return unimplemented");

                    case JMP ->
                        //TODO jump
                        throw new UnsupportedOperationException("jump unimplemented");

                    case JEQ,JNE ->
                        //TODO cond-jump
                        throw new UnsupportedOperationException("conditional jump unimplemented");

                    case SWITCH ->
                        //TODO switch
                        throw new UnsupportedOperationException("switch unimplemented");

                }
            }catch(ConcatRuntimeError|RandomAccessStack.StackUnderflow  e){
                throw new SyntaxError(e.getMessage(),next.pos);
            }
            ip++;
        }
    }

    public RandomAccessStack<Value> run(Program program, String[] arguments,IOContext context){
        RandomAccessStack<Value> stack=new RandomAccessStack<>(16);
        Declareable main=program.rootContext.elements.get("main");
        if(main==null){
            recursiveRun(stack,program,null,null,null,context);
        }else{
            if(program.tokens.size()>0){
                context.stdErr.println("programs with main procedure cannot contain code at top level "+
                        program.tokens.get(0).pos);
            }
            if(main.declarableType()!=DeclareableType.PROCEDURE){
                context.stdErr.println("main is not a procedure but "+
                        declarableName(main.declarableType(),true)+" declared at "+main.declaredAt());
            }else{
                Type.Procedure type=(Type.Procedure) ((Value.Procedure)main).type;
                if(type.outTypes.length>0){
                    context.stdErr.println("illegal signature for main "+type+", main procedure cannot return values at "
                            +main.declaredAt());
                }else if(type.inTypes.length>0){
                    if(type.inTypes.length>1){
                        context.stdErr.println("illegal signature for main "+type+
                                ", main procedure can accept at most one argument"+main.declaredAt());
                    }else if(type.inTypes[0].isList()&&type.inTypes[0].content().equals(Type.RAW_STRING())){//string list
                        ArrayList<Value> args=new ArrayList<>(arguments.length);
                        for(String s:arguments){
                            args.add(Value.ofString(s,false));
                        }
                        try {
                            stack.push(Value.createList(Type.listOf(Type.RAW_STRING()),args));
                        } catch (ConcatRuntimeError e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                recursiveRun(stack,(Value.Procedure)main,new ArrayList<>(),null,null,context);
            }
        }
        return stack;
    }

    private void executeOperator(OperatorToken op, RandomAccessStack<Value> stack)
            throws RandomAccessStack.StackUnderflow, ConcatRuntimeError {
        switch (op.opType) {
            case REF_ID ->
                    stack.push(Value.ofInt(stack.pop().id(),true));
            case CLONE -> {
                Value t = stack.pop();
                stack.push(t.clone(false));
            }
            case DEEP_CLONE -> {
                Value t = stack.pop();
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
                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x + y,false),
                        (x, y) -> Value.ofInt(x + y,true),
                        (x, y) -> Value.ofFloat(x + y)));
            }
            case MINUS -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x - y,false),
                        (x, y) -> Value.ofInt(x - y,true),
                        (x, y) -> Value.ofFloat(x - y)));
            }
            case MULTIPLY -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.mathOp(a, b, (x, y) -> Value.ofInt(x * y,false),
                        (x, y) -> Value.ofInt(x * y,true),
                        (x, y) -> Value.ofFloat(x * y)));
            }
            case DIV -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.mathOp(a, b,
                        (x, y) -> Value.ofInt(x / y,false),
                        (x, y) -> Value.ofInt(Long.divideUnsigned(x,y),true),
                        (x, y) -> Value.ofFloat(x / y)));
            }
            case MOD -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.mathOp(a, b,
                        (x, y) -> Value.ofInt(x % y,false),
                        (x, y) -> Value.ofInt(Long.remainderUnsigned(x,y),true),
                        (x, y) -> Value.ofFloat(x % y)));
            }
            case POW -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.mathOp(a, b,
                        (x, y) -> Value.ofInt(longPow(x, y),false),
                        (x, y) -> Value.ofInt(longPow(x, y),true),
                        (x, y) -> Value.ofFloat(Math.pow(x, y))));
            }
            case EQ, NE, GT, GE, LE, LT,REF_EQ,REF_NE -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.compare(a, op.opType, b));
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
            case LSHIFT -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.shift(a, b,true));
            }
            case RSHIFT -> {
                Value b = stack.pop();
                Value a = stack.pop();
                stack.push(Value.shift(a, b,false));
            }
            case LIST_OF -> {
                Type contentType = stack.pop().asType();
                stack.push(Value.ofType(Type.listOf(contentType)));
            }
            case CONTENT -> {
                Type wrappedType = stack.pop().asType();
                stack.push(Value.ofType(wrappedType.content()));
            }
            case TYPE_OF -> {
                Value val = stack.pop();
                stack.push(Value.ofType(val.type));
            }
            case LENGTH -> {
                Value val = stack.pop();
                stack.push(Value.ofInt(val.length(),true));
            }
            case CLEAR ->
                    stack.pop().clear();
            case ENSURE_CAP -> {
                long newCap= stack.pop().asLong();
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
            case IS_ENUM -> {
                Type type = stack.pop().asType();
                stack.push(type instanceof Type.Enum?Value.TRUE:Value.FALSE);
            }
            case OPTIONAL_OF -> {
                Type contentType = stack.pop().asType();
                stack.push(Value.ofType(Type.optionalOf(contentType)));
            }
            case EMPTY_OPTIONAL -> {
                Type t= stack.pop().asType();
                stack.push(Value.emptyOptional(t));
            }
            case WRAP -> {
                Value value= stack.pop();
                stack.push(Value.wrap(value));
            }
            case UNWRAP -> {
                Value value= stack.pop();
                stack.push(value.unwrap());
            }
            case HAS_VALUE -> {
                Value value= stack.peek();
                stack.push(value.hasValue()?Value.TRUE:Value.FALSE);
            }
        }
    }

    private ExitType recursiveRun(RandomAccessStack<Value> stack, CodeSection program,
                                  ArrayList<Variable[]> globalVariables,ArrayList<Variable[]> variables,
                                  Value[] curried, IOContext context){
        if(variables==null){
            variables=new ArrayList<>();
            variables.add(new Variable[program.context().elements.size()]);
        }
        int ip=0;
        ArrayList<Token> tokens=program.tokens();
        while(ip<tokens.size()){
            Token next=tokens.get(ip);
            boolean incIp=true;
            try {
                switch (next.tokenType) {
                    case VALUE -> {
                        ValueToken value = (ValueToken) next;
                        if(value.value.type==Type.TYPE&&value.value.type instanceof Type.GenericParameter){
                            //TODO resolve generic types in procedure signatures
                            throw new ConcatRuntimeError("unresolved generic type");
                        }else if(value.cloneOnCreate){
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
                            }else if(id.context.procedureContext()!=null){
                                curried2[i]=variables.get(id.level)[id.id].value;
                            }else{
                                throw new RuntimeException("global variables should only be curried");
                            }
                        }
                        stack.push(proc.withCurried(curried2));
                    }
                    case OPERATOR ->
                        executeOperator((OperatorToken) next, stack);
                    case NEW_LIST -> {//{ e1 e2 ... eN }
                        RandomAccessStack<Value> listStack=new RandomAccessStack<>(((ListCreatorToken)next).tokens.size());
                        ExitType res=recursiveRun(listStack,((ListCreatorToken)next),globalVariables,variables,curried,context);
                        if(res!=ExitType.NORMAL){
                            if(res==ExitType.ERROR) {
                                context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                            }
                            return res;
                        }
                        ArrayList<Value> values=new ArrayList<>(listStack.asList());
                        Type type=values.stream().map(v->v.type).reduce(null, Type::commonSuperType);
                        for(int i=0;i< values.size();i++){
                            values.set(i,values.get(i).castTo(type));
                        }
                        stack.push(Value.createList(Type.listOf(type), values));
                    }
                    case CAST -> {
                        Type type=((TypedToken)next).target;
                        if(type==null){//dynamic operation
                            type=stack.pop().asType();
                        }
                        Value val = stack.pop();
                        stack.push(val.castTo(type));
                    }
                    case NEW -> {
                        Type type=((TypedToken)next).target;
                        if(type==null){//dynamic operation
                            type=stack.pop().asType();
                        }
                        if(type instanceof Type.Tuple){
                            int count=((Type.Tuple)type).elementCount();
                            Value[] values=new Value[count];
                            for(int i=1;i<= values.length;i++){
                                values[count-i]= stack.pop().castTo(((Type.Tuple) type).get(count-i));
                            }
                            stack.push(Value.createTuple((Type.Tuple)type,values));
                        }else if(type.isList()){
                            long initCap= stack.pop().asLong();
                            stack.push(Value.createList(type,initCap));
                        }else{
                            throw new ConcatRuntimeError("new only supports tuples and lists");
                        }
                    }
                    case DEBUG_PRINT -> context.stdOut.println(stack.pop().stringValue());
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
                                            throw new ConcatRuntimeError("cannot modify curried variables");
                                }
                            }
                            case CONST_DECLARE,DECLARE -> {
                                Type  type=stack.pop().asType();
                                Value initValue=stack.pop();
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            (globalVariables==null?variables:globalVariables).get(asVar.id.level)
                                                    [asVar.id.id] = new Variable(type, initValue);
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables.get(asVar.id.level)[asVar.id.id]
                                                    = new Variable(type, initValue);
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            throw new ConcatRuntimeError("cannot declare curried variables");
                                }
                            }
                        }
                    }
                    case ASSERT -> {
                        if(!stack.pop().asBool()){
                            throw new ConcatRuntimeError("assertion failed: "+((AssertToken)next).message);
                        }
                    }
                    case IDENTIFIER, PLACEHOLDER ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case CONTEXT_OPEN ->
                            variables.add(new Variable[((ContextOpen)next).context.elements.size()]);
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
                    case PUSH_NATIVE_PROC_PTR -> {
                        NativeProcedureToken token=(NativeProcedureToken) next;
                        stack.push(token.value);
                    }
                    case CALL_NATIVE_PROC ,CALL_PROC, CALL_PTR -> {
                        Value called;
                        if(next.tokenType==TokenType.CALL_PROC){
                            called=((ProcedureToken) next).getProcedure();
                        }else if(next.tokenType==TokenType.CALL_NATIVE_PROC){
                            called=((NativeProcedureToken) next).value;
                        }else{
                            called = stack.pop();
                        }
                        if(called instanceof Value.NativeProcedure nativeProc){
                            int count=nativeProc.argCount();
                            Value[] args=new Value[count];
                            for(int i=count-1;i>=0;i--){
                                args[i]=stack.pop();
                            }
                            args=nativeProc.callWith(args);
                            for (Value arg : args) {
                                stack.push(arg);
                            }
                        }else if(called instanceof Value.Procedure procedure){
                            assert ((Value.Procedure) called).context.curried.isEmpty();
                            ExitType e=recursiveRun(stack,procedure,globalVariables==null?variables:globalVariables,
                                    null,null,context);
                            if(e!=ExitType.NORMAL){
                                if(e==ExitType.ERROR) {
                                    context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                                }
                                return e;
                            }
                        }else if(called instanceof Value.CurriedProcedure procedure){
                            ExitType e=recursiveRun(stack,procedure,globalVariables==null?variables:globalVariables,
                                    null,procedure.curried,context);
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

    static void compileAndRun(String path,String[] arguments,IOContext context) throws IOException {
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
        PrintStream outTmp = System.out;
        System.setOut(context.stdOut);
        PrintStream errTmp = System.err;
        System.setErr(context.stdErr);
        InputStream inTmp  = System.in;
        System.setIn(context.stdIn);
        RandomAccessStack<Value> stack = ip.run(program, arguments, context);
        System.setIn(inTmp);
        System.setOut(outTmp);
        System.setErr(errTmp);
        context.stdOut.println("\nStack:");
        context.stdOut.println(stack);
    }

    public static void main(String[] args) throws IOException {
        if(args.length==0){
            System.out.println("usage: <pathToFile> (-lib <libPath>)");
            return;
        }
        String path=args[0];
        int consumed=1;
        if(args.length>1&&args[1].equals("-lib")){
            consumed+=2;
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
        String[] arguments=new String[args.length-consumed+1];
        arguments[0]=System.getProperty("user.dir");
        System.arraycopy(args,1,arguments,consumed,args.length-consumed);
        compileAndRun(path,arguments,defaultContext);
    }

}
