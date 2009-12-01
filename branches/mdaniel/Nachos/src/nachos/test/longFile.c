#include "stdio.h"

int main(int argc, char* argv[])
{
  int fd;
  const char file[] = "sjfkljflaskjdflkajdfkljaslfkjasdlkjfsdlkajfldksjflkadsjflkdajsfkjsaflkjaldfkjdlaksjfldksajflksajflkajsdflkajsdflkjdsafkljasldkfjsaklfjslkvnjndsvjkndskjfnvdskjnjksdahfkjsdncmsdlkcnjsdalkjnckjdnvlkadsnvkldsjvkjdslksdnvlknsdvjkdsncjkndsvkjdnvkjdsnvjkladhvlksdjlksjdalkfmmsadljnvjunjwnodjnvnvnkjadnvjkdnsvkjnsdjkvnajknvdnvaldknvdjvnkjanvkjsdan";
    if (0 >= (fd = creat(file))) {
      printf("error: unable to creat\n");
      return 1;
    }
    printf("Created file %s\n",file);
    if (0 > close(fd)) {
      printf("error: unable to close\n");
      return 1;
    }
  
    fd = open(file);
    if (fd==-1) {
      printf("Unable to open %s\n", file);
      return 1;
    }
    printf("Opened file %s\n",file);
  
    if (0 > close(fd)) {
      printf("error: unable to close\n");
      return 1;
    }
    printf("Closed file %s\n",file);

  
    if (unlink(file) != 0) {
      printf("Unable to remove %s\n", file);
      return 1;
    }
    printf("Unlinked file %s\n",file);	
  
  return 0;
}
