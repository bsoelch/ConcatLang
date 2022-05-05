// compiled concat file

#include "stdbool.h"
#include "inttypes.h"
#include "stdlib.h"
#include "stdio.h"
#include "string.h"

// internal types
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
  bool*  asBoolPtr;
  float64_t  asFloat;
  float64_t*  asFloatPtr;
  fptr_t  asProcPtr;
  int32_t  asI32;
  int32_t*  asI32Ptr;
  int64_t  asI64;
  int64_t*  asI64Ptr;
  int8_t  asI8;
  int8_t*  asI8Ptr;
  type_t  asType;
  type_t*  asTypePtr;
  uint64_t  asU64;
  uint64_t*  asU64Ptr;
  value_t*  asAny;
};

// type definitions

// procedure definitions

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_27_13(Stack* stack, value_t* curried);
// procedure two ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_10_5(Stack* stack, value_t* curried);
// procedure cmpCheck ( int uint => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_14_10(Stack* stack, value_t* curried);
// procedure three ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_11_7(Stack* stack, value_t* curried);
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried);
// procedure one ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_9_5(Stack* stack, value_t* curried);
// procedure four ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_12_6(Stack* stack, value_t* curried);
// lambda ( int => int ) at test/compiler:84:4
void concat_private_procedure_test0x2F_compiler_84_4(Stack* stack, value_t* curried);
// lambda ( int => int ) at test/compiler:86:4
void concat_private_procedure_test0x2F_compiler_86_4(Stack* stack, value_t* curried);

// constant arrays/global variables
int8_t concat_const_array_test0x2F_compiler_88_4[] = {0x54, 0x65, 0x73, 0x74};
int64_t concat_const_array_test0x2F_compiler_90_12[] = {1LL, 2LL, 3LL};
int8_t concat_const_array_test0x2F_compiler_89_4[] = {0x54, 0x65, 0x73, 0x74, 0x32};

// procedure bodies

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_27_13(Stack* stack, value_t* curried){
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asU64 = 2ULL;
  // VALUE: byte:3
  (stack->ptr++)->asI8 = 0x33;
  // VALUE: codepoint:💻
  (stack->ptr++)->asI32 = 0x1f4bb;
  // STACK_ROT at lib/stack:15:27 expanded at test/compiler:28:18
  memmove(stack->ptr ,stack->ptr-2,1*sizeof(value_t));
  memmove(stack->ptr-2,stack->ptr-1,2*sizeof(value_t));
  // STACK_ROT at lib/stack:16:27 expanded at test/compiler:28:23
  memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
  memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
  // STACK_DUP at lib/stack:13:27 expanded at test/compiler:28:28
  memmove(stack->ptr,stack->ptr-2,1*sizeof(value_t));
  stack->ptr += 1;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:28:33
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asI8));
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:30:3
  stack->ptr -= 1;
  // DEBUG_PRINT: uint
  printf("%"PRIu64"\n", ((--(stack->ptr))->asU64));
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asI8));
  // DEBUG_PRINT: codepoint
  printf("U+%"PRIx32"\n", ((--(stack->ptr))->asI32));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: bool:false
  (stack->ptr++)->asBool = false;
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:37:12
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:39:3
    // ELSE: +11
  }else{
    // CONTEXT_OPEN at test/compiler:39:3
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // _IF: +6
    if(((--(stack->ptr))->asBool)){
      // CONTEXT_OPEN at test/compiler:39:14
      // VALUE: int:1
      (stack->ptr++)->asI64 = 1LL;
      // CONTEXT_CLOSE at test/compiler:41:3
      // CONTEXT_CLOSE at test/compiler:41:3
      // ELSE: +3
    }else{
      // VALUE: int:0
      (stack->ptr++)->asI64 = 0LL;
      // CONTEXT_CLOSE at test/compiler:43:3
      // END_IF: +2
    }
  }
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: int:66
  (stack->ptr++)->asI64 = 66LL;
  // CALL_PROC: ( int => byte ):@(test/compiler:6:6)
  concat_private_procedure_test0x2F_compiler_6_6(stack, NULL);
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:47:3
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // LOCAL_DECLARE:0 (x)
  int8_t local_var_0_0 = ((--(stack->ptr))->asI8);
  // CAST at test/compiler:49:7
  ((stack->ptr)-1)->asI64 = (((stack->ptr)-1)->asI8);
  // LOCAL_DECLARE:1 (y)
  int64_t local_var_0_1 = ((--(stack->ptr))->asI64);
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asI8Ptr = &local_var_0_0;
  // DEBUG_PRINT: byte reference mut
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asI8Ptr = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asI8 = *(((stack->ptr)-1)->asI8Ptr);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asI8));
  // VALUE: byte:x
  (stack->ptr++)->asI8 = 0x78;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asI8Ptr = &local_var_0_0;
  // ASSIGN: byte
  *((stack->ptr)-1)->asI8Ptr = (((stack->ptr)-2)->asI8);
  stack->ptr -= 2;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asI8Ptr = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asI8 = *(((stack->ptr)-1)->asI8Ptr);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asI8));
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:56:13
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // STACK_ROT at lib/stack:16:27 expanded at test/compiler:56:26
    memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
    memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
    // CONTEXT_CLOSE at test/compiler:56:31
    // DO: +6
  if(!((--(stack->ptr))->asBool)) break; //exit while loop
    // CONTEXT_OPEN at test/compiler:56:31
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:57:5
    memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
    stack->ptr += 1;
    // DEBUG_PRINT: bool
    puts((((--(stack->ptr))->asBool)) ? "true" : "false");
    // CONTEXT_CLOSE at test/compiler:58:3
    // END_WHILE: -10
  }while(true);
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:58:5
  stack->ptr -= 1;
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:58:10
  stack->ptr -= 1;
  // VALUE: byte: 
  (stack->ptr++)->asI8 = 0x20;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asI8));
  // VALUE: int:5
  (stack->ptr++)->asI64 = 5LL;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:60:5
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:61:5
    memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
    stack->ptr += 1;
    // DEBUG_PRINT: int
    printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CALL_PROC: ( int int => int ):-
    stack->ptr -= 1;
    ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) - ((int64_t)((stack->ptr)->asI64)));
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:62:9
    memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
    stack->ptr += 1;
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CALL_PROC: ( int int => bool ):>
    stack->ptr -= 1;
    ((stack->ptr)-1)->asBool = (((int64_t)(((stack->ptr)-1)->asI64)) > ((int64_t)((stack->ptr)->asI64)));
    // CONTEXT_CLOSE at test/compiler:63:6
    // DO_WHILE: -9
  }while(((--(stack->ptr))->asBool));
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:63:8
  stack->ptr -= 1;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // CALL_PROC: ( int int => int ):+
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) + ((int64_t)((stack->ptr)->asI64)));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: uint:2
  (stack->ptr++)->asU64 = 2ULL;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // CALL_PROC: ( uint int => uint ):-
  stack->ptr -= 1;
  ((stack->ptr)-1)->asU64 = (((uint64_t)(((stack->ptr)-1)->asU64)) - ((uint64_t)((stack->ptr)->asI64)));
  // DEBUG_PRINT: uint
  printf("%"PRIu64"\n", ((--(stack->ptr))->asU64));
  // VALUE: int:-1
  (stack->ptr++)->asI64 = -1LL;
  // VALUE: uint:1
  (stack->ptr++)->asU64 = 1ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asU64 = 2ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: int:0
  (stack->ptr++)->asI64 = 0LL;
  // VALUE: uint:1
  (stack->ptr++)->asU64 = 1ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:0
  (stack->ptr++)->asU64 = 0ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:18446744073709551615
  (stack->ptr++)->asU64 = 18446744073709551615ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // CALL_PROC: ( => int ):@(test/compiler:9:5)
  concat_private_procedure_test0x2F_compiler_9_5(stack, NULL);
  // CALL_PROC: ( int => int ):~
  ((stack->ptr)-1)->asI64 =  ~ (((stack->ptr)-1)->asI64);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // CALL_PROC: ( => int ):@(test/compiler:9:5)
  concat_private_procedure_test0x2F_compiler_9_5(stack, NULL);
  // CALL_PROC: ( int => int ):-_
  ((stack->ptr)-1)->asI64 =  - (((stack->ptr)-1)->asI64);
  // CALL_PROC: ( int => int ):~
  ((stack->ptr)-1)->asI64 =  ~ (((stack->ptr)-1)->asI64);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // CALL_PROC: ( => int ):@(test/compiler:9:5)
  concat_private_procedure_test0x2F_compiler_9_5(stack, NULL);
  // CALL_PROC: ( => int ):@(test/compiler:10:5)
  concat_private_procedure_test0x2F_compiler_10_5(stack, NULL);
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( => int ):@(test/compiler:11:7)
  concat_private_procedure_test0x2F_compiler_11_7(stack, NULL);
  // CALL_PROC: ( int int => int ):&
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) & ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( => int ):@(test/compiler:12:6)
  concat_private_procedure_test0x2F_compiler_12_6(stack, NULL);
  // CALL_PROC: ( int int => int ):xor
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) ^ ((int64_t)((stack->ptr)->asI64)));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // VALUE: type:bool
  (stack->ptr++)->asType = 1/* bool */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: type:byte
  (stack->ptr++)->asType = 3/* byte */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: type:codepoint
  (stack->ptr++)->asType = 7/* codepoint */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: type:int
  (stack->ptr++)->asType = 9/* int */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: type:uint
  (stack->ptr++)->asType = 8/* uint */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: type:float
  (stack->ptr++)->asType = 16/* float */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: type:type
  (stack->ptr++)->asType = 17/* type */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asU64));
  // VALUE: ( => int ):@(test/compiler:9:5)
  (stack->ptr++)->asProcPtr = &concat_private_procedure_test0x2F_compiler_9_5;
  (stack->ptr++)->asAny = NULL;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:83:9
  memmove(stack->ptr,stack->ptr-2,2*sizeof(value_t));
  stack->ptr += 2;
  // CALL_PTR at test/compiler:83:13
  stack->ptr -= 2;
  ((stack->ptr)->asProcPtr)(stack, (((stack->ptr)+1)->asAny));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // DEBUG_PRINT: ( => int )
  stack->ptr -= 2;
  printf("procedure @%p (curried: @%p)\n", ((stack->ptr)->asProcPtr),(((stack->ptr)+1)->asAny));
  // LAMBDA: ( int => int ):@(test/compiler:84:4)
  (stack->ptr++)->asProcPtr = &concat_private_procedure_test0x2F_compiler_84_4;
  (stack->ptr++)->asAny = NULL;
  // LOCAL_DECLARE:2 (f)
  stack->ptr -= 2;
  value_t local_var_0_2[2];
  memcpy(local_var_0_2, (stack->ptr), 2*sizeof(value_t));
  // VALUE: int:2
  (stack->ptr++)->asI64 = 2LL;
  // LOCAL_REFERENCE_TO:2 (f)
  (stack->ptr++)->asAny = local_var_0_2;
  // DEREFERENCE: ( int => int )
  memcpy(((stack->ptr)-1), (((stack->ptr)-1)->asAny), 2*sizeof(value_t));
  stack->ptr += 1;
  // CALL_PTR at test/compiler:85:8
  stack->ptr -= 2;
  ((stack->ptr)->asProcPtr)(stack, (((stack->ptr)+1)->asAny));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // LAMBDA: ( int => int ):@(test/compiler:86:4)
  (stack->ptr++)->asProcPtr = &concat_private_procedure_test0x2F_compiler_86_4;
  (stack->ptr++)->asAny = NULL;
  // LOCAL_REFERENCE_TO:2 (f)
  (stack->ptr++)->asAny = local_var_0_2;
  // ASSIGN: ( int => int )
  memcpy((((stack->ptr)-1)->asAny), ((stack->ptr)-3), 2*sizeof(value_t));
  stack->ptr -= 3;
  // VALUE: int:2
  (stack->ptr++)->asI64 = 2LL;
  // LOCAL_REFERENCE_TO:2 (f)
  (stack->ptr++)->asAny = local_var_0_2;
  // DEREFERENCE: ( int => int )
  memcpy(((stack->ptr)-1), (((stack->ptr)-1)->asAny), 2*sizeof(value_t));
  stack->ptr += 1;
  // CALL_PTR at test/compiler:87:8
  stack->ptr -= 2;
  ((stack->ptr)->asProcPtr)(stack, (((stack->ptr)+1)->asAny));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
  // GLOBAL_VALUE: byte array mut~:Test
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_88_4;
  (stack->ptr++)->asU64 = 4ULL;
  // DEBUG_PRINT: byte array mut~
  stack->ptr -= 2;
  printf("array @%p length: %"PRIu64"\n", ((stack->ptr)->asI8Ptr),(((stack->ptr)+1)->asU64));
  // GLOBAL_VALUE: byte array mut~:Test2
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_89_4;
  (stack->ptr++)->asU64 = 5ULL;
  // GLOBAL_VALUE: byte array mut~:Test
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_88_4;
  (stack->ptr++)->asU64 = 4ULL;
  // STACK_ROT at lib/stack:15:27 expanded at test/compiler:89:19
  memmove(stack->ptr ,stack->ptr-4,2*sizeof(value_t));
  memmove(stack->ptr-4,stack->ptr-2,4*sizeof(value_t));
  // DEBUG_PRINT: byte array mut~
  stack->ptr -= 2;
  printf("array @%p length: %"PRIu64"\n", ((stack->ptr)->asI8Ptr),(((stack->ptr)+1)->asU64));
  // DEBUG_PRINT: byte array mut~
  stack->ptr -= 2;
  printf("array @%p length: %"PRIu64"\n", ((stack->ptr)->asI8Ptr),(((stack->ptr)+1)->asU64));
  // VALUE: int array:[int:1, int:2, int:3]
  (stack->ptr++)->asI64Ptr = concat_const_array_test0x2F_compiler_90_12;
  (stack->ptr++)->asU64 = 3ULL;
  // FOR_ARRAY_PREPARE: -1
  ((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64Ptr)+(((stack->ptr)-1)->asU64);
  // FOR_ARRAY_LOOP: +5
  for(; (((stack->ptr)-2)->asI64Ptr) < (((stack->ptr)-1)->asI64Ptr); (((stack->ptr)-2)->asI64Ptr)++){
    (stack->ptr)->asI64 = *(((stack->ptr)-2)->asI64Ptr);
    stack->ptr += 1;
    // CONTEXT_OPEN at test/compiler:90:14
    // DEBUG_PRINT: int
    printf("%"PRIi64"\n", ((--(stack->ptr))->asI64));
    // CONTEXT_CLOSE at test/compiler:92:4
    // FOR_ARRAY_END: -4
  }
  stack->ptr -= 2;
}
// procedure two ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_10_5(Stack* stack, value_t* curried){
  // VALUE: int:2
  (stack->ptr++)->asI64 = 2LL;
}
// procedure cmpCheck ( int uint => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_14_10(Stack* stack, value_t* curried){
  // LOCAL_DECLARE:0 (y)
  uint64_t local_var_0_0 = ((--(stack->ptr))->asU64);
  // LOCAL_DECLARE:1 (x)
  int64_t local_var_0_1 = ((--(stack->ptr))->asI64);
  // VALUE: int:0
  (stack->ptr++)->asI64 = 0LL;
  // LOCAL_DECLARE:2 (res)
  int64_t local_var_0_2 = ((--(stack->ptr))->asI64);
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asI64 = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asU64 = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):<
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asI64) <  0 || (((stack->ptr)-1)->asI64) < ((stack->ptr)->asU64));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:18:10
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:18:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:18:16
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CONTEXT_CLOSE at test/compiler:18:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
  // VALUE: int:10
  (stack->ptr++)->asI64 = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asI64 = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asU64 = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):<=
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asI64) <  0 || (((stack->ptr)-1)->asI64) <= ((stack->ptr)->asU64));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:19:10
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:19:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:19:16
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CONTEXT_CLOSE at test/compiler:19:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
  // VALUE: int:10
  (stack->ptr++)->asI64 = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asI64 = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asU64 = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):>
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asI64) >= 0 && (((stack->ptr)-1)->asI64) > ((stack->ptr)->asU64));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:20:10
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:20:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:20:16
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CONTEXT_CLOSE at test/compiler:20:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
  // VALUE: int:10
  (stack->ptr++)->asI64 = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asI64 = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asU64 = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):>=
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asI64) >= 0 && (((stack->ptr)-1)->asI64) >= ((stack->ptr)->asU64));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:21:10
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:21:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:21:16
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CONTEXT_CLOSE at test/compiler:21:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
  // VALUE: int:10
  (stack->ptr++)->asI64 = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asI64 = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asU64 = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):==
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asI64) >= 0 && (((stack->ptr)-1)->asI64) == ((stack->ptr)->asU64));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:22:10
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:22:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:22:16
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CONTEXT_CLOSE at test/compiler:22:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
  // VALUE: int:10
  (stack->ptr++)->asI64 = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asI64 = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asU64 = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):!=
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asI64) <  0 || (((stack->ptr)-1)->asI64) != ((stack->ptr)->asU64));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:23:10
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:23:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:23:16
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CONTEXT_CLOSE at test/compiler:23:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
  // VALUE: int:10
  (stack->ptr++)->asI64 = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) | ((int64_t)((stack->ptr)->asI64)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64);
  stack->ptr -= 2;
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asI64Ptr = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asI64 = *(((stack->ptr)-1)->asI64Ptr);
}
// procedure three ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_11_7(Stack* stack, value_t* curried){
  // VALUE: int:3
  (stack->ptr++)->asI64 = 3LL;
}
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried){
  // CAST at test/compiler:6:32
  ((stack->ptr)-1)->asI8 = (((stack->ptr)-1)->asI64);
}
// procedure one ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_9_5(Stack* stack, value_t* curried){
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
}
// procedure four ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_12_6(Stack* stack, value_t* curried){
  // VALUE: int:4
  (stack->ptr++)->asI64 = 4LL;
}
// lambda ( int => int ) at test/compiler:84:4
void concat_private_procedure_test0x2F_compiler_84_4(Stack* stack, value_t* curried){
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:84:19
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
}
// lambda ( int => int ) at test/compiler:86:4
void concat_private_procedure_test0x2F_compiler_86_4(Stack* stack, value_t* curried){
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:86:19
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:86:23
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
}


int main(){
  value_t data[100];
  Stack stack = {.data = data, .ptr = data, .capacity = 100};
  concat_public_procedure_test0x2F_compiler_27_13(&stack, NULL);
}
