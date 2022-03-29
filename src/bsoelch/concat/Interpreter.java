package bsoelch.concat;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public class Interpreter {
    public static final String DEFAULT_FILE_EXTENSION = ".concat";
    //use ' as separator for namespaces, as it cannot be part of identifiers
    public static final String NAMESPACE_SEPARATOR = " .";

    /**
     * Context for running the program
     */
    record IOContext(InputStream stdIn, PrintStream stdOut, PrintStream stdErr) {}
    static final IOContext defaultContext=new IOContext(System.in,System.out,System.err);

    enum TokenType {
        VALUE, DECLARE_LAMBDA,LAMBDA, CURRIED_LAMBDA,OPERATOR,CAST,NEW, NEW_ARRAY,
        STACK_DROP,STACK_DUP,STACK_SET,
        IDENTIFIER,//addLater option to free values/variables
        VARIABLE,
        CONTEXT_OPEN,CONTEXT_CLOSE,
        CALL_PROC, CALL_PTR,
        CALL_NATIVE_PROC,
        RETURN,
        DEBUG_PRINT,ASSERT,UNREACHABLE,//debug operations, may be removed at compile time
        BLOCK_TOKEN,//jump commands only for internal representation
        SWITCH,
        EXIT,
        CAST_ARG, //internal operation to cast function arguments without putting them to the top of the stack
        TUPLE_GET_INDEX,TUPLE_SET_INDEX,//direct access to tuple elements
        //compile time operations
        ARRAY_OF,MEMORY_OF,OPTIONAL_OF,EMPTY_OPTIONAL,
        MARK_MUTABLE,MARK_MAYBE_MUTABLE,MARK_IMMUTABLE,MARK_INHERIT_MUTABILITY,//mutability modifiers
        //overloaded procedure pointer placeholders
        NOP,OVERLOADED_PROC_PTR
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
        READ,WRITE,DECLARE
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
            return tokenType.toString()+" at "+pos;
        }

        public Token replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
            return this;
        }
    }
    enum Accessibility {
        DEFAULT,PUBLIC,READ_ONLY,PRIVATE
    }
    enum IdentifierType{
        DECLARE, WORD,PROC_ID,VAR_WRITE,GET_FIELD,SET_FIELD,IMPLICIT_DECLARE,DECLARE_FIELD
    }
    static class IdentifierToken extends Token {
        static final int FLAG_NATIVE=1;
        static final int ACCESSIBILITY_MASK =6;
        static final int ACCESSIBILITY_DEFAULT=0;
        static final int ACCESSIBILITY_PUBLIC=2;
        static final int ACCESSIBILITY_READ_ONLY=4;
        static final int ACCESSIBILITY_PRIVATE=6;
        static final int MUTABILITY_MASK=24;
        static final int MUTABILITY_DEFAULT=0;
        static final int MUTABILITY_MUTABLE=8;
        static final int MUTABILITY_IMMUTABLE=16;

        final IdentifierType type;
        final int flags;

        final String name;
        IdentifierToken(IdentifierType type, String name, int flags, FilePosition pos) throws SyntaxError {
            super(TokenType.IDENTIFIER, pos);
            if(name.startsWith(".")||name.startsWith(":")||name.startsWith("@")){
                throw new SyntaxError("Identifiers cannot start with '.' ':' or '@'",pos);
            }else if(name.isEmpty()){
                throw new SyntaxError("Identifiers have to be nonempty",pos);
            }//no else
            if(flags!=0&&(type!=IdentifierType.WORD&&type!=IdentifierType.DECLARE &&type!=IdentifierType.IMPLICIT_DECLARE&&
                    type!=IdentifierType.DECLARE_FIELD)){
                throw new SyntaxError("modifiers can only be used in declarations",pos);
            }
            this.flags = flags;
            this.name = name;
            this.type=type;
        }
        @Override
        public String toString() {
            return type.toString()+": \""+ name +"\"";
        }
        Accessibility accessibility(){
            switch (flags&ACCESSIBILITY_MASK){
                case ACCESSIBILITY_PRIVATE ->{
                    return Accessibility.PRIVATE;
                }
                case ACCESSIBILITY_READ_ONLY ->{
                    return Accessibility.READ_ONLY;
                }
                case ACCESSIBILITY_PUBLIC ->{
                    return Accessibility.PUBLIC;
                }
            }
            return Accessibility.DEFAULT;
        }
        boolean isPublicReadable(){
            return accessibility()==Accessibility.PUBLIC||accessibility()==Accessibility.READ_ONLY;
        }
        boolean isNative(){
            return (flags&FLAG_NATIVE)!=0;
        }
        Mutability mutability(){
            switch (flags&MUTABILITY_MASK){
                case MUTABILITY_DEFAULT ->{
                    return type==IdentifierType.IMPLICIT_DECLARE?Mutability.UNDECIDED:Mutability.IMMUTABLE;
                }
                case MUTABILITY_MUTABLE ->{
                    return Mutability.MUTABLE;
                }
                case MUTABILITY_IMMUTABLE ->{
                    return Mutability.IMMUTABLE;
                }
            }
            return Mutability.UNDECIDED;
        }
    }
    static class InternalFieldToken extends Token {
        final InternalFieldName opType;
        InternalFieldToken(InternalFieldName opType, FilePosition pos) {
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
        ValueToken(TokenType type, Value value, FilePosition pos) {
            super(type, pos);
            this.value=value;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+value;
        }

        @Override
        public Token replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
            if(value.type==Type.TYPE){
                try {
                    return new ValueToken(Value.ofType(value.asType().replaceGenerics(genericParams)),pos);
                } catch (TypeError e) {
                    throw new RuntimeException(e);
                }
            }else{//value.type should not contain any generics
                return this;
            }
        }
    }
    static class StackModifierToken extends Token{
        final int[] args;
        StackModifierToken(TokenType type,int[] args,FilePosition pos) {
            super(type,pos);
            this.args=args;
        }
    }
    enum BlockTokenType{
        IF, ELSE, _IF,END_IF, WHILE,DO, END_WHILE, DO_WHILE,SWITCH,CASE,END_CASE,DEFAULT, ARRAY, END
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
    static class CallToken extends Token {
        final Callable called;
        CallToken(Callable called, FilePosition pos) {
            super(TokenType.CALL_PROC, pos);
            this.called = called;
        }
        @Override
        public String toString() {
            return tokenType.toString()+": "+ called;
        }

        //call tokens are only created after generics are resolved
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

        @Override
        public Token replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
            return new TypedToken(tokenType,target==null?null:target.replaceGenerics(genericParams),pos);
        }
    }
    static class ArrayCreatorToken extends Token implements CodeSection {
        final ArrayList<Token> tokens;
        ArrayCreatorToken(ArrayList<Token> tokens, FilePosition pos) {
            super(TokenType.NEW_ARRAY, pos);
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

        @Override
        public Token replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
            boolean changed=false;
            ArrayList<Token> newTokens=new ArrayList<>(tokens.size());
            Token newT;
            for(Token t:tokens){
                newT=t.replaceGenerics(genericParams);
                newTokens.add(newT);
                changed|=newT!=t;
            }
            return changed?new ArrayCreatorToken(newTokens,pos):this;
        }
    }
    static class ArgCastToken extends Token{
        final int offset;
        final Type target;
        ArgCastToken(int offset,Type target,FilePosition pos) {
            super(TokenType.CAST_ARG, pos);
            this.offset=offset;
            this.target=target;
        }

        @Override
        public Token replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
            return new ArgCastToken(offset,target.replaceGenerics(genericParams),pos);
        }
    }
    static class DeclareLambdaToken extends Token{
        final Type[] inTypes;
        final Type[] outTypes;
        final ArrayList<Type.GenericParameter> generics;
        final ArrayList<Token> tokens;
        final ProcedureContext context;
        final FilePosition endPos;
        DeclareLambdaToken(Type[] inTypes, Type[] outTypes, ArrayList<Type.GenericParameter> generics, ArrayList<Token> tokens,
                           ProcedureContext context, FilePosition pos, FilePosition endPos) {
            super(TokenType.DECLARE_LAMBDA, pos);
            this.inTypes = inTypes;
            this.outTypes = outTypes;
            this.generics=generics;
            this.tokens = tokens;
            this.context = context;
            this.endPos=endPos;
        }

        @Override
        public Token replaceGenerics(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
            boolean changed=false;
            Type[] newIn = new Type[inTypes.length];
            for(int i=0;i<inTypes.length;i++){
                newIn[i]=inTypes[i].replaceGenerics(genericParams);
                changed|=newIn[i]!=inTypes[i];
            }
            Type[] newOut = new Type[outTypes.length];
            for(int i=0;i<outTypes.length;i++){
                newOut[i]=outTypes[i].replaceGenerics(genericParams);
                changed|=newOut[i]!=outTypes[i];
            }
            ArrayList<Token> newTokens=new ArrayList<>(tokens.size());
            Token newT;
            for(Token t:tokens){
                newT=t.replaceGenerics(genericParams);
                newTokens.add(newT);
                changed|=newT!=t;
            }
            return changed?new DeclareLambdaToken(newIn,newOut,generics,newTokens,context,pos,endPos):this;
        }
    }
    static class TupleElementAccess extends Token{
        final int index;
        TupleElementAccess(int index, boolean set, FilePosition pos) {
            super(set?TokenType.TUPLE_SET_INDEX:TokenType.TUPLE_GET_INDEX, pos);
            this.index = index;
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
                        case DECLARE ->
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
                if(id.mutability==Mutability.IMMUTABLE){
                    throw new SyntaxError("cannot write to immutable variable "+name,pos);
                }else if(id.mutability==Mutability.UNDECIDED){
                    id.mutability=Mutability.MUTABLE;
                }
            }
        }
        @Override
        public String toString() {
            return variableType+"_"+accessType +":" +(variableType==VariableType.CURRIED?id:id.id)+" ("+variableName+")";
        }
    }

    enum CompilerTokenType{TOKENS,BLOCKS, GLOBAL_CONSTANTS,TYPES,CONTEXT}
    static class CompilerToken extends Token{
        final CompilerTokenType type;
        final int count;
        CompilerToken(CompilerTokenType type, int count, FilePosition pos) {
            super(TokenType.NOP, pos);
            this.type = type;
            this.count = count;
        }
    }

    enum BlockType{
        PROCEDURE,IF,WHILE,SWITCH_CASE,ENUM,TUPLE,ANONYMOUS_TUPLE,PROC_TYPE, CONST_ARRAY,UNION,STRUCT
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

        @Override
        public String toString() {
            return type+"-Block at "+startPos;//addLater overwrite in subclasses
        }
    }
    static class ProcedureBlock extends CodeBlock{
        static final int STATE_IN=0,STATE_OUT=1,STATE_BODY=2;

        final String name;
        final boolean isPublic;
        final ProcedureContext context;
        Type[] inTypes=null,outTypes=null;
        int state=STATE_IN;
        final boolean isNative;

        ProcedureBlock(String name, boolean isPublic, int startToken, FilePosition pos, VariableContext parentContext, boolean isNative) {
            super(startToken, BlockType.PROCEDURE,pos, parentContext);
            this.name = name;
            this.isPublic = isPublic;
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
                    if(v.value.type.canAssignTo(Type.TYPE)){
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
            if(state!=STATE_IN){
                throw new SyntaxError("Procedure already has input arguments",pos);
            }
            inTypes = getSignature(ins,false);
            ins.clear();
            state=STATE_OUT;
        }
        void addOuts(List<Token> outs,FilePosition pos) throws SyntaxError {
            if(state==STATE_IN){
                if(name==null){
                    inTypes = getSignature(outs,false);
                    outs.clear();
                }else{
                    throw new SyntaxError("named procedures cannot have implicit output arguments",pos);
                }
            }else if(state==STATE_OUT){
                outTypes = getSignature(outs,false);
                outs.clear();
            }else{
                throw new SyntaxError("Procedure already has output arguments",pos);
            }
            state=STATE_BODY;
        }
        @Override
        ProcedureContext context() {
            return context;
        }
    }
    record BranchWithEnd(RandomAccessStack<TypeFrame> types,FilePosition end){}
    static class IfBlock extends CodeBlock{
        ArrayList<Integer> elsePositions = new ArrayList<>();
        int forkPos;

        VariableContext ifContext;
        VariableContext elseContext;

        RandomAccessStack<TypeFrame> elseTypes;
        final ArrayDeque<BranchWithEnd> branchTypes=new ArrayDeque<>();

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
            elsePositions.add(tokenPos);
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

        RandomAccessStack<TypeFrame> loopTypes;
        RandomAccessStack<TypeFrame> forkTypes;

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

        final HashMap<Value,Integer> blockJumps = new HashMap<>();
        final ArrayDeque<Integer> blockEnds = new ArrayDeque<>();
        final Type switchType;
        VariableContext context;

        RandomAccessStack<TypeFrame> defaultTypes;
        final ArrayDeque<BranchWithEnd> caseTypes=new ArrayDeque<>();

        SwitchCaseBlock(Type type, int start, FilePosition startPos, VariableContext parentContext) throws SyntaxError {
            super(start, BlockType.SWITCH_CASE, startPos, parentContext);
            if (!type.switchable) {
                throw new SyntaxError("cannot use switch-case on values of type " + type, startPos);
            }
            this.switchType=type;
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
                if(!v.type.canAssignTo(switchType)) {
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
                    ioContext.stdErr.println("missing cases in enum switch-case:");
                    for(int i=0;i<((Type.Enum) switchType).elementCount();i++){
                        if(!blockJumps.containsKey(((Type.Enum) switchType).entries[i])){
                            ioContext.stdErr.println(" - "+((Type.Enum) switchType).entryNames[i]);
                        }
                    }
                    throw new SyntaxError("enum switch-case does not cover all cases",pos);
                }
            }
        }

        @Override
        VariableContext context() {
            return context==null?parentContext:context;
        }

        public boolean hasMoreCases() {
            return (!(switchType instanceof Type.Enum)) || blockJumps.size() < ((Type.Enum) switchType).elementCount();
        }
    }
    private static class EnumBlock extends CodeBlock{
        final String name;
        final boolean isPublic;
        final ArrayList<String> elements=new ArrayList<>();
        final ArrayList<FilePosition> elementPositions=new ArrayList<>();
        EnumBlock(String name,boolean isPublic, int start, FilePosition startPos, VariableContext parentContext) {
            super(start, BlockType.ENUM, startPos, parentContext);
            this.name=name;
            this.isPublic=isPublic;
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
        final boolean isPublic;
        final GenericContext context;

        TupleBlock(String name,boolean isPublic,int start, FilePosition startPos, VariableContext parentContext) {
            super(start, BlockType.TUPLE, startPos, parentContext);
            this.name=name;
            this.isPublic=isPublic;
            context=new GenericContext(parentContext, false);
        }

        @Override
        VariableContext context() {
            return context;
        }
    }
    private static class ArrayBlock extends CodeBlock{
        RandomAccessStack<TypeFrame> prevTypes;
        final VariableContext context;
        ArrayBlock(int start, BlockType type, FilePosition startPos, VariableContext parentContext) {
            super(start, type, startPos, parentContext);
            this.context=new BlockContext(parentContext);
        }
        @Override
        VariableContext context() {
            return context;
        }
    }
    private static class ProcTypeBlock extends CodeBlock{
        final int separatorPos;
        final BlockContext context;
        ProcTypeBlock(ArrayBlock start, int separatorPos) {
            super(start.start,BlockType.PROC_TYPE,start.startPos, start.parentContext);
            this.separatorPos=separatorPos;
            assert start.type==BlockType.ANONYMOUS_TUPLE;
            context=(BlockContext)start.context;
        }
        @Override
        VariableContext context() {
            return context;
        }
    }
    private static class StructBlock extends CodeBlock{
        final String name;
        final boolean isPublic;
        final StructContext context;
        Type.Struct extended;

        StructBlock(String name,boolean isPublic,int start, FilePosition startPos, VariableContext parentContext) {
            super(start, BlockType.STRUCT, startPos, parentContext);
            this.name=name;
            this.isPublic=isPublic;
            context=new StructContext(parentContext,false);
        }

        @Override
        VariableContext context() {
            return context;
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
                throw new SyntaxError("unexpected end of File",currentPos());
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
        VARIABLE,CURRIED_VARIABLE,MACRO,PROCEDURE,ENUM, TUPLE, GENERIC, NATIVE_PROC,
        GENERIC_TUPLE, OVERLOADED_PROCEDURE, STRUCT, GENERIC_STRUCT, GENERIC_PROCEDURE,CONSTANT
    }
    static String declarableName(DeclareableType t, boolean a){
        switch (t){
            case CONSTANT -> {
                return a?"a constant":"constant";
            }
            case VARIABLE -> {
                return a?"a variable":"variable";
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
            case GENERIC_PROCEDURE -> {
                return a?"a generic procedure":"generic procedure";
            }
            case ENUM -> {
                return a?"an enum":"enum";
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
            case OVERLOADED_PROCEDURE -> {
                return a?"an overloaded procedure":"overloaded procedure";
            }
            case STRUCT -> {
                return a?"a struct":"struct";
            }
            case GENERIC_STRUCT -> {
                return a?"a generic struct":"generic struct";
            }
        }
        throw new RuntimeException("unreachable");
    }
    interface Declareable{
        DeclareableType declarableType();
        FilePosition declaredAt();
        boolean isPublic();
        default Accessibility accessibility() {
            return isPublic()?Accessibility.PUBLIC:Accessibility.PRIVATE;
        }
        void markAsUsed();
        boolean unused();
    }
    interface NamedDeclareable extends Declareable{
        String name();
    }
    interface Callable extends NamedDeclareable{
        Type.Procedure type();
    }
    static boolean isCallable(DeclareableType type){
        switch (type){
            case PROCEDURE,NATIVE_PROC,OVERLOADED_PROCEDURE,GENERIC_PROCEDURE -> {
                return true;
            }
            case VARIABLE,CURRIED_VARIABLE,MACRO,
                    ENUM,TUPLE,GENERIC,GENERIC_TUPLE,STRUCT,GENERIC_STRUCT,CONSTANT -> {
                return false;
            }
        }
        return false;
    }
    static class Macro implements NamedDeclareable{
        final FilePosition pos;
        final String name;
        final boolean isPublic;
        final ArrayList<StringWithPos> content;
        Macro(FilePosition pos, String name,boolean isPublic,ArrayList<StringWithPos> content) {
            this.pos=pos;
            this.name=name;
            this.isPublic=isPublic;
            this.content=content;
        }
        @Override
        public String toString() {
            return "macro " +name+":"+content;
        }
        @Override
        public DeclareableType declarableType() {
            return DeclareableType.MACRO;
        }

        @Override
        public String name() {
            return name;
        }
        @Override
        public FilePosition declaredAt() {
            return pos;
        }
        @Override
        public boolean isPublic() {
            return isPublic;
        }

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }

    static final class Constant implements NamedDeclareable {
        final String name;
        final boolean isPublic;
        final Value value;
        final FilePosition declaredAt;

        Constant(String name, boolean isPublic, Value value,
                 FilePosition declaredAt) {
            this.name = name;
            this.isPublic = isPublic;
            this.value = value;
            this.declaredAt = declaredAt;
        }

        @Override
        public String name() {
            return name;
        }
        @Override
        public boolean isPublic() {
            return isPublic;
        }
        @Override
        public DeclareableType declarableType() {
            return DeclareableType.CONSTANT;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }

    private static class VariableId implements Declareable{
        final VariableContext context;
        final int level;
        final Type type;
        final Accessibility accessibility;
        final int id;
        Mutability mutability;
        final FilePosition declaredAt;
        VariableId(VariableContext context, int level, int id, Type type, Mutability mutability,
                   Accessibility accessibility, FilePosition declaredAt){
            this.context=context;
            this.id=id;
            this.level=level;
            this.type=type;
            this.mutability = mutability;
            this.accessibility = accessibility==Accessibility.DEFAULT?Accessibility.PRIVATE:accessibility;
            this.declaredAt=declaredAt;
        }
        @Override
        public String toString() {
            return "VariableId{id="+id+", type="+type+", accessibility="+accessibility+", mutability="+
                    mutability+", declaredAt="+declaredAt+", context="+context+"}";
        }

        @Override
        public boolean isPublic() {
            return accessibility==Accessibility.PUBLIC;
        }
        @Override
        public Accessibility accessibility() {
            return accessibility;
        }

        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
        @Override
        public DeclareableType declarableType() {
            return DeclareableType.VARIABLE;
        }
        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }
    private static class CurriedVariable extends VariableId{
        final VariableId source;
        CurriedVariable(VariableId source,VariableContext context, int id, FilePosition declaredAt) {
            super(context,0, id, source.type, Mutability.IMMUTABLE, Accessibility.READ_ONLY, declaredAt);
            assert source.mutability==Mutability.IMMUTABLE;
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

    private static final class GenericTuple implements NamedDeclareable {
        final String name;
        final boolean isPublic;
        final Type.GenericParameter[] params;
        final Type[] types;
        final FilePosition declaredAt;

        private GenericTuple(String name, boolean isPublic, Type.GenericParameter[] params,
                             Type[] types,
                             FilePosition declaredAt) {
            this.name = name;
            this.isPublic = isPublic;
            this.params = params;
            this.types = types;
            this.declaredAt = declaredAt;
        }

        @Override
        public DeclareableType declarableType() {
            return DeclareableType.GENERIC_TUPLE;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isPublic() {
            return isPublic;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }

    private static final class GenericStruct implements NamedDeclareable {
        final String name;
        final boolean isPublic;
        final Type.Struct extended;
        final Type.GenericParameter[] params;
        final Type[] types;
        final Type.StructField[] fields;
        final FilePosition declaredAt;

        private GenericStruct(String name, boolean isPublic, Type.Struct extended, Type.GenericParameter[] params,
                              Type[] types,Type.StructField[] fields, FilePosition declaredAt) {
            this.name = name;
            this.isPublic = isPublic;
            this.extended = extended;
            this.params = params;
            this.types = types;
            this.fields = fields;
            this.declaredAt = declaredAt;
        }

        @Override
        public DeclareableType declarableType() {
            return DeclareableType.GENERIC_STRUCT;
        }
        @Override
        public String name() {
            return name;
        }
        @Override
        public boolean isPublic() {
            return isPublic;
        }
        public FilePosition declaredAt() {
            return declaredAt;
        }
        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }

    static abstract class VariableContext{
        int variables=0;

        public int varCount() {
            return variables;
        }
        /**
         * @param merge if true all procedures of the given name will be merged
         *             the returned value should not be modified if merge is true
        * */
        protected abstract Declareable getElement(String name,boolean merge);
        protected abstract boolean containsElement(String name);
        protected abstract Declareable putElement(String name,Declareable val);

        void ensureDeclareable(String name, DeclareableType type, FilePosition pos) throws SyntaxError {
            Declareable prev= getElement(name,false);
            if(prev!=null&&!(isCallable(type)&&(prev instanceof Callable||prev instanceof OverloadedProcedure))){
                throw new SyntaxError("cannot declare " + declarableName(type,false) + " "+name+
                        ", the identifier is already used by " + declarableName(prev.declarableType(), true)
                        + " (declared at " + prev.declaredAt() + ")",pos);
            }
        }

        int nextVarId() {
            return variables++;
        }
        VariableId declareVariable(String name, Type type, Mutability mutability, Accessibility accessibility,
                                   FilePosition pos, IOContext ioContext) throws SyntaxError {
            ensureDeclareable(name, DeclareableType.VARIABLE,pos);
            VariableId id = new VariableId(this,level(), nextVarId(), type, mutability, accessibility, pos);
            putElement(name, id);
            return id;
        }
        abstract VariableId wrapCurried(String name,VariableId id,FilePosition pos) throws SyntaxError;
        Declareable merge(Declareable main,Declareable shadowed){
            if(main==null){
                return shadowed;
            }else if(main instanceof Callable){
                OverloadedProcedure merged=new OverloadedProcedure((Callable) main);
                if(shadowed instanceof Callable){
                    try {
                        if(!merged.addProcedure((Callable) shadowed,true)){//TODO better error messages
                            System.err.println("cannot merge "+shadowed+" into "+merged);
                        }
                    } catch (SyntaxError e) {throw new RuntimeException(e);}
                    return merged;
                }else if(shadowed instanceof OverloadedProcedure){
                    try {
                        for(Callable c:((OverloadedProcedure) shadowed).procedures){
                            merged.addProcedure(c,true);
                        }
                    } catch (SyntaxError e) {throw new RuntimeException(e);}
                    return merged;
                }
            }else if(main instanceof OverloadedProcedure){
                OverloadedProcedure merged=new OverloadedProcedure((OverloadedProcedure) main);
                if(shadowed instanceof Callable){
                    try {
                        merged.addProcedure((Callable) shadowed,true);
                    } catch (SyntaxError e) {throw new RuntimeException(e);}
                    return merged;
                }else if(shadowed instanceof OverloadedProcedure){
                    try {
                        for(Callable c:((OverloadedProcedure) shadowed).procedures){
                            merged.addProcedure(c,true);
                        }
                    } catch (SyntaxError e) {throw new RuntimeException(e);}
                    return merged;
                }
            }
            return main;
        }
        abstract Declareable getDeclareable(String name);
        /**returns the enclosing procedure or null if this variable is not enclosed in a procedure*/
        abstract ProcedureContext procedureContext();
        /**number of blocks (excluding procedures) this variable is contained in*/
        abstract int level();
    }

    private static abstract  class TopLevelContext extends VariableContext{
        final ArrayList<String> imports=new ArrayList<>();

        abstract RootContext root();

        void addImport(String path,FilePosition pos) throws SyntaxError {
            if(namespaces().contains(path)){
                path+=NAMESPACE_SEPARATOR;
                imports.add(path);
            }else if(containsElement(path)){
                //addLater static imports
                throw new UnsupportedOperationException("static imports are currently unimplemented");
            }else{
                throw new SyntaxError("namespace "+path+" does not exist",pos);
            }
        }

        HashSet<String> namespaces() {
            return root().namespaces;
        }

        abstract String namespace();

        Declareable getDeclareable(String name){
            Declareable d=getElement(name,true);
            ArrayDeque<String> paths = new ArrayDeque<>(imports);
            while(paths.size()>0){//check all imports
                d=merge(d,root().getElement(paths.removeLast()+name,true));
                if(d!=null&&!isCallable(d.declarableType())){
                    return d;
                }
            }
            if(this instanceof NamespaceContext){//check parent namespace (if existent)
                return merge(d, ((NamespaceContext) this).parent.getDeclareable(name));
            }else{
                return d;
            }
        }
        void checkShadowed(DeclareableType declaredType,String name,FilePosition pos,IOContext ioContext){
            Declareable shadowed = getDeclareable(name);
            if(shadowed!=null&&!(isCallable(declaredType)&&(shadowed instanceof Callable||shadowed instanceof OverloadedProcedure))){
                ioContext.stdErr.println("Warning: "+declarableName(declaredType,false)+" " + name
                        + " declared at " + pos +"\n     shadows existing " +
                        declarableName(shadowed.declarableType(),false)+ " declared at "+ shadowed.declaredAt());
            }
        }

        void declareProcedure(Callable proc,IOContext ioContext) throws SyntaxError {
            String name= proc.name();
            ensureDeclareable(name,proc.declarableType(),proc.declaredAt());
            checkShadowed(proc.declarableType(),proc.name(),proc.declaredAt(),ioContext);
            Declareable prev = getElement(name,false);
            if(prev instanceof Callable){
                if(prev.isPublic() == proc.isPublic()){
                    OverloadedProcedure overloaded=new OverloadedProcedure((Callable)prev);
                    overloaded.addProcedure(proc,false);
                    putElement(name,overloaded);
                }else{
                    putElement(name,proc);
                }
            }else if(prev instanceof OverloadedProcedure overloaded){
                if(prev.isPublic() == proc.isPublic()) {
                    overloaded.addProcedure(proc, false);
                }else{
                    putElement(name,proc);
                }
            }else if(prev==null){
                putElement(name,proc);
            }else{
                throw new RuntimeException("this path should be covered be ensureDeclareable");
            }
        }

        void declareEnum(EnumBlock source, IOContext ioContext) throws SyntaxError {
            Type.Enum anEnum=new Type.Enum(source.name, source.isPublic, source.elements.toArray(new String[0]),
                    source.startPos);
            declareNamedDeclareable(anEnum,ioContext);
        }
        void declareNamedDeclareable(NamedDeclareable declareable, IOContext ioContext) throws SyntaxError {
            String name= declareable.name();
            ensureDeclareable(name,declareable.declarableType(),declareable.declaredAt());
            checkShadowed(declareable.declarableType(),declareable.name(),declareable.declaredAt(),ioContext);
            putElement(name,declareable);
        }

        @Override
        int nextVarId() {
            return this instanceof RootContext?super.nextVarId():root().nextVarId();//declare variables in root
        }
        @Override
        VariableId declareVariable(String name, Type type, Mutability mutability, Accessibility accessibility,
                                   FilePosition pos, IOContext ioContext) throws SyntaxError {
            checkShadowed(DeclareableType.VARIABLE,name,pos,ioContext);
            return super.declareVariable(name, type, mutability, accessibility, pos,ioContext);
        }
        @Override
        VariableId wrapCurried(String name, VariableId id, FilePosition pos){
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
    static class RootContext extends TopLevelContext{
        private final HashMap<String,Declareable> elements =new HashMap<>();
        HashSet<String> namespaces=new HashSet<>();

        RootContext() throws SyntaxError {
            for(Value.InternalProcedure proc:Value.internalProcedures()){
                Declareable prev=getElement(proc.name,false);
                if(prev==null){
                    putElement(proc.name,proc);
                }else if(prev instanceof Callable){
                    OverloadedProcedure overload=new OverloadedProcedure((Callable)prev);
                    overload.addProcedure(proc,false);
                    putElement(proc.name,overload);
                }else if(prev instanceof OverloadedProcedure overload){
                    overload.addProcedure(proc,false);
                }else{
                    throw new UnsupportedOperationException("multiple declarations of internal constant "+proc.name+":\n"
                            +prev+", "+proc);
                }
            }
        }
        Iterable<Map.Entry<String,Declareable>> declareables(){
            return elements.entrySet();
        }
        @Override
        RootContext root() {
            return this;
        }
        @Override
        protected Declareable getElement(String name,boolean merge){
            return elements.get(name);
        }
        @Override
        protected boolean containsElement(String name){
            return elements.containsKey(name);
        }
        @Override
        protected Declareable putElement(String name,Declareable val){
            return elements.put(name,val);
        }

        @Override
        String namespace() {
            return "";
        }

        @Override
        public String toString() {
            return "root ";
        }
    }
    private static class FileContext extends TopLevelContext{
        final RootContext root;
        final String fileName;
        final HashMap<String,Declareable> localDeclareables =new HashMap<>();
        FileContext(RootContext root,String fileName) {
            this.root = root;
            this.fileName = fileName;
        }
        @Override
        RootContext root() {
            return root;
        }
        @Override
        protected Declareable getElement(String name, boolean merge) {
            Declareable d=localDeclareables.get(name);
            if(d!=null){
                if(merge&&isCallable(d.declarableType())){
                    return merge(d,root.getElement(name,true));
                }
                return d;
            }
            return root.getElement(name, merge);
        }
        @Override
        protected boolean containsElement(String name) {
            return localDeclareables.containsKey(name)||root.containsElement(name);
        }
        @Override
        protected Declareable putElement(String name, Declareable val) {
            Accessibility access=val.accessibility();
            if(access==Accessibility.PRIVATE){
                return localDeclareables.put(name, val);
            }
            return root.putElement(name,val);
        }
        @Override
        String namespace() {
            return "";
        }
        @Override
        public String toString() {
            return root+"("+fileName+") ";
        }
    }
    private static class NamespaceContext extends TopLevelContext{
        final TopLevelContext parent;
        final String prefix;
        NamespaceContext(String namespace,TopLevelContext parent) {
            this.parent = parent;
            this.prefix = namespace+NAMESPACE_SEPARATOR;

            if(parent.namespace().length()>0){//add namespace path to namespaces
                root().namespaces.add(parent.namespace()+NAMESPACE_SEPARATOR+namespace);
            }else{
                root().namespaces.add(namespace);
            }
        }
        @Override
        RootContext root() {
            return parent.root();
        }
        @Override
        protected Declareable getElement(String name, boolean merge) {
            return parent.getElement(prefix+name,merge);
        }
        @Override
        protected boolean containsElement(String name) {
            return parent.containsElement(prefix+name);
        }
        @Override
        protected Declareable putElement(String name, Declareable val) {
            return parent.putElement(prefix+name,val);
        }
        @Override
        String namespace() {
            return parent.namespace()+prefix;
        }

        @Override
        public String toString() {
            return parent.toString()+prefix;
        }
    }
    private static class BlockContext extends VariableContext {
        private final HashMap<String,Declareable> elements =new HashMap<>();
        final VariableContext parent;

        public BlockContext(VariableContext parent) {
            this.parent = parent;
            assert parent!=null;
        }

        @Override
        protected Declareable getElement(String name,boolean merge){
            return elements.get(name);
        }
        @Override
        protected boolean containsElement(String name){
            return elements.containsKey(name);
        }
        @Override
        protected Declareable putElement(String name,Declareable val){
            return elements.put(name,val);
        }

        @Override
        VariableId declareVariable(String name, Type type, Mutability mutability, Accessibility accessibility,
                                   FilePosition pos, IOContext ioContext) throws SyntaxError {
            VariableId id = super.declareVariable(name, type, mutability, accessibility, pos,ioContext);
            Declareable shadowed = parent.getDeclareable(name);
            if (shadowed != null) {//check for shadowing
                ioContext.stdErr.println("Warning: variable " + name + " declared at " + pos +
                        "\n     shadows existing " + declarableName(shadowed.declarableType(),false)
                        + " declared at "  + shadowed.declaredAt());
            }
            return id;
        }

        @Override
        Declareable getDeclareable(String name) {
            return merge(getElement(name,true),parent.getDeclareable(name));
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

        @Override
        public String toString() {
            return parent+" - ";
        }
    }
    static class GenericContext extends BlockContext{
        ArrayList<Type.GenericParameter> generics=new ArrayList<>();
        final boolean allowImplicit;

        boolean locked=false;

        public GenericContext(VariableContext parent, boolean allowImplicit) {
            super(parent);
            this.allowImplicit = allowImplicit;
        }
        void declareGeneric(String name, boolean isImplicit, FilePosition pos, IOContext ioContext) throws SyntaxError {
            if(locked){
                throw new SyntaxError("declaring generics is not allowed in the current context",pos);
            }else if(isImplicit&&!allowImplicit){
                throw new SyntaxError("implicit generics are not allowed in the current context",pos);
            }
            Type.GenericParameter generic;
            ensureDeclareable(name,DeclareableType.GENERIC,pos);
            generic = new Type.GenericParameter(name, generics.size(), isImplicit, pos);
            generics.add(generic);
            putElement(name, generic);
            Declareable shadowed = parent.getDeclareable(name);
            if (shadowed != null) {//check for shadowing
                ioContext.stdErr.println("Warning: "+declarableName(generic.declarableType(),false)+" " + name +
                        " declared at " + pos +"\n     shadows existing " + declarableName(shadowed.declarableType(),false)
                        + " declared at "  + shadowed.declaredAt());
            }
        }
        //disable declaration of generics
        void lock(){
            locked=true;
        }
    }

    static class ProcedureContext extends GenericContext {
        ArrayList<CurriedVariable> curried=new ArrayList<>();
        ProcedureContext(VariableContext parent){
            super(parent, true);
            assert parent!=null;
        }

        VariableId wrapCurried(String name,VariableId id,FilePosition pos) throws SyntaxError {
            ProcedureContext procedure = id.context.procedureContext();
            if(procedure !=this){
                id=parent.wrapCurried(name, id, pos);//curry through parent
                if(id.mutability==Mutability.MUTABLE){
                    throw new SyntaxError("cannot curry mutable variable "+name+" (declared at "+id.declaredAt+")",pos);
                }
                id.mutability=Mutability.IMMUTABLE;
                if(!(id.context instanceof TopLevelContext)){
                    id=new CurriedVariable(id,this, curried.size(), pos);
                    curried.add((CurriedVariable)id);//curry variable
                    putElement(name,id);//add curried variable to variable list
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

    record StructFieldWithType(Type.StructField field, Type type){}
    static class StructContext extends GenericContext{
        final ArrayList<StructFieldWithType> fields=new ArrayList<>();
        StructContext(VariableContext parent, boolean allowImplicit) {
            super(parent, allowImplicit);
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
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,10,unsigned), unsigned), pos));
                return true;
            }else if(intBin.matcher(str).matches()){//bin-Int
                str=str.replaceAll(BIN_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,2,unsigned), unsigned), pos));
                return true;
            }else if(intHex.matcher(str).matches()){ //hex-Int
                str=str.replaceAll(HEX_PREFIX,"");//remove header
                tokens.add(new ValueToken(Value.ofInt(Value.parseInt(str,16,unsigned), unsigned), pos));
                return true;
            }
        } catch (ConcatRuntimeError nfeL) {
            throw new SyntaxError("Number out of Range: "+str0,pos);
        }
        return false;
    }

    record TypeFrame(Type type,Value value,FilePosition pushedAt){}
    static class ParserState {
        final ArrayList<Token> tokens =new ArrayList<>();
        /**global code after type-check*/
        final ArrayList<Token> globalCode =new ArrayList<>();

        final ArrayDeque<CodeBlock> openBlocks=new ArrayDeque<>();

        RandomAccessStack<TypeFrame> typeStack=new RandomAccessStack<>(64);

        final RootContext rootContext;
        final ArrayDeque<TopLevelContext> openedFiles=new ArrayDeque<>();
        private TopLevelContext topLevelContext;
        void startFile(String name){
            if(topLevelContext!=null){
                openedFiles.add(topLevelContext);
            }
            topLevelContext=new FileContext(rootContext,name);
        }
        void startNamespace(String name){
            if(topLevelContext==null){
                throw new IllegalArgumentException("namespaces cannot exist outside of files");
            }
            topLevelContext=new NamespaceContext(name,topLevelContext);
        }
        void endNamespace(FilePosition pos) throws SyntaxError {
            if(topLevelContext instanceof NamespaceContext){
                topLevelContext=((NamespaceContext)topLevelContext).parent;
            }else{
                throw new SyntaxError("unexpected end of namespace",pos);
            }
        }
        void endFile(){
            //addLater report unclosed namespaces
            topLevelContext=openedFiles.pollLast();
        }
        final HashSet<String> files=new HashSet<>();
        public HashMap<VariableId, Value> globalConstants =new HashMap<>();
        //proc-contexts that are currently open
        final ArrayDeque<VariableContext> openedContexts =new ArrayDeque<>();

        Macro currentMacro;//may be null

        ParserState(RootContext rootContext) {
            this.rootContext = rootContext;
        }

        TopLevelContext topLevelContext(){
            return topLevelContext==null?rootContext:topLevelContext;
        }
        VariableContext getContext(){
            VariableContext currentProc = openedContexts.peekLast();
            return currentProc==null?topLevelContext():currentProc;
        }
    }
    public Program parse(File file, ParserState pState, IOContext ioContext) throws IOException, SyntaxError {
        ParserReader reader=new ParserReader(file.getAbsolutePath());
        int c;
        String fileId=null;
        while((c=reader.nextChar())>=0){
            if(c==':'){
                fileId=reader.buffer.toString().trim();
                break;
            }else if(c=='\n'||c=='\r'){
                break;//invalid fileId
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
        }
        //ensure that each file is included only once
        pState.files.add(fileId);

        pState.startFile(fileId);

        reader.nextToken();
        WordState state=WordState.ROOT;
        int nComments=0;//counts the currently open block-comments
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
                                    nComments=1;
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
                    if(c=='+'){
                        c = reader.forceNextChar();
                        if(c=='#'){
                            nComments--;
                            if(nComments==0){
                                state=WordState.ROOT;
                            }
                        }
                    }else if(c=='#'){
                        c = reader.forceNextChar();
                        if(c=='+'){
                            nComments++;
                        }else if(c=='#'){
                            state=WordState.LINE_COMMENT;
                        }
                    }
                    break;
                case LINE_COMMENT:
                    if(c=='\n'||c=='\r'){
                        state=nComments>0?WordState.COMMENT:WordState.ROOT;
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
        finishParsing(pState, ioContext,reader.currentPos());
        Declareable main=pState.topLevelContext().getDeclareable("main");
        if(main instanceof Value.Procedure){
            typeCheckProcedure((Value.Procedure) main,pState.globalConstants,ioContext);
            if(!((Value.Procedure) main).isPublic){
                ioContext.stdErr.println("Warning: procedure main at "+((Value.Procedure) main).declaredAt+" is private");
            }
        }

        if(pState.openBlocks.size()>0){
            throw new SyntaxError("unclosed block: "+pState.openBlocks.getLast(),pState.openBlocks.getLast().startPos);
        }

        pState.endFile();

        return new Program(pState.globalCode,pState.files,pState.rootContext);
    }

    private void finishWord(String str, ParserState pState, FilePosition pos, IOContext ioContext) throws SyntaxError {
        if (str.length() > 0) {
            if(str.startsWith("#compiler:")){
                String[] commands=str.substring("#compiler:".length()).split(":");
                switch (commands[0]){
                    case "tokens"->{
                        int n=Integer.parseInt(commands[1]);
                        ArrayList<Token> tokens = pState.tokens;
                        if(n>tokens.size()){
                            System.out.println("n > #tokens ("+tokens.size()+")");
                            n=tokens.size();
                        }
                        System.out.println("tokens:");
                        for(int k=1;k<=n;k++){
                            System.out.println("  "+tokens.get(tokens.size()-k));
                        }
                    }
                    case "globalCode"->{
                        int n=Integer.parseInt(commands[1]);
                        ArrayList<Token> tokens = pState.globalCode;
                        if(n>tokens.size()){
                            System.out.println("n > #tokens ("+tokens.size()+")");
                            n=tokens.size();
                        }
                        System.out.println("tokens:");
                        for(int k=1;k<=n;k++){
                            System.out.println("  "+tokens.get(tokens.size()-k));
                        }
                    }
                    case "globalBlocks"->{
                        int n=Integer.parseInt(commands[1]);
                        ArrayDeque<CodeBlock> blocks = pState.openBlocks;
                        if(n>blocks.size()){
                            System.out.println("n > #blocks ("+blocks.size()+")");
                            n=blocks.size();
                        }
                        System.out.println("globalBlocks:");
                        CodeBlock[] blockArray = blocks.toArray(CodeBlock[]::new);
                        for(int k=1;k<=n;k++){
                            System.out.println("  "+blockArray[blocks.size()-k]);
                        }
                    }
                    case "code"->{
                        int n=Integer.parseInt(commands[1]);
                        pState.tokens.add(new CompilerToken(CompilerTokenType.TOKENS,n,pos));
                    }
                    case "blocks"->{
                        int n=Integer.parseInt(commands[1]);
                        pState.tokens.add(new CompilerToken(CompilerTokenType.BLOCKS,n,pos));
                    }
                    case "types"->{
                        int n=Integer.parseInt(commands[1]);
                        pState.tokens.add(new CompilerToken(CompilerTokenType.TYPES,n,pos));
                    }
                    case "globalConstants"->
                            pState.tokens.add(new CompilerToken(CompilerTokenType.GLOBAL_CONSTANTS,0,pos));
                    case "context"->
                            pState.tokens.add(new CompilerToken(CompilerTokenType.CONTEXT,0,pos));
                    default ->
                        System.out.println("unknown compiler command: "+commands[0]);
                }
                return;
            }
            ArrayList<Token> tokens = pState.tokens;
            Token prev= tokens.size()>0? tokens.get(tokens.size()-1):null;
            String prevId=(prev instanceof IdentifierToken &&((IdentifierToken) prev).type == IdentifierType.WORD)?
                    ((IdentifierToken)prev).name:null;
            if(pState.currentMacro!=null){
                if(str.equals("#end")){
                    pState.topLevelContext().declareNamedDeclareable(pState.currentMacro,ioContext);
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
                        tokens.add(new ValueToken(Value.ofByte((byte)codePoint), pos));
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
                    tokens.add(new ValueToken(Value.ofChar(codePoint), pos));
                }else{
                    throw new SyntaxError("A char-literal must contain exactly one codepoint", pos);
                }
                return;
            }else if(str.charAt(0)=='"'){
                str=str.substring(1);
                tokens.add(new ValueToken(Value.ofString(str,false),  pos));
                return;
            }else if(str.startsWith("u\"")){
                str=str.substring(2);
                tokens.add(new ValueToken(Value.ofString(str,true),  pos));
                return;
            }
            try{
                if (tryParseInt(tokens, str,pos)) {
                    return;
                }else if(floatDec.matcher(str).matches()){
                    //dez-Float
                    double d = Double.parseDouble(str);
                    tokens.add(new ValueToken(Value.ofFloat(d), pos));
                    return;
                }else if(floatBin.matcher(str).matches()){
                    //bin-Float
                    double d= Value.parseFloat(str.substring(BIN_PREFIX.length()),2);
                    tokens.add(new ValueToken(Value.ofFloat(d), pos));
                    return;
                }else if(floatHex.matcher(str).matches()){
                    //hex-Float
                    double d=Value.parseFloat(str.substring(BIN_PREFIX.length()),16);
                    tokens.add(new ValueToken(Value.ofFloat(d), pos));
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
                        finishParsing(pState, ioContext,pos);
                        pState.currentMacro=new Macro(pos,prevId,((IdentifierToken) prev).isPublicReadable(),new ArrayList<>());
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
                        finishParsing(pState, ioContext,pos);
                        pState.startNamespace(prevId);
                    }else{
                        throw new SyntaxError("namespace name has to be an identifier",pos);
                    }
                }
                case "#end"-> {
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("namespaces can only be closed at root-level",pos);
                    }else{
                        finishParsing(pState, ioContext,pos);
                        pState.endNamespace(pos);
                    }
                }
                case "#import"-> {
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("imports are can only allowed at root-level",pos);
                    }
                    if(prevId != null){
                        tokens.remove(tokens.size()-1);
                        finishParsing(pState, ioContext,pos);
                        pState.topLevelContext().addImport(prevId,pos);
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
                        finishParsing(pState, ioContext,pos);
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
                        finishParsing(pState, ioContext,pos);
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
                case "tuple{" -> { //named tuples may be removed in a future version
                    String name;
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("tuples can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing tuple name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,pos);
                    if(!(prev instanceof IdentifierToken)||((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before tuple has to be an identifier",pos);
                    }
                    name = ((IdentifierToken) prev).name;
                    TupleBlock tupleBlock = new TupleBlock(name,((IdentifierToken) prev).isPublicReadable(),
                            0, pos, pState.topLevelContext());
                    pState.openBlocks.add(tupleBlock);
                    pState.openedContexts.add(tupleBlock.context());
                }
                case "enum{" ->{
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("enums can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing enum name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,pos);
                    if(!(prev instanceof IdentifierToken)){
                        throw new SyntaxError("token before enum has to be an identifier",pos);
                    }else if(((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before enum has to be an unmodified identifier",pos);
                    }
                    String name = ((IdentifierToken) prev).name;
                    pState.openBlocks.add(new EnumBlock(name,((IdentifierToken) prev).isPublicReadable(), 0,pos,pState.topLevelContext()));
                }
                case "struct{" -> {
                    String name;
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("structs can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing struct name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,pos);
                    if(!(prev instanceof IdentifierToken)||((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before struct has to be an identifier",pos);
                    }
                    name = ((IdentifierToken) prev).name;
                    StructBlock structBlock = new StructBlock(name,((IdentifierToken) prev).isPublicReadable(),
                            0, pos, pState.topLevelContext());
                    pState.openBlocks.add(structBlock);
                    pState.openedContexts.add(structBlock.context());
                }
                case "extend" -> {
                    CodeBlock block=pState.openBlocks.peek();
                    if(!(block instanceof StructBlock)){
                        throw new SyntaxError("'"+str+"' can only be used in struct blocks",pos);
                    }
                    if(((StructBlock) block).extended!=null){
                        throw new SyntaxError("structs can only contain one '"+str+"' statement",pos);
                    }else if(((StructBlock) block).context.fields.size()>0){
                        throw new SyntaxError("'"+str+"' cannot appear after a field declaration",pos);
                    }
                    List<Token> subList = tokens.subList(block.start, tokens.size());
                    TypeCheckResult r=typeCheck(subList,pState.getContext(),pState.globalConstants,
                            new RandomAccessStack<>(8),null,pos,ioContext);
                    subList.clear();
                    if(r.types.size()!=1||r.types.get(1).type!=Type.TYPE||r.types.get(1).value==null){
                        throw new SyntaxError("value before '"+str+"' has to be one constant type",pos);
                    }
                    try {
                        Type extended=r.types.get(1).value.asType();
                        if(!(extended instanceof Type.Struct)){
                            throw new SyntaxError("extended type has to be a struct got: "+extended,pos);
                        }
                        ((StructBlock) block).extended=(Type.Struct) extended;
                        for(int i=0;i<((Type.Struct) extended).elementCount();i++){
                            ((StructBlock) block).context.fields.add(new StructFieldWithType(((Type.Struct) extended).fields[i],
                                    ((Type.Struct) extended).getElement(i)));
                        }
                    } catch (TypeError e) {
                        throw new SyntaxError(e,pos);
                    }
                }
                case "proc(","procedure(" ->{
                    if(pState.openBlocks.size()>0){
                        throw new SyntaxError("procedures can only be declared at root level",pos);
                    }
                    if(tokens.size()==0){
                        throw new SyntaxError("missing procedure name",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    finishParsing(pState, ioContext,pos);
                    boolean isNative=false;
                    if(!(prev instanceof IdentifierToken)){
                        throw new SyntaxError("token before '"+str+"' has to be an identifier",pos);
                    }else if(((IdentifierToken) prev).isNative()){
                        isNative=true;
                    }else if(((IdentifierToken) prev).type!=IdentifierType.WORD){
                        throw new SyntaxError("token before '"+str+"' has to be an unmodified identifier",pos);
                    }
                    String name = ((IdentifierToken) prev).name;
                    ProcedureBlock proc = new ProcedureBlock(name, ((IdentifierToken) prev).isPublicReadable(), 0,
                            pos, pState.topLevelContext(), isNative);
                    pState.openBlocks.add(proc);
                    pState.openedContexts.add(proc.context());
                }
                case "lambda(","λ(" -> {
                    ProcedureBlock lambda = new ProcedureBlock(null, false, tokens.size(),pos, pState.getContext(), false);
                    pState.openBlocks.add(lambda);
                    lambda.context().lock();//no generics in lambdas
                    pState.openedContexts.add(lambda.context());
                }
                case "=>" ->{
                    CodeBlock block = pState.openBlocks.peekLast();
                    if(block instanceof ProcedureBlock proc) {
                        List<Token> ins = tokens.subList(proc.start, tokens.size());
                        proc.addIns(typeCheck(ins,block.context(),pState.globalConstants,
                                new RandomAccessStack<>(8),null,pos,ioContext).tokens,pos);
                        ins.clear();
                        proc.context().lock();
                    }else if(block!=null&&block.type==BlockType.ANONYMOUS_TUPLE){
                        pState.openBlocks.removeLast();
                        pState.openBlocks.addLast(new ProcTypeBlock((ArrayBlock)block,tokens.size()));
                    }else{
                        throw new SyntaxError("'"+str+"' can only be used in proc- or proc-type blocks ",pos);
                    }
                }
                case "){" -> {
                    CodeBlock block=pState.openBlocks.peekLast();
                    if(block==null){
                        throw new SyntaxError(str+" can only be used in proc- and lambda- blocks",pos);
                    }else if(block.type==BlockType.PROCEDURE){
                        //handle procedure separately since : does not change context of produce a jump
                        ProcedureBlock proc=(ProcedureBlock) block;
                        if(proc.state==ProcedureBlock.STATE_BODY){
                            throw new SyntaxError("unexpected '"+str+"'",pos);
                        }
                        List<Token> outs = tokens.subList(proc.start, tokens.size());
                        proc.addOuts(typeCheck(outs,block.context(),pState.globalConstants,
                                new RandomAccessStack<>(8),null,pos,ioContext).tokens,pos);
                        outs.clear();
                    }else{
                        throw new SyntaxError(str+" can only be used in proc- and lambda- blocks", pos);
                    }
                }
                case "{" -> {
                    tokens.add(new BlockToken(BlockTokenType.ARRAY,        pos,-1));
                    pState.openBlocks.add(new ArrayBlock(tokens.size(),BlockType.CONST_ARRAY,pos, pState.getContext()));
                }
                case "while{" -> {
                    tokens.add(new BlockToken(BlockTokenType.WHILE, pos, -1));
                    pState.openBlocks.add(new WhileBlock(tokens.size(),pos, pState.getContext()));
                }
                case "switch{" -> {
                    tokens.add(new BlockToken(BlockTokenType.SWITCH, pos, -1));
                    pState.openBlocks.add(new SwitchCaseBlock(Type.INT,tokens.size(),pos, pState.getContext()));
                }
                case "if{" -> {
                    tokens.add(new BlockToken(BlockTokenType.IF, pos, -1));
                    pState.openBlocks.add(new IfBlock(tokens.size(),pos, pState.getContext()));
                }
                case "do", "}do{" -> {
                    if(!(pState.openBlocks.peekLast() instanceof WhileBlock)){
                        throw new SyntaxError("'"+str+"' can only appear in while-blocks",pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.DO, pos, -1));
                }
                case "if", "}if{" -> {
                    if(!(pState.openBlocks.peekLast() instanceof IfBlock)){
                        throw new SyntaxError("'"+str+"' can only appear in if-blocks",pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType._IF, pos, -1));
                }
                case "else", "}else{" ->  {
                    if(!(pState.openBlocks.peekLast() instanceof IfBlock)){
                        throw new SyntaxError("'"+str+"' can only appear in if-blocks",pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.ELSE, pos, -1));
                }
                case "case" -> {
                    if(!(pState.openBlocks.peekLast() instanceof SwitchCaseBlock)){
                        throw new SyntaxError("'"+str+"' can only appear in switch-blocks", pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.CASE, pos, -1));
                }
                case "default"  -> {
                    if(!(pState.openBlocks.peekLast() instanceof SwitchCaseBlock)){
                        throw new SyntaxError("'"+str+"' can only appear in switch-blocks", pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.DEFAULT, pos, -1));
                }
                case "end-case" -> {//addLater? simplify end-case
                    if(!(pState.openBlocks.peekLast() instanceof SwitchCaseBlock)){
                        throw new SyntaxError("'"+str+"' can only appear in switch-blocks", pos);
                    }
                    tokens.add(new BlockToken(BlockTokenType.END_CASE, pos, -1));
                }
                case "}" -> {
                    CodeBlock block=pState.openBlocks.pollLast();
                    if(block==null) {
                        throw new SyntaxError("unexpected '"+str+"' statement",pos);
                    }
                    Token tmp;
                    switch (block.type) {
                        case PROCEDURE -> {
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            ArrayList<Token> content=new ArrayList<>(subList);
                            subList.clear();
                            ProcedureContext context = ((ProcedureBlock) block).context();
                            if(context != pState.openedContexts.pollLast()){
                                throw new RuntimeException("openedContexts is out of sync with openBlocks");
                            }
                            assert context != null;
                            Type[] ins=((ProcedureBlock) block).inTypes;
                            Type[] outs=((ProcedureBlock) block).outTypes;
                            ArrayList<Type.GenericParameter> generics=((ProcedureBlock) block).context.generics;
                            Type.Procedure procType=null;
                            if(((ProcedureBlock) block).state==ProcedureBlock.STATE_BODY) {
                                if (outs != null) {
                                    procType=(generics.size()>0)?
                                            Type.GenericProcedureType.create(generics.toArray(Type.GenericParameter[]::new),ins,outs):
                                            Type.Procedure.create(ins,outs);
                                }
                            }else{
                                if(((ProcedureBlock) block).state==ProcedureBlock.STATE_IN){
                                    throw new SyntaxError("procedure does not have a signature",block.startPos);
                                }else{
                                    throw new SyntaxError("procedure does not provide output arguments",block.startPos);
                                }
                            }
                            if(((ProcedureBlock) block).isNative){
                                assert procType!=null;
                                assert ((ProcedureBlock) block).name!=null;
                                if(content.size()>0){
                                    throw new SyntaxError("unexpected token: "+subList.get(0)+
                                            " (at "+subList.get(0).pos+") native procedures have to have an empty body",pos);
                                }
                                Value.NativeProcedure proc=Value.createExternalProcedure(((ProcedureBlock) block).name,
                                        ((ProcedureBlock) block).isPublic, procType,block.startPos
                                );
                                pState.topLevelContext().declareProcedure(proc,ioContext);
                            }else{
                                if(((ProcedureBlock) block).name!=null){
                                    assert procType!=null;
                                    if(generics.size()>0){
                                        GenericProcedure proc=new GenericProcedure(((ProcedureBlock) block).name,
                                                ((ProcedureBlock) block).isPublic,(Type.GenericProcedureType) procType,
                                                content,block.startPos,pos,context);
                                        assert context.curried.isEmpty();
                                        pState.topLevelContext().declareProcedure(proc,ioContext);
                                    }else{
                                        Value.Procedure proc=Value.createProcedure(((ProcedureBlock) block).name,
                                                ((ProcedureBlock) block).isPublic,procType, content,block.startPos,pos,context);
                                        assert context.curried.isEmpty();
                                        pState.topLevelContext().declareProcedure(proc,ioContext);
                                    }
                                }else{
                                    tokens.add(new DeclareLambdaToken(ins,outs,generics,content,context,block.startPos,pos));
                                }
                            }
                        }
                        case ENUM -> {
                            if(tokens.size()>block.start){
                                tmp=tokens.get(block.start);
                                throw new SyntaxError("Invalid token in enum:"+tmp,tmp.pos);
                            }
                            pState.topLevelContext().declareEnum(((EnumBlock) block),ioContext);
                        }
                        case TUPLE -> {
                            if(((TupleBlock) block).context != pState.openedContexts.pollLast()){
                                throw new RuntimeException("openedContexts is out of sync with openBlocks");
                            }
                            ArrayList<Type.GenericParameter> generics=((TupleBlock) block).context.generics;
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            Type[] types=ProcedureBlock.getSignature(
                                    typeCheck(subList, block.context(), pState.globalConstants,
                                            new RandomAccessStack<>(8),null,pos,ioContext).tokens,true);
                            subList.clear();
                            if(generics.size()>0){
                                GenericTuple tuple=new GenericTuple(((TupleBlock) block).name,((TupleBlock) block).isPublic,
                                        generics.toArray(Type.GenericParameter[]::new),
                                        types,block.startPos);
                                pState.topLevelContext().declareNamedDeclareable(tuple,ioContext);
                            }else{
                                Type.Tuple tuple=Type.Tuple.create(((TupleBlock) block).name, ((TupleBlock) block).isPublic,
                                        types,block.startPos);
                                pState.topLevelContext().declareNamedDeclareable(tuple,ioContext);
                            }
                        }
                        case STRUCT ->{
                            assert block instanceof StructBlock;
                            StructContext structContext = ((StructBlock) block).context;
                            assert structContext!=null;
                            if(structContext != pState.openedContexts.pollLast()){
                                throw new RuntimeException("openedContexts is out of sync with openBlocks");
                            }
                            List<Token> subList = tokens.subList(block.start, tokens.size());
                            ArrayList<Token> body=typeCheck(subList, block.context(), pState.globalConstants,
                                            new RandomAccessStack<>(8),null,pos,ioContext).tokens;
                            subList.clear();
                            if(body.size()>0){
                                tmp=body.get(0);
                                throw new SyntaxError("Unexpected token in struct: "+tmp,tmp.pos);
                            }
                            ArrayList<Type.GenericParameter> generics= structContext.generics;
                            Type.StructField[] fieldNames=new Type.StructField[structContext.fields.size()];
                            Type[] types=new Type[fieldNames.length];
                            for(int i=0;i<types.length;i++){
                                fieldNames[i]= structContext.fields.get(i).field;
                                types[i]= structContext.fields.get(i).type;
                            }
                            if(generics.size()>0){
                                GenericStruct struct=new GenericStruct(((StructBlock) block).name,((StructBlock) block).isPublic,
                                        ((StructBlock) block).extended,generics.toArray(Type.GenericParameter[]::new),
                                        types,fieldNames,block.startPos);
                                pState.topLevelContext().declareNamedDeclareable(struct,ioContext);
                            }else{
                                Type.Struct struct=Type.Struct.create(((StructBlock) block).name, ((StructBlock) block).isPublic,
                                        ((StructBlock) block).extended,fieldNames,types,block.startPos);
                                pState.topLevelContext().declareNamedDeclareable(struct,ioContext);
                            }
                        }
                        case IF,WHILE,SWITCH_CASE, CONST_ARRAY ->{
                            if(tokens.size()>0&&tokens.get(tokens.size()-1) instanceof BlockToken b&&
                                    b.blockType==BlockTokenType.DO){//merge do-end
                                tokens.set(tokens.size()-1,new BlockToken(BlockTokenType.DO_WHILE, pos, -1));
                            }else{
                                tokens.add(new BlockToken(BlockTokenType.END, pos, -1));
                            }
                        }
                        case UNION,ANONYMOUS_TUPLE,PROC_TYPE ->
                                throw new SyntaxError("unexpected '"+str+"' statement",pos);
                    }
                }
                case "return" -> tokens.add(new Token(TokenType.RETURN,  pos));
                case "exit"   -> tokens.add(new Token(TokenType.EXIT,  pos));
                case "union(" ->{
                    ArrayBlock block = new ArrayBlock(tokens.size(), BlockType.UNION, pos, pState.getContext());
                    pState.openBlocks.add(block);
                    pState.openedContexts.add(block.context());
                }
                case "(" ->{
                    ArrayBlock block = new ArrayBlock(tokens.size(), BlockType.ANONYMOUS_TUPLE, pos, pState.getContext());
                    pState.openBlocks.add(block);
                    pState.openedContexts.add(block.context());
                }
                case ")" -> {
                    CodeBlock open=pState.openBlocks.pollLast();
                    if(open==null||(open.type!=BlockType.ANONYMOUS_TUPLE&&open.type!=BlockType.PROC_TYPE&&open.type!=BlockType.UNION)){
                        throw new SyntaxError("unexpected '"+str+"' statement ",pos);
                    }
                    if(open.context() != pState.openedContexts.pollLast()){
                        throw new RuntimeException("openedContexts is out of sync with openBlocks");
                    }
                    if(open.type==BlockType.PROC_TYPE){
                        List<Token> subList=tokens.subList(open.start, ((ProcTypeBlock)open).separatorPos);
                        Type[] inTypes=ProcedureBlock.getSignature(
                                typeCheck(subList,open.context(),pState.globalConstants,
                                        new RandomAccessStack<>(8),null,pos,ioContext).tokens,false);
                        subList=tokens.subList(((ProcTypeBlock)open).separatorPos, tokens.size());
                        Type[] outTypes=ProcedureBlock.getSignature(
                                typeCheck(subList,open.context(),pState.globalConstants,
                                        new RandomAccessStack<>(8),null,pos,ioContext).tokens,false);
                        subList=tokens.subList(open.start,tokens.size());
                        subList.clear();
                        Type.Procedure procType=Type.Procedure.create(inTypes,outTypes);
                        tokens.add(new ValueToken(Value.ofType(procType),pos));
                    }else if(open.type==BlockType.ANONYMOUS_TUPLE){
                        List<Token> subList=tokens.subList(open.start, tokens.size());
                        Type[] tupleTypes=ProcedureBlock.getSignature(
                                typeCheck(subList,open.context(),pState.globalConstants,
                                        new RandomAccessStack<>(8),null,pos,ioContext).tokens,true);
                        subList.clear();
                        tokens.add(new ValueToken(Value.ofType(Type.Tuple.create(null, false, tupleTypes,pos)),
                                pos));
                    }else /*if(open.type==BlockType.UNION)*/{
                        List<Token> subList=tokens.subList(open.start, tokens.size());
                        Type[] elements=ProcedureBlock.getSignature(
                                typeCheck(subList,open.context(),pState.globalConstants,
                                        new RandomAccessStack<>(8),null,pos,ioContext).tokens,true);
                        if(elements.length==0){
                            throw new SyntaxError("union has to contain at least one element",pos);
                        }
                        subList.clear();
                        tokens.add(new ValueToken(Value.ofType(Type.UnionType.create(elements)),
                                pos));
                    }
                }

                //debug helpers
                case "debugPrint"    -> tokens.add(new Token(TokenType.DEBUG_PRINT, pos));
                case "assert"    -> {
                    if(tokens.size()<1){
                        throw new SyntaxError("not enough tokens for 'assert'",pos);
                    }
                    prev=tokens.remove(tokens.size()-1);
                    if(!(prev instanceof ValueToken&&((ValueToken) prev).value.isString())){
                        throw new SyntaxError("tokens directly preceding 'assert' has to be a constant string",pos);
                    }
                    String message=((ValueToken) prev).value.stringValue();
                    tokens.add(new AssertToken(message, pos));
                }
                case "unreachable" ->
                    tokens.add(new Token(TokenType.UNREACHABLE,pos));
                //constants
                case "true"  -> tokens.add(new ValueToken(Value.TRUE,    pos));
                case "false" -> tokens.add(new ValueToken(Value.FALSE,   pos));
                //types
                case "bool"       -> tokens.add(new ValueToken(Value.ofType(Type.BOOL),              pos));
                case "byte"       -> tokens.add(new ValueToken(Value.ofType(Type.BYTE),              pos));
                case "int"        -> tokens.add(new ValueToken(Value.ofType(Type.INT),               pos));
                case "uint"       -> tokens.add(new ValueToken(Value.ofType(Type.UINT),              pos));
                case "codepoint"  -> tokens.add(new ValueToken(Value.ofType(Type.CODEPOINT),         pos));
                case "float"      -> tokens.add(new ValueToken(Value.ofType(Type.FLOAT),             pos));
                case "string"     -> tokens.add(new ValueToken(Value.ofType(Type.RAW_STRING()),      pos));
                case "ustring"    -> tokens.add(new ValueToken(Value.ofType(Type.UNICODE_STRING()),  pos));
                case "type"       -> tokens.add(new ValueToken(Value.ofType(Type.TYPE),              pos));
                case "var"        -> tokens.add(new ValueToken(Value.ofType(Type.ANY),               pos));

                case "mut?"     -> tokens.add(new Token(TokenType.MARK_MAYBE_MUTABLE, pos));
                case "mut^"     -> tokens.add(new Token(TokenType.MARK_INHERIT_MUTABILITY, pos));
                case "array"    -> tokens.add(new Token(TokenType.ARRAY_OF,       pos));
                case "memory"   -> tokens.add(new Token(TokenType.MEMORY_OF,      pos));
                case "optional" -> tokens.add(new Token(TokenType.OPTIONAL_OF,    pos));
                case "empty"    -> tokens.add(new Token(TokenType.EMPTY_OPTIONAL, pos));

                case "cast"   -> tokens.add(new TypedToken(TokenType.CAST,null,pos));

                //stack modifiers
                //<count> $drop
                case "$drop" ->{
                    int[] args = getArgInts(str, 1, tokens, pos);
                    tokens.add(new StackModifierToken(TokenType.STACK_DROP,args,pos));
                }
                //<src> $dup
                case "$dup" ->{
                    int[] args = getArgInts(str, 1, tokens, pos);
                    tokens.add(new StackModifierToken(TokenType.STACK_DUP,args,pos));
                }
                //<target> <src> $set
                case "$set" ->{
                    int[] args = getArgInts(str, 2, tokens, pos);
                    tokens.add(new StackModifierToken(TokenType.STACK_SET,args,pos));
                }

                case "()"     -> tokens.add(new Token(TokenType.CALL_PTR, pos));


                case "new"       -> tokens.add(new TypedToken(TokenType.NEW,null, pos));

                //identifiers
                case "="->{
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '=' modifier",pos);
                    }else if(prev instanceof IdentifierToken){
                        if(((IdentifierToken) prev).type == IdentifierType.WORD){
                            prev=new IdentifierToken(IdentifierType.VAR_WRITE,((IdentifierToken) prev).name,
                                    ((IdentifierToken) prev).flags, prev.pos);
                        }else if(((IdentifierToken) prev).type == IdentifierType.GET_FIELD){
                            prev=new IdentifierToken(IdentifierType.SET_FIELD,((IdentifierToken) prev).name,
                                    ((IdentifierToken) prev).flags, prev.pos);
                        }else{
                            throw new SyntaxError("invalid token for '=' modifier: "+prev,prev.pos);
                        }
                        tokens.set(tokens.size()-1,prev);
                    }else{
                        throw new SyntaxError("invalid token for '=' modifier: "+prev,prev.pos);
                    }
                }
                case "=:"->{
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '=:' modifier",pos);
                    }else if(prevId!=null){
                        prev=new IdentifierToken(IdentifierType.DECLARE,prevId,((IdentifierToken) prev).flags, prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '=:' modifier "+prev,prev.pos);
                    }
                    tokens.set(tokens.size()-1,prev);
                }
                case "=::"->{
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '=::' modifier",pos);
                    }else if(prevId!=null){
                        prev=new IdentifierToken(IdentifierType.IMPLICIT_DECLARE,prevId,((IdentifierToken) prev).flags, prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '=::' modifier "+prev,prev.pos);
                    }
                    tokens.set(tokens.size()-1,prev);
                }
                case "native" -> {
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }else if(prevId!=null){
                        if(((IdentifierToken) prev).isNative()){
                            throw new SyntaxError("duplicate modifier for identifier "+prevId+" : '"+str+"'",pos);
                        }
                        prev=new IdentifierToken(IdentifierType.WORD,prevId,
                                ((IdentifierToken) prev).flags|IdentifierToken.FLAG_NATIVE, prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '"+str+"' modifier: "+prev,prev.pos);
                    }
                    tokens.set(tokens.size()-1,prev);
                }
                case "public" -> {
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }else if(prev instanceof IdentifierToken&&(((IdentifierToken) prev).type == IdentifierType.WORD||
                            ((IdentifierToken) prev).type == IdentifierType.DECLARE_FIELD)){
                        if((((IdentifierToken) prev).flags&IdentifierToken.ACCESSIBILITY_MASK)!=IdentifierToken.ACCESSIBILITY_DEFAULT){
                            throw new SyntaxError("multiple accessibility modifiers for identifier "+
                                    ((IdentifierToken) prev).name+" : '"+str+"'",pos);
                        }
                        prev=new IdentifierToken(((IdentifierToken) prev).type,((IdentifierToken) prev).name,
                                ((IdentifierToken) prev).flags|IdentifierToken.ACCESSIBILITY_PUBLIC, prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '"+str+"' modifier: "+prev,prev.pos);
                    }
                    tokens.set(tokens.size()-1,prev);
                }
                case "restricted" -> { //addLater better name
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }else if(prev instanceof IdentifierToken&&(((IdentifierToken) prev).type == IdentifierType.WORD||
                            ((IdentifierToken) prev).type == IdentifierType.DECLARE_FIELD)){
                        if((((IdentifierToken) prev).flags&IdentifierToken.ACCESSIBILITY_MASK)!=IdentifierToken.ACCESSIBILITY_DEFAULT){
                            throw new SyntaxError("multiple accessibility modifiers for identifier "+
                                    ((IdentifierToken) prev).name+" : '"+str+"'",pos);
                        }
                        prev=new IdentifierToken(((IdentifierToken) prev).type,((IdentifierToken) prev).name,
                                ((IdentifierToken) prev).flags|IdentifierToken.ACCESSIBILITY_READ_ONLY, prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '"+str+"' modifier: "+prev,prev.pos);
                    }
                    tokens.set(tokens.size()-1,prev);
                }
                case "private" -> {
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }else if(prev instanceof IdentifierToken&&(((IdentifierToken) prev).type == IdentifierType.WORD||
                            ((IdentifierToken) prev).type == IdentifierType.DECLARE_FIELD)){
                        if((((IdentifierToken) prev).flags&IdentifierToken.ACCESSIBILITY_MASK)!=IdentifierToken.ACCESSIBILITY_DEFAULT){
                            throw new SyntaxError("multiple accessibility modifiers for identifier "+
                                    ((IdentifierToken) prev).name+" : '"+str+"'",pos);
                        }
                        prev=new IdentifierToken(((IdentifierToken) prev).type,((IdentifierToken) prev).name,
                                ((IdentifierToken) prev).flags|IdentifierToken.ACCESSIBILITY_PRIVATE, prev.pos);
                    }else{
                        throw new SyntaxError("invalid token for '"+str+"' modifier: "+prev,prev.pos);
                    }
                    tokens.set(tokens.size()-1,prev);
                }
                case "mut"  -> {
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }
                    if(prev instanceof IdentifierToken&&(((IdentifierToken) prev).type == IdentifierType.WORD||
                            ((IdentifierToken) prev).type == IdentifierType.DECLARE_FIELD)){//mut as name modifier
                        if((((IdentifierToken) prev).flags&IdentifierToken.MUTABILITY_MASK)!=IdentifierToken.MUTABILITY_DEFAULT){
                            throw new SyntaxError("multiple mutability modifiers for identifier "+
                                    ((IdentifierToken) prev).name+" : '"+str+"'",pos);
                        }
                        prev=new IdentifierToken(((IdentifierToken) prev).type,((IdentifierToken) prev).name,
                                ((IdentifierToken) prev).flags|IdentifierToken.MUTABILITY_MUTABLE, prev.pos);
                        tokens.set(tokens.size()-1,prev);
                    }else {
                        tokens.add(new Token(TokenType.MARK_MUTABLE, pos));
                    }
                }
                case "mut~"  -> {
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }
                    if(prev instanceof IdentifierToken&&(((IdentifierToken) prev).type == IdentifierType.WORD||
                            ((IdentifierToken) prev).type == IdentifierType.DECLARE_FIELD)){//mut as name modifier
                        if((((IdentifierToken) prev).flags&IdentifierToken.MUTABILITY_MASK)!=IdentifierToken.MUTABILITY_DEFAULT){
                            throw new SyntaxError("multiple mutability modifiers for identifier "+
                                    ((IdentifierToken) prev).name+" : '"+str+"'",pos);
                        }
                        prev=new IdentifierToken(((IdentifierToken) prev).type,((IdentifierToken) prev).name,
                                ((IdentifierToken) prev).flags|IdentifierToken.MUTABILITY_IMMUTABLE, prev.pos);
                        tokens.set(tokens.size()-1,prev);
                    }else {
                        tokens.add(new Token(TokenType.MARK_IMMUTABLE, pos));
                    }
                }
                case "<>", "<?>" ->{
                    if(prev==null){
                        throw new SyntaxError("not enough tokens tokens for '"+str+"' modifier",pos);
                    }else if(prevId!=null){
                        VariableContext context=pState.getContext();
                        if(!(context instanceof GenericContext)){
                            throw new SyntaxError("generics can only be declared in tuple and procedure signatures",pos);
                        }
                        ((GenericContext) context).declareGeneric(prevId, str.equals("<?>"), pos, ioContext);
                        tokens.remove(tokens.size()-1);
                    }else{
                        throw new SyntaxError("invalid token for '<>' modifier: "+prev,prev.pos);
                    }
                }
                default -> {
                    if(str.startsWith(".")){
                        String name=str.substring(1);
                        boolean isPtr=false;
                        if(name.startsWith("@")){
                            name=name.substring(1);
                            isPtr=true;
                        }
                        prev = tokens.size()>0 ? tokens.get(tokens.size()-1) : null;
                        if(prev instanceof IdentifierToken&&((IdentifierToken) prev).type==IdentifierType.WORD&&
                                pState.topLevelContext().namespaces().contains(((IdentifierToken) prev).name)){
                            String newName = ((IdentifierToken) prev).name + NAMESPACE_SEPARATOR + name;
                            Declareable d=pState.topLevelContext().getDeclareable(newName);
                            if(d instanceof Macro){
                                tokens.remove(tokens.size()-1);
                                expandMacro(pState,(Macro)d,pos,ioContext);
                            }else {
                                tokens.set(tokens.size() - 1, new IdentifierToken(isPtr ? IdentifierType.PROC_ID : IdentifierType.WORD,
                                        newName, 0, pos));
                            }
                        }else{
                            tokens.add(new IdentifierToken(IdentifierType.GET_FIELD,name, 0, pos));
                        }
                    }else if(str.startsWith("@")){
                        tokens.add(new IdentifierToken(IdentifierType.PROC_ID, str.substring(1), 0, pos));
                    }else if(str.startsWith(":")){
                        tokens.add(new IdentifierToken(IdentifierType.DECLARE_FIELD, str.substring(1), 0, pos));
                    }else{
                        Declareable d=pState.topLevelContext().getDeclareable(str);
                        if(d instanceof Macro){
                            expandMacro(pState,(Macro)d,pos,ioContext);
                        }else{
                            CodeBlock last= pState.openBlocks.peekLast();
                            if(last instanceof EnumBlock){
                                ((EnumBlock) last).add(str,pos);
                            }else{
                                tokens.add(new IdentifierToken(IdentifierType.WORD, str, 0, pos));
                            }
                        }
                    }
                }
            }
        }
    }

    private int[] getArgInts(String op, int nArgs, ArrayList<Token> tokens, FilePosition pos) throws SyntaxError {
        if(tokens.size()<nArgs){
            throw new SyntaxError("not enough arguments for "+op,pos);
        }
        int[] args=new int[nArgs];
        for(int i = 0; i< nArgs; i++){
            Token arg= tokens.remove(tokens.size()-1);
            if(arg instanceof ValueToken){
                try {
                    long c=((ValueToken) arg).value.asLong();
                    if(c<=0){
                        throw new SyntaxError(op +": count has to be greater than 0",arg.pos);
                    }else if(c>Integer.MAX_VALUE){
                        throw new SyntaxError(op +": count has to be less than of equal to "+Integer.MAX_VALUE,
                                arg.pos);
                    }
                    args[i]=(int)c;
                } catch (TypeError e) {
                    throw new SyntaxError(e, pos);
                }
            }else{
                throw new SyntaxError("the arguments of "+ op +" have to be compile time constants",arg.pos);
            }
        }
        return args;
    }

    private void expandMacro(ParserState pState, Macro m, FilePosition pos, IOContext ioContext) throws SyntaxError {
        m.markAsUsed();
        for(StringWithPos s:m.content){
            finishWord(s.str,pState,new FilePosition(s.start, pos),ioContext);
        }
    }
    private void finishParsing(ParserState pState, IOContext ioContext,FilePosition blockEnd) throws SyntaxError {
        TypeCheckResult res=typeCheck(pState.tokens, pState.topLevelContext(),pState.globalConstants,
                pState.typeStack, null,blockEnd,ioContext);
        pState.globalCode.addAll(res.tokens);
        pState.typeStack=res.types;
        pState.tokens.clear();
    }

    private void typeCheckProcedure(Value.Procedure p,HashMap<Interpreter.VariableId, Value> globalConstants, IOContext ioContext) throws SyntaxError {
        if(p.typeCheckState == Value.TypeCheckState.UNCHECKED){
            p.typeCheckState =Value.TypeCheckState.CHECKING;
            TypeCheckResult res;
            RandomAccessStack<TypeFrame> typeStack=new RandomAccessStack<>(8);
            for(Type t:((Type.Procedure) p.type).inTypes){
                typeStack.push(new TypeFrame(t,null, p.declaredAt));
            }
            res=typeCheck(p.tokens, p.context, globalConstants,typeStack,((Type.Procedure) p.type).outTypes,
                    p.endPos, ioContext);
            p.tokens=res.tokens;
            p.typeCheckState = Value.TypeCheckState.CHECKED;
        }
    }

    private String typesToString(RandomAccessStack<TypeFrame> types){
        StringBuilder str=new StringBuilder("[");
        for(TypeFrame f:types){
            if(str.length()>1){
                str.append(", ");
            }
            str.append(f.type);
        }
        return str.append("]").toString();
    }
    private void checkReturnValue(RandomAccessStack<TypeFrame> typeStack, Type[] outTypes,FilePosition pos) throws SyntaxError {
        int k = typeStack.size();
        if(typeStack.size() != outTypes.length){
            throw new SyntaxError("return value "+typesToString(typeStack)+" does not match signature "
                    +Arrays.toString(outTypes), pos);
        }
        for(Type t: outTypes){
            if(!typeStack.get(k--).type().canAssignTo(t)){
                throw new SyntaxError("return value "+typesToString(typeStack)+" does not match signature "
                        +Arrays.toString(outTypes), pos);
            }
        }
    }

    private boolean notAssignable(RandomAccessStack<TypeFrame> a, RandomAccessStack<TypeFrame> b) {
        if(a.size()!=b.size()){
            return true;
        }
        for(int i=1;i<=a.size();i++){
            if(!a.get(i).type.canAssignTo(b.get(i).type)){
                return true;
            }
        }
        return false;
    }
    private void merge(RandomAccessStack<TypeFrame> main,FilePosition endMain,RandomAccessStack<TypeFrame> branch,FilePosition endBranch,
                       String name) throws SyntaxError {
        if(branch.size()!= main.size()){
            throw new SyntaxError("branch of "+name+"-statement "+typesToString(branch)+" at "+endBranch+
                    " cannot be merged into the main branch "+typesToString(main),endMain);
        }
        for(int p = 1; p <= branch.size(); p++){
            TypeFrame t1= main.get(p);
            TypeFrame t2= branch.get(p);
            if((!t1.equals(t2))){
                Optional<Type> merged = Type.commonSuperType(t1.type, t2.type, true);
                if(merged.isEmpty()){
                    throw new SyntaxError("Cannot merge "+t1.type+" and "+t2.type,endMain);
                }
                main.set(p,new TypeFrame(merged.get(),null,t1.pushedAt));
                //addLater? better position reporting for merged positions
            }
        }
    }
    record TypeCheckResult(ArrayList<Token> tokens,RandomAccessStack<TypeFrame> types){}
    public TypeCheckResult typeCheck(List<Token> tokens,VariableContext context,HashMap<VariableId,Value> globalConstants,
                                      RandomAccessStack<TypeFrame> typeStack,Type[] expectedReturnTypes,FilePosition blockEnd,
                                     IOContext ioContext) throws SyntaxError {
        ArrayDeque<CodeBlock> openBlocks=new ArrayDeque<>();
        ArrayList<Token> ret=new ArrayList<>(tokens.size());
        ArrayDeque<BranchWithEnd> retStacks=new ArrayDeque<>();
        boolean finishedBranch=false;
        Token prev;
        for(int i=0;i<tokens.size();i++){
            Token t=tokens.get(i);
            if(t instanceof CompilerToken){
                processCompilerToken((CompilerToken) t, typeStack, openBlocks, ret,globalConstants,context);
                continue;//don't check t as a normal token
            }
            if(finishedBranch){
                if(t.tokenType!=TokenType.UNREACHABLE&&((!(t instanceof BlockToken block))
                        ||(block.blockType!=BlockTokenType.ELSE&&block.blockType!=BlockTokenType.END_CASE
                        &&block.blockType!=BlockTokenType.END))){
                    //end of branch that is not always executed
                    throw new SyntaxError("unreachable statement: "+t,t.pos);
                }
            }
            try {
            switch(t.tokenType){
                case BLOCK_TOKEN -> {
                    BlockToken block=(BlockToken)t;
                    BlockCheckReturn tmp=typeCheckBlock(block,openBlocks,typeStack,finishedBranch,globalConstants,ret,i,
                            tokens,context,ioContext,t.pos);
                    finishedBranch=tmp.finishedBranch;
                    typeStack=tmp.typeStack;
                    context=tmp.context;
                    i=tmp.i;
                }
                case IDENTIFIER ->
                    typeCheckIdentifier(t, ret, context, globalConstants, typeStack, ioContext);
                case ASSERT ->
                    typeCheckAssert(typeStack, ret, t);
                case UNREACHABLE -> {
                    ret.add(t);
                    finishedBranch=true;
                }
                case OPERATOR ->
                        throw new RuntimeException("internal field access operations should not exist at this stage of compilation "+t.pos);
                case NEW ->
                    typeCheckNew(typeStack, ret, ioContext,t.pos);
                case DECLARE_LAMBDA -> {//parse lambda-procedures
                    assert t instanceof DeclareLambdaToken;
                    typeCheckLambda(globalConstants,context, typeStack, ioContext, ret, (DeclareLambdaToken)t);
                }
                case VALUE -> {
                    assert t instanceof ValueToken;
                    //push type information
                    typeStack.push(new TypeFrame(((ValueToken) t).value.type, ((ValueToken) t).value,t.pos));
                    ret.add(t);
                }
                case DEBUG_PRINT -> {
                    typeStack.pop();
                    ret.add(t);
                }
                case EXIT -> {
                    Type t1=typeStack.pop().type;
                    if((t1!=Type.INT)&&(t1!=Type.UINT)){
                        throw new SyntaxError("exit code has to be an integer",t.pos);
                    }
                    finishedBranch=true;
                    ret.add(t);
                }
                case RETURN -> {
                    if(expectedReturnTypes!=null){
                        //addLater? implicit casting of return values ( int <-> uint -> float )
                        checkReturnValue(typeStack,expectedReturnTypes,t.pos);
                    }else{
                        retStacks.addLast(new BranchWithEnd(typeStack.clone(),t.pos));
                    }
                    finishedBranch=true;
                    ret.add(t);
                }
                case CAST -> {
                    if(ret.size()==0){
                        throw new SyntaxError("missing type parameter for 'cast'",t.pos);
                    }else if(!((prev=ret.remove(ret.size()-1))instanceof ValueToken)||
                            ((ValueToken) prev).value.type!=Type.TYPE){
                        throw new SyntaxError("token before 'cast' has to be a type",prev.pos);
                    }else if(typeStack.pop().type!=Type.TYPE){
                        throw new SyntaxError("type stack out of sync with tokens",t.pos);
                    }
                    TypeFrame f=typeStack.pop();
                    Type target=((ValueToken) prev).value.asType();
                    if(target.mutability==Mutability.DEFAULT){
                        target=target.setMutability(f.type.mutability);
                    }
                    typeCheckCast(f.type,1,target, ret,ioContext, t.pos);
                    typeStack.push(new TypeFrame(target,null,t.pos));
                }
                case STACK_DROP ->
                    typeCheckDrop((StackModifierToken)t, typeStack, ret);
                case STACK_DUP ->
                    typeCheckDup((StackModifierToken)t, typeStack, ret);
                case STACK_SET ->
                    typeCheckStackSet((StackModifierToken)t, typeStack, ret);
                case CALL_PTR ->
                    typeCheckCallPtr(typeStack, ret, globalConstants, ioContext, t.pos);
                case MARK_MUTABLE ->
                    typeCheckTypeModifier("mut",(t1)->Value.ofType(t1.mutable()),ret,typeStack,t.pos);
                case MARK_MAYBE_MUTABLE ->
                    typeCheckTypeModifier("mut?",(t1)->Value.ofType(t1.maybeMutable()),ret,typeStack,t.pos);
                case MARK_IMMUTABLE ->
                    typeCheckTypeModifier("mut~",(t1)->Value.ofType(t1.immutable()),ret,typeStack,t.pos);
                case MARK_INHERIT_MUTABILITY ->
                    typeCheckTypeModifier("mut^",(t1)->Value.ofType(t1.setMutability(Mutability.INHERIT)),ret,typeStack,t.pos);
                case ARRAY_OF ->
                    typeCheckTypeModifier("array",(t1)->Value.ofType(Type.arrayOf(t1)),ret,typeStack,t.pos);
                case MEMORY_OF ->
                    typeCheckTypeModifier("memory",(t1)->Value.ofType(Type.memoryOf(t1)),ret,typeStack,t.pos);
                case OPTIONAL_OF ->
                    typeCheckTypeModifier("optional",(t1)->Value.ofType(Type.optionalOf(t1)),ret,typeStack,t.pos);
                case EMPTY_OPTIONAL ->
                    typeCheckTypeModifier("empty", Value::emptyOptional,ret,typeStack,t.pos);
                case SWITCH,CURRIED_LAMBDA,VARIABLE,CONTEXT_OPEN,CONTEXT_CLOSE,NOP,OVERLOADED_PROC_PTR,
                        CALL_PROC,CALL_NATIVE_PROC, NEW_ARRAY,CAST_ARG,LAMBDA,TUPLE_GET_INDEX,TUPLE_SET_INDEX ->
                        throw new RuntimeException("tokens of type "+t.tokenType+" should not exist in this phase of compilation");
            }
            } catch (ConcatRuntimeError|RandomAccessStack.StackUnderflow e) {
                throw new SyntaxError(e,t.pos);
            }catch (SyntaxError e) {
                throw e;
            }catch (Throwable e) {
                System.err.println("while compiling "+t.pos);
                throw e;
            }
        }
        if(expectedReturnTypes!=null){
            if(!finishedBranch) {
                checkReturnValue(typeStack, expectedReturnTypes,blockEnd);
            }
            return new TypeCheckResult(ret,null);
        }else if(finishedBranch){
            if(retStacks.size()>0){
                BranchWithEnd bWe=retStacks.removeLast();
                typeStack=bWe.types;
                blockEnd=bWe.end;//true block end is not needed after this position
            }else{//procedure exits on every execution path
                //addLater mark functions that exit on every path of execution
                return new TypeCheckResult(ret,typeStack);
            }
        }
        for(BranchWithEnd branch:retStacks){
            merge(typeStack,blockEnd,branch.types,branch.end,"return");
        }
        return new TypeCheckResult(ret,typeStack);
    }

    private void processCompilerToken(CompilerToken t, RandomAccessStack<TypeFrame> typeStack, ArrayDeque<CodeBlock> openBlocks,
                                      ArrayList<Token> ret, HashMap<VariableId, Value> globalConstants, VariableContext context) {
        switch(t.type){
            case TOKENS -> {
                int n= t.count;
                if(n > ret.size()){
                    System.out.println("n > #tokens ("+ ret.size()+")");
                    n= ret.size();
                }
                System.out.println("tokens:");
                for(int k=1;k<=n;k++){
                    System.out.println("  "+ret.get(ret.size()-k));
                }
            }
            case BLOCKS -> {
                int n= t.count;
                if(n > openBlocks.size()){
                    System.out.println("n > #blocks ("+ openBlocks.size()+")");
                    n= openBlocks.size();
                }
                System.out.println("openBlocks:");
                CodeBlock[] blocks= openBlocks.toArray(CodeBlock[]::new);
                for(int k=1;k<=n;k++){
                    System.out.println("  "+blocks[openBlocks.size()-k]);
                }
            }
            case GLOBAL_CONSTANTS -> {
                System.out.println("globalConstants:");
                for(Map.Entry<VariableId, Value> e:globalConstants.entrySet()){
                    System.out.println("  "+e.getValue()+"\n  @"+e.getKey());
                }
            }
            case TYPES -> {
                int n= t.count;
                if(n > typeStack.size()){
                    System.out.println("n > #types ("+ typeStack.size()+")");
                    n= typeStack.size();
                }
                System.out.println("types:");
                for(int k=1;k<=n;k++){
                    System.out.println("  "+typeStack.get(k));
                }
            }
            case CONTEXT -> {
                System.out.println("context:");
                System.out.println("  "+context);
            }
        }
    }

    record BlockCheckReturn(boolean finishedBranch,RandomAccessStack<TypeFrame> typeStack,VariableContext context,int i){}
    private BlockCheckReturn typeCheckBlock(BlockToken block, ArrayDeque<CodeBlock> openBlocks, RandomAccessStack<TypeFrame> typeStack,
                                            boolean finishedBranch, HashMap<VariableId,Value> globalConstants,
                                            ArrayList<Token> ret, int i, List<Token> tokens, VariableContext context,
                                            IOContext ioContext, FilePosition pos) throws RandomAccessStack.StackUnderflow, SyntaxError {
        switch (block.blockType){
            case IF ->{
                IfBlock ifBlock = new IfBlock(ret.size(), pos, context);
                TypeFrame f = typeStack.pop();
                ifBlock.elseTypes = typeStack;
                typeStack = typeStack.clone();
                if(f.type!=Type.BOOL){
                    if(f.type.isOptional()){
                        typeStack.push(new TypeFrame(f.type.content(),null,pos));
                    }else {
                        throw new SyntaxError("argument of 'if' has to be an optional or 'bool' got " + f.type, pos);
                    }
                }
                openBlocks.add(ifBlock);
                ret.add(block);
                context=ifBlock.context();
                ret.add(new ContextOpen(context,pos));
            }
            case ELSE -> {
                CodeBlock open=openBlocks.peekLast();
                if(!(open instanceof IfBlock ifBlock)){
                    throw new SyntaxError("'else' can only be used in if-blocks",pos);
                }
                if(finishedBranch) {
                    finishedBranch=false;
                }else{
                    ifBlock.branchTypes.add(new BranchWithEnd(typeStack,pos));
                }
                typeStack = ifBlock.elseTypes;

                ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                if(ifBlock.elsePositions.size()>0){//end else-context
                    ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                }
                Token tmp=ret.get(ifBlock.forkPos);
                ((BlockToken)tmp).delta=ret.size()-ifBlock.forkPos+1;

                context=ifBlock.elseBranch(ret.size(),pos);

                ret.add(block);
                if(ifBlock.elsePositions.size()<2){//start else-context after first else
                    ret.add(new ContextOpen(context,pos));
                }
            }
            case _IF -> {
                CodeBlock open=openBlocks.peekLast();
                if(!(open instanceof IfBlock ifBlock)){
                    throw new SyntaxError("'_if' can only be used in if-blocks",pos);
                }
                TypeFrame f = typeStack.pop();
                ifBlock.elseTypes = typeStack;
                typeStack = typeStack.clone();
                if(f.type!=Type.BOOL){
                    if(f.type.isOptional()){
                        typeStack.push(new TypeFrame(f.type.content(),null,pos));
                    }else {
                        throw new SyntaxError("argument of '_if' has to be an optional or 'bool' got " + f.type, pos);
                    }
                }
                context=ifBlock.newBranch(ret.size(),pos);
                ret.add(block);
                ret.add(new ContextOpen(context,pos));
            }
            case END_IF,END_WHILE ->
                    throw new RuntimeException("block tokens of type "+block.blockType+
                            " should not exist at this stage of compilation");
            case WHILE -> {
                WhileBlock whileBlock = new WhileBlock(ret.size(), pos, context);
                whileBlock.loopTypes=typeStack.clone();

                openBlocks.add(whileBlock);
                context= whileBlock.context();
                ret.add(block);
                ret.add(new ContextOpen(context,pos));
            }
            case DO -> {
                CodeBlock open=openBlocks.peekLast();
                if(!(open instanceof WhileBlock whileBlock)){
                    throw new SyntaxError("do can only be used in while- blocks",pos);
                }
                TypeFrame f = typeStack.pop();
                whileBlock.forkTypes=typeStack;
                typeStack=typeStack.clone();
                if(f.type!=Type.BOOL){
                    if(f.type.isOptional()){
                        typeStack.push(new TypeFrame(f.type.content(),null,pos));
                    }else {
                        throw new SyntaxError("argument of '_if' has to be an optional or 'bool' got " + f.type, pos);
                    }
                }

                ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                int forkPos=ret.size();
                //context variable will be reset on fork
                ret.add(block);
                context= whileBlock.fork(forkPos, pos);
                ret.add(new ContextOpen(context,pos));
            }
            case DO_WHILE -> {
                CodeBlock open=openBlocks.pollLast();
                if(!(open instanceof WhileBlock whileBlock)){
                    throw new SyntaxError("do can only be used in while- blocks",pos);
                }
                TypeFrame f = typeStack.pop();
                if(f.type!=Type.BOOL){
                    throw new SyntaxError("argument of 'do end' has to be 'bool' got "+f.type,pos);
                }//no else
                if(notAssignable(typeStack, ((WhileBlock) open).loopTypes)){
                    throw new SyntaxError("do-while body modifies the stack",pos);
                }

                whileBlock.fork(ret.size(), pos);//whileBlock.end() only checks if fork was called
                ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                context=((BlockContext)context).parent;
                ret.add(new BlockToken(BlockTokenType.DO_WHILE,pos,open.start - (ret.size()-1)));
            }
            case SWITCH -> {
                TypeFrame f=typeStack.pop();
                SwitchCaseBlock switchBlock=new SwitchCaseBlock(f.type,ret.size(), pos,context);
                switchBlock.defaultTypes=typeStack;

                openBlocks.addLast(switchBlock);
                ret.add(new SwitchToken(switchBlock,pos));
                switchBlock.newSection(ret.size(),pos);
                //find case statement
                int j=i+1;
                while(j<tokens.size()&&(!(tokens.get(j) instanceof BlockToken))){
                    j++;
                }
                if(j>= tokens.size()){
                    throw new SyntaxError("found no case-statement for switch at "+pos,
                            tokens.get(tokens.size()-1).pos);
                }else if(((BlockToken)tokens.get(j)).blockType!=BlockTokenType.CASE){
                    throw new SyntaxError("unexpected statement in switch at "+pos+" "+
                            tokens.get(j)+" expected 'case' statement",
                            tokens.get(j).pos);
                }
                List<Token> caseValues=tokens.subList(i+1,j);
                findEnumFields(switchBlock, caseValues);
                context=switchBlock.caseBlock(typeCheck(caseValues,context,globalConstants,
                                new RandomAccessStack<>(8),null,pos,ioContext).tokens,
                        tokens.get(j).pos);
                ret.add(new ContextOpen(context,pos));
                i=j;
                if(switchBlock.hasMoreCases()){//only clone typeStack if there are more cases
                    typeStack=typeStack.clone();
                }
            }
            case END_CASE -> {
                CodeBlock open=openBlocks.peekLast();
                if(!(open instanceof SwitchCaseBlock switchBlock)){
                    throw new SyntaxError("end-case can only be used in switch-case-blocks",pos);
                }
                if(finishedBranch) {
                    finishedBranch=false;
                }else{
                    switchBlock.caseTypes.add(new BranchWithEnd(typeStack,pos));
                }
                typeStack=switchBlock.defaultTypes;

                ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                context=((BlockContext)context).parent;
                ret.add(new BlockToken(BlockTokenType.END_CASE, pos, -1));
                switchBlock.newSection(ret.size(),pos);
                //find case statement
                int j=i+1;
                while(j<tokens.size()&&(!(tokens.get(j) instanceof BlockToken))){
                    j++;
                }
                if(j>= tokens.size()){
                    throw new SyntaxError("found no case-statement for switch at "+pos,
                            tokens.get(tokens.size()-1).pos);
                }
                Token t=tokens.get(j);
                if(((BlockToken)t).blockType==BlockTokenType.CASE){
                    List<Token> caseValues=tokens.subList(i+1,j);
                    findEnumFields(switchBlock, caseValues);
                    context=switchBlock.caseBlock(typeCheck(caseValues,context,globalConstants,
                            new RandomAccessStack<>(8),null,pos,ioContext).tokens,tokens.get(j).pos);
                    ret.add(new ContextOpen(context,pos));

                    if(switchBlock.hasMoreCases()){//only clone typeStack if there are more cases
                        typeStack=typeStack.clone();
                    }
                }else if(((BlockToken)t).blockType==BlockTokenType.DEFAULT){
                    context=switchBlock.defaultBlock(ret.size(),pos);
                    ret.add(new ContextOpen(context,pos));
                }else if(((BlockToken)t).blockType==BlockTokenType.END){
                    switchBlock.end(ret.size(),pos,ioContext);
                    openBlocks.removeLast();//remove switch-block form blocks
                    for(Integer p:switchBlock.blockEnds){
                        Token tmp=ret.get(p);
                        ((BlockToken)tmp).delta=ret.size()-p;
                    }
                    for(BranchWithEnd branch:switchBlock.caseTypes){
                        merge(typeStack,pos,branch.types,branch.end,"switch");
                    }
                }else{
                    throw new SyntaxError("unexpected statement after end-case at "+tokens.get(i).pos+": "+
                            block+" expected 'case', 'default' or 'end' statement",pos);
                }
                i=j;
            }
            case CASE ->
                    throw new SyntaxError("unexpected 'case' statement",pos);
            case DEFAULT ->
                    throw new SyntaxError("unexpected 'default' statement",pos);
            case ARRAY -> {
                ArrayBlock arrayBlock = new ArrayBlock(ret.size(), BlockType.CONST_ARRAY, pos, context);
                arrayBlock.prevTypes=typeStack;
                typeStack=new RandomAccessStack<>(8);
                openBlocks.add(arrayBlock);
            }
            case END -> {
                CodeBlock open=openBlocks.pollLast();
                if(open==null){
                    throw new SyntaxError("unexpected '}' statement ",pos);
                }
                Token tmp;
                switch(open.type) {
                    case CONST_ARRAY -> {
                        Type type = null;
                        for (TypeFrame f : typeStack) {
                            Optional<Type> merged = Type.commonSuperType(type, f.type, false);
                            if(merged.isEmpty()){
                                throw new SyntaxError("cannot merge types "+type+" and "+f.type,pos);
                            }
                            type = merged.get();
                        }
                        if (type == null) {
                            type = Type.ANY;
                        }
                        typeStack = ((ArrayBlock) open).prevTypes;
                        List<Token> subList = ret.subList(open.start, ret.size());
                        ArrayList<Value> values = new ArrayList<>(subList.size());
                        boolean constant = true;
                        for (Token v : subList) {
                            if (v instanceof ValueToken) {
                                values.add(((ValueToken) v).value);
                            } else {
                                values.clear();
                                constant = false;
                                break;
                            }
                        }
                        if (constant) {
                            subList.clear();
                            try {
                                for (int p = 0; p < values.size(); p++) {
                                    values.set(p, values.get(p).castTo(type));
                                }
                                Value list = Value.createArray(Type.arrayOf(type), values.toArray(Value[]::new));
                                typeStack.push(new TypeFrame(list.type, list, pos));

                                ret.add(new ValueToken(list, open.startPos));
                            } catch (ConcatRuntimeError e) {
                                throw new SyntaxError(e, pos);
                            }
                        } else {
                            typeStack.push(new TypeFrame(Type.arrayOf(type), null, pos));

                            ArrayList<Token> listTokens = new ArrayList<>(subList);
                            subList.clear();
                            ret.add(new ArrayCreatorToken(listTokens, pos));
                        }
                    }
                    case IF -> {
                        if(((IfBlock) open).forkPos!=-1){
                            if(finishedBranch){
                                finishedBranch=false;
                            }else {
                                ((IfBlock) open).branchTypes.add(new BranchWithEnd(typeStack,pos));
                            }
                            typeStack = ((IfBlock) open).elseTypes;

                            tmp=ret.get(((IfBlock) open).forkPos);
                            ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            context=((BlockContext)context).parent;
                            //when there is no else then the last branch has to jump onto the close operation
                            ((BlockToken)tmp).delta=ret.size()-((IfBlock) open).forkPos;
                            if(((IfBlock) open).elsePositions.size()>0){//close-else context
                                ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                                context=((BlockContext)context).parent;
                            }
                        }else{//close else context
                            ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                            context=((BlockContext)context).parent;
                        }
                        for(Integer branch:((IfBlock) open).elsePositions){
                            tmp=ret.get(branch);
                            ((BlockToken)tmp).delta=ret.size()-branch;
                        }
                        ret.add(new BlockToken(BlockTokenType.END_IF,pos,-1));
                        FilePosition mainEnd=pos;
                        if(finishedBranch){
                            if(((IfBlock) open).branchTypes.size()>0){
                                finishedBranch=false;
                                BranchWithEnd bWe=((IfBlock) open).branchTypes.removeLast();
                                typeStack=bWe.types;
                                mainEnd=bWe.end;
                            }else{
                                break;//exit on all branches of if statement
                            }
                        }
                        //merge Types
                        for(BranchWithEnd branch:((IfBlock) open).branchTypes){
                            merge(typeStack,mainEnd,branch.types,branch.end,"if");
                        }
                    }
                    case WHILE -> {
                        if(finishedBranch){//exit if loop is traversed at least once
                            finishedBranch=false;
                        }else if(notAssignable(typeStack, ((WhileBlock) open).loopTypes)){
                            throw new SyntaxError("while body modifies the stack",pos);
                        }
                        typeStack=((WhileBlock) open).forkTypes;

                        ((WhileBlock)open).end(pos);
                        ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                        context=((BlockContext)context).parent;
                        tmp=ret.get(((WhileBlock) open).forkPos);
                        ret.add(new BlockToken(BlockTokenType.END_WHILE,pos, open.start - ret.size()));
                        ((BlockToken)tmp).delta=ret.size()-((WhileBlock)open).forkPos;
                    }
                    case SWITCH_CASE -> {//end switch with default
                        if(((SwitchCaseBlock)open).defaultJump==-1){
                            throw new SyntaxError("missing end-case statement",pos);
                        }
                        ret.add(new Token(TokenType.CONTEXT_CLOSE,pos));
                        context=((BlockContext)context).parent;
                        ((SwitchCaseBlock)open).end(ret.size(),pos,ioContext);
                        for(Integer p:((SwitchCaseBlock) open).blockEnds){
                            tmp=ret.get(p);
                            ((BlockToken)tmp).delta=ret.size()-p;
                        }
                        FilePosition mainEnd=pos;
                        if(finishedBranch){
                            if(((SwitchCaseBlock)open).caseTypes.size()>0){
                                finishedBranch=false;
                                BranchWithEnd bWe=((SwitchCaseBlock)open).caseTypes.removeLast();
                                typeStack=bWe.types;
                                mainEnd=bWe.end;
                            }else{
                                break;//exit on all branches of if statement
                            }
                        }
                        for(BranchWithEnd branch:((SwitchCaseBlock)open).caseTypes){
                            merge(typeStack,mainEnd,branch.types,branch.end,"switch");
                        }
                    }
                    case PROCEDURE,PROC_TYPE,ANONYMOUS_TUPLE,TUPLE,ENUM,UNION,STRUCT ->
                            throw new SyntaxError("blocks of type "+open.type+
                                    " should not exist at this stage of compilation",pos);
                }
            }
        }
        return new BlockCheckReturn(finishedBranch,typeStack,context,i);
    }

    private void typeCheckAssert(RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret, Token t) throws RandomAccessStack.StackUnderflow, SyntaxError, TypeError {
        Token prev;
        assert t instanceof AssertToken;
        TypeFrame f= typeStack.pop();
        if(f.type!=Type.BOOL){
            throw new SyntaxError("parameter of assertion has to be a bool got "+f.type, t.pos);
        }else if(f.value!=null&&!f.value.asBool()){//addLater? replace assert with drop if condition is always true
            throw new SyntaxError("assertion failed: "+ ((AssertToken) t).message, t.pos);
        }
        if((prev= ret.get(ret.size()-1)) instanceof ValueToken){
            try {
                if(!((ValueToken) prev).value.asBool()){
                    throw new SyntaxError("assertion failed: "+ ((AssertToken) t).message, t.pos);
                }
            } catch (TypeError e) {
                throw new SyntaxError(e, t.pos);
            }
        }else{
            ret.add(t);
        }
    }
    private void typeCheckDrop(StackModifierToken t, RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret) throws RandomAccessStack.StackUnderflow {
        for(TypeFrame dropped: typeStack.drop(t.args[0])){
            if(dropped.type instanceof Type.OverloadedProcedurePointer opp){
                if(ret.get(opp.tokenPos).tokenType==TokenType.OVERLOADED_PROC_PTR){
                    //delete unresolved procedure pointers
                    ret.set(opp.tokenPos, new Token(TokenType.NOP, opp.pushedAt));
                    t.args[0]--;
                }
            }
        }
        if(t.args[0]>0){
            ret.add(t);
        }
    }
    private void typeCheckDup(StackModifierToken t, RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret) {
        TypeFrame duped= typeStack.get(t.args[0]);
        if(duped.type instanceof Type.OverloadedProcedurePointer opp){
            typeStack.push(new TypeFrame(new Type.OverloadedProcedurePointer(opp.proc,opp.genArgs, ret.size(),opp.pushedAt),
                    null, t.pos));
            ret.add(new Token(TokenType.OVERLOADED_PROC_PTR, t.pos));
        }else{
            duped=new TypeFrame(duped.type,duped.value, t.pos);
            typeStack.push(duped);
            ret.add(t);
        }
    }
    private void typeCheckStackSet(StackModifierToken t, RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret) {
        TypeFrame replaced= typeStack.get(t.args[0]);
        if(replaced.type instanceof Type.OverloadedProcedurePointer opp&&
                ret.get(opp.tokenPos).tokenType==TokenType.OVERLOADED_PROC_PTR){
            ret.set(opp.tokenPos,new Token(TokenType.NOP,opp.pushedAt));
        }
        //addLater? update overloaded procedure pointers
        typeStack.set(t.args[0],typeStack.get(t.args[1]));
        ret.add(t);
    }
    private void typeCheckTypeModifier(String name, Function<Type,Value> modifier,ArrayList<Token> ret,
                                       RandomAccessStack<TypeFrame> typeStack, FilePosition pos) throws SyntaxError,
            RandomAccessStack.StackUnderflow, TypeError {
        TypeFrame f = typeStack.pop();
        if(f.type==Type.TYPE) {
            if (f.value == null) {
                throw new SyntaxError("type argument of '"+name+"' has to be a constant",pos);
            }
            Value modified = modifier.apply(f.value.asType());
            f = new TypeFrame(modified.type, modified, pos);
            typeStack.push(f);
            Token prev;
            if (ret.size() > 0 && (prev = ret.get(ret.size() - 1)) instanceof ValueToken) {
                try {
                    ret.set(ret.size() - 1,
                            new ValueToken(modifier.apply(((ValueToken) prev).value.asType()),pos));
                } catch (ConcatRuntimeError e) {
                    throw new SyntaxError(e, pos);
                }
            } else {
                throw new SyntaxError("token before of '" + name + "' has to be a constant type", pos);
            }
        }else{
            throw new SyntaxError("invalid argument-type for '" + name + "':" + f.type +
                    " argument has to be a constant type", pos);
        }
    }


    private void typeCheckCallPtr(RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret,
                                  HashMap<VariableId, Value> globalConstants, IOContext ioContext, FilePosition pos)
            throws RandomAccessStack.StackUnderflow, SyntaxError {
        TypeFrame f= typeStack.pop();
        if(f.type instanceof Type.Procedure){
            typeCheckCall("call-ptr", typeStack, (Type.Procedure) f.type, ret,ioContext, pos, true);
            ret.add(new Token(TokenType.CALL_PTR, pos));
        }else if(f.type instanceof Type.OverloadedProcedurePointer){
            CallMatch call = typeCheckOverloadedCall("call-ptr",
                    ((Type.OverloadedProcedurePointer) f.type).proc,
                    ((Type.OverloadedProcedurePointer) f.type).genArgs,
                    typeStack, globalConstants, ret, ioContext, pos);
            Callable proc=call.called;
            proc.markAsUsed();
            setOverloadedProcPtr(ret,((Type.OverloadedProcedurePointer) f.type), (Value) proc);
            ret.add(new Token(TokenType.CALL_PTR, pos));
        }else{
            throw new SyntaxError("invalid argument for operator '()': "+f.type, pos);
        }
    }

    private void setOverloadedProcPtr(ArrayList<Token> ret, Type.OverloadedProcedurePointer opp, Value proc) throws SyntaxError {
        Token prev=ret.set(opp.tokenPos,new ValueToken(proc, opp.pushedAt));
        if(prev.tokenType!=TokenType.OVERLOADED_PROC_PTR&&prev.tokenType!=TokenType.NOP){
            throw new SyntaxError("overloaded procedure pointer is resolved more than once ",opp.pushedAt);
        }
    }

    private void findEnumFields(SwitchCaseBlock switchBlock, List<Token> caseValues) {
        if(switchBlock.switchType instanceof Type.Enum sType){
            String[] names=((Type.Enum) switchBlock.switchType).entryNames;
            for(int p = 0; p < caseValues.size(); p++){
                Token prev=caseValues.get(p);
                if(prev instanceof IdentifierToken id&&id.type==IdentifierType.WORD){
                    for(int p2 = 0;p2 < names.length; p2++){
                        if(id.name.equals(names[p2])){
                            caseValues.set(p,new ValueToken(sType.entries[p2],id.pos));
                            break;
                        }
                    }
                }
            }
        }
    }

    private void typeCheckLambda(HashMap<VariableId, Value> globalConstants, VariableContext context,
                                 RandomAccessStack<TypeFrame> typeStack
            , IOContext ioContext, ArrayList<Token> ret, DeclareLambdaToken t) throws SyntaxError {
        RandomAccessStack<TypeFrame> procTypes=new RandomAccessStack<>(8);
        for(Type in:t.inTypes){
            procTypes.push(new TypeFrame(in,null, t.pos));
        }
        ProcedureContext newContext=new ProcedureContext(context);
        newContext.generics.addAll(t.generics);//move generics to new context
        TypeCheckResult res=typeCheck(t.tokens,newContext, globalConstants,procTypes,t.outTypes,t.endPos, ioContext);
        Type[] outTypes;
        if(t.outTypes!=null){
            outTypes=t.outTypes;
        }else{
            outTypes=new Type[res.types.size()];
            for(int i= outTypes.length-1;i>=0;i--){
                try {
                    outTypes[i]=res.types.pop().type;
                } catch (RandomAccessStack.StackUnderflow e) {
                    throw new RuntimeException(e);
                }
            }
        }
        Type.Procedure procType=t.generics.size()>0?
                Type.GenericProcedureType.create(t.generics.toArray(new Type.GenericParameter[0]),t.inTypes,outTypes):
                Type.Procedure.create(t.inTypes, outTypes);
        Value.Procedure lambda=Value.createProcedure(null,false,procType,res.tokens,t.pos,t.endPos, newContext);
        lambda.typeCheckState = Value.TypeCheckState.CHECKED;//mark lambda as checked
        //push type information
        typeStack.push(new TypeFrame(lambda.type, lambda, t.pos));
        if(newContext.curried.isEmpty()){
            ret.add(new ValueToken(TokenType.LAMBDA,lambda, t.pos));
        }else{
            ret.add(new ValueToken(TokenType.CURRIED_LAMBDA,lambda, t.pos));
        }
    }

    private void typeCheckNew(RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret, IOContext ioContext,FilePosition pos)
            throws SyntaxError, RandomAccessStack.StackUnderflow {
        Token prev;
        if(ret.size()<1||!((prev= ret.remove(ret.size()-1)) instanceof ValueToken)) {
            throw new SyntaxError("token before of new has to be a type", pos);
        }
        if(typeStack.pop().type!=Type.TYPE){
            throw new RuntimeException("type-stack out of sync with tokens");
        }
        try {
            Type type = ((ValueToken)prev).value.asType();
            if(type instanceof Type.Tuple){
                Type[] elements = ((Type.Tuple) type).getElements();
                typeCheckCall("new", typeStack,
                        Type.Procedure.create(elements,new Type[]{type}), ret,ioContext, pos, false);

                int c=((Type.Tuple) type).elementCount();
                if(c < 0){
                    throw new ConcatRuntimeError("the element count has to be at least 0");
                }
                int iMin= ret.size()-c;
                if(iMin>=0){
                    Value[] values=new Value[c];
                    for(int j=c-1;j>=0;j--){
                        prev= ret.get(iMin+j);
                        if(prev instanceof ValueToken){
                            values[j]=((ValueToken) prev).value.castTo(elements[j]);
                        }else{
                            break;
                        }
                    }
                    if(c==0||values[0]!=null){//all types resolved successfully
                        ret.subList(iMin, ret.size()).clear();
                        ret.add(new ValueToken(Value.createTuple((Type.Tuple)type,values),pos));
                        return;
                    }
                }
            }else if(type.isMemory()||type.isArray()){
                TypeFrame f= typeStack.pop();
                if(f.type!=Type.UINT&&f.type!=Type.INT){
                    throw new SyntaxError("invalid argument for '"+type+" new': "+f.type+
                            " expected an integer", pos);
                }//no else
                if(type.isArray()){
                    f=typeStack.pop();
                    typeCheckCast(f.type,2,type.content(), ret,ioContext,pos);
                }
                typeStack.push(new TypeFrame(type,null, pos));
                //addLater? support new memory/array in pre-evaluation
            }else{
                throw new SyntaxError("cannot apply 'new' to type "+type, pos);
            }
            ret.add(new TypedToken(TokenType.NEW,type, pos));
        } catch (ConcatRuntimeError e) {
            throw new SyntaxError(e, prev.pos);
        }
    }

    private void typeCheckIdentifier(Token t, ArrayList<Token> ret, VariableContext context, HashMap<VariableId, Value> globalConstants,
                                     RandomAccessStack<TypeFrame> typeStack, IOContext ioContext) throws SyntaxError, RandomAccessStack.StackUnderflow {
        Token prev;
        IdentifierToken identifier=(IdentifierToken) t;
        switch (identifier.type) {
            case DECLARE, IMPLICIT_DECLARE -> {
                try {//remember constant declarations
                    Type type;
                    if(identifier.type==IdentifierType.IMPLICIT_DECLARE){
                        type=typeStack.peek().type;
                        if(!type.canAssignTo(Type.ANY)){
                            throw new SyntaxError("cannot create variable of type "+type,t.pos);
                        }
                    }else if (ret.size() > 0 && (prev = ret.remove(ret.size() - 1)) instanceof ValueToken) {
                        type = ((ValueToken) prev).value.asType();
                        if(typeStack.pop().type != Type.TYPE){
                            throw new RuntimeException("type stack out of sync with token list");
                        }
                    }else {
                        throw new SyntaxError("Token before declaration has to be a type", identifier.pos);
                    }
                    Mutability mutability=identifier.mutability();
                    VariableId id = context.declareVariable( identifier.name, type, mutability,
                            identifier.accessibility(), identifier.pos, ioContext);
                    //only remember root-level constants
                    if (identifier.isNative()) {
                        if(id.mutability==Mutability.MUTABLE){
                            throw new SyntaxError("native variables have to be immutable",t.pos);
                        }else if(identifier.type==IdentifierType.IMPLICIT_DECLARE){
                            throw new SyntaxError("native variables cannot be implicitly typed",t.pos);
                        }
                        globalConstants.put(id, Value.loadNativeConstant(type, identifier.name, t.pos));
                        break;
                    }
                    TypeFrame val = typeStack.pop();
                    typeCheckCast(val.type,1, id.type, ret,ioContext, t.pos);
                    if (id.mutability==Mutability.IMMUTABLE && id.context.procedureContext() == null
                            && (prev = ret.get(ret.size()-1)) instanceof ValueToken) {
                        Value value = ((ValueToken) prev).value;
                        if(!value.type.isDeeplyImmutable()){
                            value=value.clone(true,null);
                        }
                        globalConstants.put(id, value.castTo(type));
                        if(val.type != ((ValueToken) prev).value.type){
                            throw new RuntimeException("type stack out of sync with token list");
                        }
                        ret.remove(ret.size() - 1);
                        break;//don't add token to code
                    }
                    ret.add(new VariableToken(identifier.pos, identifier.name, id,AccessType.DECLARE, context));
                } catch (ConcatRuntimeError e) {
                    throw new SyntaxError(e, t.pos);
                }
            }
            case WORD -> {
                boolean isMutabilityMarked=(identifier.flags&IdentifierToken.MUTABILITY_MASK)!=0;
                if((identifier.flags|IdentifierToken.MUTABILITY_MASK)!=IdentifierToken.MUTABILITY_MASK){
                    throw new SyntaxError("modifiers can only be used in declarations",t.pos);
                }
                Declareable d = context.getDeclareable(identifier.name);
                if(d==null){
                    throw new SyntaxError("variable "+identifier.name+" does not exist",identifier.pos);
                }
                d.markAsUsed();
                DeclareableType type = d.declarableType();
                switch (type) {
                    case PROCEDURE,NATIVE_PROC,GENERIC_PROCEDURE,OVERLOADED_PROCEDURE -> {
                        if(isMutabilityMarked){
                            throw new SyntaxError("values of type "+declarableName(type,false)+
                                    " cannot be marked as mutable",t.pos);
                        }
                        OverloadedProcedure proc =
                                d instanceof OverloadedProcedure?(OverloadedProcedure) d:new OverloadedProcedure((Callable) d);
                        CallMatch match = typeCheckOverloadedCall(
                                "procedure "+identifier.name, new OverloadedProcedure(proc),null,
                                typeStack,globalConstants,ret,ioContext,t.pos);
                        match.called.markAsUsed();
                        CallToken token = new CallToken( match.called, identifier.pos);
                        ret.add(token);
                    }
                    case VARIABLE, CURRIED_VARIABLE -> {
                        if(isMutabilityMarked){//addLater better error message
                            throw new SyntaxError("values of type "+declarableName(type,false)+
                                    " cannot be marked as mutable",t.pos);
                        }
                        VariableId id = (VariableId) d;
                        id = context.wrapCurried(identifier.name, id, identifier.pos);
                        Value constValue = globalConstants.get(id);
                        if (constValue != null) {
                            typeStack.push(new TypeFrame(constValue.type,constValue,t.pos));
                            ret.add(new ValueToken(constValue, identifier.pos));
                        } else {
                            typeStack.push(new TypeFrame(id.type,null,t.pos));
                            ret.add(new VariableToken(identifier.pos, identifier.name, id,
                                    AccessType.READ, context));
                        }
                    }
                    case MACRO ->
                            throw new SyntaxError("Unable to expand macro \""+((Macro)d).name+
                                    "\", try defining it before its first appearance in a procedure body",t.pos);
                    case TUPLE, ENUM, GENERIC,STRUCT -> {
                        Type asType=(Type)d;
                        if(isMutabilityMarked){
                            asType=asType.setMutability(identifier.mutability());
                        }
                        Value e = Value.ofType(asType);
                        typeStack.push(new TypeFrame(e.type,e,t.pos));
                        ret.add(new ValueToken(e, identifier.pos));
                    }
                    case CONSTANT -> {
                        Value e = ((Constant) d).value;
                        if(isMutabilityMarked){
                            if(e.type==Type.TYPE){
                                try {
                                    e=Value.ofType(e.asType().setMutability(identifier.mutability()));
                                } catch (TypeError ex) {
                                    throw new SyntaxError(ex,t.pos);
                                }
                            }else{
                                throw new SyntaxError("values of type "+e.type+
                                        " cannot be marked as mutable",t.pos);
                            }
                        }
                        typeStack.push(new TypeFrame(e.type,e,t.pos));
                        ret.add(new ValueToken(e, identifier.pos));
                    }
                    case GENERIC_TUPLE -> {
                        GenericTuple g = (GenericTuple) d;
                        String tupleName=g.name;
                        Type[] genArgs=getArguments(tupleName,DeclareableType.GENERIC_TUPLE,g.params.length, typeStack, ret, t.pos);
                        Type typeValue = Type.Tuple.create(g.name, g.isPublic, g.params.clone(), genArgs,
                                g.types.clone(), g.declaredAt);
                        if(isMutabilityMarked){
                            typeValue=typeValue.setMutability(identifier.mutability());
                        }
                        Value tupleType = Value.ofType(typeValue);
                        typeStack.push(new TypeFrame(Type.TYPE,tupleType,identifier.pos));
                        ret.add(new ValueToken(tupleType,identifier.pos));
                    }
                    case GENERIC_STRUCT -> {
                        GenericStruct g = (GenericStruct) d;
                        String structName=g.name;
                        Type[] genArgs=getArguments(structName,DeclareableType.GENERIC_STRUCT, g.params.length, typeStack, ret, t.pos);
                        Type typeValue = Type.Struct.create(g.name, g.isPublic, g.extended, g.params.clone(), genArgs,
                                g.fields,g.types.clone(), g.declaredAt);
                        if(isMutabilityMarked){
                            typeValue=typeValue.setMutability(identifier.mutability());
                        }
                        Value structValue = Value.ofType(typeValue);
                        typeStack.push(new TypeFrame(Type.TYPE,structValue,identifier.pos));
                        ret.add(new ValueToken(structValue,identifier.pos));
                    }
                }
            }
            case VAR_WRITE -> {
                Declareable d= context.getDeclareable(identifier.name);
                if(d==null){
                    throw new SyntaxError("variable "+identifier.name+" does not exist", t.pos);
                }//no else
                if(d.declarableType()!=DeclareableType.VARIABLE||((VariableId)d).mutability==Mutability.IMMUTABLE){
                    throw new SyntaxError(declarableName(d.declarableType(),false)+" "+
                            identifier.name+" (declared at "+d.declaredAt()+") is not a mutable variable", t.pos);
                }//no else
                if(d.accessibility()==Accessibility.READ_ONLY&&!((VariableId) d).declaredAt.path.equals(t.pos.path)){
                    throw new SyntaxError("variable "+identifier.name+"(declared at "+d.declaredAt()
                            +") has cannot be modified",t.pos);
                }
                VariableId id=(VariableId) d;
                id.mutability=Mutability.MUTABLE;
                context.wrapCurried(identifier.name,id,identifier.pos);
                assert !globalConstants.containsKey(id);
                TypeFrame f = typeStack.pop();
                typeCheckCast(f.type,1, id.type, ret, ioContext,t.pos);
                ret.add(new VariableToken(identifier.pos,identifier.name,id,
                        AccessType.WRITE, context));
            }
            case PROC_ID ->
                typeCheckPushProcPointer(identifier, typeStack, ret, globalConstants, context, ioContext, t.pos);
            case GET_FIELD -> {
                TypeFrame f=typeStack.pop();
                boolean hasField=false;
                try {
                    if(f.type instanceof Type.Struct){
                        Integer index=((Type.Struct) f.type).indexByName.get(identifier.name);
                        if(index!=null){
                            Type.StructField field=((Type.Struct) f.type).fields[index];
                            if((field.accessibility()!=Accessibility.PRIVATE||field.declaredAt().path.equals(t.pos.path))){
                                typeStack.push(new TypeFrame(((Type.Struct) f.type).getElement(index),null, t.pos));
                                ret.add(new TupleElementAccess(index, false, t.pos));
                                hasField=true;
                            }else{
                                ioContext.stdErr.println("cannot access private field "+field.name()+" (declared at "+
                                        field.declaredAt()+") of struct "+f.type);
                            }
                        }
                    }else if(f.type==Type.TYPE&&f.value!=null&&f.value.asType() instanceof Type.Enum anEnum){
                        int p=0;
                        for(;p<anEnum.entryNames.length;p++){
                            if(anEnum.entryNames[p].equals(identifier.name)){
                                hasField=true;
                                typeStack.push(new TypeFrame(anEnum,anEnum.entries[p],t.pos));
                                ValueToken entry = new ValueToken(anEnum.entries[p], t.pos);
                                prev=ret.get(ret.size()-1);
                                if(prev instanceof ValueToken&&((ValueToken) prev).value.type==Type.TYPE&&
                                        ((ValueToken) prev).value.asType().equals(anEnum)) {
                                    ret.set(ret.size()-1,entry);
                                }else{//addLater? better way to replace previous value
                                    ret.add(new StackModifierToken(TokenType.STACK_DROP,new int[]{1},t.pos));
                                    ret.add(entry);
                                }
                                break;
                            }
                        }
                    }
                    if(!hasField) {
                        if(identifier.name.equals("type")){
                            if(f.type.canAssignTo(Type.ANY)){
                                typeStack.push(new TypeFrame(Type.TYPE,f.value==null?null:Value.ofType(f.value.type),t.pos));
                                ret.add(new InternalFieldToken(InternalFieldName.TYPE_OF, t.pos));
                                hasField = true;
                            }
                        }else if(f.type.isMemory()){
                            switch (identifier.name){
                                case "length" ->{
                                    typeStack.push(new TypeFrame(Type.UINT, null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.LENGTH, t.pos));
                                    hasField = true;
                                }
                                case "capacity" ->{
                                    typeStack.push(new TypeFrame(Type.UINT, null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.CAPACITY, t.pos));
                                    hasField = true;
                                }
                                case "offset" ->{
                                    typeStack.push(new TypeFrame(Type.UINT, null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.OFFSET, t.pos));
                                    hasField = true;
                                }
                            }
                        }else if (identifier.name.equals("length") && f.type.isArray()) {
                            typeStack.push(new TypeFrame(Type.UINT, null, t.pos));
                            ret.add(new InternalFieldToken(InternalFieldName.LENGTH, t.pos));
                            hasField = true;
                        }else if (f.type == Type.TYPE) {
                            hasField = true;
                            switch (identifier.name) {//TODO don't allow type fields as enum entry names
                                case "content" -> {
                                    if (f.value != null) {
                                        f = new TypeFrame(Type.TYPE, Value.ofType(f.value.asType().content()), t.pos);
                                    }
                                    typeStack.push(f);
                                    ret.add(new InternalFieldToken(InternalFieldName.CONTENT, t.pos));
                                }
                                case "inTypes" -> {
                                    typeStack.push(new TypeFrame(Type.arrayOf(Type.TYPE), null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IN_TYPES, t.pos));
                                }
                                case "outTypes" -> {
                                    typeStack.push(new TypeFrame(Type.arrayOf(Type.TYPE), null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.OUT_TYPES, t.pos));
                                }
                                case "name" -> {
                                    typeStack.push(new TypeFrame(Type.RAW_STRING(), null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.TYPE_NAME, t.pos));
                                }
                                case "fieldNames" -> {
                                    typeStack.push(new TypeFrame(Type.arrayOf(Type.RAW_STRING()), null, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.TYPE_FIELDS, t.pos));
                                }
                                case "isEnum" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType() instanceof Type.Enum ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_ENUM, t.pos));
                                }
                                case "isArray" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType().isArray() ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_ARRAY, t.pos));
                                }
                                case "isMemory" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType().isMemory() ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_MEMORY, t.pos));
                                }
                                case "isProc" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType() instanceof Type.Procedure ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_PROC, t.pos));
                                }
                                case "isOptional" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType().isOptional() ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_OPTIONAL, t.pos));
                                }
                                case "isTuple" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType() instanceof Type.Tuple ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_TUPLE, t.pos));
                                }
                                case "isStruct" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType() instanceof Type.Struct ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_STRUCT, t.pos));
                                }
                                case "isUnion" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType() instanceof Type.UnionType ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_UNION, t.pos));
                                }
                                case "isMutable" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType().isMutable() ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_MUTABLE, t.pos));
                                }
                                case "isMaybeMutable" -> {
                                    typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                            f.value.asType().isMaybeMutable() ? Value.TRUE : Value.FALSE, t.pos));
                                    ret.add(new InternalFieldToken(InternalFieldName.IS_MAYBE_MUTABLE, t.pos));
                                }
                                default -> hasField = false;
                            }
                        }else if(f.type.isOptional()){
                            if(identifier.name.equals("hasValue")){
                                typeStack.push(new TypeFrame(Type.BOOL, f.value == null ? null :
                                        f.value.hasValue() ? Value.TRUE : Value.FALSE, t.pos));
                                ret.add(new InternalFieldToken(InternalFieldName.HAS_VALUE, t.pos));
                                hasField=true;
                            }else if(identifier.name.equals("value")){
                                typeStack.push(new TypeFrame(f.type.content(), null, t.pos));
                                //addLater re-add explicit unwrapping of optionals (with static check if optional is nonempty)
                                //hasField=true;
                                throw new UnsupportedOperationException("unimplemented");
                            }
                        }else if(f.type instanceof Type.Tuple){
                            try {
                                int index = Integer.parseInt(identifier.name);
                                if(index>=0&&index<((Type.Tuple) f.type).elementCount()){
                                    typeStack.push(new TypeFrame(((Type.Tuple) f.type).getElement(index),null, t.pos));
                                    ret.add(new TupleElementAccess(index, false, t.pos));
                                    hasField=true;
                                }
                            }catch (NumberFormatException ignored){}
                        }
                        if(!hasField)
                            throw new SyntaxError(f.type+" does not have a field "+identifier.name,t.pos);
                    }
                } catch (TypeError e) {
                    throw new SyntaxError(e,t.pos);
                }
            }
            case SET_FIELD -> {
                TypeFrame f=typeStack.pop();
                TypeFrame val=typeStack.pop();
                boolean hasField=false;
                if(f.type instanceof Type.Struct struct){
                    Integer index= struct.indexByName.get(identifier.name);
                    if(index!=null){
                        if(!struct.isMutable()){
                            throw new SyntaxError("cannot write to field "+identifier.name+" of immutable struct "+
                                    struct.baseName,t.pos);
                        }
                        Type.StructField field = struct.fields[index];
                        if(field.accessibility()==Accessibility.PUBLIC||field.declaredAt().path.equals(t.pos.path)){
                            if(field.mutable()){
                                typeCheckCast(val.type,2,struct.getElement(index), ret,ioContext, t.pos);
                                ret.add(new TupleElementAccess(index, true, t.pos));
                                hasField=true;
                            }else{
                                throw new SyntaxError("field "+identifier.name+" (declared at "+ field.declaredAt()+
                                        ") of struct "+struct.baseName+" (declared at "+struct.declaredAt+") is not mutable",t.pos);
                            }
                        }else{
                            throw new SyntaxError("field "+identifier.name+" (declared at "+ field.declaredAt()+
                                    ") of struct "+struct.baseName+" (declared at "+struct.declaredAt+") cannot be accessed",t.pos);
                        }
                    }
                }//no else
                if(f.type instanceof Type.Tuple tuple){
                    try {
                        int index = Integer.parseInt(identifier.name);
                        if(index>=0&&index< tuple.elementCount()){
                            Type fieldType = tuple.getElement(index);
                            if(tuple.isMutable(index)){
                                typeCheckCast(val.type,2,fieldType, ret, ioContext,t.pos);
                                ret.add(new TupleElementAccess(index, true, t.pos));
                                hasField=true;
                            }else{
                                throw new SyntaxError("element at "+index+" in tuple "+ tuple.name+
                                        " (declared at "+tuple.declaredAt+") is not mutable",t.pos);
                            }
                        }
                    }catch (NumberFormatException ignored){}
                }
                if(!hasField)
                    throw new SyntaxError(f.type+" does not have a mutable field "+identifier.name,t.pos);
            }
            case DECLARE_FIELD -> {
                if(!(context instanceof StructContext)){
                    throw new SyntaxError("field declarations are only allowed in structs",t.pos);
                }
                if (ret.size() <= 0 || !((prev = ret.remove(ret.size() - 1)) instanceof ValueToken)) {
                    throw new SyntaxError("Token before declaration has to be a type", identifier.pos);
                }
                Type type;
                try {
                    type = ((ValueToken) prev).value.asType();
                } catch (TypeError e) {
                    throw new SyntaxError(e,t.pos);
                }
                if(typeStack.pop().type != Type.TYPE){
                    throw new RuntimeException("type stack out of sync with token list");
                }
                Accessibility accessibility = identifier.accessibility();
                if(accessibility==Accessibility.DEFAULT){
                    accessibility = Accessibility.PUBLIC;//struct fields are public by default
                }
                ((StructContext)context).fields.add(new StructFieldWithType(
                        new Type.StructField(identifier.name, accessibility,
                                identifier.mutability()==Mutability.MUTABLE,t.pos),type));
            }
        }
    }

    private void typeCheckPushProcPointer(IdentifierToken identifier, RandomAccessStack<TypeFrame> typeStack, ArrayList<Token> ret,
                                          HashMap<VariableId, Value> globalConstants, VariableContext context, IOContext ioContext,
                                          FilePosition pos) throws SyntaxError, RandomAccessStack.StackUnderflow {
        Declareable d= context.getDeclareable(identifier.name);
        if(d==null){
            throw new SyntaxError("procedure "+ identifier.name+" does not exist", pos);
        }else if(d instanceof Value.NativeProcedure proc){
            Type.Procedure procType= (Type.Procedure) proc.type;
            if(procType instanceof Type.GenericProcedureType genType &&
                    ((Type.GenericProcedureType)procType).explicitGenerics.length>0) {
                Type[] genArgs=getArguments(proc.name,DeclareableType.NATIVE_PROC,genType.explicitGenerics.length, typeStack, ret, pos);
                IdentityHashMap<Type.GenericParameter, Type> genMap = new IdentityHashMap<>();
                for (int i = 0; i < genArgs.length; i++) {
                    genMap.put(genType.explicitGenerics[i], genArgs[i]);
                }
                procType=genType.replaceGenerics(genMap);
                try {
                    proc=(Value.NativeProcedure)proc.castTo(procType);
                } catch (ConcatRuntimeError e) {
                    throw new RuntimeException(e);
                }
            }
            d.markAsUsed();
            typeStack.push(new TypeFrame(procType,proc, pos));
            ValueToken token=new ValueToken(proc, pos);
            ret.add(token);
        }else if(d instanceof Value.Procedure proc) {
            pushSimpleProcPointer(proc, typeStack, ret, globalConstants, ioContext, pos);
        }else if(d instanceof GenericProcedure proc){
            Type[] genArgs;
            if(proc.procType.explicitGenerics.length>0) {
                genArgs = getArguments(proc.name,DeclareableType.GENERIC_PROCEDURE,proc.procType.explicitGenerics.length,
                        typeStack, ret, pos);
            }else{
                genArgs=new Type[0];
            }
            if(proc.procType.implicitGenerics.length==0){
                IdentityHashMap<Type.GenericParameter,Type> genMap=new IdentityHashMap<>();
                for(int i=0;i<genArgs.length;i++){
                    genMap.put(proc.procType.explicitGenerics[i], genArgs[i]);
                }
                Value.Procedure non_generic=proc.withPrams(genMap);
                pushSimpleProcPointer(non_generic, typeStack, ret, globalConstants, ioContext, pos);
            }else{
                typeStack.push(new TypeFrame(new Type.OverloadedProcedurePointer(proc,genArgs, ret.size(), pos),null, pos));
                ret.add(new Token(TokenType.OVERLOADED_PROC_PTR, pos));//push placeholder token
            }
        }else if(d instanceof OverloadedProcedure proc){
            Type[] genArgs=getArguments(proc.name,DeclareableType.GENERIC_PROCEDURE,proc.nGenericParams, typeStack, ret, pos);
            typeStack.push(new TypeFrame(new Type.OverloadedProcedurePointer(proc,genArgs, ret.size(), pos),null, pos));
            ret.add(new Token(TokenType.OVERLOADED_PROC_PTR, pos));//push placeholder token
        }else{
            throw new SyntaxError(declarableName(d.declarableType(),false)+" "+
                    identifier.name+" (declared at "+d.declaredAt()+") is not a procedure", pos);
        }
    }

    private void pushSimpleProcPointer(Value.Procedure procedure, RandomAccessStack<TypeFrame> typeStack,
                                       ArrayList<Token> ret, HashMap<VariableId, Value> globalConstants,
                                       IOContext ioContext, FilePosition pos) throws SyntaxError {
        procedure.markAsUsed();
        typeCheckProcedure(procedure, globalConstants, ioContext);//type check procedure before it is pushed onto the stack
        ValueToken token = new ValueToken(procedure, pos);
        typeStack.push(new TypeFrame(token.value.type, token.value, pos));
        ret.add(token);
    }

    private Type[] getArguments(String name,DeclareableType type,int nArgs, RandomAccessStack<TypeFrame> typeStack,
                              ArrayList<Token> ret, FilePosition pos) throws SyntaxError, RandomAccessStack.StackUnderflow {
        Type[] genArgs=new Type[nArgs];
        Token prev;
        for (int j = genArgs.length - 1; j >= 0; j--) {
            if (ret.size() <= 0) {
                throw new SyntaxError("Not enough arguments for " +
                        declarableName(type, false) + " " + name,pos);
            }
            prev = ret.remove(ret.size()-1);
            if (!(prev instanceof ValueToken)) {
                throw new SyntaxError("invalid token for type-parameter:" + prev, prev.pos);
            }
            try {
                genArgs[j] = ((ValueToken) prev).value.asType();
                Value value = typeStack.pop().value;
                if(value==null||value.type!=Type.TYPE||!value.asType().equals(genArgs[j])){
                    throw new RuntimeException("type-stack out of sync with tokens");
                }
            } catch (TypeError e) {
                throw new SyntaxError(e, prev.pos);
            }
        }
        return genArgs;
    }

    private void typeCheckCast(Type src, int stackPos, Type target, ArrayList<Token> ret,
                               IOContext ioContext, FilePosition pos) throws SyntaxError {
        Type.BoundMaps bounds=new Type.BoundMaps();
        if(src instanceof Type.OverloadedProcedurePointer){
            if(!(target instanceof Type.Procedure)){
                throw new SyntaxError("overloaded procedure cannot be cast to "+target,pos);
            }
            ArrayList<CallMatch> matches=new ArrayList<>();
            OverloadedProcedure proc = ((Type.OverloadedProcedurePointer) src).proc;
            for(Callable c: proc.procedures){
                bounds=new Type.BoundMaps();
                if(c.type().canAssignTo(target,bounds)){//TODO allow casts
                    if(c instanceof GenericProcedure){
                        assert bounds.r.size()==0;
                        IdentityHashMap<Type.GenericParameter,Type> update=new IdentityHashMap<>(bounds.l.size());
                        for(Map.Entry<Type.GenericParameter, Type.GenericBound> e:bounds.l.entrySet()){
                            if(e.getValue().min()!=null){
                                if(e.getValue().max()==null||e.getValue().min().canAssignTo(e.getValue().max())){
                                    update.put(e.getKey(),e.getValue().min());
                                }else{
                                    throw new SyntaxError("cannot cast from "+ src+" to "+ target, pos);
                                }
                            }else if(e.getValue().max()!=null){
                                update.put(e.getKey(),e.getValue().max());
                            }
                        }
                        c=((GenericProcedure) c).withPrams(update);
                    }
                    matches.add(new CallMatch(c,c.type(),new IdentityHashMap<>(),0,0,new HashMap<>()));
                }
            }
            if(matches.isEmpty()){
                for(Callable c:proc.procedures){
                    ioContext.stdErr.println(" - "+c.name()+":"+c.type()+" at "+c.declaredAt());
                }
                throw new SyntaxError("no version of "+proc.name+" matches "+target,pos);
            }else if(matches.size()>1){
                //addLater resolve best match (if possible)
                for(CallMatch c:matches){
                    ioContext.stdErr.println(" - "+c.called.name()+":"+c.type+" at "+c.called.declaredAt());
                }
                throw new SyntaxError("more than one version of "+proc.name+" matches "+target,pos);
            }
            setOverloadedProcPtr(ret,((Type.OverloadedProcedurePointer) src),(Value)matches.get(0).called);
        }else{
            if(!src.canAssignTo(target,bounds)){//cast to correct type if necessary
                bounds=new Type.BoundMaps();
                if(!src.canCastTo(target,bounds)){
                    throw new SyntaxError("cannot cast from "+ src+" to "+ target, pos);
                }
                if(stackPos==1){
                    ret.add(new TypedToken(TokenType.CAST, target, pos));
                }else{
                    ret.add(new ArgCastToken(stackPos, target, pos));
                }
            }
            if(bounds.l.size()>0||bounds.r.size()>0){
                //generics only exist in Tuple/Struct/Procedures concrete values should not contain any generic information
                throw new RuntimeException("generics should not exist here");
            }
        }
    }

    private void typeCheckCall(String procName, RandomAccessStack<TypeFrame> typeStack, Type.Procedure type,
                               ArrayList<Token> tokens,IOContext ioContext,FilePosition pos, boolean isPtr)
            throws RandomAccessStack.StackUnderflow, SyntaxError {
        int offset=isPtr?1:0;
        if(type instanceof Type.GenericProcedureType){
            //generic procedures should be type-checked with typeCheckOverloadedCall()
            throw new RuntimeException("unexpected generic procedure");
        }
        Type[] inTypes=new Type[type.inTypes.length];
        for(int i=inTypes.length-1;i>=0;i--){
            inTypes[i]=typeStack.pop().type;
        }
        Type.BoundMaps bounds=new Type.BoundMaps();
        for(int i=0;i<inTypes.length;i++){
            try{
                typeCheckCast(inTypes[i],inTypes.length-i+offset,type.inTypes[i], tokens,ioContext,pos);
            }catch (SyntaxError e){
                throw new SyntaxError("wrong parameters for "+procName+" "+Arrays.toString(type.inTypes)+
                        ": "+Arrays.toString(inTypes),pos);
            }
        }
        if(bounds.l.size()>0||bounds.r.size()>0){
            //generics only exist in Tuple/Struct/Procedures concrete values should not contain any generic information
            throw new RuntimeException("generics should not exist here");
        }
        for(Type t:type.outTypes){
            typeStack.push(new TypeFrame(t,null,pos));
        }
    }

    record CallMatch(Callable called, Type.Procedure type, IdentityHashMap<Type.GenericParameter,Type> genericParams,
                     int nCasts, int nImplicit,
                    /*overloaded procedure pointers in the arguments of this call match*/
                     HashMap<Type.OverloadedProcedurePointer,CallMatch> opps){}

    private static final Comparator<CallMatch> compareBySignature = (m1, m2) -> {
        int c = 0;
        for (int i = 0; i < m1.type.inTypes.length; i++) {
            if (c == 0) {
                if (m1.type.inTypes[i].canAssignTo(m2.type.inTypes[i])) {
                    if (!m2.type.inTypes[i].canAssignTo(m1.type.inTypes[i])) {
                        c = -1;
                    }
                } else {
                    if (m2.type.inTypes[i].canAssignTo(m1.type.inTypes[i])) {
                        c = 1;
                    } else {
                        return 0;
                    }
                }
            } else if (c < 0) {
                if (!m1.type.inTypes[i].canAssignTo(m2.type.inTypes[i])) {
                    return 0;
                }
            } else {
                if (!m2.type.inTypes[i].canAssignTo(m1.type.inTypes[i])) {
                    return 0;
                }
            }
        }
        return c;
    };
    private static final Comparator<CallMatch> compareByTypeArgs = (m1, m2) -> {
        //type arguments with lower "depth" are better
        int maxDepth1 = 0,maxDepth2=0;
        for (Map.Entry<Type.GenericParameter, Type> e:m1.genericParams.entrySet()) {
            if(e.getKey().isImplicit){
                maxDepth1=Math.max(maxDepth1,e.getValue().depth());
            }
        }
        for (Map.Entry<Type.GenericParameter, Type> e:m2.genericParams.entrySet()) {
            if(e.getKey().isImplicit){
                maxDepth2=Math.max(maxDepth2,e.getValue().depth());
            }
        }
        return maxDepth1-maxDepth2;
    };

    private CallMatch typeCheckOverloadedCall(String procName, OverloadedProcedure proc, Type[] ptrGenArgs,
                                              RandomAccessStack<TypeFrame> typeStack, HashMap<VariableId, Value> globalConstants,
                                              ArrayList<Token> tokens, IOContext ioContext, FilePosition pos)
            throws RandomAccessStack.StackUnderflow, SyntaxError {
        Type[] typeArgs=null;
        if(proc.nGenericParams!=0){
            if(ptrGenArgs!=null){
                typeArgs=ptrGenArgs;
            }else{
                typeArgs=getArguments(procName,DeclareableType.GENERIC_PROCEDURE,proc.nGenericParams,typeStack, tokens, pos);
            }
        }
        Type[] inTypes=new Type[proc.nArgs];
        for(int i=inTypes.length-1;i>=0;i--){
            inTypes[i]=typeStack.pop().type;
        }
        ArrayList<CallMatch> matchingCalls=new ArrayList<>();
        for(Callable p1:proc.procedures){
            typeCheckPotentialCall(p1, typeArgs, inTypes, matchingCalls, pos);
        }
        CallMatch match = findMatchingCall(matchingCalls, proc, inTypes, globalConstants, ioContext, pos);
        updateProcedureArguments(match, inTypes, tokens, ptrGenArgs!=null, pos);
        for(Type t:match.type.outTypes){
            typeStack.push(new TypeFrame(t,null,pos));
        }
        return match;
    }
    private void typeCheckPotentialCall(Callable potentialCall, Type[] typeArgs, Type[] inTypes,
                           ArrayList<CallMatch> matchingCalls, FilePosition pos) throws SyntaxError {
        Type.Procedure type= potentialCall.type();
        boolean isMatch=true;
        IdentityHashMap<Type.GenericParameter,Type> generics=new IdentityHashMap<>();
        int nCasts=0,nImplicit=0;
        HashMap<Type.OverloadedProcedurePointer,CallMatch> opps=new HashMap<>();
        if(typeArgs !=null) {//update type signature
            for (int i = 0; i < typeArgs.length; i++) {
                generics.put(((Type.GenericProcedureType) type).explicitGenerics[i], typeArgs[i]);
            }
            type = type.replaceGenerics(generics);
        }
        Type.BoundMaps bounds=new Type.BoundMaps();
        boolean hasOpp=false;
        for(int i = 0; i< inTypes.length; i++){
            if(inTypes[i] instanceof Type.OverloadedProcedurePointer){
                hasOpp=true;
            }else if(!inTypes[i].canAssignTo(type.inTypes[i],bounds)){
                nCasts++;
                if(!inTypes[i].canCastTo(type.inTypes[i],bounds)){//try to implicitly cast input arguments
                    isMatch=false;
                    break;
                }
            }
        }
        if(hasOpp){
            for(int i = 0; i< inTypes.length; i++){
                if(inTypes[i] instanceof Type.OverloadedProcedurePointer){
                    bounds = resolveOppParam(type.inTypes[i],bounds,(Type.OverloadedProcedurePointer)inTypes[i],
                            opps, pos);
                    if(bounds==null||!isMatch){
                        isMatch=false;
                        break;
                    }
                }
            }
        }
        if(isMatch){
            if(bounds.r.size()>0){
                assert bounds.l.size()==0;
                nImplicit=bounds.r.size();
                IdentityHashMap<Type.GenericParameter,Type> implicitGenerics=new IdentityHashMap<>();
                isMatch = resolveGenericParams(bounds.r, implicitGenerics);
                type=type.replaceGenerics(implicitGenerics);
                generics.putAll(implicitGenerics);
            }//no else
            if(isMatch){
                matchingCalls.add(new CallMatch(potentialCall,type,generics,nCasts,nImplicit,opps));
            }
        }
    }
    private Type.BoundMaps resolveOppParam(Type calledType, Type.BoundMaps callBounds,
                                           Type.OverloadedProcedurePointer param,
                                           HashMap<Type.OverloadedProcedurePointer,CallMatch> opps,
                                           FilePosition pos) throws SyntaxError {
        ArrayList<CallMatch> matches=new ArrayList<>();
        ArrayList<Type.BoundMaps> matchBounds=new ArrayList<>();
        boolean matchesParam;
        for(Callable c: param.proc.procedures){
            matchesParam=true;
            Type.Procedure procType=c.type();
            Type.BoundMaps test= callBounds.copy();
            if(procType.canAssignTo(calledType,test)){
                IdentityHashMap<Type.GenericParameter,Type> implicitGenerics=new IdentityHashMap<>();
                if(test.l.size()>0){
                    IdentityHashMap<Type.GenericParameter, Type.GenericBound> l = test.l;
                    matchesParam = resolveGenericParams(l, implicitGenerics);
                    //update generics in generic parameters
                    for(Map.Entry<Type.GenericParameter, Type.GenericBound> p:test.r.entrySet()){
                        Type min = p.getValue().min();
                        Type max = p.getValue().max();
                        p.setValue(new Type.GenericBound(min==null?null:min.replaceGenerics(implicitGenerics),
                                max==null?null:max.replaceGenerics(implicitGenerics)));
                    }
                    procType=procType.replaceGenerics(implicitGenerics);
                    if(c instanceof GenericProcedure){
                        c=((GenericProcedure) c).withPrams(implicitGenerics);
                    }
                }
                //check rBounds
                for(Type.GenericBound b:test.r.values()){
                    if(b.min()!=null&&b.max()!=null&&!b.min().canAssignTo(b.max())){
                        matchesParam=false;
                        break;
                    }
                }
                if(matchesParam){
                    matches.add(new CallMatch(c,procType,implicitGenerics,0,implicitGenerics.size(),
                            new HashMap<>()));
                    matchBounds.add(test);
                }
            }//addLater? allow casting
        }
        if(matches.size()==0){
            return null;
        }else if(matches.size()>1){
            //TODO resolve best match (if possible) otherwise return null
            for(CallMatch m:matches){
                System.err.println(" - "+m.type+":"+m.called.name()+" at "+m.called.declaredAt());
            }
            throw new SyntaxError("more than one version of "+ param.name+" matches the given type: "+
                    param, pos);
        }
        opps.put(param,matches.get(0));
        callBounds =matchBounds.get(0);
        return callBounds;
    }
    private boolean resolveGenericParams(IdentityHashMap<Type.GenericParameter, Type.GenericBound> bounds,
                                         IdentityHashMap<Type.GenericParameter, Type> implicitGenerics) {
        for(Map.Entry<Type.GenericParameter, Type.GenericBound> e: bounds.entrySet()){
            if(e.getValue().min()!=null){
                if(e.getValue().max()==null||e.getValue().min().canAssignTo(e.getValue().max())){
                    implicitGenerics.put(e.getKey(),e.getValue().min());
                }else{
                    return false;
                }
            }else if(e.getValue().max()!=null){
                implicitGenerics.put(e.getKey(),e.getValue().max());
            }
        }
        return true;
    }
    private CallMatch findMatchingCall(ArrayList<CallMatch> matchingCalls, OverloadedProcedure proc,
                                       Type[] inTypes, HashMap<VariableId, Value> globalConstants,
                                       IOContext ioContext, FilePosition pos) throws SyntaxError {
        for(int i = 0; i< matchingCalls.size(); i++){
            CallMatch c= matchingCalls.get(i);
            if(c.called instanceof Value.Procedure){
                typeCheckProcedure((Value.Procedure)c.called, globalConstants, ioContext);
            }else if(c.called instanceof GenericProcedure){//resolve generic procedure
                Value.Procedure withPrams = ((GenericProcedure) c.called).withPrams(c.genericParams);
                try {
                    typeCheckProcedure(withPrams, globalConstants, ioContext);
                }catch (SyntaxError err){
                    throw new SyntaxError(err, pos);
                }
                matchingCalls.set(i,new CallMatch(withPrams,withPrams.type(),c.genericParams,c.nCasts,c.nImplicit,c.opps));
            }
        }
        if(matchingCalls.size()==0){
            for(Callable c:proc.procedures){
                ioContext.stdErr.println(" - "+c.name()+":"+ c.type()+" at "+ c.declaredAt());
            }
            throw new SyntaxError("no version of "+ proc.name+" matches the given arguments "+Arrays.toString(inTypes), pos);
        }else if(matchingCalls.size()>1){
            Comparator<CallMatch> matchSort= Comparator.comparingInt((CallMatch m) -> m.nCasts)
                    .thenComparing(compareBySignature).thenComparingInt(m -> m.nImplicit).thenComparing(compareByTypeArgs);
            matchingCalls.sort(matchSort);
            int i=1;
            while(i< matchingCalls.size()&&matchSort.compare(matchingCalls.get(0), matchingCalls.get(i))==0){
                i++;
            }
            if(i>1){
                ioContext.stdErr.println("more than one version of "+ proc.name+" matches the given arguments "+Arrays.toString(inTypes));
                for(int k=0;k<i;k++){
                    ioContext.stdErr.println(proc.name+":"+ matchingCalls.get(k).type+" at "+ matchingCalls.get(k).called.declaredAt());
                }
                throw new SyntaxError("cannot resolve procedure call", pos);
            }
        }
        return matchingCalls.get(0);
    }
    private void updateProcedureArguments(CallMatch match, Type[] inTypes, ArrayList<Token> tokens, boolean isPtr,
                                          FilePosition pos) throws SyntaxError {
        for(int i = 0; i< inTypes.length; i++){
            if (inTypes[i] instanceof Type.OverloadedProcedurePointer) {
                CallMatch opp= match.opps.get((Type.OverloadedProcedurePointer) inTypes[i]);
                if(opp!=null&&!(opp.called instanceof OverloadedProcedure||opp.called instanceof GenericProcedure)){
                    inTypes[i]=opp.type;
                }else{
                    throw new SyntaxError("unresolved overloaded procedure pointer in procedure signature: "+ inTypes[i], pos);
                }
            }//no else
            if(!inTypes[i].canAssignTo(match.type.inTypes[i],new Type.BoundMaps())){
                tokens.add(new ArgCastToken(inTypes.length-i+(isPtr ?1:0), match.type.inTypes[i], pos));
            }
        }
        for(Map.Entry<Type.OverloadedProcedurePointer, CallMatch> opp: match.opps.entrySet()){
            //resolve overloaded procedure pointers
            setOverloadedProcPtr(tokens,opp.getKey(),(Value)opp.getValue().called);
        }
    }


    enum ExitType{
        NORMAL,FORCED,ERROR
    }

    public RandomAccessStack<Value> run(Program program, String[] arguments,IOContext context){
        RandomAccessStack<Value> stack=new RandomAccessStack<>(16);
        Declareable main=program.rootContext.getElement("main",true);
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
                    }else if(type.inTypes[0].isArray()&&type.inTypes[0].content().equals(Type.RAW_STRING())){//string list
                        ArrayList<Value> args=new ArrayList<>(arguments.length);
                        for(String s:arguments){
                            args.add(Value.ofString(s,false));
                        }
                        stack.push(Value.createArray(Type.arrayOf(Type.RAW_STRING()),args.toArray(Value[]::new)));
                    }
                }
                recursiveRun(stack,(Value.Procedure)main,new ArrayList<>(),null,null,context);
            }
        }
        return stack;
    }

    private void getInternalField(InternalFieldToken op, RandomAccessStack<Value> stack)
            throws RandomAccessStack.StackUnderflow, ConcatRuntimeError {
        switch (op.opType) {
            case CONTENT -> {
                Type wrappedType = stack.pop().asType();
                stack.push(Value.ofType(wrappedType.content()));
            }
            case IN_TYPES -> {
                Type wrappedType = stack.pop().asType();
                stack.push(Value.createArray(Type.arrayOf(Type.TYPE),wrappedType.inTypes().stream().map(Value::ofType)
                        .toArray(Value[]::new)));
            }
            case OUT_TYPES -> {
                Type wrappedType = stack.pop().asType();
                stack.push(Value.createArray(Type.arrayOf(Type.TYPE),wrappedType.outTypes().stream().map(Value::ofType)
                        .toArray(Value[]::new)));
            }
            case TYPE_NAME -> {
                Type wrappedType = stack.pop().asType();
                stack.push(Value.ofString(wrappedType.typeName(),false));
            }
            case TYPE_FIELDS -> {
                Type wrappedType = stack.pop().asType();
                stack.push(Value.createArray(Type.arrayOf(Type.RAW_STRING()),wrappedType.fields().stream()
                        .map(s->Value.ofString(s,false)).toArray(Value[]::new)));
            }
            case TYPE_OF -> {
                Value val = stack.pop();
                stack.push(Value.ofType(val.type));
            }
            case LENGTH -> {
                Value val = stack.pop();
                stack.push(Value.ofInt(val.length(),true));
            }
            case OFFSET -> {
                Value val = stack.pop();
                stack.push(Value.ofInt(((Value.ArrayLike)val).offset(),true));
            }
            case CAPACITY -> {
                Value val = stack.pop();
                stack.push(Value.ofInt(((Value.ArrayLike)val).capacity(),true));
            }
            case IS_OPTIONAL -> {
                Type type = stack.pop().asType();
                stack.push(type.isOptional()?Value.TRUE:Value.FALSE);
            }
            case IS_ENUM -> {
                Type type = stack.pop().asType();
                stack.push(type instanceof Type.Enum?Value.TRUE:Value.FALSE);
            }
            case IS_PROC -> {
                Type type = stack.pop().asType();
                stack.push(type instanceof Type.Procedure?Value.TRUE:Value.FALSE);
            }
            case IS_TUPLE -> {
                Type type = stack.pop().asType();
                stack.push(type instanceof Type.Tuple?Value.TRUE:Value.FALSE);
            }
            case IS_STRUCT -> {
                Type type = stack.pop().asType();
                stack.push(type instanceof Type.Struct?Value.TRUE:Value.FALSE);
            }
            case IS_UNION -> {
                Type type = stack.pop().asType();
                stack.push(type instanceof Type.UnionType?Value.TRUE:Value.FALSE);
            }
            case IS_MUTABLE -> {
                Type type = stack.pop().asType();
                stack.push(type.isMutable()?Value.TRUE:Value.FALSE);
            }
            case IS_MAYBE_MUTABLE -> {
                Type type = stack.pop().asType();
                stack.push(type.isMaybeMutable()?Value.TRUE:Value.FALSE);
            }
            case IS_ARRAY -> {
                Type type = stack.pop().asType();
                stack.push(type.isArray()?Value.TRUE:Value.FALSE);
            }
            case IS_MEMORY -> {
                Type type = stack.pop().asType();
                stack.push(type.isMemory()?Value.TRUE:Value.FALSE);
            }
            case HAS_VALUE -> {
                Value value= stack.pop();
                stack.push(value.hasValue()?Value.TRUE:Value.FALSE);
            }
        }
    }

    private ExitType recursiveRun(RandomAccessStack<Value> stack, CodeSection program,ArrayList<Value[]> globalVariables,
                                  ArrayList<Value[]> variables,Value[] curried, IOContext context){
        if(variables==null){
            variables=new ArrayList<>();
            variables.add(new Value[program.context().varCount()]);
        }
        int ip=0;
        ArrayList<Token> tokens=program.tokens();
        while(ip<tokens.size()){
            Token next=tokens.get(ip);
            boolean incIp=true;
            try {
                switch (next.tokenType) {
                    case NOP -> {}
                    case LAMBDA, VALUE -> {
                        assert next instanceof ValueToken;
                        ValueToken value = (ValueToken) next;
                        if(!value.value.type.isDeeplyImmutable()){
                            stack.push(value.value.clone(true,null));
                        }else{
                            stack.push(value.value);
                        }
                    }
                    case CURRIED_LAMBDA -> {
                        assert next instanceof ValueToken;
                        Value.Procedure proc=(Value.Procedure)((ValueToken) next).value;
                        Value[] curried2=new Value[proc.context.curried.size()];
                        for(int i=0;i<proc.context.curried.size();i++){
                            VariableId id=proc.context.curried.get(i).source;
                            if(id instanceof CurriedVariable){
                                curried2[i]=curried[id.id];
                            }else{
                                curried2[i]=variables.get(id.level)[id.id];
                            }
                        }
                        stack.push(proc.withCurried(curried2));
                    }
                    case OPERATOR -> {
                        assert next instanceof InternalFieldToken;
                        getInternalField((InternalFieldToken) next, stack);
                    }
                    case NEW_ARRAY -> {//{ e1 e2 ... eN }
                        assert next instanceof ArrayCreatorToken;
                        RandomAccessStack<Value> listStack=new RandomAccessStack<>(((ArrayCreatorToken)next).tokens.size());
                        ExitType res=recursiveRun(listStack,((ArrayCreatorToken)next),globalVariables,variables,curried,context);
                        if(res!=ExitType.NORMAL){
                            if(res==ExitType.ERROR) {
                                context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                            }
                            return res;
                        }
                        ArrayList<Value> values=new ArrayList<>(listStack.asList());
                        Type type;
                        try {
                            type = values.stream().map(v -> v.type).reduce(null,
                                    (a, b) -> Type.commonSuperTypeThrow(a, b, false));
                        }catch (WrappedConcatError e){
                            throw e.wrapped;
                        }
                        for(int i=0;i< values.size();i++){
                            values.set(i,values.get(i).castTo(type));
                        }
                        stack.push(Value.createArray(Type.arrayOf(type),values.toArray(Value[]::new)));
                    }
                    case CAST -> {
                        assert next instanceof TypedToken;
                        Type type=((TypedToken)next).target;
                        if(type==null){//dynamic operation
                            type=stack.pop().asType();
                        }
                        Value val = stack.pop();
                        stack.push(val.castTo(type));
                    }
                    case NEW -> {
                        assert next instanceof TypedToken;
                        Type type=((TypedToken)next).target;
                        if(type==null){//dynamic operation
                            type=stack.pop().asType();
                        }
                        if(type instanceof Type.Tuple){
                            int count=((Type.Tuple)type).elementCount();
                            Value[] values=new Value[count];
                            for(int i=1;i<= values.length;i++){
                                values[count-i]= stack.pop();//values should already have the correct types
                            }
                            stack.push(Value.createTuple((Type.Tuple)type,values));
                        }else if(type.isMemory()){
                            long initCap= stack.pop().asLong();
                            stack.push(Value.createMemory(type,initCap));
                        }else if(type.isArray()){
                            long initCap = stack.pop().asLong();
                            Value fill = stack.pop();
                            Value array=Value.createArray(type,fill,initCap);
                            stack.push(array);
                        }else{
                            throw new ConcatRuntimeError("new only supports arrays, memories, lists and tuples");
                        }
                    }
                    case DEBUG_PRINT -> context.stdOut.println(stack.pop().stringValue());
                    case STACK_DROP ->{
                        assert next instanceof StackModifierToken;
                        stack.drop(((StackModifierToken)next).args[0]);
                    }
                    case STACK_DUP -> {
                        assert next instanceof StackModifierToken;
                        stack.dup(((StackModifierToken)next).args[0]);
                    }
                    case STACK_SET -> {
                        assert next instanceof StackModifierToken;
                        stack.set(((StackModifierToken)next).args[0],((StackModifierToken)next).args[1]);
                    }
                    case VARIABLE -> {
                        assert next instanceof VariableToken;
                        VariableToken asVar=(VariableToken) next;
                        switch (asVar.accessType){
                            case READ -> {
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            stack.push((globalVariables==null?variables:globalVariables)
                                                    .get(asVar.id.level)[asVar.id.id]);
                                    case LOCAL -> {
                                        if (globalVariables != null) {
                                            stack.push(variables.get(asVar.id.level)[asVar.id.id]);
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
                                                    [asVar.id.id]=newValue;
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables.get(asVar.id.level)[asVar.id.id]=newValue;
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            throw new ConcatRuntimeError("cannot modify curried variables");
                                }
                            }
                            case DECLARE -> {
                                Type type= asVar.id.type;
                                assert type != null;
                                Value initValue=stack.pop();
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            (globalVariables==null?variables:globalVariables).get(asVar.id.level)
                                                    [asVar.id.id] = initValue;
                                    case LOCAL ->{
                                        if (globalVariables != null) {
                                            variables.get(asVar.id.level)[asVar.id.id]= initValue;
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
                        assert next instanceof AssertToken;
                        if(!stack.pop().asBool()){
                            throw new ConcatRuntimeError("assertion failed: "+((AssertToken)next).message);
                        }
                    }
                    case UNREACHABLE -> {
                        context.stdErr.println("reached unreachable statement: "+next.pos);
                        return ExitType.ERROR;
                    }
                    case OVERLOADED_PROC_PTR -> {
                        context.stdErr.println("unresolved overloaded procedure pointer: "+next.pos);
                        return ExitType.ERROR;
                    }
                    case DECLARE_LAMBDA, IDENTIFIER,OPTIONAL_OF,EMPTY_OPTIONAL,
                            MARK_MUTABLE,MARK_MAYBE_MUTABLE,MARK_IMMUTABLE,MARK_INHERIT_MUTABILITY,ARRAY_OF,MEMORY_OF ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case CONTEXT_OPEN -> {
                        assert next instanceof ContextOpen;
                        variables.add(new Value[((ContextOpen) next).context.varCount()]);
                    }
                    case CONTEXT_CLOSE -> {
                        if(variables.size()<=1){
                            throw new RuntimeException("unexpected CONTEXT_CLOSE operation");
                        }
                        variables.remove(variables.size()-1);
                    }
                    case CALL_NATIVE_PROC ,CALL_PROC, CALL_PTR -> {
                        Callable called;
                        if(next.tokenType==TokenType.CALL_PROC){
                            assert next instanceof CallToken;
                            called=((CallToken) next).called;
                        }else{
                            Value ptr = stack.pop();
                            if(!(ptr instanceof Callable)){
                                throw new ConcatRuntimeError("cannot call objects of type "+ptr.type);
                            }
                            called=(Callable) ptr;
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
                            assert ((Value.Procedure) called).context.curried.isEmpty() || procedure.curriedArgs != null;
                            ExitType e=recursiveRun(stack,procedure,globalVariables==null?variables:globalVariables,
                                    null,procedure.curriedArgs,context);
                            if(e!=ExitType.NORMAL){
                                if(e==ExitType.ERROR) {
                                    context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                                }
                                return e;
                            }
                        }else{
                            throw new RuntimeException("unexpected callable type: "+called.getClass());
                        }//no else
                    }
                    case RETURN -> {
                        return ExitType.NORMAL;
                    }
                    case BLOCK_TOKEN -> {
                        assert next instanceof BlockToken;
                        switch(((BlockToken)next).blockType){
                            case IF,_IF,DO -> {
                                Value c = stack.pop();
                                if(c.type.isOptional()){
                                    if(c.hasValue()){
                                        stack.push(c.unwrap());
                                    }else{
                                        ip+=((BlockToken) next).delta;
                                        incIp = false;
                                    }
                                }else if (!c.asBool()) {
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
                            case SWITCH,CASE,DEFAULT, ARRAY, END ->
                                throw new RuntimeException("blocks of type "+((BlockToken)next).blockType+
                                        " should be eliminated at compile time");
                        }
                    }
                    case SWITCH -> {
                        assert next instanceof SwitchToken;
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
                    case CAST_ARG -> {
                        assert next instanceof ArgCastToken;
                        stack.set(((ArgCastToken) next).offset,
                                stack.get(((ArgCastToken) next).offset).castTo(((ArgCastToken) next).target));
                    }
                    case TUPLE_GET_INDEX -> {
                        assert next instanceof TupleElementAccess;
                        Value tuple = stack.pop();
                        stack.push(tuple.get(((TupleElementAccess) next).index));
                    }
                    case TUPLE_SET_INDEX -> {
                        assert next instanceof TupleElementAccess;
                        Value tuple = stack.pop();
                        Value val  = stack.pop();
                        tuple.set(((TupleElementAccess) next).index, val);
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

    static Program compileAndRun(String path,String[] arguments,IOContext context) throws IOException {
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
            return null;
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
        return program;
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
