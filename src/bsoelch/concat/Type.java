package bsoelch.concat;

import java.util.*;

public class Type {
    public static final Type INT   = new Type("int",  true) {
        @Override
        public boolean canCastTo(Type t,BoundMaps bounds) {
            return t==UINT||t==CODEPOINT||t==BYTE||t==FLOAT||super.canCastTo(t,bounds);
        }
    };
    public static final Type UINT  = new Type("uint", true) {
        @Override
        public boolean canCastTo(Type t, BoundMaps bounds) {
            return t==INT||t==CODEPOINT||t==BYTE||t==FLOAT||super.canCastTo(t,bounds);
        }
    };
    public static final Type FLOAT = new Type("float",false) {
        @Override
        public boolean canCastTo(Type t, BoundMaps bounds) {
            return t==INT||t==UINT||super.canCastTo(t,bounds);
        }
    };
    public static final Type CODEPOINT = new Type("codepoint", true) {
        @Override
        public boolean canCastTo(Type t, BoundMaps bounds) {
            return t==INT||t==UINT||t==BYTE||super.canCastTo(t,bounds);
        }
    };
    public static final Type BYTE  = new Type("byte", true){
        @Override
        public boolean canCastTo(Type t, BoundMaps bounds) {
            return t==INT||t==UINT||t==CODEPOINT||super.canCastTo(t,bounds);
        }
    };
    public static final Type TYPE  = new Type("type", false);
    public static final Type BOOL  = new Type("bool", false);

    /**blank type that could contain any value*/
    public static final Type ANY = new Type("var", false) {};

    public static Type UNICODE_STRING() {
        return WrapperType.UNICODE_STRING;
    }
    public static Type RAW_STRING() {
        return WrapperType.BYTES;
    }

    private Type(String name, boolean switchable) {
        this.name = name;
        this.switchable = switchable;
    }

    final String name;
    final boolean switchable;

    public static Type commonSuperType(Type a, Type b,boolean strict) {
        if(a==ANY||b==null){
            return a;
        }else if(a==null||b==ANY){
            return b;
        }else {
            if(a.canAssignTo(b)){
                return b;
            }else if(b.canAssignTo(a)){
                return a;
            }else if((!strict)&&((a==UINT||a==INT)&&(b==UINT||b==INT))){
                return a;
            }else if((!strict)&&((a==FLOAT&&(b==UINT||b==INT))||(b==FLOAT&&(a==UINT||a==INT)))){
                return FLOAT;
            }else{
                return ANY;
            }
        }
    }

    static class BoundMaps{
        final IdentityHashMap<GenericParameter, GenericBound> l;
        final IdentityHashMap<GenericParameter, GenericBound> r;
        private BoundMaps(IdentityHashMap<GenericParameter, GenericBound> l,IdentityHashMap<GenericParameter, GenericBound> r){
            this.l = l;
            this.r = r;
        }
        public BoundMaps(){
            this(new IdentityHashMap<>(),new IdentityHashMap<>());
        }
        BoundMaps swapped(){
            return new BoundMaps(r,l);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type)) return false;
        return equals((Type) o,new IdentityHashMap<>());
    }
    protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics){
        return super.equals(t);
    }

    record GenericBound(Type min, Type max) {}

    /**@return true if this type can be directly assigned to type t*/
    public final boolean canAssignTo(Type t){
        return canAssignTo(t,new BoundMaps());
    }

    protected boolean canAssignTo(Type t, BoundMaps bounds){
        if(t instanceof GenericParameter&&!((GenericParameter) t).isBound){
            GenericBound bound=bounds.r.get(t);
            if(bound!=null){
                if(bound.min==null||bound.min.canAssignTo(this,bounds.swapped())) {
                    bound=new GenericBound(this,bound.max);
                }else if(!canAssignTo(bound.min,bounds)){
                    return false;
                }
            }else{
                bound=new GenericBound(this,null);
            }
            bounds.r.put((GenericParameter) t,bound);
            return true;
        }else if(t instanceof UnionType){
            for(Type t1:((UnionType) t).elements){
                if(canAssignTo(t1,bounds)){
                    return true;
                }
            }
        }
        return this==t||t==ANY;
    }

    /**@return true if values of this type can be cast to type t*/
    public final boolean canCastTo(Type t){
        return canCastTo(t,new BoundMaps());
    }
    protected boolean canCastTo(Type t, BoundMaps bounds){
        return canAssignTo(t,bounds)||t.canAssignTo(this,bounds.swapped());
    }

    @Override
    public String toString() {
        return name;
    }
    Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
        return this;
    }

    public boolean isList() {
        return false;
    }
    public boolean isOptional() {
        return false;
    }
    public Type content() {//addLater make type-data getters return optional
        throw new UnsupportedOperationException();
    }
    public List<Type> inTypes(){
        throw new UnsupportedOperationException();
    }
    public List<Type> outTypes(){
        throw new UnsupportedOperationException();
    }
    public String typeName(){
        throw new UnsupportedOperationException();
    }
    public List<String> fields(){
        throw new UnsupportedOperationException();
    }


    public static Type listOf(Type contentType){
        return WrapperType.create(WrapperType.LIST,contentType);
    }

    public static Type optionalOf(Type contentType){
        return WrapperType.create(WrapperType.OPTIONAL,contentType);
    }

    private static class WrapperType extends Type {
        static final String LIST = "list";
        static final String OPTIONAL = "optional";

        static final WrapperType BYTES= new WrapperType(LIST,Type.BYTE);
        static final WrapperType UNICODE_STRING= new WrapperType(LIST,Type.CODEPOINT);

        final Type contentType;
        final String wrapperName;

        private static WrapperType create(String wrapperName, Type contentType) {
            if(LIST.equals(wrapperName)) {
                if (contentType == CODEPOINT) {
                    return UNICODE_STRING;
                } else if (contentType == BYTE) {
                    return BYTES;
                }
            }
            //addLater? caching
            return new WrapperType(wrapperName,contentType);
        }
        private WrapperType(String wrapperName, Type contentType){
            super(contentType.name+" "+ wrapperName, LIST.equals(wrapperName)&&
                    (contentType==BYTE||contentType==CODEPOINT));
            this.wrapperName = wrapperName;
            this.contentType = contentType;
        }

        @Override
        WrapperType replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type newContent = contentType.replaceGenerics(generics);
            return contentType==newContent?this:create(wrapperName, newContent);
        }

        @Override
        public boolean isList() {
            return wrapperName.equals(LIST);
        }
        @Override
        public boolean isOptional() {
            return wrapperName.equals(OPTIONAL);
        }

        //addLater don't allow casts between mutable lists of different type
        @Override
        public boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().canAssignTo(t.content(),bounds);
            }else{
                return super.canAssignTo(t,bounds);
            }
        }

        @Override
        public boolean canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().canCastTo(t.content(),bounds);
            }else{
                return super.canCastTo(t,bounds);
            }
        }

        @Override
        public Type content() {
            return contentType;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if (this == t) return true;
            if (!(t instanceof WrapperType that)) return false;
            return contentType.equals(that.contentType,generics) && Objects.equals(wrapperName, that.wrapperName);
        }
        @Override
        public int hashCode() {
            return Objects.hash(contentType, wrapperName);
        }
    }

    public static class Tuple extends Type implements Interpreter.NamedDeclareable{
        static final Tuple EMPTY_TUPLE=new Tuple(null,new Type[0],Value.InternalProcedure.POSITION);

        final FilePosition declaredAt;
        final Type[] elements;
        final boolean named;

        private static String getName(Type[] elements){
            StringBuilder sb=new StringBuilder("( ");
            for(Type t:elements){
                sb.append(t).append(" ");
            }
            return sb.append(")").toString();
        }
        public Tuple(String name, Type[] elements, FilePosition declaredAt){
            super(name==null?getName(elements):name, false);
            this.elements=elements;
            this.declaredAt = declaredAt;
            named=name!=null;
        }

        @Override
        public List<Type> outTypes() {
            return Arrays.asList(elements);
        }
        @Override
        public String typeName() {
            return name;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            return changed?new Tuple(named?name:null,newElements,declaredAt):this;
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof Tuple&&((Tuple) t).elementCount()<=elementCount()){
                for(int i=0;i<((Tuple) t).elements.length;i++){
                    if(!elements[i].canAssignTo(((Tuple) t).elements[i],bounds)){
                        return false;
                    }
                }
                return true;
            }else{
                return super.canAssignTo(t,bounds);
            }
        }
        @Override
        protected boolean canCastTo(Type t,  BoundMaps bounds) {
            if(t instanceof Tuple){
                int n=Math.min(elements.length,((Tuple) t).elements.length);
                for(int i=0;i<n;i++){
                    if(!elements[i].canCastTo(((Tuple) t).elements[i],bounds)){
                        return false;
                    }
                }
                return true;
            }else{
                return super.canCastTo(t,bounds);
            }
        }

        public int elementCount(){
            return elements.length;
        }
        public Type get(long i) throws ConcatRuntimeError {
            if(i<0||i>=elements.length){
                throw new ConcatRuntimeError("tuple index out of bounds: "+i+" length:"+elements.length);
            }
            return elements[(int) i];
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> genericsIds) {
            if (this == t) return true;
            if (!(t instanceof Tuple tuple)) return false;
            if(tuple.elements.length!=elements.length)
                return false;
            for(int i=0;i<elements.length;i++){
                if(!(elements[i].equals(tuple.elements[i],genericsIds)))
                    return false;
            }
            return true;
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }

        @Override
        public String name() {
            return name;
        }
        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.TUPLE;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
    }
    public static class GenericTuple extends Tuple{
        final Type[] genericArgs;
        final GenericParameter[] explicitParams;
        final GenericParameter[] implicitParams;
        final String tupleName;

        public static GenericTuple create(String name,GenericParameter[] genericParams,Type[] genericArgs,Type[] elements, FilePosition declaredAt) {
            ArrayList<GenericParameter> explicitParams=new ArrayList<>(genericParams.length);
            ArrayList<GenericParameter> implicitParams=new ArrayList<>(genericParams.length);
            IdentityHashMap<GenericParameter,Type> generics=new IdentityHashMap<>();
            for (GenericParameter genericParam : genericParams) {
                if (genericParam.isImplicit) {
                    implicitParams.add(genericParam);
                } else {
                    generics.put(genericParam,genericArgs[explicitParams.size()]);
                    explicitParams.add(genericParam);
                }
            }
            if(explicitParams.size()!=genericArgs.length){
                throw new RuntimeException("Number of generic arguments ("+genericArgs.length+
                        ") does not match number of generic parameters ("+explicitParams.size()+")");
            }
            //Unwrap generic arguments
            for(int i=0;i< elements.length;i++){
                elements[i]=elements[i].replaceGenerics(generics);
            }
            StringBuilder sb=new StringBuilder();
            for(Type t:genericArgs){
                sb.append(t.name).append(" ");
            }
            return new GenericTuple(name,sb.append(name).toString(),genericParams, genericArgs,
                    implicitParams.toArray(GenericParameter[]::new), elements, declaredAt);
        }
        private GenericTuple(String baseName, String name, GenericParameter[] genericParams, Type[] genericArgs, GenericParameter[] implicitParams, Type[] elements, FilePosition declaredAt) {
            super(name, elements, declaredAt);
            tupleName=baseName;
            this.explicitParams =genericParams;
            this.genericArgs=genericArgs;
            this.implicitParams = implicitParams;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(generics);
                if(newArgs[i]!=this.genericArgs[i]){
                    changed=true;
                }
            }
            return changed?create(tupleName, explicitParams,newArgs,newElements,declaredAt):this;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if((!(t instanceof GenericTuple))||((GenericTuple) t).explicitParams.length!= explicitParams.length)
                return false;
            for(int i = 0; i< genericArgs.length; i++){//compare generic parameters with their equivalents
                if(!genericArgs[i].equals(((GenericTuple) t).genericArgs[i],generics))
                    return false;
            }
            return super.equals(t, generics);
        }
        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericTuple){
                if(((GenericTuple) t).explicitParams.length!= explicitParams.length)
                    return false;
                for(int i = 0; i< genericArgs.length; i++){//compare generic parameters with their equivalents
                    if(!genericArgs[i].canAssignTo(((GenericTuple) t).genericArgs[i],bounds))
                        return false;
                }
            }
            return super.canAssignTo(t, bounds);
        }
        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericTuple) {
                if (((GenericTuple) t).explicitParams.length != explicitParams.length)
                    return false;
                for (int i = 0; i < genericArgs.length; i++) {//compare generic parameters with their equivalents
                    if (!genericArgs[i].canCastTo(((GenericTuple) t).genericArgs[i], bounds))
                        return false;
                }
            }
            return super.canCastTo(t, bounds);
        }
    }

    public static class Procedure extends Type{
        final Type[] inTypes;
        final Type[] outTypes;


        static StringBuilder getName(Type[] inTypes, Type[] outTypes) {
            StringBuilder name=new StringBuilder("( ");
            for(Type t: inTypes){
                name.append(t.name).append(' ');
            }
            name.append("=> ");
            for(Type t: outTypes){
                name.append(t.name).append(' ');
            }
            name.append(")");
            return name;
        }
        public static Procedure create(Type[] inTypes,Type[] outTypes){
            StringBuilder name = getName(inTypes, outTypes);
            return new Procedure(name.toString(),inTypes,outTypes);
        }

        private Procedure(String name,Type[] inTypes,Type[] outTypes) {
            super(name, false);
            this.inTypes=inTypes;
            this.outTypes=outTypes;
        }

        @Override
        public List<Type> inTypes() {
            return Arrays.asList(inTypes);
        }
        @Override
        public List<Type> outTypes() {
            return Arrays.asList(outTypes);
        }

        @Override
        Procedure replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type[] newIns=new Type[inTypes.length];
            boolean changed=false;
            for(int i=0;i<inTypes.length;i++){
                newIns[i]=inTypes[i].replaceGenerics(generics);
                if(newIns[i]!=inTypes[i]){
                    changed=true;
                }
            }
            Type[] newOuts=new Type[outTypes.length];
            for(int i=0;i<this.outTypes.length;i++){
                newOuts[i]=this.outTypes[i].replaceGenerics(generics);
                if(newOuts[i]!=this.outTypes[i]){
                    changed=true;
                }
            }
            return changed?Procedure.create(newIns,newOuts):this;
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof Procedure proc){
                if(proc.inTypes.length== inTypes.length&&proc.outTypes.length==outTypes.length){
                    for(int i=0;i< inTypes.length;i++){
                        if(!proc.inTypes[i].canAssignTo(inTypes[i],bounds.swapped())){
                            return false;
                        }
                    }
                    for(int i=0;i< outTypes.length;i++){
                        if(!outTypes[i].canAssignTo(proc.outTypes[i],bounds)){
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }
            return super.canAssignTo(t,bounds);
        }
        //addLater? overwrite canCastTo

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if (this == t) return true;
            if ( ! (t instanceof Procedure proc)) return false;
            if(proc.inTypes.length!=inTypes.length||((Procedure) t).outTypes.length!=outTypes.length)
                return false;
            for(int i=0;i<inTypes.length;i++){
                if(!inTypes[i].equals(proc.inTypes[i],generics))
                    return false;
            }
            for(int i=0;i<outTypes.length;i++){
                if(!outTypes[i].equals(proc.outTypes[i],generics))
                    return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(inTypes);
            result = 31 * result + Arrays.hashCode(outTypes);
            return result;
        }
    }

    public static class GenericProcedure extends Procedure {
        final GenericParameter[] explicitGenerics;
        final GenericParameter[] implicitGenerics;
        final Type[] genericArgs;

        private static String genericName(Type[] inTypes, Type[] outTypes, List<? extends Type> explicitParams) {
            StringBuilder sb=new StringBuilder();
            for(Type t: explicitParams){
                sb.append(t.name).append(" ");
            }
            return sb.append(getName(inTypes, outTypes)).toString();
        }
        public static GenericProcedure create(GenericParameter[] genericParams,Type[] inTypes,Type[] outTypes) {
            ArrayList<GenericParameter> explicitParams=new ArrayList<>(genericParams.length);
            ArrayList<GenericParameter> implicitParams=new ArrayList<>(genericParams.length);
            for (GenericParameter genericParam : genericParams) {
                if (genericParam.isImplicit) {
                    implicitParams.add(genericParam);
                } else {
                    explicitParams.add(genericParam);
                }
            }
            //Unwrap generic arguments
            String name = genericName(inTypes, outTypes, explicitParams);
            return new GenericProcedure(name,explicitParams.toArray(GenericParameter[]::new),
                    explicitParams.toArray(GenericParameter[]::new),implicitParams.toArray(GenericParameter[]::new),inTypes,outTypes);
        }

        private GenericProcedure(String name, GenericParameter[] explicitGenerics, Type[] genericArgs,
                                 GenericParameter[] implicitGenerics,Type[] inTypes, Type[] outTypes) {
            super(name, inTypes,outTypes);
            this.explicitGenerics = explicitGenerics;
            this.implicitGenerics = implicitGenerics;
            this.genericArgs=genericArgs;
        }

        @Override
        Procedure replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type[] newIn=new Type[inTypes.length];
            boolean changed=false;
            for(int i=0;i<inTypes.length;i++){
                newIn[i]=inTypes[i].replaceGenerics(generics);
                if(newIn[i]!=inTypes[i]){
                    changed=true;
                }
            }
            Type[] newOut=new Type[outTypes.length];
            for(int i=0;i<outTypes.length;i++){
                newOut[i]=outTypes[i].replaceGenerics(generics);
                if(newOut[i]!=outTypes[i]){
                    changed=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(generics);
                if(newArgs[i]!=this.genericArgs[i]){
                    changed=true;
                }
            }
            return changed?new GenericProcedure(genericName(newIn,newOut,Arrays.asList(newArgs)), explicitGenerics,
                    newArgs,implicitGenerics,newIn,newOut):this;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if((!(t instanceof GenericProcedure))||((GenericProcedure) t).explicitGenerics.length!= explicitGenerics.length)
                return false;
            for(int i = 0; i< genericArgs.length; i++){//map generic parameters to their equivalents
                if(!genericArgs[i].equals(((GenericProcedure) t).genericArgs[i],generics))
                    return false;
            }
            return super.equals(t, generics);
        }
        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericProcedure) {
                if (((GenericProcedure) t).explicitGenerics.length != explicitGenerics.length)
                    return false;
                for (int i = 0; i < genericArgs.length; i++) {//map generic parameters to their equivalents
                    if (!genericArgs[i].canAssignTo(((GenericProcedure) t).genericArgs[i], bounds))
                        return false;
                }
            }
            return super.canAssignTo(t, bounds);
        }
        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericProcedure){
                if(((GenericProcedure) t).explicitGenerics.length!= explicitGenerics.length)
                    return false;
                for(int i = 0; i< genericArgs.length; i++){//map generic parameters to their equivalents
                    if(!genericArgs[i].canAssignTo(((GenericProcedure) t).genericArgs[i],bounds))
                        return false;
                }
            }
            return super.canCastTo(t, bounds);
        }
    }



    public static class Enum extends Type implements Interpreter.NamedDeclareable {
        final FilePosition declaredAt;
        final String[] entryNames;
        final Value.EnumEntry[] entries;
        public Enum(String name, String[] entryNames, ArrayList<FilePosition> entryPositions,
                    FilePosition declaredAt) {
            super(name, true);
            this.entryNames =entryNames;
            entries=new Value.EnumEntry[entryNames.length];
            for(int i=0;i<entryNames.length;i++){
                entries[i]=new Value.EnumEntry(this,i,entryPositions.get(i));
            }
            this.declaredAt = declaredAt;
        }
        public int elementCount(){
            return entryNames.length;
        }
        public String get(int i) {
            return entryNames[i];
        }

        @Override
        public boolean canCastTo(Type t, BoundMaps bounds) {
            return t==UINT||t==INT||super.canCastTo(t, bounds);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String typeName() {
            return name;
        }
        @Override
        public List<String> fields() {
            return Arrays.asList(entryNames);
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.ENUM;
        }
        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
    }

    public static class NativeType extends Type{
        public final Class<?> jClass;
        NativeType(String name, Class<?> jClass) {
            super(name, false);
            this.jClass = jClass;
        }
        @Override
        public String typeName() {
            return name;
        }
    }

    public static class GenericParameter extends Type implements Interpreter.Declareable {
        final int id;
        final boolean isImplicit;
        final FilePosition declaredAt;
        private boolean isBound =true;
        public GenericParameter(int id, boolean isImplicit, FilePosition pos) {
            super("'"+id,false);
            this.id=id;
            this.isImplicit = isImplicit;
            this.declaredAt=pos;
        }
        void bind(){
            isBound = true;
        }
        void unbind(){
            isBound = false;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type r=generics.get(this);
            return r!=null?r:this;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if(this==t)
                return true;
            if(isBound)
                return false;
            if(t instanceof GenericParameter){
                GenericParameter t1=generics.get(t);
                GenericParameter this1=generics.get(this);
                if(t1==null&&this1==null){
                    generics.put((GenericParameter)t,this);
                    generics.put(this,(GenericParameter)t);
                    return true;
                }
                return this==t1||this1==t;
            }
            return false;
        }
        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(this==t)
                return true;
            if(isBound ||t instanceof GenericParameter)//TODO handling of bounds if both sides are unbound generics
                return super.canAssignTo(t,bounds);
            GenericBound bound=bounds.l.get(this);
            if(bound!=null){
                if(bound.max==null||t.canAssignTo(bound.max,bounds)) {
                    bound=new GenericBound(bound.min,t);
                }else if(!bound.max.canAssignTo(t,bounds.swapped())){
                    return false;
                }
            }else{
                bound=new GenericBound(null,t);
            }
            bounds.l.put(this,bound);
            return true;
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.GENERIC;
        }

        @Override
        public FilePosition declaredAt() {
            return declaredAt;
        }
    }

    static IdentityHashMap<GenericParameter, Type> mergeArgs(IdentityHashMap<GenericParameter, Type> genArgs,
                                                             IdentityHashMap<GenericParameter, Type> update) {
        IdentityHashMap<GenericParameter,Type> newArgs=new IdentityHashMap<>();
        IdentityHashMap<GenericParameter,Type> remaining=new IdentityHashMap<>(update);
        for(Map.Entry<GenericParameter, Type> g: genArgs.entrySet()){
            if(g.getValue() instanceof GenericParameter){
                Type t= remaining.remove(g.getValue());
                if(t!=null){
                    newArgs.put(g.getKey(),t);
                }else{
                    newArgs.put(g.getKey(),g.getValue());
                }
            }else{
                newArgs.put(g.getKey(),g.getValue());
            }
        }
        for(Map.Entry<GenericParameter, Type> g: remaining.entrySet()){
            if(newArgs.put(g.getKey(),g.getValue())!=null){
                throw new RuntimeException("unexpected replacement of generic value");
            }
        }
        return newArgs;
    }

    static class UnionType extends Type{
        final Type[] elements;
        static Type create(Type[] elements){
            if(elements.length<=0){
                throw new RuntimeException("unions cannot be empty");
            }
            StringBuilder name=new StringBuilder("union( ");
            ArrayList<Type> types=new ArrayList<>(elements.length);
            for(Type t:elements){//TODO merge types with their supertypes
                if(t instanceof UnionType) {
                    for(Type t1:((UnionType) t).elements){
                        types.add(t1);
                        name.append(t1).append(" ");
                    }
                }else{
                    types.add(t);
                    name.append(t).append(" ");
                }
            }
            if(types.size()==1){
                return types.get(0);
            }
            return new UnionType(name.append(")").toString(),types.toArray(Type[]::new));
        }
        private UnionType(String name, Type[] elements) {
            super(name, false);
            this.elements = elements;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            if(!(t instanceof UnionType)||((UnionType) t).elements.length!=elements.length){
                return false;
            }
            for(int i=0;i<elements.length;i++){
                if(!elements[i].equals(((UnionType) t).elements[i],generics))
                    return false;
            }
            return true;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter, Type> generics) {
            boolean changed=false;
            Type[] newElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(elements[i]!=newElements[i]){
                    changed=true;
                }
            }
            return changed?create(newElements):this;
        }

        @Override
        public List<Type> inTypes() {
            return Arrays.asList(elements);
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericParameter){
                return super.canAssignTo(t, bounds);
            }
            for(Type e:elements){
                if(!e.canAssignTo(t,bounds))
                    return false;
            }
            return true;
        }

        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericParameter){
                return super.canCastTo(t, bounds);
            }
            for(Type e:elements){
                if(e.canCastTo(t,bounds))
                    return true;
            }
            return false;
        }
    }
}