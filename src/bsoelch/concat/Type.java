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
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{TYPE},"content",
                    (values) ->
                            new Value[]{Value.ofType(values[0].asType().content())}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"genericArguments",
                (values) ->
                        new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().genericArguments().stream().map(Value::ofType).toArray(Value[]::new))}),
                    declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"inTypes",
                (values) ->
                        new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().inTypes().stream().map(Value::ofType).toArray(Value[]::new))}),
                    declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(TYPE)},"outTypes",
                (values) ->
                        new Value[]{Value.createArray(Type.arrayOf(Type.TYPE),
                            values[0].asType().outTypes().stream().map(Value::ofType).toArray(Value[]::new))}),
                    declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{RAW_STRING()},"name",
                (values) -> new Value[]{Value.ofString(values[0].asType().typeName(),false)}),
                    declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{arrayOf(RAW_STRING())},"fieldNames",
                (values) -> new Value[]{Value.createArray(Type.arrayOf(Type.RAW_STRING()),values[0].asType().fields()
                            .stream().map(s->Value.ofString(s,false)).toArray(Value[]::new))}),
                    declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{ANY,UINT,TYPE},new Type[]{ANY},"getField",
                (values) ->  {
                    Value instance = values[0];
                    long index     = values[1].asLong();
                    Type type      = values[2].asType();
                    if(!instance.type.canAssignTo(type)){
                        throw new TypeError("cannot assign "+instance.type+" to "+type);
                    }
                    return new Value[]{instance.getField(index)};
                }), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isEnum",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Enum)}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isArray",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isArray())}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMemory",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isMemory())}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isProc",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Type.Procedure)}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isOptional",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isOptional())}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isTuple",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Tuple)}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isStruct",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof Struct)}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isUnion",
                (values) -> new Value[]{Value.ofBool(values[0].asType() instanceof UnionType)}), declaredAt());

            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMutable",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isMutable())}), declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{TYPE},new Type[]{BOOL},"isMaybeMutable",
                (values) -> new Value[]{Value.ofBool(values[0].asType().isMaybeMutable())}), declaredAt());
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
    private final HashMap<String, Integer> typeFieldNames;
    /**"pseudo-fields" for values of this type,
     * a pseudo field is a procedure that takes this type as last parameter*/
    private final HashMap<String, Integer> pseudoFieldNames;
    private final ArrayList<Value> typeFields;
    private boolean typeFieldsInitialized=false;
    private int nativeFieldCount=-1;

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
    private Type(String name, boolean switchable) {
        this(name,switchable,Mutability.DEFAULT);
    }
    private Type(String name, boolean switchable,Mutability mutability) {
        this.name = name;
        this.switchable = switchable;
        this.mutability=mutability;

        typeFieldNames = new HashMap<>();
        pseudoFieldNames = new HashMap<>();
        typeFields=new ArrayList<>();
        withMutability=new HashMap<>();
        withMutability.put(mutability,this);
    }
    private Type(String newName,Type src,Mutability newMutability){
        this.name = newName;
        this.switchable = src.switchable;
        this.mutability = newMutability;

        src.ensureFieldsInitialized();//ensure type fields are initialized
        typeFieldNames =src.typeFieldNames;
        typeFields=src.typeFields;
        typeFieldsInitialized=true;//type fields of copied type are already initialized
        nativeFieldCount=src.nativeFieldCount;//same number of native fields
        pseudoFieldNames = src.pseudoFieldNames;
        withMutability= src.withMutability;
        withMutability.put(newMutability,this);
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
            for(Type t:withMutability.values()){//update native field count for all mutabilities
                t.nativeFieldCount=typeFields.size();
            }
        }
    }
    void initTypeFields() throws SyntaxError {
        addPseudoField(new Value.InternalProcedure(new Type[]{this},new Type[]{TYPE},"type",
                (values) ->  new Value[]{Value.ofType(values[0].type)}),declaredAt());
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

    void addField(String name, Value fieldValue, FilePosition pos) throws SyntaxError {
        ensureFieldsInitialized();
        int id=typeFields.size();
        if(typeFieldNames.put(name,id)!=null){
            throw new SyntaxError(this.name+" already has a type field "+name+" ",pos);
        }
        typeFields.add(fieldValue);
    }
    void addPseudoField(Parser.Callable fieldValue, FilePosition declaredAt) throws SyntaxError {
        addPseudoField(fieldValue.name(),fieldValue,declaredAt);
    }
    void addPseudoField(String name,Parser.Callable fieldValue, FilePosition declaredAt) throws SyntaxError {
        ensureFieldsInitialized();
        Type[] in=fieldValue.type().inTypes;
        if(in.length==0||!canAssignTo(in[in.length-1].maybeMutable())){
            throw new SyntaxError(fieldValue.name()+" (declared at "+fieldValue.declaredAt()+") "+
                    "has an invalid signature for a pseudo-field of "+this+": "+Arrays.toString(in),declaredAt);
        }
        int id=typeFields.size();
        if(pseudoFieldNames.put(name,id)!=null){
            throw new SyntaxError(this.name+" already has a field "+name+" ",declaredAt);
        }
        typeFields.add((Value) fieldValue);
    }
    void inheritDeclaredFields(Type extended) {
        ensureFieldsInitialized();
        if(!extended.typeFieldsInitialized){
            throw new RuntimeException("extended type has to be initialized");
        }
        int initPos=typeFields.size();
        //addLater remember start position of inherited type fields
        for(int i=extended.nativeFieldCount;i<extended.typeFields.size();i++){
            typeFields.add(extended.typeFields.get(i));
        }
        //copy field names
        for(Map.Entry<String, Integer> e:extended.typeFieldNames.entrySet()){
            if(e.getValue()>=extended.nativeFieldCount){
                typeFieldNames.put(e.getKey(),e.getValue()- extended.nativeFieldCount+initPos);
            }
        }
        for(Map.Entry<String, Integer> e:extended.pseudoFieldNames.entrySet()){
            if(e.getValue()>=extended.nativeFieldCount){
                pseudoFieldNames.put(e.getKey(),e.getValue()- extended.nativeFieldCount+initPos);
            }
        }
    }
    /**@return true iff newValue cannot overwrite the type-field at index*/
    boolean overwriteTypeField(int index,Value newValue){
        Value prev=typeFields.get(index);
        if(!newValue.type.canAssignTo(prev.type)){
            return true;
        }
        typeFields.set(index,newValue);
        return false;
    }
    /**@return true iff newValue cannot overwrite the pseudo-field at index*/
    boolean overwritePseudoField(int index, Parser.Callable newValue){
        Procedure prevType=(Procedure) typeFields.get(index).type;
        Procedure newType = newValue.type();
        //check for correct type
        if(prevType.inTypes.length!= newType.inTypes.length||
                prevType.outTypes.length!= newType.outTypes.length)
            return true;
        for(int i=0;i<newType.inTypes.length-1;i++){//ignore last parameter (last parameter always has to be the container type)
            if(!prevType.inTypes[i].canAssignTo(newType.inTypes[i]))
                return true;
        }
        for(int i=0;i<newType.outTypes.length;i++){
            if(!newType.outTypes[i].canAssignTo(prevType.outTypes[i]))
                return true;
        }
        typeFields.set(index,(Value)newValue);
        return false;
    }


    List<Value> typeFields(){
        return typeFields;
    }
    int typeFieldId(String name){
        ensureFieldsInitialized();
        Integer id=typeFieldNames.get(name);
        return id==null?-1:id;
    }
    Value getTypeField(String name){
        ensureFieldsInitialized();
        Integer id=typeFieldNames.get(name);
        return id==null?null:typeFields.get(id);
    }
    Value getTypeField(int id){
        ensureFieldsInitialized();
        return typeFields.get(id);
    }
    private boolean pseudoFieldIncompatible(int id){
        Procedure t= (Procedure) typeFields.get(id).type;
        return !canAssignTo(t.inTypes[t.inTypes.length-1]);
    }
    int pseudoFieldId(String name){
        ensureFieldsInitialized();
        Integer id=pseudoFieldNames.get(name);
        return id==null||pseudoFieldIncompatible(id)?-1:id;
    }
    Parser.Callable getPseudoField(String name){
        ensureFieldsInitialized();
        Integer id=pseudoFieldNames.get(name);
        return id==null||pseudoFieldIncompatible(id)?null:(Parser.Callable)typeFields.get(id);
    }
    Parser.Callable getPseudoField(int id){
        ensureFieldsInitialized();
        return (Parser.Callable)typeFields.get(id);
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
    /**classes overwriting this method should create a new instance using the {@link #Type(String,Type,Mutability))} constructor*/
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
        private WrapperType(WrapperType src,Mutability mutability){
            super(updateChildMutability(src.contentType,mutability).name+" "+
                    src.wrapperName+mutabilityPostfix(mutability), src,mutability);
            this.wrapperName = src.wrapperName;
            this.contentType = src.contentType;
        }

        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();
            switch (wrapperName) {
                case OPTIONAL -> {
                    addPseudoField(new Value.InternalProcedure(new Type[]{this},new Type[]{BOOL}, "hasValue",
                            (values) -> new Value[]{Value.ofBool(values[0].hasValue())}), declaredAt());
                    //addLater static check if optional is nonempty
                    addPseudoField(new Value.InternalProcedure(new Type[]{this},new Type[]{contentType}, "value",
                            (values) -> new Value[]{values[0].unwrap()}), declaredAt());
                }
                case MEMORY -> {
                    addPseudoField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                            new Type[]{UINT}, "length", (values) ->
                            new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).length(), true)}), declaredAt());
                    addPseudoField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                            new Type[]{UINT}, "capacity", (values) ->
                            new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).capacity(), true)}), declaredAt());
                    addPseudoField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                            new Type[]{UINT}, "offset", (values) ->
                            new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).offset(), true)}), declaredAt());
                }
                case ARRAY -> addPseudoField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},
                        new Type[]{UINT}, "length", (values) ->
                        new Value[]{Value.ofInt(((Value.ArrayLike) values[0]).length(), true)}), declaredAt());
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
            return wrapperName.equals(OPTIONAL)?this:new WrapperType(this,newMutability);
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
        private TupleLike(String name, Type src, Mutability mutability) {
            super(name, src, mutability);
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
        private Tuple(Tuple src,Mutability mutability){
            super(src.name, src,mutability);
            this.elements=src.elements;
        }

        @Override
        void initTypeFields() throws SyntaxError {
            super.initTypeFields();
            addPseudoField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},new Type[]{UINT},"length",
                (values) -> //addLater? remember declaration position of tuples
                        new Value[]{Value.ofInt(values[0].length(),true)}),declaredAt());
            addPseudoField(new Value.InternalProcedure(new Type[]{this.maybeMutable()},new Type[]{arrayOf(ANY)},"elements",
                (values) ->  new Value[]{Value.createArray(arrayOf(ANY),values[0].getElements())}),declaredAt());
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
                             FilePosition declaredAt,FilePosition endPos){
            return create(name, isPublic, extended, new Type[0],tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        static Struct create(String name,boolean isPublic,Struct extended,Type[] genericArgs,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             FilePosition declaredAt,FilePosition endPos){
           return create(name, isPublic, extended, genericArgs, tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        static Struct create(String name, boolean isPublic, Struct extended, Type[] genericArgs,
                             ArrayList<Parser.Token> tokens, Parser.StructContext context,
                             Mutability mutability, FilePosition declaredAt, FilePosition endPos){
            StructId id=new StructId(declaredAt,genericArgs);
            Struct prev;
            prev=cached.get(id);
            if(prev!=null){
                return prev.setMutability(mutability);
            }
            prev=new Struct(namePrefix(name,genericArgs)+mutabilityPostfix(mutability), name, isPublic,
                    extended, genericArgs,null,null,tokens,context,mutability,declaredAt,endPos);
            cached.put(id,prev);
            return prev;
        }
        private static Struct create(String name, boolean isPublic, Struct extended, Type[] genericArgs,
                                     StructField[] fields, Type[] types, Mutability mutability,
                                     FilePosition declaredAt, FilePosition endPos) {
            if(fields.length!=types.length){
                throw new IllegalArgumentException("fields and types have to have the same length");
            }
            StructId id=new StructId(declaredAt,genericArgs);
            Struct prev;
            prev=cached.get(id);
            if(prev!=null){
                return prev.setMutability(mutability);
            }
            prev = new Struct(namePrefix(name,genericArgs)+mutabilityPostfix(mutability), name, isPublic,
                    extended, genericArgs, types,fields,null, null,mutability, declaredAt,endPos);
            cached.put(id,prev);
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
            declaredTypeFields=new HashSet<>();
            this.isPublic=isPublic;
            this.declaredAt=declaredAt;
            this.endPos=endPos;
            this.extended =extended;
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
        private Struct(Struct src,Mutability mutability){
            super(namePrefix(src.baseName,src.genericArgs)+mutabilityPostfix(mutability),src,mutability);

            declaredTypeFields=src.declaredTypeFields;
            this.isPublic = src.isPublic;
            this.declaredAt = src.declaredAt;
            this.endPos = src.endPos;
            this.extended = src.extended==null?null:src.extended.setMutability(mutability);
            this.baseName = src.baseName;
            this.genericArgs = src.genericArgs;

            this.elements = src.elements;
            this.fields=src.fields;
            this.tokens = src.tokens;
            this.context = src.context;
            fieldNames=src.fieldNames;
            indexByName=src.indexByName;
        }

        void declareTypeField(String name, Value fieldValue, FilePosition pos) throws SyntaxError {
            ensureFieldsInitialized();
            if(declaredTypeFields.contains(name)){
                throw new SyntaxError(baseName+" already has a field "+name,pos);
            }
            declaredTypeFields.add(name);
            int prevPos=typeFieldId(name);
            if(prevPos==-1){
                addField(name, fieldValue, pos);
                return;
            }
            if(overwriteTypeField(prevPos,fieldValue)){
                throw new SyntaxError("cannot overwrite type field "+name+": cannot assign "+
                        fieldValue.type+" to "+typeFields().get(prevPos).type,pos);
            }
        }
        void declarePseudoField(String name, Parser.Callable fieldValue, FilePosition declaredAt) throws SyntaxError {
            ensureFieldsInitialized();
            if(declaredTypeFields.contains(name)){
                throw new SyntaxError(baseName+" already has a field "+name,declaredAt);
            }
            declaredTypeFields.add(name);
            int prevPos=pseudoFieldId(name);
            if(prevPos==-1) {
                addPseudoField(name, fieldValue, declaredAt);
                return;
            }
            if(overwritePseudoField(prevPos,fieldValue)){
                throw new SyntaxError("cannot overwrite pseudo field "+name+": "+typeFields().get(prevPos).type
                        +" with "+fieldValue.type(),declaredAt);
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
                Parser.StructContext newContext=context.emptyCopy();
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
                    }else if(e.getValue() instanceof Parser.Constant c){
                        newContext.putElement(e.getKey(),
                                new Parser.Constant(e.getKey(),false,c.value.replaceGenerics(generics),
                                        e.getValue().declaredAt()));
                    }
                }
                return changed?create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                        newArgs,newTokens,newContext,mutability,declaredAt,endPos):this;
            }
            Type[] newElements=new Type[elements.length];
            for(int i=0;i<elements.length;i++){
                newElements[i]=elements[i].replaceGenerics(generics);
                if(newElements[i]!=elements[i]){
                    changed =true;
                }
            }
            return changed?create(baseName, isPublic,extended==null?null:extended.replaceGenerics(generics),
                            newArgs,fields,newElements,mutability,declaredAt,endPos):this;
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
        protected boolean canCastTo(Type t, BoundMaps bounds) {
            if(t instanceof Struct) {
                if(mutabilityIncompatible(t)){
                    return false;
                }
                if(!declaredAt.equals(((Struct) t).declaredAt)){
                    return (extended != null && extended.canAssignTo(t, bounds))||
                            (((Struct) t).extended != null && canCastTo(((Struct) t).extended,bounds));
                }
                for(int i=0;i<genericArgs.length;i++){
                    if(!(genericArgs[i].canAssignTo(((Struct) t).genericArgs[i],bounds)&&
                            ((Struct) t).genericArgs[i].canAssignTo(genericArgs[i],bounds.swapped())))
                        return false;
                }
                return true;
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

    record TraitField(String name,Procedure procType,FilePosition declaredAt){}
    public static class Trait extends Type implements Parser.NamedDeclareable{
        final String baseName;
        final boolean isPublic;
        final FilePosition declaredAt;
        final FilePosition endPos;
        final Type[] genericArgs;
        final GenericParameter[] genericParameters;

        /**Tokens in the body of this Trait*/
        ArrayList<Parser.Token> tokens;
        /**Context in which this trait was declared*/
        Parser.TraitContext context;
        /**fields of this trait*/
        TraitField[] traitFields;

        //TODO caching
        static Trait create(String baseName,boolean isPublic,
                            ArrayList<Parser.Token> tokens, Parser.TraitContext context,
                            FilePosition declaredAt, FilePosition endPos){
            return new Trait(baseName,baseName,isPublic,new Type[0],new GenericParameter[0],
                   null, tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        static Trait create(String baseName,boolean isPublic,GenericParameter[] params,
                            ArrayList<Parser.Token> tokens, Parser.TraitContext context,
                            FilePosition declaredAt, FilePosition endPos){
            return new Trait(baseName,baseName,isPublic,params,params,null,tokens,context,Mutability.DEFAULT,declaredAt,endPos);
        }
        static Trait create(String baseName,boolean isPublic,Type[] genericArgs,GenericParameter[] params,
                            ArrayList<Parser.Token> tokens, Parser.TraitContext context,Mutability mutability,
                            FilePosition declaredAt, FilePosition endPos){
            return new Trait(baseName,Struct.namePrefix(baseName,genericArgs)+mutabilityPostfix(mutability),isPublic,
                    params, params,null, tokens,context, mutability, declaredAt, endPos);
        }
        static Trait create(String baseName,boolean isPublic,Type[] genericArgs,GenericParameter[] params,
                            TraitField[] traitFields,Mutability mutability,
                            FilePosition declaredAt, FilePosition endPos){
            return new Trait(baseName,Struct.namePrefix(baseName,genericArgs)+mutabilityPostfix(mutability),isPublic,
                    params, params, traitFields,null,null, mutability, declaredAt, endPos);
        }

        private Trait(String baseName,String name,boolean isPublic, Type[] genericArgs,GenericParameter[] genericParameters,
                      TraitField[] traitFields,ArrayList<Parser.Token> tokens, Parser.TraitContext context, Mutability mutability,
                      FilePosition declaredAt, FilePosition endPos) {
            super(name,false,mutability);
            assert (traitFields==null)!=(tokens==null);
            this.baseName = baseName;
            this.isPublic = isPublic;
            this.declaredAt = declaredAt;
            this.endPos = endPos;
            this.genericArgs=genericArgs;
            this.genericParameters = genericParameters;
            this.tokens = tokens;
            this.context = context;
        }
        private Trait(Trait src,Mutability newMutability){
            super(Struct.namePrefix(src.baseName,src.genericArgs)+mutabilityPostfix(newMutability),
                    src,newMutability);
            baseName=src.baseName;
            isPublic=src.isPublic;
            genericParameters=src.genericParameters;
            genericArgs=src.genericArgs;
            declaredAt=src.declaredAt;
            endPos=src.endPos;

            tokens=src.tokens;
            context=src.context;
            traitFields=src.traitFields;
        }

        boolean isTypeChecked(){
            return traitFields!=null;
        }
        void setTraitFields(TraitField[] fields){
            for(Type t:withMutability()){//change for all mutabilities
                assert t instanceof Trait;
                ((Trait)t).traitFields=fields;
                ((Trait)t).tokens=null;
                ((Trait)t).context=null;
            }
            assert context==null;
        }

        Trait withArgs(Type[] args){
            IdentityHashMap<GenericParameter,Type> replace=new IdentityHashMap<>();
            for(int i=0;i<args.length;i++){
                replace.put(genericParameters[i],args[i]);
            }
            return replaceGenerics(replace);
        }
        @Override
        public Trait replaceGenerics(IdentityHashMap<GenericParameter,Type> replace){
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
            ArrayList<GenericParameter> newParams=new ArrayList<>();
            for(GenericParameter t:genericParameters){
                if(!replace.containsKey(t)){
                    newParams.add(t);
                }
            }
            if(traitFields==null){
                ArrayList<Parser.Token> newTokens=new ArrayList<>(tokens.size());
                for(Parser.Token t:tokens){
                    Parser.Token newToken = t.replaceGenerics(replace);
                    newTokens.add(newToken);
                    if(newToken!=t)
                        changed=true;
                }
                Parser.TraitContext newContext=context.emptyCopy();
                for(Map.Entry<String, Parser.Declareable> e:context.elements()){
                    if(e.getValue() instanceof GenericParameter){
                        Type rType=replace.get((GenericParameter)e.getValue());
                        if(rType!=null){//replace generics in constants
                            if(rType instanceof Type.GenericParameter){
                                newContext.putElement(e.getKey(),(Type.GenericParameter) rType);
                            }else{
                                newContext.putElement(e.getKey(),
                                        new Parser.Constant(e.getKey(),false,Value.ofType(rType),e.getValue().declaredAt()));
                            }
                            changed=true;
                        }
                    }else if(e.getValue() instanceof Parser.Constant c){
                        newContext.putElement(e.getKey(),
                                new Parser.Constant(e.getKey(),false,c.value.replaceGenerics(replace),
                                        e.getValue().declaredAt()));
                    }
                }
                return changed?create(baseName, isPublic,newArgs,newParams.toArray(GenericParameter[]::new),
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
            return changed?create(baseName, isPublic,newArgs,newParams.toArray(GenericParameter[]::new),
                    newFields,mutability,declaredAt,endPos):this;
        }

        @Override
        public Trait setMutability(Mutability newMutability) {
            return (Trait) super.setMutability(newMutability);
        }

        @Override
        Type copyWithMutability(Mutability newMutability) {
            return new Trait(this,mutability);
        }

        //TODO overwrite type methods

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
            return name;
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