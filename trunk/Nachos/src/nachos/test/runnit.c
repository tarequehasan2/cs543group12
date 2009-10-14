#include "stdio.h"
int main(int argc, char* argv[])
{
  char *prog = "echo.coff";
  char *prog_argv[] = { prog };
  int child_pid;
  int child_ok;
  int child_rc;

  if (0 >= (child_pid = exec(prog, 1, prog_argv))) {
    printf("unable to exec\n");
    return 1;
  }
  printf("fork away..pid=%d.\n", child_pid);

  printf("joining on %d\n", child_pid);
  child_ok = join(child_pid, &child_rc);
  if (1 == child_ok) {
    printf("child exited normally: rc=%d\n", child_rc);
  } else if (0 == child_ok) {
    printf("egad, child encountered errors: rc=%d\n", child_rc);
  } else {
    printf("egad, join : %d\n", child_ok);
  }

  printf("testing bogus join() call, hang tight ...");
  child_ok = join(0xCAFEBABE, &child_rc);
  if (-1 == child_ok) {
    printf("ok");
  } else {
    printf("FAIL: %d", child_ok);
  }
  printf("\n");

  printf("testing bogus memory to join() call, hang tight ...");
  child_ok = join(0xCAFEBABE, (int*)0xDEADBEEF);
  if (-1 == child_ok) {
    printf("ok");
  } else {
    printf("FAIL: %d", child_ok);
  }
  printf("\n");

  return 0;
}
