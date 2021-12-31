package bsoelch.concat;

public enum OperatorType {
    DUP,DROP,SWAP,OVER, //<off> dup, <off> swap, <count> drop
    REF_ID,CLONE,DEEP_CLONE,
    NEGATE,PLUS,MINUS,
    INVERT, MULTIPLY,DIV,MOD, UNSIGNED_DIV,UNSIGNED_MOD,
    POW,
    NOT,FLIP,AND,OR,XOR,
    LOG,FLOOR,CEIL,
    LSHIFT,SLSHIFT,RSHIFT,SRSHIFT,
    GT,GE,EQ,NE,LE,LT,//value equality
    REF_EQ,REF_NE,//reference equality
    CAST,TYPE_OF,
    LIST_OF, CONTENT,
    NEW_LIST,LENGTH,ENSURE_CAP,
    GET_INDEX,SET_INDEX,GET_SLICE,SET_SLICE,
    PUSH_FIRST,PUSH_ALL_FIRST,PUSH_LAST,PUSH_ALL_LAST,
    TUPLE,NEW,
    CALL,
    IMPORT,CONST_IMPORT,
    BYTES_LE,BYTES_BE,BYTES_AS_INT_LE,BYTES_AS_INT_BE,BYTES_AS_FLOAT_LE,BYTES_AS_FLOAT_BE,
    OPEN,CLOSE,SIZE,POS,SEEK,SEEK_END,READ,WRITE,TRUNCATE
}
