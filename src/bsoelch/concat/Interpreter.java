package bsoelch.concat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import static bsoelch.concat.Parser.declarableName;

public class Interpreter {

    static final IOContext defaultContext=new IOContext(System.in,System.out,System.err);

    enum ExitType{
        NORMAL,FORCED,ERROR
    }

    public RandomAccessStack<Value> run(Parser.Program program, String[] arguments, IOContext context){
        RandomAccessStack<Value> stack=new RandomAccessStack<>(16);
        Parser.Declareable main=program.rootContext().getElement("main",true);
        if(main==null){
            recursiveRun(stack,program,null,null,null,context);
        }else{
            if(program.tokens().size()>0){
                context.stdErr.println("programs with main procedure cannot contain code at top level "+
                        program.tokens().get(0).pos);
            }
            if(main.declarableType()!= Parser.DeclareableType.PROCEDURE){
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

    private ExitType recursiveRun(RandomAccessStack<Value> stack, Parser.CodeSection program, ArrayList<Value[]> globalVariables,
                                  ArrayList<Value[]> variables, Value[] curried, IOContext ioContext){
        if(variables==null){
            variables=new ArrayList<>();
            variables.add(new Value[program.context().varCount()]);
        }
        int ip=0;
        ArrayList<Parser.Token> tokens=program.tokens();
        while(ip<tokens.size()){
            Parser.Token next=tokens.get(ip);
            boolean incIp=true;
            try {
                switch (next.tokenType) {
                    case NOP -> {}
                    case LAMBDA, VALUE, GLOBAL_VALUE -> {
                        assert next instanceof Parser.ValueToken;
                        Parser.ValueToken value = (Parser.ValueToken) next;
                        if(!value.value.type.isDeeplyImmutable()){
                            stack.push(value.value.clone(true,null));
                        }else{
                            stack.push(value.value);
                        }
                    }
                    case CURRIED_LAMBDA -> {
                        assert next instanceof Parser.ValueToken;
                        Value.Procedure proc=(Value.Procedure)((Parser.ValueToken) next).value;
                        Value[] curried2=new Value[proc.context.curried.size()];
                        for(int i=0;i<proc.context.curried.size();i++){
                            Parser.VariableId id=proc.context.curried.get(i).source;
                            if(id instanceof Parser.CurriedVariable){
                                curried2[i]=curried[id.id];
                            }else{
                                curried2[i]=variables.get(id.level)[id.id];
                            }
                        }
                        stack.push(proc.withCurried(curried2));
                    }
                    case NEW_ARRAY -> {//{ e1 e2 ... eN }
                        assert next instanceof Parser.ArrayCreatorToken;
                        RandomAccessStack<Value> listStack=new RandomAccessStack<>(((Parser.ArrayCreatorToken)next).tokens.size());
                        ExitType res=recursiveRun(listStack,((Parser.ArrayCreatorToken)next),globalVariables,variables,curried,ioContext);
                        if(res!=ExitType.NORMAL){
                            if(res==ExitType.ERROR) {
                                ioContext.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
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
                        assert next instanceof Parser.TypedToken;
                        Type type=((Parser.TypedToken)next).target;
                        if(type==null){//dynamic operation
                            type=stack.pop().asType();
                        }
                        Value val = stack.pop();
                        stack.push(val.castTo(type));
                    }
                    case NEW -> {
                        assert next instanceof Parser.TypedToken;
                        Type type=((Parser.TypedToken)next).target;
                        if(type==null){//dynamic operation
                            type=stack.pop().asType();
                        }
                        if(type instanceof Type.TupleLike){
                            int count=((Type.TupleLike)type).elementCount();
                            Value[] values=new Value[count];
                            for(int i=1;i<= values.length;i++){
                                values[count-i]= stack.pop();//values should already have the correct types
                            }
                            stack.push(Value.createTuple((Type.TupleLike)type,values));
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
                    case DEBUG_PRINT -> ioContext.stdOut.println(stack.pop().stringValue());
                    case STACK_DROP ->{
                        assert next instanceof Parser.StackModifierToken;
                        stack.drop(((Parser.StackModifierToken)next).args[0],((Parser.StackModifierToken)next).args[1]);
                    }
                    case STACK_DUP -> {
                        assert next instanceof Parser.StackModifierToken;
                        stack.dup(((Parser.StackModifierToken)next).args[0]);
                    }
                    case STACK_ROT -> {
                        assert next instanceof Parser.StackModifierToken;
                        stack.rotate(((Parser.StackModifierToken)next).args[0],((Parser.StackModifierToken)next).args[1]);
                    }
                    case VARIABLE -> {
                        assert next instanceof Parser.VariableToken;
                        Parser.VariableToken asVar=(Parser.VariableToken) next;
                        switch (asVar.accessType){
                            case READ -> {
                                Value[] values;
                                switch (asVar.variableType){
                                    case GLOBAL ->
                                            values=(globalVariables==null?variables:globalVariables)
                                                    .get(asVar.id.level);
                                    case LOCAL -> {
                                        if (globalVariables != null) {
                                            values=variables.get(asVar.id.level);
                                        }else{
                                            throw new RuntimeException("access to local variable outside of procedure");
                                        }
                                    }
                                    case CURRIED ->
                                            values=curried;
                                    default -> throw new RuntimeException("unexpected variableType:"+asVar.variableType);
                                }
                                stack.push(values[asVar.id.id]);
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
                        assert next instanceof Parser.AssertToken;
                        if(!stack.pop().asBool()){
                            throw new ConcatRuntimeError("assertion failed: "+((Parser.AssertToken)next).message);
                        }
                    }
                    case UNREACHABLE -> {
                        ioContext.stdErr.println("reached unreachable statement: "+next.pos);
                        return ExitType.ERROR;
                    }
                    case OVERLOADED_PROC_PTR -> {
                        ioContext.stdErr.println("unresolved overloaded procedure pointer: "+next.pos);
                        return ExitType.ERROR;
                    }
                    case DECLARE_LAMBDA, IDENTIFIER,REFERENCE_TO,OPTIONAL_OF,EMPTY_OPTIONAL,
                            MARK_MUTABLE,MARK_MAYBE_MUTABLE,MARK_IMMUTABLE,MARK_INHERIT_MUTABILITY,ARRAY_OF,MEMORY_OF,STACK_SIZE ->
                            throw new RuntimeException("Tokens of type " + next.tokenType +
                                    " should be eliminated at compile time");
                    case CONTEXT_OPEN -> {
                        assert next instanceof Parser.ContextOpen;
                        variables.add(new Value[((Parser.ContextOpen) next).context.varCount()]);
                    }
                    case CONTEXT_CLOSE -> {
                        if(variables.size()<=1){
                            throw new RuntimeException("unexpected CONTEXT_CLOSE operation");
                        }
                        variables.remove(variables.size()-1);
                    }
                    case TRAIT_FIELD_ACCESS -> {
                        assert next instanceof Parser.TraitFieldAccess;
                        Value val = stack.peek();
                        Parser.Callable called;
                        if(((Parser.TraitFieldAccess) next).isDirect) {
                            called=val.type.getTraitField(((Parser.TraitFieldAccess) next).id);
                        }else{
                            if (!(val instanceof Value.TraitValue tv)) {
                                throw new RuntimeException("trait field access on non-trait value");
                            }
                            stack.pop();//call trait on unwrapped value
                            stack.push(tv.wrapped);

                            called=tv.wrapped.type.getTraitField(((Parser.TraitFieldAccess) next).id);
                        }
                        ExitType e=call(called, next, stack, globalVariables, variables, ioContext);
                        if(e!=ExitType.NORMAL){
                            return e;
                        }
                    }
                    case CALL_NATIVE_PROC ,CALL_PROC, CALL_PTR -> {
                        Parser.Callable called;
                        if(next.tokenType== Parser.TokenType.CALL_PROC){
                            assert next instanceof Parser.CallToken;
                            called=((Parser.CallToken) next).called;
                        }else{
                            Value ptr = stack.pop();
                            if(!(ptr instanceof Parser.Callable)){
                                throw new ConcatRuntimeError("cannot call objects of type "+ptr.type);
                            }
                            called=(Parser.Callable) ptr;
                        }
                        ExitType e=call(called, next, stack, globalVariables, variables, ioContext);
                        if(e!=ExitType.NORMAL){
                            return e;
                        }
                    }
                    case RETURN -> {
                        return ExitType.NORMAL;
                    }
                    case BLOCK_TOKEN -> {
                        assert next instanceof Parser.BlockToken;
                        switch(((Parser.BlockToken)next).blockType){
                            case IF,_IF,DO -> {
                                Value c = stack.pop();
                                if(c.type.isOptional()){
                                    if(c.hasValue()){
                                        stack.push(c.unwrap());
                                    }else{
                                        ip+=((Parser.BlockToken) next).delta;
                                        incIp = false;
                                    }
                                }else if (!c.asBool()) {
                                    ip+=((Parser.BlockToken) next).delta;
                                    incIp = false;
                                }
                            }
                            case DO_WHILE -> {
                                Value c = stack.pop();
                                if (c.asBool()) {
                                    ip+=((Parser.BlockToken) next).delta;
                                    incIp = false;
                                }
                            }
                            case ELSE,END_WHILE,END_CASE,FOR_ITERATOR_END -> {
                                ip+=((Parser.BlockToken) next).delta;
                                incIp = false;
                            }
                            case WHILE,END_IF -> {
                                //do nothing
                            }
                            case FOR_ARRAY_PREPARE ->
                                stack.push(Value.ofInt(0,true));
                            case FOR_ARRAY_LOOP ->{
                                long index=stack.pop().asLong();
                                Value.ArrayLike array=(Value.ArrayLike) stack.peek();
                                if(index<array.length()){
                                    stack.push(Value.ofInt(index,true));
                                    stack.push(array.get(index));
                                }else{
                                    stack.pop();//array
                                    ip+=((Parser.BlockToken) next).delta;
                                    incIp = false;
                                }
                            }
                            case FOR_ARRAY_END ->{
                                long index=stack.pop().asLong();
                                stack.push(Value.ofInt(index+1,true));
                                ip+=((Parser.BlockToken) next).delta;
                                incIp = false;
                            }
                            case FOR_ITERATOR_LOOP -> {
                                Value.TraitValue itr=(Value.TraitValue)stack.pop();
                                stack.push(itr.wrapped);//call trait on unwrapped value
                                Parser.Callable called=itr.wrapped.type.getTraitField(((Parser.ForIteratorLoop) next).itrNext);
                                ExitType e=call(called, next, stack, globalVariables, variables, ioContext);
                                if(e!=ExitType.NORMAL){
                                    return e;
                                }
                                Value nextValue=stack.pop();
                                if(nextValue.hasValue()){
                                    stack.push(nextValue.unwrap());
                                }else{
                                    stack.pop();//iterator
                                    ip+=((Parser.BlockToken) next).delta;
                                    incIp = false;
                                }
                            }
                            case FOR,SWITCH,CASE,DEFAULT, ARRAY, END, UNION_TYPE,TUPLE_TYPE,PROC_TYPE,ARROW,END_TYPE ->
                                    throw new RuntimeException("blocks of type "+((Parser.BlockToken)next).blockType+
                                            " should be eliminated at compile time");
                        }
                    }
                    case SWITCH -> {
                        assert next instanceof Parser.SwitchToken;
                        Value v= stack.pop();
                        Integer jumpTo=((Parser.SwitchToken)next).block.blockJumps.get(v);
                        if(jumpTo==null){
                            ip+=((Parser.SwitchToken)next).block.defaultJump;
                        }else{
                            ip+=jumpTo;
                        }
                        incIp=false;
                    }
                    case EXIT -> {
                        long exitCode=stack.pop().asLong();
                        ioContext.stdErr.println("exited with exit code:"+exitCode);
                        return ExitType.FORCED;
                    }
                    case CAST_ARG -> {
                        assert next instanceof Parser.ArgCastToken;
                        stack.set(((Parser.ArgCastToken) next).offset,
                                stack.get(((Parser.ArgCastToken) next).offset).castTo(((Parser.ArgCastToken) next).target));
                    }
                    case TUPLE_GET_INDEX -> {
                        assert next instanceof Parser.TupleElementAccess;
                        Value tuple = stack.pop();
                        stack.push(tuple.getField(((Parser.TupleElementAccess) next).index));
                    }
                    case TUPLE_SET_INDEX -> {
                        assert next instanceof Parser.TupleElementAccess;
                        Value tuple = stack.pop();
                        Value val  = stack.pop();
                        tuple.set(((Parser.TupleElementAccess) next).index, val);
                    }
                }
            }catch(ConcatRuntimeError|RandomAccessStack.StackUnderflow  e){
                ioContext.stdErr.println(e.getMessage());
                Parser.Token token = tokens.get(ip);
                ioContext.stdErr.printf("  while executing %-20s\n   at %s\n",token,token.pos);
                return ExitType.ERROR;
            }catch(Throwable  t){//show expression in source code that crashed the interpreter
                Parser.Token token = tokens.get(ip);
                try {
                    ioContext.stdErr.printf("  while executing %-20s\n   at %s\n", token, token.pos);
                }catch (Throwable ignored){}//ignore exceptions while printing
                throw t;
            }
            if(incIp){
                ip++;
            }
        }
        return ExitType.NORMAL;
    }

    private ExitType call(Parser.Callable called, Parser.Token next, RandomAccessStack<Value> stack,
                          ArrayList<Value[]> globalVariables, ArrayList<Value[]> variables,
                          IOContext context)
            throws RandomAccessStack.StackUnderflow, ConcatRuntimeError {
        if(called instanceof Value.NativeProcedure nativeProc){
            int count=nativeProc.argCount();
            Value[] args=new Value[count];
            for(int i=count-1;i>=0;i--){
                args[i]= stack.pop();
            }
            args=nativeProc.callWith(args);
            for (Value arg : args) {
                stack.push(arg);
            }
        }else if(called instanceof Value.Procedure procedure){
            assert ((Value.Procedure) called).context.curried.isEmpty() || procedure.curriedArgs != null;
            ExitType e=recursiveRun(stack,procedure, globalVariables ==null? variables : globalVariables,
                    null,procedure.curriedArgs, context);
            if(e!=ExitType.NORMAL){
                if(e==ExitType.ERROR) {
                    context.stdErr.printf("   while executing %-20s\n   at %s\n", next, next.pos);
                }
                return e;
            }
        }else{
            throw new RuntimeException("unexpected callable type: "+ called.getClass());
        }//no else
        return ExitType.NORMAL;
    }

    static Parser.Program compileAndRun(String path, String[] arguments, IOContext context) throws IOException {
        Type.resetCached();
        Parser.Program program;
        try {
            program = Parser.parse(new File(path),null,context);
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
        Interpreter ip=new Interpreter();
        RandomAccessStack<Value> stack = ip.run(program, arguments, context);
        System.setIn(inTmp);
        System.setOut(outTmp);
        System.setErr(errTmp);
        context.stdOut.println("\nStack:");
        context.stdOut.println(stack);
        return program;
    }

    public static void main(String[] args) throws IOException {
        FilePosition.ID_MODE=false;
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
            Parser.libPath=args[2];
        }
        File libDir=new File(Parser.libPath);
        if(!(libDir.exists()||libDir.mkdirs())){
            System.out.println(Parser.libPath+"  is no valid library path");
            return;
        }
        String[] arguments=new String[args.length-consumed+1];
        arguments[0]=System.getProperty("user.dir");
        System.arraycopy(args,1,arguments,consumed,args.length-consumed);
        compileAndRun(path,arguments,defaultContext);
    }

}
