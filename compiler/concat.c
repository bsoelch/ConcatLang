/*
  implementations for the functions in common.h
 */

#include "common.h"
#include "stdlib.h"
#include "stdio.h"

void stack_dup(Stack* stack,size_t count){
	value_t duped=stack->data[stack->size-count];
	stack->data[stack->size++]=duped;
}
void stack_drop(Stack* stack,size_t off,size_t count){
	memmove(stack->data+stack->size-(off+count),stack->data+stack->size-off,off*sizeof(value_t));
	stack->size-=count;
}
void stack_rot(Stack* stack,size_t count,size_t steps){
	memmove(stack->data+stack->size      ,stack->data+stack->size-count,steps*sizeof(value_t));
	memmove(stack->data+stack->size-count,stack->data+stack->size-count+steps,count*sizeof(value_t));
}

void ptr_call(Stack* stack){
	stack->size-=2;
	((fptr_t)(stack->data[stack->size].asPtr))(stack,(value_t*)stack->data[stack->size+1].asPtr);
}



//test-functions
void testFunction(Stack* stack,value_t* curried){
	printf("called test %p\n",curried);
	if(curried!=NULL){
		printf("curried[0]:%"PRIu64"\n",curried[0].asU64);
	}
}
static void printValues(value_t* data,size_t count){
	fputs("[",stdout);
	for(size_t i=0;i<count;i++){
		printf("%"PRIu64", ",data[i].asU64);
	}
	puts("..]");
}
int main(void){
	value_t data[100];
	Stack s={.data=data,.size=0,.capacity=100};
	s.data[s.size++]=(value_t){.asU64=1};
	s.data[s.size++]=(value_t){.asU64=2};
	s.data[s.size++]=(value_t){.asU64=3};
	s.data[s.size++]=(value_t){.asU64=4};
	printValues(s.data,s.size);
	stack_dup(&s,1);
	printValues(s.data,s.size);
	stack_drop(&s,0,2);
	printValues(s.data,s.size);
	stack_dup(&s,2);
	printValues(s.data,s.size);
	stack_drop(&s,2,1);
	printValues(s.data,s.size);
	stack_rot(&s,3,1);
	printValues(s.data,s.size);
	s.data[s.size++]=(value_t){.asFPtr=&testFunction};
	s.data[s.size++]=(value_t){.asPtr=(value_t[]){(value_t){.asU64=1},(value_t){.asU64=2},(value_t){.asU64=3}}};
	ptr_call(&s);
}
