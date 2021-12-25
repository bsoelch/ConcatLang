/*
 * Main.c
 *
 *  common functions for all compiled concat-code
 *
 *  Created on: 24.12.2021
 *      Author: bsoelch
 */

#include "stdlib.h"
#include "stdio.h"
#include "string.h"
#include "inttypes.h"
#include "stdbool.h"

#define STACK_INIT_SIZE  1024

#define ERR_MEM              -1
#define ERR_STACK_UNDERFLOW   1
#define ERR_VAR_NOT_DEFINED   2
#define ERR_CONST_OVERWRITTEN 3
#define ERR_CONST_OVERWRITES  4
#define ERR_CONST_WRITE       5

typedef struct StackImpl   Stack;
typedef struct ContextImpl Context;

typedef Context*(*Procedure)(Stack*,Context*);

typedef uint8_t Type;
#define TYPE_VAR       0x00
#define TYPE_BOOL      0x01
#define TYPE_BYTE      0x02
#define TYPE_CHAR      0x03
#define TYPE_INT       0x04
#define TYPE_FLOAT     0x05
#define TYPE_TYPE      0x06
#define TYPE_DEEP_TYPE 0x07
#define TYPE_PROCEDURE 0x08
#define TYPE_STRUCT    0x09

#define DEEP_TYPE_LIST   0x10
#define DEEP_TYPE_ITR    0x11
#define DEEP_TYPE_STREAM 0x12

typedef struct{//addLater? replace with uint64_t and modifier functions
	Type type;
	uint32_t refCount;
}RefType;

typedef struct DeepTypeImpl DeepType;
typedef uint64_t DeepTypePtr;
struct DeepTypeImpl{
	RefType refType;
	DeepTypePtr contentI;
};
typedef struct ValueImpl Value;

typedef struct{
	DeepType contentType;
	size_t size;
	size_t cap;
	Value* values;
}ListHeader;

typedef struct{
	size_t index;
	size_t size;
	Value* list;
}ListIterator;

struct ValueImpl{
	RefType refType;
	union{
		bool        asBool;
		uint8_t     asByte;
		uint32_t    asChar;
		uint64_t    asInt;
		double      asFloat;
		Type        asType;
		DeepTypePtr asDeepType;
		Procedure   asProc;
		Context*    asStruct;
		ListHeader* asList;
		//addLater  iterator, stream
	}data;
};

//memory allocator for DeepTypes
#define TYPE_POOL_INIT 1024
size_t  deepTypeFreeCount=0;
size_t  deepTypeFreedCap=0;
DeepTypePtr* deepTypeFreed=NULL;

size_t deepTypePoolCap=0;
DeepTypePtr deepTypeMaxId=0;
DeepType* deepTypePool;

DeepType deepTypeById(DeepTypePtr id){
 return deepTypePool[id];
}
DeepTypePtr deepTypeAlloc(){
	if(deepTypeFreeCount>0){
		return deepTypeFreed[deepTypeFreeCount--];
	}
	if(deepTypePool==NULL){
		deepTypePool=malloc(TYPE_POOL_INIT*sizeof(DeepType*));
		deepTypePoolCap=TYPE_POOL_INIT;
	}else if(deepTypeMaxId>=deepTypePoolCap){//update sizer
		deepTypePool=realloc(deepTypePool,2*deepTypePoolCap*sizeof(DeepType*));
		deepTypePoolCap*=2;
	}
	if(deepTypePool==NULL){
		fputs("memory error",stderr);
		exit(ERR_MEM);
	}
	return deepTypeMaxId++;
}
void freeDeepType(DeepTypePtr toFree){
	if(toFree==deepTypeMaxId-1){
		deepTypeMaxId--;
	}else{
		if(deepTypeFreed==NULL){
			deepTypeFreed=malloc((TYPE_POOL_INIT>>2)*sizeof(DeepTypePtr));
			deepTypeFreedCap=(TYPE_POOL_INIT>>2);
		}else if(deepTypeFreeCount<=deepTypeFreedCap){
			deepTypeFreed=realloc(deepTypeFreed,2*deepTypeFreedCap*sizeof(DeepTypePtr));
			deepTypeFreedCap*=2;
		}
		if(deepTypeFreed==NULL){
			fputs("memory error",stderr);
			exit(ERR_MEM);
		}
		deepTypeFreed[deepTypeFreeCount++]=toFree;
	}
}
void unlinkDeepType(DeepType toFree){
	//TODO unlinkDeepType
}

//end DeepTypes mem-alloc

void freeValue(Value toFree){
	//TODO freeValue
}
// stack
struct StackImpl{
	size_t cap;
	size_t size;
	Value* data;
};
Stack* stackInit(){
	Stack* aStack=malloc(sizeof(Stack));
	if(aStack!=NULL){
		aStack->data=malloc(STACK_INIT_SIZE*sizeof(Value));
		if(aStack->data!=NULL){
			aStack->cap=STACK_INIT_SIZE;
			aStack->size=0;
			return aStack;
		}
	}
	return NULL;
}
void stackPush(Stack* stack,const Value aValue){
	if(stack->cap<=stack->size){
		Value* tmp=realloc(stack->data,2*stack->cap);
		if(tmp==NULL){
			fputs("memory error",stderr);
			exit(ERR_MEM);
		}
		stack->data=tmp;
		stack->cap*=2;
	}
	stack->data[stack->size++]=aValue;
}
Value stackPop(Stack* stack){
	if(stack->size>0){
		return stack->data[stack->size--];
	}else{
		fputs("stack underflow \n",stderr);
		exit(ERR_STACK_UNDERFLOW);
		return (Value){0};
	}
}
Value stackPeek(const Stack* stack){
	if(stack->size>0){
		return stack->data[stack->size-1];
	}else{
		fputs("stack underflow \n",stderr);
		exit(ERR_STACK_UNDERFLOW);
		return (Value){0};
	}
}

void stackDrop(Stack* stack){
	if(stack->size>0){
		stack->size--;
	}else{
		fputs("stack underflow \n",stderr);
		exit(ERR_STACK_UNDERFLOW);
	}
}
void stackDup(Stack* stack){
	stackPush(stack,stackPeek(stack));
}
void stackSwap(const Stack* stack){
	if(stack->size>1){
		Value tmp=stack->data[stack->size-1];
		stack->data[stack->size-1]=stack->data[stack->size-2];
		stack->data[stack->size-2]=tmp;
	}else{
		fputs("stack underflow \n",stderr);
		exit(ERR_STACK_UNDERFLOW);
	}
}
//stack end
//context
typedef struct{
  DeepType varType;
  bool isConst;
  Value value;
}Variable;

void varAssign(Variable* target,Value newValue,bool ignoreConst){
	if(target->isConst||!ignoreConst){
		fputs("tried writing to constant variable \n",stderr);
		exit(ERR_CONST_WRITE);
	}
	//TODO type-cast
	target->value=newValue;
}
void varInit(Variable* target,DeepType type,bool isConst,Value value){
	target->varType=type;
	target->isConst=isConst;
	varAssign(target,value,true);
}
typedef struct EntryImpl Entry;
struct EntryImpl{
  const char* name;
  Variable data;
  Entry* next;
};
struct ContextImpl{
	size_t cap;
	Entry** data;
	Context* parent;
};
size_t strHashCode(const char* name,size_t max){
 size_t hash=*name;
 while(*name!='\0'&&max!=0){
	 hash*=31;
	 hash+=(*(++name));
	 max--;
 }
 return hash;
}
Context* contextInit(size_t initCap,Context* parent){
  Context* create=malloc(sizeof(Context));
  if(create!=NULL){
	  create->data=calloc(initCap,sizeof(Entry));
	  if(create->data!=NULL){
		  create->cap=initCap;
		  create->parent=parent;
		  return create;
	  }
  }
  return NULL;
}
Context* contextFree(Context* context){
	if(context!=NULL){
		Context* tmp=context->parent;
		for(size_t i=0;i<context->cap;i++){
			Entry* e=context->data[i];
			while(e!=NULL){
				unlinkDeepType(e->data.varType);
				freeValue(e->data.value);
				Entry* tmp=e->next;
				free(e);
				e=tmp;
			}
		}
		free(context->data);
		return tmp;
	}
	return NULL;
}

Variable* getVariable(const Context* context,const char* name,size_t maxChars){
	size_t hash=strHashCode(name,maxChars);
	Entry* e=context->data[hash%context->cap];
	while(e!=NULL){
		if(strcmp(e->name,name)==0){
			return &e->data;
		}
		e=e->next;
	}
	if(context->parent!=NULL){
		return getVariable(context->parent,name,maxChars);
	}
	fprintf(stderr,"Variable %.*s is not defined\n",(int)maxChars,name);
	exit(ERR_VAR_NOT_DEFINED);
	return NULL;
}
Variable* declareVariable(const Context* context,const char* name,size_t maxChars,DeepType type,bool isConst,Value value){
	size_t hash=strHashCode(name,maxChars);
	Entry** e=context->data+(hash%context->cap);
	while(*e!=NULL){
		if(strcmp((*e)->name,name)==0){
			if((*e)->data.isConst){
				fprintf(stderr,"cannot replace constant variable %.*s\n",(int)maxChars,name);
				exit(ERR_CONST_OVERWRITTEN);
			}else if(isConst){
				fprintf(stderr,"constant variable %.*s cannot replace existing variables\n",(int)maxChars,name);
				exit(ERR_CONST_OVERWRITES);
			}
		    varInit(&(*e)->data,type,isConst,value);
			return &(*e)->data;
		}
		*e=(*e)->next;
	}
	*e=malloc(sizeof(Entry));
	if(*e==NULL){
		fputs("memory error",stderr);
		exit(ERR_MEM);
	}
    (*e)->name=name;
    varInit(&(*e)->data,type,isConst,value);
    (*e)->next=NULL;
	return &(*e)->data;
}
//addLater? removeVariable

//end context


Value addValues(Value a,Value b){
 //TODO implement add
 return (Value){0};
}

//test implementation of the root procedure
Context* proc_filename_global(Stack* stack,Context* context){
  //add
  {
    Value tmp1=stackPop(stack);
    Value tmp2=stackPop(stack);
    stackPush(stack,addValues(tmp1,tmp2));
  }
  return context;
}


int main(int argc,char**argv){


	return 0;
}
