package bsoelch.concat;

public enum OperatorType {
    DUP,DROP,SWAP,OVER,CLONE,DEEP_CLONE,
    NEGATE,PLUS,MINUS,
    INVERT,MULT,DIV,MOD,
    POW,
    NOT,FLIP,AND,OR,XOR,
    LOG,FLOOR,CEIL,
    LSHIFT,SLSHIFT,RSHIFT,SRSHIFT,
    GT,GE,EQ,NE,LE,LT,//value equality
    REF_EQ,REF_NE,//reference equality
    CAST,TYPE_OF,
    LIST_OF,ITR_OF,UNWRAP,
    NEW_LIST,LENGTH,ENSURE_CAP,
    GET_INDEX,SET_INDEX,GET_SLICE,SET_SLICE,
    PUSH_FIRST,PUSH_ALL_FIRST,PUSH_LAST,PUSH_ALL_LAST,
    TUPLE,NEW,
    ITR_START,ITR_END,ITR_NEXT,ITR_PREV,
    CALL,
    IMPORT,CONST_IMPORT,
    BYTES_LE,BYTES_BE,BYTES_AS_INT_LE,BYTES_AS_INT_BE,BYTES_AS_FLOAT_LE,BYTES_AS_FLOAT_BE,
    OPEN,CLOSE,
    STREAM_OF,REVERSED_STREAM,
    STREAM_STATE,SIZE,POS,
    READ,READ_MULTIPLE,SKIP,SEEK,SEEK_END,WRITE,WRITE_MULTIPLE
    //TODO cToB, bToC converting between char and byte (list, stream, itr)
}
