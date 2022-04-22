// compiled concat file

#include "stdbool.h"
#include "inttypes.h"
#include "stdlib.h"
#include "stdio.h"
#include "string.h"

typedef union value_t_Impl value_t;

typedef struct{
  size_t capacity;
  value_t* ptr;
  value_t* data;
}Stack;

typedef uint64_t type_t;
typedef double float64_t;
typedef void(*fptr_t)(Stack*, value_t*);
typedef void* ptr_t;

union value_t_Impl {
  type_t  asType;
  type_t* asTypeRef;
  uint64_t  asUint;
  uint64_t* asUintRef;
  uint32_t  asCodepoint;
  uint32_t* asCodepointRef;
  bool  asBool;
  bool* asBoolRef;
  int64_t  asInt;
  int64_t* asIntRef;
  float64_t  asFloat;
  float64_t* asFloatRef;
  uint8_t  asByte;
  uint8_t* asByteRef;
  fptr_t  asFPtr;
  fptr_t* asFPtrRef;
  ptr_t   asPtr;
};

// type definitions

// procedure definitions

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_8_13(Stack* stack, value_t* curried);
// procedure test ( int => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried);

// global variables

// procedure bodies

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_8_13(Stack* stack, value_t* curried){
  // VALUE: int:3
  (stack->ptr++)->asInt = 3LL;
  // CALL_PROC: ( int => int ):@(test/compiler:6:6)
  concat_private_procedure_test0x2F_compiler_6_6(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", (--(stack->ptr))->asInt);
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asUint = 2ULL;
  // VALUE: byte:3
  (stack->ptr++)->asByte = 0x33;
  // VALUE: codepoint:💻
  (stack->ptr++)->asCodepoint = 0x1f4bb;
  // STACK_ROT at lib/stack:13:26
  // expanded at test/compiler:10:18
  memmove(stack->ptr ,stack->ptr-2,1*sizeof(value_t));
  memmove(stack->ptr-2,stack->ptr-1,2*sizeof(value_t));
  // STACK_ROT at lib/stack:14:26
  // expanded at test/compiler:10:23
  memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
  memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
  // STACK_DUP at lib/stack:11:26
  // expanded at test/compiler:10:28
  value_t dup_tmp = *(stack->ptr-2);
  *(stack->ptr++) = dup_tmp;
  // STACK_DUP at lib/stack:10:26
  // expanded at test/compiler:10:33
  dup_tmp = *(stack->ptr-1);
  *(stack->ptr++) = dup_tmp;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:12:3
  stack->ptr-=1;
  // DEBUG_PRINT: uint
  printf("%"PRIu64"\n", (--(stack->ptr))->asUint);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // DEBUG_PRINT: codepoint
  printf("%"PRIx32"\n", (--(stack->ptr))->asCodepoint);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", (--(stack->ptr))->asInt);
  // VALUE: bool:false
  (stack->ptr++)->asBool = false;
  // IF: +5
  if((--(stack->ptr))->asBool){
    // CONTEXT_OPEN at test/compiler:19:12
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:21:3
    // ELSE: +11
  }else{
    // CONTEXT_OPEN at test/compiler:21:3
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // _IF: +6
    if((--(stack->ptr))->asBool){
      // CONTEXT_OPEN at test/compiler:21:14
      // VALUE: int:1
      (stack->ptr++)->asInt = 1LL;
      // CONTEXT_CLOSE at test/compiler:23:3
      // CONTEXT_CLOSE at test/compiler:23:3
      // ELSE: +3
    }else{
      // VALUE: int:0
      (stack->ptr++)->asInt = 0LL;
      // CONTEXT_CLOSE at test/compiler:25:3
      // END_IF: +2
    }
  }
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", (--(stack->ptr))->asInt);
}
// procedure test ( int => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried){
}


int main(){
  value_t data[100];
  Stack stack = {.data = data, .ptr = data, .capacity = 100};
  concat_public_procedure_test0x2F_compiler_8_13(&stack, NULL);
}
