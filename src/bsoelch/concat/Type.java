package bsoelch.concat;

import java.util.*;

public class Type {

    public static class IntType extends Type{
        static final ArrayList<IntType> intTypes = new ArrayList<>();

        public static final IntType BYTE = new IntType("byte",8,true);
        public static final IntType CODEPOINT = new IntType("codepoint",32,true);
        public static final IntType INT = new IntType("int",64,true);
        public static final IntType UINT = new IntType("uint",64,false);
        final int bits;
        final boolean signed;
        /**true if this type can be cast to/from floats*/
        private IntType(String name, int bits, boolean signed) {
            super(name, BaseType.Primitive.getInt(bits, !signed), true, true);
            this.bits = bits;
            this.signed = signed;

            for(IntType t:intTypes){
                if(t.bits==bits&&t.signed==signed)//ensure each combination of sign and bit-count only exists once
                    throw new RuntimeException("multiple intTypes for "+(signed?"uint":"int")+bits+": "+t.name+" and "+name);
            }
            intTypes.add(this);
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            return t instanceof IntType&&((IntType) t).bits==bits&&((IntType) t).signed==signed;
        }

        /**find a IntType that can contain values of both a and b*/
        static Optional<IntType> commonSuperType(IntType a, IntType b){
            int n=Math.max(a.bits,b.bits);
            boolean signed=a.signed|b.signed;
            if(n==a.bits&&signed!=a.signed)
                n*=2;
            if(n==b.bits&&signed!=b.signed)
                n*=2;
            return switch (n) {
                case 8 -> Optional.of(signed ? BYTE : UINT);
                case 16, 32 -> Optional.of(signed ? CODEPOINT : UINT);
                case 64 -> Optional.of(signed ? INT : UINT);
                case 128 -> Optional.empty();
                default -> throw new RuntimeException("unexpected bit count:" + n);
            };
        }

        @Override
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            if(t.equals(this))
                return CastType.ASSIGN;
            if(t instanceof IntType)
                return ((signed==((IntType) t).signed&&bits<=((IntType) t).bits)||
                        (((IntType) t).signed&&bits<((IntType) t).bits))?CastType.CONVERT:CastType.RESTRICT;
            if(t==FLOAT)
                return CastType.CAST;
            return super.canCastTo(t, bounds);
        }
    }

    public static Type BYTE(){
        return IntType.BYTE;
    }
    public static Type CODEPOINT(){
        return IntType.CODEPOINT;
    }
    public static Type INT(){
        return IntType.INT;
    }
    public static Type UINT(){
        return IntType.UINT;
    }
    public static final Type FLOAT = new Type("float",BaseType.Primitive.get(BaseType.PrimitiveType.FLOAT),false, true) {
        @Override
        public CastType canCastTo(Type t, BoundMaps bounds) {
            return t instanceof IntType?CastType.RESTRICT:super.canCastTo(t,bounds);
        }
    };

    public static final Type TYPE  = new Type("type",BaseType.Primitive.get(BaseType.PrimitiveType.TYPE), true, true) {
        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();//addLater? make type-data getters return optional
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{TYPE},"content",
                    (values) ->
                            new Value[]{Value.ofType(values[0].asType().content())},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"genericArguments",
                (values) ->
                        new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().genericArguments().stream().map(Value::ofType).toArray(Value[]::new))},true),
                    declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"inTypes",
                (values) ->
                        new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().inTypes().stream().map(Value::ofType).toArray(Value[]::new))},true),
                    declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"outTypes",
                (values) ->
                        new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().outTypes().stream().map(Value::ofType).toArray(Value[]::new))},true),
                    declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{RAW_STRING()},"name",
                (values) -> new Value[]{Value.ofString(values[0].asType().name(),false)},true),
                    declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(RAW_STRING())},"fieldNames",
                (values) -> new Value[]{Value.createArray(Type.arrayOf(Type.RAW_STRING()),values[0].asType().fields()
                            .stream().map(s->Value.ofString(s,false)).toArray(Value[]::new))},true),
                    declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{ANY,IntType.UINT,TYPE},new Type[]{ANY},"getField",
                (values) ->  {
                    Value instance = values[0];
                    long index     = values[1].asLong();
                    Type type      = values[2].asType();
                    if(!instance.type.canAssignTo(type)){
                        throw new TypeError("cannot assign "+instance.type+" to "+type);
                    }
                    return new Value[]{instance.getField(index)};
                },true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isEnum",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Enum)},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isArray",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isArray())},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMemory",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isMemory())},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isProc",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Type.Procedure)},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isOptional",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isOptional())},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isTuple",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Tuple)},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isStruct",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Struct)},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isUnion",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof UnionType)},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isReference",
                    (values) -> new Value[]{Value.ofBool(values[0].asType().isReference())},true), declaredAt());

            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMutable",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isMutable())},true), declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMaybeMutable",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isMaybeMutable())},true), declaredAt());
        }
    };
    public static final Type BOOL  = new Type("bool",BaseType.Primitive.get(BaseType.PrimitiveType.BOOL), false, true);

    /**blank type that could contain any value*/
    public static final Type ANY = new Type("var",BaseType.StackValue.PTR, false, false) {
        @Override
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            CastType ret=canCastTo0(t, bounds);
            return ret==CastType.NONE?CastType.RESTRICT:ret;
        }
    };

    public static Type UNICODE_STRING() {
        return WrapperType.create(WrapperType.ARRAY, IntType.CODEPOINT, Mutability.IMMUTABLE);
    }
    public static Type RAW_STRING() {
        return WrapperType.create(WrapperType.ARRAY, IntType.BYTE, Mutability.IMMUTABLE);
    }

    public static Optional<Type> commonSuperType(Type a, Type b,boolean strict,boolean deRef) {
        if(a==b||b==null){
            return Optional.of(a);
        }else if(a==null){
            return Optional.of(b);
        }else {
            if(a.canAssignTo(b)){
                return Optional.of(b);
            }else if(b.canAssignTo(a)){
                return Optional.of(a);
            }else if(deRef&&a.isReference()&&a.content().canAssignTo(b)){
                    return Optional.of(b);
            }else if(deRef&&a.isReference()&&b.canAssignTo(a.content())){
                return Optional.of(a.content());
            }else if(deRef&&b.isReference()&&b.content().canAssignTo(a)){
                return Optional.of(a);
            }else if(deRef&&b.isReference()&&a.canAssignTo(b.content())){
                return Optional.of(b.content());
            }else if((!strict)&&(a instanceof IntType&&b instanceof IntType)){
                Optional<IntType> t=IntType.commonSuperType((IntType) a,(IntType)b);
                return Optional.of(t.isPresent()?t.get():UnionType.create(new Type[]{a,b}));
            }else if((!strict)&&((a==FLOAT&&b instanceof IntType)||(b==FLOAT&&a instanceof IntType))){
                return Optional.of(FLOAT);
            }else if((!strict)&&((a.isArray()&&b.isMemory())||(a.isMemory()&&b.isArray()))){
                Mutability mut=a.mutabilityIncompatible(b)?b.mutabilityIncompatible(a)?null:a.mutability:b.mutability;
                if(mut==null)
                    return Optional.empty();
                Optional<Type> content=commonSuperType(a.content(),b.content(), false,deRef);
                return content.map(t->arrayOf(t).setMutability(mut));
            }else if((!strict)&&a.isValid()&&b.isValid()){
                return Optional.of(ANY);
            }
            //TODO allow merging to a common non-var supertype
            return Optional.empty();
        }
    }
    public static Type commonSuperTypeThrow(Type a, Type b,boolean strict) {
        Optional<Type> opt=commonSuperType(a, b, strict,false);
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
        Tuple.resetCached();
    }

    record GenericTraitImplementation(Trait trait, GenericParameter[] params, Value.Procedure[] values,
                                      FilePosition implementedAt,HashMap<Parser.VariableId, Value> globalConstants,
                                      HashMap<Parser.VariableId, Parser.ValueInfo> variables,
                                      IOContext ioContext){}
    record TraitFieldPosition(Trait trait, int offset){}
    interface TraitImplementation{
        FilePosition implementedAt();
        Value.Procedure get(int offset);
    }
    record DirectTraitImplementation(Value.Procedure[] values,FilePosition implementedAt) implements TraitImplementation{
        public Value.Procedure get(int offset){
            return values[offset];
        }
    }
    record IndirectTraitImplementation(TraitImplementation base, int offset) implements TraitImplementation{
        public Value.Procedure get(int offset){
            return base.get(this.offset+offset);
        }
        @Override
        public FilePosition implementedAt() {
            return base.implementedAt();
        }
    }

    final String name;
    final boolean switchable;
    final Mutability mutability;
    private final boolean primitive;
    private BaseType baseType;
    /**fields that are attached to this type*/
    private final HashMap<String, Value> typeFields;
    private final HashMap<String, Parser.Callable> internalFields;
    private boolean typeFieldsInitialized=false;

    private final HashMap<String, TraitFieldPosition> traitFieldNames;
    //store traits by traitId, final trait offsets will be calculated in code generation phase
    private final HashMap<Trait, TraitImplementation> implementedTraits;

    /**cache for the different variants of this type*/
    private final HashMap<Mutability,Type> withMutability;
    Iterable<Type> withMutability(){
        return withMutability.values();
    }

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
    private Type(String name, BaseType baseType, boolean switchable, boolean primitive) {
        this(name,baseType,switchable,Mutability.DEFAULT, primitive);
    }
    private Type(String name, BaseType baseType, boolean switchable, Mutability mutability, boolean primitive) {
        this.name = name;
        this.baseType=baseType;
        this.switchable = switchable;
        this.mutability=mutability;
        this.primitive = primitive;

        typeFields = new HashMap<>();
        internalFields = new HashMap<>();
        traitFieldNames = new HashMap<>();
        withMutability=new HashMap<>();
        withMutability.put(mutability,this);

        implementedTraits=new HashMap<>();
    }
    private Type(String newName,BaseType newBase,Type src,Mutability newMutability){
        this.name = newName;
        this.baseType = newBase;
        this.switchable = src.switchable;
        this.mutability = newMutability;
        this.primitive=src.primitive;

        src.ensureFieldsInitialized();//ensure type fields are initialized
        typeFields =src.typeFields;
        internalFields=src.internalFields;
        typeFieldsInitialized=true;//type fields of copied type are already initialized
        traitFieldNames = src.traitFieldNames;
        withMutability= src.withMutability;
        withMutability.put(newMutability,this);

        implementedTraits=src.implementedTraits;
    }

    BaseType initBaseType() {
        throw new UnsupportedOperationException();
    }
    BaseType baseType(){
        if(baseType==null)
            baseType=initBaseType();
        return baseType;
    }



    /**returns true if this type is a valid variable type*/
    boolean isValid(){
        return true;
    }
    boolean isPrimitive(){
        return primitive;
    }

    int blockCount(){
        return baseType().blockCount();
    }

    FilePosition declaredAt(){
        return Value.InternalProcedure.POSITION;
    }
    void ensureFieldsInitialized(){
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
        addInternalField(new Value.InternalProcedure(new Type[]{this},new Type[]{TYPE},"type",
                (values) ->  new Value[]{Value.ofType(values[0].valueType())},true),declaredAt());
        //addLater allow accessing current trait-interface at runtime
    }
    void forEachStruct(ThrowingConsumer<Struct,SyntaxError> action) throws SyntaxError{ }

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
                    Optional<Type> newMin=commonSuperType(this, bound.min, false,true);
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
        return equals(t);
    }

    /**@return true if this type has the trait t*/
    public final boolean hasTrait(Trait t){
        return hasTrait(t,new BoundMaps());
    }
    boolean hasTrait(Type trait, BoundMaps bounds) {
        if(trait instanceof Trait){
            for(Trait implemented:implementedTraits.keySet()){
                BoundMaps newBounds=bounds.copy();
                if(implemented.setMutability(mutability).canAssignTo(trait, newBounds)){
                    bounds.r.putAll(newBounds.r);
                    bounds.l.putAll(newBounds.l);
                    return true;
                }
            }
        }
        return false;
    }
    FilePosition implementationPosition(Trait t){
        TraitImplementation implementation = implementedTraits.get(t);
        return implementation==null?null:implementation.implementedAt();
    }
    enum CastType{
        /**value can be directly assigned to the target type*/
        ASSIGN,
        /**conversion to a different representations of the same value*/
        CONVERT,
        /**possible lossy conversion to another type*/
        CAST,
        /**casts this type to a restricted version of this type (may fail at runtime)*/
        RESTRICT,
        /**this object cannot be casts to the given type*/
        NONE
    }
    /**@return true if values of this type can be cast to type t*/
    public final CastType canCastTo(Type t){
        return canCastTo(t,new BoundMaps());
    }

    protected CastType canCastTo0(Type t, BoundMaps bounds){
        if (hasTrait(t, bounds)) return CastType.CONVERT;
        if(t instanceof GenericParameter)
            return canAssignTo(t, bounds)?CastType.ASSIGN:CastType.NONE;
        return t==ANY?CastType.CONVERT:CastType.NONE;
    }
    protected CastType canCastTo(Type t, BoundMaps bounds){
        if(canAssignTo(t,bounds))
            return CastType.ASSIGN;
        CastType ret=canCastTo0(t, bounds);
        if(ret!=CastType.NONE)
            return ret;
        return t.canAssignTo(this,bounds.swapped())?CastType.RESTRICT:CastType.NONE;
    }
    public boolean canConvertTo(Type t){
        CastType ct=canCastTo(t);
        return ct==CastType.ASSIGN||ct==CastType.CONVERT;
    }

    @Override
    public String toString() {
        return name;
    }
    Set<GenericParameter> unboundGenerics(){
        return recursiveUnboundGenerics(Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound){
        return unbound;
    }
    Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) throws SyntaxError {
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
    public boolean isReference(){
        return false;
    }
    public boolean isRawString(){
        return false;
    }
    public boolean isUnicodeString(){
        return false;
    }
    public String name(){
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

    void addField(String name, Value fieldValue, FilePosition pos) throws SyntaxError {
        ensureFieldsInitialized();
        if(typeFields.put(name,fieldValue)!=null){
            throw new SyntaxError(this.name+" already has a type field "+name+" ",pos);
        }
    }
    void addInternalField(Parser.Callable fieldValue, FilePosition declaredAt) throws SyntaxError {
        ensureFieldsInitialized();
        Type[] in=fieldValue.type().inTypes;
        if(in.length==0||!canAssignTo(in[in.length-1].maybeMutable())){
            throw new SyntaxError(fieldValue.name()+" (declared at "+fieldValue.declaredAt()+") "+
                    "has an invalid signature for an internal-field of "+this+": "+Arrays.toString(in),declaredAt);
        }
        if(internalFields.put(fieldValue.name(),fieldValue)!=null){
            throw new SyntaxError(name+" already has a field "+fieldValue.name()+" ",declaredAt);
        }
    }

    void implementTrait(Trait trait, Value.Procedure[] implementation, FilePosition implementedAt) throws SyntaxError {
        assert trait.traitFields!=null;
        if(trait.traitFields.length!=implementation.length){
            throw new SyntaxError("wrong number of elements in implementation: "+implementation.length+
                    " expected:"+trait.traitFields.length,implementedAt);
        }
        TraitImplementation impl=implementedTraits.get(trait);
        if(impl!=null){
            throw new SyntaxError("trait "+trait+" was already implemented for "+this+" (at "+impl.implementedAt()+")",
                    implementedAt);
        }
        impl=new DirectTraitImplementation(implementation,implementedAt);
        for(int i=0;i<implementation.length;i++){
            Type[] in=implementation[i].type().inTypes;
            if(in.length==0||!canAssignTo(in[in.length-1].maybeMutable())){
                throw new SyntaxError("implementation for trait-field "+trait.traitFields[i].name()+
                        " (declared at "+trait.traitFields[i].declaredAt()+")  has an invalid signature for a trait-field of "
                        +this+": "+Arrays.toString(in),implementedAt);
            }
            TraitFieldPosition id= new TraitFieldPosition(trait,i);
            if(traitFieldNames.put(trait.traitFields[i].name(),id)!=null){//addLater give only a warning on shadowed names
                throw new SyntaxError(name+" already has a field "+trait.traitFields[i].name()+" ",implementedAt);
            }
        }
        implementedTraits.put(trait,impl);
        addInheritedTraits(impl, trait, 0);
    }
    private void addInheritedTraits(TraitImplementation baseImpl, Trait trait, int off) {
        for(Trait parent: trait.extended){
            implementedTraits.put(parent,new IndirectTraitImplementation(baseImpl, off));
            addInheritedTraits(baseImpl,parent,off);
            off +=parent.traitFields.length;
        }
    }

    void implementGenericTrait(Trait trait, GenericParameter[] params, Value.Procedure[] implementation,
                               FilePosition implementedAt, HashMap<Parser.VariableId, Value> globalConstants,
                               HashMap<Parser.VariableId, Parser.ValueInfo> variables,IOContext ioContext)
            throws SyntaxError {
        throw new UnsupportedOperationException("cannot implement generic traits for "+this);
    }

    Iterable<Value> typeFields(){
        return typeFields.values();
    }
    Value getTypeField(String name){
        ensureFieldsInitialized();
        return typeFields.get(name);
    }
    Parser.Callable getInternalField(String name){
        ensureFieldsInitialized();
        return internalFields.get(name);
    }
    private boolean traitFieldIncompatible(TraitFieldPosition id){
        Procedure t= (Procedure) getTraitField(id).type;
        return !canAssignTo(t.inTypes[t.inTypes.length-1]);
    }
    TraitFieldPosition traitFieldId(String name){
        TraitFieldPosition id= traitFieldNames.get(name);
        return id==null|| traitFieldIncompatible(id)?null:id;
    }
    Value.Procedure getTraitField(TraitFieldPosition id){
        TraitImplementation impl=implementedTraits.get(id.trait);
        return impl==null?null:impl.get(id.offset);
    }
    final Value.Procedure getTraitField(String name){
        TraitFieldPosition id=traitFieldId(name);
        return id==null?null:getTraitField(id);
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
            assert withMut.withMutability==withMutability;
            return withMut;
        }
        return this;
    }
    /**classes overwriting this method should create a new instance using the
     * {@link #Type(String,BaseType,Type,Mutability)} constructor*/
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

    public static Type referenceTo(Type contentType){
        return WrapperType.create(WrapperType.REFERENCE,contentType,Mutability.DEFAULT);
    }

    private static class WrapperType extends Type {
        static final HashMap<Type,WrapperType> arrays = new HashMap<>();
        static final HashMap<Type,WrapperType> memories = new HashMap<>();
        static final HashMap<Type,WrapperType> optionals = new HashMap<>();
        static final HashMap<Type,WrapperType> references = new HashMap<>();

        static final ArrayList<GenericTraitImplementation> arrayTraits    = new ArrayList<>();
        static final ArrayList<GenericTraitImplementation> memoryTraits   = new ArrayList<>();
        static final ArrayList<GenericTraitImplementation> optionalTraits = new ArrayList<>();
        static final ArrayList<GenericTraitImplementation> referenceTraits = new ArrayList<>();

        public static void resetCached(){
            arrays.clear();
            memories.clear();
            optionals.clear();
        }

        static final String ARRAY = "array";
        static final String MEMORY = "memory";
        static final String OPTIONAL = "optional";
        static final String REFERENCE = "reference";

        final Type contentType;
        final String wrapperName;

        private static WrapperType create(String wrapperName, Type contentType,Mutability mutability) {
            WrapperType cached;
            switch (wrapperName){
                case ARRAY -> cached = arrays.get(contentType);
                case MEMORY -> cached = memories.get(contentType);
                case OPTIONAL ->cached = optionals.get(contentType);
                case REFERENCE ->cached = references.get(contentType);
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
                case REFERENCE -> references.put(contentType,cached);
                default -> throw new IllegalArgumentException("unexpected type-wrapper : "+wrapperName);
            }
            cached.initGenericTraits();//init generic traits after storing value in cache
            return cached;
        }
        private WrapperType(String wrapperName, Type contentType,Mutability mutability){
            super(updateChildMutability(contentType,mutability).name+" "+
                    wrapperName+mutabilityPostfix(mutability),null,ARRAY.equals(wrapperName)&&
                    (contentType==IntType.BYTE||contentType==IntType.CODEPOINT),mutability,false);
            this.wrapperName = wrapperName;
            this.contentType = contentType;
        }

        @Override
        BaseType initBaseType() {
            BaseType contentBase=contentType.baseType();
            return switch (wrapperName) {
                case ARRAY -> BaseType.arrayOf(contentBase);
                case REFERENCE -> contentBase.pointerTo();
                case OPTIONAL -> BaseType.optionalOf(contentType);
                case MEMORY -> BaseType.StackValue.PTR;
                default -> throw new IllegalArgumentException("unexpected type-wrapper : " + wrapperName);
            };
        }

        private WrapperType(WrapperType src,Mutability mutability){
            super(updateChildMutability(src.contentType,mutability).name+" "+
                    src.wrapperName+mutabilityPostfix(mutability),null, src,mutability);
            this.wrapperName = src.wrapperName;
            this.contentType = src.contentType;
        }
        private void initGenericTraits(){
            IdentityHashMap<GenericParameter,Type> generics=new IdentityHashMap<>();
            List<GenericTraitImplementation> traits;
            try {
                switch (wrapperName) {
                    case ARRAY ->
                            traits=arrayTraits;
                    case MEMORY ->
                            traits=memoryTraits;
                    case OPTIONAL ->
                            traits=optionalTraits;
                    case REFERENCE ->
                            traits=referenceTraits;
                    default -> throw new RuntimeException("unexpected wrapper name:" + wrapperName);
                }
                for (GenericTraitImplementation t : traits) {
                    Value.Procedure[] updated = new Value.Procedure[t.values.length];
                    generics.clear();
                    generics.put(t.params[0], contentType);
                    for (int i = 0; i < t.values.length; i++) {
                        updated[i] = (Value.Procedure)t.values[i].replaceGenerics(generics);
                    }
                    implementTrait(t.trait.replaceGenerics(generics), updated, t.implementedAt);
                    if (!(contentType instanceof GenericParameter)) {
                        for (Value.Procedure callable : updated) {
                            Parser.typeCheckProcedure(callable, t.globalConstants, t.variables, t.ioContext);
                        }
                    }
                }
            }catch (SyntaxError e){
                throw new RuntimeException(e);//TODO handle syntaxError
            }
        }

        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();
            switch (wrapperName) {
                case REFERENCE -> {}
                case OPTIONAL -> {
                    addInternalField(new Value.InternalProcedure(new Type[]{this},new Type[]{BOOL}, "hasValue",
                            (values) -> new Value[]{Value.ofBool(values[0].hasValue())},true), declaredAt());
                    //addLater static check if optional is nonempty
                    addInternalField(new Value.InternalProcedure(new Type[]{this},new Type[]{contentType}, "value",
                            (values) -> new Value[]{values[0].unwrap()},true), declaredAt());
                }
                case MEMORY -> {
                    addInternalField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                            new Type[]{IntType.UINT}, "length", (values) ->
                            new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).length(), true)},false), declaredAt());
                    addInternalField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                            new Type[]{IntType.UINT}, "capacity", (values) ->
                            new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).capacity(), true)},false), declaredAt());
                    addInternalField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                            new Type[]{IntType.UINT}, "offset", (values) ->
                            new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).offset(), true)},false), declaredAt());
                }
                case ARRAY -> addInternalField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                        new Type[]{IntType.UINT}, "length", (values) ->
                        new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).length(), true)},false), declaredAt());
            }
        }

        void forEachStruct(ThrowingConsumer<Struct,SyntaxError> action) throws SyntaxError {
            contentType.forEachStruct(action);
        }

        @Override
        void implementGenericTrait(Trait trait, GenericParameter[] params, Value.Procedure[] implementation,
                                   FilePosition implementedAt, HashMap<Parser.VariableId, Value> globalConstants,
                                   HashMap<Parser.VariableId, Parser.ValueInfo> variables,
                                   IOContext ioContext) throws SyntaxError {
            if(params.length!=1){
                throw new IllegalArgumentException("generic traits of "+wrapperName+" have to have exactly one generic parameter");
            }
            IdentityHashMap<GenericParameter,Type> generics=new IdentityHashMap<>();
            Collection<WrapperType> types;
            final GenericTraitImplementation impl = new GenericTraitImplementation(trait, params, implementation,
                    implementedAt, globalConstants,variables, ioContext);
            switch(wrapperName){
                case ARRAY -> {
                    arrayTraits.add(impl);
                    types=arrays.values();
                }
                case MEMORY -> {
                    memoryTraits.add(impl);
                    types=memories.values();
                }
                case OPTIONAL -> {
                    optionalTraits.add(impl);
                    types=optionals.values();
                }
                case REFERENCE -> {
                    referenceTraits.add(impl);
                    types=references.values();
                }
                default -> throw new RuntimeException("unexpected wrapper name:"+wrapperName);
            }
            for(WrapperType t:types){
                Value.Procedure[] updated = new Value.Procedure[implementation.length];
                generics.clear();
                generics.put(params[0], t.contentType);
                for (int i = 0; i < implementation.length; i++) {
                    updated[i] = (Value.Procedure) implementation[i].replaceGenerics(generics);
                }
                t.implementTrait(trait.replaceGenerics(generics),updated,implementedAt);
                if(!(t.contentType instanceof GenericParameter)){
                    for (Value.Procedure callable : updated) {
                        Parser.typeCheckProcedure(callable, globalConstants,variables,ioContext);
                    }
                }
            }
        }
        @Override
        public List<Type> genericArguments() {
            return Collections.singletonList(contentType);
        }
        @Override
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            unbound=contentType.recursiveUnboundGenerics(unbound);
            return super.recursiveUnboundGenerics(unbound);
        }
        @Override
        WrapperType replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) throws SyntaxError {
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
            return wrapperName.equals(OPTIONAL)?this:new WrapperType(this,newMutability);
        }

        @Override
        public Type asArray() {
            return wrapperName.equals(ARRAY)?this:wrapperName.equals(MEMORY)?create(ARRAY,contentType,mutability):super.asArray();
        }

        @Override
        public boolean isRawString() {
            return wrapperName.equals(ARRAY)&&contentType==IntType.BYTE;
        }
        @Override
        public boolean isUnicodeString() {
            return wrapperName.equals(ARRAY)&&contentType==IntType.CODEPOINT;
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
        public boolean isReference() {
            return wrapperName.equals(REFERENCE);
        }

        @Override
        public boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof WrapperType&&(((WrapperType)t).wrapperName.equals(wrapperName))){
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
        public CastType canCastTo(Type t, BoundMaps bounds) {
            if (hasTrait(t, bounds))
                return CastType.CONVERT;
            if(t instanceof WrapperType&&((WrapperType)t).wrapperName.equals(wrapperName)){
                if (mutabilityIncompatible(t))
                    return CastType.NONE;//incompatible mutability
                return content().canCastTo(t.content(),bounds);
            }else if(isMemory()&&t.isArray()){
                if (mutabilityIncompatible(t))
                    return CastType.NONE;//incompatible mutability
                CastType ct=content().canCastTo(t.content(),bounds);
                return ct==CastType.ASSIGN?CastType.CONVERT:ct;
            }else if(wrapperName.equals(REFERENCE)){
                BoundMaps newBounds=bounds.copy();
                CastType castType = content().canCastTo(t, newBounds);
                if(castType!=CastType.NONE){
                    bounds.l.putAll(newBounds.l);
                    bounds.r.putAll(newBounds.r);
                    return castType==CastType.ASSIGN?CastType.CONVERT:castType;
                }
            }
            return super.canCastTo0(t,bounds);
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
        private TupleLike(String name,boolean switchable, Mutability mutability) {
            super(name, null,switchable, mutability, false);
        }
        private TupleLike(String name,Type src, Mutability mutability) {
            super(name,null, src, mutability);
        }
        static BaseType tupleBaseType(Type[] elements,boolean immutable) {
            if(immutable){
                ArrayList<BaseType.StackValue> values=new ArrayList<>();
                for(Type t:elements){
                    values.addAll(Arrays.asList(t.baseType().blocks()));
                }
                if(values.size()<=16){
                    return new BaseType.Composite(values.toArray(BaseType.StackValue[]::new));
                }
            }
            return BaseType.StackValue.PTR;
        }
        @Override
        void forEachStruct(ThrowingConsumer<Struct,SyntaxError> action) throws SyntaxError {
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
        static final HashMap<List<Type>,Tuple> tupleCache=new HashMap<>();
        public static void resetCached(){
            tupleCache.clear();
        }
        /**initialized types in this Tuple or null if this tuple is not yet initialized*/
        final Type[] elements;
        final FilePosition declaredAt;

        public static Tuple create(Type[] elements,FilePosition declaredAt){
            return create(elements, Mutability.DEFAULT,declaredAt);
        }
        private static Tuple create(Type[] elements,Mutability mutability,FilePosition declaredAt) {
            Tuple cached=tupleCache.get(Arrays.asList(elements));
            if(cached!=null){
                return cached.setMutability(mutability);
            }
            String typeName = getTypeName(elements);
            Tuple tuple=new Tuple(typeName+mutabilityPostfix(mutability),elements, mutability, declaredAt);
            tupleCache.put(Arrays.asList(elements),tuple);
            return tuple;
        }
        private static String getTypeName(Type[] elements) {
            StringBuilder sb = new StringBuilder("( ");
            for (Type t : elements) {
                sb.append(t).append(" ");
            }
            return sb.append(")").toString();
        }

        @Override
        BaseType initBaseType() {
            return tupleBaseType(elements,Mutability.isEqual(mutability,Mutability.IMMUTABLE));
        }
        private Tuple(String name, Type[] elements, Mutability mutability, FilePosition declaredAt){
            super(name, false,mutability);
            this.elements=elements;
            this.declaredAt = declaredAt;
        }

        private Tuple(Tuple src,Mutability mutability){
            super(src.name,src,mutability);
            this.elements=src.elements;
            this.declaredAt=src.declaredAt;
        }

        @Override
        FilePosition declaredAt() {
            return declaredAt;
        }

        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();
            addInternalField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},new Type[]{IntType.UINT},"length",
                (values) ->
                        new Value[]{Value.ofInt(values[0].length(),true)},true),declaredAt());
            addInternalField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},new Type[]{arrayOf(ANY)},"elements",
                (values) ->  new Value[]{Value.createArray(arrayOf(ANY),values[0].getElements())},true),declaredAt());
        }

        @Override
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            for(Type t:elements){
                unbound=t.recursiveUnboundGenerics(unbound);
            }
            return super.recursiveUnboundGenerics(unbound);
        }
        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) throws SyntaxError {
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
            return changed ? create(newElements,mutability,declaredAt) : this;
        }

        @Override
        public Tuple setMutability(Mutability newMutability) {
            return (Tuple) super.setMutability(newMutability);
        }
        Tuple copyWithMutability(Mutability newMutability) {
            return new Tuple(this,newMutability);
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
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof Tuple){
                if(mutabilityIncompatible(t)){
                    return CastType.NONE;//incompatible mutability
                }
                if(TupleLike.canAssignElements(this,(Tuple) t, bounds))
                    return CastType.ASSIGN;
                return TupleLike.canAssignElements(((Tuple) t),this, bounds.swapped())?CastType.RESTRICT:CastType.NONE;
            }
            return super.canCastTo0(t,bounds);
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

    record CachedStruct(HashMap<List<Type>,Struct> versions,ArrayList<Type.GenericTraitImplementation> genericTraits){}
    record StructField(String name, Parser.Accessibility accessibility, boolean mutable, FilePosition declaredAt){}
    public static class Struct extends TupleLike implements Parser.NamedDeclareable {
        static final HashMap<FilePosition,CachedStruct> cache=new HashMap<>();
        public static void resetCached(){
            cache.clear();
        }
        final boolean isPublic;
        final FilePosition declaredAt;
        final FilePosition endPos;

        final String baseName;
        final Type[] genericArgs;
        final GenericStruct genericVersion;
        final HashSet<String> declaredTypeFields;

        /**initialized types in this Tuple or null if this tuple is not yet initialized*/
        Type[] elements;
        /**Tokens in the body of this Struct*/
        ArrayList<Parser.Token> tokens;
        /**Context in which this Struct was declared*/
        Parser.StructContext context;

        StructField[] fields;
        String[] fieldNames;
        HashMap<String,Integer> indexByName;
        final Struct extended;

        static Struct create(String name,boolean isPublic,Struct extended,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             FilePosition declaredAt,FilePosition endPos) throws SyntaxError {
            return create(name, isPublic, extended, new Type[0],null,tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        static Struct create(String name,boolean isPublic,Struct extended,Type[] genericArgs,GenericStruct genericVersion,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             FilePosition declaredAt,FilePosition endPos) throws SyntaxError {
           return create(name, isPublic, extended, genericArgs,genericVersion,tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        static Struct create(String name, boolean isPublic, Struct extended, Type[] genericArgs,GenericStruct genericVersion,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             Mutability mutability, FilePosition declaredAt, FilePosition endPos) throws SyntaxError {
            CachedStruct cached=cache.get(declaredAt);
            Struct prev;
            prev=cached==null?null:cached.versions.get(Arrays.asList(genericArgs));
            if(prev!=null){
                return prev.setMutability(mutability);
            }
            prev=new Struct(namePrefix(name,genericArgs)+mutabilityPostfix(mutability), name, isPublic,
                    extended, genericArgs,genericVersion,null,null,tokens,context,mutability,declaredAt,endPos);
            if(cached==null){
                cached=new CachedStruct(new HashMap<>(),new ArrayList<>());
                cache.put(declaredAt,cached);
            }
            cached.versions.put(Arrays.asList(genericArgs),prev);
            prev.implementAll(cached.genericTraits);
            return prev;
        }
        private static Struct create(String name, boolean isPublic, Struct extended, Type[] genericArgs,GenericStruct genericVersion,
                                     StructField[] fields, Type[] types, Mutability mutability,
                                     FilePosition declaredAt, FilePosition endPos) throws SyntaxError {
            if(fields.length!=types.length){
                throw new IllegalArgumentException("fields and types have to have the same length");
            }
            CachedStruct cached=cache.get(declaredAt);
            Struct prev;
            prev=cached==null?null:cached.versions.get(Arrays.asList(genericArgs));
            if(prev!=null){
                return prev.setMutability(mutability);
            }
            prev = new Struct(namePrefix(name,genericArgs)+mutabilityPostfix(mutability), name, isPublic,
                    extended, genericArgs,genericVersion,types,fields,null, null,mutability, declaredAt,endPos);
            if(cached==null){
                cached=new CachedStruct(new HashMap<>(),new ArrayList<>());
                cache.put(declaredAt,cached);
            }
            cached.versions.put(Arrays.asList(genericArgs),prev);
            prev.implementAll(cached.genericTraits);
            return prev;
        }
        private void implementAll(ArrayList<GenericTraitImplementation> genericTraits) throws SyntaxError {
            IdentityHashMap<Type.GenericParameter, Type> update=new IdentityHashMap<>();
            boolean nonGeneric;
            for(Type.GenericTraitImplementation trait:genericTraits){
                update.clear();
                nonGeneric=true;
                for(int i=0;i<trait.params().length;i++){
                    update.put(trait.params()[i],genericArgs[i]);
                    if(genericArgs[i] instanceof Type.GenericParameter){
                        nonGeneric=false;
                    }
                }
                Value.Procedure[] updated = new Value.Procedure[trait.values().length];
                for (int i = 0; i < trait.values().length; i++) {
                    updated[i] = (Value.Procedure) trait.values[i].replaceGenerics(update);
                }
                implementTrait(trait.trait().replaceGenerics(update), updated, trait.implementedAt());
                if(nonGeneric){
                    for (Value.Procedure callable : updated) {
                        Parser.typeCheckProcedure(callable, trait.globalConstants,
                                trait.variables,trait.ioContext);
                    }
                }
            }
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
                       Type[] genArgs,GenericStruct genericVersion, Type[] elements, StructField[] fields,
                       ArrayList<Parser.Token> tokens, Parser.StructContext context,
                       Mutability mutability, FilePosition declaredAt,FilePosition endPos) {
            super(name, false,mutability);
            if((elements==null)==(tokens==null)){
                throw new RuntimeException("exactly one of elements and tokens should be non-null");
            }
            declaredTypeFields=new HashSet<>();
            this.isPublic=isPublic;
            this.declaredAt=declaredAt;
            this.endPos=endPos;
            this.extended =extended;
            this.baseName=baseName;
            this.genericArgs=genArgs;
            this.genericVersion=genericVersion;

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
        private Struct(Struct src,Mutability mutability){
            super(namePrefix(src.baseName,src.genericArgs)+mutabilityPostfix(mutability),src,mutability);

            declaredTypeFields=src.declaredTypeFields;
            this.isPublic = src.isPublic;
            this.declaredAt = src.declaredAt;
            this.endPos = src.endPos;
            this.extended = src.extended==null?null:src.extended.setMutability(mutability);
            this.baseName = src.baseName;
            this.genericArgs = src.genericArgs;
            this.genericVersion = src.genericVersion;

            this.elements = src.elements;
            this.fields=src.fields;
            this.tokens = src.tokens;
            this.context = src.context;
            fieldNames=src.fieldNames;
            indexByName=src.indexByName;
        }

        @Override
        BaseType initBaseType() {
            if(!isTypeChecked())
                throw new RuntimeException("initBaseType() can only be called after type-checking");
            boolean immutable=true;
            for(StructField f:fields){
                if (f.mutable) {
                    immutable = false;
                    break;
                }
            }
            immutable|=Mutability.isEqual(mutability,Mutability.IMMUTABLE);
            return tupleBaseType(elements,immutable);
        }

        @Override
        TraitFieldPosition traitFieldId(String name) {
            TraitFieldPosition pos=super.traitFieldId(name);
            return pos!=null||extended==null?pos:extended.traitFieldId(name);
        }
        @Override
        Value.Procedure getTraitField(TraitFieldPosition id) {
            Value.Procedure res=super.getTraitField(id);
            return res!=null||extended==null?res:extended.getTraitField(id);
        }

        @Override
        void implementGenericTrait(Trait trait, GenericParameter[] params, Value.Procedure[] implementation,
                                   FilePosition implementedAt, HashMap<Parser.VariableId, Value> globalConstants,
                                   HashMap<Parser.VariableId, Parser.ValueInfo> variables,
                                   IOContext ioContext) throws SyntaxError {
            if(params.length!=genericArgs.length){
                throw new IllegalArgumentException("wrong number of generic parameters: "+params.length+
                        " generic traits of "+name+" have to have exactly "+genericArgs.length+
                        " generic parameter");
            }
            CachedStruct cached=cache.get(declaredAt);
            assert cached!=null;//this struct (or a clone with another mutability) should be an element of cached
            IdentityHashMap<Type.GenericParameter, Type> update=new IdentityHashMap<>();
            boolean nonGeneric;
            for(Type.Struct s:cached.versions.values()){
                nonGeneric=true;
                update.clear();
                for(int i=0;i<params.length;i++){
                    update.put(params[i],s.genericArgs[i]);
                    if(s.genericArgs[i] instanceof Type.GenericParameter){
                        nonGeneric=false;
                    }
                }
                Value.Procedure[] updated = new Value.Procedure[implementation.length];
                for (int i = 0; i < implementation.length; i++) {
                    updated[i] = (Value.Procedure) implementation[i].replaceGenerics(update);
                }
                s.implementTrait(trait.replaceGenerics(update), updated, implementedAt);
                if(nonGeneric){
                    for (Value.Procedure callable : updated) {
                        Parser.typeCheckProcedure(callable,globalConstants,variables, ioContext);
                    }
                }
            }
            cached.genericTraits.add(new Type.GenericTraitImplementation(trait,params,implementation,implementedAt,
                    globalConstants,variables,ioContext));
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
            for(Type t:withMutability()){//update elements of all mutabilities
                assert t instanceof Struct;
                if(((Struct)t).elements==null){
                    ((Struct) t).elements=elements;
                    ((Struct) t).initializeFields(fields);
                    ((Struct) t).context=null;
                    ((Struct) t).tokens.clear();//tokens are no longer necessary
                }
            }
            assert context==null;
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
        void forEachStruct(ThrowingConsumer<Struct,SyntaxError> action) throws SyntaxError {
            action.accept(this);
            super.forEachStruct(action);
        }

        @Override
        public Struct setMutability(Mutability newMutability) {
            return (Struct) super.setMutability(newMutability);
        }
        @Override
        Struct copyWithMutability(Mutability newMutability) {
            return new Struct(this,newMutability);
        }

        @Override
        public List<String> fields() {
            if(!isTypeChecked()){
                throw new RuntimeException("cannot call setMutability() on uninitialized struct");
            }
            return Arrays.asList(fieldNames);
        }

        @Override
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            if(elements!=null){
                for(Type t:elements){
                    unbound=t.recursiveUnboundGenerics(unbound);
                }
            }else{
                try{
                for(Map.Entry<String, Parser.Declareable> d:context.elements()){
                    if(d.getValue() instanceof GenericParameter){
                        unbound.add((GenericParameter) d.getValue());
                    }else if(d.getValue() instanceof Parser.Constant&&
                            ((Parser.Constant) d.getValue()).value.type==TYPE&&
                            ((Parser.Constant) d.getValue()).value.asType() instanceof GenericParameter){
                        unbound.add((GenericParameter)((Parser.Constant) d.getValue()).value.asType());
                    }
                }
                }catch (TypeError t){
                    throw new RuntimeException(t);
                }
            }
            return super.recursiveUnboundGenerics(unbound);
        }
        @Override
        Struct replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) throws SyntaxError {
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
                ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens);
                Parser.StructContext newContext=context.replaceGenerics(generics);
                return changed?create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                        newArgs,genericVersion,newTokens,newContext,mutability,declaredAt,endPos):this;
            }
            Type[] newElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed =true;
                }
            }
            return changed?create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                            newArgs,genericVersion,fields,newElements,mutability,declaredAt,endPos):this;
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            if(!(t instanceof Struct))
                return false;
            return ((Struct) t).declaredAt.equals(declaredAt)&&
                    Arrays.equals(genericArgs,((Struct) t).genericArgs);
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
                for(int i=0;i<genericArgs.length;i++){
                    if(!(genericArgs[i].canAssignTo(((Struct) t).genericArgs[i],bounds)&&
                            ((Struct) t).genericArgs[i].canAssignTo(genericArgs[i],bounds.swapped())))
                        return false;//addLater allow up/down-casting generic parameters (in specific conditions)
                }
                return true;
            }
            return super.canAssignTo(t, bounds);
        }
        @Override
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            if (hasTrait(t, bounds))
                return CastType.CONVERT;
            if(t instanceof Struct) {
                if(mutabilityIncompatible(t)){
                    return CastType.NONE;
                }
                if(!declaredAt.equals(((Struct) t).declaredAt)){
                    if(extended != null){
                        CastType ret=extended.canCastTo(t, bounds);
                        if(ret!=CastType.NONE)
                            return ret;
                    }
                    if(((Struct) t).extended != null){
                        CastType ret=canCastTo(((Struct) t).extended,bounds);
                        if(ret!=CastType.NONE)
                            return CastType.RESTRICT;
                    }
                    return CastType.NONE;
                }
                boolean restrict=false;
                for(int i=0;i<genericArgs.length;i++){
                    if(!(genericArgs[i].canAssignTo(((Struct) t).genericArgs[i],bounds))){
                        if(!(((Struct) t).genericArgs[i].canAssignTo(genericArgs[i],bounds.swapped())))
                            return CastType.NONE;
                        restrict=true;
                    }
                }
                return restrict?CastType.RESTRICT:CastType.ASSIGN;
            }
            return super.canCastTo0(t, bounds);
        }

        @Override
        public String name() {
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

    record TraitField(String name,Procedure procType,FilePosition declaredAt){}
    public static class Trait extends Type implements Parser.NamedDeclareable{
        final String baseName;
        final boolean isPublic;
        final FilePosition declaredAt;
        final FilePosition endPos;
        final Type[] genericArgs;
        final GenericParameter[] genericParameters;
        final Trait[] extended;

        /**Tokens in the body of this Trait*/
        ArrayList<Parser.Token> tokens;
        /**Context in which this trait was declared*/
        Parser.TraitContext context;
        /**fields of this trait*/
        TraitField[] traitFields;

        //TODO caching
        static Trait create(String baseName,boolean isPublic,Trait[] extended,GenericParameter[] params,
                            ArrayList<Parser.Token> tokens, Parser.TraitContext context,
                            FilePosition declaredAt, FilePosition endPos){
            return create(baseName,isPublic,extended,params,params,tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        private static Trait create(String baseName,boolean isPublic,Trait[] extended,Type[] genericArgs,GenericParameter[] params,
                            ArrayList<Parser.Token> tokens, Parser.TraitContext context,Mutability mutability,
                            FilePosition declaredAt, FilePosition endPos){
            return new Trait(baseName,Struct.namePrefix(baseName,genericArgs)+mutabilityPostfix(mutability),isPublic,
                    extended,genericArgs, params, null, tokens,context, mutability, declaredAt, endPos);
        }
        private static Trait create(String baseName,boolean isPublic,Trait[] extended,Type[] genericArgs,GenericParameter[] params,
                            TraitField[] traitFields,Mutability mutability,
                            FilePosition declaredAt, FilePosition endPos){
            return new Trait(baseName,Struct.namePrefix(baseName,genericArgs)+mutabilityPostfix(mutability),isPublic,
                    extended,genericArgs, params, traitFields,null,null, mutability, declaredAt, endPos);
        }

        private Trait(String baseName, String name, boolean isPublic, Trait[] extended,
                      Type[] genericArgs, GenericParameter[] genericParameters, TraitField[] traitFields,
                      ArrayList<Parser.Token> tokens, Parser.TraitContext context, Mutability mutability,
                      FilePosition declaredAt, FilePosition endPos) {
            super(name,null,false,mutability, false);
            this.extended = extended;
            assert (traitFields==null)!=(tokens==null);
            this.baseName = baseName;
            this.isPublic = isPublic;
            this.declaredAt = declaredAt;
            this.endPos = endPos;
            this.genericArgs=genericArgs;
            this.genericParameters = genericParameters;

            this.traitFields=traitFields;
            this.tokens = tokens;
            this.context = context;
        }
        private Trait(Trait src,Mutability newMutability){
            super(Struct.namePrefix(src.baseName,src.genericArgs)+mutabilityPostfix(newMutability),
                    null,src,newMutability);
            baseName=src.baseName;
            isPublic=src.isPublic;
            extended=src.extended;
            genericParameters=src.genericParameters;
            genericArgs=src.genericArgs;
            declaredAt=src.declaredAt;
            endPos=src.endPos;

            traitFields=src.traitFields;
            tokens=src.tokens;
            context=src.context;
        }

        @Override
        BaseType initBaseType() {
            //TODO handle base-type for traits
            return BaseType.StackValue.PTR;
        }

        TraitField[] withExtended(TraitField[] declaredFields){
            ArrayList<TraitField> fields=new ArrayList<>();
            for(Trait t:extended){
                fields.addAll(Arrays.asList(t.traitFields));
            }
            fields.addAll(Arrays.asList(declaredFields));
            return fields.toArray(TraitField[]::new);
        }
        boolean isTypeChecked(){
            return traitFields!=null;
        }
        void setTraitFields(TraitField[] fields){
            for(Type t:withMutability()){//change for all mutabilities
                assert t instanceof Trait;
                ((Trait)t).traitFields=withExtended(fields);
                ((Trait)t).tokens=null;
                ((Trait)t).context=null;
            }
            assert context==null;
        }
        int fieldIdByName(String name){
            assert traitFields!=null;
            for(int i=0;i<traitFields.length;i++){
                if(traitFields[i].name.equals(name))
                    return i;
            }
            return -1;
        }
        public static TraitFieldPosition rootVersion(TraitFieldPosition itrNext) {
            Trait trait = itrNext.trait;
            int off = itrNext.offset;
            Trait[] extended = trait.extended;
            if(extended.length==0)
                return itrNext;
            for(int i = 0; i< extended.length&&off>=0; i++){
                if(off< extended[i].traitFields.length){
                    trait=extended[i];
                    extended=trait.extended;
                    i=0;
                }else{
                    off-= extended[i].traitFields.length;
                }
            }
            return new TraitFieldPosition(trait,off);
        }

        @Override
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            if(traitFields!=null){
                for(TraitField t:traitFields){
                    unbound=t.procType.recursiveUnboundGenerics(unbound);
                }
            }else{
                try{
                    for(Map.Entry<String, Parser.Declareable> d:context.elements()){
                        if(d.getValue() instanceof GenericParameter){
                            unbound.add((GenericParameter) d.getValue());
                        }else if(d.getValue() instanceof Parser.Constant&&
                                ((Parser.Constant) d.getValue()).value.type==TYPE&&
                                ((Parser.Constant) d.getValue()).value.asType() instanceof GenericParameter){
                            unbound.add((GenericParameter)((Parser.Constant) d.getValue()).value.asType());
                        }
                    }
                }catch (TypeError t){
                    throw new RuntimeException(t);
                }
            }
            return super.recursiveUnboundGenerics(unbound);
        }
        Trait withArgs(Type[] args) throws SyntaxError {
            IdentityHashMap<GenericParameter,Type> replace=new IdentityHashMap<>();
            for(int i=0;i<args.length;i++){
                replace.put(genericParameters[i],args[i]);
            }
            return replaceGenerics(replace);
        }

        @Override
        public List<Type> genericArguments() {
            return Arrays.asList(genericArgs);
        }
        @Override
        public Trait replaceGenerics(IdentityHashMap<GenericParameter,Type> replace) throws SyntaxError {
            boolean changed=false;
            Type[] newArgs;
            if(replace.size()>0){
                newArgs=new Type[this.genericArgs.length];
                for(int i=0;i<this.genericArgs.length;i++){
                    newArgs[i]=this.genericArgs[i].replaceGenerics(replace);
                    if(newArgs[i]!=this.genericArgs[i]){
                        changed=true;
                    }
                }
            }else{
                newArgs=genericArgs;
            }
            Trait[] newExtended=new Trait[extended.length];
            for(int i=0;i<extended.length;i++){
                newExtended[i]=extended[i].replaceGenerics(replace);
            }
            ArrayList<GenericParameter> newParams=new ArrayList<>();
            for(GenericParameter t:genericParameters){
                if(!replace.containsKey(t)){
                    newParams.add(t);
                }
            }
            if(traitFields==null){
                ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens);
                Parser.TraitContext newContext=context.replaceGenerics(replace);
                return changed?create(baseName, isPublic,newExtended,newArgs,newParams.toArray(GenericParameter[]::new),
                        newTokens,newContext,mutability,declaredAt,endPos):this;
            }
            TraitField[] newFields=new TraitField[traitFields.length];
            for(int i=0;i<traitFields.length;i++){
                newFields[i]=new TraitField(traitFields[i].name,traitFields[i].procType.replaceGenerics(replace),
                        traitFields[i].declaredAt);
                if(newFields[i]!=traitFields[i]){
                    changed =true;
                }
            }
            return changed?create(baseName, isPublic,newExtended,newArgs,newParams.toArray(GenericParameter[]::new),
                    newFields,mutability,declaredAt,endPos):this;
        }

        @Override
        public Trait setMutability(Mutability newMutability) {
            return (Trait) super.setMutability(newMutability);
        }

        @Override
        Type copyWithMutability(Mutability newMutability) {
            return new Trait(this,newMutability);
        }

        @Override
        protected boolean canAssignTo(Type t, BoundMaps bounds) {
            if(t instanceof Trait){
                if(mutabilityIncompatible(t))
                    return false;
                if(!declaredAt.equals(((Trait) t).declaredAt))
                    return false;
                for(int i=0;i<genericArgs.length;i++){
                    if(!(genericArgs[i].canAssignTo(((Trait) t).genericArgs[i],bounds)&&
                            ((Trait) t).genericArgs[i].canAssignTo(genericArgs[i],bounds.swapped())))
                        return false;
                }
                return true;
            }else{
                return super.canAssignTo(t,bounds);
            }
        }

        @Override
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            if(hasTrait(t, bounds))
                return CastType.CONVERT;
            if(t.hasTrait(this,bounds.swapped()))
                return CastType.RESTRICT;
            if(t instanceof Trait) {
                if(mutabilityIncompatible(t)){
                    return CastType.NONE;
                }
                if(!declaredAt.equals(((Trait) t).declaredAt)){
                    for(Trait ext:extended){
                        BoundMaps tmp = bounds.copy();
                        CastType castType = ext.canCastTo(t, tmp);
                        if(castType!=CastType.NONE){
                            bounds.l.putAll(tmp.l);
                            bounds.r.putAll(tmp.r);
                            return castType;
                        }
                    }
                    for(Trait ext:((Trait) t).extended){
                        BoundMaps tmp = bounds.copy();
                        CastType castType = canCastTo(ext,tmp);
                        if(castType!=CastType.NONE) {
                            bounds.l.putAll(tmp.l);
                            bounds.r.putAll(tmp.r);
                            return CastType.RESTRICT;
                        }
                    }
                    return CastType.NONE;
                }
                boolean restrict=false;
                for(int i=0;i<genericArgs.length;i++){
                    if(!(genericArgs[i].canAssignTo(((Trait)t).genericArgs[i],bounds))){
                        if(!(((Trait) t).genericArgs[i].canAssignTo(genericArgs[i],bounds.swapped())))
                            return CastType.NONE;
                        restrict=true;
                    }
                }
                return restrict?CastType.RESTRICT:CastType.ASSIGN;
            }
            return super.canCastTo0(t, bounds);
        }

        @Override
        protected boolean equals(Type t, IdentityHashMap<GenericParameter, GenericParameter> generics) {
            return t instanceof Trait&&((Trait) t).declaredAt.equals(declaredAt)&&
                    Arrays.equals(genericArgs,((Trait) t).genericArgs);
        }
        @Override
        public int hashCode() {
            return declaredAt.hashCode();
        }

        @Override
        public Parser.DeclareableType declarableType() {
            return Parser.DeclareableType.TRAIT;
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
            return baseName;
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
            super(name,BaseType.Composite.PROC_POINTER, false, true);
            this.inTypes=inTypes;
            this.outTypes=outTypes;
            this.declaredAt = declaredAt;
        }

        @Override
        FilePosition declaredAt() {
            return declaredAt;
        }

        @Override
        void forEachStruct(ThrowingConsumer<Struct,SyntaxError> action) throws SyntaxError {
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
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            for(Type t:inTypes){
                unbound=t.recursiveUnboundGenerics(unbound);
            }
            for(Type t:outTypes){
                unbound=t.recursiveUnboundGenerics(unbound);
            }
            return super.recursiveUnboundGenerics(unbound);
        }
        @Override
        Procedure replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) throws SyntaxError {
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
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            unbound=super.recursiveUnboundGenerics(unbound);
            //remove bound generics
            for(GenericParameter g:explicitGenerics){
                unbound.remove(g);
            }
            for(GenericParameter g:implicitGenerics){
                unbound.remove(g);
            }
            return unbound;
        }

        @Override
        Procedure replaceGenerics(IdentityHashMap<GenericParameter,Type> generics) throws SyntaxError {
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
            super(name,BaseType.Primitive.getInt(32,true),true, true);
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
        public CastType canCastTo(Type t, BoundMaps bounds) {
            return (t instanceof IntType&&(((IntType) t).bits>=32||
                    (entryNames.length>(1<<(((IntType) t).bits-(((IntType) t).signed?1:0))))))
                    ?CastType.CAST:super.canCastTo(t, bounds);
        }

        @Override
        public String name() {
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
            super(name,BaseType.StackValue.PTR,false, true);
            this.jClass = jClass;
        }
        @Override
        public String name() {
            return name;
        }
    }

    public static class GenericParameter extends Type implements Parser.Declareable {
        final String label;
        final int id;
        final boolean isImplicit;
        final FilePosition declaredAt;

        public GenericParameter(String label, int id, boolean isImplicit, FilePosition pos) {
            super("'"+id,null,false, false);
            this.label = label;
            this.id=id;
            this.isImplicit = isImplicit;
            this.declaredAt=pos;
        }

        @Override
        BaseType baseType() {
            throw new UnsupportedOperationException("baseType() is not supported for generic parameters");
        }

        @Override
        boolean isValid() {
            return false;
        }

        @Override
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            unbound.add(this);
            return super.recursiveUnboundGenerics(unbound);
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
                        Optional<Type> newMin = commonSuperType(mBound.min, tBound.min, false,true);
                        if(newMin.isEmpty()){
                            return false;
                        }
                        GenericBound commonBounds=new GenericBound(newMin.get(),newMax);
                        bounds.r.put((GenericParameter) t,commonBounds);
                        bounds.r.put((GenericParameter) t,commonBounds);
                    }
                }else{
                    if(mBound==null){
                        throw new RuntimeException("comparison of two unbound generics");
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

        private UnionType(String name,Type[] elements) {
            super(name,null, false, Arrays.stream(elements).allMatch(e->e.primitive));
            this.elements = elements;
        }

        @Override
        BaseType initBaseType() {
            //TODO union base-type
            return BaseType.StackValue.PTR;
        }

        @Override
        void forEachStruct(ThrowingConsumer<Struct,SyntaxError> action) throws SyntaxError {
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
        Set<GenericParameter> recursiveUnboundGenerics(Set<GenericParameter> unbound) {
            for(Type t:elements){
                unbound=t.recursiveUnboundGenerics(unbound);
            }
            return super.recursiveUnboundGenerics(unbound);
        }
        @Override
        Type replaceGenerics(IdentityHashMap<GenericParameter, Type> generics) throws SyntaxError {
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
            if(super.canAssignTo(t, bounds))
                return true;
            if(t instanceof GenericParameter)
                return false;
            for(Type e:elements){
                if(!e.canAssignTo(t,bounds))
                    return false;
            }
            return true;
        }

        @Override
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof GenericParameter){
                return super.canCastTo(t, bounds);
            }
            boolean hasMatch=false;
            CastType matchType=CastType.ASSIGN;
            for(Type e:elements){
                CastType castType = e.canCastTo(t, bounds);
                switch (castType){
                    case ASSIGN ->
                        hasMatch=true;
                    case CONVERT -> {
                        hasMatch=true;
                        if (matchType == CastType.ASSIGN)
                            matchType = CastType.CONVERT;
                    }
                    case CAST -> {
                        hasMatch=true;
                        if(matchType!=CastType.RESTRICT)
                            matchType = CastType.CAST;
                    }
                    case RESTRICT -> {
                        hasMatch=true;
                        matchType = CastType.RESTRICT;
                    }
                    case NONE -> //at least one is no match -> restrict
                        matchType = CastType.RESTRICT;
                }
            }
            return hasMatch?matchType:canCastTo0(t, bounds);
        }
    }

    static class OverloadedProcedurePointer extends Type{
        final OverloadedProcedure proc;
        final Type[] genArgs;
        final int tokenPos;
        final FilePosition pushedAt;

        OverloadedProcedurePointer(OverloadedProcedure proc, Type[] genArgs, int tokenPos, FilePosition pushedAt) {
            super(proc.name+" .type",BaseType.Composite.PROC_POINTER, false, true);
            this.proc = proc;
            this.genArgs = genArgs;
            this.tokenPos = tokenPos;
            this.pushedAt=pushedAt;
        }
        OverloadedProcedurePointer(GenericProcedure proc, Type[] genArgs, int tokenPos, FilePosition pushedAt) {
            super(proc.name()+" .type",BaseType.Composite.PROC_POINTER, false, true);
            this.proc = new OverloadedProcedure(proc);
            this.genArgs = genArgs;
            this.tokenPos = tokenPos;
            this.pushedAt=pushedAt;
        }

        @Override
        boolean isValid() {
            return false;
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
        protected CastType canCastTo(Type t, BoundMaps bounds) {
            return equals(t)?CastType.ASSIGN:CastType.NONE;
        }
    }
}