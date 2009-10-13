#include "stdio.h"

int main(int argc, char* argv[])
{
  int fd;
  char buf[] = {'h','i','!','\0'};

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
  return 0;
}
