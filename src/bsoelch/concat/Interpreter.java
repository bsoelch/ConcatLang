package bsoelch.concat;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Interpreter {

    enum WordState{
        ROOT,STRING,COMMENT,LINE_COMMENT
    }
    enum TokenType {
        VALUE,OPERATOR,DECLARE,CONST_DECLARE,NAME,WRITE_TO,IF,START,ELIF,ELSE,DO,WHILE,END,PROCEDURE,RETURN, PROCEDURE_START,
        DUP,DROP,SWAP,
        SPRINTF,PRINT,PRINTF,PRINTLN,//fprint,fprintln,fprintf
        //jump commands only for internal representation
        JEQ,JNE,JMP,
    }

    record TokenPosition(long line, int posInLine) {
        @Override
        public String toString() {
            return line + ":" + posInLine;
        }
    }

    static class Token {
        final TokenType tokenType;
        final TokenPosition pos;
        Token(TokenType tokenType, TokenPosition pos) {
            this.tokenType = tokenType;
            this.pos = pos;
        }
        @Override
        public String toString() {
            return tokenType.toString();
        }
    }
    static class NamedToken extends Token {
        final String value;
        NamedToken(TokenType type, String value, TokenPosition pos) {
            super(type, pos);
            this.value=value;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": \""+value+"\"";
        }
    }
    static class OperatorToken extends Token {
        final OperatorType opType;
        OperatorToken(OperatorType opType, TokenPosition pos) {
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
        ValueToken(Value value, TokenPosition pos) {
            super(TokenType.VALUE, pos);
            this.value=value;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+value;
        }
    }
    static class JumpToken extends Token{
        final int address;
        JumpToken(TokenType tokenType, TokenPosition pos, int address) {
            super(tokenType, pos);
            this.address = address;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+address;
        }
    }
    static class ProcedureStart extends Token{
        final int end;
        ProcedureStart(TokenPosition pos, int end) {
            super(TokenType.PROCEDURE_START,pos);
            this.end = end;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": end:"+end+"";
        }
    }


    private final Reader input;
    private final StringBuilder buffer=new StringBuilder();
    private Interpreter(Reader input) {
        this.input = input;
    }

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
    static final Pattern floatHex=Pattern.compile(HEX_FLOAT_MAGNITUDE+"([Pp][+-]?"+HEX_DIGIT+"+)?");
    static final Pattern floatBin=Pattern.compile(BIN_FLOAT_MAGNITUDE+"([Ee][+-]?"+BIN_DIGIT+"+)?");

    private WordState state=WordState.ROOT;
    private int stringStart=-1;
    private int cached=-1;

    private int line =1;
    private int posInLine =0;

    private int nextChar() throws IOException {
        if(cached>=0){
            int c=cached;
            cached = -1;
            return c;
        }else{
            return input.read();
        }
    }
    private int forceNextChar() throws IOException {
        int c=nextChar();
        if (c < 0) {
            throw new SyntaxError("Unexpected end of File");
        }
        return c;
    }

    //addLater
    // structs/tuples ?

    private TokenPosition currentPos;
    private TokenPosition currentPos() {
        if(currentPos==null){
            currentPos=new TokenPosition(line, posInLine);
        }
        return currentPos;
    }
    private void nextToken(){
        buffer.setLength(0);
        currentPos=new TokenPosition(line, posInLine);
    }

    //addLater better error feedback
    public ArrayList<Token>  parse() throws IOException {
        ArrayList<Token> tokenBuffer=new ArrayList<>();
        TreeMap<Integer,Token> openBlocks=new TreeMap<>();
        int c;
        nextToken();
        while((c=nextChar())>=0){
            posInLine++;
            if(c=='\n'){//addLater? support for \r line separator
                line++;
                posInLine=0;
            }
            switch(state){
                case ROOT:
                    if(Character.isWhitespace(c)){
                        finishWord(tokenBuffer,openBlocks, buffer);
                    }else{
                        switch (c) {
                            case '"', '\'' -> {
                                state = WordState.STRING;
                                stringStart = (char) c;
                                if(buffer.length()>0) {
                                    throw new SyntaxError("Illegal string prefix:\"" + buffer + "\"");
                                }
                            }
                            case '#' -> {
                                c = forceNextChar();
                                if (c == '#') {
                                    state = WordState.LINE_COMMENT;
                                    finishWord(tokenBuffer,openBlocks, buffer);
                                } else if (c == '_') {
                                    state = WordState.COMMENT;
                                    finishWord(tokenBuffer,openBlocks, buffer);
                                } else {
                                    buffer.append('#').append((char) c);
                                }
                            }
                            default -> buffer.append((char) c);
                        }
                    }
                    break;
                case STRING:
                    if(c==stringStart){
                        if(c=='\''){//char literal
                            if(buffer.codePoints().count()==1){
                                int codePoint = buffer.codePointAt(0);
                                tokenBuffer.add(new ValueToken(Value.ofChar(codePoint), currentPos()));
                            }else{
                                throw new SyntaxError("A char-literal must contain exactly one character");
                            }
                        }else{
                            tokenBuffer.add(new ValueToken(Value.ofString(buffer.toString()),  currentPos()));
                        }
                        nextToken();
                        state=WordState.ROOT;
                    }else{
                        if(c=='\\'){
                            c = forceNextChar();
                            switch (c) {
                                case '\\', '\'', '"' -> buffer.append((char) c);
                                case 'n' -> buffer.append('\n');
                                case 't' -> buffer.append('\t');
                                case 'r' -> buffer.append('\r');
                                case 'b' -> buffer.append('\b');
                                case 'f' -> buffer.append('\f');
                                case '0' -> buffer.append('\0');
                                case 'u', 'U' -> {
                                    int l = c == 'u' ? 4 : 6;
                                    StringBuilder tmp = new StringBuilder(l);
                                    for (int i = 0; i < l; i++) {
                                        tmp.append((char) forceNextChar());
                                    }
                                    buffer.append(Character.toChars(Integer.parseInt(tmp.toString(), 16)));
                                }
                                default -> throw new IllegalArgumentException("The escape sequence: '\\" + c + "' is not supported");
                            }
                        }else{
                            buffer.append((char)c);
                        }
                    }
                    break;
                case COMMENT:
                    if(c=='_'){
                        c = forceNextChar();
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
            case ROOT ->{}
            case STRING ->throw new SyntaxError("unfinished string");
            case COMMENT,LINE_COMMENT -> throw new SyntaxError("unfinished comment");
        }
        if(state!=WordState.ROOT){
            throw new SyntaxError("Unexpected end of File");
        }
        finishWord(tokenBuffer,openBlocks,buffer);
        if(openBlocks.size()>0){
            throw new SyntaxError("unclosed block: "+openBlocks.lastEntry().getValue());
        }
        return tokenBuffer;
    }

    /**@return false if the value was an integer otherwise true*/
    private boolean tryParseInt(ArrayList<Token> tokens, String str){
        try {
            if(intDec.matcher(str).matches()){//dez-Int
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 10)),  currentPos()));
                return false;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 2)),  currentPos()));
                return false;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Long.parseLong(str, 16)),  currentPos()));
                return false;
            }
        } catch (NumberFormatException nfeL) {
            throw new SyntaxError("Number out of Range:"+str);
        }
        return true;
    }

    private void finishWord(ArrayList<Token> tokens,TreeMap<Integer,Token> openBlocks, StringBuilder buffer) {
        if (buffer.length() > 0) {
            String str=buffer.toString();
            try{
                if (tryParseInt(tokens, str)) {
                    if(floatDec.matcher(str).matches()){
                        //dez-Float
                        double d = Double.parseDouble(str);
                        tokens.add(new ValueToken(Value.ofFloat(d),currentPos()));
                    }else if(floatBin.matcher(str).matches()){
                        //bin-Float
                        double d=parseBinFloat(
                                str.replaceAll(BIN_PREFIX,"")//remove header
                        );
                        tokens.add(new ValueToken(Value.ofFloat(d),currentPos()));
                    }else if(floatHex.matcher(str).matches()){
                        //hex-Float
                        double d=parseHexFloat(
                                str.replaceAll(HEX_PREFIX,"")//remove header
                        );
                        tokens.add(new ValueToken(Value.ofFloat(d),currentPos()));
                    }else {
                        if(str.length()>0)
                            addWord(str,tokens,openBlocks);
                    }
                }
            }catch (NumberFormatException nfe){
                throw new SyntaxError(nfe);
            }
            nextToken();
        }
    }

    private void addWord(String str,ArrayList<Token> tokens,TreeMap<Integer,Token> openBlocks) {
        switch (str) {
            case "true" -> tokens.add(new ValueToken(Value.TRUE,  currentPos()));
            case "false" -> tokens.add(new ValueToken(Value.FALSE,  currentPos()));

            case "bool" -> tokens.add(new ValueToken(Value.ofType(Type.BOOL),  currentPos()));
            case "int" -> tokens.add(new ValueToken(Value.ofType(Type.INT),  currentPos()));
            case "char" -> tokens.add(new ValueToken(Value.ofType(Type.CHAR),  currentPos()));
            case "float" -> tokens.add(new ValueToken(Value.ofType(Type.FLOAT),  currentPos()));
            case "string" -> tokens.add(new ValueToken(Value.ofType(Type.STRING()),  currentPos()));
            case "type" -> tokens.add(new ValueToken(Value.ofType(Type.TYPE),  currentPos()));
            case "list" -> tokens.add(new OperatorToken(OperatorType.LIST_OF,  currentPos()));
            case "*->*" -> tokens.add(new ValueToken(Value.ofType(Type.PROCEDURE),currentPos()));
            case "var" -> tokens.add(new ValueToken(Value.ofType(Type.ANY),currentPos()));

            case "cast" ->  tokens.add(new OperatorToken(OperatorType.CAST,  currentPos()));
            case "typeof" ->  tokens.add(new OperatorToken(OperatorType.TYPE_OF,  currentPos()));

            case "dup" -> tokens.add(new Token(TokenType.DUP,  currentPos()));
            case "drop" -> tokens.add(new Token(TokenType.DROP,  currentPos()));
            case "swap" -> tokens.add(new Token(TokenType.SWAP,  currentPos()));

            case "sprintf" -> tokens.add(new Token(TokenType.SPRINTF,  currentPos()));
            case "print" -> tokens.add(new Token(TokenType.PRINT,  currentPos()));
            case "printf" -> tokens.add(new Token(TokenType.PRINTF,  currentPos()));
            case "println" -> tokens.add(new Token(TokenType.PRINTLN,  currentPos()));

            case "if" -> {
                Token t = new Token(TokenType.IF, currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case ":" -> {
                Token s = new Token(TokenType.START, currentPos());
                openBlocks.put(tokens.size(),s);
                tokens.add(s);
            }
            case "elif" -> {
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                if(label.getValue().tokenType==TokenType.IF||label.getValue().tokenType==TokenType.ELIF){
                    Token t = new Token(TokenType.ELIF, currentPos());
                    openBlocks.put(tokens.size(),t);
                    if(label.getValue().tokenType==TokenType.ELIF){//jump before elif to chain jumps
                        tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,tokens.size()));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,tokens.size()));
                }else{
                    throw new SyntaxError("elif has to be preceded with if or elif followed by a :");
                }
            }
            case "else" -> {
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                if(label.getValue().tokenType==TokenType.IF||label.getValue().tokenType==TokenType.ELIF){
                    Token t = new Token(TokenType.ELSE, currentPos());
                    openBlocks.put(tokens.size(),t);
                    if(label.getValue().tokenType==TokenType.ELIF){
                        //jump before else to chain jumps
                        tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,tokens.size()));
                    }
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,tokens.size()));
                }else{
                    throw new SyntaxError("else has to be preceded with if or elif followed by a :");
                }
            }
            case "end" ->{
                Token t = new Token(TokenType.END, currentPos());
                Map.Entry<Integer, Token> start=openBlocks.pollLastEntry();
                if(start==null){
                    throw new SyntaxError("unexpected end statement:"+currentPos());
                }else if(start.getValue().tokenType==TokenType.START){
                    Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                    switch (label.getValue().tokenType){
                        case IF,ELIF -> {//(el)if ... : ... end
                            tokens.add(t);
                            tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,tokens.size()));
                            if(label.getValue().tokenType==TokenType.ELIF){
                                tokens.set(label.getKey(),new JumpToken(TokenType.JMP,label.getValue().pos,tokens.size()));
                            }
                        }
                        case WHILE -> {//while ... : ... end
                            tokens.add(new JumpToken(TokenType.JMP,t.pos,label.getKey()));
                            tokens.set(start.getKey(),new JumpToken(TokenType.JNE,start.getValue().pos,tokens.size()));
                        }
                        case VALUE,OPERATOR,DECLARE,CONST_DECLARE,NAME,WRITE_TO,START,END,ELSE,DO,PROCEDURE,
                                RETURN, PROCEDURE_START,DUP,DROP,SWAP,JEQ,JNE,JMP,SPRINTF,PRINT,PRINTF,PRINTLN
                                -> throw new RuntimeException("Invalid block syntax \""+
                                label.getValue().tokenType+"\"...':' at" +label.getValue().pos);
                    }
                }else if(start.getValue().tokenType==TokenType.WHILE){// do ... while ... end
                    Map.Entry<Integer, Token> label=openBlocks.pollLastEntry();
                    if(label.getValue().tokenType!=TokenType.DO){
                        throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                                " 'do ... while'  or 'else'");
                    }
                    tokens.add(new JumpToken(TokenType.JEQ,t.pos,label.getKey()));
                }else if(start.getValue().tokenType==TokenType.ELSE){// ... else ... end
                    tokens.add(t);
                    tokens.set(start.getKey(),new JumpToken(TokenType.JMP,start.getValue().pos, tokens.size()));
                }else if(start.getValue().tokenType==TokenType.PROCEDURE){// proc ... : ... end
                    tokens.add(new Token(TokenType.RETURN,currentPos()));
                    tokens.add(t);
                    tokens.set(start.getKey(),new ProcedureStart(start.getValue().pos,tokens.size()));
                    tokens.add(new ValueToken(Value.ofProcedureId(start.getKey()+1),currentPos()));
                }else{
                    throw new SyntaxError("'end' can only terminate blocks starting with 'if/elif/while/proc ... :'  " +
                            " 'do ... while'  or 'else' got:"+start.getValue());
                }
            }
            case "while" -> {
                Token t = new Token(TokenType.WHILE, currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "do" -> {
                Token t = new Token(TokenType.DO, currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "proc","procedure" -> {
                Token t = new Token(TokenType.PROCEDURE, currentPos());
                openBlocks.put(tokens.size(),t);
                tokens.add(t);
            }
            case "return" -> tokens.add(new Token(TokenType.RETURN,  currentPos()));

            case "+" -> tokens.add(new OperatorToken(OperatorType.PLUS,currentPos()));
            case "-" -> tokens.add(new OperatorToken(OperatorType.MINUS,currentPos()));
            case "-_" -> tokens.add(new OperatorToken(OperatorType.NEGATE,currentPos()));
            case "/_" -> tokens.add(new OperatorToken(OperatorType.INVERT,currentPos()));
            case "*" -> tokens.add(new OperatorToken(OperatorType.MULT,currentPos()));
            case "/" -> tokens.add(new OperatorToken(OperatorType.DIV,currentPos()));
            case "%" -> tokens.add(new OperatorToken(OperatorType.MOD,currentPos()));
            case "**" -> tokens.add(new OperatorToken(OperatorType.POW,currentPos()));
            case "!" -> tokens.add(new OperatorToken(OperatorType.NOT,currentPos()));
            case "~" -> tokens.add(new OperatorToken(OperatorType.FLIP,currentPos()));
            case "&" -> tokens.add(new OperatorToken(OperatorType.AND,currentPos()));
            case "|" -> tokens.add(new OperatorToken(OperatorType.OR,currentPos()));
            case "xor" -> tokens.add(new OperatorToken(OperatorType.XOR,currentPos()));
            case "<" -> tokens.add(new OperatorToken(OperatorType.LT,currentPos()));
            case "<=" -> tokens.add(new OperatorToken(OperatorType.LE,currentPos()));
            case "==" -> tokens.add(new OperatorToken(OperatorType.EQ,currentPos()));
            case "!=" -> tokens.add(new OperatorToken(OperatorType.NE,currentPos()));
            case ">=" -> tokens.add(new OperatorToken(OperatorType.GE,currentPos()));
            case ">" -> tokens.add(new OperatorToken(OperatorType.GT,currentPos()));

            case ">>" -> tokens.add(new OperatorToken(OperatorType.RSHIFT,currentPos()));
            case ".>>" -> tokens.add(new OperatorToken(OperatorType.SRSHIFT,currentPos()));
            case "<<" -> tokens.add(new OperatorToken(OperatorType.LSHIFT,currentPos()));
            case ".<<" -> tokens.add(new OperatorToken(OperatorType.SLSHIFT,currentPos()));

            case "++" -> tokens.add(new OperatorToken(OperatorType.CONCAT,currentPos()));
            case ">>:" -> tokens.add(new OperatorToken(OperatorType.PUSH_FIRST,currentPos()));
            case ":<<" -> tokens.add(new OperatorToken(OperatorType.PUSH_LAST,currentPos()));
            //<array> <index> []
            case "[]" -> tokens.add(new OperatorToken(OperatorType.AT_INDEX,currentPos()));
            //<array> <off> <to> [:]
            case "[:]" -> tokens.add(new OperatorToken(OperatorType.SLICE,currentPos()));
            //<e0> ... <eN> <N> {}
            case "{}" -> tokens.add(new OperatorToken(OperatorType.NEW_LIST,currentPos()));
            case "length" -> tokens.add(new OperatorToken(OperatorType.LENGTH,currentPos()));
            case "()" -> tokens.add(new OperatorToken(OperatorType.CALL,currentPos()));

            default ->{
                if(str.charAt(0) == '!') {
                    tokens.add(new NamedToken(TokenType.WRITE_TO, str.substring(1), currentPos()));
                }else if(str.charAt(0) == ':') {
                    tokens.add(new NamedToken(TokenType.DECLARE, str.substring(1), currentPos()));
                }else if(str.charAt(0) == '$') {
                    tokens.add(new NamedToken(TokenType.CONST_DECLARE, str.substring(1), currentPos()));
                }else{
                    tokens.add(new NamedToken(TokenType.NAME, str, currentPos()));
                }
            }
        }
    }

    private double parseBinFloat(String str){
        long val=0;
        int c1=0,c2=0;
        int d2=0,e=-1;
        for(int i=0;i<str.length();i++){
            switch (str.charAt(i)){
                case '0':
                case '1':
                    if(c1<63){
                        val*=2;
                        val+=str.charAt(i)-'0';
                        c1++;
                        c2+=d2;
                    }
                    break;
                case '.':
                    if(d2!=0){
                        throw new SyntaxError("Duplicate decimal point");
                    }
                    d2=1;
                    break;
                case 'E':
                case 'e':
                    e=i+1;
                    break;
            }
        }
        if (e > 0) {
            c2-=Integer.parseInt(str.substring(e),2);
        }
        return val*Math.pow(2,-c2);
    }
    private double parseHexFloat(String str){
        long val=0;
        int c1=0,c2=0;
        int d2=0,e=-1;
        for(int i=0;i<str.length();i++){
            switch (str.charAt(i)){
                case '0':case '1':case '2':
                case '3':case '4':case '5':
                case '6':case '7':case '8':
                case '9':
                    if(c1<15){
                        val*=16;
                        val+=str.charAt(i)-'0';
                        c1++;
                        c2+=d2;
                    }
                    break;
                case 'A':case 'B':case 'C':
                case 'D':case 'E':case 'F':
                    if(c1<15){
                        val*=16;
                        val+=str.charAt(i)-'A'+10;
                        c1++;
                        c2+=d2;
                    }
                    break;
                case 'a':case 'b':case 'c':
                case 'd':case 'e':case 'f':
                    if(c1<15){
                        val*=16;
                        val+=str.charAt(i)-'a'+10;
                        c1++;
                        c2+=d2;
                    }
                    break;
                case '.':
                    if(d2!=0){
                        throw new SyntaxError("Duplicate decimal point");
                    }
                    d2=1;
                    break;
                case 'P':
                case 'p':
                    e=i+1;
                    break;
            }
        }
        if (e > 0) {
            c2-=Integer.parseInt(str.substring(e),16);
        }
        return val*Math.pow(2,-c2);
    }

    static class Variable{
        final boolean isConst;
        final Type type;
        private Value value;

        Variable(Type type, boolean isConst, Value value) {
            this.type = type;
            this.isConst = isConst;
            setValue(value);
        }

        public Value getValue() {
            return value;
        }

        public void setValue(Value newValue) {
            value=newValue.castTo(type);
        }
    }
    static class ProgramState{
        final HashMap<String,Variable> variables=new HashMap<>();
        final ProgramState parent;
        int ip;

        ProgramState(int ip,ProgramState parent) {
            this.parent = parent;
            this.ip=ip;
        }

        /**@return  the variable with the name or null if no variable with the given name exists*/
        Variable getVariable(String name){
            Variable var=variables.get(name);
            if(var==null&&parent!=null){
                return parent.getVariable(name);
            }
            return var;
        }

        public ProgramState getParent() {
            return parent;
        }

        /**declares a new Variable with the given name,type and value*/
        public void declareVariable(String name, Type type, boolean isConst, Value value,TokenPosition pos) {
            Variable prev = getVariable(name);
            if(prev!=null) {
                if (prev.isConst){
                    throw new SyntaxError("const variable " + name + " is overwritten " + pos);
                }else if (isConst){
                    throw new SyntaxError("const variable " + name + " is overwrites existing variable " + pos);
                }
            }
            variables.put(name,new Variable(type, isConst, value));
        }
    }

    static Value pop(ArrayDeque<Value> stack){
        Value v=stack.pollLast();
        if(v==null){
            throw new SyntaxError("stack underflow");
        }
        return v;
    }

    public ArrayDeque<Value> run(List<Token>  program){
        ArrayDeque<Value> stack=new ArrayDeque<>();
        ProgramState state=new ProgramState(0,null);
        while(state.ip<program.size()){
            Token next=program.get(state.ip);
            boolean incIp=true;
            switch (next.tokenType){
                case VALUE -> stack.addLast(((ValueToken)next).value);
                case OPERATOR -> {
                    switch (((OperatorToken)next).opType){
                        case NEGATE -> {
                            Value v=pop(stack);
                            stack.push(v.negate());
                        }
                        case INVERT -> {
                            Value v=pop(stack);
                            stack.push(v.invert());
                        }
                        case NOT -> {
                            Value v=pop(stack);
                            stack.push(v.asBool()?Value.FALSE:Value.TRUE);
                        }
                        case FLIP -> {
                            Value v=pop(stack);
                            stack.push(v.flip());
                        }
                        case PLUS->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.mathOp(a,b,(x,y)->Value.ofInt(x+y),(x, y)->Value.ofFloat(x+y)));
                        }
                        case MINUS->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.mathOp(a,b,(x,y)->Value.ofInt(x-y),(x, y)->Value.ofFloat(x-y)));
                        }
                        case MULT->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.mathOp(a,b,(x,y)->Value.ofInt(x*y),(x, y)->Value.ofFloat(x*y)));
                        }
                        case DIV->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.mathOp(a,b,(x,y)->Value.ofInt(x/y),(x, y)->Value.ofFloat(x/y)));
                        }
                        case MOD->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.mathOp(a,b,(x,y)->Value.ofInt(x%y),(x, y)->Value.ofFloat(x%y)));
                        }
                        case POW->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.mathOp(a,b,(x,y)->Value.ofInt(longPow(x,y)),
                                    (x,y)->Value.ofFloat(Math.pow(x,y))));
                        }
                        case EQ,NE,GT,GE,LE,LT ->{
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.compare(a,((OperatorToken)next).opType,b));
                        }
                        case AND -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.logicOp(a,b,(x,y)->x&&y,(x,y)->x&y));
                        }
                        case OR -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.logicOp(a,b,(x,y)->x||y,(x,y)->x|y));
                        }
                        case XOR -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.logicOp(a,b,(x,y)->x^y,(x,y)->x^y));
                        }
                        case LSHIFT -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.intOp(a,b,(x,y)->y<0?x>>>-y:x<<y));
                        }
                        case RSHIFT -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.intOp(a,b,(x,y)->y<0?x<<-y:x>>>y));
                        }
                        case SLSHIFT -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.intOp(a,b,(x,y)->y<0?x>>-y:slshift(x,y)));
                        }
                        case SRSHIFT -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.intOp(a,b,(x,y)->y<0?slshift(x,-y):x>>y));
                        }
                        case LIST_OF -> {
                            Type contentType=pop(stack).asType();
                            stack.addLast(Value.ofType(Type.listOf(contentType)));
                        }
                        case NEW_LIST -> {//e1 e2 ... eN type count {}
                            long count = pop(stack).asLong();
                            Type type  = pop(stack).asType();
                            ArrayDeque<Value> tmp=new ArrayDeque<>((int)count);
                            while(count>0){
                                tmp.addFirst(pop(stack).castTo(type));
                                count--;
                            }
                            ArrayList<Value> list=new ArrayList<>(tmp);
                            stack.addLast(Value.createList(Type.listOf(type),list));
                        }
                        case CAST -> {
                            Type type  = pop(stack).asType();
                            Value val  = pop(stack);
                            stack.addLast(val.castTo(type));
                        }
                        case TYPE_OF -> {
                            Value val  = pop(stack);
                            stack.addLast(Value.ofType(val.type));
                        }
                        case LENGTH -> {
                            Value val  = pop(stack);
                            stack.addLast(Value.ofInt(val.length()));
                        }
                        case AT_INDEX->{//array index []
                            long index = pop(stack).asLong();
                            Value list = pop(stack);
                            stack.addLast(list.get(index));
                        }
                        case SLICE -> {
                            long to = pop(stack).asLong();
                            long off = pop(stack).asLong();
                            Value list = pop(stack);
                            stack.addLast(list.slice(off,to));
                        }
                        case PUSH_FIRST -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.pushFirst(a,b));
                        }
                        case CONCAT -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.concat(a,b));
                        }
                        case PUSH_LAST -> {
                            Value b=pop(stack);
                            Value a=pop(stack);
                            stack.addLast(Value.pushLast(a,b));
                        }
                        case CALL -> {
                            Value procedure = pop(stack);
                            state=new ProgramState(procedure.asProcedure(),state);
                            incIp=false;
                        }
                    }
                }
                case SPRINTF -> {
                    String format=pop(stack).castTo(Type.STRING()).stringValue();
                    StringBuilder build=new StringBuilder();
                    Printf.printf(format,stack, build::append);
                    stack.addLast(Value.ofString(build.toString()));
                }
                case PRINT -> System.out.print(pop(stack).stringValue());
                case PRINTF -> {
                    String format=pop(stack).castTo(Type.STRING()).stringValue();
                    Printf.printf(format,stack, System.out::print);
                }
                case PRINTLN -> System.out.println(pop(stack).stringValue());
                case DECLARE,CONST_DECLARE -> {
                    Value type=pop(stack);
                    Value value=pop(stack);
                    state.declareVariable(((NamedToken)next).value,type.asType(),
                            next.tokenType==TokenType.CONST_DECLARE, value,next.pos);
                }
                case NAME -> {
                    Variable var=state.getVariable(((NamedToken)next).value);
                    if(var==null){
                        throw new SyntaxError("Variable "+((NamedToken)next).value+" does not exist "+next.pos);
                    }
                    stack.addLast(var.getValue());
                }
                case WRITE_TO -> {
                    Variable var=state.getVariable(((NamedToken)next).value);
                    if(var==null){
                        throw new SyntaxError("Variable "+((NamedToken)next).value+" does not exist "+next.pos);
                    }else if(var.isConst){
                        throw new SyntaxError("Tried to overwrite const variable "+((NamedToken)next).value+" "+next.pos);
                    }
                    var.setValue(pop(stack));
                }
                case IF,ELIF,DO,WHILE,END -> {
                    //labels are no-ops
                }
                case START,ELSE, PROCEDURE -> throw new RuntimeException("Tokens of type "+next.tokenType+" should be eliminated at compile time");
                case RETURN -> {
                    state=state.getParent();
                    if(state==null){
                        throw new SyntaxError("call-stack underflow");
                    }
                }
                case PROCEDURE_START -> {
                    state.ip=((ProcedureStart)next).end;
                    incIp=false;
                }
                case DUP -> {
                    Value t=stack.peekLast();
                    if(t==null){
                        throw new SyntaxError("stack underflow");
                    }
                    stack.addLast(t);
                }
                case DROP -> pop(stack);
                case SWAP -> {
                    Value tmp1=pop(stack);
                    Value tmp2=pop(stack);
                    stack.addLast(tmp1);
                    stack.addLast(tmp2);
                }
                case JEQ -> {
                    Value c=pop(stack);
                    if(c.asBool()){
                        state.ip=((JumpToken)next).address;
                        incIp=false;
                    }
                }
                case JNE -> {
                    Value c=pop(stack);
                    if(!c.asBool()){
                        state.ip=((JumpToken)next).address;
                        incIp=false;
                    }
                }
                case JMP -> {
                    state.ip=((JumpToken)next).address;
                    incIp=false;
                }
            }
            if(incIp){
                state.ip++;
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
        Interpreter ip = new Interpreter(new BufferedReader(new FileReader("./examples/test.concat")));
        ArrayList<Token>  prog= ip.parse();
        for(Token t:prog){
            System.out.println(t);
        }
        System.out.println();
        //TODO? compile to C
        ArrayDeque<Value> stack = ip.run(prog);
        System.out.println("\nStack:");
        System.out.println(stack);
    }
}
