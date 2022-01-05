package bsoelch.concat;

import bsoelch.concat.streams.FileStream;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Interpreter {
    public static final String DEFAULT_FILE_EXTENSION = "concat";

    enum WordState{
        ROOT,STRING,COMMENT,LINE_COMMENT
    }

    enum TokenType {
        VALUE,OPERATOR,
        DROP,STACK_GET,STACK_SET, STACK_SLICE_GET,STACK_SLICE_SET,
        DECLARE,CONST_DECLARE, IDENTIFIER,MACRO_EXPAND,VAR_WRITE,HAS_VAR,//addLater option to free variables
        IF,START,ELIF,ELSE,DO,WHILE,END,
        SHORT_AND_HEADER, SHORT_OR_HEADER,SHORT_AND_JMP, SHORT_OR_JMP,
        PROCEDURE,RETURN, SKIP_PROC,
        MODULE_READ_VAR, MODULE_WRITE_VAR, MODULE_HAS_VAR,
        PRINT,PRINTLN,
        JEQ,JNE,JMP,//jump commands only for internal representation
        INCLUDE,
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

    record TokenPosition(String file, int ip) {
        @Override
        public String toString() {
            return ip+" in "+file;
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
    static class VariableToken extends Token {
        final String name;
        VariableToken(TokenType type, String name, FilePosition pos) throws SyntaxError {
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
    static class AbsoluteJump extends Token{
        final TokenPosition target;
        AbsoluteJump(TokenType tokenType, FilePosition pos, TokenPosition target) {
            super(tokenType, pos);
            this.target = target;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+(tokenType== TokenType.INCLUDE?target.file:target);
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
            buffer.setLength(0);
            currentPos = new FilePosition(path, line, posInLine);
        }
    }

    record Program(String mainFile,HashMap<String,ArrayList<Token>> fileTokens,HashMap<String,Macro> macros){
        @Override
        public String toString() {
            return "Program{" +
                    "mainFile='" + mainFile + '\'' +
                    ", fileTokens=" + fileTokens +
                    '}';
        }
    }

    public Program parse(File file,Program program) throws IOException, SyntaxError {
        String fileName=file.getAbsolutePath();
        if(program==null){
            program=new Program(fileName,new HashMap<>(),new HashMap<>());
        }else if(program.fileTokens.containsKey(fileName)){
            return program;
        }
        ParserReader reader=new ParserReader(fileName);
        ArrayList<Token> tokenBuffer=new ArrayList<>();
        TreeMap<Integer,Token> openBlocks=new TreeMap<>();
        Macro[] currentMacroPtr=new Macro[1];
        int c;
        reader.nextToken();
        WordState state=WordState.ROOT;
        while((c=reader.nextChar())>=0){
            switch(state){
                case ROOT:
                    if(Character.isWhitespace(c)){
                        finishWord(reader.buffer,tokenBuffer,openBlocks,currentMacroPtr,reader.currentPos(),program,fileName);
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
                                    finishWord(reader.buffer,tokenBuffer,openBlocks,currentMacroPtr,
                                            reader.currentPos(),program,fileName);
                                    reader.nextToken();
                                } else if (c == '_') {
                                    state = WordState.COMMENT;
                                    finishWord(reader.buffer,tokenBuffer,openBlocks,currentMacroPtr,
                                            reader.currentPos(),program,fileName);
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
                        finishWord(reader.buffer,tokenBuffer,openBlocks,currentMacroPtr,
                                reader.currentPos(),program,fileName);
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
                finishWord(reader.buffer,tokenBuffer,openBlocks,currentMacroPtr,reader.currentPos(),program,fileName);
                reader.nextToken();
            }
            case LINE_COMMENT ->{} //do nothing
            case STRING ->throw new SyntaxError("unfinished string", reader.currentPos());
            case COMMENT -> throw new SyntaxError("unfinished comment", reader.currentPos());
        }
        //pass "##" to finishWord to expand macros at end of file,
        // "##" will normally be eliminated before it reaches this method and therefore does not lead to any problems
        finishWord("##",tokenBuffer,openBlocks,currentMacroPtr,reader.currentPos(),program,fileName);
        if(openBlocks.size()>0){
            throw new SyntaxError("unclosed block: "+openBlocks.lastEntry().getValue(),
                    openBlocks.lastEntry().getValue().pos);
        }
        program.fileTokens.put(fileName,tokenBuffer);
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

    //TODO update variable resolving in procedures
    // new order: local variables > local constants of containing procedure > ... > constants outside of procedure
    // procedures should only be able to access constants of containing contexts
    // when a procedure refers to a constant that cannot be resolved at compile time the
    // resulting procedure pointer contains the value of that constant at the time of declaration

    private void finishWord(CharSequence buffer,ArrayList<Token> tokens,TreeMap<Integer,Token> openBlocks,
                            Macro[] currentMacroPtr,FilePosition pos,Program program,String fileName) throws SyntaxError, IOException {
        if (buffer.length() > 0) {
            String str=buffer.toString();
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
                        ((VariableToken)prev).name:null;
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
                            if(program.macros.remove(((VariableToken)prev).name)==null){
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
                                tokens.add(new AbsoluteJump(TokenType.INCLUDE,pos,
                                        new TokenPosition(file.getAbsolutePath(),0)));
                            }else{
                                throw new SyntaxError("File "+name+" does not exist",pos);
                            }
                        }else if(prevId != null){
                            tokens.remove(tokens.size()-1);
                            String path=libPath+File.separator+ prevId + "." + DEFAULT_FILE_EXTENSION;
                            File file=new File(path);
                            if(file.exists()){
                                parse(file,program);
                                tokens.add(new AbsoluteJump(TokenType.INCLUDE,pos,
                                        new TokenPosition(file.getAbsolutePath(),0)));
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
                        }else if(prev instanceof VariableToken){
                            if(prev.tokenType == TokenType.IDENTIFIER){
                                prev=new VariableToken(TokenType.VAR_WRITE,((VariableToken) prev).name,prev.pos);
                            }else if(prev.tokenType == TokenType.MODULE_READ_VAR){
                                prev=new VariableToken(TokenType.MODULE_WRITE_VAR,((VariableToken) prev).name,prev.pos);
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
                        if(prevId!=null){
                            prev=new VariableToken(TokenType.MODULE_READ_VAR,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '.' modifier: "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "?"->{
                        if(!(prev instanceof VariableToken)){
                            throw new SyntaxError("invalid token for '?' modifier: "+prev,pos);
                        }
                        if(prevId!=null){
                            prev=new VariableToken(TokenType.HAS_VAR,((VariableToken) prev).name,prev.pos);
                        }else if(prev.tokenType == TokenType.MODULE_READ_VAR){
                            prev=new VariableToken(TokenType.MODULE_HAS_VAR,((VariableToken) prev).name,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '?' modifier: "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=:"->{
                        if(prevId!=null){
                            prev=new VariableToken(TokenType.DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=:' modifier "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                    case "=$"->{
                        if(prevId!=null){
                            prev=new VariableToken(TokenType.CONST_DECLARE,prevId,prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=$' modifier: "+prev,pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                        return;
                    }
                }
                if(prev!=null&&prev.tokenType==TokenType.MACRO_EXPAND){
                    tokens.remove(tokens.size()-1);//remove prev
                    Macro m=program.macros.get(((VariableToken)prev).name);
                    for(StringWithPos s:m.content){//expand macro
                        finishWord(s.str,tokens,openBlocks,currentMacroPtr,new FilePosition(s.start,pos),program,fileName);
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
                        addWord(str,tokens,openBlocks,program.macros,pos, fileName);
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

    private void addWord(String str, ArrayList<Token> tokens, TreeMap<Integer, Token> openBlocks, HashMap<String, Macro> macros,
                         FilePosition pos, String fileName) throws SyntaxError {
        switch (str) {
            case "##"    -> {} //## string can only be passed to the method on end of file
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

            case "if" -> {
                Token t = new Token(TokenType.IF, pos);
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "&&" -> {
                Token t = new Token(TokenType.SHORT_AND_HEADER, pos);
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "||" -> {
                Token t = new Token(TokenType.SHORT_OR_HEADER, pos);
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case ":" -> {
                Token s = new Token(TokenType.START, pos);
                openBlocks.put(tokens.size(),s);
                tokens.add(s);
            }
            case "elif" -> {
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                if(label!=null&&(label.getValue().tokenType==TokenType.IF||label.getValue().tokenType==TokenType.ELIF)){
                    Token t = new Token(TokenType.ELIF, pos);
                    openBlocks.put(tokens.size(),t);
                    if(label.getValue().tokenType==TokenType.ELIF){//jump before elif to chain jumps
                        tokens.set(label.getKey(),new RelativeJump(TokenType.JMP,label.getValue().pos,
                                tokens.size()-label.getKey()));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new RelativeJump(TokenType.JNE,start.getValue().pos,
                            tokens.size()-start.getKey()));
                }else{
                    throw new SyntaxError("elif has to be preceded with if or elif followed by a :",pos);
                }
            }
            case "else" -> {
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                if(label!=null&&(label.getValue().tokenType==TokenType.IF||label.getValue().tokenType==TokenType.ELIF)){
                    Token t = new Token(TokenType.ELSE, pos);
                    openBlocks.put(tokens.size(),t);
                    if(label.getValue().tokenType==TokenType.ELIF){
                        //jump before else to chain jumps
                        tokens.set(label.getKey(),new RelativeJump(TokenType.JMP,label.getValue().pos,
                               tokens.size()-label.getKey()));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new RelativeJump(TokenType.JNE,start.getValue().pos,
                            tokens.size()-start.getKey()));
                }else{
                    throw new SyntaxError("else has to be preceded with if or elif followed by a :",pos);
                }
            }
            case "end" ->{
                Token t = new Token(TokenType.END, pos);
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                if(start==null){
                    throw new SyntaxError("unexpected end statement",pos);
                }else if(start.getValue().tokenType==TokenType.START){
                    Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                    switch (label.getValue().tokenType){
                        case IF,ELIF -> {//(el)if ... : ... end
                            tokens.add(t);
                            tokens.set(start.getKey(),new RelativeJump(TokenType.JNE,start.getValue().pos,
                                    tokens.size()-start.getKey()));
                            if(label.getValue().tokenType==TokenType.ELIF){
                                tokens.set(label.getKey(),new RelativeJump(TokenType.JMP,label.getValue().pos,
                                        tokens.size()-label.getKey()));
                            }
                        }
                        case SHORT_AND_HEADER -> {
                            tokens.add(t);
                            tokens.set(start.getKey(),new RelativeJump(TokenType.SHORT_AND_JMP,start.getValue().pos,
                                    tokens.size()-start.getKey()));
                        }
                        case SHORT_OR_HEADER -> {
                            tokens.add(t);
                            tokens.set(start.getKey(),new RelativeJump(TokenType.SHORT_OR_JMP,start.getValue().pos,
                                    tokens.size()-start.getKey()));
                        }
                        case WHILE -> {//while ... : ... end
                            tokens.add(new RelativeJump(TokenType.JMP,t.pos,label.getKey()-tokens.size()));
                            tokens.set(start.getKey(),new RelativeJump(TokenType.JNE,start.getValue().pos,
                                    tokens.size()-start.getKey()));
                        }
                        case VALUE,OPERATOR,DECLARE,CONST_DECLARE, IDENTIFIER,MACRO_EXPAND,
                                DROP,STACK_GET,STACK_SLICE_GET,STACK_SET,STACK_SLICE_SET,
                                VAR_WRITE,START,END,ELSE,DO,PROCEDURE,
                                RETURN, SKIP_PROC,JEQ,JNE,JMP,SHORT_AND_JMP,SHORT_OR_JMP,
                                PRINT,PRINTLN, MODULE_READ_VAR, MODULE_WRITE_VAR,INCLUDE,
                                HAS_VAR, MODULE_HAS_VAR,EXIT
                                -> throw new SyntaxError("Invalid block syntax \""+
                                label.getValue().tokenType+"\"...':'",label.getValue().pos);
                    }
                }else if(start.getValue().tokenType==TokenType.WHILE){// do ... while ... end
                    Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                    if(label.getValue().tokenType!=TokenType.DO){
                        throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                                " 'do ... while'  or 'else'",pos);
                    }
                    tokens.add(new RelativeJump(TokenType.JEQ,t.pos,label.getKey()-tokens.size()));
                }else if(start.getValue().tokenType==TokenType.ELSE){// ... else ... end
                    tokens.add(t);
                    tokens.set(start.getKey(),new RelativeJump(TokenType.JMP,start.getValue().pos,
                            tokens.size()-start.getKey()));
                }else if(start.getValue().tokenType==TokenType.PROCEDURE){// proc ... : ... end
                    tokens.add(new Token(TokenType.RETURN,pos));
                    tokens.add(t);
                    tokens.set(start.getKey(),new RelativeJump(TokenType.SKIP_PROC,start.getValue().pos,
                            tokens.size()-start.getKey()));
                    tokens.add(new ValueToken(Value.ofProcedureId(new TokenPosition(fileName,start.getKey()+1)),pos));
                }else{
                    throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                            " 'do ... while'  or 'else' got:"+start.getValue(),pos);
                }
            }
            case "while" -> {
                Token t = new Token(TokenType.WHILE, pos);
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "do" -> {
                Token t = new Token(TokenType.DO, pos);
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "proc","procedure" -> {
                Token t = new Token(TokenType.PROCEDURE, pos);
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
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

            default -> tokens.add(new VariableToken(macros.containsKey(str)?TokenType.MACRO_EXPAND:TokenType.IDENTIFIER,str, pos));
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
    static class ProgramState{
        final HashMap<String,Variable> variables=new HashMap<>();
        final ProgramState parent;

        ProgramState(ProgramState parent) {
            this.parent = parent;
        }

        /**@return  the variable with the name or null if no variable with the given name exists*/
        Variable getVariable(String name){
            Variable var=variables.get(name);
            if(var==null&&parent!=null){
                return parent.getVariable(name);
            }
            return var;
        }
        public Value hasVariable(String name) {
            Variable var=variables.get(name);
            if(var==null&&parent!=null){
                return parent.hasVariable(name);
            }
            return var!=null?Value.TRUE:Value.FALSE;
        }

        public ProgramState getParent() {
            return parent;
        }

        /**declares a new Variable with the given name,type and value*/
        public void declareVariable(String name, Type type, boolean isConst, Value value) throws ConcatRuntimeError {
            Variable prev = getVariable(name);
            if(prev!=null) {
                if (prev.isConst){
                    throw new ConcatRuntimeError("const variable " + name + " is overwritten or shadowed");
                }else if (isConst){
                    throw new ConcatRuntimeError("const variable " + name + " is overwrites existing variable ");
                }
            }
            variables.put(name,new Variable(type, isConst, value));
        }
    }

    private ArrayList<Token> updateTokens(String oldFile, TokenPosition newPos, Program prog, ArrayList<Token> tokens) {
        if(oldFile.equals(newPos.file)){
            return tokens;
        }else{
            return prog.fileTokens.get(newPos.file);
        }
    }

    public RandomAccessStack<Value> run(Program program){
        RandomAccessStack<Value> stack=new RandomAccessStack<>(16);
        ProgramState state=new ProgramState(null);
        String file=program.mainFile;
        int ip=0;
        ArrayDeque<TokenPosition> callStack=new ArrayDeque<>();
        ArrayList<Token> tokens=program.fileTokens.get(program.mainFile);
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
                                Value procedure = stack.pop();
                                state = new ProgramState(state);
                                callStack.push(new TokenPosition(file, ip));
                                TokenPosition newPos=procedure.asProcedure();
                                tokens=updateTokens(file,newPos,program,tokens);
                                file=newPos.file;
                                ip=newPos.ip;
                                incIp = false;
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
                    case DECLARE, CONST_DECLARE -> {
                        Value type = stack.pop();
                        Value value = stack.pop();
                        state.declareVariable(((VariableToken) next).name, type.asType(),
                                next.tokenType == TokenType.CONST_DECLARE, value);
                    }
                    case IDENTIFIER -> {
                        Variable var = state.getVariable(((VariableToken) next).name);
                        if (var == null) {
                            throw new ConcatRuntimeError("Variable " + ((VariableToken) next).name + " does not exist ");
                        }
                        stack.push(var.getValue());
                    }
                    case VAR_WRITE -> {
                        Variable var = state.getVariable(((VariableToken) next).name);
                        if (var == null) {
                            throw new ConcatRuntimeError("Variable " + ((VariableToken) next).name + " does not exist");
                        } else if (var.isConst) {
                            throw new ConcatRuntimeError("Tried to overwrite const variable " + ((VariableToken) next).name);
                        }
                        var.setValue(stack.pop());
                    }
                    case HAS_VAR -> stack.push(state.hasVariable(((VariableToken) next).name));
                    case IF, ELIF,SHORT_AND_HEADER,SHORT_OR_HEADER,DO, WHILE, END -> {
                        //labels are no-ops
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
                    case MACRO_EXPAND,START, ELSE, PROCEDURE -> throw new RuntimeException("Tokens of type " + next.tokenType +
                            " should be eliminated at compile time");
                    case RETURN -> {
                        TokenPosition ret=callStack.poll();
                        if (ret == null) {
                            throw new ConcatRuntimeError("call-stack underflow");
                        }
                        tokens=updateTokens(file,ret,program,tokens);
                        file=ret.file;
                        ip=ret.ip;
                        state = state.getParent();
                        assert state!=null;
                    }
                    case MODULE_READ_VAR,MODULE_WRITE_VAR,MODULE_HAS_VAR ->
                        throw new UnsupportedOperationException("modules are currently not supported");
                    case JMP,SKIP_PROC -> {
                        ip+=((RelativeJump) next).delta;
                        incIp = false;
                    }
                    case INCLUDE -> {
                        callStack.push(new TokenPosition(file, ip));
                        tokens= updateTokens(file,((AbsoluteJump) next).target, program, tokens);
                        file=((AbsoluteJump) next).target.file;
                        ip=((AbsoluteJump) next).target.ip;
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
                        return stack;
                    }
                }
            }catch (ConcatRuntimeError|RandomAccessStack.StackUnderflow  e){
                System.err.println(e.getMessage());
                Token token = tokens.get(ip);
                System.err.printf("  while executing %-20s\n   at %s\n",token,token.pos);
                while(callStack.size()>0){
                    TokenPosition prev=callStack.poll();
                    tokens=updateTokens(file,prev,program,tokens);
                    token=tokens.get(prev.ip);
                    //addLater more readable names for tokens
                    System.err.printf("  while executing %-20s\n   at %s\n",token,token.pos);
                    assert state!=null;
                    state=state.getParent();
                }
                break;
            }
            if(incIp){
                ip++;
            }//no else
            if(ip>=tokens.size()&&!callStack.isEmpty()){
                TokenPosition ret=callStack.poll();
                tokens=updateTokens(file,ret,program,tokens);
                file=ret.file;
                ip=ret.ip+1;//increment ip after returning from file
            }
        }
        return stack;
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
