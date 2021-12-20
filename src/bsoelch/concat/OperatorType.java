package bsoelch.concat;

public enum OperatorType {
    NEGATE,PLUS,MINUS,
    INVERT,MULT,DIV,MOD,
    POW,
    NOT,FLIP,AND,OR,XOR,
    LSHIFT,SLSHIFT,RSHIFT,SRSHIFT,
    GT,GE,EQ,NE,LE,LT,
    CAST,TYPE_OF,
    LIST_OF,ITR_OF,UNWRAP,
    NEW_LIST,LENGTH, GET_INDEX,SET_INDEX,GET_SLICE,SET_SLICE,
    PUSH_FIRST,CONCAT,PUSH_LAST,
    ITR_START,ITR_END,ITR_NEXT,ITR_PREV,
    CALL//addLater possibility to import element into current scope
}
