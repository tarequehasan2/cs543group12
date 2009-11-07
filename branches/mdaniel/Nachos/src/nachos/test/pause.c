/* pause.c
 *
 *	This programs "delays" so that the main thread can join while is running.
 *
 */
 
#include "stdlib.h"

int 
main(int argc, char** argv)
{
 
	for (long i = 50000000000; i > 0; --i);	
	return atoi(argv[0]);
	
}

