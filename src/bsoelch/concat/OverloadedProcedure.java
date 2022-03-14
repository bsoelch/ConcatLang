package bsoelch.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

public class OverloadedProcedure implements Interpreter.Declareable {
    final String name;
    final FilePosition declaredAt;
    final ArrayList<Value.Procedure> procedures;

    //TODO overloading of native procedures
    public OverloadedProcedure(String name, Value.Procedure p0) {
        this.name = name;
        this.declaredAt = p0.declaredAt;
        procedures=new ArrayList<>();
        procedures.add(p0);
    }
    public OverloadedProcedure(OverloadedProcedure src) {
        this.name = src.name;
        this.declaredAt = src.declaredAt;
        procedures=new ArrayList<>(src.procedures);
    }


    public void addProcedure(Value.Procedure newProc) throws SyntaxError{
        for(Value.Procedure p:procedures){
            Type.Procedure t0=((Type.Procedure)p.type);
            Type.Procedure t1=((Type.Procedure)newProc.type);
            if(t0.inTypes.length!=t1.inTypes.length){
                throw new SyntaxError("overloaded procedures all must have the same number of arguments got:"+
                        t1.inTypes.length+" expected:"+t0.inTypes.length,newProc.declaredAt);
            }
            boolean isEqual=true;
            IdentityHashMap<Type.GenericParameter, Type.GenericParameter> generics=new IdentityHashMap<>();
            for(int i=0;i<t0.inTypes.length;i++){
                if(!t0.inTypes[i].equals(t1.inTypes[i],generics)){
                    isEqual=false;
                    break;
                }
            }
            if(isEqual){
                throw new SyntaxError("procedure "+name+" already has the signature "+ Arrays.toString(t1.inTypes)+" at "+p.declaredAt,
                        newProc.declaredAt);
            }
            {//check number of generic arguments
                int gen0 = 0, gen1 = 0;
                if (p.type instanceof Type.GenericProcedure genP) {
                    gen0 = ((Type.GenericProcedure) p.type).explicitGenerics.length;
                }//no else
                if (newProc.type instanceof Type.GenericProcedure genP) {
                    gen1 = ((Type.GenericProcedure) newProc.type).explicitGenerics.length;
                }//no else
                if (gen0 != gen1) {
                    throw new SyntaxError("overloaded procedures all must have the same number of generic arguments got:" +
                            gen1 + " expected:" + gen0, newProc.declaredAt);
                }
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
