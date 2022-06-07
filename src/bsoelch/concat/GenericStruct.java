package bsoelch.concat;

import java.util.*;

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

    private static boolean check(Parser.Callable compare,Type.Procedure target){
        Type.Procedure srcType=compare.type();
        if(srcType.inTypes.length!=target.inTypes.length||srcType.outTypes.length!=target.outTypes.length)
            return false;
        if(srcType instanceof Type.GenericProcedureType &&((Type.GenericProcedureType) srcType).explicitGenerics.length>0)
            return false;
        for(int i=0;i<srcType.inTypes.length;i++)
            if(!srcType.inTypes[i].equals(target.inTypes[i]))
                return false;//input-signature has to match perfectly to ensure procedure will not be overwritten
        for(int i=0;i<srcType.outTypes.length;i++)
            if(!srcType.outTypes[i].canAssignTo(target.outTypes[i]))
                return false;//out-types have to be assignable
        return true;
    }
    public Type.Struct withPrams(Type[] genericArgs, FilePosition pos) throws SyntaxError {
        IdentityHashMap<Type.GenericParameter, Type> update=new IdentityHashMap<>();
        for(int i=0;i< genericArgs.length;i++){
            Type.GenericParameter t=context.generics.get(i);
            Type replace=genericArgs[i];
            update.put(t,replace);
        }
        for(Map.Entry<String, Parser.WithConstant> e:context.withConsts.entrySet()){//check with-constants
            if(e.getValue().constType() instanceof Type.Procedure){
                Type.Procedure requiredProc=((Type.Procedure)e.getValue().constType() ).replaceGenerics(update);
                Parser.Declareable candidate=context.getDeclareable(e.getKey());
                if(candidate instanceof Parser.Callable){
                    if(!check((Parser.Callable) candidate, requiredProc)){
                        throw new SyntaxError("procedure "+e.getKey()+" (declared at "+candidate.declaredAt()+") does not match "+
                                "the required signature "+requiredProc+" (at "+e.getValue().declaredAt()+")",pos);
                    }
                }else if(candidate instanceof OverloadedProcedure){
                    ArrayList<Parser.Callable> matches=new ArrayList<>();
                    for(Parser.Callable c:((OverloadedProcedure) candidate).procedures){
                        if(check(c,requiredProc))
                            matches.add(c);
                    }
                    if(matches.isEmpty()){
                        System.err.println("candidates:");
                        for(Parser.Callable c:((OverloadedProcedure) candidate).procedures){
                            System.err.println(" - "+c.type()+" at "+c.declaredAt());
                        }
                        throw new SyntaxError("no version of "+e.getKey()+" matches the required signature "+requiredProc
                                +" (at "+e.getValue().declaredAt()+")",pos);
                    }
                    if(matches.size()>1){//the current version of check only allows one possible match (identical input signature)
                        throw new RuntimeException("unreachable");
                    }
                }else{
                    throw new SyntaxError("unable to find procedure "+e.getKey()+" (at "+e.getValue().declaredAt()+")",pos);
                }
            }else{
                throw new UnsupportedOperationException("currently only procedure types are supported in with-constants");
            }
        }
        Parser.StructContext newContext=context.replaceGenerics(update);
        Type.Struct struct=cached.get(new TypeArray(genericArgs));
        if(struct==null){
            ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens);
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
