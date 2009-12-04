This is the release notes and breaking news related to the submission
for project 4 by Group 12.

It is organized into the following sections:

 * COMPILATION WARNING
 * APPROACH
 * COMPILING THE TEST PROGRAMS
 * RUNNING THE TEST PROGRAMS


== COMPILATION WARNING ==

It is our experience that compiling on Tux using the provided Makefile
is insufficient. This is because on Tux, they use the Eclipse Java
Compiler (ejc) which requires more specific arguments than does the
Javac which ships with Java6.

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

We will speak to each of the parts of the project 4 assignment in turn.

=== Part 1 ===

This part was mostly implemented.

Currently, we know of the following bugs: 

1. Received data packets are not ordered by sequence number.

We created accept() and connect() handlers in NetProcess just like we
implemented the other system calls in UserProcess and friends.

We have tested our implementation with a drop rate as high as 75%
(reliability = 0.25). It takes some time while they shout at each other,
but it does work for a low enough volume of messages.

Our handlers guard against out of range port and host numbers.

We implemented an "accept guard" which prevents two programs on the
same Nachos node from both accept()ing on the same port. This is
restriction is lifted when the first process exits.

We assign a local port number for each connection, but it is part of the
tuple, so incoming packets may have the same local port but will be
routed to the correct process based on the entirety of the tuple.

We implemented read() as described, and included a mechanism for the
caller to read() less than the size of a packet (32 bytes). A read()
call which requires more than a packet will only receive the data which
is available at the time of read.

While we did not strictly implement the credit count as described, we do
force the send and receive windows to be 16 packets each, and those
windows are enforced per connection and per direction.

We require only three extra KThreads: one for the PostOffice delivery
mechanism (as it was by default), one for the Message Sender/Resender
(named "PostOfficeSender") and one for the packet Timer.

Our receive implementation discards Messages if the receive buffer is
full, and will also discard Messages it has seen before, however, it
will still ACK them, under the assumption that the other end did not
hear the first ACK.

The byte-stream communication is the bug which we reported
above. Currently, in cases of Message loss, it results in Messages being
provided to read() out of order (based on their sequence number). We
deeply regret this error, but simply ran out of time.

The calls to read() and write() do not block the caller.

=== Part 2 ===

This part was successfully implemented.

For this part, we implemented two coffs: chat and chatserver

The chat.c accepts its target host on the command line, and the target
port is a compiled-in constant. This constant is compiled into
chatserver.c, also. The port is 15, as prescribed.

Chatserver is implemented using a main loop.  It first checks for a new
connection, then sets up the fd for the new connection, then handles
reading the inputs from each of the connected clients in turn.  When it
receives input from a socket, it broadcasts that message to all the
clients.  If a client cannot be broadcast to, it is assumed that the
client has disconnected and the socket is closed.

Chat takes a single argument that is the network link address of the
chat server.  It first attempts to connect.  Once it has connected, it
enters the main loop.  The first part of the main loop is to read from
the socket and output any message that was broadcast from the server.
Next, it checks if the user has written anything.  If it received the
exit code, it exits.  Otherwise, it checks if the user entered a full
line.  If so, the message is sent to the server.

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

sh$ make TARGETS=chat

As one might expect, it is legal to specify multiple COFF programs
which should be built by including them in a space delimited list. Be
aware that you'll need to protect the spaces from your shell, usually
accomplished by including the entire expression in quotation marks, as
seen here:

sh$ make "TARGETS=chat chatserver"

== RUNNING THE TEST PROGRAMS ==

We utilized the two coffs that we created: chat.coff and chatserver.coff.  
These programs exercised all of the necessary pieces of the protocol 
in order for us to get to a point that we felt the implementation was 
complete.

Due to the way Nachos parses command line arguments, you may opt to run
chatserver.coff first (so it receives a network-id of 0), and then run
chat.coff (which will try and parse "chat.coff" as an integer, resulting
in zero, by coincidence the server's id). Or you can run chat.coff from
within sh.coff, just as before.

