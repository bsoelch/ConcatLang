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
    CAST,TYPE_OF,
    LIST_OF, CONTENT,
    NEW_LIST,LENGTH,ENSURE_CAP,FILL,
    GET_INDEX,SET_INDEX,GET_SLICE,SET_SLICE,
    PUSH_FIRST,PUSH_ALL_FIRST,PUSH_LAST,PUSH_ALL_LAST,
    NEW,
    NEW_PROC_TYPE,VAR_ARG,IS_VAR_ARG,IS_ENUM,
    OPTIONAL_OF,WRAP,UNWRAP,HAS_VALUE,EMPTY_OPTIONAL,
    CLEAR,
    INT_AS_FLOAT,FLOAT_AS_INT,
}
