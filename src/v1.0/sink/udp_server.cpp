/******************************************************************************************************************
* This is a multi-threaded UDP server which generates NETWORK INITIATED SERVICE REQUEST.                          *
* Each server thread generates a request for a corresponding UE thread at the RAN.                                *
* First, it receives a request (UDP packet) from controller to initiate a service request.          			  *
* It then sleeps for a random idle time and then sends a UDP downlink data packet to trigger service request. 	  *
*******************************************************************************************************************/


#include <stdio.h>
#include <stdlib.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <pthread.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <arpa/inet.h>
#include <iostream>
#include <random>
#include <vector>

using namespace std;

#define BUFFER_SIZE 1024
#define DEBUG 0

#define UE_MEAN_SLEEP_TIME 1

struct _threadArgs {
   int threadId;
   char* serverIp;
   int serverPort;
};

int numServersReady = 0;

string INITIATE_NETWORK_SERVICE_REQUEST = "22";
string SINK_SERVICE_REQUEST = "23";
char * SEPARATOR = "@:##:@";

std::default_random_engine generator;
std::exponential_distribution<double> distribution(1.0/UE_MEAN_SLEEP_TIME);

vector<string> split(char *str, const char *delim){
   vector<string> ans;
   string s(str);
   string delimiter(delim);
   size_t pos = 0;
   std::string token;
   while ((pos = s.find(delimiter)) != std::string::npos) {
      token = s.substr(0, pos);
      ans.push_back(token);
      s.erase(0, pos + delimiter.length());
   }
   ans.push_back(s);
   return ans;
}

void* threadFunc(void *arg){
   struct _threadArgs *args = (struct _threadArgs *)arg;

   int sockfd, nBytes, sockopt;
   char buffer[BUFFER_SIZE];
   struct sockaddr_in serv_addr;
   struct sockaddr_storage serverStorage;
   socklen_t addr_size;
   
   /* First call to socket() function */
   sockfd = socket(PF_INET, SOCK_DGRAM, 0);
   
   if (sockfd < 0) {
      perror("ERROR opening socket");
      exit(1);
   }

   /* Allow the socket to be reused - In case, connection is closed prematurely */
   if(setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &sockopt, sizeof(sockopt)) == -1){
      perror("setsockopt");
      close(sockfd);
      exit(EXIT_FAILURE);
   }
   
   /* Initialize socket structure */
   bzero((char *) &serv_addr, sizeof(serv_addr));
   
   serv_addr.sin_family = AF_INET;
   serv_addr.sin_addr.s_addr = inet_addr(args->serverIp);
   serv_addr.sin_port = htons(args->serverPort);
   memset(serv_addr.sin_zero, '\0', sizeof serv_addr.sin_zero);     

   /* Bind socket with address struct. */
   if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
      perror("**ERROR on binding");
      exit(1);
   }

   numServersReady++;

   /* Initialize size variable to be used later on*/
   addr_size = sizeof serverStorage;

   if(DEBUG){
      printf("Server %d ready...\n", args->threadId);
   }

   while(1){
      /* Try to receive any incoming UDP datagram. Address and port of 
       * requesting client will be stored on serverStorage variable.
       */

      // Clear the buffer
      memset(buffer, '\0', sizeof buffer);

      nBytes = recvfrom(sockfd, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&serverStorage, &addr_size);

      if (nBytes < 0) {
         perror("ERROR on recv");
         exit(1);
      }
      if(DEBUG){
         struct sockaddr_in *sin = (struct sockaddr_in *)&serverStorage;
         unsigned char *ip = (unsigned char *)&sin->sin_addr;
         unsigned char *client_port = (unsigned char *)&sin->sin_port;
         printf("Received from Client IP: %d.%d.%d.%d\n", ip[0], ip[1], ip[2], ip[3]);
      }

       vector<string> tmpArray = split(buffer, SEPARATOR);
       // tmpArray[0]: INITIATE_NETWORK_SERVICE_REQUEST Code, tmpArray[1]: UE Key

      if (tmpArray[0] != INITIATE_NETWORK_SERVICE_REQUEST){
         cout<<"ERROR: non authentic code received, got = "<<tmpArray[0]<<" required = "<<INITIATE_NETWORK_SERVICE_REQUEST<<endl;
         exit(1);
      }

      size_t sleepTime = (size_t) distribution(generator);
      if(DEBUG){
         printf("\nServer %d sleeping for %zu secs\n", args->threadId, sleepTime);
      }

      // Sleep for random time
      usleep(sleepTime*1000000);

      /* Send downlink data packet to initiate service request */
      string send = SINK_SERVICE_REQUEST + SEPARATOR + tmpArray[1];
      bzero(buffer, BUFFER_SIZE);
      sprintf(buffer, "%s", send.c_str());
      sendto(sockfd, buffer, strlen(buffer), 0, (struct sockaddr *)&serverStorage, addr_size);
   }

   close(sockfd);
}

int main( int argc, char *argv[] ) {
   if(argc != 4){
      printf("usage <server-ip> <starting-port> <num-servers>\n");
      exit(1);
   }
   int startPort, numServers, portNum, i;
   sscanf(argv[2], "%d", &startPort);
   sscanf(argv[3], "%d", &numServers);
   portNum = startPort;
   pthread_t tid[numServers];
   struct _threadArgs* args[numServers];
   for (i = 0; i < numServers; ++i){
      args[i] = (struct _threadArgs *)malloc(sizeof(struct _threadArgs));
      args[i]->threadId = i;
      args[i]->serverIp = (char *)malloc(strlen(argv[1])+1);
      strcpy(args[i]->serverIp, argv[1]);
      args[i]->serverPort = portNum;
      pthread_create(&tid[i], NULL, threadFunc, args[i]);
      portNum++;
   }

   sleep(1);
   printf("No. of UDP servers ready: %d\n", numServersReady);

   for (i = 0; i < numServers; ++i){
      pthread_join(tid[i], NULL);
      free(args[i]);
   }     
   return 0;
}