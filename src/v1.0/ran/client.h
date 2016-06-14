#ifndef UTILS_H
#define UTILS_H
#include "utils.h"
#endif

class Client{
public:
	int client_socket;
	char client_buffer[BUFFER_SIZE];
	// Byte array in C++
	unsigned char client_byte_buffer[BUFFER_SIZE];	

	int server_port;
	const char *server_address;
	struct sockaddr_in server_sock_addr;

	// Constructor
	Client();

	// Socket methods
	void input_server_details(int,const char*);
	void read_data();
	void write_data();
	void read_byte();
	void write_byte();

	int sendUEData(int, string, string, int, int, int, string, size_t);

	// Utility functions
	string GetStdoutFromCommand(string cmd);	

	// Destructor
	~Client();		
};
