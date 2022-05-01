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
// procedure two ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_67_5(Stack* stack, value_t* curried);
// procedure cmpCheck ( int uint => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_71_10(Stack* stack, value_t* curried);
// procedure three ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_68_7(Stack* stack, value_t* curried);
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried);
// procedure one ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_66_5(Stack* stack, value_t* curried);
// procedure four ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_69_6(Stack* stack, value_t* curried);

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
  // STACK_ROT at lib/stack:15:27 expanded at test/compiler:9:18
  memmove(stack->ptr ,stack->ptr-2,1*sizeof(value_t));
  memmove(stack->ptr-2,stack->ptr-1,2*sizeof(value_t));
  // STACK_ROT at lib/stack:16:27 expanded at test/compiler:9:23
  memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
  memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
  // STACK_DUP at lib/stack:13:27 expanded at test/compiler:9:28
  memmove(stack->ptr,stack->ptr-2,1*sizeof(value_t));
  stack->ptr += 1;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:9:33
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asByte));
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:11:3
  stack->ptr -= 1;
  // DEBUG_PRINT: uint
  printf("%"PRIu64"\n", ((--(stack->ptr))->asUint));
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asByte));
  // DEBUG_PRINT: codepoint
  printf("U+%"PRIx32"\n", ((--(stack->ptr))->asCodepoint));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: bool:false
  (stack->ptr++)->asBool = false;
  // IF: +5
  if(((--(stack->ptr))->asBool)){
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
    if(((--(stack->ptr))->asBool)){
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
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: int:66
  (stack->ptr++)->asInt = 66LL;
  // CALL_PROC: ( int => byte ):@(test/compiler:6:6)
  concat_private_procedure_test0x2F_compiler_6_6(stack, NULL);
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:28:3
  memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
  stack->ptr += 1;
  // LOCAL_DECLARE:0 (x)
  int8_t local_var_0_0 = ((--(stack->ptr))->asByte);
  // CAST at test/compiler:30:7
  ((stack->ptr)-1)->asInt = (((stack->ptr)-1)->asByte);
  // LOCAL_DECLARE:1 (y)
  int64_t local_var_0_1 = ((--(stack->ptr))->asInt);
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // DEBUG_PRINT: byte reference mut
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asInt = local_var_0_1;
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asByte = *(((stack->ptr)-1)->asByteRef);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asByte));
  // VALUE: byte:x
  (stack->ptr++)->asByte = 0x78;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // ASSIGN: byte
  *((stack->ptr)-1)->asByteRef = (((stack->ptr)-2)->asByte);
  stack->ptr -= 2;
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asByteRef = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asByte = *(((stack->ptr)-1)->asByteRef);
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asByte));
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:37:13
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // STACK_ROT at lib/stack:16:27 expanded at test/compiler:37:26
    memmove(stack->ptr ,stack->ptr-3,1*sizeof(value_t));
    memmove(stack->ptr-3,stack->ptr-2,3*sizeof(value_t));
    // CONTEXT_CLOSE at test/compiler:37:31
    // DO: +6
  if(!((--(stack->ptr))->asBool)) break; //exit while loop
    // CONTEXT_OPEN at test/compiler:37:31
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:38:5
    memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
    stack->ptr += 1;
    // DEBUG_PRINT: bool
    puts((((--(stack->ptr))->asBool)) ? "true" : "false");
    // CONTEXT_CLOSE at test/compiler:39:3
    // END_WHILE: -10
  }while(true);
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:39:5
  stack->ptr -= 1;
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:39:10
  stack->ptr -= 1;
  // VALUE: byte: 
  (stack->ptr++)->asByte = 0x20;
  // DEBUG_PRINT: byte
  printf("'%1$c' (%1$"PRIx8")\n", ((--(stack->ptr))->asByte));
  // VALUE: int:5
  (stack->ptr++)->asInt = 5LL;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:41:5
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:42:5
    memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
    stack->ptr += 1;
    // DEBUG_PRINT: int
    printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CALL_PROC: ( int int => int ):-
    stack->ptr -= 1;
    ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) - ((int64_t)((stack->ptr)->asInt)));
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:43:9
    memmove(stack->ptr,stack->ptr-1,1*sizeof(value_t));
    stack->ptr += 1;
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CALL_PROC: ( int int => bool ):>
    stack->ptr -= 1;
    ((stack->ptr)-1)->asBool = (((int64_t)(((stack->ptr)-1)->asInt)) > ((int64_t)((stack->ptr)->asInt)));
    // CONTEXT_CLOSE at test/compiler:44:6
    // DO_WHILE: -9
  }while(((--(stack->ptr))->asBool));
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:44:8
  stack->ptr -= 1;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asInt = local_var_0_1;
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
  // CALL_PROC: ( int int => int ):+
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) + ((int64_t)((stack->ptr)->asInt)));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: uint:2
  (stack->ptr++)->asUint = 2ULL;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asInt = local_var_0_1;
  // CALL_PROC: ( uint int => uint ):-
  stack->ptr -= 1;
  ((stack->ptr)-1)->asUint = (((uint64_t)(((stack->ptr)-1)->asUint)) - ((uint64_t)((stack->ptr)->asInt)));
  // DEBUG_PRINT: uint
  printf("%"PRIu64"\n", ((--(stack->ptr))->asUint));
  // VALUE: int:-1
  (stack->ptr++)->asInt = -1LL;
  // VALUE: uint:1
  (stack->ptr++)->asUint = 1ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:71:10)
  concat_private_procedure_test0x2F_compiler_71_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asUint = 2ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:71:10)
  concat_private_procedure_test0x2F_compiler_71_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: int:0
  (stack->ptr++)->asInt = 0LL;
  // VALUE: uint:1
  (stack->ptr++)->asUint = 1ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:71:10)
  concat_private_procedure_test0x2F_compiler_71_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
  // VALUE: uint:0
  (stack->ptr++)->asUint = 0ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:71:10)
  concat_private_procedure_test0x2F_compiler_71_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
  // VALUE: uint:18446744073709551615
  (stack->ptr++)->asUint = 18446744073709551615ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:71:10)
  concat_private_procedure_test0x2F_compiler_71_10(stack, NULL);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // CALL_PROC: ( => int ):@(test/compiler:66:5)
  concat_private_procedure_test0x2F_compiler_66_5(stack, NULL);
  // CALL_PROC: ( int => int ):~
  ((stack->ptr)-1)->asInt =  ~ (((stack->ptr)-1)->asInt);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // CALL_PROC: ( => int ):@(test/compiler:66:5)
  concat_private_procedure_test0x2F_compiler_66_5(stack, NULL);
  // CALL_PROC: ( int => int ):-_
  ((stack->ptr)-1)->asInt =  - (((stack->ptr)-1)->asInt);
  // CALL_PROC: ( int => int ):~
  ((stack->ptr)-1)->asInt =  ~ (((stack->ptr)-1)->asInt);
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // CALL_PROC: ( => int ):@(test/compiler:66:5)
  concat_private_procedure_test0x2F_compiler_66_5(stack, NULL);
  // CALL_PROC: ( => int ):@(test/compiler:67:5)
  concat_private_procedure_test0x2F_compiler_67_5(stack, NULL);
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( => int ):@(test/compiler:68:7)
  concat_private_procedure_test0x2F_compiler_68_7(stack, NULL);
  // CALL_PROC: ( int int => int ):&
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) & ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( => int ):@(test/compiler:69:6)
  concat_private_procedure_test0x2F_compiler_69_6(stack, NULL);
  // CALL_PROC: ( int int => int ):xor
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) ^ ((int64_t)((stack->ptr)->asInt)));
  // DEBUG_PRINT: int
  printf("%"PRIi64"\n", ((--(stack->ptr))->asInt));
  // VALUE: type:bool
  (stack->ptr++)->asType = 1/* bool */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // VALUE: type:byte
  (stack->ptr++)->asType = 3/* byte */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // VALUE: type:codepoint
  (stack->ptr++)->asType = 7/* codepoint */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // VALUE: type:int
  (stack->ptr++)->asType = 9/* int */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // VALUE: type:uint
  (stack->ptr++)->asType = 8/* uint */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // VALUE: type:float
  (stack->ptr++)->asType = 16/* float */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
  // VALUE: type:type
  (stack->ptr++)->asType = 17/* type */;
  // DEBUG_PRINT: type
  printf("%"PRIx64"\n", ((--(stack->ptr))->asUint));
}
// procedure two ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_67_5(Stack* stack, value_t* curried){
  // VALUE: int:2
  (stack->ptr++)->asInt = 2LL;
}
// procedure cmpCheck ( int uint => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_71_10(Stack* stack, value_t* curried){
  // LOCAL_DECLARE:0 (y)
  uint64_t local_var_0_0 = ((--(stack->ptr))->asUint);
  // LOCAL_DECLARE:1 (x)
  int64_t local_var_0_1 = ((--(stack->ptr))->asInt);
  // VALUE: int:0
  (stack->ptr++)->asInt = 0LL;
  // LOCAL_DECLARE:2 (res)
  int64_t local_var_0_2 = ((--(stack->ptr))->asInt);
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asInt = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asUint = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):<
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asInt) <  0 || (((stack->ptr)-1)->asInt) < ((stack->ptr)->asUint));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:75:10
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:75:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:75:16
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CONTEXT_CLOSE at test/compiler:75:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
  // VALUE: int:10
  (stack->ptr++)->asInt = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) * ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asIntRef = (((stack->ptr)-2)->asInt);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asInt = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asUint = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):<=
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asInt) <  0 || (((stack->ptr)-1)->asInt) <= ((stack->ptr)->asUint));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:76:10
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:76:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:76:16
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CONTEXT_CLOSE at test/compiler:76:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
  // VALUE: int:10
  (stack->ptr++)->asInt = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) * ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asIntRef = (((stack->ptr)-2)->asInt);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asInt = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asUint = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):>
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asInt) >= 0 && (((stack->ptr)-1)->asInt) > ((stack->ptr)->asUint));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:77:10
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:77:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:77:16
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CONTEXT_CLOSE at test/compiler:77:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
  // VALUE: int:10
  (stack->ptr++)->asInt = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) * ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asIntRef = (((stack->ptr)-2)->asInt);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asInt = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asUint = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):>=
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asInt) >= 0 && (((stack->ptr)-1)->asInt) >= ((stack->ptr)->asUint));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:78:10
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:78:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:78:16
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CONTEXT_CLOSE at test/compiler:78:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
  // VALUE: int:10
  (stack->ptr++)->asInt = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) * ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asIntRef = (((stack->ptr)-2)->asInt);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asInt = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asUint = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):==
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asInt) >= 0 && (((stack->ptr)-1)->asInt) == ((stack->ptr)->asUint));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:79:10
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:79:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:79:16
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CONTEXT_CLOSE at test/compiler:79:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
  // VALUE: int:10
  (stack->ptr++)->asInt = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) * ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asIntRef = (((stack->ptr)-2)->asInt);
  stack->ptr -= 2;
  // LOCAL_READ:1 (x)
  (stack->ptr++)->asInt = local_var_0_1;
  // LOCAL_READ:0 (y)
  (stack->ptr++)->asUint = local_var_0_0;
  // CALL_PROC: ( int uint => bool ):!=
  stack->ptr -= 1;
  ((stack->ptr)-1)->asBool = ((((stack->ptr)-1)->asInt) <  0 || (((stack->ptr)-1)->asInt) != ((stack->ptr)->asUint));
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:80:10
    // VALUE: int:1
    (stack->ptr++)->asInt = 1LL;
    // CONTEXT_CLOSE at test/compiler:80:16
    // ELSE: +4
  }else{
    // CONTEXT_OPEN at test/compiler:80:16
    // VALUE: int:0
    (stack->ptr++)->asInt = 0LL;
    // CONTEXT_CLOSE at test/compiler:80:23
    // END_IF: +1
  }
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
  // VALUE: int:10
  (stack->ptr++)->asInt = 10LL;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) * ((int64_t)((stack->ptr)->asInt)));
  // CALL_PROC: ( int int => int ):|
  stack->ptr -= 1;
  ((stack->ptr)-1)->asInt = (((int64_t)(((stack->ptr)-1)->asInt)) | ((int64_t)((stack->ptr)->asInt)));
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // ASSIGN: int
  *((stack->ptr)-1)->asIntRef = (((stack->ptr)-2)->asInt);
  stack->ptr -= 2;
  // LOCAL_REFERENCE_TO:2 (res)
  (stack->ptr++)->asIntRef = &local_var_0_2;
  // DEREFERENCE: int
  ((stack->ptr)-1)->asInt = *(((stack->ptr)-1)->asIntRef);
}
// procedure three ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_68_7(Stack* stack, value_t* curried){
  // VALUE: int:3
  (stack->ptr++)->asInt = 3LL;
}
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, value_t* curried){
  // CAST at test/compiler:6:32
  ((stack->ptr)-1)->asByte = (((stack->ptr)-1)->asInt);
}
// procedure one ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_66_5(Stack* stack, value_t* curried){
  // VALUE: int:1
  (stack->ptr++)->asInt = 1LL;
}
// procedure four ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_69_6(Stack* stack, value_t* curried){
  // VALUE: int:4
  (stack->ptr++)->asInt = 4LL;
}


int main(){
  value_t data[100];
  Stack stack = {.data = data, .ptr = data, .capacity = 100};
  concat_public_procedure_test0x2F_compiler_8_13(&stack, NULL);
}
