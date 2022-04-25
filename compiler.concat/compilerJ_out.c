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
  bool  asBool;
  bool* asBoolRef;
  int64_t  asInt;
  int64_t* asIntRef;
  float64_t  asFloat;
  float64_t* asFloatRef;
  int8_t  asByte;
  int8_t* asByteRef;
  uint64_t  asUint;
  uint64_t* asUintRef;
  int32_t  asCodepoint;
  int32_t* asCodepointRef;
  type_t  asType;
  type_t* asTypeRef;
  fptr_t  asFPtr;
  fptr_t* asFPtrRef;
  ptr_t   asPtr;
};

// type definitions

// procedure definitions

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_8_13(Stack* stack, value_t* curried);
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried);

// global variables

// procedure bodies

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_8_13(Stack* stack, value_t* curried){
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asUint = 2ULL;
  // VALUE: byte:3
  (stack->ptr++)->asByte = 0x33;
  // VALUE: codepoint:💻
  (stack->ptr++)->asCodepoint = 0x1f4bb;
  // STACK_ROT at lib/stack:13:26
  // expanded at test/compiler:9:18
  memmove(stack->ptr ,stack->ptr-2,1*sizeof(value_t));
  memmove(stack->ptr-2,stack->ptr-1,2*sizeof(value_t));
  // STACK_ROT at lib/stack:14:26
  // expanded at test/compiler:9:23
  memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
  memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
  // STACK_DUP at lib/stack:11:26
  // expanded at test/compiler:9:28
  value_t dup_tmp = *(stack->ptr-2);
  *(stack->ptr++) = dup_tmp;
  // STACK_DUP at lib/stack:10:26
  // expanded at test/compiler:9:33
  dup_tmp = *(stack->ptr-1);
  *(stack->ptr++) = dup_tmp;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:11:3
  stack->ptr-=1;
  // DEBUG_PRINT: uint
  printf("%"PRIu64"\n", (--(stack->ptr))->asUint);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // DEBUG_PRINT: codepoint
  printf("U+%"PRIx32"\n", (--(stack->ptr))->asCodepoint);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", (--(stack->ptr))->asInt);
  // VALUE: bool:false
  (stack->ptr++)->asBool = false;
  // IF: +5
  if((--(stack->ptr))->asBool){
    // CONTEXT_OPEN at test/compiler:18:12
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:20:3
    // ELSE: +11
  }else{
    // CONTEXT_OPEN at test/compiler:20:3
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // _IF: +6
    if((--(stack->ptr))->asBool){
      // CONTEXT_OPEN at test/compiler:20:14
      // VALUE: int:1
      (stack->ptr++)->asInt = 1LL;
      // CONTEXT_CLOSE at test/compiler:22:3
      // CONTEXT_CLOSE at test/compiler:22:3
      // ELSE: +3
    }else{
      // VALUE: int:0
      (stack->ptr++)->asInt = 0LL;
      // CONTEXT_CLOSE at test/compiler:24:3
      // END_IF: +2
    }
  }
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", (--(stack->ptr))->asInt);
  // VALUE: int:66
  (stack->ptr++)->asInt = 66LL;
  // CALL_PROC: ( int => byte ):@(test/compiler:6:6)
  concat_private_procedure_test0x2F_compiler_6_6(stack, NULL);
  // STACK_DUP at lib/stack:10:26
  // expanded at test/compiler:28:3
  dup_tmp = *(stack->ptr-1);
  *(stack->ptr++) = dup_tmp;
  // LOCAL_DECLARE:0 (x)
  int8_t local_var_0_0 = (--(stack->ptr))->asByte;
  // CAST at test/compiler:30:7
  (stack->ptr)->asInt = (stack->ptr)->asByte;
  // LOCAL_DECLARE:1 (y)
  int64_t local_var_0_1 = (--(stack->ptr))->asInt;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // DEBUG_PRINT: byte reference mut
  printf("%"PRIx64"\n", (--(stack->ptr))->asUint);
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asInt = local_var_0_1;
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", (--(stack->ptr))->asInt);
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asByte = *(((stack->ptr)-1)->asByteRef);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // VALUE: byte:x
  (stack->ptr++)->asByte = 0x78;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // ASSIGN: byte
  *(((stack->ptr)-1)->asByteRef) = ((stack->ptr)-2)->asByte;
  stack->ptr-=2;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asByte = *(((stack->ptr)-1)->asByteRef);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:37:13
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // STACK_ROT at lib/stack:14:26
    // expanded at test/compiler:37:26
    memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
    memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
    // CONTEXT_CLOSE at test/compiler:37:31
    // DO: +6
  if(!((--(stack->ptr))->asBool)) break; //exit while loop
    // CONTEXT_OPEN at test/compiler:37:31
    // STACK_DUP at lib/stack:10:26
    // expanded at test/compiler:38:5
    dup_tmp = *(stack->ptr-1);
    *(stack->ptr++) = dup_tmp;
    // DEBUG_PRINT: bool
    puts(((--(stack->ptr))->asBool) ? "true" : "false");
    // CONTEXT_CLOSE at test/compiler:39:3
    // END_WHILE: -10
  }while(true);
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:39:5
  stack->ptr-=1;
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:39:10
  stack->ptr-=1;
  // VALUE: byte: 
  (stack->ptr++)->asByte = 0x20;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", (--(stack->ptr))->asByte);
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:41:13
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // STACK_ROT at lib/stack:14:26
    // expanded at test/compiler:42:11
    memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
    memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
    // STACK_DUP at lib/stack:10:26
    // expanded at test/compiler:42:16
    dup_tmp = *(stack->ptr-1);
    *(stack->ptr++) = dup_tmp;
    // DEBUG_PRINT: bool
    puts(((--(stack->ptr))->asBool) ? "true" : "false");
    // CONTEXT_CLOSE at test/compiler:43:6
    // DO_WHILE: -6
  }while(((--(stack->ptr))->asBool));
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:43:8
  stack->ptr-=1;
  // STACK_DROP at lib/stack:9:26
  // expanded at test/compiler:43:13
  stack->ptr-=1;
}
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried){
  // CAST at test/compiler:6:32
  (stack->ptr)->asByte = (stack->ptr)->asInt;
}


int main(){
  value_t data[100];
  Stack stack = {.data = data, .ptr = data, .capacity = 100};
  concat_public_procedure_test0x2F_compiler_8_13(&stack, NULL);
}
