/*************************************************************************
 * This file contains all the socket level functions that is used by UE. *
 *************************************************************************/

#include "client.h"

/*
 *Constructor: Create a UDP socket.
 */
 Client::Client(){
 	client_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(client_socket < 0){
    cout << "ERROR opening UDP socket" << endl;
    exit(1);
  }
 }

/*
 * This function configures the port number and IP address of the created socket.
 */
 void Client::input_server_details(int server_port, const char *server_address){
 	int status;
 	this->server_port = server_port;
 	this->server_address = server_address;
 	bzero((char*)&server_sock_addr, sizeof(server_sock_addr));
 	server_sock_addr.sin_family = AF_INET;
 	server_sock_addr.sin_port = htons(server_port);
	// Store this IP address in server_sock_addr; pton supports IPv6 while aton does not
 	status = inet_pton(AF_INET, server_address, &(server_sock_addr.sin_addr));
 	if(status == 0){
 		cout<<"ERROR: Invalid IP address"<<endl;
 		exit(EXIT_FAILURE);
 	}
 }

/*
 * This function reads from the UDP socket.
 */
 void Client::read_data(){
 	int status;
 	bzero(client_buffer, BUFFER_SIZE);
 	status = recvfrom(client_socket, client_buffer, BUFFER_SIZE-1, 0, NULL, NULL);
 	report_error(status);
 }

/*
 * This function writes to the UDP socket.
 */
 void Client::write_data(){
 	int status;
 	status = sendto(client_socket, client_buffer, strlen(client_buffer), 0,(struct sockaddr*)&server_sock_addr, sizeof(server_sock_addr));
 	report_error(status);
 }

/*
 * This function reads from the UDP socket in the form of unsigned char.
 */
 void Client::read_byte(){
 	int status;
 	bzero(client_byte_buffer, BUFFER_SIZE);
 	status = recvfrom(client_socket, client_byte_buffer, BUFFER_SIZE-1, 0, NULL, NULL);
 	report_error(status);
 }

/*
 * This function writes to the UDP socket in the form of unsigned char.
 */
 void Client::write_byte(){
 	int status;
 	status = sendto(client_socket, client_byte_buffer, strlen((char*)client_byte_buffer), 0,(struct sockaddr*)&server_sock_addr, sizeof(server_sock_addr));
 	report_error(status);
 }

/*
 * This function generates TCP traffic at given rate using iperf3 for the specified duration of time.
 */
 int Client::sendUEData(int ue_num, string srcIp, string dstIp, int portnum, int startingPort, int endPort, string rate, size_t meanTime){
 	const char *srcIpptr = srcIp.c_str();
 	char c = tolower(rate[rate.size()-1]);
 	string dstNetwork(SINK_SERVER_NETMASK);
 	string format(1,c);

 	string f = "iperf3 -c "+dstIp+" -p "+to_string(portnum)+" -b "+rate+" -M "+to_string(LINK_MTU)+" -f "+format+" -t "+to_string(meanTime)+" -B "+srcIp;
 	if(DO_DEBUG){
 		cout<<"SOURCE IP="<<srcIp<<endl;
 		cout<<"DESTINATION IP="<<dstIp<<endl;
 		cout<<f<<endl;
 	}

 	bool done = false, loopedOnce = false;
 	int count = 0, tmp_port, ret, realCounter = 0;
 	int port_gap = endPort - startingPort;
 	int numGlobaltriedPorts = 0;

 	do{
 		string g = GetStdoutFromCommand("iperf3 -c "+dstIp+" -p "+to_string(portnum)+" -b "+rate+" -M "+to_string(LINK_MTU)+" -f "+format+" -t "+to_string(meanTime)+" -B "+srcIp);

 		size_t f = g.find("Connecting to host");
 		size_t found = g.find("iperf3: error - the server is busy running a test");
 		size_t timeout = g.find("iperf3: error - unable to connect to server:");

 		if(f == std::string::npos && found == std::string::npos && timeout != std::string::npos){
 			cout<<"iperf3 output: "<<g<<endl;
 			exit(1);
 		}
 		if(found != std::string::npos){
 			cout<<"iperf3 output: "<<g<<endl;
 			cout<< "Using local port " << portnum<<" "<<" count="<<count<<" real counter="<<realCounter<<endl;
 			portnum++;
 			if(portnum >= endPort){
 				if(count < port_gap){
 					portnum = startingPort;
 				}else{
 					loopedOnce = true;
 					tmp_port = startingPort;
 				}
 			}
 			if(count >= port_gap && numGlobaltriedPorts <= NUM_GLOBAL_PORTS){
 				numGlobaltriedPorts++;
				// Use global ports
 				ret = pthread_mutex_lock(&request_mutex);
 				if(ret < 0)
 				{
 					perror("ERROR: mutex lock failed");
 					exit(1);
 				}
 				portnum = global_ports[globalPortsIndex];
 				if(globalPortsIndex < NUM_GLOBAL_PORTS-1){
 					globalPortsIndex++;
 				}else{
 					globalPortsIndex = 0;
 				}

				// Releasing lock
 				ret = pthread_mutex_unlock(&request_mutex);
 				if(ret < 0)
 				{
 					perror("ERROR: mutex unlock failed");
 					exit(1);
 				}
 				cout<<"Global port ="<<portnum<<" used approx index="<<(globalPortsIndex-1)<<endl;
 			}else if(count >= port_gap && numGlobaltriedPorts > NUM_GLOBAL_PORTS){
 				numGlobaltriedPorts = 0;
 				count = 0;
 				portnum = tmp_port;
 			}
 			count++;
 			realCounter++;
 		}else{
 			if(realCounter != 0){
 				cout<< "Using global port " << portnum<<" "<<realCounter<<" DONE"<<endl;
 			}
 			done = true;
 		}
 	}while(!done);

 	if(loopedOnce){
 		portnum = tmp_port;
 	}

 	return portnum;
 }

/*
 * This function executes the specified command and returns its output.
 */
 string Client::GetStdoutFromCommand(string cmd) {
 	string data;
 	FILE * stream;
 	const int max_buffer = 256;
 	char buffer[max_buffer];
 	cmd.append(" 2>&1");

 	stream = popen(cmd.c_str(), "r");
 	if (stream) {
 		while (!feof(stream))
 			if (fgets(buffer, max_buffer, stream) != NULL) data.append(buffer);
 		pclose(stream);
 	}
 	return data;
 }

// Destructor: Close the UDP client socket
Client::~Client(){
	close(client_socket);
}