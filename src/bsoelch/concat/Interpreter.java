package bsoelch.concat;

import bsoelch.concat.streams.FileStream;
import bsoelch.concat.streams.ValueStream;

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
        DECLARE,CONST_DECLARE, VAR_READ, VAR_WRITE,HAS_VAR,//addLater undef/free
        IF,START,ELIF,ELSE,DO,WHILE,END,
        PROCEDURE,RETURN, PROCEDURE_START,
        STRUCT_START,STRUCT_END,FIELD_READ,FIELD_WRITE,HAS_FIELD,//TODO rename struct
        SPRINTF,PRINT,PRINTF,PRINTLN,//impleTODO move (s)printf to concat standard library
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

    record Program(String mainFile,HashMap<String,ArrayList<Token>> fileTokens){
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
            program=new Program(fileName,new HashMap<>());
        }else if(program.fileTokens.containsKey(fileName)){
            return program;
        }
        ParserReader reader=new ParserReader(fileName);
        ArrayList<Token> tokenBuffer=new ArrayList<>();
        TreeMap<Integer,Token> openBlocks=new TreeMap<>();
        int c;
        reader.nextToken();
        WordState state=WordState.ROOT;
        int stringStart=-1;
        while((c=reader.nextChar())>=0){
            switch(state){
                case ROOT:
                    if(Character.isWhitespace(c)){
                        finishWord(tokenBuffer,openBlocks, reader.buffer,reader,program,fileName);
                    }else{
                        switch (c) {
                            case '"', '\'' -> {
                                state = WordState.STRING;
                                stringStart = (char) c;
                                if(reader.buffer.length()>0) {
                                    throw new SyntaxError("Illegal string prefix:\"" + reader.buffer + "\"",
                                            reader.currentPos());
                                }
                            }
                            case '#' -> {
                                c = reader.forceNextChar();
                                if (c == '#') {
                                    state = WordState.LINE_COMMENT;
                                    finishWord(tokenBuffer,openBlocks, reader.buffer,reader,program,fileName);
                                } else if (c == '_') {
                                    state = WordState.COMMENT;
                                    finishWord(tokenBuffer,openBlocks, reader.buffer,reader,program,fileName);
                                } else {
                                    reader.buffer.append('#').append((char) c);
                                }
                            }
                            default -> reader.buffer.append((char) c);
                        }
                    }
                    break;
                case STRING:
                    if(c==stringStart){
                        if(c=='\''){//char literal
                            if(reader.buffer.codePoints().count()==1){
                                int codePoint = reader.buffer.codePointAt(0);
                                tokenBuffer.add(new ValueToken(Value.ofChar(codePoint), reader.currentPos()));
                            }else{
                                throw new SyntaxError("A char-literal must contain exactly one character", reader.currentPos());
                            }
                        }else{
                            tokenBuffer.add(new ValueToken(Value.ofString(reader.buffer.toString()),   reader.currentPos()));
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
            case ROOT->finishWord(tokenBuffer,openBlocks,reader.buffer,reader,program,fileName);
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
    private boolean tryParseInt(ArrayList<Token> tokens, String str,ParserReader reader) throws SyntaxError {
        try {
            if(intDec.matcher(str).matches()){//dez-Int
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 10)), reader.currentPos()));
                return false;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 2)), reader.currentPos()));
                return false;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 16)), reader.currentPos()));
                return false;
            }
        } catch (NumberFormatException nfeL) {
            throw new SyntaxError("Number out of Range:"+str, reader.currentPos());
        }
        return true;
    }

    private void finishWord(ArrayList<Token> tokens,TreeMap<Integer,Token> openBlocks, StringBuilder buffer,
                            ParserReader reader,Program program,String fileName) throws SyntaxError, IOException {
        if (buffer.length() > 0) {
            String str=buffer.toString();
            try{
                if (tryParseInt(tokens, str,reader)) {
                    if(floatDec.matcher(str).matches()){
                        //dez-Float
                        double d = Double.parseDouble(str);
                        tokens.add(new ValueToken(Value.ofFloat(d), reader.currentPos()));
                    }else if(floatBin.matcher(str).matches()){
                        //bin-Float
                        double d= Value.parseFloat(str.substring(BIN_PREFIX.length()),2);
                        tokens.add(new ValueToken(Value.ofFloat(d), reader.currentPos()));
                    }else if(floatHex.matcher(str).matches()){
                        //hex-Float
                        double d=Value.parseFloat(str.substring(BIN_PREFIX.length()),16);
                        tokens.add(new ValueToken(Value.ofFloat(d), reader.currentPos()));
                    }else {
                        if(str.length()>0)
                            addWord(str,tokens,openBlocks,reader,program,fileName);
                    }
                }
            }catch(SyntaxError e){
                if(e.pos.equals(reader.currentPos())){
                    throw e;//avoid duplicate positions in stack trace
                }else {
                    throw new SyntaxError(e, reader.currentPos());
                }
            }catch(ConcatRuntimeError|NumberFormatException e){
                throw new SyntaxError(e, reader.currentPos());
            }
        }
        reader.nextToken();
    }

    private void addWord(String str,ArrayList<Token> tokens,TreeMap<Integer,Token> openBlocks,
                         ParserReader reader,Program program,String fileName) throws SyntaxError, IOException {
        switch (str) {
            case "true"  -> tokens.add(new ValueToken(Value.TRUE,    reader.currentPos()));
            case "false" -> tokens.add(new ValueToken(Value.FALSE,   reader.currentPos()));

            case "bool"     -> tokens.add(new ValueToken(Value.ofType(Type.BOOL),      reader.currentPos()));
            case "byte"     -> tokens.add(new ValueToken(Value.ofType(Type.BYTE),      reader.currentPos()));
            case "int"      -> tokens.add(new ValueToken(Value.ofType(Type.INT),       reader.currentPos()));
            case "char"     -> tokens.add(new ValueToken(Value.ofType(Type.CHAR),      reader.currentPos()));
            case "float"    -> tokens.add(new ValueToken(Value.ofType(Type.FLOAT),     reader.currentPos()));
            case "string"   -> tokens.add(new ValueToken(Value.ofType(Type.STRING()),  reader.currentPos()));
            case "type"     -> tokens.add(new ValueToken(Value.ofType(Type.TYPE),      reader.currentPos()));
            case "list"     -> tokens.add(new OperatorToken(OperatorType.LIST_OF,      reader.currentPos()));
            case "itr"      -> tokens.add(new OperatorToken(OperatorType.ITR_OF,       reader.currentPos()));
            case "stream"   -> tokens.add(new OperatorToken(OperatorType.STREAM_OF,    reader.currentPos()));
            case "unwrap"   -> tokens.add(new OperatorToken(OperatorType.UNWRAP,       reader.currentPos()));
            case "*->*"     -> tokens.add(new ValueToken(Value.ofType(Type.PROCEDURE), reader.currentPos()));
            case "(struct)" -> tokens.add(new ValueToken(Value.ofType(Type.STRUCT),    reader.currentPos()));
            case "var"      -> tokens.add(new ValueToken(Value.ofType(Type.ANY),       reader.currentPos()));
            case "tuple"    -> tokens.add(new OperatorToken(OperatorType.TUPLE,      reader.currentPos()));

            case "cast"   ->  tokens.add(new OperatorToken(OperatorType.CAST,    reader.currentPos()));
            case "typeof" ->  tokens.add(new OperatorToken(OperatorType.TYPE_OF, reader.currentPos()));

            case "dup"    -> tokens.add(new OperatorToken(OperatorType.DUP,        reader.currentPos()));
            case "drop"   -> tokens.add(new OperatorToken(OperatorType.DROP,       reader.currentPos()));
            case "swap"   -> tokens.add(new OperatorToken(OperatorType.SWAP,       reader.currentPos()));
            case "over"   -> tokens.add(new OperatorToken(OperatorType.OVER,       reader.currentPos()));
            case "clone"  -> tokens.add(new OperatorToken(OperatorType.CLONE,      reader.currentPos()));
            case "clone!" -> tokens.add(new OperatorToken(OperatorType.DEEP_CLONE, reader.currentPos()));

            case "sprintf"    -> tokens.add(new Token(TokenType.SPRINTF, reader.currentPos()));
            case "print"      -> tokens.add(new Token(TokenType.PRINT,   reader.currentPos()));
            case "printf"     -> tokens.add(new Token(TokenType.PRINTF,  reader.currentPos()));
            case "println"    -> tokens.add(new Token(TokenType.PRINTLN, reader.currentPos()));

            case "bytes"      -> tokens.add(new OperatorToken(OperatorType.BYTES_LE, reader.currentPos()));
            case "bytes_BE"   -> tokens.add(new OperatorToken(OperatorType.BYTES_BE, reader.currentPos()));
            case "asInt"      -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_INT_LE,
                    reader.currentPos()));
            case "asInt_BE"   -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_INT_BE,
                    reader.currentPos()));
            case "asFloat"    -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_FLOAT_LE,
                    reader.currentPos()));
            case "asFloat_BE" -> tokens.add(new OperatorToken(OperatorType.BYTES_AS_FLOAT_BE,
                    reader.currentPos()));

            case "if" -> {
                Token t = new Token(TokenType.IF, reader.currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case ":" -> {
                Token s = new Token(TokenType.START, reader.currentPos());
                openBlocks.put(tokens.size(),s);
                tokens.add(s);
            }
            case "elif" -> {
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                if(label!=null&&(label.getValue().tokenType==TokenType.IF||label.getValue().tokenType==TokenType.ELIF)){
                    Token t = new Token(TokenType.ELIF, reader.currentPos());
                    openBlocks.put(tokens.size(),t);
                    if(label.getValue().tokenType==TokenType.ELIF){//jump before elif to chain jumps
                        tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,
                                new TokenPosition(fileName,tokens.size())));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
                }else{
                    throw new SyntaxError("elif has to be preceded with if or elif followed by a :",reader.currentPos());
                }
            }
            case "else" -> {
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                if(label!=null&&(label.getValue().tokenType==TokenType.IF||label.getValue().tokenType==TokenType.ELIF)){
                    Token t = new Token(TokenType.ELSE, reader.currentPos());
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
                    throw new SyntaxError("else has to be preceded with if or elif followed by a :",reader.currentPos());
                }
            }
            case "end" ->{
                Token t = new Token(TokenType.END, reader.currentPos());
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                if(start==null){
                    throw new SyntaxError("unexpected end statement",reader.currentPos());
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
                        case WHILE -> {//while ... : ... end
                            tokens.add(new JumpToken(TokenType.JMP,t.pos,new TokenPosition(fileName,label.getKey())));
                            tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,
                                    new TokenPosition(fileName,tokens.size())));
                        }
                        case VALUE,OPERATOR,DECLARE,CONST_DECLARE, VAR_READ, VAR_WRITE,START,END,ELSE,DO,PROCEDURE,
                                RETURN, PROCEDURE_START,JEQ,JNE,JMP,
                                SPRINTF,PRINT,PRINTF,PRINTLN,STRUCT_START,STRUCT_END,FIELD_READ,FIELD_WRITE,INCLUDE,
                                HAS_VAR,HAS_FIELD,EXIT
                                -> throw new SyntaxError("Invalid block syntax \""+
                                label.getValue().tokenType+"\"...':'",label.getValue().pos);
                    }
                }else if(start.getValue().tokenType==TokenType.WHILE){// do ... while ... end
                    Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                    if(label.getValue().tokenType!=TokenType.DO){
                        throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                                " 'do ... while'  or 'else'",reader.currentPos());
                    }
                    tokens.add(new JumpToken(TokenType.JEQ,t.pos,new TokenPosition(fileName,label.getKey())));
                }else if(start.getValue().tokenType==TokenType.ELSE){// ... else ... end
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JMP,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
                }else if(start.getValue().tokenType==TokenType.PROCEDURE){// proc ... : ... end
                    tokens.add(new Token(TokenType.RETURN,reader.currentPos()));
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.PROCEDURE_START,start.getValue().pos,
                            new TokenPosition(fileName,tokens.size())));
                    tokens.add(new ValueToken(Value.ofProcedureId(new TokenPosition(fileName,start.getKey()+1)),reader.currentPos()));
                }else if(start.getValue().tokenType==TokenType.STRUCT_START){//struct ... end
                    tokens.add(new Token(TokenType.STRUCT_END,reader.currentPos()));
                }else{
                    throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                            " 'do ... while'  or 'else' got:"+start.getValue(),reader.currentPos());
                }
            }
            case "while" -> {
                Token t = new Token(TokenType.WHILE, reader.currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "do" -> {
                Token t = new Token(TokenType.DO, reader.currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "proc","procedure" -> {
                Token t = new Token(TokenType.PROCEDURE, reader.currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "struct" -> {
                Token t = new Token(TokenType.STRUCT_START, reader.currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "return" -> tokens.add(new Token(TokenType.RETURN,  reader.currentPos()));
            case "exit"   -> tokens.add(new Token(TokenType.EXIT,  reader.currentPos()));

            case "+"   -> tokens.add(new OperatorToken(OperatorType.PLUS,   reader.currentPos()));
            case "-"   -> tokens.add(new OperatorToken(OperatorType.MINUS,  reader.currentPos()));
            case "-_"  -> tokens.add(new OperatorToken(OperatorType.NEGATE, reader.currentPos()));
            case "/_"  -> tokens.add(new OperatorToken(OperatorType.INVERT, reader.currentPos()));
            case "*"   -> tokens.add(new OperatorToken(OperatorType.MULT,   reader.currentPos()));
            case "/"   -> tokens.add(new OperatorToken(OperatorType.DIV,    reader.currentPos()));
            case "%"   -> tokens.add(new OperatorToken(OperatorType.MOD,    reader.currentPos()));
            case "**"  -> tokens.add(new OperatorToken(OperatorType.POW,    reader.currentPos()));
            case "!"   -> tokens.add(new OperatorToken(OperatorType.NOT,    reader.currentPos()));
            case "~"   -> tokens.add(new OperatorToken(OperatorType.FLIP,   reader.currentPos()));
            case "&"   -> tokens.add(new OperatorToken(OperatorType.AND,    reader.currentPos()));
            case "|"   -> tokens.add(new OperatorToken(OperatorType.OR,     reader.currentPos()));
            case "xor" -> tokens.add(new OperatorToken(OperatorType.XOR,    reader.currentPos()));
            case "<"   -> tokens.add(new OperatorToken(OperatorType.LT,     reader.currentPos()));
            case "<="  -> tokens.add(new OperatorToken(OperatorType.LE,     reader.currentPos()));
            case "=="  -> tokens.add(new OperatorToken(OperatorType.EQ,     reader.currentPos()));
            case "!="  -> tokens.add(new OperatorToken(OperatorType.NE,     reader.currentPos()));
            case ">="  -> tokens.add(new OperatorToken(OperatorType.GE,     reader.currentPos()));
            case ">"   -> tokens.add(new OperatorToken(OperatorType.GT,     reader.currentPos()));

            case ">>"  -> tokens.add(new OperatorToken(OperatorType.RSHIFT,  reader.currentPos()));
            case ".>>" -> tokens.add(new OperatorToken(OperatorType.SRSHIFT, reader.currentPos()));
            case "<<"  -> tokens.add(new OperatorToken(OperatorType.LSHIFT,  reader.currentPos()));
            case ".<<" -> tokens.add(new OperatorToken(OperatorType.SLSHIFT, reader.currentPos()));

            case "log"   -> tokens.add(new OperatorToken(OperatorType.LOG, reader.currentPos()));
            case "floor" -> tokens.add(new OperatorToken(OperatorType.FLOOR, reader.currentPos()));
            case "ceil"  -> tokens.add(new OperatorToken(OperatorType.CEIL, reader.currentPos()));

            case ">>:" -> tokens.add(new OperatorToken(OperatorType.PUSH_FIRST,     reader.currentPos()));
            case ":<<" -> tokens.add(new OperatorToken(OperatorType.PUSH_LAST,      reader.currentPos()));
            case "+:"  -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_FIRST, reader.currentPos()));
            case ":+"  -> tokens.add(new OperatorToken(OperatorType.PUSH_ALL_LAST,  reader.currentPos()));
            //<array> <index> []
            case "[]"   -> tokens.add(new OperatorToken(OperatorType.GET_INDEX,  reader.currentPos()));
            //<array> <off> <to> [:]
            case "[:]"  -> tokens.add(new OperatorToken(OperatorType.GET_SLICE,  reader.currentPos()));

            case "^.." -> tokens.add(new OperatorToken(OperatorType.ITR_START, reader.currentPos()));
            case "..^" -> tokens.add(new OperatorToken(OperatorType.ITR_END,   reader.currentPos()));
            case "^>"  -> tokens.add(new OperatorToken(OperatorType.ITR_NEXT,  reader.currentPos()));
            case "<^"  -> tokens.add(new OperatorToken(OperatorType.ITR_PREV,  reader.currentPos()));
            //<e0> ... <eN> <N> {}
            case "{}"     -> tokens.add(new OperatorToken(OperatorType.NEW_LIST, reader.currentPos()));
            case "length" -> tokens.add(new OperatorToken(OperatorType.LENGTH,   reader.currentPos()));
            case "()"     -> tokens.add(new OperatorToken(OperatorType.CALL,     reader.currentPos()));

            case "new"       -> tokens.add(new OperatorToken(OperatorType.NEW,        reader.currentPos()));
            case "ensureCap" -> tokens.add(new OperatorToken(OperatorType.ENSURE_CAP, reader.currentPos()));

            case "open"          -> tokens.add(new OperatorToken(OperatorType.OPEN,            reader.currentPos()));
            case "close"         -> tokens.add(new OperatorToken(OperatorType.CLOSE,           reader.currentPos()));
            case "size"          -> tokens.add(new OperatorToken(OperatorType.SIZE,            reader.currentPos()));
            case "pos"           -> tokens.add(new OperatorToken(OperatorType.POS,             reader.currentPos()));
            case "asStream"      -> tokens.add(new OperatorToken(OperatorType.STREAM_OF,       reader.currentPos()));
            case "reverseStream" -> tokens.add(new OperatorToken(OperatorType.REVERSED_STREAM, reader.currentPos()));
            case "state"         -> tokens.add(new OperatorToken(OperatorType.STREAM_STATE,    reader.currentPos()));
            case "read"          -> tokens.add(new OperatorToken(OperatorType.READ,            reader.currentPos()));
            case "read+"         -> tokens.add(new OperatorToken(OperatorType.READ_MULTIPLE,   reader.currentPos()));
            case "skip"          -> tokens.add(new OperatorToken(OperatorType.SKIP,            reader.currentPos()));
            case "seek"          -> tokens.add(new OperatorToken(OperatorType.SEEK,            reader.currentPos()));
            case "seekEnd"       -> tokens.add(new OperatorToken(OperatorType.SEEK_END,        reader.currentPos()));
            case "write"         -> tokens.add(new OperatorToken(OperatorType.WRITE,           reader.currentPos()));
            case "write+"        -> tokens.add(new OperatorToken(OperatorType.WRITE_MULTIPLE,  reader.currentPos()));

            case "include" -> {
                Token prev=tokens.get(tokens.size()-1);
                if(prev instanceof ValueToken){
                    tokens.remove(tokens.size()-1);
                    String name=((ValueToken) prev).value.stringValue();
                    File file=new File(name);
                    if(file.exists()){
                        parse(file,program);
                        tokens.add(new JumpToken(TokenType.INCLUDE,reader.currentPos(),
                                new TokenPosition(file.getAbsolutePath(),0)));
                    }else{
                        throw new SyntaxError("File "+name+" does not exist",reader.currentPos());
                    }
                }else if(prev instanceof VariableToken){
                    tokens.remove(tokens.size()-1);
                    String name = ((VariableToken) prev).name;
                    String path=libPath+File.separator+ name + "." + DEFAULT_FILE_EXTENSION;
                    File file=new File(path);
                    if(file.exists()){
                        parse(file,program);
                        tokens.add(new JumpToken(TokenType.INCLUDE,reader.currentPos(),
                                new TokenPosition(file.getAbsolutePath(),0)));
                    }else{
                        throw new SyntaxError(name+" is not part of the standard library",reader.currentPos());
                    }
                }else{
                    throw new UnsupportedOperationException("include path has to be a string literal or identifier");
                }
            }
            case "import"  -> tokens.add(new OperatorToken(OperatorType.IMPORT,       reader.currentPos()));
            case "$import" -> tokens.add(new OperatorToken(OperatorType.CONST_IMPORT, reader.currentPos()));

            case "="->{
                Token prev=tokens.get(tokens.size()-1);
                if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_INDEX){
                    //<array> <val> <index> []
                    tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_INDEX,prev.pos));
                }else if(prev instanceof OperatorToken&&((OperatorToken) prev).opType==OperatorType.GET_SLICE){
                    //<array> <val> <off> <to> [:]
                    tokens.set(tokens.size()-1,new OperatorToken(OperatorType.SET_SLICE,prev.pos));
                }else if(prev instanceof VariableToken){
                    if(prev.tokenType == TokenType.VAR_READ){
                        prev=new VariableToken(TokenType.VAR_WRITE,((VariableToken) prev).name,prev.pos);
                    }else if(prev.tokenType == TokenType.FIELD_READ){
                        prev=new VariableToken(TokenType.FIELD_WRITE,((VariableToken) prev).name,prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '=' modifier: "+prev,reader.currentPos());
                    }
                    tokens.set(tokens.size()-1,prev);
                }else{
                    throw new SyntaxError("invalid token for '=' modifier: "+prev,reader.currentPos());
                }
            }
            case "."->{
                Token prev=tokens.get(tokens.size()-1);
                if(!(prev instanceof VariableToken)){
                    throw new SyntaxError("invalid token for '.' modifier: "+prev,reader.currentPos());
                }
                if(prev.tokenType == TokenType.VAR_READ){
                    prev=new VariableToken(TokenType.FIELD_READ,((VariableToken) prev).name,prev.pos);
                }else{
                    throw new SyntaxError("invalid token for '.' modifier: "+prev,reader.currentPos());
                }
                tokens.set(tokens.size()-1,prev);
            }
            case "?"->{
                Token prev=tokens.get(tokens.size()-1);
                if(!(prev instanceof VariableToken)){
                    throw new SyntaxError("invalid token for '?' modifier: "+prev,reader.currentPos());
                }
                if(prev.tokenType == TokenType.VAR_READ){
                    prev=new VariableToken(TokenType.HAS_VAR,((VariableToken) prev).name,prev.pos);
                }else if(prev.tokenType == TokenType.FIELD_READ){
                    prev=new VariableToken(TokenType.HAS_FIELD,((VariableToken) prev).name,prev.pos);
                }else{
                    throw new SyntaxError("invalid token for '?' modifier: "+prev,reader.currentPos());
                }
                tokens.set(tokens.size()-1,prev);
            }
            case "=:"->{
                Token prev=tokens.get(tokens.size()-1);
                if(!(prev instanceof VariableToken)){
                    throw new SyntaxError("invalid token for '=:' modifier: "+prev,reader.currentPos());
                }
                if(prev.tokenType == TokenType.VAR_READ){
                    prev=new VariableToken(TokenType.DECLARE,((VariableToken) prev).name,prev.pos);
                }else{
                    throw new SyntaxError("invalid token for '=:' modifier "+prev,reader.currentPos());
                }
                tokens.set(tokens.size()-1,prev);
            }
            case "=$"->{
                Token prev=tokens.get(tokens.size()-1);
                if(!(prev instanceof VariableToken)){
                    throw new SyntaxError("invalid token for '=$' modifier: "+prev,reader.currentPos());
                }
                if(prev.tokenType == TokenType.VAR_READ){
                    prev=new VariableToken(TokenType.CONST_DECLARE,((VariableToken) prev).name,prev.pos);
                }else{
                    throw new SyntaxError("invalid token for '=$' modifier: "+prev,reader.currentPos());
                }
                tokens.set(tokens.size()-1,prev);
            }
            default -> tokens.add(new VariableToken(TokenType.VAR_READ, str, reader.currentPos()));
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
                            case MULT -> {
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
                            case POW -> {
                                Value b = pop(stack);
                                Value a = pop(stack);
                                stack.addLast(Value.mathOp(a, b, (x, y) -> Value.ofInt(longPow(x, y)),
                                        (x, y) -> Value.ofFloat(Math.pow(x, y))));
                            }
                            case EQ, NE, GT, GE, LE, LT -> {
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
                            case UNWRAP -> {
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
                            case ITR_OF -> {
                                Type contentType = pop(stack).asType();
                                stack.addLast(Value.ofType(Type.iteratorOf(contentType)));
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
                            case ITR_START -> {
                                Value a = pop(stack);
                                stack.addLast(a.iterator(false));
                            }
                            case ITR_END -> {
                                Value a = pop(stack);
                                stack.addLast(a.iterator(true));
                            }
                            case ITR_NEXT -> {
                                Value peek = peek(stack);
                                Value.ValueIterator itr = peek.asItr();
                                if (itr.hasNext()) {
                                    stack.addLast(itr.next());
                                    stack.addLast(Value.TRUE);
                                } else {
                                    stack.addLast(Value.FALSE);
                                }
                            }
                            case ITR_PREV -> {
                                Value peek = peek(stack);
                                Value.ValueIterator itr = peek.asItr();
                                if (itr.hasPrev()) {
                                    stack.addLast(itr.prev());
                                    stack.addLast(Value.TRUE);
                                } else {
                                    stack.addLast(Value.FALSE);
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
                            case STREAM_OF -> stack.addLast(pop(stack).stream(false));
                            case OPEN -> {
                                String options = pop(stack).stringValue();
                                String path    = pop(stack).stringValue();
                                stack.addLast(Value.ofStream(new FileStream(path,options)));
                            }
                            case CLOSE -> {
                                ValueStream stream = pop(stack).asStream();
                                stack.addLast(stream.close()?Value.TRUE:Value.FALSE);
                                //??? keep stream
                            }
                            case REVERSED_STREAM -> stack.addLast(pop(stack).stream(true));
                            case STREAM_STATE -> {
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(Value.ofInt(stream.state()));
                            }
                            case SIZE -> {
                                ValueStream stream  = peek(stack).asStream();
                                Optional<Long> size = stream.size();
                                size.ifPresent(x -> stack.addLast(Value.ofInt(x)));
                                stack.addLast(size.isPresent()?Value.TRUE:Value.FALSE);
                            }
                            case POS -> {
                                ValueStream stream  = peek(stack).asStream();
                                Optional<Long> pos = stream.pos();
                                pos.ifPresent(x -> stack.addLast(Value.ofInt(x)));
                                stack.addLast(pos.isPresent()?Value.TRUE:Value.FALSE);
                            }
                            case READ -> {
                                ValueStream stream = peek(stack).asStream();
                                Optional<Value> read = stream.read();
                                read.ifPresent(stack::addLast);
                                stack.addLast(read.isPresent()?Value.TRUE:Value.FALSE);
                            }
                            case READ_MULTIPLE -> {
                                long count = pop(stack).asLong();
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(Value.createList(Type.listOf(stream.contentType()),
                                        new ArrayList<>(stream.readMultiple((int)count))));
                            }
                            case SKIP -> {
                                long count = pop(stack).asLong();
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(Value.ofInt(stream.skip((int)count)));
                            }
                            case SEEK -> {
                                long pos = pop(stack).asLong();
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(stream.seek(pos)?Value.TRUE:Value.FALSE);
                            }
                            case SEEK_END -> {
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(stream.seekEnd()?Value.TRUE:Value.FALSE);
                            }
                            case WRITE -> {
                                Value val = pop(stack);
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(stream.write(val)?Value.TRUE:Value.FALSE);
                            }
                            case WRITE_MULTIPLE -> {
                                Value val = pop(stack);
                                ValueStream stream = peek(stack).asStream();
                                stack.addLast(stream.write(val.stream(false).asStream())?Value.TRUE:Value.FALSE);
                            }
                        }
                    }
                    case SPRINTF -> {
                        String format = pop(stack).castTo(Type.STRING()).stringValue();
                        StringBuilder build = new StringBuilder();
                        Printf.printf(format, stack, build::append);
                        stack.addLast(Value.ofString(build.toString()));
                    }
                    case PRINT -> System.out.print(pop(stack).stringValue());
                    case PRINTF -> {
                        String format = pop(stack).castTo(Type.STRING()).stringValue();
                        Printf.printf(format, stack, System.out::print);
                    }
                    case PRINTLN -> System.out.println(pop(stack).stringValue());
                    case DECLARE, CONST_DECLARE -> {
                        Value type = pop(stack);
                        Value value = pop(stack);
                        state.declareVariable(((VariableToken) next).name, type.asType(),
                                next.tokenType == TokenType.CONST_DECLARE, value);
                    }
                    case VAR_READ -> {
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
                    case IF, ELIF, DO, WHILE, END -> {
                        //labels are no-ops
                    }
                    case START, ELSE, PROCEDURE -> throw new RuntimeException("Tokens of type " + next.tokenType +
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
