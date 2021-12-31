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
        DECLARE,CONST_DECLARE, IDENTIFIER,MACRO_EXPAND,VAR_WRITE,HAS_VAR,//addLater option to free variables
        IF,START,ELIF,ELSE,DO,WHILE,END,
        SHORT_AND_HEADER, SHORT_OR_HEADER,SHORT_AND_JMP, SHORT_OR_JMP,
        PROCEDURE,RETURN, PROCEDURE_START,
        STRUCT_START,STRUCT_END,FIELD_READ,FIELD_WRITE,HAS_FIELD,//TODO rename struct
        PRINT,PRINTLN,
        JEQ,JNE,JMP,//jump commands only for internal representation
        INCLUDE,
        EXIT
    }

    record FilePosition(String path, long line, int posInLine) {
        @Override
        public String toString() {
            return path+":"+line + ":" + posInLine;
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
    static class JumpToken extends Token{
        final TokenPosition target;
        JumpToken(TokenType tokenType, FilePosition pos, TokenPosition target) {
            super(tokenType, pos);
            this.target = target;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+(tokenType== TokenType.INCLUDE?target.file:target);
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
                    //addLater #ifdef #ifndef ...
                    case "#end"-> throw new SyntaxError("#end outside of macro",pos);
                    case "#include" -> {
                        if(prev instanceof ValueToken){
                            tokens.remove(tokens.size()-1);
                            String name=((ValueToken) prev).value.stringValue();
                            File file=new File(name);
                            if(file.exists()){
                                parse(file,program);
                                tokens.add(new JumpToken(TokenType.INCLUDE,pos,
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
                                tokens.add(new JumpToken(TokenType.INCLUDE,pos,
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
                            //<array> <val> <index> []
                            tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_INDEX,prev.pos));
                        }else if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_SLICE){
                            //<array> <val> <off> <to> [:]
                            tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_SLICE,prev.pos));
                        }else if(prev instanceof VariableToken){
                            if(prev.tokenType == TokenType.IDENTIFIER){
                                prev=new VariableToken(TokenType.VAR_WRITE,((VariableToken) prev).name,prev.pos);
                            }else if(prev.tokenType == TokenType.FIELD_READ){
                                prev=new VariableToken(TokenType.FIELD_WRITE,((VariableToken) prev).name,prev.pos);
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
                            prev=new VariableToken(TokenType.FIELD_READ,prevId,prev.pos);
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
                        }else if(prev.tokenType == TokenType.FIELD_READ){
                            prev=new VariableToken(TokenType.HAS_FIELD,((VariableToken) prev).name,prev.pos);
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
                        //TODO remember position of expansion
                        finishWord(s.str,tokens,openBlocks,currentMacroPtr,s.start,program,fileName);
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
            case "(struct)" -> tokens.add(new ValueToken(Value.ofType(Type.STRUCT),        pos));
            case "var"      -> tokens.add(new ValueToken(Value.ofType(Type.ANY),           pos));
            case "tuple"    -> tokens.add(new OperatorToken(OperatorType.TUPLE,            pos));
            case "(list)"   -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_LIST),  pos));
            case "(tuple)"  -> tokens.add(new ValueToken(Value.ofType(Type.GENERIC_TUPLE), pos));
            case "(file)"   -> tokens.add(new ValueToken(Value.ofType(Type.FILE),          pos));

            case "cast"   ->  tokens.add(new OperatorToken(OperatorType.CAST,    pos));
            case "typeof" ->  tokens.add(new OperatorToken(OperatorType.TYPE_OF, pos));

            case "dup"    -> tokens.add(new OperatorToken(OperatorType.DUP,        pos));
            case "drop"   -> tokens.add(new OperatorToken(OperatorType.DROP,       pos));
            case "swap"   -> tokens.add(new OperatorToken(OperatorType.SWAP,       pos));
            case "over"   -> tokens.add(new OperatorToken(OperatorType.OVER,       pos));

            case "refId"  -> tokens.add(new OperatorToken(OperatorType.REF_ID,     pos));

            case "clone"  -> tokens.add(new OperatorToken(OperatorType.CLONE,      pos));
            case "clone!" -> tokens.add(new OperatorToken(OperatorType.DEEP_CLONE, pos));

            case "print"      -> tokens.add(new Token(TokenType.PRINT,   pos));
            case "println"    -> tokens.add(new Token(TokenType.PRINTLN, pos));

            case "bytes"      -> tokens.add(new OperatorToken(OperatorType.BYTES_LE,          pos));
            case "bytes_BE"   -> tokens.add(new OperatorToken(OperatorType.BYTES_BE,          pos));
            case "asInt"      -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_INT_LE,   pos));
            case "asInt_BE"   -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_INT_BE,   pos));
            case "asFloat"    -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_FLOAT_LE, pos));
            case "asFloat_BE" -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_FLOAT_BE, pos));

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
                        tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,
                                new TokenPosition(fileName,tokens.size())));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
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
                        tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,
                                new TokenPosition(fileName,tokens.size())));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
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
                            tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,
                                    new TokenPosition(fileName,tokens.size())));
                            if(label.getValue().tokenType==TokenType.ELIF){
                                tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,
                                        new TokenPosition(fileName,tokens.size())));
                            }
                        }
                        case SHORT_AND_HEADER -> {
                            tokens.add(t);
                            tokens.set(start.getKey(),new JumpToken(TokenType.SHORT_AND_JMP,start.getValue().pos,
                                    new TokenPosition(fileName,tokens.size())));
                        }
                        case SHORT_OR_HEADER -> {
                            tokens.add(t);
                            tokens.set(start.getKey(),new JumpToken(TokenType.SHORT_OR_JMP,start.getValue().pos,
                                    new TokenPosition(fileName,tokens.size())));
                        }
                        case WHILE -> {//while ... : ... end
                            tokens.add(new JumpToken(TokenType.JMP,t.pos,new TokenPosition(fileName,label.getKey())));
                            tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,
                                    new TokenPosition(fileName,tokens.size())));
                        }
                        case VALUE,OPERATOR,DECLARE,CONST_DECLARE, IDENTIFIER,MACRO_EXPAND,
                                VAR_WRITE,START,END,ELSE,DO,PROCEDURE,
                                RETURN, PROCEDURE_START,JEQ,JNE,JMP,SHORT_AND_JMP,SHORT_OR_JMP,
                                PRINT,PRINTLN,STRUCT_START,STRUCT_END,FIELD_READ,FIELD_WRITE,INCLUDE,
                                HAS_VAR,HAS_FIELD,EXIT
                                -> throw new SyntaxError("Invalid block syntax \""+
                                label.getValue().tokenType+"\"...':'",label.getValue().pos);
                    }
                }else if(start.getValue().tokenType==TokenType.WHILE){// do ... while ... end
                    Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                    if(label.getValue().tokenType!=TokenType.DO){
                        throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                                " 'do ... while'  or 'else'",pos);
                    }
                    tokens.add(new JumpToken(TokenType.JEQ,t.pos,new TokenPosition(fileName,label.getKey())));
                }else if(start.getValue().tokenType==TokenType.ELSE){// ... else ... end
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JMP,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
                }else if(start.getValue().tokenType==TokenType.PROCEDURE){// proc ... : ... end
                    tokens.add(new Token(TokenType.RETURN,pos));
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.PROCEDURE_START,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
                    tokens.add(new ValueToken(Value.ofProcedureId(new TokenPosition(fileName,start.getKey()+1)),pos));
                }else if(start.getValue().tokenType==TokenType.STRUCT_START){//struct ... end
                    tokens.add(new Token(TokenType.STRUCT_END,pos));
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
            case "struct" -> {
                Token t = new Token(TokenType.STRUCT_START, pos);
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

            case "import"  -> tokens.add(new OperatorToken(OperatorType.IMPORT,       pos));
            case "$import" -> tokens.add(new OperatorToken(OperatorType.CONST_IMPORT, pos));

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

    static Value pop(ArrayDeque<Value> stack) throws ConcatRuntimeError {
        Value v=stack.pollLast();
        if(v==null){
            throw new ConcatRuntimeError("stack underflow");
        }
        return v;
    }
    static Value peek(ArrayDeque<Value> stack) throws ConcatRuntimeError {
        Value v=stack.peekLast();
        if(v==null){
            throw new ConcatRuntimeError("stack underflow");
        }
        return v;
    }
    private ArrayList<Token> updateTokens(String oldFile, TokenPosition newPos, Program prog, ArrayList<Token> tokens) {
        if(oldFile.equals(newPos.file)){
            return tokens;
        }else{
            return prog.fileTokens.get(newPos.file);
        }
    }

    public ArrayDeque<Value> run(Program program){
        ArrayDeque<Value> stack=new ArrayDeque<>();
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
                    case VALUE -> stack.addLast(((ValueToken) next).value.clone(true));
                    case OPERATOR -> {
                        switch (((OperatorToken) next).opType) {
                            case DUP -> {
                                Value t = peek(stack);
                                stack.addLast(t);
                            }
                            case OVER -> {// a b -> a b a
                                Value b=pop(stack);
                                Value a=peek(stack);
                                stack.addLast(b);
                                stack.addLast(a);
                            }
                            case REF_ID -> stack.addLast(Value.ofInt(pop(stack).id()));
                            case CLONE -> {
                                Value t = peek(stack);
                                stack.addLast(t.clone(false));
                            }
                            case DEEP_CLONE -> {
                                Value t = peek(stack);
                                stack.addLast(t.clone(true));
                            }
                            case DROP -> pop(stack);
                            case SWAP -> {
                                Value tmp1 = pop(stack);
                                Value tmp2 = pop(stack);
                                stack.addLast(tmp1);
                                stack.addLast(tmp2);
                            }
                            case NEGATE -> {
                                Value v = pop(stack);
                                stack.addLast(v.negate());
                            }
                            case INVERT -> {
                                Value v = pop(stack);
                                stack.addLast(v.invert());
                            }
                            case NOT -> {
                                Value v = pop(stack);
                                stack.addLast(v.asBool() ? Value.FALSE : Value.TRUE);
                            }
                            case FLIP -> {
                                Value v = pop(stack);
                                stack.addLast(v.flip());
                            }
                            case PLUS -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(x + y), (x, y) -> Value.ofFloat(x + y)));
                            }
                            case MINUS -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(x - y), (x, y) -> Value.ofFloat(x - y)));
                            }
                            case MULTIPLY -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(x * y), (x, y) -> Value.ofFloat(x * y)));
                            }
                            case DIV -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(x / y), (x, y) -> Value.ofFloat(x / y)));
                            }
                            case MOD -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(x % y), (x, y) -> Value.ofFloat(x % y)));
                            }
                            case UNSIGNED_DIV -> {
                                long b = pop(stack).asLong();
                                long a = pop(stack).asLong();
                                stack.addLast(Value.ofInt(Long.divideUnsigned(a,b)));
                            }
                            case UNSIGNED_MOD -> {
                                long b = pop(stack).asLong();
                                long a = pop(stack).asLong();
                                stack.addLast(Value.ofInt(Long.remainderUnsigned(a,b)));
                            }
                            case POW -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(longPow(x, y)),
                                        (x, y) -> Value.ofFloat(Math.pow(x, y))));
                            }
                            case EQ, NE, GT, GE, LE, LT,REF_EQ,REF_NE -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.compare(a, ((OperatorToken) next).opType, b));
                            }
                            case AND -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.logicOp(a, b, (x, y) -> x && y, (x, y) -> x & y));
                            }
                            case OR -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.logicOp(a, b, (x, y) -> x || y, (x, y) -> x | y));
                            }
                            case XOR -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.logicOp(a, b, (x, y) -> x ^ y, (x, y) -> x ^ y));
                            }
                            case LOG   -> stack.addLast(Value.ofFloat(Math.log(pop(stack).asDouble())));
                            case FLOOR -> stack.addLast(Value.ofFloat(Math.floor(pop(stack).asDouble())));
                            case CEIL  -> stack.addLast(Value.ofFloat(Math.ceil(pop(stack).asDouble())));
                            case LSHIFT -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.intOp(a, b, (x, y) -> y < 0 ? x >>> -y : x << y));
                            }
                            case RSHIFT -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.intOp(a, b, (x, y) -> y < 0 ? x << -y : x >>> y));
                            }
                            case SLSHIFT -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.intOp(a, b, (x, y) -> y < 0 ? x >> -y : slshift(x, y)));
                            }
                            case SRSHIFT -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.intOp(a, b, (x, y) -> y < 0 ? slshift(x, -y) : x >> y));
                            }
                            case LIST_OF -> {
                                Type contentType = pop(stack).asType();
                                stack.addLast(Value.ofType(Type.listOf(contentType)));
                            }
                            case CONTENT -> {
                                Type wrappedType = pop(stack).asType();
                                stack.addLast(Value.ofType(wrappedType.content()));
                            }
                            case NEW_LIST -> {//e1 e2 ... eN type count {}
                                long count = pop(stack).asLong();
                                Type type = pop(stack).asType();
                                ArrayDeque<Value> tmp = new ArrayDeque<>((int) count);
                                while (count > 0) {
                                    tmp.addFirst(pop(stack).castTo(type));
                                    count--;
                                }
                                ArrayList<Value> list = new ArrayList<>(tmp);
                                stack.addLast(Value.createList(Type.listOf(type), list));
                            }
                            case CAST -> {
                                Type type = pop(stack).asType();
                                Value val = pop(stack);
                                stack.addLast(val.castTo(type));
                            }
                            case TYPE_OF -> {
                                Value val = pop(stack);
                                stack.addLast(Value.ofType(val.type));
                            }
                            case LENGTH -> {
                                Value val = pop(stack);
                                stack.addLast(Value.ofInt(val.length()));
                            }
                            case ENSURE_CAP -> {
                                long newCap=pop(stack).asLong();
                                peek(stack).ensureCap(newCap);
                            }
                            case FILL -> {
                                Value val   = pop(stack);
                                long  count = pop(stack).asLong();
                                long  off   = pop(stack).asLong();
                                Value list  = pop(stack);
                                list.fill(val,off,count);
                            }
                            case GET_INDEX -> {//array index []
                                long index = pop(stack).asLong();
                                Value list = pop(stack);
                                stack.addLast(list.get(index));
                            }
                            case SET_INDEX -> {//array value index [] =
                                long index = pop(stack).asLong();
                                Value val = pop(stack);
                                Value list = pop(stack);
                                list.set(index,val);
                            }
                            case GET_SLICE -> {
                                long to = pop(stack).asLong();
                                long off = pop(stack).asLong();
                                Value list = pop(stack);
                                stack.addLast(list.getSlice(off, to));
                            }
                            case SET_SLICE -> {
                                long to = pop(stack).asLong();
                                long off = pop(stack).asLong();
                                Value val = pop(stack);
                                Value list = pop(stack);
                                list.setSlice(off,to,val);
                            }
                            case PUSH_FIRST -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                b.push(a,true);
                                stack.addLast(b);
                            }
                            case PUSH_LAST -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                a.push(b,false);
                                stack.addLast(a);
                            }
                            case PUSH_ALL_FIRST -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                b.pushAll(a,true);
                                stack.addLast(b);
                            }
                            case PUSH_ALL_LAST -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                a.pushAll(b,false);
                                stack.addLast(a);
                            }
                            case TUPLE -> {
                                long count=pop(stack).asLong();
                                Type[] types=new Type[(int)count];
                                for(int i=1;i<=count;i++){
                                    types[types.length-i]=pop(stack).asType();
                                }
                                stack.addLast(Value.ofType(Type.Tuple.create(types)));
                            }
                            case NEW -> {
                                Type type=pop(stack).asType();
                                if(type instanceof Type.Tuple){
                                    int count=((Type.Tuple)type).elementCount();
                                    Value[] values=new Value[count];
                                    for(int i=1;i<= values.length;i++){
                                        values[count-i]=pop(stack).castTo(((Type.Tuple) type).get(count-i));
                                    }
                                    stack.addLast(Value.createTuple((Type.Tuple)type,values));
                                }else if(type.isList()){
                                    long initCap=pop(stack).asLong();
                                    stack.addLast(Value.createList(type,initCap));
                                }else{
                                    throw new ConcatRuntimeError("new only supports tuples and lists");
                                }
                            }
                            case CALL -> {
                                Value procedure = pop(stack);
                                state = new ProgramState(state);
                                callStack.push(new TokenPosition(file, ip));
                                TokenPosition newPos=procedure.asProcedure();
                                tokens=updateTokens(file,newPos,program,tokens);
                                file=newPos.file;
                                ip=newPos.ip;
                                incIp = false;
                            }
                            case IMPORT       -> pop(stack).importTo(state, true);
                            case CONST_IMPORT -> pop(stack).importTo(state, false);

                            case BYTES_LE -> stack.addLast(pop(stack).bytes(false));
                            case BYTES_BE -> stack.addLast(pop(stack).bytes(true));
                            case BYTES_AS_INT_LE -> stack.addLast(
                                    Value.ofInt(Value.intFromBytes(pop(stack).asByteArray())));
                            case BYTES_AS_INT_BE -> stack.addLast(
                                    Value.ofInt(Value.intFromBytes(
                                            ReversedList.reverse(pop(stack).asByteArray()))));
                            case BYTES_AS_FLOAT_LE -> stack.addLast(
                                    Value.ofFloat(Double.longBitsToDouble(
                                            Value.intFromBytes(pop(stack).asByteArray()))));
                            case BYTES_AS_FLOAT_BE -> stack.addLast(
                                    Value.ofFloat(Double.longBitsToDouble(Value.intFromBytes(
                                            ReversedList.reverse(pop(stack).asByteArray())))));
                            case OPEN -> {
                                String options = pop(stack).stringValue();
                                String path    = pop(stack).stringValue();
                                stack.addLast(Value.ofFile(new FileStream(path,options)));
                            }
                            case CLOSE -> {
                                FileStream stream = pop(stack).asStream();
                                stack.addLast(stream.close()?Value.TRUE:Value.FALSE);
                                //??? keep stream
                            }
                            case READ -> {//<file> <buff> <off> <count> read => <nRead>
                                long count  = pop(stack).asLong();
                                long off    = pop(stack).asLong();
                                Value buff  = pop(stack);
                                FileStream stream = pop(stack).asStream();
                                stack.addLast(Value.ofInt(stream.read(buff.elements(),off,count)));
                            }
                            case WRITE -> {//<file> <buff> <off> <count> write => <isOk>
                                long count  = pop(stack).asLong();
                                long off    = pop(stack).asLong();
                                Value buff  = pop(stack);
                                FileStream stream = pop(stack).asStream();
                                stack.addLast(stream.write(buff.elements(),off,count)?Value.TRUE:Value.FALSE);
                            }
                            case SIZE -> {
                                FileStream stream  = pop(stack).asStream();
                                stack.addLast(Value.ofInt(stream.size()));
                            }
                            case POS -> {
                                FileStream stream  = pop(stack).asStream();
                                stack.addLast(Value.ofInt(stream.pos()));
                            }
                            case TRUNCATE -> {
                                FileStream stream = pop(stack).asStream();
                                stack.addLast(stream.truncate()?Value.TRUE:Value.FALSE);
                            }
                            case SEEK -> {
                                long pos = pop(stack).asLong();
                                FileStream stream = pop(stack).asStream();
                                stack.addLast(stream.seek(pos)?Value.TRUE:Value.FALSE);
                            }
                            case SEEK_END -> {
                                FileStream stream = pop(stack).asStream();
                                stack.addLast(stream.seekEnd()?Value.TRUE:Value.FALSE);
                            }
                        }
                    }
                    case PRINT -> System.out.print(pop(stack).stringValue());
                    case PRINTLN -> System.out.println(pop(stack).stringValue());
                    case DECLARE, CONST_DECLARE -> {
                        Value type = pop(stack);
                        Value value = pop(stack);
                        state.declareVariable(((VariableToken) next).name, type.asType(),
                                next.tokenType == TokenType.CONST_DECLARE, value);
                    }
                    case IDENTIFIER -> {
                        Variable var = state.getVariable(((VariableToken) next).name);
                        if (var == null) {
                            throw new ConcatRuntimeError("Variable " + ((VariableToken) next).name + " does not exist ");
                        }
                        stack.addLast(var.getValue());
                    }
                    case VAR_WRITE -> {
                        Variable var = state.getVariable(((VariableToken) next).name);
                        if (var == null) {
                            throw new ConcatRuntimeError("Variable " + ((VariableToken) next).name + " does not exist");
                        } else if (var.isConst) {
                            throw new ConcatRuntimeError("Tried to overwrite const variable " + ((VariableToken) next).name);
                        }
                        var.setValue(pop(stack));
                    }
                    case HAS_VAR -> stack.addLast(state.hasVariable(((VariableToken) next).name));
                    case IF, ELIF,SHORT_AND_HEADER,SHORT_OR_HEADER,DO, WHILE, END -> {
                        //labels are no-ops
                    }
                    case SHORT_AND_JMP -> {
                        Value c = peek(stack);
                        if (c.asBool()) {
                            pop(stack);// remove and evaluate branch
                        }else{
                            tokens= updateTokens(file,((JumpToken) next).target, program, tokens);
                            file=((JumpToken) next).target.file;
                            ip=((JumpToken) next).target.ip;
                            incIp = false;
                        }
                    }
                    case SHORT_OR_JMP -> {
                        Value c = peek(stack);
                        if (c.asBool()) {//  remove and evaluate branch
                            tokens= updateTokens(file,((JumpToken) next).target, program, tokens);
                            file=((JumpToken) next).target.file;
                            ip=((JumpToken) next).target.ip;
                            incIp = false;
                        }else{
                            pop(stack);// remove token
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
                    case STRUCT_START ->
                            state = new ProgramState(state);
                    case STRUCT_END -> {
                        ProgramState tmp=state;
                        state=state.getParent();
                        if (state == null) {
                            throw new ConcatRuntimeError("scope-stack underflow");
                        }
                        stack.addLast(Value.newStruct(tmp.variables));
                    }
                    case FIELD_READ -> {
                        Value struct = pop(stack);
                        stack.addLast(struct.getField(((VariableToken)next).name));
                    }
                    case FIELD_WRITE -> {
                        Value val    = pop(stack);
                        Value struct = pop(stack);
                        struct.setField(((VariableToken)next).name,val);
                        stack.addLast(struct);
                    }
                    case HAS_FIELD -> {
                        Value struct = pop(stack);
                        stack.addLast(struct.hasField(((VariableToken)next).name));
                    }
                    case PROCEDURE_START,JMP -> {
                        tokens= updateTokens(file,((JumpToken) next).target, program, tokens);
                        file=((JumpToken) next).target.file;
                        ip=((JumpToken) next).target.ip;
                        incIp = false;
                    }
                    case INCLUDE -> {
                        callStack.push(new TokenPosition(file, ip));
                        tokens= updateTokens(file,((JumpToken) next).target, program, tokens);
                        file=((JumpToken) next).target.file;
                        ip=((JumpToken) next).target.ip;
                        incIp = false;
                    }
                    case JEQ -> {
                        Value c = pop(stack);
                        if (c.asBool()) {
                            tokens= updateTokens(file,((JumpToken) next).target, program, tokens);
                            file=((JumpToken) next).target.file;
                            ip=((JumpToken) next).target.ip;
                            incIp = false;
                        }
                    }
                    case JNE -> {
                        Value c = pop(stack);
                        if (!c.asBool()) {
                            tokens= updateTokens(file,((JumpToken) next).target,program,tokens);
                            file=((JumpToken) next).target.file;
                            ip=((JumpToken) next).target.ip;
                            incIp = false;
                        }
                    }
                    case EXIT -> {
                        long exitCode=pop(stack).asLong();
                        System.out.println("exited with exit code:"+exitCode);
                        return stack;
                    }
                }
            }catch (ConcatRuntimeError|IndexOutOfBoundsException|NegativeArraySizeException e){
                //TODO readable messages for Java index errors
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
        //TODO? compile to C
        ArrayDeque<Value> stack = ip.run(program);
        System.out.println("\nStack:");
        System.out.println(stack);
    }
}
