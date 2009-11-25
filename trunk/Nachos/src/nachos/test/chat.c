#include "stdio.h"

#define CHAT_PORT 15

#ifndef EXIT_FAILURE
#define EXIT_FAILURE 1
#endif/*EXIT_FAILURE*/

int main(int argc, char* argv[])
{
  int host;
  int sock;
  int bytesWritten;
  int bytesRead;
  char buffer[1024];

  if (1 == argc) {
    printf("Usage: %s server-link-address\n", argv[0]);
    return EXIT_FAILURE;
  }
  host = atoi(argv[1]);
  if (-1 == (sock = connect(host, CHAT_PORT))) {
    printf("ERROR: Unable to connect(%d,%d)\n", host, CHAT_PORT);
    return EXIT_FAILURE;
  }
  /* for now, just be obnoxious */
  /* FIXME: too bad there are no syscalls to get MY link address */
  strcpy(buffer, "hello!");
  { /*lexical scoping*/
    int len = strlen(buffer);
  if (len != (bytesWritten = write(sock, buffer, len))) {
    printf("ERROR:short write 1, wanted %d but got %d\n", len, bytesWritten);
    return EXIT_FAILURE;
  }
  }
  strcpy(buffer, "bye, now!");
  { /*lexical scoping*/
    int len = strlen(buffer);
  if (len != (bytesWritten = write(sock, buffer, len))) {
    printf("ERROR:short write 2, wanted %d but got %d\n", len, bytesWritten);
    return EXIT_FAILURE;
  }
  }
  if (-1 == close(sock)) {
    printf("Unable to close socket\n");
    return EXIT_FAILURE;
  }
}
