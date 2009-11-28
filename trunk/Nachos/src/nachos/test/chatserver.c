#include "stdio.h"

#define CHAT_PORT 15

#ifdef WITH_DEBUG
#define DEBUG(...) fprintf(debugFD, __VA_ARGS__)
#else
#define DEBUG(...) if (0) printf(".")
#endif

#ifndef EXIT_FAILURE
#define EXIT_FAILURE 1
#endif/*EXIT_FAILURE*/

static char buffer[1024];
static int  buf_len;
static FILE client_sockets[1024];
static int  numClients;

/*
 * Implements the getchar function except without blocking.
 * @return the read character or -1 if no character is available.
 */
int non_blocking_getchar();
/*
 * Implements the fgetc function except without blocking.
 * @param fd the file descriptor from which to read.
 * @return the read character or -1 if no character is available.
 */
int non_blocking_fgetc(FILE fd);
/*
 * Transmits the contents of buffer[0 .. buf_len] to all the registered
 * client sockets in client_sockets. It also will close() and then
 * drop_client() if unable to write to a socket.
 */
void broadcast();
/*
 * Removes the client socket identified by the 0-indexed parameter.
 * @param clientNum the 0-indexed client socket number.
 */
void drop_client(int clientNum);

int main(int argc, char* argv[])
{
  FILE sock;
  int  i;
#ifdef WITH_DEBUG
  FILE debugFD;
  if (-1 == (debugFD = creat("server.debug.log"))) {
    printf("ERROR:unable to creat server.debug.log\n");
    return EXIT_FAILURE;
  }
#endif

  numClients = 0; /* explicitly initialize it */
  buf_len = 0; /* explicitly initialize it */

  do {
    DEBUG("accepting...\n");
    if (-1 == (sock = accept(CHAT_PORT))) {
      /* no client? check for charpress on stdin */
      if (-1 != non_blocking_getchar()) {
        DEBUG("getchar said something, bye!\n");
        break;
      }
    }
    if (-1 != sock) {
      printf("Welcome, chat client on %d\n", sock);
      client_sockets[numClients] = sock;
      numClients++;
    }
    DEBUG("checking all clients\n");
    /* see if anyone said anything */
    for (i = 0; i < numClients; i++) {
      int ch;
      if (-1 != (ch = non_blocking_fgetc(client_sockets[i]))) {
        DEBUG("client %d said something\n", i);
        buffer[ buf_len ] = ch;
        buf_len++;
        if (buf_len > sizeof(buffer) || '\n' == ch) {
          DEBUG("broadcasting\n");
          broadcast();
          buf_len = 0;
        }
      }
    }
    DEBUG("loop...\n");
  } while (1 == 1);
  DEBUG("closing everyone down\n");
  for (i = 0; i < numClients; i++) {
    if (-1 == close(client_sockets[i])) {
      printf("Unable to close socket[%d]\n", i);
      /* not fatal, keep going */
    }
  }
  return 0;
}

void broadcast()
{
  int i;
  int bytesWritten;
  /* have to run backwards so we don't write to a sock twice if one fails;
   * let's consider a client_sockets of size 6:
   * observe that if sock[3] fails, and we're going up, then we'll swap
   * sock[5] into 3 and thus 5 will get skipped because we just attempted to
   * write to the sock at the 3rd position (now containing 5);
   * however, if sock[3] fails and we're going down, then we will have already
   * written to sock[5], which gets swapped into 3, no harm done.
   */
  for (i = numClients - 1; i >= 0; i--) {
    FILE client_fd = client_sockets[i];
    bytesWritten = write(client_fd, buffer, buf_len);
    if (buf_len != bytesWritten) {
      printf("ERROR:short write on sock(%d), wanted %d, got %d\n",
          i, buf_len, bytesWritten);
      /* attempt to free the resources */
      if (-1 == close(client_fd)) {
        printf("Unable to close socket %d\n", i);
      }
      drop_client(i);
    }
  }
}

void drop_client(int clientNum)
{
  /* if it's the last one, just drop it off the list */
  if ((numClients - 1) == clientNum) {
    numClients--;
  } else {
    /* grab the last socket, so we can "promote" it into the dead slot */
    int end_sock = client_sockets[numClients - 1];
    numClients--;
    client_sockets[clientNum] = end_sock;
  }
}

int non_blocking_getchar()
{
  return non_blocking_fgetc(stdin);
}

int non_blocking_fgetc(FILE fd)
{
  int ch;
  if (1 == read(fd, &ch, 1)) {
    return ch;
  } else {
    return -1;
  }
}
