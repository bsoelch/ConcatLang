package bsoelch.concat;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Interpreter {
    public static final String DEFAULT_FILE_EXTENSION = ".concat";
    //use ' as separator for namespaces, as it cannot be part of identifiers
    public static final String MODULE_SEPARATOR = "'";

    /**
     * Context for running the program
     */
    record IOContext(InputStream stdIn, PrintStream stdOut, PrintStream stdErr) {}
    static final IOContext defaultContext=new IOContext(System.in,System.out,System.err);

    enum TokenType {
        VALUE, LAMBDA, CURRIED_LAMBDA,OPERATOR,CAST,NEW,NEW_LIST,
        DROP,DUP,
        IDENTIFIER,//addLater option to free values/variables
        VARIABLE,
        CONTEXT_OPEN,CONTEXT_CLOSE,
        CALL_PROC, PUSH_PROC_PTR,CALL_PTR,
        CALL_NATIVE_PROC,PUSH_NATIVE_PROC_PTR,
        RETURN,
        DEBUG_PRINT,ASSERT,//debug time operations, may be replaced with drop in compiled code
        BLOCK_TOKEN,//jump commands only for internal representation
        SWITCH,
        EXIT
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
        DECLARE,CONST_DECLARE, WORD,PROC_ID,VAR_WRITE,NATIVE, NEW_GENERIC, NATIVE_DECLARE
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
    enum BlockTokenType{
        IF, ELSE, _IF,END_IF, WHILE,DO, END_WHILE, DO_WHILE,SWITCH,CASE,END_CASE,DEFAULT,END,LIST,END_LIST
    }
    static class BlockToken extends Token{
        final BlockTokenType blockType;
        int delta;
        BlockToken(BlockTokenType blockType, FilePosition pos, int delta) {
            super(TokenType.BLOCK_TOKEN, pos);
            this.blockType=blockType;
            this.delta = delta;
        }
        @Override
        public String toString() {
            return blockType.toString()+": "+(delta>0?"+":"")+delta;
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
        final Value.Procedure procedure;
        ProcedureToken(boolean isPtr, Value.Procedure procedure, FilePosition pos) {
            super(isPtr?TokenType.PUSH_PROC_PTR :TokenType.CALL_PROC, pos);
            this.procedure = procedure;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+ procedure;
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
            context=new BlockContext(parentContext);
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
            context=new BlockContext(parentContext);
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
            currentPos=new FilePosition(path, line, posInLine);
            buffer.setLength(0);
        }
        void updateNextPos() {
            currentPos=new FilePosition(path, line, posInLine);
        }
    }

    enum DeclareableType{
        VARIABLE,CONSTANT,CURRIED_VARIABLE,MACRO,PROCEDURE,ENUM, TUPLE, ENUM_ENTRY, GENERIC, NATIVE_PROC,
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

    record ModuleBlock(String[] path,ArrayList<String> imports,FilePosition declaredAt){
        @Override
        public String toString() {
            return Arrays.toString(path) +" declaredAt " + declaredAt;
        }
    }
    /**File specific part of namespace parsing*/
    private static class FileContext{
        final ArrayList<String> globalImports=new ArrayList<>();
        final ArrayList<ModuleBlock> openModules=new ArrayList<>();
        final HashMap<String,Declareable> declared =new HashMap<>();
    }
    private static class RootContext extends VariableContext{
        RootContext(){}
        HashSet<String> namespaces=new HashSet<>();
        ArrayDeque<FileContext> openFiles=new ArrayDeque<>();

        private FileContext file(){
            return openFiles.peekLast();
        }
        void startFile(){
            openFiles.addLast(new FileContext());
        }
        void startModule(String namespaceName,FilePosition declaredAt) throws SyntaxError {
            Declareable d=elements.get(inCurrentModule(namespaceName));
            if(d!=null){
                throw new SyntaxError("cannot declare namespace "+namespaceName+
                        ", the identifier is already used by " + declarableName(d.declarableType(), true)
                        + " (declared at " + d.declaredAt() + ")",declaredAt);
            }
            StringBuilder fullPath=new StringBuilder();
            for(ModuleBlock m:file().openModules){
                for(String s:m.path){
                    fullPath.append(s).append(MODULE_SEPARATOR);
                }
            }
            String[] path = namespaceName.split(MODULE_SEPARATOR);
            for(String s:path){
                fullPath.append(s);
                namespaces.add(fullPath.toString());
                fullPath.append(MODULE_SEPARATOR);
            }
            file().openModules.add(new ModuleBlock(path,new ArrayList<>(),declaredAt));
        }
        void addImport(String path,FilePosition pos) throws SyntaxError {
            if(namespaces.contains(path)){
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
                //addLater formatted printing of namespace paths
                throw new SyntaxError("namespace "+path+" does not exist",pos);
            }
        }
        void endModule(FilePosition pos) throws SyntaxError {
            if(file().openModules.isEmpty()){
                throw new SyntaxError("Unexpected End of namespace",pos);
            }
            file().openModules.remove(file().openModules.size() - 1);
        }
        void endFile(IOContext context){
            FileContext ctx=openFiles.removeLast();
            if(ctx.openModules.size()>0) {
                context.stdErr.println("unclosed namespaces at end of File:");
                while(ctx.openModules.size()>0){
                    ModuleBlock removed=ctx.openModules.remove(ctx.openModules.size()-1);
                    context.stdErr.println(" - "+removed);
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

        Declareable getDeclareable(String name){
            Declareable d;
            ArrayDeque<String> paths = currentPaths();
            while(paths.size()>0){//go through all namespaces
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

        void declareProcedure(String name, Value.Procedure proc,IOContext ioContext) throws SyntaxError {
            String name0=name;
            name=inCurrentModule(name);
            ensureDeclareable(name,DeclareableType.PROCEDURE,proc.declaredAt);
            checkShadowed(proc,name0,proc.declaredAt,ioContext);
            elements.put(name,proc);
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

    //addLater allow declaring generics only in in-signature
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
    record Program(ArrayList<Token> tokens, HashSet<String> files,RootContext rootContext) implements CodeSection{
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

    /**@return true if the value was an integer otherwise true*/
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
                return true;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,2,unsigned), unsigned), pos, false));
                return true;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,16,unsigned), unsigned), pos, false));
                return true;
            }
        } catch (ConcatRuntimeError nfeL) {
            throw new SyntaxError("Number out of Range: "+str0,pos);
        }
        return false;
    }

    static class ParserState {
        final ArrayList<Token> tokens =new ArrayList<>();
        /**global code after type-check*/
        final ArrayList<Token> globalCode =new ArrayList<>();

        final ArrayDeque<CodeBlock> openBlocks=new ArrayDeque<>();
        int openBlocks2=0;//number of opened blocks that will be processed in the next step
        final RootContext rootContext;
        final HashSet<String> files=new HashSet<>();
        public HashMap<VariableId, Value> globalVariables=new HashMap<>();
        //proc-contexts that are currently open
        final ArrayDeque<ProcedureContext> openedProcs =new ArrayDeque<>();
        public ArrayDeque<Value.Procedure> unparsedProcs=new ArrayDeque<>();

        Macro currentMacro;//may be null

        ParserState(RootContext rootContext) {
            this.rootContext = rootContext;
        }

        VariableContext getContext(){
            ProcedureContext currentProc = openedProcs.peekLast();
            return currentProc==null?rootContext:currentProc;
        }
    }
    public Program parse(File file, ParserState pState, IOContext ioContext) throws IOException, SyntaxError {
        ParserReader reader=new ParserReader(file.getAbsolutePath());
        int c;
        String fileId=null;
        while((c=reader.nextChar())>=0){
            if(Character.isWhitespace(c)){
                if(fileId==null){
                    fileId=reader.buffer.toString();
                    reader.nextToken();
                }else if(reader.buffer.toString().equals(":")){
                    break;
                }else{
                    throw new SyntaxError("invalid start of file, all concat files have to start with \"<file-id> :\"",
                            reader.currentPos());
                }
            }else{
                reader.buffer.append((char) c);
            }
        }
        reader.nextToken();
        if(fileId==null){
            throw new SyntaxError("invalid start of file, all concat files have to start with \"<file-id> :\"",
                    reader.currentPos());
        }
        if(pState==null){
            pState=new ParserState(new RootContext());
        }else if(pState.files.contains(fileId)){
            //TODO detect if file was already included through different path
            return new Program(pState.globalCode,pState.files,pState.rootContext);
        }else{//ensure that each file is included only once
            pState.files.add(fileId);
        }
        pState.rootContext.startFile();
        reader.nextToken();
        WordState state=WordState.ROOT;
        while((c=reader.nextChar())>=0){
            switch(state){
                case ROOT:
                    if(Character.isWhitespace(c)){
                        if(reader.buffer.length()>0){
                            finishWord(reader.buffer.toString(), pState,reader.currentPos(),ioContext);
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
                                    finishWord(reader.buffer.toString(), pState,reader.currentPos(),ioContext);
                                    reader.nextToken();
                                } else if (c == '+') {
                                    state = WordState.COMMENT;
                                    finishWord(reader.buffer.toString(), pState,reader.currentPos(),ioContext);
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
                        finishWord(reader.buffer.toString(),pState,reader.currentPos(),ioContext);
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
                    if(c=='+'){//addLater? comments starting with #++ can only be closed with ++#
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
                finishWord(reader.buffer.toString(), pState,reader.currentPos(),ioContext);
                reader.nextToken();
            }
            case LINE_COMMENT ->{} //do nothing
            case STRING,UNICODE_STRING ->throw new SyntaxError("unfinished string", reader.currentPos());
            case COMMENT -> throw new SyntaxError("unfinished comment", reader.currentPos());
        }
        finishParsing(pState, ioContext,true);

        if(pState.openBlocks.size()>0){
            throw new SyntaxError("unclosed block: "+pState.openBlocks.getLast(),pState.openBlocks.getLast().startPos);
        }
        pState.rootContext.endFile(ioContext);
        return new Program(pState.globalCode,pState.files,pState.rootContext);
    }

    private void finishWord(String str, ParserState pState, FilePosition pos, IOContext ioContext) throws SyntaxError {
        if (str.length() > 0) {
            ArrayList<Token> tokens = pState.tokens;
            Token prev= tokens.size()>0? tokens.get(tokens.size()-1):null;
            String prevId=(prev instanceof IdentifierToken &&((IdentifierToken) prev).type == IdentifierType.WORD)?
                    ((IdentifierToken)prev).name:null;
            if(pState.currentMacro!=null){
                if(str.equals("#end")){
                    pState.rootContext.declareNamedDeclareable(pState.currentMacro,ioContext);
                    pState.currentMacro=null;
                }else{
                    pState.currentMacro.content.add(new StringWithPos(str,pos));
                }
                return;
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
                    return;
                }else if(floatDec.matcher(str).matches()){
                    //dez-Float
                    double d = Double.parseDouble(str);
                    tokens.add(new ValueToken(Value.ofFloat(d), pos, false));
                    return;
                }else if(floatBin.matcher(str).matches()){
                    //bin-Float
                    double d= Value.parseFloat(str.substring(BIN_PREFIX.length()),2);
                    tokens.add(new ValueToken(Value.ofFloat(d), pos, false));
                    return;
                }else if(floatHex.matcher(str).matches()){
                    //hex-Float
                    double d=Value.parseFloat(str.substring(BIN_PREFIX.length()),16);
                    tokens.add(new ValueToken(Value.ofFloat(d), pos, false));
                    return;
                }
            }catch(ConcatRuntimeError|NumberFormatException e){
                throw new SyntaxError(e, pos);
            }
            switch (str){
                //code-sections
                case "#define"->{
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("macros can only be defined at root-level",pos);
                    }
                    if(prevId!=null){
                        tokens.remove(tokens.size()-1);
                        finishParsing(pState, ioContext,false);
                        pState.currentMacro=new Macro(pos,prevId,new ArrayList<>());
                    }else{
                        throw new SyntaxError("invalid token preceding #define: "+prev+" expected identifier",pos);
                    }
                }
                case "#namespace"-> {
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("namespaces can only be declared at root-level",pos);
                    }
                    if(prevId != null){
                        tokens.remove(tokens.size()-1);
                        finishParsing(pState, ioContext,true);
                        pState.rootContext.startModule(prevId,pos);
                    }else{
                        throw new SyntaxError("namespace name has to be an identifier",pos);
                    }
                }
                case "#end"-> {
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("namespaces can only be closed at root-level",pos);
                    }else{
                        finishParsing(pState, ioContext,true);
                        pState.rootContext.endModule(pos);
                    }
                }
                case "#import"-> {
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("imports are can only allowed at root-level",pos);
                    }
                    if(prevId != null){
                        tokens.remove(tokens.size()-1);
                        finishParsing(pState, ioContext,false);
                        pState.rootContext.addImport(prevId,pos);
                    }else{
                        throw new SyntaxError("imported namespace name has to be an identifier",pos);
                    }
                }
                case "#include" -> {
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("includes are can only allowed at root-level",pos);
                    }
                    if(prev instanceof ValueToken){
                        tokens.remove(tokens.size()-1);
                        finishParsing(pState, ioContext,false);
                        String name=((ValueToken) prev).value.stringValue();
                        File file=new File(name);
                        if(file.exists()){
                            try {
                                parse(file,pState,ioContext);
                            } catch (IOException e) {
                                throw new SyntaxError(e,pos);
                            }
                        }else{
                            throw new SyntaxError("File "+name+" does not exist",pos);
                        }
                    }else if(prevId != null){
                        tokens.remove(tokens.size()-1);
                        finishParsing(pState, ioContext,false);
                        String path=libPath+File.separator+ prevId +DEFAULT_FILE_EXTENSION;
                        File file=new File(path);
                        if(file.exists()){
                            try {
                                parse(file,pState,ioContext);
                            } catch (IOException e) {
                                throw new SyntaxError(e,pos);
                            }
                        }else{
                            throw new SyntaxError(prevId+" is not part of the standard library",pos);
                        }
                    }else{
                        throw new SyntaxError("include path has to be a string literal or identifier",pos);
                    }
                }
                case "tuple" -> {
                    String name;
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("tuples can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing enum name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,false);
                    if(!(prev instanceof IdentifierToken)){
                        throw new SyntaxError("token before root level tuple has to be an identifier",pos);
                    }else if(((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before root level tuple has to be an unmodified identifier",pos);
                    }
                    name = ((IdentifierToken) prev).name;
                    pState.openBlocks.add(new TupleBlock(name, 0,pos,pState.rootContext));
                }
                case "enum" ->{
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("enums can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing enum name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,false);
                    if(!(prev instanceof IdentifierToken)){
                        throw new SyntaxError("token before 'enum' has to be an identifier",pos);
                    }else if(((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before 'enum' has to be an unmodified identifier",pos);
                    }
                    String name = ((IdentifierToken) prev).name;
                    pState.openBlocks.add(new EnumBlock(name, 0,pos,pState.rootContext));
                }
                case "proc","procedure" ->{
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("procedures can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing procedure name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,false);
                    boolean isNative=false;
                    if(!(prev instanceof IdentifierToken)){
                        throw new SyntaxError("token before 'proc' has to be an identifier",pos);
                    }else if(((IdentifierToken) prev).type==IdentifierType.NATIVE){
                        isNative=true;
                    }else if(((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before 'proc' has to be an unmodified identifier",pos);
                    }
                    String name = ((IdentifierToken) prev).name;
                    ProcedureBlock proc = new ProcedureBlock(name, 0, pos, pState.rootContext, isNative);
                    pState.openBlocks.add(proc);
                    pState.openedProcs.add(proc.context());
                }
                case "lambda","λ" -> {
                    ProcedureBlock lambda = new ProcedureBlock(null, tokens.size(), pos, pState.getContext(), false);
                    pState.openBlocks.add(lambda);
                    pState.openedProcs.add(lambda.context());
                }
                case "=>" ->{
                    CodeBlock block = pState.openBlocks.peekLast();
                    if(block instanceof ProcedureBlock proc) {
                        List<Token> ins = tokens.subList(proc.start, tokens.size());
                        proc.addIns(typeCheck(ins,block.context(),pState.globalVariables,ioContext),pos);
                        ins.clear();
                    }else if(block!=null&&block.type==BlockType.ANONYMOUS_TUPLE){
                        pState.openBlocks.removeLast();
                        pState.openBlocks.addLast(new ProcTypeBlock((ListBlock)block,tokens.size()));
                    }else{
                        throw new SyntaxError("'=>' can only be used in proc- or proc-type blocks ",pos);
                    }
                }
                case ":" -> {
                    CodeBlock block=pState.openBlocks.peekLast();
                    if(block==null){
                        throw new SyntaxError(": can only be used in proc- and lambda- blocks",pos);
                    }else if(block.type==BlockType.PROCEDURE){
                        //handle procedure separately since : does not change context of produce a jump
                        ProcedureBlock proc=(ProcedureBlock) block;
                        List<Token> outs = tokens.subList(proc.start, tokens.size());
                        proc.addOuts(typeCheck(outs,block.context(),pState.globalVariables,ioContext),pos);
                        outs.clear();
                    }else{
                        throw new SyntaxError(": can only be used in proc- and lambda- blocks", pos);
                    }
                }
                case "end" ->{
                    if(pState.openBlocks2>0){//process 'end' for blocks processed in 2nd compile step
                        pState.openBlocks2--;
                        if(tokens.size()>0&&tokens.get(tokens.size()-1) instanceof BlockToken b&&
                                b.blockType==BlockTokenType.DO){//merge do-end
                            tokens.set(tokens.size()-1,new BlockToken(BlockTokenType.DO_WHILE, pos, -1));
                        }else{
                            tokens.add(new BlockToken(BlockTokenType.END, pos, -1));
                        }
                        break;
                    }
                    CodeBlock block=pState.openBlocks.pollLast();
                    if(block==null) {
                        throw new SyntaxError("unexpected 'end' statement",pos);
                    }
                    Token tmp;
                    switch (block.type) {
                        case PROCEDURE -> {
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            ArrayList<Token> content=new ArrayList<>(subList);
                            subList.clear();
                            ProcedureContext context = ((ProcedureBlock) block).context();
                            if(context != pState.openedProcs.pollLast()){
                                throw new RuntimeException("openedProcs is out of sync with openBlocks");
                            }
                            assert context != null;
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
                                pState.rootContext.declareNamedDeclareable(proc,ioContext);
                            }else{
                                Value.Procedure proc=Value.createProcedure(procType, content,
                                        generics.toArray(Type.GenericParameter[]::new), block.startPos, context);
                                assert context.curried.isEmpty();
                                if(((ProcedureBlock) block).name!=null){
                                    pState.unparsedProcs.add(proc);
                                    pState.rootContext.declareProcedure(((ProcedureBlock) block).name,proc,ioContext);
                                }else{
                                    tokens.add(new ValueToken(TokenType.LAMBDA,proc, block.startPos, false));
                                }
                            }
                        }
                        case ENUM -> {
                            if(tokens.size()>block.start){
                                tmp=tokens.get(block.start);
                                throw new SyntaxError("Invalid token in enum:"+tmp,tmp.pos);
                            }
                            pState.rootContext.declareEnum(((EnumBlock) block),ioContext);
                        }
                        case TUPLE -> {
                            ArrayList<Type.GenericParameter> generics=((TupleBlock) block).context.generics;
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            Type[] types=ProcedureBlock.getSignature(
                                    typeCheck(subList, block.context(), pState.globalVariables,ioContext), true);
                            subList.clear();
                            if(generics.size()>0){
                                GenericTuple tuple=new GenericTuple(((TupleBlock) block).name,
                                        generics.toArray(Type.GenericParameter[]::new),types,block.startPos);
                                pState.rootContext.declareNamedDeclareable(tuple,ioContext);
                            }else{
                                Type.Tuple tuple=new Type.Tuple(((TupleBlock) block).name,types,block.startPos);
                                pState.rootContext.declareNamedDeclareable(tuple,ioContext);
                            }
                        }
                        case IF,WHILE,SWITCH_CASE ->
                                throw new SyntaxError("blocks of type "+block.type+
                                        " should not exist at this stage of compilation",pos);
                        case ANONYMOUS_TUPLE,PROC_TYPE,CONST_LIST ->
                                throw new SyntaxError("unexpected 'end' statement",pos);
                    }
                }
                case "while" -> {
                    tokens.add(new BlockToken(BlockTokenType.WHILE, pos, -1));
                    pState.openBlocks2++;
                }
                case "switch" -> {
                    tokens.add(new BlockToken(BlockTokenType.SWITCH, pos, -1));
                    pState.openBlocks2++;
                }
                case "if" -> {
                    tokens.add(new BlockToken(BlockTokenType.IF, pos, -1));
                    pState.openBlocks2++;
                }
                case "do" -> {
                    if(pState.openBlocks2==0){
                        throw new SyntaxError("'do' can only appear in while-blocks",pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.DO, pos, -1));
                }
                case "_if" -> {
                    if(pState.openBlocks2==0){
                        throw new SyntaxError("'_if' can only appear in if-blocks",pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType._IF, pos, -1));
                }
                case "else" ->  {
                    if(pState.openBlocks2==0){
                        throw new SyntaxError("'else' can only appear in if-blocks",pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.ELSE, pos, -1));
                }
                case "case" -> {
                    if (pState.openBlocks2 == 0) {
                        throw new SyntaxError("'case' can only appear in switch-blocks", pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.CASE, pos, -1));
                }
                case "default"  -> {
                    if (pState.openBlocks2 == 0) {
                        throw new SyntaxError("'default' can only appear in switch-blocks", pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.DEFAULT, pos, -1));
                }
                case "end-case" -> {
                    if (pState.openBlocks2 == 0) {
                        throw new SyntaxError("'end-case' can only appear in switch-blocks", pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.END_CASE, pos, -1));
                }
                case "return" -> tokens.add(new Token(TokenType.RETURN,  pos));
                case "exit"   -> tokens.add(new Token(TokenType.EXIT,  pos));
                case "(" ->
                        pState.openBlocks.add(new ListBlock(tokens.size(),BlockType.ANONYMOUS_TUPLE,pos, pState.getContext()));
                case ")" -> {
                    CodeBlock open=pState.openBlocks.pollLast();
                    if(open==null||(open.type!=BlockType.ANONYMOUS_TUPLE&&open.type!=BlockType.PROC_TYPE)){
                        throw new SyntaxError("unexpected ')' statement ",pos);
                    }
                    if(open.type==BlockType.PROC_TYPE){
                        List<Token> subList=tokens.subList(open.start, ((ProcTypeBlock)open).separatorPos);
                        Type[] inTypes=ProcedureBlock.getSignature(
                                typeCheck(subList,pState.rootContext,pState.globalVariables,ioContext),false);
                        subList=tokens.subList(((ProcTypeBlock)open).separatorPos, tokens.size());
                        Type[] outTypes=ProcedureBlock.getSignature(
                                typeCheck(subList,pState.rootContext,pState.globalVariables,ioContext),false);
                        subList=tokens.subList(open.start,tokens.size());
                        subList.clear();
                        tokens.add(new ValueToken(Value.ofType(Type.Procedure.create(inTypes,outTypes)),
                                pos,false));
                    }else {
                        List<Token> subList=tokens.subList(open.start, tokens.size());
                        Type[] tupleTypes=ProcedureBlock.getSignature(
                                typeCheck(subList,pState.rootContext,pState.globalVariables,ioContext),true);
                        subList.clear();
                        tokens.add(new ValueToken(Value.ofType(new Type.Tuple(null,tupleTypes,pos)),
                                pos,false));
                    }
                }
                case "{" -> tokens.add(new BlockToken(BlockTokenType.LIST,        pos,-1));
                case "}" -> tokens.add(new BlockToken(BlockTokenType.END_LIST,    pos,-1));

                //debug helpers
                case "debugPrint"    -> tokens.add(new Token(TokenType.DEBUG_PRINT, pos));
                case "assert"    -> {
                    if(tokens.size()<2){
                        throw new SyntaxError("not enough tokens for 'assert'",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    if(!(prev instanceof ValueToken&&((ValueToken) prev).value.isString())){
                        throw new SyntaxError("tokens directly preceding 'assert' has to be a constant string",pos);
                    }
                    String message=((ValueToken) prev).value.stringValue();
                    tokens.add(new AssertToken(message, pos));
                }
                //constants
                case "true"  -> tokens.add(new ValueToken(Value.TRUE,    pos, false));
                case "false" -> tokens.add(new ValueToken(Value.FALSE,   pos, false));
                //types
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

                case "list"     -> tokens.add(new OperatorToken(OperatorType.LIST_OF, pos));
                case "optional" -> tokens.add(new OperatorToken(OperatorType.OPTIONAL_OF, pos));
                case "content"  -> tokens.add(new OperatorToken(OperatorType.CONTENT, pos));
                case "isEnum"   -> tokens.add(new OperatorToken(OperatorType.IS_ENUM,pos));

                case "cast"   -> tokens.add(new TypedToken(TokenType.CAST,null,pos));
                case "typeof" -> tokens.add(new OperatorToken(OperatorType.TYPE_OF, pos));
                //stack modifiers addLater better stack modifiers
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
                //operators
                case "refId"  -> tokens.add(new OperatorToken(OperatorType.REF_ID,     pos));
                case "clone"  -> tokens.add(new OperatorToken(OperatorType.CLONE,      pos));
                case "clone!" -> tokens.add(new OperatorToken(OperatorType.DEEP_CLONE, pos));

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

                case ">>:"   -> tokens.add(new OperatorToken(OperatorType.PUSH_FIRST,     pos));
                case ":<<"   -> tokens.add(new OperatorToken(OperatorType.PUSH_LAST,      pos));
                case "+:"    -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_FIRST, pos));
                case ":+"    -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_LAST,  pos));
                case "clear" -> tokens.add(new OperatorToken(OperatorType.CLEAR,          pos));
                //<array> <index> []
                case "[]"    -> tokens.add(new OperatorToken(OperatorType.GET_INDEX,      pos));
                //<array> <off> <to> [:]
                case "[:]"   -> tokens.add(new OperatorToken(OperatorType.GET_SLICE,      pos));
                case "length" -> tokens.add(new OperatorToken(OperatorType.LENGTH,   pos));

                case "()"     -> tokens.add(new Token(TokenType.CALL_PTR, pos));

                case "wrap"   -> tokens.add(new OperatorToken(OperatorType.WRAP,           pos));
                case "unwrap" -> tokens.add(new OperatorToken(OperatorType.UNWRAP,         pos));
                case "??"     -> tokens.add(new OperatorToken(OperatorType.HAS_VALUE,      pos));
                case "empty"  -> tokens.add(new OperatorToken(OperatorType.EMPTY_OPTIONAL, pos));

                case "new"       -> tokens.add(new TypedToken(TokenType.NEW,null, pos));
                case "ensureCap" -> tokens.add(new OperatorToken(OperatorType.ENSURE_CAP, pos));
                case "fill"      -> tokens.add(new OperatorToken(OperatorType.FILL,       pos));

                //identifiers
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
                        if(((IdentifierToken) prev).type == IdentifierType.WORD){
                            prev=new IdentifierToken(IdentifierType.VAR_WRITE,((IdentifierToken) prev).name,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=' modifier: "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                    }else{
                        throw new SyntaxError("invalid token for '=' modifier: "+prev,prev.pos);
                    }
                }
                case "."->{
                    if(prev==null||tokens.size()<2){
                        throw new SyntaxError("not enough tokens tokens for '.' modifier",pos);
                    }else if(prevId!=null){
                        Token prePrev=tokens.get(tokens.size()-2);
                        if(!(prePrev instanceof IdentifierToken && ((IdentifierToken) prePrev).type==IdentifierType.WORD)){
                            throw new SyntaxError("invalid token for '.' modifier: "+prePrev,prePrev.pos);
                        }
                        String newName=((IdentifierToken)prePrev).name+ MODULE_SEPARATOR +prevId;
                        tokens.remove(tokens.size()-1);
                        Declareable d=pState.rootContext.getDeclareable(newName);
                        if(d instanceof Macro){
                            tokens.remove(tokens.size()-1);
                            expandMacro(pState,(Macro)d,pos,ioContext);
                        }else{
                            prev=new IdentifierToken(IdentifierType.WORD, newName,pos);
                            tokens.set(tokens.size()-1,prev);
                        }
                    }else{
                        throw new SyntaxError("invalid token for '.' modifier: "+prev,prev.pos);
                    }
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
                }
                case "@()"->{
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '@()' modifier",pos);
                    }else if(prevId!=null){
                        tokens.set(tokens.size()-1,new IdentifierToken(IdentifierType.PROC_ID,prevId,prev.pos));
                    }else{
                        throw new SyntaxError("invalid token for '@()' modifier: "+prev,prev.pos);
                    }
                }
                case "<>" ->{
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '<>' modifier",pos);
                    }else if(prevId!=null){
                        tokens.set(tokens.size()-1,new IdentifierToken(IdentifierType.NEW_GENERIC,prevId,prev.pos));
                    }else{
                        throw new SyntaxError("invalid token for '<>' modifier: "+prev,prev.pos);
                    }
                }
                default -> {
                    Declareable d=pState.rootContext.getDeclareable(str);
                    if(d instanceof Macro){
                        expandMacro(pState,(Macro)d,pos,ioContext);
                    }else{
                        CodeBlock last= pState.openBlocks.peekLast();
                        if(last instanceof EnumBlock){
                            ((EnumBlock) last).add(str,pos);
                        }else{
                            tokens.add(new IdentifierToken(IdentifierType.WORD, str, pos));
                        }
                    }
                }
            }
        }
    }
    private void expandMacro(ParserState pState, Macro m, FilePosition pos, IOContext ioContext) throws SyntaxError {
        for(StringWithPos s:m.content){
            finishWord(s.str,pState,new FilePosition(s.start, pos),ioContext);
        }
    }
    private void finishParsing(ParserState pState, IOContext ioContext,boolean parseProcs) throws SyntaxError {
        pState.globalCode.addAll(typeCheck(pState.tokens, pState.rootContext,
                pState.globalVariables, ioContext));
        if(parseProcs){
            Value.Procedure p;
            while((p=pState.unparsedProcs.poll())!=null){
                p.tokens=typeCheck(p.tokens,p.context,pState.globalVariables,ioContext);
            }
        }
        pState.tokens.clear();
    }

    public ArrayList<Token> typeCheck(List<Token> tokens,VariableContext context,
                                      HashMap<VariableId,Value> globalConstants,IOContext ioContext) throws SyntaxError {
        ArrayDeque<CodeBlock> openBlocks=new ArrayDeque<>();
        ArrayList<Token> ret=new ArrayList<>(tokens.size());
        Token prev;
        for(int i=0;i<tokens.size();i++){
            Token t=tokens.get(i);
            if(t instanceof BlockToken block){
                switch (block.blockType){
                    case IF ->{
                        IfBlock ifBlock = new IfBlock(ret.size(), t.pos, context);
                        openBlocks.add(ifBlock);
                        ret.add(t);
                        context=ifBlock.context();
                        ret.add(new ContextOpen(context,t.pos));
                    }
                    case ELSE -> {//addLater handle if-jumps in else-block
                        CodeBlock open=openBlocks.peekLast();
                        if(!(open instanceof IfBlock ifBlock)){
                            throw new SyntaxError("'else' can only be used in if-blocks",t.pos);
                        }
                        ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                        if(ifBlock.branches.size()>0){//end else-context
                            ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                        }
                        context=ifBlock.elseBranch(ret.size(),t.pos);
                        ret.add(t);
                        if(ifBlock.branches.size()<2){//start else-context after first else
                            ret.add(new ContextOpen(context,t.pos));
                        }
                    }
                    case _IF -> {
                        CodeBlock open=openBlocks.peekLast();
                        if(!(open instanceof IfBlock ifBlock)){
                            throw new SyntaxError("'_if' can only be used in if-blocks",t.pos);
                        }
                        context=ifBlock.newBranch(ret.size(),t.pos);
                        ret.add(t);
                        ret.add(new ContextOpen(context,t.pos));
                    }
                    case END_IF,END_WHILE ->
                        throw new RuntimeException("block tokens of type "+block.blockType+
                                " should not exist at this stage of compilation");
                    case WHILE -> {
                        WhileBlock whileBlock = new WhileBlock(ret.size(), t.pos, context);
                        openBlocks.add(whileBlock);
                        context= whileBlock.context();
                        ret.add(t);
                        ret.add(new ContextOpen(context,t.pos));
                    }
                    case DO -> {
                        CodeBlock open=openBlocks.peekLast();
                        if(!(open instanceof WhileBlock whileBlock)){
                            throw new SyntaxError("do can only be used in while- blocks",t.pos);
                        }
                        ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                        int forkPos=ret.size();
                        //context variable will be reset on fork
                        ret.add(t);
                        context= whileBlock.fork(forkPos, t.pos);
                        ret.add(new ContextOpen(context,t.pos));
                    }
                    case DO_WHILE -> {
                        CodeBlock open=openBlocks.pollLast();
                        if(!(open instanceof WhileBlock whileBlock)){
                            throw new SyntaxError("do can only be used in while- blocks",t.pos);
                        }
                        whileBlock.fork(ret.size(), t.pos);//whileBlock.end() only checks if fork was called
                        ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                        context=((BlockContext)context).parent;
                        ret.add(new BlockToken(BlockTokenType.DO_WHILE,t.pos,open.start - (ret.size()-1)));
                    }
                    case SWITCH -> {
                        SwitchCaseBlock switchBlock=new SwitchCaseBlock(ret.size(), t.pos,context);
                        openBlocks.addLast(switchBlock);
                        //handle switch-case separately since : does not change context and produces special jump
                        ret.add(new SwitchToken(switchBlock,t.pos));
                        switchBlock.newSection(ret.size(),t.pos);
                        //find case statement
                        int j=i+1;
                        while(j<tokens.size()&&(!(tokens.get(j) instanceof BlockToken))){
                            j++;
                        }
                        if(j>= tokens.size()){
                            throw new SyntaxError("found no case-statement for switch at "+t.pos,
                                    tokens.get(tokens.size()-1).pos);
                        }else if(((BlockToken)tokens.get(j)).blockType!=BlockTokenType.CASE){
                            throw new SyntaxError("unexpected statement in switch at "+t.pos+" "+
                                    tokens.get(j)+" expected 'case' statement",
                                    tokens.get(j).pos);
                        }
                        List<Token> caseValues=tokens.subList(i+1,j);
                        context=switchBlock.caseBlock(typeCheck(caseValues,context,globalConstants,ioContext),
                                tokens.get(j).pos);
                        ret.add(new ContextOpen(context,t.pos));
                        i=j;
                    }
                    case END_CASE -> {
                        CodeBlock open=openBlocks.peekLast();
                        if(!(open instanceof SwitchCaseBlock switchBlock)){
                            throw new SyntaxError("end-case can only be used in switch-case-blocks",t.pos);
                        }
                        ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                        context=((BlockContext)context).parent;
                        ret.add(new BlockToken(BlockTokenType.END_CASE, t.pos, -1));
                        switchBlock.newSection(ret.size(),t.pos);
                        //find case statement
                        int j=i+1;
                        while(j<tokens.size()&&(!(tokens.get(j) instanceof BlockToken))){
                            j++;
                        }
                        if(j>= tokens.size()){
                            throw new SyntaxError("found no case-statement for switch at "+t.pos,
                                    tokens.get(tokens.size()-1).pos);
                        }
                        t=tokens.get(j);
                        if(((BlockToken)t).blockType==BlockTokenType.CASE){
                            List<Token> caseValues=tokens.subList(i+1,j);
                            context=switchBlock.caseBlock(typeCheck(caseValues,context,globalConstants,ioContext),
                                    tokens.get(j).pos);
                            ret.add(new ContextOpen(context,t.pos));
                        }else if(((BlockToken)t).blockType==BlockTokenType.DEFAULT){
                            context=switchBlock.defaultBlock(ret.size(),t.pos);
                            ret.add(new ContextOpen(context,t.pos));
                        }else if(((BlockToken)t).blockType==BlockTokenType.END){
                            switchBlock.end(ret.size(),t.pos,ioContext);
                            openBlocks.removeLast();//remove switch-block form blocks
                            for(Integer p:switchBlock.blockEnds){
                                Token tmp=ret.get(p);
                                ((BlockToken)tmp).delta=ret.size()-p;
                            }
                        }else{
                            throw new SyntaxError("unexpected statement after end-case at "+tokens.get(i).pos+": "+
                                    t+" expected 'case', 'default' or 'end' statement",t.pos);
                        }
                        i=j;
                    }
                    case CASE ->
                            throw new SyntaxError("unexpected 'case' statement",t.pos);
                    case DEFAULT ->
                            throw new SyntaxError("unexpected 'default' statement",t.pos);
                    case END -> {
                        CodeBlock open=openBlocks.pollLast();
                        if(open==null){
                            throw new SyntaxError("unexpected 'end' statement",t.pos);
                        }
                        Token tmp;
                        switch (open.type){
                            case IF -> {
                                if(((IfBlock) open).forkPos!=-1){
                                    tmp=ret.get(((IfBlock) open).forkPos);
                                    ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                                    context=((BlockContext)context).parent;
                                    //when there is no else then the last branch has to jump onto the close operation
                                    ((BlockToken)tmp).delta=ret.size()-((IfBlock) open).forkPos;
                                    if(((IfBlock) open).branches.size()>0){//close-else context
                                        ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                                        context=((BlockContext)context).parent;
                                    }
                                }else{//close else context
                                    ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                                    context=((BlockContext)context).parent;
                                }
                                for(IfBranch branch:((IfBlock) open).branches){
                                    tmp=ret.get(branch.fork);
                                    ((BlockToken)tmp).delta=branch.end-branch.fork+1;
                                    tmp=ret.get(branch.end);
                                    ((BlockToken)tmp).delta=ret.size()-branch.end;
                                }
                                ret.add(new BlockToken(BlockTokenType.END_IF,t.pos,-1));
                            }
                            case WHILE -> {
                                ((WhileBlock)open).end(t.pos);
                                ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                                context=((BlockContext)context).parent;
                                tmp=ret.get(((WhileBlock) open).forkPos);
                                ret.add(new BlockToken(BlockTokenType.END_WHILE,t.pos, open.start - ret.size()));
                                ((BlockToken)tmp).delta=ret.size()-((WhileBlock)open).forkPos;
                            }
                            case SWITCH_CASE -> {//end switch with default
                                if(((SwitchCaseBlock)open).defaultJump==-1){
                                    throw new SyntaxError("missing end-case statement",t.pos);
                                }
                                ret.add(new Token(TokenType.CONTEXT_CLOSE,t.pos));
                                context=((BlockContext)context).parent;
                                ((SwitchCaseBlock)open).end(ret.size(),t.pos,ioContext);
                                for(Integer p:((SwitchCaseBlock) open).blockEnds){
                                    tmp=ret.get(p);
                                    ((BlockToken)tmp).delta=ret.size()-p;
                                }
                            }
                            case PROCEDURE,PROC_TYPE,CONST_LIST,ANONYMOUS_TUPLE,TUPLE,ENUM ->
                                throw new SyntaxError("blocks of type "+open.type+
                                        " should not exist at this stage of compilation",t.pos);
                        }
                    }
                    case LIST ->
                            openBlocks.add(new ListBlock(ret.size(),BlockType.CONST_LIST,t.pos,context));
                    case END_LIST -> {
                        CodeBlock open=openBlocks.pollLast();
                        if(open==null||open.type!=BlockType.CONST_LIST){
                            throw new SyntaxError("unexpected '}' statement ",t.pos);
                        }
                        List<Token> subList = ret.subList(open.start, ret.size());
                        ArrayList<Value> values=new ArrayList<>(subList.size());
                        boolean constant=true;
                        Type type=null;
                        for(Token v:subList){
                            if(v instanceof ValueToken){
                                type=Type.commonSuperType(type,((ValueToken) v).value.type);
                                values.add(((ValueToken) v).value);
                            }else{
                                values.clear();
                                constant=false;
                                break;
                            }
                        }
                        if(constant){
                            subList.clear();
                            try {
                                for(int p=0;p< values.size();p++){
                                    values.set(p,values.get(p).castTo(type));
                                }
                                ret.add(new ValueToken(Value.createList(Type.listOf(type),values),
                                        open.startPos,true));
                            } catch (ConcatRuntimeError e) {
                                throw new SyntaxError(e,t.pos);
                            }
                        }else{
                            ArrayList<Token> listTokens=new ArrayList<>(subList);
                            subList.clear();
                            ret.add(new ListCreatorToken(listTokens,t.pos));
                        }
                    }
                }
            }else if(t instanceof IdentifierToken identifier){
                switch (identifier.type) {
                    case NATIVE -> throw new RuntimeException("unreachable");
                    case NEW_GENERIC -> {
                        if(!(context instanceof GenericContext)){
                            throw new SyntaxError("generics can only be declared in tuple and procedure signatures",
                                    identifier.pos);
                        }
                        ((GenericContext) context).declareGeneric( identifier.name,identifier.pos,ioContext);
                    }
                    case DECLARE, CONST_DECLARE, NATIVE_DECLARE -> {
                        if (ret.size() > 0 && (prev = ret.remove(ret.size() - 1)) instanceof ValueToken) {
                            try {//remember constant declarations
                                Type type = ((ValueToken) prev).value.asType();
                                VariableId id = context.declareVariable(
                                        identifier.name, type, identifier.type != IdentifierType.DECLARE,
                                        identifier.pos, ioContext);
                                AccessType accessType = identifier.type == IdentifierType.DECLARE ?
                                        AccessType.DECLARE : AccessType.CONST_DECLARE;
                                //only remember root-level constants
                                if (identifier.type == IdentifierType.NATIVE_DECLARE) {
                                    globalConstants.put(id, Value.loadNativeConstant(type, identifier.name, t.pos));
                                    break;
                                } else if (id.isConstant && id.context.procedureContext() == null
                                        && (prev = ret.get(ret.size()-1)) instanceof ValueToken) {
                                    globalConstants.put(id, ((ValueToken) prev).value.clone(true).castTo(type));
                                    ret.remove(ret.size() - 1);
                                    break;//don't add token to code
                                }
                                ret.add(new VariableToken(identifier.pos, identifier.name, id,
                                        accessType, context));
                            } catch (ConcatRuntimeError e) {
                                throw new SyntaxError(e.getMessage(), prev.pos);
                            }
                        } else {
                            throw new SyntaxError("Token before declaration has to be a type", identifier.pos);
                        }
                    }
                    case WORD -> {
                        Declareable d = context.getDeclareable(identifier.name);
                        if(d==null){
                            throw new SyntaxError("variable "+identifier.name+" does not exist",identifier.pos);
                        }
                        DeclareableType type = d.declarableType();
                        switch (type) {
                            case PROCEDURE -> {
                                Value.Procedure proc = (Value.Procedure) d;
                                ProcedureToken token = new ProcedureToken(false, proc, identifier.pos);
                                ret.add(token);
                            }
                            case NATIVE_PROC -> {
                                Value.NativeProcedure proc = (Value.NativeProcedure) d;
                                NativeProcedureToken token = new NativeProcedureToken(false, proc, identifier.pos);
                                ret.add(token);
                            }
                            case VARIABLE, CONSTANT, CURRIED_VARIABLE -> {
                                VariableId id = (VariableId) d;
                                id = context.wrapCurried(identifier.name, id, identifier.pos);
                                Value constValue = globalConstants.get(id);
                                if (constValue != null) {
                                    ret.add(new ValueToken(constValue, identifier.pos, false));
                                } else {
                                    ret.add(new VariableToken(identifier.pos, identifier.name, id,
                                            AccessType.READ, context));
                                }
                            }
                            case MACRO ->
                                    throw new RuntimeException("macros should already be resolved at this state of compilation");
                            case TUPLE, ENUM, GENERIC -> ret.add(
                                    new ValueToken(Value.ofType((Type) d), identifier.pos, false));
                            case ENUM_ENTRY -> ret.add(
                                    new ValueToken((Value.EnumEntry) d, identifier.pos, false));
                            case GENERIC_TUPLE -> {
                                GenericTuple g = (GenericTuple) d;
                                Type[] genArgs = new Type[g.params.length];
                                for (int j = genArgs.length - 1; j >= 0; j--) {
                                    if (ret.size() <= 0) {
                                        throw new SyntaxError("Not enough arguments for " +
                                                declarableName(DeclareableType.GENERIC_TUPLE, false) + " " + ((GenericTuple) d).name,
                                                t.pos);
                                    }
                                    prev = ret.remove(ret.size()-1);
                                    if (!(prev instanceof ValueToken)) {
                                        throw new SyntaxError("invalid token for type-parameter:" + prev, prev.pos);
                                    }
                                    try {
                                        genArgs[j] = ((ValueToken) prev).value.asType();
                                    } catch (TypeError e) {
                                        throw new SyntaxError(e.getMessage(), prev.pos);
                                    }
                                }
                                ret.add(new ValueToken(Value.ofType(
                                        Type.GenericTuple.create(g.name, g.params.clone(), genArgs, g.types.clone(), g.declaredAt)),
                                        identifier.pos, false));
                            }
                        }
                    }
                    case VAR_WRITE -> {
                        Declareable d=context.getDeclareable(identifier.name);
                        if(d==null){
                            throw new SyntaxError("variable "+identifier.name+" does not exist",t.pos);
                        }else if(d.declarableType()==DeclareableType.VARIABLE){
                            VariableId id=(VariableId) d;
                            context.wrapCurried(identifier.name,id,identifier.pos);
                            assert !globalConstants.containsKey(id);
                            ret.add(new VariableToken(identifier.pos,identifier.name,id,
                                    AccessType.WRITE,context));
                        }else{
                            throw new SyntaxError(declarableName(d.declarableType(),false)+" "+
                                    identifier.name+" (declared at "+d.declaredAt()+") is not a variable",t.pos);
                        }
                    }
                    case PROC_ID -> {
                        Declareable d=context.getDeclareable(identifier.name);
                        if(d instanceof Value.NativeProcedure proc){
                            NativeProcedureToken token=new NativeProcedureToken(true,proc,t.pos);
                            ret.add(token);
                        }else if(d instanceof Value.Procedure proc){
                            ProcedureToken token=new ProcedureToken(true,proc,t.pos);
                            ret.add(token);
                        }else{
                            throw new SyntaxError(declarableName(d.declarableType(),false)+" "+
                                    identifier.name+" (declared at "+d.declaredAt()+") is not a procedure",t.pos);
                        }
                    }
                }
            }else if(t instanceof AssertToken){
                if((prev=ret.get(ret.size()-1)) instanceof ValueToken){
                    try {
                        if(!((ValueToken) prev).value.asBool()){
                            throw new SyntaxError("assertion failed: "+ ((AssertToken)t).message,t.pos);
                        }
                    } catch (TypeError e) {
                        throw new SyntaxError(e,t.pos);
                    }
                }else{
                    ret.add(t);
                }
            }else if(t instanceof OperatorToken op){
                switch (op.opType){
                    case LIST_OF -> {//addLater make pre-evaluation optional (excluding type-signatures and global context)
                        if(ret.size()>0&&(prev=ret.get(ret.size()-1)) instanceof ValueToken){
                            try {
                                ret.set(ret.size()-1,
                                        new ValueToken(Value.ofType(
                                                Type.listOf(((ValueToken)prev).value.asType())),
                                                t.pos, false));
                            } catch (ConcatRuntimeError e) {
                                throw new SyntaxError(e,t.pos);
                            }
                        }else{
                            ret.add(t);
                        }
                    }
                    case OPTIONAL_OF -> {
                        if(ret.size()>0&&(prev=ret.get(ret.size()-1)) instanceof ValueToken){
                            try {
                                ret.set(ret.size()-1,
                                        new ValueToken(Value.ofType(
                                                Type.optionalOf(((ValueToken)prev).value.asType())),
                                                t.pos, false));
                            } catch (ConcatRuntimeError e) {
                                throw new SyntaxError(e,t.pos);
                            }
                        }else{
                            ret.add(t);
                        }
                    }
                    default -> //TODO type-check and pre-evaluate other operations
                            ret.add(t);
                }
            }else if(t.tokenType==TokenType.NEW){
                if(ret.size()<1||!((prev=ret.remove(ret.size()-1)) instanceof ValueToken)) {
                    throw new SyntaxError("token before of new has to be a type", t.pos);
                }
                try {
                    Type type = ((ValueToken)prev).value.asType();
                    if(type instanceof Type.Tuple){
                        int c=((Type.Tuple) type).elementCount();
                        if(c < 0){
                            throw new ConcatRuntimeError("the element count has to be at least 0");
                        }
                        int iMin=ret.size()-c;
                        if(iMin>=0){
                            Value[] values=new Value[c];
                            for(int j=c-1;j>=0;j--){
                                prev=ret.get(iMin+j);
                                if(prev instanceof ValueToken){
                                    values[j]=((ValueToken) prev).value.castTo(((Type.Tuple) type).get(j));
                                }else{
                                    break;
                                }
                            }
                            if(c==0||values[0]!=null){//all types resolved successfully
                                ret.subList(iMin, ret.size()).clear();
                                ret.add(new ValueToken(Value.createTuple((Type.Tuple)type,values),
                                        t.pos, false));
                                continue;//got to next item
                            }
                        }
                    }//TODO support list in pre-evaluation
                    ret.add(new TypedToken(TokenType.NEW,type, t.pos));
                } catch (ConcatRuntimeError e) {
                    throw new SyntaxError(e.getMessage(), prev.pos);
                }
            }else if(t.tokenType == TokenType.LAMBDA){//parse lambda-procedures
                Value.Procedure lambda = (Value.Procedure) ((ValueToken) t).value;
                lambda.tokens=typeCheck(lambda.tokens(),lambda.context,globalConstants,ioContext);
                if(lambda.context.curried.isEmpty()){
                    ret.add(t);
                }else{
                    ret.add(new ValueToken(TokenType.CURRIED_LAMBDA,lambda,t.pos,false));
                }
            }else{
                ret.add(t);
            }
        }
        return ret;
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
                    case LAMBDA, VALUE -> {
                        ValueToken value = (ValueToken) next;
                        if(value.value.type==Type.TYPE){
                            Type type = value.value.asType();
                            if(type instanceof Type.GenericParameter){
                                throw new UnsupportedOperationException("type-checking generics is currently unimplemented");
                            }
                        }
                        stack.push(value.value.type);
                    }
                    case CURRIED_LAMBDA -> {
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
                    case IDENTIFIER ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case CONTEXT_OPEN,CONTEXT_CLOSE -> {

                    }
                    case PUSH_PROC_PTR -> {
                        ProcedureToken token=(ProcedureToken) next;
                        stack.push(token.procedure.type);
                    }
                    case PUSH_NATIVE_PROC_PTR -> {
                        NativeProcedureToken token=(NativeProcedureToken) next;
                        stack.push(token.value.type);
                    }
                    case CALL_NATIVE_PROC ,CALL_PROC, CALL_PTR -> {
                        Type called;
                        if(next.tokenType==TokenType.CALL_PROC){
                            called=((ProcedureToken) next).procedure.type;
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
                    case BLOCK_TOKEN ->
                        //TODO blocks
                        throw new UnsupportedOperationException("blocks unimplemented");
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
                    case LAMBDA,VALUE -> {
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
                    case CURRIED_LAMBDA -> {
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
                                Type type=asVar.id.type;
                                if(type == null){
                                    type = stack.pop().asType();
                                }
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
                    case IDENTIFIER ->
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
                        stack.push(token.procedure);
                    }
                    case PUSH_NATIVE_PROC_PTR -> {
                        NativeProcedureToken token=(NativeProcedureToken) next;
                        stack.push(token.value);
                    }
                    case CALL_NATIVE_PROC ,CALL_PROC, CALL_PTR -> {
                        Value called;
                        if(next.tokenType==TokenType.CALL_PROC){
                            called=((ProcedureToken) next).procedure;
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
                    case BLOCK_TOKEN -> {
                        switch(((BlockToken)next).blockType){
                            case IF,_IF,DO -> {
                                Value c = stack.pop();
                                if (!c.asBool()) {
                                    ip+=((BlockToken) next).delta;
                                    incIp = false;
                                }
                            }
                            case DO_WHILE -> {
                                Value c = stack.pop();
                                if (c.asBool()) {
                                    ip+=((BlockToken) next).delta;
                                    incIp = false;
                                }
                            }
                            case ELSE,END_WHILE,END_CASE -> {
                                ip+=((BlockToken) next).delta;
                                incIp = false;
                            }
                            case WHILE,END_IF -> {
                                //do nothing
                            }
                            case SWITCH,CASE,DEFAULT,END,LIST,END_LIST ->
                                throw new RuntimeException("blocks of type "+((BlockToken)next).blockType+
                                        " should be eliminated at compile time");
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
