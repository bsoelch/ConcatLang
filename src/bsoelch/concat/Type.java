package bsoelch.concat;

import java.util.*;

public class Type {
    public static final Type INT   = new Type("int",  true) {
        @Override
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            return t==UINT||t==CODEPOINT||t==BYTE||t==FLOAT||super.canCastTo(t,genericIds,implGenerics);
        }
    };
    public static final Type UINT  = new Type("uint", true) {
        @Override
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            return t==INT||t==CODEPOINT||t==BYTE||t==FLOAT||super.canCastTo(t,genericIds,implGenerics);
        }
    };
    public static final Type FLOAT = new Type("float",false) {
        @Override
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            return t==INT||t==UINT||super.canCastTo(t,genericIds,implGenerics);
        }
    };
    public static final Type CODEPOINT = new Type("codepoint", true) {
        @Override
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            return t==INT||t==UINT||t==BYTE||super.canCastTo(t,genericIds,implGenerics);
        }
    };
    public static final Type BYTE  = new Type("byte", true){
        @Override
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            return t==INT||t==UINT||t==CODEPOINT||super.canCastTo(t,genericIds,implGenerics);
        }
    };
    public static final Type TYPE  = new Type("type", false);
    public static final Type BOOL  = new Type("bool", false);

    //addLater remove untyped ... types
    public static final Type UNTYPED_LIST      = new Type("(list)", false) {
        @Override
        public boolean isList() {
            return true;
        }
        @Override
        public Type content() {
            return ANY;
        }
    };
    public static final Type UNTYPED_OPTIONAL = new Type("(optional)", false) {
        @Override
        public boolean isOptional() {
            return true;
        }
        @Override
        public Type content() {
            return ANY;
        }
    };
    public static final Type UNTYPED_PROCEDURE = new Type("*->*", false);

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

    public static Type commonSuperType(Type a, Type b) {
        if(a==ANY||b==null){
            return a;
        }else if(a==null||b==ANY){
            return b;
        }else {
            if(a.isSubtype(b)){
                return b;
            }else if(b.isSubtype(a)){
                return a;
            }else if((a==UINT||a==INT)&&(b==UINT||b==INT)){
                return a;
            }else if((a==FLOAT&&(b==UINT||b==INT))||(b==FLOAT&&(a==UINT||a==INT))){
                return FLOAT;
            }else{
                return ANY;
            }
        }
    }

    record GenericId(int value,boolean implicit){}
    static class GenericIds{
        final IdentityHashMap<GenericParameter, GenericId> l  = new IdentityHashMap<>();
        final IdentityHashMap<GenericParameter, GenericId> r  = new IdentityHashMap<>();
        int expL=0,expR,implL,implR;
    }
    static class BoundMaps{
        final HashMap<GenericId, GenericBound> l;
        final HashMap<GenericId, GenericBound> r;
        private BoundMaps(HashMap<GenericId, GenericBound> l,HashMap<GenericId, GenericBound> r){
            this.l = l;
            this.r = r;
        }
        public BoundMaps(){
            this(new HashMap<>(),new HashMap<>());
        }
        BoundMaps swapped(){
            return new BoundMaps(r,l);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type)) return false;
        return equals((Type) o,new GenericIds());
    }
    protected boolean equals(Type t, GenericIds genericIds){
        return super.equals(t);
    }

    record GenericBound(Type min,Type max){}

    /**@return true if this type is a subtype of type t (values of this type can be directly assigned to type t)*/
    public final boolean isSubtype(Type t){
        return isSubtype(t,new GenericIds(),new BoundMaps());
    }
    public final boolean isSubtype(Type t,HashMap<GenericId,Type> implicitGenerics){
        BoundMaps bounds = new BoundMaps();
        boolean ret=isSubtype(t,new GenericIds(), bounds);
        if(ret){
            if(bounds.l.size()>0){
                //TODO throw ConcatRuntimeError or similar exception
                throw new UnsupportedOperationException("unexpected l-bounds");
            }
            for(Map.Entry<GenericId, GenericBound> e:bounds.r.entrySet()){
                if(e.getValue().min!=null){
                    if(e.getValue().max==null||e.getValue().min.isSubtype(e.getValue().max)){
                        implicitGenerics.put(e.getKey(),e.getValue().min);
                    }else{
                        return false;
                    }
                }else if(e.getValue().max!=null){
                    implicitGenerics.put(e.getKey(),e.getValue().max);
                }
            }
        }
        return ret;
    }

    protected boolean isSubtype(Type t, GenericIds genericIds, BoundMaps implGenerics){
        if(t instanceof GenericParameter&&((GenericParameter) t).isImplicit){
            GenericId boundId=genericIds.r.get(t);
            if(boundId==null){
                boundId=new GenericId(genericIds.implR++, true);
                genericIds.r.put((GenericParameter)t,boundId);
            }
            GenericBound bound=implGenerics.r.get(boundId);
            if(bound!=null){
                if(bound.min!=null&&!isSubtype(bound.min,genericIds,implGenerics)) {
                    return false;
                }
                bound=new GenericBound(this,bound.max);
            }else{
                bound=new GenericBound(this,null);
            }
            implGenerics.r.put(boundId,bound);
            return true;
        }
        return this==t||t==ANY;
    }

    /**@return true if values of this type can be cast to type t*/
    public final boolean canCastTo(Type t){
        return canCastTo(t,new GenericIds(),new BoundMaps());
    }
    protected boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics){
        return isSubtype(t,genericIds,implGenerics)||t.isSubtype(this,genericIds,implGenerics);
    }

    @Override
    public String toString() {
        return name;
    }
    Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
        return this;
    }
    final Type replaceGenerics(HashMap<GenericId,Type> generics) {
        return replaceGenerics(generics,new GenericIds());
    }
    Type replaceGenerics(HashMap<GenericId,Type> generics,GenericIds ids) {
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


    public static Type listOf(Type contentType) throws ConcatRuntimeError {
        return WrapperType.create(WrapperType.LIST,contentType);
    }

    public static Type optionalOf(Type contentType) throws ConcatRuntimeError {
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
        WrapperType replaceGenerics(HashMap<GenericId,Type> generics,GenericIds ids) {
            Type newContent = contentType.replaceGenerics(generics,ids);
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
        public boolean isSubtype(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().isSubtype(t.content(),genericIds,implGenerics);
            }else{
                return (wrapperName.equals(LIST)&&t==UNTYPED_LIST)||(wrapperName.equals(OPTIONAL)&&t== UNTYPED_OPTIONAL)||
                        super.isSubtype(t,genericIds,implGenerics);
            }
        }

        @Override
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                return content().canCastTo(t.content(),genericIds,implGenerics);
            }else{
                return (wrapperName.equals(LIST)&&t==UNTYPED_LIST)||(wrapperName.equals(OPTIONAL)&&t== UNTYPED_OPTIONAL)||
                        super.canCastTo(t,genericIds,implGenerics);
            }
        }

        @Override
        public Type content() {
            return contentType;
        }

        @Override
        protected boolean equals(Type t, GenericIds genericIds) {
            if (this == t) return true;
            if (!(t instanceof WrapperType that)) return false;
            return contentType.equals(that.contentType,genericIds) && Objects.equals(wrapperName, that.wrapperName);
        }
        @Override
        public int hashCode() {
            return Objects.hash(contentType, wrapperName);
        }
    }

    public static class Tuple extends Type implements Interpreter.NamedDeclareable{
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
        Type replaceGenerics(HashMap<GenericId,Type> generics,GenericIds ids) {
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics,ids);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            return changed?new Tuple(named?name:null,newElements,declaredAt):this;
        }

        @Override
        protected boolean isSubtype(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if(t instanceof Tuple&&((Tuple) t).elementCount()<=elementCount()){
                for(int i=0;i<((Tuple) t).elements.length;i++){
                    if(!elements[i].isSubtype(((Tuple) t).elements[i],genericIds,implGenerics)){
                        return false;
                    }
                }
                return true;
            }else{
                return super.isSubtype(t,genericIds,implGenerics);
            }
        }
        @Override
        protected boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if(t instanceof Tuple){
                int n=Math.min(elements.length,((Tuple) t).elements.length);
                for(int i=0;i<n;i++){
                    if(!elements[i].canCastTo(((Tuple) t).elements[i],genericIds,implGenerics)){
                        return false;
                    }
                }
                return true;
            }else{
                return super.canCastTo(t,genericIds,implGenerics);
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
        protected boolean equals(Type t, GenericIds genericsIds) {
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
        Type replaceGenerics(HashMap<GenericId,Type> generics,GenericIds ids) {
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics,ids);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(generics,ids);
                if(newArgs[i]!=this.genericArgs[i]){
                    changed=true;
                }
            }
            return changed?create(tupleName, explicitParams,newArgs,newElements,declaredAt):this;
        }

        @Override
        protected boolean equals(Type t, GenericIds genericIds) {
            if((!(t instanceof GenericTuple))||((GenericTuple) t).explicitParams.length!= explicitParams.length)
                return false;
            for(int i = 0; i< explicitParams.length; i++){//map generic parameters to their equivalents
                if(!explicitParams[i].equals(((GenericTuple) t).explicitParams[i],genericIds))
                    return false;
            }
            return super.equals(t, genericIds);
        }
        @Override
        protected boolean isSubtype(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if((!(t instanceof GenericTuple))||((GenericTuple) t).explicitParams.length!= explicitParams.length)
                return false;
            for(int i = 0; i< explicitParams.length; i++){//map generic parameters to their equivalents
                if(!explicitParams[i].equals(((GenericTuple) t).explicitParams[i],genericIds))
                    return false;
            }
            return super.isSubtype(t, genericIds,implGenerics);
        }
        @Override
        protected boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if((!(t instanceof GenericTuple))||((GenericTuple) t).explicitParams.length!= explicitParams.length)
                return false;
            for(int i = 0; i< explicitParams.length; i++){//map generic parameters to their equivalents
                if(!explicitParams[i].equals(((GenericTuple) t).explicitParams[i],genericIds))
                    return false;
            }
            return super.canCastTo(t, genericIds,implGenerics);
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
        Procedure replaceGenerics(HashMap<GenericId,Type> generics,GenericIds ids) {
            Type[] newIns=new Type[inTypes.length];
            boolean changed=false;
            for(int i=0;i<inTypes.length;i++){
                newIns[i]=inTypes[i].replaceGenerics(generics,ids);
                if(newIns[i]!=inTypes[i]){
                    changed=true;
                }
            }
            Type[] newOuts=new Type[outTypes.length];
            for(int i=0;i<this.outTypes.length;i++){
                newOuts[i]=this.outTypes[i].replaceGenerics(generics,ids);
                if(newOuts[i]!=this.outTypes[i]){
                    changed=true;
                }
            }
            return changed?Procedure.create(newIns,newOuts):this;
        }

        @Override
        protected boolean isSubtype(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if(t instanceof Procedure proc){
                if(proc.inTypes.length== inTypes.length&&proc.outTypes.length==outTypes.length){
                    for(int i=0;i< inTypes.length;i++){
                        if(!proc.inTypes[i].isSubtype(inTypes[i],genericIds,implGenerics.swapped())){
                            return false;
                        }
                    }
                    for(int i=0;i< outTypes.length;i++){
                        if(!outTypes[i].isSubtype(proc.outTypes[i],genericIds,implGenerics)){
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }
            return t== UNTYPED_PROCEDURE ||super.isSubtype(t,genericIds,implGenerics);
        }
        //addLater? overwrite canCastTo

        @Override
        protected boolean equals(Type t, GenericIds genericIds) {
            if (this == t) return true;
            if ( ! (t instanceof Procedure proc)) return false;
            if(proc.inTypes.length!=inTypes.length||((Procedure) t).outTypes.length!=outTypes.length)
                return false;
            for(int i=0;i<inTypes.length;i++){
                if(!inTypes[i].equals(proc.inTypes[i],genericIds))
                    return false;
            }
            for(int i=0;i<outTypes.length;i++){
                if(!outTypes[i].equals(proc.outTypes[i],genericIds))
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
            StringBuilder sb=new StringBuilder();
            for(Type t:explicitParams){
                sb.append(t.name).append(" ");
            }
            return new GenericProcedure(sb.append(getName(inTypes,outTypes)).toString(),explicitParams.toArray(GenericParameter[]::new),
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
            return changed?new GenericProcedure(name, explicitGenerics,newArgs,implicitGenerics,newIn,newOut):this;
        }
        @Override
        Procedure replaceGenerics(HashMap<GenericId,Type> generics,GenericIds ids) {
            Type[] newIn=new Type[inTypes.length];
            boolean changed=false;
            for(int i=0;i<inTypes.length;i++){
                newIn[i]=inTypes[i].replaceGenerics(generics,ids);
                if(newIn[i]!=inTypes[i]){
                    changed=true;
                }
            }
            Type[] newOut=new Type[outTypes.length];
            for(int i=0;i<outTypes.length;i++){
                newOut[i]=outTypes[i].replaceGenerics(generics,ids);
                if(newOut[i]!=outTypes[i]){
                    changed=true;
                }
            }
            Type[] newArgs=new Type[this.genericArgs.length];
            for(int i=0;i<this.genericArgs.length;i++){
                newArgs[i]=this.genericArgs[i].replaceGenerics(generics,ids);
                if(newArgs[i]!=this.genericArgs[i]){
                    changed=true;
                }
            }
            return changed?new GenericProcedure(name, explicitGenerics,newArgs,implicitGenerics,newIn,newOut):this;
        }
        @Override
        protected boolean equals(Type t, GenericIds genericIds) {
            if((!(t instanceof GenericProcedure))||((GenericProcedure) t).explicitGenerics.length!= explicitGenerics.length)
                return false;
            for(int i = 0; i< explicitGenerics.length; i++){//map generic parameters to their equivalents
                if(!explicitGenerics[i].equals(((GenericProcedure) t).explicitGenerics[i],genericIds))
                    return false;
            }
            return super.equals(t, genericIds);
        }
        @Override
        protected boolean isSubtype(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if((!(t instanceof GenericProcedure))||((GenericProcedure) t).explicitGenerics.length!= explicitGenerics.length)
                return false;
            for(int i = 0; i< explicitGenerics.length; i++){//map generic parameters to their equivalents
                if(!explicitGenerics[i].equals(((GenericProcedure) t).explicitGenerics[i],genericIds))
                    return false;
            }
            return super.isSubtype(t, genericIds, implGenerics);
        }
        @Override
        protected boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if((!(t instanceof GenericProcedure))||((GenericProcedure) t).explicitGenerics.length!= explicitGenerics.length)
                return false;
            for(int i = 0; i< explicitGenerics.length; i++){//map generic parameters to their equivalents
                if(!explicitGenerics[i].equals(((GenericProcedure) t).explicitGenerics[i],genericIds))
                    return false;
            }
            return super.canCastTo(t, genericIds, implGenerics);
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
        public boolean canCastTo(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            return t==UINT||t==INT||super.canCastTo(t,genericIds,implGenerics);
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
        public GenericParameter(int id, boolean isImplicit, FilePosition pos) {
            super("'"+id,false);
            this.id=id;
            this.isImplicit = isImplicit;
            this.declaredAt=pos;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type r=generics.get(this);
            return r!=null?r:this;
        }
        @Override
        Type replaceGenerics(HashMap<GenericId, Type> generics, GenericIds ids) {
            GenericId id=ids.l.get(this);
            if (id == null) {
                id=new GenericId(isImplicit?ids.implL++:ids.implR++,isImplicit);
                ids.l.put(this,id);
            }
            Type r=generics.get(id);
            return r!=null?r:this;
        }

        @Override
        protected boolean equals(Type t, GenericIds genericIds) {
            if(this==t)
                return true;
            if(!(t instanceof GenericParameter)||isImplicit!=((GenericParameter) t).isImplicit)
                return false;
            GenericId id1=genericIds.l.get(this);
            GenericId id2=genericIds.r.get((GenericParameter)t);
            if(id1==null||id2==null){
                if(id1==null&&id2==null){
                    genericIds.l.put(this,
                            isImplicit?new GenericId(genericIds.implL++,true):new GenericId(genericIds.expL++,false));
                    genericIds.r.put((GenericParameter)t,//this.isImplicit == t.isImplicit
                            isImplicit?new GenericId(genericIds.implR++,true):new GenericId(genericIds.expR++,false));
                    return true;
                }
                return false;
            }
            return id1.equals(id2);
        }
        @Override
        protected boolean isSubtype(Type t, GenericIds genericIds,BoundMaps implGenerics) {
            if(this==t)
                return true;
            if(isImplicit){
                GenericId boundId=genericIds.l.get(this);
                if(boundId==null){
                    boundId=new GenericId(genericIds.implL++,true);
                    genericIds.l.put(this,boundId);
                }
                GenericBound bound=implGenerics.l.get(boundId);
                if(bound!=null){
                    if(bound.max==null||t.isSubtype(bound.max,genericIds,implGenerics)) {
                        bound=new GenericBound(bound.min,t);
                    }
                }else{
                    bound=new GenericBound(null,t);
                }
                implGenerics.l.put(boundId,bound);
                return true;
            }else{
                return equals(t,genericIds);
            }
        }
        @Override
        protected boolean canCastTo(Type t, GenericIds genericIds, BoundMaps implGenerics) {
            if(this==t)
                return true;
            if(isImplicit){
                throw new UnsupportedOperationException("casting implicit generics is currently not implemented");
            }else {
                return equals(t,genericIds);
            }
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
}