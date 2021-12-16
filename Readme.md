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
 if dup 1 > :
   dup 1 - fib () swap 2 - fib () +
 elif 1 == :
   1
 else
   0
 end
end int int 1 1 -> $fib

#_ print 10th Fibonacci Number: _#
10 fib () print
```

## Syntax
The code is a sequence of instructions 
separated by whitespaces.

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

### Control Flow

#### If-Statements
If statements start with

```Python
if <condition> :
 <body>
```

followed by zero or more elif-sections

```Python
elif <condition> :
 <body>
```

and an optional else-block

```Python
else 
 <body>
```

they end with

```Julia
end
```

Examples:
```Julia
if a b > : a else b end

if a ! :
 "not a" println
end

if dup 0 == : drop
 "zero"
elif dup 1 == : drop
 "one"
elif 2 == :
 "two"
else 
 "may"
end 

println
```

#### while-loops
While loops have the syntax
```Julia
while <condition> :
 <body>
end
```

do-while loops have the syntax

```Julia
do 
  <body> 
while  <condition> end
```

### Procedures
!!!TODO!!!

### Variables
All commands that are not reserved names or values
are interpreted as variables.

- `<varName>` pushes the value of a variable on the stack
- `!<varName>` writes the top element of the stack
  in the given variable
- `:<varName>` declares a new variable
- `$<varName>` declares a new constant

Examples:
```Python
1 int :a #_ declare a as integer with value 1 _#
42 !a #_ store 42 in a _#
a println #_ print the value of a _#
3.14 float :a #_ redeclare a as float _#
2.718281828 float $e #_ declare a constant with the name e_#
```
#### Scopes
- At a given point all variables of procedures 
on the call-stack are accessible
- Variables declared in procedures will be invalidated 
once that procedure returns.
- Variable accessibility is determined at call-time 
not at declaration time
- Variables in Procedures may shadow global variables
- constants cannot overwrite/shadow existing variables
- constants cannot be shadowed by local variables

Examples:
```Rust
proc 
1 int :local1
proc2 ()
end 0 0 -> $proc1

proc 
1 int :local2
2 !local1
proc3 ()
end 0 0 -> $proc2

proc 
local1 local2 + print
end 0 0 -> $proc3

proc1 ()
```
works fine and prints `3`

But
```Rust
proc 
1 int :local1
proc
local1
end
end 0 0 -> 0 1 -> $proc1
proc1 () ()
```
crashes since `local1` is not accessible when calling 
the returned procedure

### Stack Manipulation
These Operations directly manipulate the stack without
interacting with the specific values
- `dup`  duplicates the top element on the stack
- `drop` removes the top element from the stack
- `swap` swaps the top 2 element on the stack
