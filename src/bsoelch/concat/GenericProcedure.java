package bsoelch.concat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class GenericProcedure implements Interpreter.Callable {
    final String name;
    final boolean isPublic;
    final Type.Procedure procType;
    final FilePosition declaredAt;

    final FilePosition endPos;
    final Interpreter.ProcedureContext context;

    final ArrayList<Interpreter.Token> tokens;

    public GenericProcedure(String name, boolean isPublic, Type.Procedure procType,
                            ArrayList<Interpreter.Token> tokens, FilePosition declaredAt,
                            FilePosition endPos, Interpreter.ProcedureContext context) {
        this.name = name;
        this.isPublic = isPublic;
        this.procType = procType;
        this.declaredAt = declaredAt;
        this.tokens = tokens;
        this.endPos = endPos;
        this.context=context;
    }


    @Override
    public Interpreter.DeclareableType declarableType() {
        return Interpreter.DeclareableType.GENERIC_PROCEDURE;
    }

    @Override
    public FilePosition declaredAt() {
        return declaredAt;
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }

    @Override
    public String name() {
        return name;
    }
    @Override
    public Type.Procedure type() {
        return procType;
    }

    private final HashMap<IdentityHashMap<Type.GenericParameter,Type>, Value.Procedure> cached=new HashMap<>();
    public Value.Procedure withPrams(IdentityHashMap<Type.GenericParameter, Type> genericParams) {
        Value.Procedure proc=cached.get(genericParams);
        if(proc==null){
            ArrayList<Interpreter.Token> newTokens=new ArrayList<>(tokens.size());
            for(Interpreter.Token t:tokens){
                newTokens.add(t.replaceGenerics(genericParams));
            }
            Interpreter.ProcedureContext newContext=new Interpreter.ProcedureContext(context.parent);
            for(Type.GenericParameter t:context.generics){
                Type replace=genericParams.get(t);
                if(replace!=null){
                    newContext.putElement(t.label,new Interpreter.Constant(t.name,false,Value.ofType(replace),t.declaredAt));
                }else{//TODO choose better value for runtime generics
                    newContext.putElement(t.label,new Interpreter.Constant(t.name,false,Value.ofType(Type.ANY),t.declaredAt));
                }
            }
            //TODO merge procedures with equivalent bodies
            proc=Value.createProcedure(name,isPublic,procType.replaceGenerics(genericParams),newTokens,declaredAt,endPos,newContext);
            cached.put(genericParams,proc);
        }
        return proc;
    }
}