This is the release notes and breaking news related to the submission
for project 4 by Group 12.

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

We will speak to each of the parts of the project 4 assignment in turn.

=== Part 1 ===

This part was mostly implemented.
Currently, we know of two bugs: 
1. Duplicate data packets are not dropped.
2. Received data packets are not ordered by sequence number.

For our implementation, we extended the PostOffice.

To ease the correctness checks, we implemented a 
SocketState class.  This class holds all the possible 
states for the socket.  We also implemented a SocketEvent 
class that contains the events for the state diagram.  These 
together are used to check for valid program flow.

For each of tracking connections, we created a SocketKey class.  
This class is simply the tuple for the connection as defined by 
the protocol.

In order to implement the protocol headers, we created a class called 
NachosMessage.  This class contains the payload and all the header 
information defined by the protocol.  It is used to encode and decode 
packets and to read the header information from packets.

NetProcess extends from VMProcess and simply 
adds the accept and connect system handlers.  These functions 
are implemented on the kernel, which is discussed next.

NetKernel extends from VMKernel.  Initializations were added 
for the PostOffice, the PostOfficeSender, and the Dispatcher.  
Additionally, threads for the PostOfficeSender and 
Dispatcher are forked during initialization.  Each of these 
will be discussed in detail below.  

Accept checks to determine if the process is already waiting 
on the port.  If the port does not have a syn message, then 
the function returns immediately.  If there is one waiting, 
the dispatcher is used to send a SYN-ACK (using the PostOfficeSender), 
set the state to established, and we return the socket key.  
The process then uses the key to create a file descriptor.

Connect hands the processing off to the dispatcher.  Here, 
it first determines if there is a free local port and assigns 
the socket one of the local port.  It then uses the 
PostOfficeSender to send a SYN.  Next, it creates a 
condition variable to sleep until a SYN-ACK is returned.  
Then, it returns the key back to the process where 
the process creates a file descriptor for the socket.

The Close function hands control to the kernel, which 
in turn goes to the dispatcher.  The dispatcher first 
checks if the socket is already closed.  If not, it 
checks if there are messages waiting to be sent.  If there 
are no messages sending, then a FIN is sent.  If there 
are messages sending, then a STP is sent to stop incoming 
messages.  The state of the socket is then set to CLOSED.  
The PostOfficeSender and dispatcher handle all the sending 
asynchronously, so no special code is needed to continue sending 
after we set the socket to closed.

Read uses the kernel to get the next piece of data from the dispatcher.  
The dispatcher checks the received messages queue for this 
socket key and returns the next message.  If there is no message, 
the dispatcher and kernel return immediately.  If there is a message, 
the kernel checks the length.  If the length of the message is greater 
than what was asked for, the remaining data is pushed back into the 
received queue in the dispatcher.  The data is the returned to the process.

Write hands control to the kernel.  Here, the message is chunked into 
packet payload sized chunks.  Each chunk is inserted into a message 
and the PostOfficeSender is called to send the message.\

The Timer for the PostOfficeSender and Dispatcher attempts to resend 
all unacked messages and clear all closed sockets.

The Dispatcher handles all incoming messages.  If the message is a 
data message, it's put in the appropriate queue based on the 
socket key.  If it's a SYN, the state is set to SYN received.  
If it's a SYN-ACK, the state is set to estabilshed, and the 
waiting process is woken up.  If it's an ACK, the message is 
removed from the PostOfficeSender's unacked message queue.  
If it's a STP, send an ACK, tell the PostOfficeSender to stop 
sending.  If it's a FIN, send a FIN-ACK.  If it's a FIN-ACK, 
then tell the PostOfficeSender to close and deallocate the 
socket.

The PostOfficeSender first sets the sequence number of the message
if needed.  It then sends the message.  It then adds the message 
to the unacked queue if necessary.  

Additional notes:
-The sliding window is implemented by simply limiting the 
size of the sent and received queues to 16.
-There are 2 main sender threads.  One for sending all messages and one 
for resending unacked messages and clearing closing states.

=== Part 2 ===

This part was successfully implemented.

For this part, we implemented two coffs: chat and chatserver

Chatserver is implemented using a main loop.  It first checks 
for a new connection, then sets up the fd for the new connection, 
then handles reading the inputs from each of the connected clients 
in turn.  When it receives input from a socket, it broadcasts 
that message to all the clients.  If a client cannot be broadcast to, 
it is assumed that the client has disconnected and the socket is 
closed.  

Chat takes a single argument that is the network link address of the 
chat server.  It first attempts to connect.  Once it has connected, 
it enters the main loop.  The first part of the main loop is to 
read from the socket and output any message that was broadcast from 
the server.  Next, it checks if the user has written anything.  If 
it received the exit code, it exits.  Otherwise, it checks if the 
user entered a full line.  If so, the message is sent to the server.

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

We utilized the two coffs that we created: chat.coff and chatserver.coff.  
These programs exercised all of the necessary pieces of the protocol 
in order for us to get to a point that we felt the implementation was 
complete.

