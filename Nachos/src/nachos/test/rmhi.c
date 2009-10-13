#include "stdio.h"
int main(void) {
  if (0 > unlink("hi.txt")) {
    printf("unable to unlink \"hi.txt\"\n");
    return 1;
  }
  return 0;
}
