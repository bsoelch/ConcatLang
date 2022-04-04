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

    final ArrayList<Type.GenericTraitImplementation> genericTraits=new ArrayList<>();

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
        Parser.StructContext newContext=context.emptyCopy();
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
            for(Type.GenericTraitImplementation trait:genericTraits){
                update.clear();
                for(int i=0;i<trait.params().length;i++){
                    update.put(trait.params()[i],struct.genericArgs[i]);
                }
                Parser.Callable[] updated = new Parser.Callable[trait.values().length];
                for (int i = 0; i < trait.values().length; i++) {
                    updated[i] = (Parser.Callable) ((Value)trait.values()[i]).replaceGenerics(update);
                }
                struct.implementTrait(trait.trait(), updated, trait.implementedAt());
            }
        }
        return struct;
    }
    public void addGenericTrait(Type.Trait trait, Type.GenericParameter[] params, Parser.Callable[] implementation,
                                FilePosition implementedAt) throws SyntaxError {
        if(params.length!=argCount()){
            throw new IllegalArgumentException("wrong number of generic parameters: "+params.length+
                    " generic traits of "+name+" have to have exactly "+params.length+
                    " generic parameter");
        }
        IdentityHashMap<Type.GenericParameter, Type> update=new IdentityHashMap<>();
        for(Type.Struct s:cached.values()){
            update.clear();
            for(int i=0;i<params.length;i++){
                update.put(params[i],s.genericArgs[i]);
            }
            Parser.Callable[] updated = new Parser.Callable[implementation.length];
            for (int i = 0; i < implementation.length; i++) {
                updated[i] = (Parser.Callable) ((Value)implementation[i]).replaceGenerics(update);
            }
            s.implementTrait(trait, updated, implementedAt);
        }
        genericTraits.add(new Type.GenericTraitImplementation(trait,params,implementation,implementedAt));
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
