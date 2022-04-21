/*
 base header file for all compiler concat files
 */

#ifndef COMMON_H
#define COMMON_H

#include "inttypes.h"
#include "string.h"
#include "stdbool.h"

typedef union value_t_Impl value_t;

typedef struct{
	size_t capacity;//update capacity at start of procedures
	size_t size;
	value_t* data;
}Stack;

typedef uint64_t type_t;
typedef double float64_t;//TODO ensure that float64_t is a 64bit (IEEE) float
typedef void(*fptr_t)(Stack*,value_t*/*curried*/);
typedef void* ptr_t;

union value_t_Impl {
	//primitive types
	bool      asBool;
	uint8_t   asU8;
	uint32_t  asU32;
	int64_t   asI64;
	uint64_t  asU64;
	float64_t asF64;
	type_t    asType;
	//primitive references
	bool*      asBoolRef;
	uint8_t*   asU8Ref;
	uint32_t*  asU32Ref;
	int64_t*   asI64Ref;
	uint64_t*  asU64Ref;
	float64_t* asF64Ref;
	type_t*    asTypeRef;
	//pointer types
	fptr_t    asFPtr;
	ptr_t     asPtr;
	//composite types are stored in multiple values
};


void stack_dup(Stack* stack,size_t count);
void stack_drop(Stack* stack,size_t off,size_t count);
void stack_rot(Stack* stack,size_t count,size_t steps);

void ptr_call(Stack* stack);


#endif /* COMMON_H */
