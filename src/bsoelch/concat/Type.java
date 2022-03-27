package bsoelch.concat;

import java.util.*;
import java.util.function.BiFunction;

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


    public static Optional<Type> commonSuperType(Type a, Type b,boolean strict) {
        if(a==b||b==null){
            return Optional.of(a);
        }else if(a==null){
            return Optional.of(b);
        }else {
            if(a.canAssignTo(b)){
                return Optional.of(b);
            }else if(b.canAssignTo(a)){
                return Optional.of(a);
            }else if((!strict)&&((a==UINT||a==INT)&&(b==UINT||b==INT))){//TODO better handling of implicit casting of numbers
                return Optional.of(UnionType.create(new Type[]{a,b}));
            }else if((!strict)&&((a==FLOAT&&(b==UINT||b==INT))||(b==FLOAT&&(a==UINT||a==INT)))){
                return Optional.of(FLOAT);
            }else if((!strict)&&a.canAssignTo(ANY)&&b.canAssignTo(ANY)){
                return Optional.of(ANY);
            }else if(((a.isList()&&b.isList())&&
                    ((a.isMutable()||a.isMaybeMutable())||(b.isMutable()||b.isMaybeMutable())))){
                Type a1=a.content(),b1=b.content();
                if(a1.canAssignTo(b1)){
                    return Optional.of(Type.maybeMutableListOf(b1));
                }else if(b1.canAssignTo(a1)){
                    return Optional.of(Type.maybeMutableListOf(a1));
                }
            }
            //TODO allow merging to a common non-var supertype
            return Optional.empty();
        }
    }
    public static Type commonSuperTypeThrow(Type a, Type b,boolean strict) {
        Optional<Type> opt=commonSuperType(a, b, strict);
        if(opt.isEmpty()){
            throw new WrappedConcatError(new TypeError("Cannot merge types "+a+" and "+b));
        }
        return opt.get();
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

        BoundMaps copy() {
            //noinspection unchecked
            return new BoundMaps((IdentityHashMap<GenericParameter, GenericBound>) l.clone(),
                    (IdentityHashMap<GenericParameter, GenericBound>) r.clone());
        }
    }

    final String name;
    final boolean switchable;
    final Mutability mutability;
    static String mutabilityPostfix(Mutability mutability) {
        switch (mutability){
            case IMMUTABLE -> {return "";}
            case MUTABLE -> {return " mut";}
            case UNDECIDED -> {return " mut?";}
            case INHERIT -> {return " mut^";}
        }
        throw new RuntimeException("unreachable");
    }
    private Type(String name, boolean switchable) {
        this(name,switchable,Mutability.IMMUTABLE);
    }
    private Type(String name, boolean switchable,Mutability mutability) {
        this.name = name;
        this.switchable = switchable;
        this.mutability=mutability;
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
        if(t instanceof GenericParameter){
            GenericBound bound=bounds.r.get(t);
            if(bound!=null){
                if(bound.min==null||bound.min.canAssignTo(this,bounds.swapped())) {
                    bound=new GenericBound(this,bound.max);
                }else if(!canAssignTo(bound.min,bounds)){ //addLater use bounds in commonSuperType
                    Optional<Type> newMin=commonSuperType(this, bound.min, false);
                    if(newMin.isEmpty()) {
                        return false;
                    }
                    bound = new GenericBound(newMin.get(), bound.max);
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
        return equals(t)||t==ANY;
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

    public int depth() {
        return 0;
    }
    public boolean isMutable() {
        return mutability==Mutability.MUTABLE;
    }
    public boolean isMaybeMutable() {
        return mutability==Mutability.UNDECIDED;
    }
    public boolean isList() {
        return false;
    }
    public boolean isArray() {
        return false;
    }
    public WrapperType asArray(){
        throw new UnsupportedOperationException();
    }
    public boolean isMemory() {
        return false;
    }
    public boolean isOptional() {
        return false;
    }
    public boolean isRawString(){
        return false;
    }
    public boolean isUnicodeString(){
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

    /**returns the semi-mutable version of this type*/
    public Type setMutability(Mutability newMutability){
        return this;
    }
    static Type updateChildMutability(Type t, Mutability mutability){
        if(t.mutability==Mutability.INHERIT){
            return t.setMutability(mutability==Mutability.INHERIT?Mutability.IMMUTABLE:mutability);
        }
        return t;
    }
    Type updateChildMutability(Type t){
        return updateChildMutability(t,mutability);
    }
    /**returns the mutable version of this type*/
    public Type mutable(){
        return setMutability(Mutability.MUTABLE);
    }
    /**returns the semi-mutable version of this type*/
    public Type maybeMutable(){
        return setMutability(Mutability.UNDECIDED);
    }
    /**returns the immutable version of this type*/
    public Type immutable(){
        return setMutability(Mutability.IMMUTABLE);
    }

    public static Type mutableListOf(Type contentType){
        return WrapperType.create(WrapperType.LIST,contentType,Mutability.MUTABLE);
    }
    public static Type maybeMutableListOf(Type contentType){
        return WrapperType.create(WrapperType.LIST,contentType,Mutability.UNDECIDED);
    }
    public static Type listOf(Type contentType){
        return WrapperType.create(WrapperType.LIST,contentType,Mutability.IMMUTABLE);
    }
    public static Type arrayOf(Type contentType){
        return WrapperType.create(WrapperType.ARRAY,contentType,Mutability.IMMUTABLE);
    }
    public static Type memoryOf(Type contentType){
        return WrapperType.create(WrapperType.MEMORY,contentType,Mutability.IMMUTABLE);
    }

    public static Type optionalOf(Type contentType){
        return WrapperType.create(WrapperType.OPTIONAL,contentType,Mutability.IMMUTABLE);
    }

    private static class WrapperType extends Type {
        static final String LIST = "list";//will be removed once it is implemented in concat (using memory)

        static final String ARRAY = "array";
        static final String MEMORY = "memory";
        static final String OPTIONAL = "optional";

        static final WrapperType BYTES= new WrapperType(LIST,Type.BYTE,Mutability.IMMUTABLE);
        static final WrapperType UNICODE_STRING= new WrapperType(LIST,Type.CODEPOINT,Mutability.IMMUTABLE);

        final Type contentType;
        final String wrapperName;

        private static WrapperType create(String wrapperName, Type contentType,Mutability mutability) {
            if(mutability==Mutability.IMMUTABLE&&LIST.equals(wrapperName)) {
                if (contentType == CODEPOINT) {
                    return UNICODE_STRING;
                } else if (contentType == BYTE) {
                    return BYTES;
                }
            }
            //addLater? caching
            return new WrapperType(wrapperName,contentType,mutability);
        }
        private WrapperType(String wrapperName, Type contentType,Mutability mutability){
            super(updateChildMutability(contentType,mutability).name+" "+
                    wrapperName+mutabilityPostfix(mutability), LIST.equals(wrapperName)&&
                    (contentType==BYTE||contentType==CODEPOINT),mutability);
            this.wrapperName = wrapperName;
            this.contentType = contentType;
        }

        @Override
        WrapperType replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            Type newContent = contentType.replaceGenerics(generics);
            return contentType==newContent?this:create(wrapperName, newContent,mutability);
        }

        @Override
        public int depth() {
            return content().depth()+1;
        }
        @Override
        public Type setMutability(Mutability newMutability) {
            if(newMutability!=mutability&&!wrapperName.equals(OPTIONAL)){
                return create(wrapperName,contentType,newMutability);
            }
            return this;
        }

        @Override
        public WrapperType asArray() {
            return wrapperName.equals(ARRAY)?this:wrapperName.equals(MEMORY)?create(ARRAY,contentType,mutability):super.asArray();
        }

        @Override
        public boolean isRawString() {
            return contentType==BYTE;
        }
        @Override
        public boolean isUnicodeString() {
            return contentType==CODEPOINT;
        }
        @Override
        public boolean isList() {
            return wrapperName.equals(LIST);
        }
        @Override
        public boolean isArray() {
            return wrapperName.equals(ARRAY);
        }
        @Override
        public boolean isMemory() {
            return wrapperName.equals(MEMORY);
        }
        @Override
        public boolean isOptional() {
            return wrapperName.equals(OPTIONAL);
        }

        @Override
        public boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof WrapperType&&(((WrapperType)t).wrapperName.equals(wrapperName)||
                    wrapperName.equals(MEMORY)&&((WrapperType)t).wrapperName.equals(ARRAY))){
                if(t.mutability!=Mutability.UNDECIDED){
                    if(t.mutability!=mutability){
                        return false;//incompatible mutability
                    }//no else
                    if(isMutable()&&!t.content().canAssignTo(content(),bounds.swapped())){
                        return false;//mutable values cannot be assigned to mutable values of a different type
                    }
                }
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
            return updateChildMutability(contentType);
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if (this == t) return true;
            if (!(t instanceof WrapperType that)) return false;
            return content().equals(that.content(),generics) &&mutability==that.mutability
                    && Objects.equals(wrapperName, that.wrapperName);
        }
        @Override
        public int hashCode() {
            return Objects.hash(content(), wrapperName);
        }
    }

    public static class Tuple extends Type implements Interpreter.NamedDeclareable{
        static final Tuple EMPTY_TUPLE=create(null, true, new Type[0],Value.InternalProcedure.POSITION);

        final FilePosition declaredAt;
        final Type[] elements;
        final boolean isPublic;

        final String baseName;

        final Type[] genericArgs;
        final GenericParameter[] genericParams;

        public static Tuple create(String name, boolean isPublic, Type[] elements, FilePosition declaredAt){
            String typeName;
            if(name==null) {
                StringBuilder sb = new StringBuilder("( ");
                for (Type t : elements) {
                    sb.append(t).append(" ");
                }
                typeName=sb.append(")").toString();
            }else{
                typeName=name;
            }
            return new Tuple(typeName,name,isPublic, new GenericParameter[0], new Type[0], elements,
                    Mutability.IMMUTABLE, declaredAt);
        }
        public static Tuple create(String name,boolean isPublic,GenericParameter[] genericParams,Type[] genericArgs,
                                          Type[] elements, FilePosition declaredAt) {
            return create(name, isPublic, genericParams, genericArgs, elements,Mutability.IMMUTABLE,declaredAt);
        }
        private static Tuple create(String name,boolean isPublic,GenericParameter[] genericParams,Type[] genericArgs,
                                   Type[] elements,Mutability mutability,FilePosition declaredAt) {
            String fullName = processGenericArguments(name,genericParams, genericArgs, elements);
            return new Tuple(fullName+mutabilityPostfix(mutability),name,isPublic,genericParams, genericArgs,
                    elements,mutability, declaredAt);
        }

        private static String processGenericArguments(String baseName,GenericParameter[] genericParams,
                                                      Type[] genericArgs, Type[] elements) {
            if(genericParams.length!= genericArgs.length){
                throw new IllegalArgumentException("Number of generic arguments ("+ genericArgs.length+
                        ") does not match number of generic parameters ("+ genericParams.length+")");
            }
            IdentityHashMap<GenericParameter,Type> generics=new IdentityHashMap<>();
            for (int i = 0; i< genericParams.length; i++) {
                if (genericParams[i].isImplicit) {
                    throw new IllegalArgumentException("all generic parameters of a struct have to be explicit");
                } else {
                    generics.put(genericParams[i], genericArgs[i]);
                }
            }
            StringBuilder sb=new StringBuilder();
            if(generics.size()>0){
                //add generic args to name
                for(Type t: genericArgs){
                    sb.append(t.name).append(" ");
                }
                //Unwrap generic arguments
                for(int i = 0; i< elements.length; i++){
                    elements[i]= elements[i].replaceGenerics(generics);
                }
            }
            return sb.append(baseName).toString();
        }


        private Tuple(String name, String baseName, boolean isPublic, GenericParameter[] genericParams,
                      Type[] genericArgs, Type[] elements, Mutability mutability, FilePosition declaredAt){
            super(name, false,mutability);
            this.isPublic = isPublic;
            this.genericParams = genericParams;
            this.genericArgs = genericArgs;
            this.elements=elements;
            this.declaredAt = declaredAt;
            this.baseName=baseName;
        }

        @Override
        public List<Type> outTypes() {
            return Arrays.stream(elements).map(this::updateChildMutability).toList();
        }
        @Override
        public String typeName() {
            return name;
        }

        @Override
        public int depth() {
            int d=0;
            for(Type t:elements){
                d=Math.max(d,t.depth());
            }
            return d+1;
        }
        boolean isMutable(int index){
            return mutability==Mutability.MUTABLE;
        }

        Tuple replaceGenerics(IdentityHashMap<GenericParameter,Type> generics, BiFunction<Type[],Type[],Tuple> create){
            Type[] newElements=new Type[elements.length];
            boolean changed=false;
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed=true;
                }
            }
            Type[] newArgs;
            if(generics.size()>0){
                newArgs=new Type[this.genericArgs.length];
                for(int i=0;i<this.genericArgs.length;i++){
                    newArgs[i]=this.genericArgs[i].replaceGenerics(generics);
                    if(newArgs[i]!=this.genericArgs[i]){
                        changed=true;
                    }
                }
            }else{
                newArgs=genericArgs;
            }
            return changed?create.apply(newArgs,newElements):this;
        }

        @Override
        public Tuple setMutability(Mutability newMutability) {
            if(newMutability!=mutability){
                return create(baseName,isPublic,genericParams,genericArgs,elements,newMutability,declaredAt);
            }
            return this;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            return replaceGenerics(generics,(newArgs,newElements)->
                    create(baseName, isPublic,genericParams,newArgs,newElements,mutability,declaredAt));
        }

        /**checks if t is a valid type for type comparison with this tuple,
         *  the base method checks if the class of t is Tuple (not struct)*/
        boolean canAssignBaseTuple(Type t){
            return t.getClass()==Tuple.class;
        }
        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(canAssignBaseTuple(t)&&((Tuple) t).elementCount()<=elementCount()){
                if(t.mutability!=mutability&&t.mutability!=Mutability.UNDECIDED){
                    return false;//incompatible mutability
                }
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
        boolean canCastBaseTuple(Type t){
            return t instanceof Tuple;
        }
        @Override
        protected boolean canCastTo(Type t,  BoundMaps bounds) {
            if(canCastBaseTuple(t)){
                if(t.mutability!=mutability&&t.mutability!=Mutability.UNDECIDED){
                    return false;//incompatible mutability
                }
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
        public Type getElement(long i){
            return updateChildMutability(elements[(int) i]);
        }
        public Type[] getElements() {
            Type[] mappedElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                mappedElements[i]=updateChildMutability(elements[i]);
            }
            return mappedElements;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if (this == t) return true;
            if (t.getClass()!=getClass())
                return false;
            Tuple tuple=(Tuple)t;
            if(mutability!=t.mutability)
                return false;
            if(tuple.elements.length!=elements.length)
                return false;
            for(int i=0;i<elements.length;i++){
                if(!(elements[i].equals(tuple.elements[i],generics)))
                    return false;
            }
            if(tuple.genericArgs.length!=genericArgs.length)
                return false;
            for(int i = 0; i< genericArgs.length; i++){//compare generic parameters with their equivalents
                if(!genericArgs[i].equals(tuple.genericArgs[i],generics))
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
        @Override
        public boolean isPublic() {
            return isPublic;
        }

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
    }

    record StructField(String name,boolean mutable,FilePosition declaredAt){}
    public static class Struct extends Tuple{
        final StructField[] fields;
        final String[] fieldNames;
        final HashMap<String,Integer> indexByName;
        final Struct extended;

        static Struct create(String name,boolean isPublic,Struct extended,StructField[] fields,Type[] types,FilePosition declaredAt){
            if(fields.length!=types.length){
                throw new IllegalArgumentException("fields and types have to have the same length");
            }
            return new Struct(name, name, isPublic, extended, new GenericParameter[0], new Type[0],
                    types, fields,Mutability.IMMUTABLE,declaredAt);
        }
        static Struct create(String name,boolean isPublic,Struct extended,GenericParameter[] genericParams,Type[] genericArgs,
                                   StructField[] fields,Type[] types, FilePosition declaredAt) {
            return create(name, isPublic, extended, genericParams, genericArgs, fields, types,Mutability.IMMUTABLE, declaredAt);
        }
        private static Struct create(String name,boolean isPublic,Struct extended,GenericParameter[] genericParams,Type[] genericArgs,
                             StructField[] fields,Type[] types,Mutability mutability, FilePosition declaredAt) {
            if(fields.length!=types.length){
                throw new IllegalArgumentException("fields and types have to have the same length");
            }
            String fullName = Tuple.processGenericArguments(name,genericParams, genericArgs, types);
            fullName+=mutabilityPostfix(mutability);
            return new Struct(fullName, name, isPublic, extended, genericParams, genericArgs, types, fields,
                    mutability, declaredAt);
        }

        private Struct(String name, String baseName, boolean isPublic, Struct extended,
                       GenericParameter[] genericParams, Type[] genArgs,
                       Type[] elements, StructField[] fields,Mutability mutability, FilePosition declaredAt) {
            super(name,baseName,isPublic,genericParams,genArgs,elements, mutability, declaredAt);
            if(extended!=null){
                IdentityHashMap<GenericParameter,Type> update=new IdentityHashMap<>();
                for(int i=0;i<genericParams.length;i++){
                    update.put(genericParams[i],genArgs[i]);
                }
                extended=extended.replaceGenerics(update);//update generic arguments in extended tuple
            }
            this.extended = extended;
            this.fields=fields;
            indexByName =new HashMap<>(fields.length);
            fieldNames=new String[fields.length];
            for(int i=0;i< fields.length;i++){
                fieldNames[i]=fields[i].name;
                indexByName.put(fields[i].name,i);
            }
        }

        @Override
        public Struct setMutability(Mutability newMutability) {
            if(newMutability!=mutability){
                return create(baseName,isPublic,extended==null?null:extended.setMutability(newMutability),
                        genericParams,genericArgs,fields,elements,newMutability,declaredAt);
            }
            return this;
        }

        @Override
        boolean isMutable(int index) {
            return super.isMutable(index)&&fields[index].mutable();
        }

        @Override
        public List<String> fields() {
            return Arrays.asList(fieldNames);
        }

        @Override
        public Interpreter.DeclareableType declarableType() {
            return Interpreter.DeclareableType.STRUCT;
        }

        @Override
        Struct replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            return (Struct)replaceGenerics(generics,(newArgs,newElements)->
                    create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                   genericParams,newArgs,fields,newElements,mutability,declaredAt));
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            if(!(t instanceof Struct))
                return false;
            return ((Struct) t).declaredAt.equals(declaredAt)&&super.equals(t, generics);
        }

        @Override
        boolean canAssignBaseTuple(Type t) {
            return super.canAssignBaseTuple(t)||(t instanceof Struct&&((Struct) t).declaredAt.equals(declaredAt))||
                    (extended!=null&&extended.canAssignBaseTuple(t));
        }
        @Override
        boolean canCastBaseTuple(Type t) {
            return canAssignBaseTuple(t)||(t instanceof Tuple&&((Tuple)t).canAssignBaseTuple(this));
        }
    }

    public static class Procedure extends Type{
        final Type[] inTypes;
        final Type[] outTypes;


        static String getName(Type[] inTypes, Type[] outTypes) {
            StringBuilder name=new StringBuilder("( ");
            for(Type t: inTypes){
                name.append(t.name).append(' ');
            }
            name.append("=> ");
            for(Type t: outTypes){
                name.append(t.name).append(' ');
            }
            name.append(")");
            return name.toString();
        }
        public static Procedure create(Type[] inTypes,Type[] outTypes){
            return new Procedure(getName(inTypes, outTypes),inTypes,outTypes);
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
        public int depth() {
            int d=0;
            for(Type t:inTypes){
                d=Math.max(d,t.depth());
            }
            for(Type t:outTypes){
                d=Math.max(d,t.depth());
            }
            return d+1;
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

    public static class GenericProcedureType extends Procedure {//addLater? merge with Procedure
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
        public static GenericProcedureType create(GenericParameter[] genericParams, Type[] inTypes, Type[] outTypes) {
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
            return new GenericProcedureType(name,explicitParams.toArray(GenericParameter[]::new),
                    explicitParams.toArray(GenericParameter[]::new),implicitParams.toArray(GenericParameter[]::new),inTypes,outTypes);
        }

        private GenericProcedureType(String name, GenericParameter[] explicitGenerics, Type[] genericArgs,
                                     GenericParameter[] implicitGenerics, Type[] inTypes, Type[] outTypes) {
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
            boolean isGeneric=false;
            for(GenericParameter t:implicitGenerics){
                if(!generics.containsKey(t)){
                    isGeneric = true;
                    break;
                }
            }
            for(GenericParameter t:explicitGenerics){
                if(!generics.containsKey(t)){
                    isGeneric = true;
                    break;
                }
            }
            return changed?isGeneric?new GenericProcedureType(genericName(newIn,newOut,Arrays.asList(newArgs)), explicitGenerics,
                            newArgs,implicitGenerics,newIn,newOut):new Procedure(getName(newIn,newOut),newIn,newOut):this;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if((!(t instanceof GenericProcedureType))||((GenericProcedureType) t).explicitGenerics.length!= explicitGenerics.length)
                return false;
            for(int i = 0; i< genericArgs.length; i++){//map generic parameters to their equivalents
                if(!genericArgs[i].equals(((GenericProcedureType) t).genericArgs[i],generics))
                    return false;
            }
            return super.equals(t, generics);
        }
        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            return super.canAssignTo(t, bounds);
        }
        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            return super.canCastTo(t, bounds);
        }
    }



    public static class Enum extends Type implements Interpreter.NamedDeclareable {
        final FilePosition declaredAt;
        final boolean isPublic;
        final String[] entryNames;
        final Value.EnumEntry[] entries;
        public Enum(String name, boolean isPublic, String[] entryNames,FilePosition declaredAt) {
            super(name, true);
            this.isPublic = isPublic;
            this.entryNames =entryNames;
            entries=new Value.EnumEntry[entryNames.length];
            for(int i=0;i<entryNames.length;i++){
                entries[i]=new Value.EnumEntry(this,i);
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
        @Override
        public boolean isPublic() {
            return isPublic;
        }

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
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
        final String label;
        final int id;
        final boolean isImplicit;
        final FilePosition declaredAt;

        public GenericParameter(String label, int id, boolean isImplicit, FilePosition pos) {
            super("'"+id,false);
            this.label = label;
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
        protected boolean equals(Type t, IdentityHashMap<GenericParameter,GenericParameter> generics) {
            if(this==t)
                return true;
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
            if(t instanceof GenericParameter) {
                GenericBound tBound=bounds.r.get((GenericParameter) t);
                GenericBound mBound=bounds.l.get(this);
                if(tBound!=null){
                    if(mBound==null){
                        bounds.l.put(this,tBound);
                    }else{
                        Type newMax;
                        if(tBound.max==null){
                            newMax=mBound.max;
                        }else if(mBound.max==null){
                            newMax=tBound.max;
                        }else if(tBound.max.canAssignTo(mBound.max)){
                            newMax=tBound.max;
                        }else if(mBound.max.canAssignTo(tBound.max)){
                            newMax=mBound.max;
                        }else{
                            return false;
                        }
                        Optional<Type> newMin = commonSuperType(mBound.min, tBound.min, false);
                        if(newMin.isEmpty()){
                            return false;
                        }
                        GenericBound commonBounds=new GenericBound(newMin.get(),newMax);
                        bounds.r.put((GenericParameter) t,commonBounds);
                        bounds.r.put((GenericParameter) t,commonBounds);
                    }
                }else{
                    if(mBound==null){
                        //TODO handle comparison with empty parameter
                        throw new UnsupportedOperationException("unimplemented");
                    }else{
                        bounds.r.put((GenericParameter)t,mBound);
                    }
                }
                return true;
            }
            GenericBound bound=bounds.l.get(this);
            if(bound!=null){
                if(bound.min!=null&&!bound.min.canAssignTo(t,bounds)){
                    return false;
                }else if(bound.max==null||t.canAssignTo(bound.max,bounds.swapped())) {
                    bound=new GenericBound(bound.min,t);
                }else if(!bound.max.canAssignTo(t,bounds)){
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

        @Override
        public boolean isPublic() {
            return false;
        }

        boolean unused=true;
        @Override
        public void markAsUsed() {
            unused=false;
        }
        @Override
        public boolean unused() {
            return unused;
        }
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
        public int depth() {
            int d = 0;
            for(Type t : elements){
                d = Math.max(d, t.depth());
            }
            return d+1;
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

    static class OverloadedProcedurePointer extends Type{
        final OverloadedProcedure proc;
        final Type[] genArgs;
        final int tokenPos;
        final FilePosition pushedAt;

        OverloadedProcedurePointer(OverloadedProcedure proc, Type[] genArgs, int tokenPos, FilePosition pushedAt) {
            super(proc.name+" .type", false);
            this.proc = proc;
            this.genArgs = genArgs;
            this.tokenPos = tokenPos;
            this.pushedAt=pushedAt;
        }
        OverloadedProcedurePointer(GenericProcedure proc, Type[] genArgs, int tokenPos, FilePosition pushedAt) {
            super(proc.name()+" .type", false);
            this.proc = new OverloadedProcedure(proc);
            this.genArgs = genArgs;
            this.tokenPos = tokenPos;
            this.pushedAt=pushedAt;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            return this==t;
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            return equals(t);
        }
        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            return equals(t);
        }
    }
}