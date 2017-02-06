# Design Document for Nachos : Phase 1: Building a Thread System

Authors: 
Group 1

## Task I: Implement `KThread` Joining

[[TK: This is an introductory paragraph about the solution to Task I. It doesn’t need to be very long, since
the majority of your choices relate specifically to the join() method, but it should communicate your
understanding of the problem. Describe any instance variables you add to the KThread class.]]

### Implementing the `join()` Method

This thread.join() 


[[TK: Discuss the choices you have made to implement this method. You will (hopefully) spend several paragraphs
describing the logic behind your solution.

Once you have introduced your implementation strategy, you should give a listing of your pseudocode in
a monospaced font. Also consider using this font to make method names stand out in your discussion. If you
are using LATEX, I suggest using the verbatim environment for any pseudocode listings.]]

```
void join()
{
  check if the thread queue exists
  if it doesn't exit
    create ThreadQueue object
  disable the interopts
  if this thread is not the current thread and the current thread is running
    add this thread to queue
  else
    run next thread

  reenable interrupts
}
```

### Other Sections You Might Include

[[TK: If there are specific topics you feel are important to discuss, add another subsection for each. For example,
you may want to talk about thread atomicity in general, as opposed to discussing it within the subsections
containing pseudocode.]]

### Test Cases for Task I

[[TK: This is an example of how you might format your test cases. I chose four test cases for the purposes of
this document, but this should not be considered an indication of how many test cases you are expected to
include. However, you will most likely have at least 3 or 4 test cases per task.

1. Description of the first test case:
     the expected result of the first test case
2. Description of the second test case:
     the expected result of the second test case
3. Description of the third test case:
     the expected result of the third test case
4. Description of the fourth test case:
     the expected result of the fourth test case
     
]]

## Task II: Implementing the `Condition2` Class

[[TK: This is an introductory paragraph about the solution to Task II. Once again, communicate your understanding
of the problem. You could also describe any data structures you will use to implement your solution.]]

### Implementing the `sleep()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void sleep()
{
  disable interupts
  set this thread status to 'blocked'
  reenable interrupts
}
```

### Implementing the `wake()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void wake()
{
  disable interupts
  if the first item on the readyQueue is a KThread
    call KThread.ready()
  reenable interupts
}
```

### Implementing the wakeAll() Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void wakeAll()
{
  DI
  if readyQueue is not empty
    for each KThread in the readyQueue
      wake the KThread
  RI
}
```

### Test Cases for Task II

[[TK:

1. Description of the first test case:
     the expected result of the first test case
2. Description of the second test case:
     the expected result of the second test case
3. Description of the third test case:
     the expected result of the third test case
4. Description of the fourth test case:
     the expected result of the fourth test case

]]

## Task III: Implementing Alarm

[[TK: This is an introductory paragraph about the solution to Task III. Communicate your understanding of the
problem and describe any data structures you will use to implement your solution. You may also choose to
implement a new class, so explain why you have chosen to do so.]]

### New Classes

[[TK: Create a subsection for any new classes you might need to implement to solve this task. You should describe
why each class is useful in solving the task. If possible, provide pseudocode.]]

```
MyClass implements SomeClass
{
  Datatype variableName;

  // Constructor
  public MyClass(Class1 var1, Class2 var2)
  {
      ...
  }
  ...
  }
```

### Implementing the `timerInterrupt()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void timerInterrupt()
{
  disable interrupts
  if readyQueue is ....
    curretThread.yield();
    wake();
  enable interupts
}
```

### Implementing the `waitUntil()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void waitUntil(long x)
{
  get start time
  while getTime < start time + x
    KThread.sleep()
}
```

### Private Methods

[[TK: Talk about any private methods you have added (for convenience) and provide pseudocode. If you decide to
implement private methods for both timerInterrupt() and waitUntil(), consider placing the discussion
for each within the relevant subsection.]]

### Test Cases for Task III

[[TK:

1. Description of the first test case:
     the expected result of the first test case
2. Description of the second test case:
     the expected result of the second test case
3. Description of the third test case:
     the expected result of the third test case
4. Description of the fourth test case:
     the expected result of the fourth test case

]]

## Task IV: Implementing `Communicator`

[[TK: This is an introductory paragraph about the solution to Task IV. Again, communicate your understanding
of the problem. Describe any instance variables you add to the Communicator class.]]

### The `speak()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void speak(int word)
{
  // This is the pseudocode listing for speak()
}
```

### The `listen()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
int listen()
{
  // This is the pseudocode listing for listen()
}
```

### Test Cases for Task IV

[[TK:

1. Description of the first test case:
     the expected result of the first test case
2. Description of the second test case:
     the expected result of the second test case
3. Description of the third test case:
     the expected result of the third test case
4. Description of the fourth test case:
     the expected result of the fourth test case

]]


## Task V: Implementing `ReactWater`

[[TK: This is an introductory paragraph about the solution to Task V. Once again, communicate your understanding
of the problem. Describe any instance variables you add to the ReactWater class.]]

### The `ReactWater` Constructor

[[TK: If you are adding any instance variables, you will most likely want to initialize them here. Describe the
purpose of this constructor and give a pseudocode listing.]]

```
public ReactWater()
{
  // This is the pseudocode listing for the ReactWater constructor
}
```

### The `hReady()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void hReady()
{
  // This is the pseudocode listing for hReady()
}
```

### The `oReady()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void oReady()
{
  // This is the pseudocode listing for oReady()
}
```

### The `Makewater()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void Makewater()
{
  // This is the pseudocode listing for Makewater()
}
```

## Task V: Implementing `ReactWater`

[[TK: This is an introductory paragraph about the solution to Task V. Once again, communicate your understanding
of the problem. Describe any instance variables you add to the ReactWater class.]]

### The `ReactWater` Constructor

[[TK: If you are adding any instance variables, you will most likely want to initialize them here. Describe the
purpose of this constructor and give a pseudocode listing.]]

```
public ReactWater()
{
  // This is the pseudocode listing for the ReactWater constructor
}
```

### The `hReady()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void hReady()
{
// This is the pseudocode listing for hReady()
}
```

### The `oReady()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void oReady()
{
  // This is the pseudocode listing for oReady()
}
```

### The `Makewater()` Method

[[TK: Discuss the choices you have made to implement this method.]]

```
void Makewater()
{
  // This is the pseudocode listing for Makewater()
}
```

## Test Cases for Task V

[[TK:

1. Description of the first test case:
     the expected result of the first test case
2. Description of the second test case:
     the expected result of the second test case
3. Description of the third test case:
     the expected result of the third test case
4. Description of the fourth test case:
     the expected result of the fourth test case
     
]]
