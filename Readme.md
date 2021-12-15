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

#_ print 10th Fibonacci Number: _#
10 fib () print
```

## Syntax
All Operations in this programming language
interact with the global evaluation stack by
pulling a given number of values, preforming 
an operation on that values and then pushing the result
### Comments
- `##` comments out the rest of the list
- Inline comments are surrounded by `#_` and `_#`
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
```C++
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
Operators are evaluated in postfix notation
i.e 1 1 + evaluated to 2
#### Arithmetic Operations
- `-_` negates the top element on the stack
- `+` addition `a b +` evaluates to `a+b`
- `-` subtraction `a b -` evaluates to `a-b`
- `/_` inverts the top element on the stack
- `*` multiplication `a b *` evaluates to `a*b`
- `/` division `a b /` evaluates to `a/b`
- `%` remainder `a b %` evaluates to `a%b`
- `**` power `a b **` evaluates to `pow(a,b)`

Examples:
```C++
1 2 + 3 * 4.0 5 / -
6 -_ 7 % 
8 9 **
```
leaves the values
`int:134217728` `int:-6` `float:8.2`
on the stack

#### Bitwise Operations
- `!` logical not
- `~` flips all bits in the top element on the stack
- `&` (bitwise) logical and
- `|` (bitwise) logical or
- `xor` (bitwise) logical xor
- `>>` right-shift
- `<<` left-shift
- `.>>` arithmetic right-shift
- `.<<` arithmetic left-shift

Examples:
```C++
false ! false xor true & false |
1 ~ 7 &
1 2 <<
-1 2 .>>
```
leaves the values
`int:-1` `int:4` `int:6` `bool:true`
on the stack

#### Comparison
`==` `!=` check if the top two elements on the stack have 
the same value, and push the result on the stack
`<` `<=` `>=` `>` compare the top two elements on the 
stack and push a bool depending on the result 
of the comparison.
comparison either needs two strings, two chars or 
two values of type int or float

Examples:
```C++
int int == print
1 2.0 > print
"Hello" "World" <= print
'A' 'a' > print
```
prints 
```C++
true
false
true
false
```
### other operators
- `typeof` replaces the top element on the stack 
with its type
- `cast` typecast `val type cast` casts `val` to 
type `type` and pushes the result
- `list` creates a list-type from the top stack element
- `{}` creates a new array (syntax:
`<elements> <type> <count> {}`)
- `[]` array access
- `->` creates a procedure-type (syntax:
  `<in-args> <out-args> <in-count> <out-count> '->'`)
- `()` call a procedure

Examples:
```C++
1 typeof print
3.1 int cast print
int list list drop ## list of list of ints
1 2 3 float 3 {} dup print
1 [] print
int int 1 1 -> drop ## procedure-type: in:[int] out:[int]
4 fib () #_ call procedure fib with argument 4 _#
```
prints 
```C++
int
3
[1.0, 2.0, 3.0]
2.0
```

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
