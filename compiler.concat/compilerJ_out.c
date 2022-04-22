// compiled concat file

#include "stdbool.h"
#include "inttypes.h"
#include "stdlib.h"
#include "stdio.h"
#include "string.h"

typedef union value_t_Impl value_t;

typedef struct{
  size_t capacity;
  size_t size;
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
  stack->data[(stack->size)++].asInt = 3LL;
  // CALL_PROC: ( int => int ):@(test/compiler:6:6)
  concat_private_procedure_test0x2F_compiler_6_6(stack, NULL);
  // DEBUG_PRINT at test/compiler:9:10
  printf("%"PRIx64, stack->data[stack->size--].asUint);
  // VALUE: int:1
  stack->data[(stack->size)++].asInt = 1LL;
  // VALUE: uint:2
  stack->data[(stack->size)++].asUint = 2ULL;
  // VALUE: byte:3
  stack->data[(stack->size)++].asByte = 0x33;
  // VALUE: codepoint:💻
  stack->data[(stack->size)++].asCodepoint = 0x1f4bb;
  // STACK_ROT at lib/stack:13:26
  // expanded at test/compiler:10:18
  memmove(stack->data+stack->size ,stack->data+stack->size-2,1*sizeof(value_t));
  memmove(stack->data+stack->size-2,stack->data+stack->size-1,2*sizeof(value_t));
  // STACK_ROT at lib/stack:14:26
  // expanded at test/compiler:10:23
  memmove(stack->data+stack->size ,stack->data+stack->size-3,1*sizeof(value_t));
  memmove(stack->data+stack->size-3,stack->data+stack->size-2,3*sizeof(value_t));
  // STACK_DUP at lib/stack:11:26
  // expanded at test/compiler:10:28
  value_t dup_tmp = stack->data[stack->size-2];
  stack->data[stack->size++] = dup_tmp;
  // STACK_DUP at lib/stack:10:26
  // expanded at test/compiler:10:33
  dup_tmp = stack->data[stack->size-1];
  stack->data[stack->size++] = dup_tmp;
  // DEBUG_PRINT at test/compiler:11:3
  printf("%"PRIx64, stack->data[stack->size--].asUint);
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:12:3
  stack->size-=1;
  // DEBUG_PRINT at test/compiler:13:3
  printf("%"PRIx64, stack->data[stack->size--].asUint);
  // DEBUG_PRINT at test/compiler:14:3
  printf("%"PRIx64, stack->data[stack->size--].asUint);
  // DEBUG_PRINT at test/compiler:15:3
  printf("%"PRIx64, stack->data[stack->size--].asUint);
  // DEBUG_PRINT at test/compiler:16:3
  printf("%"PRIx64, stack->data[stack->size--].asUint);
}
// procedure test ( int => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried){
}


int main(){
  value_t data[100];
  Stack stack = {.data = data, .size =0, .capacity = 100};
  concat_public_procedure_test0x2F_compiler_8_13(&stack, NULL);
}
