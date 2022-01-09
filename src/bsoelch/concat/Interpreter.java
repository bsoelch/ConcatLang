package bsoelch.concat;

import bsoelch.concat.streams.FileStream;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Interpreter {
    public static final String DEFAULT_FILE_EXTENSION = "concat";
    public static final String END_OF_FILE = "##";

    enum WordState{
        ROOT,STRING,COMMENT,LINE_COMMENT
    }

    //TODO? rework string to be more closely relate to its bytes
    // - string => byte list  (UTF8 encoded bytes)
    // - char => codepoint  + library methods for codepoint handling

    enum TokenType {
        VALUE,OPERATOR,
        DROP,STACK_GET,STACK_SET, STACK_SLICE_GET,STACK_SLICE_SET,
        DECLARE,CONST_DECLARE, IDENTIFIER,MACRO_EXPAND,VAR_WRITE,//addLater option to free values/variables
        VARIABLE,
        PLACEHOLDER,//placeholder token for jumps
        RETURN, SKIP_PROCEDURE,
        PRINT,PRINTLN,//addLater move print to concat
        SHORT_AND_JMP, SHORT_OR_JMP,JEQ,JNE,JMP,//jump commands only for internal representation
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
        IdentifierToken(TokenType type, String name, FilePosition pos) throws SyntaxError {
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
        ValueToken(Value value, FilePosition pos) {
            super(TokenType.VALUE, pos);
            this.value=value;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+value;
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
    static class VariableToken extends Token{
        final VariableType variableType;
        final AccessType accessType;
        final VariableId id;
        final String variableName;
        VariableToken(FilePosition pos,String name,VariableId id,AccessType access,VariableContext currentContext) throws SyntaxError {
            super(TokenType.VARIABLE, pos);
            this.variableName=name;
            this.id=id;
            if(id.context instanceof RootContext){
                variableType=VariableType.GLOBAL;
            }else if(id.context==currentContext){
                variableType=VariableType.LOCAL;
            }else{
                variableType=VariableType.CURRIED;
            }
            this.accessType=access;
            if(variableType==VariableType.CURRIED){
                switch (accessType){
                    case READ -> {}
                    case WRITE ->
                            throw new SyntaxError("cannot write to curried variable "+name,pos);
                    case DECLARE,CONST_DECLARE ->
                            throw new RuntimeException("cannot declare to curried variables "+name);
                    default -> throw new RuntimeException("unreachable");
                }
            }else if(access==AccessType.WRITE){
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

    enum BlockType{
        PROCEDURE,IF,WHILE,SHORT_AND,SHORT_OR
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
        final ProcedureContext context;
        ProcedureBlock(int startToken,FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.PROCEDURE,pos, parentContext);
            context=new ProcedureContext(parentContext);
        }
        @Override
        VariableContext context() {
            return context;
        }
    }
    private record IfBranch(int fork,int end){}
    //FIXME handling of variables in if- and while-blocks
    // - variables that have to be declared in all branches (including else)
    //   of an if-else-statement, or the condition part of conditional-statement
    //   keep existing after end
    // - other variables declared in if &&,|| or while statements
    //   cannot be accessed outside the statement
    static class IfBlock extends CodeBlock{
        ArrayList<IfBranch> branches=new ArrayList<>();
        int forkPos=-1;
        int elsePos=-1;
        IfBlock(int startToken,FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.IF,pos, parentContext);
        }
        void end(FilePosition pos) throws SyntaxError {
            if(forkPos==-1&&elsePos==-1){
                throw new SyntaxError("unexpected 'end' in if-statement 'end' can only appear after ':' or 'else'",pos);
            }
        }
        void fork(int tokenPos,FilePosition pos) throws SyntaxError {
            if(elsePos!=-1||forkPos!=-1){
                throw new SyntaxError("unexpected ':' in if-statement ':' can only appear after 'if' or 'elif'",pos);
            }
            forkPos=tokenPos;
        }
        void newBranch(int tokenPos,FilePosition pos) throws SyntaxError{
            if(forkPos==-1||elsePos!=-1){
                throw new SyntaxError("unexpected 'if' or 'elif' in if-statement 'if'/'elif'" +
                        " can only appear after ':'",pos);
            }
            branches.add(new IfBranch(forkPos,tokenPos));
            forkPos=-1;
        }
        void elseBranch(int tokenPos,FilePosition pos) throws SyntaxError {
            if(elsePos!=-1){
                throw new SyntaxError("duplicate 'else' in if-statement 'else' can appear at most once",pos);
            }else if(forkPos==-1){
                throw new SyntaxError("unexpected 'else' in if-statement 'else' can only appear after ':'",pos);
            }
            branches.add(new IfBranch(forkPos,tokenPos));
            forkPos=-1;
            elsePos=tokenPos;
        }

        @Override
        VariableContext context() {
            //TODO implement IfBlock.context
            return parentContext;
        }
    }
    static class ShortCircuitBlock extends CodeBlock{
        int forkPos=-1;
        ShortCircuitBlock(int startToken,FilePosition pos,boolean isAnd, VariableContext parentContext) {
            super(startToken, isAnd?BlockType.SHORT_AND:BlockType.SHORT_OR,pos, parentContext);
        }
        void end(FilePosition pos) throws SyntaxError {
            if(forkPos==-1){
                throw new SyntaxError("unexpected 'end' in short-circuit-statement " +
                        "'end' can only appear after ':'",pos);
            }
        }
        void fork(int tokenPos,FilePosition pos) throws SyntaxError{
            if(forkPos!=-1){
                throw new SyntaxError("duplicate ':' in short-circuit-statement ':' " +
                        "can only appear after '&&' or '||'",pos);
            }
            forkPos=tokenPos;
        }

        @Override
        VariableContext context() {
            //TODO implement ShortCircuitBlock.context
            return parentContext;
        }
    }
    static class WhileBlock extends CodeBlock{
        int forkPos=-1;
        WhileBlock(int startToken,FilePosition pos, VariableContext parentContext) {
            super(startToken, BlockType.WHILE,pos, parentContext);
        }
        void end(FilePosition pos) throws SyntaxError {
            if(forkPos==-1){
                throw new SyntaxError("unexpected 'end' in while-statement " +
                        "'end' can only appear after ':'",pos);
            }
        }
        void fork(int tokenPos,FilePosition pos) throws SyntaxError {
            if(forkPos!=-1){
                throw new SyntaxError("duplicate ':' in while-statement ':' " +
                        "can only appear after 'while'",pos);
            }
            forkPos=tokenPos;
        }

        @Override
        VariableContext context() {
            //TODO implement WhileBlock.context
            return parentContext;
        }
    }

    private Interpreter() {}

    static String libPath;

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
        int id;
        final boolean isConstant;
        final FilePosition declaredAt;
        FilePosition initializedAt;
        VariableId(VariableContext context,int id,boolean predeclared,boolean isConstant,
                   FilePosition declaredAt,FilePosition initializedAt){
            this.predeclared=predeclared;
            this.context=context;
            this.id=id;
            this.isConstant=isConstant;
            this.declaredAt=declaredAt;
            this.initializedAt=initializedAt;
        }
        @Override
        public String toString() {
            return "@"+context+"."+id;
        }
    }
    private static class PredeclaredVariable extends VariableId{
        PredeclaredVariable(VariableContext context,FilePosition declaredAt) {
            super(context, -1, true,true, declaredAt,null);
        }
        void initialize(int id,FilePosition initializedAt){
            this.id=id;
            this.initializedAt=initializedAt;
        }
    }

    static abstract class VariableContext{
        final HashMap<String,VariableId> variables=new HashMap<>();
        VariableId declareVariable(String name,boolean isConstant,FilePosition pos) throws SyntaxError {
            VariableId prev=variables.get(name);
            if(prev!=null){
                // TODO always throw an error when branch-handling is implemented
                // throw new SyntaxError("variable "+name+" already exists",pos);
                if(prev.isConstant){
                    throw new SyntaxError("cannot overwrite constant variable "+name,pos);
                }else if(isConstant){
                    throw new SyntaxError("constant cannot overwrite variable "+name,pos);
                }
                return prev;
            }
            VariableId id = new VariableId(this, variables.size(), false,isConstant, pos,pos);
            variables.put(name, id);
            return id;
        }
        abstract VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError;
        abstract VariableId unsafeGetId(String name);
    }
    private static class RootContext extends VariableContext{
        RootContext(){}
        final HashMap<String,PredeclaredVariable> predeclared=new HashMap<>();
        @Override
        VariableId declareVariable(String name,boolean isConstant,FilePosition pos) throws SyntaxError {
            PredeclaredVariable predeclared = this.predeclared.remove(name);
            if(predeclared!=null){//initialize predeclared variable
                if(!isConstant){
                    throw new SyntaxError("predeclared variables have to be constants",predeclared.declaredAt);
                }
                predeclared.initialize(variables.size(),  pos);
                variables.put(name,predeclared);
                return predeclared;
            }
            //addLater handling of modules
            return super.declareVariable(name, isConstant, pos);
        }

        @Override
        VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError {
            VariableId id=variables.get(name);
            if(id==null){
                id=predeclared.get(name);
                if(id==null){
                    if(callee instanceof ProcedureContext){
                        id=new PredeclaredVariable(this,pos);
                        predeclared.put(name,(PredeclaredVariable)id);
                    }else{
                        throw new SyntaxError("Variable "+name+" does not exist",pos);
                    }
                }
            }
            return id;
        }
        @Override
        VariableId unsafeGetId(String name) {
            VariableId id=variables.get(name);
            if(id==null){
                id=predeclared.get(name);
            }
            return id;
        }
    }
    private static class ProcedureContext extends VariableContext{
        final VariableContext parent;
        ProcedureContext(VariableContext parent){
            assert parent!=null;
            this.parent=parent;
        }
        @Override
        VariableId declareVariable(String name,boolean isConstant,FilePosition pos) throws SyntaxError {
            VariableId id=super.declareVariable(name, isConstant, pos);
            VariableId shadowed=parent.unsafeGetId(name);
            if(shadowed!=null){//check for shadowing
                if(shadowed.initializedAt==null){
                    throw new SyntaxError("unexpected initialization of predeclared constant "+name+
                            ", predeclared constants have to be initialized at global level " +
                            "\n  ("+name+" was declared at "+shadowed.declaredAt+")",pos);
                }
                System.err.println("Warning: variable "+name+" at declared at "+pos+
                        "\n     shadows existing "+ (shadowed.isConstant?"constant":"variable")+" declared at "
                        +shadowed.declaredAt);
            }
            return id;
        }

        @Override
        VariableId unsafeGetId(String name) {
            VariableId id=variables.get(name);
            return id == null ?parent.unsafeGetId(name):id;
        }
        @Override
        VariableId getId(String name,FilePosition pos,VariableContext callee) throws SyntaxError {
            VariableId id=variables.get(name);
            if(id == null){
                id=parent.getId(name,pos,callee);
                if(id!=null&&!id.isConstant){
                    throw new SyntaxError("external variable "+name+" is not constant",pos);
                }
            }
            return id;
        }
    }

    interface CodeSection{
        ArrayList<Token> tokens();
        VariableContext context();
    }
    record Program(ArrayList<Token> tokens, HashSet<String> files, HashMap<String,Macro> macros,
                   RootContext rootContext) implements CodeSection{
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

    public Program parse(File file,Program program) throws IOException, SyntaxError {
        String fileName=file.getAbsolutePath();
        if(program==null){
            program=new Program(new ArrayList<>(),new HashSet<>(),new HashMap<>(),new RootContext());
        }else if(program.files.contains(file.getAbsolutePath())){
            return program;
        }else{//ensure that each file is included only once
            program.files.add(file.getAbsolutePath());
        }
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
                            finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);
                        }
                        reader.nextToken();
                    }else{
                        switch (c) {
                            case '"', '\'' -> {
                                state = WordState.STRING;
                                if(reader.buffer.length()>0) {
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
                                        finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);
                                    }
                                    reader.nextToken();
                                } else if (c == '_') {
                                    state = WordState.COMMENT;
                                    current=next;
                                    next=reader.buffer.toString();
                                    if(current!=null){
                                        finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);
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
                case STRING:
                    if(c==reader.buffer.charAt(0)){
                        current=next;
                        next=reader.buffer.toString();
                        if(current!=null){
                            finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);
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
                    finishWord(current,next,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);
                }
                reader.nextToken();
            }
            case LINE_COMMENT ->{} //do nothing
            case STRING ->throw new SyntaxError("unfinished string", reader.currentPos());
            case COMMENT -> throw new SyntaxError("unfinished comment", reader.currentPos());
        }
        //finish parsing of all elements
        finishWord(next,END_OF_FILE,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);
        finishWord(END_OF_FILE,END_OF_FILE,program.tokens,openBlocks,currentMacroPtr,reader.currentPos(),program);

        if(openBlocks.size()>0){
            throw new SyntaxError("unclosed block: "+openBlocks.getLast(),openBlocks.getLast().startPos);
        }
        if((program.rootContext).predeclared.size()>0){
            System.err.println("Syntax Error: File "+fileName+" contains uninitialized variables");
            for(Map.Entry<String, PredeclaredVariable> p:(program.rootContext).predeclared.entrySet()){
                System.err.println("- "+p.getKey()+" (declared at "+p.getValue().declaredAt+")");
            }
            System.exit(1);
        }
        return program;
    }

    /**@return false if the value was an integer otherwise true*/
    private boolean tryParseInt(ArrayList<Token> tokens, String str,FilePosition pos) throws SyntaxError {
        try {
            if(intDec.matcher(str).matches()){//dez-Int
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 10)), pos));
                return false;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 2)),  pos));
                return false;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 16)), pos));
                return false;
            }
        } catch (NumberFormatException nfeL) {
            throw new SyntaxError("Number out of Range:"+str,pos);
        }
        return true;
    }

    //TODO reintroduce modules
    // - now modules as "preprocessor" commands
    // - syntax: <name> (<name> '.')+ #module ... #end
    // - variables/constants/macros are saved in the scope of the current module
    // - module element access though <name> (<name> '.')+
    // - imports: <name> (<name> '.')+ #import imports a complete module or a constants into the current scope
    // - when parsing a variable name the order is: current module > latest import > ... > first import > global

    private void finishWord(String str,String next, ArrayList<Token> tokens, ArrayDeque<CodeBlock> openBlocks,
                            Macro[] currentMacroPtr, FilePosition pos, Program program) throws SyntaxError, IOException {
        if (str.length() > 0) {
            if(currentMacroPtr[0]!=null){//handle macros
                if(str.equals("#end")){
                    program.macros.put(currentMacroPtr[0].name,currentMacroPtr[0]);
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
                        if(prevId!=null){
                            tokens.remove(tokens.size()-1);
                            currentMacroPtr[0]=new Macro(pos,prevId,new ArrayList<>());
                        }else{
                            throw new SyntaxError("invalid token preceding #define "+prev+" expected identifier",pos);
                        }
                        return;
                    }
                    case "#undef"->{
                        if(prev!=null&&prev.tokenType==TokenType.MACRO_EXPAND){
                            tokens.remove(tokens.size()-1);
                            if(program.macros.remove(((IdentifierToken)prev).name)==null){
                                throw new RuntimeException("macro "+prev+" does not exists");
                            }
                        }else{
                            throw new SyntaxError("invalid token preceding #undef "+prev+" expected macro-name",pos);
                        }
                        return;
                    }
                    case "#end"-> throw new SyntaxError("#end outside of macro",pos);
                    case "#include" -> {
                        if(prev instanceof ValueToken){
                            tokens.remove(tokens.size()-1);
                            String name=((ValueToken) prev).value.stringValue();
                            File file=new File(name);
                            if(file.exists()){
                                parse(file,program);
                            }else{
                                throw new SyntaxError("File "+name+" does not exist",pos);
                            }
                        }else if(prevId != null){
                            tokens.remove(tokens.size()-1);
                            String path=libPath+File.separator+ prevId + "." + DEFAULT_FILE_EXTENSION;
                            File file=new File(path);
                            if(file.exists()){
                                parse(file,program);
                            }else{
                                throw new SyntaxError(prevId+" is not part of the standard library",pos);
                            }
                        }else{
                            throw new UnsupportedOperationException("include path has to be a string literal or identifier");
                        }
                        return;
                    }
                    case "="->{
                        if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_INDEX){
                            //<array> <val> <index> [] =
                            tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_INDEX,prev.pos));
                        }else if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_SLICE){
                            //<array> <val> <off> <to> [:] =
                            tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_SLICE,prev.pos));
                        }else if(prev!=null&&prev.tokenType==TokenType.STACK_GET){
                            //<val> <index> :[] =
                            tokens.set(tokens.size()-1,new Token(TokenType.STACK_SET,prev.pos));
                        }else if(prev!=null&&prev.tokenType==TokenType.STACK_SLICE_GET){
                            //<val> <off> <to> :[:] =
                            tokens.set(tokens.size()-1,new Token(TokenType.STACK_SLICE_SET,prev.pos));
                        }else if(prev instanceof IdentifierToken){
                            if(prev.tokenType == TokenType.IDENTIFIER){
                                prev=new IdentifierToken(TokenType.VAR_WRITE,((IdentifierToken) prev).name,prev.pos);
                            }else{
                                throw new SyntaxError("invalid token for '=' modifier: "+prev,pos);
                            }
                            tokens.set(tokens.size()-1,prev);
                        }else{
                            throw new SyntaxError("invalid token for '=' modifier: "+prev,pos);
                        }
                        return;
                    }
                    case "."->{
                        Token prePrev=tokens.size()>=2?tokens.get(tokens.size()-2):null;
                        if(prevId!=null&&prePrev!=null&&prePrev.tokenType==TokenType.IDENTIFIER){
                            tokens.remove(tokens.size()-1);
                            prev=new IdentifierToken(TokenType.IDENTIFIER,((IdentifierToken)prePrev).name+
                                    "'"+prevId,prev.pos);//use ' as separator for modules, as it cannot be part of identifiers
                        }else{
                            throw new SyntaxError("invalid token for '.' modifier: "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=:"->{
                        if(prevId!=null){
                            prev=new IdentifierToken(TokenType.DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=:' modifier "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=$"->{
                        if(prevId!=null){
                            prev=new IdentifierToken(TokenType.CONST_DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=$' modifier: "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                }
                if(prev!=null){
                    if(prev.tokenType==TokenType.MACRO_EXPAND) {
                        tokens.remove(tokens.size() - 1);//remove prev
                        Macro m = program.macros.get(((IdentifierToken) prev).name);
                        for(int i=0;i<m.content.size();i++){
                            StringWithPos s=m.content.get(i);
                            finishWord(s.str,i+1<m.content.size()?m.content.get(i+1).str:"##"
                                    ,tokens, openBlocks, currentMacroPtr, new FilePosition(s.start, pos), program);
                        }
                        //update identifiers at end of macro
                        finishWord("##",next,tokens, openBlocks, currentMacroPtr, pos, program);
                    }else if((!(next.equals(".")))&& prev instanceof IdentifierToken identifier){
                        int index=tokens.size()-1;
                        VariableContext context=getContext(openBlocks.peekLast(),program.rootContext);
                        //update variables
                        switch (identifier.tokenType){
                            case DECLARE,CONST_DECLARE -> {
                                VariableId id=context.declareVariable(
                                        identifier.name, identifier.tokenType == TokenType.CONST_DECLARE,
                                        identifier.pos);
                                AccessType accessType =
                                        identifier.tokenType ==
                                                TokenType.CONST_DECLARE ? AccessType.CONST_DECLARE : AccessType.DECLARE;
                                tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                        accessType,context));
                            }
                            case IDENTIFIER -> {
                                VariableId id=context.getId(identifier.name, identifier.pos,context);
                                tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                        AccessType.READ,context));
                            }
                            case VAR_WRITE -> {
                                VariableId id=context.getId(identifier.name, identifier.pos,context);
                                if(id instanceof PredeclaredVariable){
                                    tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                            AccessType.READ,context));
                                }else{
                                    tokens.set(index,new VariableToken(identifier.pos,identifier.name,id,
                                            AccessType.WRITE,context));
                                }
                            }
                            default -> throw new RuntimeException("unexpected type of IdentifierToken:"+ identifier.tokenType);
                        }
                    }
                }
            }
            if(str.charAt(0)=='\''){//char literal
                str=str.substring(1);
                if(str.codePoints().count()==1){
                    int codePoint = str.codePointAt(0);
                    tokens.add(new ValueToken(Value.ofChar(codePoint), pos));
                }else{
                    throw new SyntaxError("A char-literal must contain exactly one character", pos);
                }
                return;
            }else if(str.charAt(0)=='"'){
                str=str.substring(1);
                tokens.add(new ValueToken(Value.ofString(str),  pos));
                return;
            }
            try{
                if (tryParseInt(tokens, str,pos)) {
                    if(floatDec.matcher(str).matches()){
                        //dez-Float
                        double d = Double.parseDouble(str);
                        tokens.add(new ValueToken(Value.ofFloat(d), pos));
                    }else if(floatBin.matcher(str).matches()){
                        //bin-Float
                        double d= Value.parseFloat(str.substring(BIN_PREFIX.length()),2);
                        tokens.add(new ValueToken(Value.ofFloat(d), pos));
                    }else if(floatHex.matcher(str).matches()){
                        //hex-Float
                        double d=Value.parseFloat(str.substring(BIN_PREFIX.length()),16);
                        tokens.add(new ValueToken(Value.ofFloat(d), pos));
                    }else {
                        addWord(str,tokens,openBlocks,program.macros,pos,program.rootContext);
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

    private void addWord(String str, ArrayList<Token> tokens, ArrayDeque<CodeBlock> openBlocks, HashMap<String, Macro> macros,
                         FilePosition pos,VariableContext rootContext) throws SyntaxError {
        switch (str) {
            case END_OF_FILE -> {} //## string can only be passed to the method on end of file
            case "true"  -> tokens.add(new ValueToken(Value.TRUE,    pos));
            case "false" -> tokens.add(new ValueToken(Value.FALSE,   pos));

            case "bool"     -> tokens.add(new ValueToken(Value.ofType(Type.BOOL),          pos));
            case "byte"     -> tokens.add(new ValueToken(Value.ofType(Type.BYTE),          pos));
            case "int"      -> tokens.add(new ValueToken(Value.ofType(Type.INT),           pos));
            case "char"     -> tokens.add(new ValueToken(Value.ofType(Type.CHAR),          pos));
            case "float"    -> tokens.add(new ValueToken(Value.ofType(Type.FLOAT),         pos));
            case "string"   -> tokens.add(new ValueToken(Value.ofType(Type.STRING()),      pos));
            case "type"     -> tokens.add(new ValueToken(Value.ofType(Type.TYPE),          pos));
            case "list"     -> tokens.add(new OperatorToken(OperatorType.LIST_OF,          pos));
            case "content"  -> tokens.add(new OperatorToken(OperatorType.CONTENT,          pos));
            case "*->*"     -> tokens.add(new ValueToken(Value.ofType(Type.PROCEDURE),     pos));
            case "var"      -> tokens.add(new ValueToken(Value.ofType(Type.ANY),           pos));
            case "tuple"    -> tokens.add(new OperatorToken(OperatorType.TUPLE,            pos));
            case "(list)"   -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_LIST),  pos));
            case "(tuple)"  -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_TUPLE), pos));
            case "(file)"   -> tokens.add(new ValueToken(Value.ofType(Type.FILE),          pos));

            case "cast"   ->  tokens.add(new OperatorToken(OperatorType.CAST,    pos));
            case "typeof" ->  tokens.add(new OperatorToken(OperatorType.TYPE_OF, pos));

            case "drop" -> tokens.add(new Token(TokenType.DROP,            pos));
            //<index> :[]
            case ":[]"  -> tokens.add(new Token(TokenType.STACK_GET,       pos));
            //<off> <to> :[]
            case ":[:]" -> tokens.add(new Token(TokenType.STACK_SLICE_GET, pos));

            case "refId"  -> tokens.add(new OperatorToken(OperatorType.REF_ID,     pos));

            case "clone"  -> tokens.add(new OperatorToken(OperatorType.CLONE,      pos));
            case "clone!" -> tokens.add(new OperatorToken(OperatorType.DEEP_CLONE, pos));

            case "print"      -> tokens.add(new Token(TokenType.PRINT,   pos));
            case "println"    -> tokens.add(new Token(TokenType.PRINTLN, pos));

            case "intAsFloat"   -> tokens.add(new OperatorToken(OperatorType.INT_AS_FLOAT, pos));
            case "floatAsInt"   -> tokens.add(new OperatorToken(OperatorType.FLOAT_AS_INT, pos));

            case "proc","procedure" ->
                    openBlocks.add(new ProcedureBlock(tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "while" -> openBlocks.add(new WhileBlock(tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "&&" -> openBlocks.addLast(new ShortCircuitBlock(tokens.size(),pos,true,
                    getContext(openBlocks.peekLast(),rootContext)));
            case "||" -> openBlocks.addLast(new ShortCircuitBlock(tokens.size(),pos,false,
                    getContext(openBlocks.peekLast(),rootContext)));
            case "if" -> openBlocks.addLast(new IfBlock(tokens.size(),pos,getContext(openBlocks.peekLast(),rootContext)));
            case "elif" -> {
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof IfBlock ifBlock)){
                    throw new SyntaxError("elif can only be used in if-blocks",pos);
                }
                ifBlock.newBranch(tokens.size(),pos);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
            }
            case "else" -> {
                CodeBlock block = openBlocks.peekLast();
                if(!(block instanceof IfBlock ifBlock)){
                    throw new SyntaxError("elif can only be used in if-blocks",pos);
                }
                ifBlock.elseBranch(tokens.size(),pos);
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
            }
            case ":" -> {
                CodeBlock block=openBlocks.peekLast();
                if(block==null){
                    throw new SyntaxError(": can only be used in if-, while-, &&- and  ||- blocks",pos);
                }else{
                    switch (block.type){
                        case IF -> ((IfBlock)block).fork(tokens.size(),pos);
                        case SHORT_AND,SHORT_OR -> ((ShortCircuitBlock)block).fork(tokens.size(),pos);
                        case WHILE -> ((WhileBlock)block).fork(tokens.size(),pos);
                        default ->
                                throw new SyntaxError(": can only be used in if-, while-, &&- and  ||- blocks",pos);
                    }
                }
                tokens.add(new Token(TokenType.PLACEHOLDER, pos));
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
                            tokens.add(new ValueToken(Value.createProcedure(block.start,content, block.context()),pos));
                        }
                        case IF -> {
                            ((IfBlock)block).end(pos);
                            for(IfBranch branch:((IfBlock) block).branches){
                                tmp=tokens.get(branch.fork);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(branch.fork,new RelativeJump(TokenType.JNE,tmp.pos,branch.end-branch.fork+1));
                                tmp=tokens.get(branch.end);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(branch.end,new RelativeJump(TokenType.JMP,tmp.pos,tokens.size()-branch.end));
                            }
                            if(((IfBlock) block).forkPos!=-1){
                                tmp=tokens.get(((IfBlock) block).forkPos);
                                assert tmp.tokenType==TokenType.PLACEHOLDER;
                                tokens.set(((IfBlock) block).forkPos,new RelativeJump(TokenType.JNE,tmp.pos,
                                        tokens.size()-((IfBlock) block).forkPos));
                            }
                        }
                        case WHILE -> {
                            ((WhileBlock)block).end(pos);
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
                            tmp=tokens.get(((ShortCircuitBlock) block).forkPos);
                            assert tmp.tokenType==TokenType.PLACEHOLDER;
                            tokens.set(((ShortCircuitBlock) block).forkPos,new RelativeJump(TokenType.SHORT_AND_JMP,tmp.pos,
                                    tokens.size()-((ShortCircuitBlock) block).forkPos));
                        }
                        case SHORT_OR -> {
                            ((ShortCircuitBlock)block).end(pos);
                            tmp=tokens.get(((ShortCircuitBlock) block).forkPos);
                            assert tmp.tokenType==TokenType.PLACEHOLDER;
                            tokens.set(((ShortCircuitBlock) block).forkPos,new RelativeJump(TokenType.SHORT_OR_JMP,tmp.pos,
                                    tokens.size()-((ShortCircuitBlock) block).forkPos));
                        }
                    }
                }
            }
            case "return" -> tokens.add(new Token(TokenType.RETURN,  pos));
            case "exit"   -> tokens.add(new Token(TokenType.EXIT,  pos));

            case "+"   -> tokens.add(new OperatorToken(OperatorType.PLUS,          pos));
            case "-"   -> tokens.add(new OperatorToken(OperatorType.MINUS,         pos));
            case "-_"  -> tokens.add(new OperatorToken(OperatorType.NEGATE,        pos));
            case "/_"  -> tokens.add(new OperatorToken(OperatorType.INVERT,        pos));
            case "*"   -> tokens.add(new OperatorToken(OperatorType.MULTIPLY,      pos));
            case "/"   -> tokens.add(new OperatorToken(OperatorType.DIV,           pos));
            case "%"   -> tokens.add(new OperatorToken(OperatorType.MOD,           pos));
            case "u/"   -> tokens.add(new OperatorToken(OperatorType.UNSIGNED_DIV, pos));
            case "u%"   -> tokens.add(new OperatorToken(OperatorType.UNSIGNED_MOD, pos));
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

            case ">>:" -> tokens.add(new OperatorToken(OperatorType.PUSH_FIRST,     pos));
            case ":<<" -> tokens.add(new OperatorToken(OperatorType.PUSH_LAST,      pos));
            case "+:"  -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_FIRST, pos));
            case ":+"  -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_LAST,  pos));
            //<array> <index> []
            case "[]"   -> tokens.add(new OperatorToken(OperatorType.GET_INDEX,  pos));
            //<array> <off> <to> [:]
            case "[:]"  -> tokens.add(new OperatorToken(OperatorType.GET_SLICE,  pos));

            //<e0> ... <eN> <N> {}
            case "{}"     -> tokens.add(new OperatorToken(OperatorType.NEW_LIST, pos));
            case "length" -> tokens.add(new OperatorToken(OperatorType.LENGTH,   pos));
            case "()"     -> tokens.add(new OperatorToken(OperatorType.CALL,     pos));

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

            default -> tokens.add(new IdentifierToken(macros.containsKey(str)?TokenType.MACRO_EXPAND:TokenType.IDENTIFIER,str, pos));
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

    public RandomAccessStack<Value> run(Program program){
        RandomAccessStack<Value> stack=new RandomAccessStack<>(16);
        recursiveRun(stack,program,null);
        return stack;
    }

    private ExitType recursiveRun(RandomAccessStack<Value> stack,
                                                  CodeSection program,Variable[] globalVariables){
        Variable[] variables=new Variable[program.context().variables.size()];
        int ip=0;
        ArrayList<Token> tokens=program.tokens();
        while(ip<tokens.size()){
            Token next=tokens.get(ip);
            boolean incIp=true;
            try {
                switch (next.tokenType) {
                    case VALUE -> stack.push(((ValueToken) next).value.clone(true));
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
                                stack.push(Value.intOp(a, b, (x, y) -> y < 0 ? x >> -y : slshift(x, y)));
                            }
                            case SRSHIFT -> {
                                Value b = stack.pop();
                                Value a = stack.pop();
                                stack.push(Value.intOp(a, b, (x, y) -> y < 0 ? slshift(x, -y) : x >> y));
                            }
                            case LIST_OF -> {
                                Type contentType = stack.pop().asType();
                                stack.push(Value.ofType(Type.listOf(contentType)));
                            }
                            case CONTENT -> {
                                Type wrappedType = stack.pop().asType();
                                stack.push(Value.ofType(wrappedType.content()));
                            }
                            case NEW_LIST -> {//e1 e2 ... eN type count {}
                                long count = stack.pop().asLong();
                                Type type = stack.pop().asType();
                                if(count<0){
                                    throw new ConcatRuntimeError("he element count has to be at least 0");
                                }else if(count>Integer.MAX_VALUE){
                                    throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
                                }
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
                                if(count<0){
                                    throw new ConcatRuntimeError("he element count has to be at least 0");
                                }else if(count>Integer.MAX_VALUE){
                                    throw new ConcatRuntimeError("the maximum allowed capacity for arrays is "+Integer.MAX_VALUE);
                                }
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
                            case CALL -> {
                                Value.Procedure procedure =(Value.Procedure)stack.pop();
                                ExitType e=recursiveRun(stack,procedure,globalVariables==null?variables:globalVariables);
                                if(e!=ExitType.NORMAL){
                                    if(e==ExitType.ERROR) {
                                        System.err.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                                    }
                                    return e;
                                }
                            }
                            case INT_AS_FLOAT -> stack.push(Value.ofFloat(Double.longBitsToDouble(stack.pop().asLong())));
                            case FLOAT_AS_INT -> stack.push(Value.ofInt(Double.doubleToRawLongBits(stack.pop().asDouble())));
                            case OPEN -> {
                                String options = stack.pop().stringValue();
                                String path    = stack.pop().stringValue();
                                stack.push(Value.ofFile(new FileStream(path,options)));
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
                        }
                    }
                    case PRINT -> System.out.print(stack.pop().stringValue());
                    case PRINTLN -> System.out.println(stack.pop().stringValue());
                    case DROP -> stack.pop();
                    case STACK_GET -> {
                        long index = stack.pop().asLong();
                        if(index<0||index>=stack.size()){
                            throw new ConcatRuntimeError("index out of bounds:"+index+" size:"+stack.size());
                        }
                        stack.push(stack.get((int)index+1));
                    }
                    case STACK_SET -> {
                        long index = stack.pop().asLong();
                        Value val=stack.pop();
                        if(index<0||index>=stack.size()){
                            throw new ConcatRuntimeError("index out of bounds:"+index+" size:"+stack.size());
                        }
                        stack.set((int)index+1,val);
                    }
                    case STACK_SLICE_GET -> {
                        long upper  = stack.pop().asLong();
                        long lower  = stack.pop().asLong();
                        stack.push(Value.newStackSlice(stack,lower,upper));
                    }
                    case STACK_SLICE_SET -> {
                        long upper  = stack.pop().asLong();
                        long lower  = stack.pop().asLong();
                        Value val=stack.pop();
                        if(lower<0||upper>stack.size()||lower>upper){
                            throw new ConcatRuntimeError("invalid stack-slice: "+lower+":"+upper+" length:"+stack.size());
                        }
                        stack.setSlice((int)upper,(int)lower,val.getElements());
                    }
                    case VARIABLE -> {
                        VariableToken asVar=(VariableToken) next;
                        switch (asVar.accessType){
                            case READ -> {
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            stack.push((globalVariables==null?variables:globalVariables)[asVar.id.id].value);
                                    case LOCAL -> {
                                        if (globalVariables != null) {
                                            stack.push(variables[asVar.id.id].value);
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            throw new ConcatRuntimeError("curried variables are currently unimplemented");
                                }
                            }
                            case WRITE -> {
                                Value newValue=stack.pop();
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            (globalVariables==null?variables:globalVariables)[asVar.id.id].setValue(newValue);
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables[asVar.id.id].setValue(newValue);
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
                                            (globalVariables==null?variables:globalVariables)[asVar.id.id] =
                                                    new Variable(type, isConst, initValue);
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables[asVar.id.id] = new Variable(type, isConst, initValue);
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
                    case DECLARE, CONST_DECLARE,IDENTIFIER,VAR_WRITE,MACRO_EXPAND, PLACEHOLDER ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case RETURN -> {
                        return ExitType.NORMAL;
                    }
                    case JMP, SKIP_PROCEDURE -> {
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
                    case EXIT -> {
                        long exitCode=stack.pop().asLong();
                        System.out.println("exited with exit code:"+exitCode);
                        return ExitType.FORCED;
                    }
                }
            }catch(ConcatRuntimeError|RandomAccessStack.StackUnderflow  e){
                System.err.println(e.getMessage());
                Token token = tokens.get(ip);
                System.err.printf("  while executing %-20s\n   at %s\n",token,token.pos);
                break;
            }
            if(incIp){
                ip++;
            }
        }
        return ExitType.NORMAL;
    }

    private long slshift(long x, long y) {
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
        }else{
            libPath=System.getProperty("user.dir")+File.separator+"lib/";
        }
        File libDir=new File(libPath);
        if(!(libDir.exists()||libDir.mkdirs())){
            System.out.println(libPath+"  is no valid library path");
            return;
        }
        Interpreter ip = new Interpreter();
        Program program;
        try {
            program = ip.parse(new File(path),null);
        }catch (SyntaxError e){
            SyntaxError s = e;
            System.err.println(s.getMessage());
            System.err.println("  at "+ s.pos);
            while(s.getCause() instanceof SyntaxError){
                s =(SyntaxError) s.getCause();
                System.err.println("  at "+ s.pos);
            }
            System.exit(1);
            return;
        }
        RandomAccessStack<Value> stack = ip.run(program);
        System.out.println("\nStack:");
        System.out.println(stack);
    }
}
