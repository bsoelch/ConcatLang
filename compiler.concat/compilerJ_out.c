// compiled concat file

#include "stdbool.h"
#include "inttypes.h"
#include "stdlib.h"
#include "stdio.h"
#include "string.h"

// internal types
typedef union Value_Impl Value;

typedef struct{
  size_t capacity;
  Value* ptr;
  Value* data;
}Stack;

typedef uint64_t Type;
typedef double float64_t;
typedef void(*FPtr)(Stack*, Value*);
typedef int32_t  optionalI32[2];
typedef uint32_t optionalU32[2];

union Value_Impl {
  bool  asBool;
  bool*  asBoolPtr;
  float64_t  asFloat;
  float64_t*  asFloatPtr;
  FPtr  asProcPtr;
  int32_t  asI32;
  int32_t*  asI32Ptr;
  int64_t  asI64;
  int64_t*  asI64Ptr;
  int8_t  asI8;
  int8_t*  asI8Ptr;
  optionalI32  asOptionalI32;
  optionalI32*  asOptionalI32Ptr;
  optionalU32  asOptionalU32;
  optionalU32*  asOptionalU32Ptr;
  Type  asType;
  Type*  asTypePtr;
  uint64_t  asU64;
  uint64_t*  asU64Ptr;
  Value*  asAny;
};

// type definitions

// procedure definitions

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_32_13(Stack* stack, Value* curried);
// procedure two ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_10_5(Stack* stack, Value* curried);
// procedure cmpCheck ( int uint => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_14_10(Stack* stack, Value* curried);
// procedure three ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_11_7(Stack* stack, Value* curried);
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, Value* curried);
// procedure one ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_9_5(Stack* stack, Value* curried);
// procedure four ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_12_6(Stack* stack, Value* curried);
// lambda ( int => int ) at test/compiler:91:4
void concat_private_procedure_test0x2F_compiler_91_4(Stack* stack, Value* curried);
// lambda ( int => int ) at test/compiler:89:4
void concat_private_procedure_test0x2F_compiler_89_4(Stack* stack, Value* curried);

// constant arrays/global variables
int8_t concat_const_array_test0x2F_compiler_119_6[] = {0x28, 0x65, 0x6d, 0x70, 0x74, 0x79, 0x29};
int8_t concat_const_array_test0x2F_compiler_112_4[] = {0x48, 0x65, 0x6c, 0x6c, 0x6f};
int8_t concat_const_array_test0x2F_compiler_101_15[] = {0x54, 0x75, 0x70, 0x6c, 0x65};
int8_t concat_const_array_test0x2F_compiler_93_4[] = {0x54, 0x65, 0x73, 0x74};
int8_t concat_const_array_test0x2F_compiler_101_4[] = {0x43, 0x6f, 0x6e, 0x73, 0x74, 0x61, 0x6e, 0x74};
int8_t concat_const_array_test0x2F_compiler_103_8[] = {0x73, 0x74, 0x72, 0x75, 0x63, 0x74};
int64_t concat_const_array_test0x2F_compiler_96_12[] = {1LL, 2LL, 3LL};
int8_t concat_const_array_test0x2F_compiler_95_4[] = {0x54, 0x65, 0x73, 0x74, 0x32};
int8_t concat_const_array_test0x2F_compiler_132_12[] = {0x57, 0x6f, 0x72, 0x6c, 0x64};

// procedure bodies

// procedure main ( => )
void concat_public_procedure_test0x2F_compiler_32_13(Stack* stack, Value* curried){
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asU64 = 2ULL;
  // VALUE: byte:3
  (stack->ptr++)->asI8 = 51LL;
  // VALUE: codepoint:💻
  (stack->ptr++)->asI32 = 128187LL;
  // STACK_ROT at lib/stack:15:27 expanded at test/compiler:33:18
  memmove(stack->ptr ,stack->ptr-2,1*sizeof(Value));
  memmove(stack->ptr-2,stack->ptr-1,2*sizeof(Value));
  // STACK_ROT at lib/stack:16:27 expanded at test/compiler:33:23
  memmove(stack->ptr ,stack->ptr-3,1*sizeof(Value));
  memmove(stack->ptr-3,stack->ptr-2,3*sizeof(Value));
  // STACK_DUP at lib/stack:13:27 expanded at test/compiler:33:28
  memmove(stack->ptr,stack->ptr-2,1*sizeof(Value));
  stack->ptr += 1;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:33:33
  memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
  stack->ptr += 1;
  // DEBUG_PRINT: byte
  fputs("byte (", stdout);
  stack->ptr -= 1;
  printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
  puts(")");
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:35:3
  stack->ptr -= 1;
  // DEBUG_PRINT: uint
  fputs("uint (", stdout);
  stack->ptr -= 1;
  printf("uint64_t: %"PRIu64, ((stack->ptr)->asU64));
  puts(")");
  // DEBUG_PRINT: byte
  fputs("byte (", stdout);
  stack->ptr -= 1;
  printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
  puts(")");
  // DEBUG_PRINT: codepoint
  fputs("codepoint (", stdout);
  stack->ptr -= 1;
  printf("int32_t: %"PRIi32, ((stack->ptr)->asI32));
  puts(")");
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: bool:false
  (stack->ptr++)->asBool = false;
  // IF: +5
  if(((--(stack->ptr))->asBool)){
    // CONTEXT_OPEN at test/compiler:42:12
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CONTEXT_CLOSE at test/compiler:44:3
    // ELSE: +11
  }else{
    // CONTEXT_OPEN at test/compiler:44:3
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // _IF: +6
    if(((--(stack->ptr))->asBool)){
      // CONTEXT_OPEN at test/compiler:44:14
      // VALUE: int:1
      (stack->ptr++)->asI64 = 1LL;
      // CONTEXT_CLOSE at test/compiler:46:3
      // CONTEXT_CLOSE at test/compiler:46:3
      // ELSE: +3
    }else{
      // VALUE: int:0
      (stack->ptr++)->asI64 = 0LL;
      // CONTEXT_CLOSE at test/compiler:48:3
      // END_IF: +2
    }
  }
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: int:66
  (stack->ptr++)->asI64 = 66LL;
  // CALL_PROC: ( int => byte ):@(test/compiler:6:6)
  concat_private_procedure_test0x2F_compiler_6_6(stack, NULL);
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:52:3
  memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
  stack->ptr += 1;
  // LOCAL_DECLARE:0 (x)
  int8_t local_var_0_0 = ((--(stack->ptr))->asI8);
  // CAST at test/compiler:54:7
  ((stack->ptr)-1)->asI64 = (((stack->ptr)-1)->asI8);
  // LOCAL_DECLARE:1 (y)
  int64_t local_var_0_1 = ((--(stack->ptr))->asI64);
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asI8Ptr = &local_var_0_0;
  // DEBUG_PRINT: byte reference mut
  fputs("byte reference mut (", stdout);
  stack->ptr -= 1;
  printf("int8_t*: %p", ((stack->ptr)->asI8Ptr));
  puts(")");
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // LOCAL_REFERENCE_TO:0 (x)
  (stack->ptr++)->asI8Ptr = &local_var_0_0;
  // DEREFERENCE: byte
  ((stack->ptr)-1)->asI8 = *(((stack->ptr)-1)->asI8Ptr);
  // DEBUG_PRINT: byte
  fputs("byte (", stdout);
  stack->ptr -= 1;
  printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
  puts(")");
  // VALUE: byte:x
  (stack->ptr++)->asI8 = 120LL;
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
  fputs("byte (", stdout);
  stack->ptr -= 1;
  printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
  puts(")");
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // VALUE: bool:true
  (stack->ptr++)->asBool = true;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:61:13
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // STACK_ROT at lib/stack:16:27 expanded at test/compiler:61:26
    memmove(stack->ptr ,stack->ptr-3,1*sizeof(Value));
    memmove(stack->ptr-3,stack->ptr-2,3*sizeof(Value));
    // CONTEXT_CLOSE at test/compiler:61:31
    // DO: +6
  if(!((--(stack->ptr))->asBool)) break; //exit while loop
    // CONTEXT_OPEN at test/compiler:61:31
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:62:5
    memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
    stack->ptr += 1;
    // DEBUG_PRINT: bool
    fputs("bool (", stdout);
    stack->ptr -= 1;
    printf("bool: %s", ((stack->ptr)->asBool) ? "true" : "false");
    puts(")");
    // CONTEXT_CLOSE at test/compiler:63:3
    // END_WHILE: -10
  }while(true);
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:63:5
  stack->ptr -= 1;
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:63:10
  stack->ptr -= 1;
  // VALUE: byte: 
  (stack->ptr++)->asI8 = 32LL;
  // DEBUG_PRINT: byte
  fputs("byte (", stdout);
  stack->ptr -= 1;
  printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
  puts(")");
  // VALUE: int:5
  (stack->ptr++)->asI64 = 5LL;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:65:5
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:66:5
    memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
    stack->ptr += 1;
    // DEBUG_PRINT: int
    fputs("int (", stdout);
    stack->ptr -= 1;
    printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
    puts(")");
    // VALUE: int:1
    (stack->ptr++)->asI64 = 1LL;
    // CALL_PROC: ( int int => int ):-
    stack->ptr -= 1;
    ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) - ((int64_t)((stack->ptr)->asI64)));
    // STACK_DUP at lib/stack:12:27 expanded at test/compiler:67:9
    memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
    stack->ptr += 1;
    // VALUE: int:0
    (stack->ptr++)->asI64 = 0LL;
    // CALL_PROC: ( int int => bool ):>
    stack->ptr -= 1;
    ((stack->ptr)-1)->asBool = (((int64_t)(((stack->ptr)-1)->asI64)) > ((int64_t)((stack->ptr)->asI64)));
    // CONTEXT_CLOSE at test/compiler:68:6
    // DO_WHILE: -9
  }while(((--(stack->ptr))->asBool));
  // STACK_DROP at lib/stack:11:27 expanded at test/compiler:68:8
  stack->ptr -= 1;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // CALL_PROC: ( int int => int ):+
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) + ((int64_t)((stack->ptr)->asI64)));
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: uint:2
  (stack->ptr++)->asU64 = 2ULL;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // CALL_PROC: ( uint int => uint ):-
  stack->ptr -= 1;
  ((stack->ptr)-1)->asU64 = (((uint64_t)(((stack->ptr)-1)->asU64)) - ((uint64_t)((stack->ptr)->asI64)));
  // DEBUG_PRINT: uint
  fputs("uint (", stdout);
  stack->ptr -= 1;
  printf("uint64_t: %"PRIu64, ((stack->ptr)->asU64));
  puts(")");
  // VALUE: int:-1
  (stack->ptr++)->asI64 = -1LL;
  // VALUE: uint:1
  (stack->ptr++)->asU64 = 1ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:2
  (stack->ptr++)->asU64 = 2ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: int:0
  (stack->ptr++)->asI64 = 0LL;
  // VALUE: uint:1
  (stack->ptr++)->asU64 = 1ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:0
  (stack->ptr++)->asU64 = 0ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // VALUE: uint:18446744073709551615
  (stack->ptr++)->asU64 = 18446744073709551615ULL;
  // CALL_PROC: ( int uint => int ):@(test/compiler:14:10)
  concat_private_procedure_test0x2F_compiler_14_10(stack, NULL);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // CALL_PROC: ( => int ):@(test/compiler:9:5)
  concat_private_procedure_test0x2F_compiler_9_5(stack, NULL);
  // CALL_PROC: ( int => int ):~
  ((stack->ptr)-1)->asI64 =  ~ (((stack->ptr)-1)->asI64);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // CALL_PROC: ( => int ):@(test/compiler:9:5)
  concat_private_procedure_test0x2F_compiler_9_5(stack, NULL);
  // CALL_PROC: ( int => int ):-_
  ((stack->ptr)-1)->asI64 =  - (((stack->ptr)-1)->asI64);
  // CALL_PROC: ( int => int ):~
  ((stack->ptr)-1)->asI64 =  ~ (((stack->ptr)-1)->asI64);
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
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
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: type:bool
  (stack->ptr++)->asType = 1/* bool */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: type:byte
  (stack->ptr++)->asType = 3/* byte */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: type:codepoint
  (stack->ptr++)->asType = 7/* codepoint */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: type:int
  (stack->ptr++)->asType = 9/* int */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: type:uint
  (stack->ptr++)->asType = 8/* uint */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: type:float
  (stack->ptr++)->asType = 16/* float */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: type:type
  (stack->ptr++)->asType = 17/* type */;
  // DEBUG_PRINT: type
  fputs("type (", stdout);
  stack->ptr -= 1;
  printf("Type: %"PRIx64"", ((stack->ptr)->asType));
  puts(")");
  // VALUE: ( => int ):@(test/compiler:9:5)
  (stack->ptr++)->asProcPtr = &concat_private_procedure_test0x2F_compiler_9_5;
  (stack->ptr++)->asAny = NULL;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:88:9
  memmove(stack->ptr,stack->ptr-2,2*sizeof(Value));
  stack->ptr += 2;
  // CALL_PTR at test/compiler:88:13
  stack->ptr -= 2;
  ((stack->ptr)->asProcPtr)(stack, (((stack->ptr)+1)->asAny));
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // DEBUG_PRINT: ( => int )
  fputs("( => int ) (", stdout);
  stack->ptr -= 2;
  printf(" FPtr: %p", ((stack->ptr)->asProcPtr));
  printf(" Value*: %p", (((stack->ptr)+1)->asAny));
  puts(")");
  // LAMBDA: ( int => int ):@(test/compiler:89:4)
  (stack->ptr++)->asProcPtr = &concat_private_procedure_test0x2F_compiler_89_4;
  (stack->ptr++)->asAny = NULL;
  // LOCAL_DECLARE:2 (f)
  stack->ptr -= 2;
  Value local_var_0_2[2];
  memcpy(local_var_0_2, (stack->ptr), 2*sizeof(Value));
  // VALUE: int:2
  (stack->ptr++)->asI64 = 2LL;
  // LOCAL_REFERENCE_TO:2 (f)
  (stack->ptr++)->asAny = local_var_0_2;
  // DEREFERENCE: ( int => int )
  memcpy(((stack->ptr)-1), (((stack->ptr)-1)->asAny), 2*sizeof(Value));
  stack->ptr += 1;
  // CALL_PTR at test/compiler:90:8
  stack->ptr -= 2;
  ((stack->ptr)->asProcPtr)(stack, (((stack->ptr)+1)->asAny));
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // LAMBDA: ( int => int ):@(test/compiler:91:4)
  (stack->ptr++)->asProcPtr = &concat_private_procedure_test0x2F_compiler_91_4;
  (stack->ptr++)->asAny = NULL;
  // LOCAL_REFERENCE_TO:2 (f)
  (stack->ptr++)->asAny = local_var_0_2;
  // ASSIGN: ( int => int )
  memcpy((((stack->ptr)-1)->asAny), ((stack->ptr)-3), 2*sizeof(Value));
  stack->ptr -= 3;
  // VALUE: int:2
  (stack->ptr++)->asI64 = 2LL;
  // LOCAL_REFERENCE_TO:2 (f)
  (stack->ptr++)->asAny = local_var_0_2;
  // DEREFERENCE: ( int => int )
  memcpy(((stack->ptr)-1), (((stack->ptr)-1)->asAny), 2*sizeof(Value));
  stack->ptr += 1;
  // CALL_PTR at test/compiler:92:8
  stack->ptr -= 2;
  ((stack->ptr)->asProcPtr)(stack, (((stack->ptr)+1)->asAny));
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // GLOBAL_VALUE: byte array mut~:Test
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_93_4;
  (stack->ptr++)->asU64 = 4ULL;
  // DEBUG_PRINT: byte array mut~
  fputs("byte array mut~ (", stdout);
  stack->ptr -= 2;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  puts(")");
  // GLOBAL_VALUE: byte array mut~:Test
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_93_4;
  (stack->ptr++)->asU64 = 4ULL;
  // LOCAL_DECLARE:3 (str)
  stack->ptr -= 2;
  Value local_var_0_3[2];
  memcpy(local_var_0_3, (stack->ptr), 2*sizeof(Value));
  // GLOBAL_VALUE: byte array mut~:Test2
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_95_4;
  (stack->ptr++)->asU64 = 5ULL;
  // LOCAL_READ:3 (str)
  memcpy((stack->ptr), local_var_0_3, 2*sizeof(Value));
  stack->ptr += 2;
  // STACK_ROT at lib/stack:15:27 expanded at test/compiler:95:16
  memmove(stack->ptr ,stack->ptr-4,2*sizeof(Value));
  memmove(stack->ptr-4,stack->ptr-2,4*sizeof(Value));
  // DEBUG_PRINT: byte array mut~
  fputs("byte array mut~ (", stdout);
  stack->ptr -= 2;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  puts(")");
  // DEBUG_PRINT: byte array mut~
  fputs("byte array mut~ (", stdout);
  stack->ptr -= 2;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  puts(")");
  // VALUE: int array:[int:1, int:2, int:3]
  (stack->ptr++)->asI64Ptr = concat_const_array_test0x2F_compiler_96_12;
  (stack->ptr++)->asU64 = 3ULL;
  // FOR_ARRAY_PREPARE: -1
  ((stack->ptr)-1)->asI64Ptr = (((stack->ptr)-2)->asI64Ptr)+(((stack->ptr)-1)->asU64);
  // FOR_ARRAY_LOOP: +5
  for(; (((stack->ptr)-2)->asI64Ptr) < (((stack->ptr)-1)->asI64Ptr); (((stack->ptr)-2)->asI64Ptr)++){
    (stack->ptr)->asI64 = *(((stack->ptr)-2)->asI64Ptr);
    stack->ptr += 1;
    // CONTEXT_OPEN at test/compiler:96:14
    // DEBUG_PRINT: int
    fputs("int (", stdout);
    stack->ptr -= 1;
    printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
    puts(")");
    // CONTEXT_CLOSE at test/compiler:98:4
    // FOR_ARRAY_END: -4
  }
  stack->ptr -= 2;
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // LOCAL_READ:1 (y)
  (stack->ptr++)->asI64 = local_var_0_1;
  // NEW: ( int int )
  // DEBUG_PRINT: ( int int )
  fputs("( int int ) (", stdout);
  stack->ptr -= 2;
  printf(" int64_t: %"PRIi64, ((stack->ptr)->asI64));
  printf(" int64_t: %"PRIi64, (((stack->ptr)+1)->asI64));
  puts(")");
  // VALUE: ( byte array mut~ byte array mut~ ( byte int ) ):(byte array mut~:Constant,byte array mut~:Tuple,( byte int ):(byte:A,int:101))
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_101_4;
  (stack->ptr++)->asU64 = 8ULL;
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_101_15;
  (stack->ptr++)->asU64 = 5ULL;
  (stack->ptr++)->asI8 = 65LL;
  (stack->ptr++)->asI64 = 101LL;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:101:84
  memmove(stack->ptr,stack->ptr-6,6*sizeof(Value));
  stack->ptr += 6;
  // DEBUG_PRINT: ( byte array mut~ byte array mut~ ( byte int ) )
  fputs("( byte array mut~ byte array mut~ ( byte int ) ) (", stdout);
  stack->ptr -= 6;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  printf(" int8_t*: %p", (((stack->ptr)+2)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+3)->asU64));
  printf(" int8_t: %"PRIi8, (((stack->ptr)+4)->asI8));
  printf(" int64_t: %"PRIi64, (((stack->ptr)+5)->asI64));
  puts(")");
  // LOCAL_DECLARE:4 (aTuple)
  stack->ptr -= 6;
  Value local_var_0_4[6];
  memcpy(local_var_0_4, (stack->ptr), 6*sizeof(Value));
  // VALUE: int testStruct:(int:1,int:2)
  (stack->ptr++)->asI64 = 1LL;
  (stack->ptr++)->asI64 = 2LL;
  // DEBUG_PRINT: int testStruct
  fputs("int testStruct (", stdout);
  stack->ptr -= 2;
  printf(" int64_t: %"PRIi64, ((stack->ptr)->asI64));
  printf(" int64_t: %"PRIi64, (((stack->ptr)+1)->asI64));
  puts(")");
  // LOCAL_READ:3 (str)
  memcpy((stack->ptr), local_var_0_3, 2*sizeof(Value));
  stack->ptr += 2;
  // GLOBAL_VALUE: byte array mut~:struct
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_103_8;
  (stack->ptr++)->asU64 = 6ULL;
  // NEW: byte array mut~ testStruct
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:103:39
  memmove(stack->ptr,stack->ptr-4,4*sizeof(Value));
  stack->ptr += 4;
  // DEBUG_PRINT: byte array mut~ testStruct
  fputs("byte array mut~ testStruct (", stdout);
  stack->ptr -= 4;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  printf(" int8_t*: %p", (((stack->ptr)+2)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+3)->asU64));
  puts(")");
  // LOCAL_DECLARE:5 (aStruct)
  stack->ptr -= 4;
  Value local_var_0_5[4];
  memcpy(local_var_0_5, (stack->ptr), 4*sizeof(Value));
  // LOCAL_READ:4 (aTuple)
  memcpy((stack->ptr), local_var_0_4, 6*sizeof(Value));
  stack->ptr += 6;
  // TUPLE_GET_INDEX at test/compiler:104:11
  stack->ptr -= 4;
  memmove(((stack->ptr)-2), ((stack->ptr)+2), 2*sizeof(Value));
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:104:14
  memmove(stack->ptr,stack->ptr-2,2*sizeof(Value));
  stack->ptr += 2;
  // DEBUG_PRINT: ( byte int )
  fputs("( byte int ) (", stdout);
  stack->ptr -= 2;
  printf(" int8_t: %"PRIi8, ((stack->ptr)->asI8));
  printf(" int64_t: %"PRIi64, (((stack->ptr)+1)->asI64));
  puts(")");
  // TUPLE_GET_INDEX at test/compiler:105:20
  stack->ptr -= 1;
  // DEBUG_PRINT: byte
  fputs("byte (", stdout);
  stack->ptr -= 1;
  printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
  puts(")");
  // LOCAL_READ:5 (aStruct)
  memcpy((stack->ptr), local_var_0_5, 4*sizeof(Value));
  stack->ptr += 4;
  // TUPLE_GET_INDEX at test/compiler:106:12
  stack->ptr -= 2;
  memmove(((stack->ptr)-2), (stack->ptr), 2*sizeof(Value));
  // DEBUG_PRINT: byte array mut~
  fputs("byte array mut~ (", stdout);
  stack->ptr -= 2;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  puts(")");
  // VALUE: codepoint optional optional:codepoint optional:codepoint empty wrap
  ((stack->ptr)->asOptionalI32)[1] = 1LL;
  stack->ptr += 1;
  // DEBUG_PRINT: codepoint optional optional
  fputs("codepoint optional optional (", stdout);
  stack->ptr -= 1;
  printf("optionalI32: %"PRIi32" %"PRIi32, ((stack->ptr)->asOptionalI32)[0], ((stack->ptr)->asOptionalI32)[1]);
  puts(")");
  // VALUE: byte optional:byte:A wrap
  ((stack->ptr)->asOptionalI32)[0] = 65LL;
  ((stack->ptr)->asOptionalI32)[1] = 1LL;
  stack->ptr += 1;
  // DEBUG_PRINT: byte optional
  fputs("byte optional (", stdout);
  stack->ptr -= 1;
  printf("optionalI32: %"PRIi32" %"PRIi32, ((stack->ptr)->asOptionalI32)[0], ((stack->ptr)->asOptionalI32)[1]);
  puts(")");
  // VALUE: byte optional optional optional optional:byte optional optional optional:byte optional optional:byte optional:byte:B wrap wrap wrap wrap
  ((stack->ptr)->asOptionalI32)[0] = 66LL;
  ((stack->ptr)->asOptionalI32)[1] = 4LL;
  stack->ptr += 1;
  // DEBUG_PRINT: byte optional optional optional optional
  fputs("byte optional optional optional optional (", stdout);
  stack->ptr -= 1;
  printf("optionalI32: %"PRIi32" %"PRIi32, ((stack->ptr)->asOptionalI32)[0], ((stack->ptr)->asOptionalI32)[1]);
  puts(")");
  // VALUE: byte array mut~ optional optional:byte array mut~ optional:byte array mut~ empty wrap
  stack->ptr += 2;
  (stack->ptr++)->asU64 = 1ULL;
  // DEBUG_PRINT: byte array mut~ optional optional
  fputs("byte array mut~ optional optional (", stdout);
  stack->ptr -= 3;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+2)->asU64));
  puts(")");
  // VALUE: byte array mut~ optional:byte array mut~:Hello wrap
  (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_112_4;
  (stack->ptr++)->asU64 = 5ULL;
  (stack->ptr++)->asU64 = 1ULL;
  // DEBUG_PRINT: byte array mut~ optional
  fputs("byte array mut~ optional (", stdout);
  stack->ptr -= 3;
  printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
  printf(" uint64_t: %"PRIu64, (((stack->ptr)+2)->asU64));
  puts(")");
  // VALUE: int optional:int:9710642 wrap
  (stack->ptr++)->asI64 = 9710642LL;
  (stack->ptr++)->asU64 = 1ULL;
  // LOCAL_DECLARE:6 (opt)
  stack->ptr -= 2;
  Value local_var_0_6[2];
  memcpy(local_var_0_6, (stack->ptr), 2*sizeof(Value));
  // VALUE: byte optional:byte empty
  ((stack->ptr)->asOptionalI32)[1] = 0LL;
  stack->ptr += 1;
  // IF_OPTIONAL: +5
  if((((stack->ptr)-1)->asOptionalI32)[1] != 0){
    ((stack->ptr)-1)->asI8 = (((stack->ptr)-1)->asOptionalI32)[0];
    // CONTEXT_OPEN at test/compiler:114:15
    // DEBUG_PRINT: byte
    fputs("byte (", stdout);
    stack->ptr -= 1;
    printf("int8_t: %"PRIi8, ((stack->ptr)->asI8));
    puts(")");
    // CONTEXT_CLOSE at test/compiler:116:4
    // ELSE: +12
  }else{
    stack->ptr -= 1;
    // CONTEXT_OPEN at test/compiler:116:4
    // LOCAL_READ:6 (opt)
    memcpy((stack->ptr), local_var_0_6, 2*sizeof(Value));
    stack->ptr += 2;
    // _IF_OPTIONAL: +6
    if((((stack->ptr)-1)->asU64) != 0){
      stack->ptr -= 1;
      // CONTEXT_OPEN at test/compiler:116:13
      // DEBUG_PRINT: int
      fputs("int (", stdout);
      stack->ptr -= 1;
      printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
      puts(")");
      // CONTEXT_CLOSE at test/compiler:118:4
      // CONTEXT_CLOSE at test/compiler:118:4
      // ELSE: +4
    }else{
      stack->ptr -= 2;
      // GLOBAL_VALUE: byte array mut~:(empty)
      (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_119_6;
      (stack->ptr++)->asU64 = 7ULL;
      // DEBUG_PRINT: byte array mut~
      fputs("byte array mut~ (", stdout);
      stack->ptr -= 2;
      printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
      printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
      puts(")");
      // CONTEXT_CLOSE at test/compiler:120:4
      // END_IF: +2
    }
  }
  // VALUE: int array:[int:1, int:2, int:3]
  (stack->ptr++)->asI64Ptr = concat_const_array_test0x2F_compiler_96_12;
  (stack->ptr++)->asU64 = 3ULL;
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
  // CAST_ARG at test/compiler:121:16
  ((stack->ptr)-1)->asU64 = (((stack->ptr)-1)->asI64);
  // CALL_PROC: ( int array mut? uint => int ):[]
  if((((stack->ptr)-1)->asU64) >= (((stack->ptr)-2)->asU64)){ //index>=len;
    fprintf(stderr,"array index (%"PRIu64") out of bounds for length %"PRIu64"\n", (((stack->ptr)-1)->asU64), (((stack->ptr)-2)->asU64));
    exit(0xa11a7);
  }
  ((stack->ptr)-3)->asI64 = *((((stack->ptr)-3)->asI64Ptr) + (((stack->ptr)-1)->asU64));
  stack->ptr -= 2;
  // DEBUG_PRINT: int
  fputs("int (", stdout);
  stack->ptr -= 1;
  printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
  puts(")");
  // VALUE: int optional:int:5 wrap
  (stack->ptr++)->asI64 = 5LL;
  (stack->ptr++)->asU64 = 1ULL;
  // WHILE: -1
  do{
    // CONTEXT_OPEN at test/compiler:122:11
    // CONTEXT_CLOSE at test/compiler:122:18
    // DO_OPTIONAL: +21
    if((((stack->ptr)-1)->asU64) == 0) break; //exit while loop
      stack->ptr -= 1;
      // CONTEXT_OPEN at test/compiler:122:18
      // STACK_DUP at lib/stack:12:27 expanded at test/compiler:123:6
      memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
      stack->ptr += 1;
      // DEBUG_PRINT: int
      fputs("int (", stdout);
      stack->ptr -= 1;
      printf("int64_t: %"PRIi64, ((stack->ptr)->asI64));
      puts(")");
      // STACK_DUP at lib/stack:12:27 expanded at test/compiler:124:6
      memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
      stack->ptr += 1;
      // VALUE: int:0
      (stack->ptr++)->asI64 = 0LL;
      // CALL_PROC: ( int int => bool ):>
      stack->ptr -= 1;
      ((stack->ptr)-1)->asBool = (((int64_t)(((stack->ptr)-1)->asI64)) > ((int64_t)((stack->ptr)->asI64)));
      // IF: +7
      if(((--(stack->ptr))->asBool)){
        // CONTEXT_OPEN at test/compiler:124:14
        // VALUE: int:1
        (stack->ptr++)->asI64 = 1LL;
        // CALL_PROC: ( int int => int ):-
        stack->ptr -= 1;
        ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) - ((int64_t)((stack->ptr)->asI64)));
        // CALL_PROC: ( int => int optional ):wrap
        (stack->ptr++)->asU64 = 1ULL;
        // CONTEXT_CLOSE at test/compiler:126:6
        // ELSE: +5
      }else{
        stack->ptr -= 2;
        // CONTEXT_OPEN at test/compiler:126:6
        // STACK_DROP at lib/stack:11:27 expanded at test/compiler:126:11
        stack->ptr -= 1;
        // VALUE: int optional:int empty
        stack->ptr += 1;
        (stack->ptr++)->asU64 = 0ULL;
        // CONTEXT_CLOSE at test/compiler:128:6
        // END_IF: +1
      }
      // CONTEXT_CLOSE at test/compiler:129:4
      // END_WHILE: -23
    }while(true);
    // LOCAL_REFERENCE_TO:0 (x)
    (stack->ptr++)->asI8Ptr = &local_var_0_0;
    // CALL_PROC: ( byte reference mut => byte reference mut optional ):wrap
    (stack->ptr++)->asU64 = 1ULL;
    // CALL_PROC: ( byte reference mut optional => byte reference mut optional optional ):wrap
    (((stack->ptr)-1)->asU64)++;
    // DEBUG_PRINT: byte reference mut optional optional
    fputs("byte reference mut optional optional (", stdout);
    stack->ptr -= 2;
    printf(" int8_t*: %p", ((stack->ptr)->asI8Ptr));
    printf(" uint64_t: %"PRIu64, (((stack->ptr)+1)->asU64));
    puts(")");
    // GLOBAL_VALUE: byte array mut~:Hello
    (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_112_4;
    (stack->ptr++)->asU64 = 5ULL;
    // GLOBAL_VALUE: byte array mut~:Hello
    (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_112_4;
    (stack->ptr++)->asU64 = 5ULL;
    // CALL_PROC: ( byte array mut~ byte array mut~ => bool ):===
    stack->ptr -= 4;
    (stack->ptr)->asBool = memcmp((stack->ptr), ((stack->ptr)+2), 2)==0;
    stack->ptr += 1;
    // DEBUG_PRINT: bool
    fputs("bool (", stdout);
    stack->ptr -= 1;
    printf("bool: %s", ((stack->ptr)->asBool) ? "true" : "false");
    puts(")");
    // GLOBAL_VALUE: byte array mut~:Hello
    (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_112_4;
    (stack->ptr++)->asU64 = 5ULL;
    // GLOBAL_VALUE: byte array mut~:World
    (stack->ptr++)->asI8Ptr = concat_const_array_test0x2F_compiler_132_12;
    (stack->ptr++)->asU64 = 5ULL;
    // CALL_PROC: ( byte array mut~ byte array mut~ => bool ):=!=
    stack->ptr -= 4;
    (stack->ptr)->asBool = memcmp((stack->ptr), ((stack->ptr)+2), 2)!=0;
    stack->ptr += 1;
    // DEBUG_PRINT: bool
    fputs("bool (", stdout);
    stack->ptr -= 1;
    printf("bool: %s", ((stack->ptr)->asBool) ? "true" : "false");
    puts(")");
    // VALUE: bool:false
    (stack->ptr++)->asBool = false;
    // LOCAL_DECLARE:7 (FALSE)
    bool local_var_0_7 = ((--(stack->ptr))->asBool);
    // VALUE: bool:true
    (stack->ptr++)->asBool = true;
    // LOCAL_DECLARE:8 (TRUE)
    bool local_var_0_8 = ((--(stack->ptr))->asBool);
    // LOCAL_READ:7 (FALSE)
    (stack->ptr++)->asBool = local_var_0_7;
    // CALL_PROC: ( bool => bool ):!
    ((stack->ptr)-1)->asBool = !(((stack->ptr)-1)->asBool);
    // LOCAL_READ:8 (TRUE)
    (stack->ptr++)->asBool = local_var_0_8;
    // CALL_PROC: ( bool bool => bool ):&
    stack->ptr -= 1;
    ((stack->ptr)-1)->asBool = (((stack->ptr)-1)->asBool) && ((stack->ptr)->asBool);
    // LOCAL_READ:7 (FALSE)
    (stack->ptr++)->asBool = local_var_0_7;
    // CALL_PROC: ( bool bool => bool ):|
    stack->ptr -= 1;
    ((stack->ptr)-1)->asBool = (((stack->ptr)-1)->asBool) || ((stack->ptr)->asBool);
    // LOCAL_READ:8 (TRUE)
    (stack->ptr++)->asBool = local_var_0_8;
    // CALL_PROC: ( bool bool => bool ):xor
    stack->ptr -= 1;
    ((stack->ptr)-1)->asBool = (((stack->ptr)-1)->asBool) ^ ((stack->ptr)->asBool);
    // DEBUG_PRINT: bool
    fputs("bool (", stdout);
    stack->ptr -= 1;
    printf("bool: %s", ((stack->ptr)->asBool) ? "true" : "false");
    puts(")");
}
// procedure two ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_10_5(Stack* stack, Value* curried){
  // VALUE: int:2
  (stack->ptr++)->asI64 = 2LL;
}
// procedure cmpCheck ( int uint => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_14_10(Stack* stack, Value* curried){
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
void concat_private_procedure_test0x2F_compiler_11_7(Stack* stack, Value* curried){
  // VALUE: int:3
  (stack->ptr++)->asI64 = 3LL;
}
// procedure test ( int => byte ) in test/compiler
void concat_private_procedure_test0x2F_compiler_6_6(Stack* stack, Value* curried){
  // CAST at test/compiler:6:32
  ((stack->ptr)-1)->asI8 = (((stack->ptr)-1)->asI64);
}
// procedure one ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_9_5(Stack* stack, Value* curried){
  // VALUE: int:1
  (stack->ptr++)->asI64 = 1LL;
}
// procedure four ( => int ) in test/compiler
void concat_private_procedure_test0x2F_compiler_12_6(Stack* stack, Value* curried){
  // VALUE: int:4
  (stack->ptr++)->asI64 = 4LL;
}
// lambda ( int => int ) at test/compiler:91:4
void concat_private_procedure_test0x2F_compiler_91_4(Stack* stack, Value* curried){
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:91:19
  memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
  stack->ptr += 1;
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:91:23
  memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
  stack->ptr += 1;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
}
// lambda ( int => int ) at test/compiler:89:4
void concat_private_procedure_test0x2F_compiler_89_4(Stack* stack, Value* curried){
  // STACK_DUP at lib/stack:12:27 expanded at test/compiler:89:19
  memmove(stack->ptr,stack->ptr-1,1*sizeof(Value));
  stack->ptr += 1;
  // CALL_PROC: ( int int => int ):*
  stack->ptr -= 1;
  ((stack->ptr)-1)->asI64 = (((int64_t)(((stack->ptr)-1)->asI64)) * ((int64_t)((stack->ptr)->asI64)));
}


int main(){
  Value data[100];
  Stack stack = {.data = data, .ptr = data, .capacity = 100};
  concat_public_procedure_test0x2F_compiler_32_13(&stack, NULL);
}
