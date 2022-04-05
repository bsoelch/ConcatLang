package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class GenericStruct implements Parser.NamedDeclareable {
    final String name;
    final boolean isPublic;
    final Type.Struct parent;

    final Parser.StructContext context;

    final ArrayList<Parser.Token> tokens;

    final FilePosition declaredAt;
    final FilePosition endPos;

    public GenericStruct(String name, boolean isPublic, Type.Struct parent,
                         Parser.StructContext context, ArrayList<Parser.Token> tokens, FilePosition declaredAt, FilePosition endPos) {
        this.name = name;
        this.isPublic = isPublic;
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
        return Parser.DeclareableType.GENERIC_STRUCT;
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
    private final HashMap<TypeArray, Type.Struct> cached=new HashMap<>();
    public Type.Struct withPrams(Type[] genericArgs) throws SyntaxError {
        IdentityHashMap<Type.GenericParameter, Type> update=new IdentityHashMap<>();
        for(int i=0;i< genericArgs.length;i++){
            Type.GenericParameter t=context.generics.get(i);
            Type replace=genericArgs[i];
            update.put(t,replace);
        }
        Parser.StructContext newContext=context.replaceGenerics(update);
        Type.Struct struct=cached.get(new TypeArray(genericArgs));
        if(struct==null){
            ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens.size());
            for(Parser.Token t:tokens){
                newTokens.add(t.replaceGenerics(update));
            }
            struct = Type.Struct.create(name,isPublic,
                    parent==null?null:parent.replaceGenerics(update),
                    genericArgs,this,newTokens,newContext,declaredAt,endPos);
            cached.put(new TypeArray(genericArgs),struct);
        }
        return struct;
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
