This is the release notes and breaking news related to the submission
for project 3 by Group 12.

It is organized into the following sections:

 * COMPILATION WARNING
 * APPROACH
 * COMPILING THE TEST PROGRAMS 
 * RUNNING THE TEST PROGRAMS


== COMPILATION WARNING ==

It is our experience that compiling on Tux using the provided Makefile is insufficient. This is because on Tux, they use the Eclipse Java Compiler (ejc) which requires more specific arguments than does the Javac which ships with Java6.

There are two approaches to circumvent this problem.

One can ensure that the Sun Java Development Kit appears first in the
$PATH, like so:

sh$ PATH=/usr/lib/jvm/java-6-sun/bin:$PATH
sh$ make

Or modify the top-level Makefile to pass additional arguments to
javac, so that it understands the source code version. An example
javac command-line would include:

javac -source 1.6 -target 1.6

and will be considerably less verbose if one also includes "-nowarn".

== APPROACH ==

We will speak to each of the parts of the project 3 assignment in turn.

=== Part 1 ===

This part was successfully implemented.

The VMProcess class has been created to extend from the UserProcess 
class as required.  Only functions that needed to be modified for the 
functionality changes were overridden.  

The handleTLBMiss function was added to deal with TLB misses.  The 
process hands control to the IPT, which first synchronizes the translation 
entries.  The IPT then pulls the entry into memory based on whether the entry 
is in the swap, is a stack entry, or is a coff entry.  Finally, it 
overwrites a random entry in the TLB with the new one.

On a context switch, the TLB entries are synchronized to update their status, 
then all the TLB entries are invalidated.

There is a single, global Inverted Page Table.  Since we have implemented 
demand paging and virtual memory, the discussion of the functionality of 
the IPT will be left to Part 2.

The Inverted Page Table has several hash maps (TODO: I think this is changing to just one.  The others should be in part 2).  
The first indexes a pid to a hash map that indexes vpns to an entry. 
This is used to find the swap aware entry based on the vpn and pid.  The 
second is the same structure as the first, but is used to store all the 
entries that are in the swap file.  The final map indexes a ppn to a 
list of TLB entries.  This is the actual contents of memory.

=== Part 2 ===

This part was successfully implemented.

To ease processing and storage of translation entries, we have implemented a 
swap aware translation entry.  This keeps track of whether the entry is coff or stack, 
the location of the swap, the coff page and section, and all other normal translation 
entry data.  It has a function to allow conversion to a normal translation entry. 

In order to implement demand paging, we first added a core map.  This 
is implemented using a hash map that maps ppns to the swap aware translation entry that 
is contained in that page.

In order to located entries in the swap, we have a separate hash map that maps 
pids to a hash map that maps vpns to swap aware translation entries.  

To ease testing, we implemented an interface called Algorithm which is used for 
page replacement.  We implemented both a random choice page replacement algorithm 
and a second chance clock algorithm, as described in the text.  Currently, the 
chosen class is hard-coded, so no runtime changes are possible.

Once a victim page is chosen, it is only written to swap if it is dirty.  
It is removed from the core map and any TLB entries are invalidated.

The swap file is handled as a file using the machine's provided filesystem.  
Since the swap aware translation entries track their location in the swap, 
the swap file class only has functions that move entries in and out of the 
swap file.  The swap file assumes that the calling function locked 
the memory as needed.  The VMProcess terminate function has been 
overridden to close the swap file in order to clean up.

=== Part 3 ===

This part was successfully implemented.

Lazy loading was achieved by overriding loadSections.  The function now 
calls IPT.addCoff().  This function creates page entries for each 
coff section, making sure to set the entry to read only.  It also 
creates an entry for each stack page.  None of the entries are added 
to the TLB, so they are loaded as the process attempts to read them.


== COMPILING THE TEST PROGRAMS ==

We have leveraged the existing Makefile based compilation mechanism,
as found in the "nachos/test" directory.

In order to use the Makefile, three preconditions must be met:

1. you must have the MIPS cross compilers installed

2. you must provide the path to the top-level MIPS cross-compiler
   directory in the Make variable named "ARCHDIR". This can be
   accomplished either by "exporting" a shell variable by the same
   name, or one may pass the make variable on the make command-line.

   It is our recommendation that one use the shell variable, as it
   reduces the error rate, but they are both equally effective.

3. you must include (preferably prepend) that same ARCHDIR to your 
   shell's "PATH" variable

For example, let's assume your MIPS cross-compiler is in a directory named
"/opt/mips". In this case, one would invoke make like so:

sh$ ARCHDIR=/opt/mips
sh$ export ARCHDIR
sh$ PATH=$ARCHDIR:$PATH
sh$ make

By default, the Makefile will build all COFF programs found in the
"TARGETS" make variable. In order to build any COFF program required
for exercising project 2 code, one may provide an alternative value of
the "TARGETS" make variable on the command-line, like so:

sh$ make TARGETS=sayhi

As one might expect, it is legal to specify multiple COFF programs
which should be built by including them in a space delimited list. Be
aware that you'll need to protect the spaces from your shell, usually
accomplished by including the entire expression in quotation marks, as
seen here:

sh$ make "TARGETS=50files bigmem"

== RUNNING THE TEST PROGRAMS ==

In this section, we will look at each of the test programs created for
the project, how to run them and what they are expected to prove (or
disprove).

//TODO: which coffs are run for this?
* 50files.coff:
  shows that we can open, close and unlink 50 files
* bigmem.coff:
  shows that the kernel will not load an executable 
  which does not fit in memory
* longFile.coff:
  shows that we successfully check the string length for calls to creat.
* LotsOfFiles.coff:
  shows that we are able to open, close and unlink a large number of files
* rmhi.coff:
  was an early test showing that we can unlink a file named "hi.txt"
* runnit.coff:
  shows that fork and join work correctly
  also shows that one can call join with various invalid arguments 
  and the correct error codes are returned

  Be aware that the SyncConsole is character buffered, not line
  buffered like wih Unix. This means that the output from echo.coff
  (the child process) is interleaved with the output from runnit.coff
  (the parent). It looks like garbage, but it's just interleaving. All
  characters are accounted for.

* sayhi.coff:
  was an early test showing that we can creat a file named "hi.txt",
  write characters to it and then read them back successfully

Due to a limitation with the way Nachos parses command-line arguments,
if one wishes to use the provided COFF programs (e.g. cat.coff,
cp.coff, echo.coff, etc), then one must start Nachos with "sh.coff"
and then invoke the COFF programs as children of the Nachos shell.
