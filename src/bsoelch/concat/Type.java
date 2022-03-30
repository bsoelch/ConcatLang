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
    public static final Type TYPE  = new Type("type", false) {
        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();//addLater? make type-data getters return optional
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{TYPE},"content") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofType(values[0].asType().content())};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"genericArguments") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().genericArguments().stream().map(Value::ofType).toArray(Value[]::new))};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"inTypes") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().inTypes().stream().map(Value::ofType).toArray(Value[]::new))};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"outTypes") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().outTypes().stream().map(Value::ofType).toArray(Value[]::new))};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{RAW_STRING()},"name") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofString(values[0].asType().typeName(),false)};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(RAW_STRING())},"fieldNames") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.createArray(Type.arrayOf(Type.RAW_STRING()),values[0].asType().fields()
                            .stream().map(s->Value.ofString(s,false)).toArray(Value[]::new))};
                }
            }, declaredAt());

            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isEnum") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType() instanceof Enum)};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isArray") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType().isArray())};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMemory") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType().isMemory())};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isProc") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType() instanceof Type.Procedure)};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isOptional") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType().isOptional())};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isTuple") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType() instanceof Tuple)};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isStruct") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType() instanceof Struct)};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isUnion") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType() instanceof UnionType)};
                }
            }, declaredAt());

            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMutable") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType().isMutable())};
                }
            }, declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMaybeMutable") {
                @Override
                Value[] callWith(Value[] values) throws ConcatRuntimeError {
                    return new Value[]{Value.ofBool(values[0].asType().isMaybeMutable())};
                }
            }, declaredAt());
        }
    };
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
    public static void resetCached() {
        WrapperType.resetCached();
        Struct.resetCached();
    }

    final String name;
    final boolean switchable;
    final Mutability mutability;
    /**fields that are attached to this type*/
    private final HashMap<String,Value> typeFields = new HashMap<>();
    /**"pseudo-fields" for values of this type,
     * a pseudo field is a procedure that takes this type as last parameter*/
    private final HashMap<String, Parser.Callable> pseudoFields = new HashMap<>();
    private boolean typeFieldsInitialized=false;

    /**cache for the different variants of this type*/
    private HashMap<Mutability,Type> withMutability=new HashMap<>();

    static String mutabilityPostfix(Mutability mutability) {
        switch (mutability){
            case DEFAULT -> {return "";}
            case MUTABLE -> {return " mut";}
            case UNDECIDED -> {return " mut?";}
            case IMMUTABLE -> {return " mut~";}
            case INHERIT -> {return " mut^";}
        }
        throw new RuntimeException("unreachable");
    }
    private Type(String name, boolean switchable) {
        this(name,switchable,Mutability.DEFAULT);
    }
    private Type(String name, boolean switchable,Mutability mutability) {
        this.name = name;
        this.switchable = switchable;
        this.mutability=mutability;

        withMutability.put(mutability,this);
    }

    FilePosition declaredAt(){
        return Value.InternalProcedure.POSITION;
    }
    private void ensureFieldsInitialized(){
        if(!typeFieldsInitialized){
            typeFieldsInitialized=true;
            try {
                initTypeFields();
            } catch (SyntaxError e) {
                throw new RuntimeException(e);
            }
        }
    }
    void initTypeFields() throws SyntaxError {
        addPseudoField(new Value.InternalProcedure(new Type[]{this},new Type[]{TYPE},"type") {
            @Override
            Value[] callWith(Value[] values){
                return new Value[]{Value.ofType(values[0].type)};
            }
        },declaredAt());
    }
    void forEachStruct(SyntaxError.ThrowingConsumer<Struct> action) throws SyntaxError{ }

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

    boolean mutabilityIncompatible(Type t) {
        return Mutability.isDifferent(t.mutability,mutability) && t.mutability != Mutability.UNDECIDED;
    }
    public boolean isMutable() {
        return mutability==Mutability.MUTABLE;
    }
    public boolean isDeeplyImmutable() {
        return mutability!=Mutability.MUTABLE;
    }
    public boolean isMaybeMutable() {
        return mutability==Mutability.UNDECIDED;
    }
    public boolean isArray() {
        return false;
    }
    public Type asArray(){
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
    public String typeName(){
        throw new UnsupportedOperationException();
    }
    public Type content() {
        throw new UnsupportedOperationException();
    }
    public List<Type> genericArguments(){
        return Collections.emptyList();
    }
    public List<Type> inTypes(){
        return Collections.emptyList();
    }
    public List<Type> outTypes(){
        return Collections.emptyList();
    }
    public List<String> fields(){
        return Collections.emptyList();
    }

    void addField(String name,Value fieldValue,FilePosition pos) throws SyntaxError {
        ensureFieldsInitialized();
        if(typeFields.put(name, fieldValue)!=null){
            throw new SyntaxError(this.name+" already has a field "+name+" ",pos);
        }
    }
    void addPseudoField(Parser.Callable fieldValue, FilePosition declaredAt) throws SyntaxError {
        ensureFieldsInitialized();
        Type[] in=fieldValue.type().inTypes;
        if(in.length==0||!canAssignTo(in[in.length-1])){
            throw new SyntaxError("invalid signature for pseudo-field: "+Arrays.toString(in),declaredAt);
        }
        pseudoFields.put(fieldValue.name(),fieldValue);
        addField(fieldValue.name(),(Value)fieldValue, declaredAt);
    }
    HashMap<String,Value> typeFields(){
        ensureFieldsInitialized();
        return typeFields;
    }
    HashMap<String, Parser.Callable> pseudoFields(){
        ensureFieldsInitialized();
        return pseudoFields;
    }

    /**returns the semi-mutable version of this type
     * @return a copy of this type with the given mutability*/
    public Type setMutability(Mutability newMutability){
        if(newMutability!=mutability){
            Type withMut=withMutability.get(newMutability);
            if(withMut!=null){
                return withMut;
            }
            withMut=copyWithMutability(newMutability);
            withMut.withMutability=withMutability;
            withMut.withMutability.put(newMutability,withMut);
            return withMut;
        }
        return this;
    }
    Type copyWithMutability(Mutability newMutability){ return this; }
    static Type updateChildMutability(Type t, Mutability mutability){
        if(t.mutability==Mutability.INHERIT){
            return t.setMutability(mutability==Mutability.INHERIT?Mutability.DEFAULT:mutability);
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

    public static Type arrayOf(Type contentType){
        return WrapperType.create(WrapperType.ARRAY,contentType,Mutability.DEFAULT);
    }
    public static Type memoryOf(Type contentType){
        return WrapperType.create(WrapperType.MEMORY,contentType,Mutability.DEFAULT);
    }

    public static Type optionalOf(Type contentType){
        return WrapperType.create(WrapperType.OPTIONAL,contentType,Mutability.DEFAULT);
    }

    private static class WrapperType extends Type {
        static final HashMap<Type,WrapperType> arrays = new HashMap<>();
        static final HashMap<Type,WrapperType> memories = new HashMap<>();
        static final HashMap<Type,WrapperType> optionals = new HashMap<>();
        public static void resetCached(){
            arrays.clear();
            memories.clear();
            optionals.clear();
        }

        static final String ARRAY = "array";
        static final String MEMORY = "memory";
        static final String OPTIONAL = "optional";

        static final WrapperType BYTES= create(ARRAY,Type.BYTE,Mutability.IMMUTABLE);
        static final WrapperType UNICODE_STRING= create(ARRAY,Type.CODEPOINT,Mutability.IMMUTABLE);


        final Type contentType;
        final String wrapperName;

        private static WrapperType create(String wrapperName, Type contentType,Mutability mutability) {
            WrapperType cached;
            switch (wrapperName){
                case ARRAY -> cached = arrays.get(contentType);
                case MEMORY -> cached = memories.get(contentType);
                case OPTIONAL ->cached = optionals.get(contentType);
                default -> throw new IllegalArgumentException("unexpected type-wrapper : "+wrapperName);
            }
            if(cached!=null){
                return cached.setMutability(mutability);
            }
            cached = new WrapperType(wrapperName,contentType,mutability);
            switch (wrapperName){
                case ARRAY -> arrays.put(contentType,cached);
                case MEMORY -> memories.put(contentType,cached);
                case OPTIONAL -> optionals.put(contentType,cached);
                default -> throw new IllegalArgumentException("unexpected type-wrapper : "+wrapperName);
            }
            return cached;
        }
        private WrapperType(String wrapperName, Type contentType,Mutability mutability){
            super(updateChildMutability(contentType,mutability).name+" "+
                    wrapperName+mutabilityPostfix(mutability), ARRAY.equals(wrapperName)&&
                    (contentType==BYTE||contentType==CODEPOINT),mutability);
            this.wrapperName = wrapperName;
            this.contentType = contentType;
        }

        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();
            switch (wrapperName) {
                case OPTIONAL -> {
                    addPseudoField(new Value.InternalProcedure(new Type[]{this}, new Type[]{BOOL}, "hasValue") {
                        @Override
                        Value[] callWith(Value[] values) throws ConcatRuntimeError {
                            return new Value[]{Value.ofBool(values[0].hasValue())};
                        }
                    }, declaredAt());
                    //addLater static check if optional is nonempty
                    addPseudoField(new Value.InternalProcedure(new Type[]{this}, new Type[]{contentType}, "value") {
                        @Override
                        Value[] callWith(Value[] values) throws ConcatRuntimeError {
                            return new Value[]{values[0].unwrap()};
                        }
                    }, declaredAt());
                }
                case MEMORY -> {
                    addPseudoField(new Value.InternalProcedure(new Type[]{this}, new Type[]{UINT}, "length") {
                        @Override
                        Value[] callWith(Value[] values) {
                            return new Value[]{Value.ofInt(((ArrayLike) values[0]).length(), true)};
                        }
                    }, declaredAt());
                    addPseudoField(new Value.InternalProcedure(new Type[]{this}, new Type[]{UINT}, "capacity") {
                        @Override
                        Value[] callWith(Value[] values) {
                            return new Value[]{Value.ofInt(((ArrayLike) values[0]).capacity(), true)};
                        }
                    }, declaredAt());
                    addPseudoField(new Value.InternalProcedure(new Type[]{this}, new Type[]{UINT}, "offset") {
                        @Override
                        Value[] callWith(Value[] values) {
                            return new Value[]{Value.ofInt(((ArrayLike) values[0]).offset(), true)};
                        }
                    }, declaredAt());
                }
                case ARRAY -> addPseudoField(new Value.InternalProcedure(new Type[]{this}, new Type[]{UINT}, "length") {
                    @Override
                    Value[] callWith(Value[] values) {
                        return new Value[]{Value.ofInt(((ArrayLike) values[0]).length(), true)};
                    }
                }, declaredAt());
            }
        }
        void forEachStruct(SyntaxError.ThrowingConsumer<Struct> action) throws SyntaxError {
            contentType.forEachStruct(action);
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
        public boolean isDeeplyImmutable() {
            return super.isDeeplyImmutable()&&contentType.isDeeplyImmutable();
        }

        @Override
        public WrapperType setMutability(Mutability newMutability) {
            return (WrapperType)super.setMutability(newMutability);
        }
        @Override
        public Type copyWithMutability(Mutability newMutability) {
            return new WrapperType(wrapperName,contentType,newMutability);
        }

        @Override
        public Type asArray() {
            return wrapperName.equals(ARRAY)?this:wrapperName.equals(MEMORY)?create(ARRAY,contentType,mutability):super.asArray();
        }

        @Override
        public boolean isRawString() {
            return wrapperName.equals(ARRAY)&&contentType==BYTE;
        }
        @Override
        public boolean isUnicodeString() {
            return wrapperName.equals(ARRAY)&&contentType==CODEPOINT;
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
                    if(Mutability.isDifferent(t.mutability,mutability)){
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
                if (mutabilityIncompatible(t)) return false;//incompatible mutability
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
            return content().equals(that.content(),generics) &&Mutability.isEqual(t.mutability,mutability)
                    && Objects.equals(wrapperName, that.wrapperName);
        }
        @Override
        public int hashCode() {
            return Objects.hash(content(), wrapperName);
        }
    }

    static abstract class TupleLike extends Type{
        private TupleLike(String name, boolean switchable, Mutability mutability) {
            super(name, switchable, mutability);
        }
        @Override
        void forEachStruct(SyntaxError.ThrowingConsumer<Struct> action) throws SyntaxError {
            for(int i=0;i<elementCount();i++){
                getElement(i).forEachStruct(action);
            }
        }
        abstract int elementCount();
        abstract Type getRawElement(long index);
        Type getElement(long index) {
            return updateChildMutability(getRawElement(index));
        }
        abstract Type[] getElements();
        private static boolean canAssignElements(TupleLike t1,TupleLike t2, BoundMaps bounds) {
            if(t1.equals(t2))
                return true;
            if(t2.elementCount()>t1.elementCount())
                return false;
            for(int i = 0; i< t2.elementCount(); i++){
                if(!t1.getElement(i).canAssignTo(t2.getElement(i), bounds)){
                    return false;
                }
            }
            if(t2.isMutable()){//check reverse comparison for mutable elements
                for(int i = 0; i< t2.elementCount(); i++){
                    if(!t2.getElement(i).canAssignTo(t1.getElement(i), bounds)){
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public List<Type> outTypes() {
            return Arrays.asList(getElements());
        }

        @Override
        public int depth() {
            int d=0;
            for(int i=0;i<elementCount();i++){
                d=Math.max(d,getRawElement(i).depth());
            }
            return d+1;
        }
        @Override
        public boolean isDeeplyImmutable() {
            if(!super.isDeeplyImmutable()){
                return false;
            }
            for(int i=0;i<elementCount();i++){
                if(!getRawElement(i).isDeeplyImmutable())
                    return false;
            }
            return true;
        }
    }
    public static class Tuple extends TupleLike{
        static final Tuple EMPTY_TUPLE=create( new Type[0]);

        /**initialized types in this Tuple or null if this tuple is not yet initialized*/
        final Type[] elements;

        public static Tuple create(Type[] elements){
            return create(elements, Mutability.DEFAULT);
        }
        private static Tuple create(Type[] elements,Mutability mutability) {
            String typeName = getTypeName(elements);
            return new Tuple(typeName+mutabilityPostfix(mutability),elements, mutability);//addLater? caching
        }
        private static String getTypeName(Type[] elements) {
            StringBuilder sb = new StringBuilder("( ");
            for (Type t : elements) {
                sb.append(t).append(" ");
            }
            return sb.append(")").toString();
        }

        private Tuple(String name,  Type[] elements,Mutability mutability){
            super(name, false,mutability);
            this.elements=elements;
        }

        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            if(elements==null){
                throw new RuntimeException("elements in anonymous tuple should not be null");
            }
            boolean changed=false;
            Type[] newElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed =true;
                }
            }
            return changed ? create(newElements,mutability) : this;
        }

        @Override
        public Tuple setMutability(Mutability newMutability) {
            return (Tuple) super.setMutability(newMutability);
        }
        Tuple copyWithMutability(Mutability newMutability) {
            Tuple prev;
            if(elements==null){
                throw new RuntimeException("elements in anonymous tuple should not be null");
            }
            prev = create(elements, newMutability);
            return prev;
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof Tuple){
                if(mutabilityIncompatible(t)){
                    return false;//incompatible mutability
                }
                return TupleLike.canAssignElements(this,(Tuple) t, bounds);
            }else{
                return super.canAssignTo(t,bounds);
            }
        }
        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            if(mutabilityIncompatible(t)){
                return false;//incompatible mutability
            }
            if(t instanceof Tuple){
                return TupleLike.canAssignElements(this,(Tuple) t, bounds)||
                        TupleLike.canAssignElements(((Tuple) t),this, bounds.swapped());
            }else{
                return super.canAssignTo(t,bounds);
            }
        }

        @Override
        public int elementCount(){
            return elements.length;
        }
        @Override
        public Type getRawElement(long i){
            return elements[(int) i];
        }
        @Override
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
            if(elements==null||tuple.elements==null)
                return false;//uninitialized tuples are only equal if they are the same object
            if(Mutability.isDifferent(t.mutability,mutability))
                return false;
            if(tuple.elements.length!=elements.length)
                return false;
            for(int i=0;i<elements.length;i++){
                if(!(elements[i].equals(tuple.elements[i],generics)))
                    return false;
            }
            return true;
        }
        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }

    }

    record StructId(FilePosition pos,Type[] genericArgs){
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StructId structId = (StructId) o;
            return Objects.equals(pos, structId.pos) && Arrays.equals(genericArgs, structId.genericArgs);
        }
        @Override
        public int hashCode() {
            int result = Objects.hash(pos);
            result = 31 * result + Arrays.hashCode(genericArgs);
            return result;
        }
    }
    record StructField(String name, Parser.Accessibility accessibility, boolean mutable, FilePosition declaredAt){}
    public static class Struct extends TupleLike implements Parser.NamedDeclareable {
        static final HashMap<StructId,Struct> cached=new HashMap<>();
        public static void resetCached(){
            cached.clear();
        }
        final boolean isPublic;
        final FilePosition declaredAt;
        final FilePosition endPos;

        final String baseName;
        final Type[] genericArgs;

        /**initialized types in this Tuple or null if this tuple is not yet initialized*/
        Type[] elements;
        /**Tokens in the body of this Tuple*/
        ArrayList<Parser.Token> tokens;
        /**Context in which this tuple was declared*/
        Parser.StructContext context;

        StructField[] fields;
        String[] fieldNames;
        HashMap<String,Integer> indexByName;
        final Struct extended;

        static Struct create(String name,boolean isPublic,Struct extended,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             FilePosition declaredAt,FilePosition endPos){
            return create(name, isPublic, extended, new Type[0],tokens,context,Mutability.DEFAULT,declaredAt,endPos,true);
        }
        static Struct create(String name,boolean isPublic,Struct extended,Type[] genericArgs,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             FilePosition declaredAt,FilePosition endPos){
           return create(name, isPublic, extended, genericArgs, tokens,context,Mutability.DEFAULT,declaredAt,endPos,true);
        }
        static Struct create(String name,boolean isPublic,Struct extended,Type[] genericArgs,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             Mutability mutability,FilePosition declaredAt,FilePosition endPos,boolean useCache){
            StructId id=new StructId(declaredAt,genericArgs);
            Struct prev;
            if(useCache){
                prev=cached.get(id);
                if(prev!=null){
                    return prev.setMutability(mutability);
                }
            }
            prev=new Struct(namePrefix(name,genericArgs)+mutabilityPostfix(mutability), name, isPublic,
                    extended, genericArgs,null,null,tokens,context,mutability,declaredAt,endPos);
            if (useCache) {
                cached.put(id,prev);
            }
            return prev;
        }
        private static Struct create(String name,boolean isPublic,Struct extended,Type[] genericArgs,
                             StructField[] fields,Type[] types,Mutability mutability, FilePosition declaredAt,FilePosition endPos,
                                     boolean useCache) {
            if(fields.length!=types.length){
                throw new IllegalArgumentException("fields and types have to have the same length");
            }
            StructId id=new StructId(declaredAt,genericArgs);
            Struct prev;
            if(useCache){
                prev=cached.get(id);
                if(prev!=null){
                    return prev.setMutability(mutability);
                }
            }
            prev = new Struct(namePrefix(name,genericArgs)+mutabilityPostfix(mutability), name, isPublic,
                    extended, genericArgs, types,fields,null, null,mutability, declaredAt,endPos);
            if (useCache) {
                cached.put(id,prev);
            }
            return prev;
        }
        static String namePrefix(String baseName, Type[] genericArgs) {
            StringBuilder sb=new StringBuilder();
            //add generic args to name
            for(Type t: genericArgs){
                sb.append(t.name).append(" ");
            }
            return sb.append(baseName).toString();
        }

        private Struct(String name, String baseName, boolean isPublic, Struct extended,
                       Type[] genArgs, Type[] elements, StructField[] fields,
                       ArrayList<Parser.Token> tokens, Parser.StructContext context,
                       Mutability mutability, FilePosition declaredAt,FilePosition endPos) {
            super(name,false,mutability);
            if((elements==null)==(tokens==null)){
                throw new RuntimeException("exactly one of elements and tokens should be non-null");
            }
            this.isPublic=isPublic;
            this.declaredAt=declaredAt;
            this.endPos=endPos;
            this.extended = extended;
            this.baseName=baseName;
            this.genericArgs=genArgs;

            this.elements = elements;
            this.tokens = tokens;
            this.context = context;
            if(fields!=null){
                assert elements!=null;
                initializeFields(fields);
            }else{
                assert elements==null;
                this.fields=null;
                fieldNames=null;
                indexByName=null;
            }
        }
        @Override
        public List<Type> genericArguments() {
            return Arrays.asList(genericArgs);
        }

        public boolean isTypeChecked(){
            return elements!=null;
        }
        public ArrayList<Parser.Token> getTokens() {
            if(isTypeChecked()){
                throw new RuntimeException("getTokens() can only be called before typeChecking");
            }
            return tokens;
        }
        private void initializeFields(StructField[] fields) {
            this.fields= fields;
            indexByName =new HashMap<>(fields.length);
            fieldNames=new String[fields.length];
            for(int i = 0; i< fields.length; i++){
                fieldNames[i]= fields[i].name;
                indexByName.put(fields[i].name,i);
            }
        }
        public void setFields(StructField[] fields,Type[] elements) {
            this.elements = elements;
            initializeFields(fields);
            tokens.clear();//tokens are no longer necessary
        }
        @Override
        public int elementCount(){
            if(elements==null){
                throw new RuntimeException("cannot call elementCount() on uninitialized tuple");
            }
            return elements.length;
        }
        @Override
        public Type getRawElement(long i){
            if(elements==null){
                throw new RuntimeException("cannot call getElement() on uninitialized tuple");
            }
            return updateChildMutability(elements[(int) i]);
        }
        @Override
        public Type[] getElements() {
            if(elements==null){
                throw new RuntimeException("cannot call getElements() on uninitialized tuple");
            }
            Type[] mappedElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                mappedElements[i]=updateChildMutability(elements[i]);
            }
            return mappedElements;
        }

        @Override
        void forEachStruct(SyntaxError.ThrowingConsumer<Struct> action) throws SyntaxError {
            action.accept(this);
            super.forEachStruct(action);
        }

        @Override
        public Struct setMutability(Mutability newMutability) {
            return (Struct) super.setMutability(newMutability);
        }
        @Override
        Struct copyWithMutability(Mutability newMutability) {
            if(!isTypeChecked()){
                return create(baseName,isPublic,extended==null?null:extended.setMutability(newMutability),
                        genericArgs,new ArrayList<>(tokens),context.newInstance(true),
                        newMutability,declaredAt,endPos,false);
            }
            return create(baseName,isPublic,extended==null?null:extended.setMutability(newMutability),
                    genericArgs,fields,elements,newMutability,declaredAt,endPos,false);
        }

        @Override
        public List<String> fields() {
            if(!isTypeChecked()){
                throw new RuntimeException("cannot call setMutability() on uninitialized struct");
            }
            return Arrays.asList(fieldNames);
        }


        @Override
        Struct replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) {
            boolean changed=false;
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
            if(elements==null){
                ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens.size());
                for(Parser.Token t:tokens){
                    Parser.Token newToken = t.replaceGenerics(generics);
                    newTokens.add(newToken);
                    if(newToken!=t)
                        changed=true;
                }
                Parser.StructContext newContext=context.newInstance(false);
                for(Map.Entry<String, Parser.Declareable> e:context.elements()){
                    if(e.getValue() instanceof GenericParameter){
                        Type replace=generics.get((GenericParameter)e.getValue());
                        if(replace!=null){//replace generics in constants
                            if(replace instanceof Type.GenericParameter){
                                newContext.putElement(e.getKey(),(Type.GenericParameter) replace);
                            }else{
                                newContext.putElement(e.getKey(),
                                        new Parser.Constant(e.getKey(),false,Value.ofType(replace),e.getValue().declaredAt()));
                            }
                            changed=true;
                        }
                    }
                }
                return changed?create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                        newArgs,newTokens,newContext,mutability,declaredAt,endPos,true):this;
            }
            Type[] newElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed =true;
                }
            }
            return changed?create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                            newArgs,fields,newElements,mutability,declaredAt,endPos,true):this;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            if(!(t instanceof Struct))
                return false;
            return ((Struct) t).declaredAt.equals(declaredAt)&&super.equals(t, generics);
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof Struct){
                if(mutabilityIncompatible(t)){
                    return false;
                }
                if(!declaredAt.equals(((Struct) t).declaredAt)){
                    return extended != null && extended.canAssignTo(t, bounds);
                }
                return TupleLike.canAssignElements(this,(Struct)t,bounds);
            }
            return super.canAssignTo(t, bounds);
        }
        private boolean rootCastableTo(Struct s){
           return declaredAt.equals(s.declaredAt)||
                   (extended != null && extended.rootCastableTo(s))||
                   (s.extended != null && rootCastableTo(s.extended));
        }
        @Override
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof Struct) {
                if(mutabilityIncompatible(t)){
                    return false;
                }
                if(!rootCastableTo((Struct)t)){
                    return false;
                }
                return TupleLike.canAssignElements(this, (Struct) t, bounds) ||
                        TupleLike.canAssignElements(((Struct) t), this, bounds.swapped());
            }
            return super.canAssignTo(t, bounds);
        }

        @Override
        public String name() {
            return name;
        }
        @Override
        public String typeName() {
            return baseName;
        }
        @Override
        public Parser.DeclareableType declarableType() {
            return Parser.DeclareableType.STRUCT;
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

    public static class Procedure extends Type{
        final Type[] inTypes;
        final Type[] outTypes;
        final FilePosition declaredAt;

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
        public static Procedure create(Type[] inTypes,Type[] outTypes, FilePosition declaredAt){
            return new Procedure(getName(inTypes, outTypes),inTypes,outTypes, declaredAt);//addLater? caching
        }

        private Procedure(String name, Type[] inTypes, Type[] outTypes, FilePosition declaredAt) {
            super(name, false);
            this.inTypes=inTypes;
            this.outTypes=outTypes;
            this.declaredAt = declaredAt;
        }

        @Override
        FilePosition declaredAt() {
            return declaredAt;
        }

        @Override
        void forEachStruct(SyntaxError.ThrowingConsumer<Struct> action) throws SyntaxError {
            for(Type t:inTypes){
                t.forEachStruct(action);
            }
            for(Type t:outTypes){
                t.forEachStruct(action);
            }
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
            return changed?Procedure.create(newIns,newOuts,declaredAt):this;
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
        public static GenericProcedureType create(GenericParameter[] genericParams, Type[] inTypes, Type[] outTypes, FilePosition declaredAt) {
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
                    explicitParams.toArray(GenericParameter[]::new),implicitParams.toArray(GenericParameter[]::new),inTypes,
                    outTypes,declaredAt);//addLater? caching
        }

        private GenericProcedureType(String name, GenericParameter[] explicitGenerics, Type[] genericArgs,
                                     GenericParameter[] implicitGenerics, Type[] inTypes, Type[] outTypes, FilePosition declaredAt) {
            super(name, inTypes,outTypes, declaredAt);
            this.explicitGenerics = explicitGenerics;
            this.implicitGenerics = implicitGenerics;
            this.genericArgs=genericArgs;
        }

        @Override
        public List<Type> genericArguments() {
            return Arrays.asList(genericArgs);
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
            }//addLater? caching
            return changed?isGeneric?new GenericProcedureType(genericName(newIn,newOut,Arrays.asList(newArgs)), explicitGenerics,
                            newArgs,implicitGenerics,newIn,newOut,declaredAt):new Procedure(getName(newIn,newOut),newIn,newOut, declaredAt):this;
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
    }



    public static class Enum extends Type implements Parser.NamedDeclareable {
        final FilePosition declaredAt;
        final boolean isPublic;
        final String[] entryNames;
        public Enum(String name, boolean isPublic, String[] entryNames,FilePosition[] entryPositions,FilePosition declaredAt)
                throws SyntaxError {
            super(name, true);
            this.isPublic = isPublic;
            this.entryNames =entryNames;
            for(int i=0;i<entryNames.length;i++){
                addField(entryNames[i],new Value.EnumEntry(this,i),entryPositions[i]);
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
        public Parser.DeclareableType declarableType() {
            return Parser.DeclareableType.ENUM;
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

    public static class GenericParameter extends Type implements Parser.Declareable {
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
        public Parser.DeclareableType declarableType() {
            return Parser.DeclareableType.GENERIC;
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
            }//addLater? caching
            return new UnionType(name.append(")").toString(),types.toArray(Type[]::new));
        }
        private UnionType(String name, Type[] elements) {
            super(name, false);
            this.elements = elements;
        }

        @Override
        void forEachStruct(SyntaxError.ThrowingConsumer<Struct> action) throws SyntaxError {
            for(Type t:elements){
                t.forEachStruct(action);
            }
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
        public boolean isDeeplyImmutable() {
            if(!super.isDeeplyImmutable()){
                return false;
            }
            for(Type t:elements){
                if(!t.isDeeplyImmutable())
                    return false;
            }
            return true;
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