package bsoelch.concat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class GenericProcedure implements Parser.Callable {
    final String name;
    final boolean isPublic;
    final Type.GenericProcedureType procType;
    final FilePosition declaredAt;

    final FilePosition endPos;
    final Parser.ProcedureContext context;

    final ArrayList<Parser.Token> tokens;

    public GenericProcedure(String name, boolean isPublic, Type.GenericProcedureType procType,
                            ArrayList<Parser.Token> tokens, FilePosition declaredAt,
                            FilePosition endPos, Parser.ProcedureContext context) {
        this.name = name;
        this.isPublic = isPublic;
        this.procType = procType;
        this.declaredAt = declaredAt;
        this.tokens = tokens;
        this.endPos = endPos;
        this.context=context;
    }

    @Override
    public Parser.DeclareableType declarableType() {
        return Parser.DeclareableType.GENERIC_PROCEDURE;
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
    public Type.GenericProcedureType type() {
        return procType;
    }

    @Override
    public HashMap<String, Parser.WithConstant> withConsts() {
        return context.withConsts;
    }

    private final HashMap<IdentityHashMap<Type.GenericParameter,Type>, Value.Procedure> cached=new HashMap<>();
    public Value.Procedure withPrams(IdentityHashMap<Type.GenericParameter, Type> genericParams) throws SyntaxError {
        Parser.ProcedureContext newContext=new Parser.ProcedureContext(context.parent);
        for(Type.GenericParameter t:context.generics){
            Type replace=genericParams.get(t);
            if(replace!=null){
                newContext.putElement(t.label,new Parser.Constant(t.name,false,Value.ofType(replace),t.declaredAt));
            }else{//TODO choose better value for unbound generics
                newContext.putElement(t.label,new Parser.Constant(t.name,false,Value.ofType(Type.ANY),t.declaredAt));
                genericParams.put(t,Type.ANY);//add to missing params
            }
        }
        Value.Procedure proc=cached.get(genericParams);
        if(proc==null){
            ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens);
            //TODO merge procedures with equivalent bodies
            proc=Value.createProcedure(name,isPublic,procType.replaceGenerics(genericParams),newTokens,declaredAt,endPos,newContext);
            cached.put(genericParams,proc);
        }
        return proc;
    }

    @Override
    public boolean unused() {
        for(Parser.Callable c:cached.values()){
            if(!c.unused())
                return false;
        }
        return true;
    }

    @Override
    public void markAsUsed() {
        //do nothing
    }
}
