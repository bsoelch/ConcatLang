package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

public class OverloadedProcedure implements Interpreter.Declareable {
    final String name;
    final boolean isPublic;
    final FilePosition declaredAt;
    final ArrayList<Interpreter.Callable> procedures;

    final int nArgs;
    final int nGenericParams;

    public OverloadedProcedure(Interpreter.Callable p0) {
        this.name = p0.name();
        this.isPublic = p0.isPublic();
        this.declaredAt = p0.declaredAt();
        procedures=new ArrayList<>();
        procedures.add(p0);
        nArgs= p0.type().inTypes.length;
        nGenericParams=p0.type() instanceof Type.GenericProcedureType ?((Type.GenericProcedureType)p0.type()).explicitGenerics.length:0;
    }

    public OverloadedProcedure(OverloadedProcedure src) {
        this.name = src.name;
        this.isPublic = src.isPublic;
        this.declaredAt = src.declaredAt;
        procedures=new ArrayList<>(src.procedures);
        this.nArgs=src.nArgs;
        this.nGenericParams=src.nGenericParams;
    }

    public boolean addProcedure(Interpreter.Callable newProc,boolean allowDuplicates) throws SyntaxError{
        if(!allowDuplicates && isPublic!= newProc.isPublic()){
            throw new RuntimeException("tried to merge a public procedure with a nonpublic procedure");
        }
        Type.Procedure t1=newProc.type();
        if(nArgs!=t1.inTypes.length){//check number of arguments
            if(allowDuplicates){
                return false;
            }else {
                throw new SyntaxError("all procedures of the same name must have the same number of arguments got:" +
                        t1.inTypes.length + " expected:" + nArgs, newProc.declaredAt());
            }
        }
        {//check number of generic arguments
            int gen1 = 0;
            if (newProc.type() instanceof Type.GenericProcedureType genP) {
                gen1 = genP.explicitGenerics.length;
            }//no else
            if (nGenericParams != gen1) {
                if(allowDuplicates){
                    return false;
                }else{
                    throw new SyntaxError("all procedures of the same name must have the same number of generic arguments" +
                            " got:" +gen1 + " expected:" + nGenericParams, newProc.declaredAt());
                }
            }
        }
        for(Interpreter.Callable p:procedures){//check for procedures with the same signature
            Type.Procedure t0=p.type();
            boolean isEqual=true;
            IdentityHashMap<Type.GenericParameter, Type.GenericParameter> generics=new IdentityHashMap<>();
            for(int i=0;i<t0.inTypes.length;i++){
                if(!t0.inTypes[i].equals(t1.inTypes[i],generics)){
                    isEqual=false;
                    break;
                }
            }
            if(isEqual){
                if(allowDuplicates){
                    return false;
                }else {
                    throw new SyntaxError("procedure " + name + " already has the signature " + Arrays.toString(t1.inTypes) +
                            " at " + p.declaredAt(), newProc.declaredAt());
                }
            }
        }
        procedures.add(newProc);
        return true;
    }

    @Override
    public Interpreter.DeclareableType declarableType() {
        return Interpreter.DeclareableType.OVERLOADED_PROCEDURE;
    }

    @Override
    public FilePosition declaredAt() {
        return declaredAt;
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }
}
