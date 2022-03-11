package bsoelch.concat;

public enum OperatorType {
    REF_ID,CLONE,DEEP_CLONE,
    NEGATE,PLUS,MINUS,
    INVERT, MULTIPLY,DIV,MOD,
    POW,
    NOT,FLIP,AND,OR,XOR,
    LSHIFT,RSHIFT,
    GT,GE,EQ,NE,LE,LT,//value equality
    REF_EQ,REF_NE,//reference equality
    TYPE_OF,
    LIST_OF, CONTENT,IN_TYPES,OUT_TYPES,TYPE_NAME,TYPE_FIELDS,
    LENGTH,ENSURE_CAP,FILL,
    GET_INDEX,SET_INDEX,GET_SLICE,SET_SLICE,
    PUSH_FIRST,PUSH_ALL_FIRST,PUSH_LAST,PUSH_ALL_LAST,
    IS_ENUM,IS_LIST,IS_PROC,IS_OPTIONAL,IS_TUPLE,
    OPTIONAL_OF,WRAP,HAS_VALUE,EMPTY_OPTIONAL,
    CLEAR,
}
