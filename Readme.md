# ConcatLang
is s stack-based [Concatenative programming language](https://en.wikipedia.org/wiki/Concatenative_programming_language)

!!! this language is currently in early development !!!

## Examples

Hello World:

```Python
"Hello World" print
```

Recursive Fibonacci Numbers:
```Python
proc
 if dup 1 < : 
   drop 0
 elif dup 1 == : 
   drop 1
 else
   dup 1 - fib () swap 2 - fib () +
 end
end int int 1 1 -> $fib

#_ print 10th fibonacci Number: _#
10 fib () print
```

## Syntax
All Operations in this programming language
interact with the global evaluation stack by
pulling a given number of values, preforming 
an operation on that values and then pushing the result
### Values
Writing a Value in the source code simply pushes that value
on the stack, the natively supported value types are

- booleans
- integers (binary, decimal or hexadecimal)
- floats (binary, decimal or hexadecimal)
- chars
- types
- strings (char list)

Examples: 
```
true false
1 -0b10
1.0E10 0x1P-1 
'A'
int type
"Hello World"
```
leaves the following values on the stack:
`char list:"Hello World"` `type:type`
`type:int` `char:A` `float:0.0625`
`float:1E10` `int:-2` `int:1`
`bool:false` `bool:true` 

### IO
- `print` removes and prints the top value on the stack

(there will be more IO operations)

### Operators
!!! TODO !!!

### Variables
!!! TODO !!!

### Control Flow
!!! TODO !!!

### Procedures
!!!TODO!!!

### Stack Manipulation
These Operations directly manipulate the stack without
interacting with the specific values
- `dup`  duplicates the top element on the stack
- `drop` removes the top element from the stack
- `swap` swaps the top 2 element on the stack
