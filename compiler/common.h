//common root-file for compiler and all native procedures
//can be assumed to be included in all native procedures
#include "stdint.h"
#include "stdbool.h"
#include "stdlib.h"
#include "stdio.h"

typedef union ValueImpl Value;
typedef struct TypedValueImpl TypedValue;
typedef struct {
  size_t lCap;
  size_t rCap;
  size_t size;
  void* elements;
} List;
typedef struct{
  size_t size;
  Value* elements;
}TypedValue;
union ValueImpl{
   bool       asBool;
   uint8_t    asByte;
   uint32_t   asCodepoint;
   int64_t    asInt;
   uint64_t   asUInt;
   double     asFloat;
   //addLater Type
   //addLater optional-primitves
   //addLater proc-ptr
   List*      asList;
   Tuple*     asTuple;
};
struct TypedValueImpl{
  //addLater Type
  Value value;
};

List*  allocList(void);
ensureLCap(List* list,size_t lCap);
ensureRCap(List* list,size_t rCap);
Tuple* allocTuple(void);



