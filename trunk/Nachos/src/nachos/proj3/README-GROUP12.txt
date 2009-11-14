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
class as required. We had to increase the visibility of some of the methods
in order to support them being overridden or called from the subclass. We now
have a better understanding of how the classes are likely to be reused in
the subsequent projects, so we do not expect to make a similar mistake next
time. We also had to abstract out the direct access to the pageTable array
in UserProcess, otherwise we would have to override the entire method which
accessed the pageTable array.

The handleTLBMiss function was added to deal with TLB misses.  The
process hands control to the Inverted Page Table class, which we will refer to
herein via its acronym: IPT. We take advantage of the Processor calling into the
operating system to synchronizes the translation entries. This is so that our
page table entries can "learn" if the Processor used or wrote to any page it
was able to access via the TLB.

The IPT then pulls the entry into memory based on whether the entry
is in the swap, is a stack entry, or is a coff entry.  Finally, it
overwrites an entry of its choosing in the TLB with the new one.

On a context switch, the TLB entries are invalidated in order to force a TLB
miss when the current process regains focus. One cannot merely cache the TLB
entries because the OS is free to move the physical pages around "underneath"
a process, and the only way to recover that information is via the TLB miss
algorithm.

VMProcess also overrides readVirtualMemory and writeVirtualMemory. By doing so,
we can keep the used and dirty status bits in sync for syscall originated
reads and writes.

=== Part 2 ===

This part was successfully implemented.

We implemented a core map, as was requested. This map is keyed by physical
page number, and it holds a list of (pid,vpn) tuples. However, because we did
not implement section sharing (as discussed in the text), the list will always
be of size one for this project.

The Inverted Page Table has two maps to keep track of the page tables
for all processes in the system. The first indexes a pid to a map of its page
tables by virtual page number. The second is the same structure, but is used
exclusively for page tables that represent swap entries.

To ease processing and storage of translation entries, we have implemented a
swap aware translation entry, which resembles the vanilla Translation Entry but
with extra fields for our needs. It keeps track of whether the entry is a coff
page, or stack page, if the entry is in swap (and the location of the swap page),
the source coff page and section, in addition to the other normal translation
entry data. It has a function to allow conversion to a normal translation entry.

To ease testing, we implemented an interface called Algorithm which is used for
page replacement.  We implemented both a random choice page replacement algorithm
and a second chance clock algorithm, as described in the text. One may replace
the algorithm by editing the nachos.conf file.

Once a victim page is chosen, it is only written to swap if it is dirty.
It is removed from the core map, the bookkeeping entries in the IPT are updated
and any TLB entries pointing to that physical page are invalidated.

The swap file is handled as a file using the machine's provided filesystem.
Since the swap aware translation entries track their location in the swap,
the swap file class only has functions that move entries in and out of the
swap file.  The swap file assumes that the calling function locked
the memory as needed.  The VMProcess terminate function has been
overridden to close the swap file in order to clean up.

We used the provided coff files, including matmult.coff and sort.coff, in
combination with sh.coff to exercise the virtual memory manager heavily.

=== Part 3 ===

This part was successfully implemented.

Lazy loading was achieved by overriding loadSections.  The function now
calls IPT.addCoff().  This function creates page entries for each
coff section, making sure to set the entry to read only if it is declared as
such in the source coff.  It also creates a page table entry for each stack
page, plus one for the program's arguments (just like UserProcess does), but
does not actually allocate any memory at all during loadSections. We chose to
implement a "purely virtual" paging scheme, so none of the entries are loaded
into memory until their frames are referenced by a TLB miss.

When a coff is loaded, the number of stack frames is taken from the variable
in UserProcess, so no changes were made to that value for this project.

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

We did not create new coff programs for this project. We found the existing
ones exercised the memory subsystem well enough for our needs.

We made heavy use of matmult.coff, sort.coff, and repeated invocations of
echo.coff, cp.coff and rm.coff invoked from under sh.coff.

We used sh.coff not only because that is the only way to pass arguments, 
but also because having sh.coff already loaded in memory before we execute
any subprocess increases the strain upon the virtual memory subsystem.

We also found early in our debugging efforts that different randomizer seed
values (the "-s" argument to Nachos) produced varying degrees of success
when using the random page replacement algorithm (as one would expect). Thus,
in order to fully stress our system, we iteratively ran those coff files
using a sequence of "-s" values to minimize the chances of it "randomly
succeeding".

