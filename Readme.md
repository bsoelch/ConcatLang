# ConcatLang
is a stack-based [Concatenative programming language](https://en.wikipedia.org/wiki/Concatenative_programming_language)

!!! this language is currently in early development !!!

## Examples

Hello World:

```
io #include
core #import ## to use puts in global scope

"Hello World\n" puts
```

Recursive Fibonacci Numbers:
```
stack #include ## for usage of dup and swap
valueIO #include ## for println
core #import ## to use println in global scope

fib proc int => int :
   dup 1 >  if 
   dup 1 - fib swap 2 - fib +
 else 1 == _if 
   1
 else
   0
 end
end

#+ print 10th Fibonacci Number: +#
10 fib println
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
- Inline comments are surrounded by `#+` and `+#`
### Values
Writing a Value in the source code simply pushes that value
on the stack, the natively supported value types are

- booleans
  - `true` or `false` 
- integers (binary, decimal or hexadecimal)
  - integer literal
  - the prefixes `0b` and `0x` signal binary and hexadecimal numbers
  - the postfix `u` signals unsigned numbers
- floats (binary, decimal or hexadecimal)
  - floating point literal literal
  - the prefixes `0b` and `0x` signal binary and hexadecimal numbers
- bytes
  - char-literal (of corresponding ascii-character)
  - or int-literal followed by `byte cast`
- codepoints
  - char-literal prefixed with `u`
- strings (byte list)
  - string-literals
- unicode-strings (codepoints list)
  - string-literals prefixed with `u`
- types
  - the type name as plain text

Examples: 
```
true false
0xffffffffffffffffu
1 -0b10
1.0E10 0x1P-1 
'A'
int type
"Hello World"
```
leaves the following values on the stack:
`string:"Hello World"` `type:type`
`type:int` `byte:A` `float:0.0625`
`float:1E10` `int:-2` `int:1`
`uint:18446744073709551615`
`bool:false` `bool:true` 

### IO
#### Native IO
- `debugPrint` prints the to value on the stack 
(this operation will not be supported in compiled code)


[//]: # (TODO file io)

#### Standard Library
in `io`
in namespace `io`
- `open` arguments: `<path> <options>`
  opens the file at `path`, `options` a string
  containing `r` for reading and `w` for writing
- `stdIn`,`stdOut`,`stdErr`
  standard input, output and error streams
- `close` closes a file stream returns `true`
  if the close operation was successful, `false` otherwise
- `read`
- `write`
- `size`
- `pos`
- `truncate`
- `seek`
- `seekEnd`

in namespace `core`
- `fputs` prints a string to a file
(arguments `<string> <file>`)
- `puts` prints a string to standard output
- `eputs` prints a string to `stderr`

in `valueIO`
- `print` removes and prints the top value on the stack
- `println` like `print` but adds a new-line add the end
- `fprint` (arguments: `<value> <file>`) 
prints `<value>` to `<file>`
- `printf` (arguments: `<argCount>` `<formatString>`)
formatted printing of values using `<formatString>` 
expects exactly `<argCount>` elements from the stack 
as parameters for the format string
- `fprintf` like `printf` but with an additional 
first argument of the input stream

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
```
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
(signed/unsigned depending on the type of the left integer)
- `<<` left-shift
(signed/unsigned depending on the type of the left integer)

Examples:
```
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
two numbers

Examples:
```
int int == println
1 2.0 > println
"Hello" "World" <= println
'A' 'a' > println
```
prints 
```
true
false
true
false
```

### Optionals 
Optionals hold an optional value.

operators for interacting with optionals

- `wrap`   wraps the top value on the stack in an optional
- `??` checks if the optional is present
  - if the optional has a value it will push true
  - if no value is present it will push false 
  - this operation does not consume the optional
- `!` consumes the optional and pushes 
  `true` if the optional is empty and `false` otherwise


unwrapping optionals:

If a nonempty optional is used as the parameter of
`if` `_if` or `do` the unwrapped value of the optional 
is pushed on the stack 
(if the optional is empty no value will be pushed on the stack)


examples for interacting with optional can be found 
in the next section.

### Iterators
Iterators simplify iterating over all elements of a list,
they are designed to work well with for-each loops.
Iterators are defined in the standard library and 
can be included with `iterators include`, iterators 
also includes [optional](https://github.com/bsoelch/ConcatLang#optionals)

- `^_` created a new iterator at the start of 
the current element (the current element has to be a list)
- `_^` created a new iterator at the end of
  the current element (the current element has to be a list)
- `^>` moves the iterator to the next element,
  - if the list has a next element the iterator pushes
    itself, and an optional wrapping the next element
  - otherwise, the iterator pushes itself,
    followed by an empty optional
- `<^` moves the iterator to the previous element
  - if the list has a previous element the iterator pushes 
    itself, and an optional wrapping the previous element
  - otherwise, the iterator pushes itself,
    followed by an empty optional

Examples:
template of for-each loop:
```
array ^_ ## create iterator
while ^> do   ## iterate over all elements
  println ## do something with data
end 
drop ## drop the iterator
```

reverse a string:
```
reverse proc string => string :
  string toReverse =:
  ## store type and length of the list
  toReverse length string new string res =:
  ## Iterate though the elements in reverse order
  toReverse _^ while <^ do
    res swap << res =
  end drop
  res return
end
```
Sum all elements of a list
```
0 var tmp =: ## Initialize sum to 0
## Iterate though all elements of the list
^_ while ^> do
 tmp swap + tmp =
end drop
tmp ## load the total sum onto the stack
```

### other operators
- `.type` replaces the top element on the stack 
with its type
- `cast` typecast `val type cast` casts `val` to 
type `type` and pushes the result
- `list`  wraps a type in the corresponding list-type
- `optional`  wraps a type in the corresponding optional-type
- `.content` unwraps list and optional types
- `>>` `<<` add a new element at the start/end of a list
- `*>>` `<<*`   concatenates two lists, changes the value of 
the argument on the side of the `:`
- `[]` get an element of a list
   - syntax: `<list> <index> []`
- `[]=`  set an element of a list
  - syntax: `<list> <value> <index> []=`
  - the element at index will be set to value cast to 
the type of the list-elements
- `[:]`  get a sublist of a list
  - syntax: `<list> <off> <to> [:]`
  - returns a new list containing the elements 
of the list with indices between `<off>` included
and `<to>` excluded
- `[:]=` replace a sublist of a list
  - syntax: `<list> <value> <off> <to> [:]=`
  - all the specified section of the list will be replaced 
with the new value cast to the type of the list
- `()` call a procedure pointer

Examples:
```
1 .type println
3.1 int cast println
int list list drop ## list of list of ints
"Hello" ' ' "World" >> <<* '!' << println
"Hello World!" 7 9 [:] println
"Hello World?" '!' 11 []= println
"Hello World!" "Programmer" 6 11 [:]= println
"Hello World!" "" 5 11 [:]= println
```
prints 
```
int
3
Hello World!
or
Hello World!
Hello Programmer!
Hello!
```

### Stack Manipulation
These Operations directly manipulate the stack without
interacting with the specific values

[//]: # (TODO $dup $drop $set)

- `dup`  duplicates the top element on the stack
  (can be included with `stack #include`)
- `drop` removes the top element from the stack
  (can be included with `stack #include`)
- `swap` swaps the top 2 element on the stack
  (can be included with `stack #include`)
- `clone` replaces the top element on the stack 
with a shallow copy
- `clone!` replaces the top element on the stack
  with a deep copy


### Control Flow

#### If-Statements
If statements start with

```C
<condition> if 
 <body>
```

followed by zero or more else-_if-sections

```C
else <condition> _if
 <body>
```

and an optional else-block

```C
else 
 <body>
```

they end with

```Julia
end
```

Examples:
```Julia
a b > if a else b end

a ! if 
 "not a" println
end

dup 0 == if  drop
 "zero"
else dup 1 == _if drop
 "one"
else 2 == _if
 "two"
else 
 "many"
end string count =:
```

#### while-loops
While loops have the syntax
```Julia
while <condition> do
 <body>
end
```

do-while loops have the syntax

```Julia
while 
  <body> 
<condition> do end
```

### Procedures
Procedures are code blocks that can be called 
from other points in the program. 
#### Syntax
Procedures are declared in blocks starting with
the name of the procedure, followed by
`proc` or `procedure` then the input arguments
followed by `=>` then the output arguments
followed by `:` then the body of the procedure 
followed by end `end`.
```
<Name> proc <In1> <In...> <InN> => <Out1> <Out...> <OutN> :
 <body>
end
```
The return instruction allows returning from a
procedure before reaching the end.

all appearances of the name of a procedure 
will be resolved as a procedure call, even 
if that procedure is declared later in the same file

#### Lambda-Procedures and Procedure-Pointers
Lambda procedures don't have a name and use `lambda` or `λ` 
instead of proc, unlike normal procedures lambda-procedures
push a procedure-pointer procedure onto the stack

If the name of a procedure appears in the code, 
that procedure is called directly,
if the name is prefixed with a `@` then a pointer to 
that procedure will be pushed onto the stack.

Procedure pointers can be called with the call-operator `()`

#### procedure Examples
Examples:
```
## procedure for recursivly printing the fibonacci numbers
fib proc int => int :
   dup 1 >  if 
   dup 1 - fib swap 2 - fib +
 else 1 == _if
   1
 else
   0
 end
end
## mutually recursive functions
isEven proc int => bool :
  dup 0 == if 
    true return
  else
    1 - isOdd return
  end
end
isOdd proc int => bool :
      dup 0 ==  if 
      false return
  else dup 0 < _if
     -_ isOdd return
  else
    1 - isEven return
  end
end

42 fib println ## prints the 42nd fibonacci number
10 isEven println
-143 isEven println
1 
{ @isEven @isOdd } 
1 [] () println 

lambda int => int : dup * end ( int => int ) square =:
4 square () println

```
prints:
```
267914296
true
false
true
16
```

### Variables
All commands that are not reserved names or values
are interpreted as variables.
All variables perform a read action on default
#### Variable modification operators
These operators change the type a variable
they are evaluated while parsing and therefor only work if 
placed directly after the corresponding variable
- `=` change read variable to write variable
- `=:` change read-variable to declare-variable
- `=$` change read-variable to declare-constant

Examples:
```Python
1 int a =: #+ declare a as integer with value 1 +#
42 a = #+ store 42 in a +#
a println  #+ print the value of a +#
3.14 float a =: #+ redeclare a as float +#
2.718281828 float e =$ #+ declare a constant with the name e+#
```

#### Scopes
!!! TODO !!!

[//]: # (TODO Scopes)

### Multi-File Projects
The keyword `#include` allows including 
other files into the source code.
The included file is determined by the token preceding `#include`
- if the preceding token is an identifier, 
the library file with that name is included, 
- if it is a string, the file at the given path 
will be included.

Each file is included exactly once, 
the global code of an included file is executed 
at the position of the first include of that file.

Example:

If the file `/path/to/a/file.concat` contains the code
```C
"included file" println
```
then 
```C
stack #include
"/path/to/a/file.concat" #include
"/path/to/a/file.concat" #include
```
includes the stack-macros from `lib/stack.concat`
and the prints `included file` (once)


