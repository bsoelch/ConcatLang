/*
 * Common.c
 *
 *  template for common functions in all compiled concat-code
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

#define ERR_MEM             -1
#define ERR_STACK_UNDERFLOW  1
#define ERR_VAR_NOT_DEFINED  2

typedef struct StackImpl   Stack;
typedef struct ContextImpl Context;

typedef void(*Procedure)(Stack*,Context*);

typedef struct{
	uint64_t flags;
	union{
		bool      asBool;
		uint8_t   asByte;
		uint32_t  asChar;
		uint64_t  asInt;
		double    asFloat;
		//addLater type
		Procedure asProc;
		Context*  asStruct;
		//addLater list, iterator, stream
	}data;
}Value;
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
bool stackPush(Stack* stack,const Value aValue){
	if(stack->cap<=stack->size){
		Value* tmp=realloc(stack->data,2*stack->cap);
		if(tmp!=NULL){
			stack->data=tmp;
			stack->cap*=2;
		}
		return false;
	}
	stack->data[stack->size++]=aValue;
	return true;
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
bool stackDup(Stack* stack){
	return stackPush(stack,stackPeek(stack));
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
typedef struct EntryImpl Entry;
struct EntryImpl{
  const char* name;
  //addLater var-type, isConst
  Value value;
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
Context* contextInit(size_t initCap){
  Context* create=malloc(sizeof(Context));
  if(create!=NULL){
	  create->data=calloc(initCap,sizeof(Entry));
	  if(create->data!=NULL){
		  create->cap=initCap;
		  create->parent=NULL;
		  return create;
	  }
  }
  return NULL;
}
Value getVariable(const Context* context,const char* name,size_t maxChars){
	size_t hash=strHashCode(name,maxChars);
	Entry* e=context->data[hash%context->cap];
	while(e!=NULL){
		if(strcmp(e->name,name)==0){
			return e->value;
		}
		e=e->next;
	}
	if(context->parent!=NULL){
		return getVariable(context->parent,name,maxChars);
	}
	fprintf(stderr,"Variable %.*s is not defined\n",(int)maxChars,name);
	exit(ERR_VAR_NOT_DEFINED);
	return (Value){0};
}
void setVariable(const Context* context,const char* name,size_t maxChars,Value newValue){
	size_t hash=strHashCode(name,maxChars);
	Entry* e=context->data[hash%context->cap];
	while(e!=NULL){
		if(strcmp(e->name,name)==0){
			//addLater type-casting
			e->value=newValue;
		}
		e=e->next;
	}
	if(context->parent!=NULL){
		setVariable(context->parent,name,maxChars,newValue);
	}
	fprintf(stderr,"Variable %.*s is not defined\n",(int)maxChars,name);
	exit(ERR_VAR_NOT_DEFINED);
}
void decalreVariable(const Context* context,const char* name,size_t maxChars,/*TODO type*/Value value){
	size_t hash=strHashCode(name,maxChars);
	Entry** e=context->data+(hash%context->cap);
	while(*e!=NULL){
		if(strcmp((*e)->name,name)==0){
			//TODO replace variable (if not constant)
			return;
		}
		*e=(*e)->next;
	}
	*e=malloc(sizeof(Entry));
	if(*e==NULL){
		fputs("memory error",stderr);
		exit(ERR_MEM);
	}
    (*e)->name=name;
    (*e)->value=value;
    (*e)->next=NULL;
	//TODO declare new variable
}

//addLater remove
//context end




int main(int argc,char**argv){


	return 0;
}
