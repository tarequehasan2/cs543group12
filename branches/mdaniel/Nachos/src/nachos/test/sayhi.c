#include "stdio.h"

int main(int argc, char* argv[])
{
  int fd;
  int i;
  char buf[] = {'h','i','!','\0'};
  char buf2[4];

  if (0 >= (fd = creat("hi.txt"))) {
    printf("error: unable to creat\n");
    return 1;
  }
  if (sizeof(buf) != write(fd, buf, sizeof(buf))) {
    printf("error: unable to write\n");
    return 1;
  }
  if (0 > close(fd)) {
    printf("error: unable to close\n");
    return 1;
  }
  if (0 > (fd = open("hi.txt"))) {
    printf("error: unable to open: %d\n", fd);
    return 1;
  }
  if (sizeof(buf2) != read(fd, buf2, sizeof(buf2))) {
    printf("error: unable to read\n");
    return 1;
  }
  if (0 > close(fd)) {
    printf("error: unable to close\n");
    return 1;
  }
  for (i=0;i<sizeof(buf2);i++) {
    if (buf[i] != buf2[i]) {
      printf("mismatch at %d: %d <> %d\n", i, buf[i], buf2[i]);
      return 1;
    }
  }
  return 0;
}
