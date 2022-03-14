package bsoelch.concat;

public enum OperatorType {
    //operations on numbers (need union{int,uint} to be transformed into internal procedures)
    PLUS,MINUS,
    MULTIPLY,DIV,MOD,
    POW,
    NOT,FLIP,AND,OR,XOR,
    LSHIFT,RSHIFT,
    GT,GE,LE,LT,
    //operations with results that are directly used in type-checking
    LIST_OF,OPTIONAL_OF,EMPTY_OPTIONAL,
    //"internal fields"
    TYPE_OF,
    CONTENT,IN_TYPES,OUT_TYPES,TYPE_NAME,TYPE_FIELDS,
    IS_ENUM,IS_LIST,IS_PROC,IS_OPTIONAL,IS_TUPLE,
    LENGTH,

    GET_INDEX,SET_INDEX,GET_SLICE,SET_SLICE,
    PUSH_FIRST,PUSH_ALL_FIRST,PUSH_LAST,PUSH_ALL_LAST,
    WRAP,HAS_VALUE,
}
