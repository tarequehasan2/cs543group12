This is the release notes and breaking news related to the submission
for project 2 by Group 12.

It is organized into the following sections:

 * APPROACH
 * COMPILING THE TEST PROGRAMS 
 * RUNNING THE TEST PROGRAMS

== APPROACH ==

We will speak to each of the parts of the project 2 assignment in turn.

=== Part 1 ===

=== Part 2 ===

=== Part 3 ===

=== Part 4 ===


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
