package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class GenericTuple implements Parser.NamedDeclareable {
    final String name;
    final boolean isPublic;
    final boolean isStruct;
    final Type.Struct parent;

    final Parser.GenericContext context;

    final ArrayList<Parser.Token> tokens;

    final FilePosition declaredAt;
    final FilePosition endPos;

    public GenericTuple(String name, boolean isPublic,boolean isStruct, Type.Struct parent,
                        Parser.GenericContext context, ArrayList<Parser.Token> tokens, FilePosition declaredAt, FilePosition endPos) {
        this.name = name;
        this.isPublic = isPublic;
        this.isStruct = isStruct;
        this.parent = parent;
        this.context = context;
        this.tokens = tokens;
        this.declaredAt = declaredAt;
        this.endPos = endPos;
    }

    int argCount(){
        return context.generics.size();
    }

    @Override
    public Parser.DeclareableType declarableType() {
        return Parser.DeclareableType.GENERIC_TUPLE;
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

    /**wrapper for Type[] with correct hashCode computation*/
    record TypeArray(Type[] types){
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeArray typeArray = (TypeArray) o;
            return Arrays.equals(types, typeArray.types);
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(types);
        }
    }
    private final HashMap<TypeArray, Type.Tuple> cached=new HashMap<>();
    public Type.Tuple withPrams(Type[] genericArgs) {
        IdentityHashMap<Type.GenericParameter, Type> update=new IdentityHashMap<>();
        Parser.GenericContext newContext=context.newInstance(false);
        for(int i=0;i< genericArgs.length;i++){
            Type.GenericParameter t=context.generics.get(i);
            Type replace=genericArgs[i];
            update.put(t,replace);
            if(replace instanceof Type.GenericParameter){
                newContext.putElement(t.label,(Type.GenericParameter) replace);
            }else{
                newContext.putElement(t.label,new Parser.Constant(t.name,false,Value.ofType(replace),t.declaredAt));
            }
        }
        Type.Tuple tuple=cached.get(new TypeArray(genericArgs));
        if(tuple==null){
            ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens.size());
            for(Parser.Token t:tokens){
                newTokens.add(t.replaceGenerics(update));
            }
            if(isStruct){
                tuple = Type.Struct.create(name,isPublic,
                        parent==null?null:parent.replaceGenerics(update),
                        genericArgs,newTokens,newContext,declaredAt,endPos);
            }else{
                tuple = Type.Tuple.create(name,isPublic,genericArgs,newTokens,newContext,declaredAt,endPos);
            }
            cached.put(new TypeArray(genericArgs),tuple);
        }
        return tuple;
    }

    @Override
    public boolean unused() {
        return cached.isEmpty();
    }

    @Override
    public void markAsUsed() {
        //do nothing
    }
}
