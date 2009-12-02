#include "stdio.h"

#define CHAT_PORT 15

#ifndef EXIT_FAILURE
#define EXIT_FAILURE 1
#endif/*EXIT_FAILURE*/

#define WRITE_FULLY_AND_RESET(fd,buf,len) \
 do { \
   int bytesWritten; \
   bytesWritten = write(fd, buf, len); \
   if (bytesWritten != len) { \
     printf("ERROR: short write: wanted %d, got %d\n", \
       len, bytesWritten); \
   } else { \
     in_pos = 0; \
   } \
 } while(0 == 1);

static char in_buffer[1024];
static int  in_pos;
static char out_buffer[1024];
static int  out_pos;

int main(int argc, char* argv[])
{
  int host;
  int sock;
  int bytesRead;

  if (1 == argc) {
    const char *cp;
    cp = argv[0];
    cp += 5; /* c h a t - */
   host = atoi(cp);
/*
    printf("Usage: %s server-link-address\n", argv[0]);
    return EXIT_FAILURE;
*/
  } else {
    host = atoi(argv[1]);
  }
  printf("connecting to (%d,%d)...\n", host, CHAT_PORT);

  if (-1 == (sock = connect(host, CHAT_PORT))) {
    printf("ERROR: Unable to connect(%d,%d)\n", host, CHAT_PORT);
    return EXIT_FAILURE;
  }
  printf("connected OK\n");

  memset(in_buffer, 0, sizeof(in_buffer));
  in_pos = 0;
  memset(out_buffer, 0, sizeof(out_buffer));
  out_pos = 0;

  do {
    bytesRead = read(sock, &in_buffer, 1);
    if (-1 != bytesRead) {
      /* process chatserver input */
      in_pos++;
      if ('\n' == in_buffer[in_pos - 1]) {
        printf("server>");
        WRITE_FULLY_AND_RESET(stdout, in_buffer, in_pos);
        if (0 != in_pos) {
          /* it didn't write the whole buffer */
          break;
        }
      }
    }

    bytesRead = read(stdin, out_buffer + out_pos, 1);
    if (-1 != bytesRead) {
      /* user said something */
      out_pos++;
      /* check for user exit */
      if (2 == out_pos &&
          '.' == out_buffer[0] &&
          '\n' == out_buffer[1]) {
        printf("EXIT CODE RECEIVED\n");
        break;
      }
      if ('\n' == out_buffer[out_pos - 1]) {
        printf("\nTX\n");
        WRITE_FULLY_AND_RESET(sock, out_buffer, out_pos);
        if (0 != in_pos) {
          /* it didn't write the whole buffer */
          break;
        }
      }
    }
  } while (1 == 1);

  printf("closing socket...\n");
  if (-1 == close(sock)) {
    printf("Unable to close socket\n");
    return EXIT_FAILURE;
  }
}
