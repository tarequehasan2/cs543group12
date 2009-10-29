This is the release notes and breaking news related to the submission
for project 2 by Group 12.

It is organized into the following sections:

 * APPROACH
 * COMPILING THE TEST PROGRAMS 
 * RUNNING THE TEST PROGRAMS

== APPROACH ==

We will speak to each of the parts of the project 2 assignment in turn.

=== Part 1 ===

In order to bullet-proof the calls, we

The invocation to halt() checks that the current process in the root
process, and otherwise silently returns with success.

All invocations in the syscall handlers use readVirtualMemory and
writeVirtualMemory as prescribed.

There was some discussion in BlackBoard about whether the string
length was inclusive or exclusive of the trailing zero. Our group
concluded that the objective was to have 256 byte strings, which
occupy 257 bytes in memory when the terminating character is
considered.

As prescribed, all syscall handlers return -1 to signal error. They
will return -1 on procedural errors, also, such as bogus memory
addresses or an out of bounds file descriptor.

All syscalls required for part 1 were implemented successfully.

=== Part 2 ===

We implemented UserKernel.malloc and UserKernel.free using the linked
list of free pages as recommended by the assignment. The UserKernel
will allocate pages in the order the linked list returns them, so it
will initially be contiguous and then more fragmented as the
UserKernel continues to run.

Since each process maps its virtual pages to physical pages, there
should be no need for the UserKernel to re-order the page numbers in
the "free pool". In fact, recent security advances in modern operating
systems take great steps to randomize allocated addresses to lower the
attack surface for stack overruns. Consider it a feature, not a bug. :-)

We modified readVirtualMemory and writeVirtualMemory to do virtual to
physical address translation. A current limitation in our
readVirtualMemory and writeVirtualMemory is that a read which crosses
a page boundary is not handled. We did not expect this was necessary
for our assignment. We know how to do it, and will be glad to
implement it if necessary for subsequent assignments.

=== Part 3 ===

=== Part 4 ===

The lottery scheduler extends the priority scheduler.  Although, in
practice, it uses very little of the functionality Priority donation
is achieved by calling methods in the superclass, although priority
donation is only used in pickNextThread() where it would not make
sense to use lottery functionality.  By definition of a
LotteryScheduler, pickNextThread could choose a different thread each
time and thus a different thread from nextThread; so we thought that
it would be just as useful to use the priority version from the
superclass.

With the LotteryScheduler, everything works correctly; however,
without having an active controlling thread, there is a higher
probability that the ready queue will be empty and the machine will
halt.  Therefore, it is wise to have a controlling thread join on all
forked threads so that there is always a thread in the ready queue.

The selfTest from the Priority Scheduler yielded the same results each
time, because there were not enough threads to hold a lottery that
would differ from run to run.  Self test was reimplemented on the
LotteryScheduler with many threads having varying priorities; as
expected, every run was different because different threads won the
lottery (not strictly based on priority).

We are unaware of anything that does not work as expected on the
lottery scheduler.


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

* 50files.coff:
* bigmem.coff:
* longFile.coff:
* LotsOfFiles.coff:
* rmhi.coff:
* runnit.coff:
* sayhi.coff:

Due to a limitation with the way Nachos parses command-line arguments,
if one wishes to use the provided COFF programs (e.g. cat.coff,
cp.coff, echo.coff, etc), then one must start Nachos with "sh.coff"
and then invoke the COFF programs as children of the Nachos shell.
