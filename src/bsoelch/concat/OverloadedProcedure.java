package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

public class OverloadedProcedure implements Interpreter.Declareable {
    final String name;
    final FilePosition declaredAt;
    final ArrayList<Value.Procedure> procedures;

    final int nArgs;
    final int nGenericParams;

    //TODO overloading of native procedures
    public OverloadedProcedure(String name, Value.Procedure p0) {
        this.name = name;
        this.declaredAt = p0.declaredAt;
        procedures=new ArrayList<>();
        procedures.add(p0);
        nArgs=((Type.Procedure)p0.type).inTypes.length;
        nGenericParams=p0.type instanceof Type.GenericProcedure?((Type.GenericProcedure)p0.type).explicitGenerics.length:0;

    }
    public OverloadedProcedure(OverloadedProcedure src) {
        this.name = src.name;
        this.declaredAt = src.declaredAt;
        procedures=new ArrayList<>(src.procedures);
        this.nArgs=src.nArgs;
        this.nGenericParams=src.nGenericParams;
    }


    public void addProcedure(Value.Procedure newProc) throws SyntaxError{
        Type.Procedure t1=((Type.Procedure)newProc.type);
        if(nArgs!=t1.inTypes.length){//check number of arguments
            throw new SyntaxError("overloaded procedures all must have the same number of arguments got:"+
                    t1.inTypes.length+" expected:"+nArgs,newProc.declaredAt);
        }
        {//check number of generic arguments
            int gen1 = 0;
            if (newProc.type instanceof Type.GenericProcedure genP) {
                gen1 = genP.explicitGenerics.length;
            }//no else
            if (nGenericParams != gen1) {
                throw new SyntaxError("overloaded procedures all must have the same number of generic arguments got:" +
                        gen1 + " expected:" + nGenericParams, newProc.declaredAt);
            }
        }
        for(Value.Procedure p:procedures){//check for procedures with the same signature
            Type.Procedure t0=((Type.Procedure)p.type);
            boolean isEqual=true;
            IdentityHashMap<Type.GenericParameter, Type.GenericParameter> generics=new IdentityHashMap<>();
            for(int i=0;i<t0.inTypes.length;i++){
                if(!t0.inTypes[i].equals(t1.inTypes[i],generics)){
                    isEqual=false;
                    break;
                }
            }
            if(isEqual){
                throw new SyntaxError("procedure "+name+" already has the signature "+ Arrays.toString(t1.inTypes)+
                        " at "+p.declaredAt,newProc.declaredAt);
            }
        }
        procedures.add(newProc);
    }

    @Override
    public Interpreter.DeclareableType declarableType() {
        return Interpreter.DeclareableType.OVERLOADED_PROCEDURE;
    }

    @Override
    public FilePosition declaredAt() {
        return declaredAt;
    }
}
