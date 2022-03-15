package bsoelch.concat;

public enum OperatorType {
    //operations with results that are directly used in type-checking
    LIST_OF,OPTIONAL_OF,EMPTY_OPTIONAL,
    //"internal fields"
    TYPE_OF,
    CONTENT,IN_TYPES,OUT_TYPES,TYPE_NAME,TYPE_FIELDS,
    IS_ENUM,IS_LIST,IS_PROC,IS_OPTIONAL,IS_TUPLE,
    LENGTH,
    HAS_VALUE,
}
