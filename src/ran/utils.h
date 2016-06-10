//(C++) Operations: Input/Output
#include <iostream>
#include <math.h>

//(C++) STL Operations: String, Vector, String stream
#include <string>
#include <vector>
#include <sstream>
#include <unordered_map>
#include <queue>

// For integrity protection (NAS Signalling)
#include <openssl/hmac.h>

// For encryption/decryption (AES)
#include <openssl/aes.h>
#include <openssl/conf.h>
#include <openssl/evp.h>
#include <openssl/err.h>

//(C) Operations: Input/Output, String, Standard libraries(like atoi, malloc)
#include <stdio.h>
#include <random>
#include <string.h>
#include <stdlib.h>
#include <thread> 

//(C) Operations: Socket programming
#include <sys/timeb.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <signal.h>
#include <netdb.h> 
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/types.h>

//(C) Operations: Multithreading
#include <pthread.h>

// Raw socket
#include <linux/if_packet.h>
#include <sys/ioctl.h>
#include <bits/ioctls.h>
#include <net/if.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netinet/ether.h>

//Tun device
#include <linux/if_tun.h>
#include <sys/stat.h>
#include <sys/select.h>
#include <sys/time.h>
#include <errno.h>
#include <stdarg.h>

//Writing file
#include <fstream>

using namespace std;

#define DO_DEBUG 0

/**************************************** Configurable parameters **********************************************/

#define DEFAULT_IF  "eth0"   // Default network interface name                                                 *
#define DGW_IP "10.127.41.3"    // IP address of DGW machine                                                   *
#define SINK_IP "10.127.41.6"   // IP address of Sink machine; TCP server at sink listens on this IP           *
#define SINK_SERVER_NETMASK "/16"   // Sink subnet netmask                                                     *
#define UE_MEAN_DATA_SENDING_TIME 10   // Mean time for which a UE sends data                                  *
#define UE_MEAN_IDLE_TIME 1   // Mean idle for UE before S1 Release                                            *
#define UE_MEAN_SERVICE_REQUEST_TIME 1   // Mean idle time for UE before SERVICE REQUEST                       *

/***************************************************************************************************************/

#define BUFFER_SIZE 300   // Maximum packet size
#define MAX_PACKET_SIZE 2048
#define LINK_MTU 2500   // MTU value for iperf3

#define MIN_TEID 1
#define MAX_TEID 4095

#define RAN_UDP_PORT 5858
#define SINK_UDP_PORT 7891

#define PER_THREAD_PORT_WINDOW 1   // Per thread window of destination port numbers used by iperf3 only if current port is found busy
#define NUM_GLOBAL_PORTS 0    // A global window of destination port numbers used by iperf3 only if current port is found busy
#define MIN_NW_CAPABILITY 0
#define MAX_NW_CAPABILITY 10
#define ENC_KEY_LENGTH 32   // Key length for AES encryption/decryption algorithm

#define COMMA ","
#define STATISTIC_FILE "stats.csv"

struct _EtherHeader {
  uint8_t ether_dhost[6];
  uint8_t ether_shost[6];
  uint32_t VLANTag;
  uint16_t ether_type;
} __attribute__((packed));

struct _EtherReceiveHeader {
  uint8_t ether_dhost[6];
  uint8_t ether_shost[6];
  uint16_t ether_type;
} __attribute__((packed));

struct pseudo_header
{
  u_int32_t source_address;
  u_int32_t dest_address;
  u_int8_t placeholder;
  u_int8_t protocol;
  u_int16_t udp_length;
};

extern string SAMPLE_ENC_KEY;   // Sample encryption/decryption key; Used when length of generated key is less than 32
extern int g_mme_port;
extern const char *g_mme_address;
extern unordered_map<string, int> UE_IP_SGW_TEID_MAP;
extern default_random_engine generator;
extern exponential_distribution<double> distribution;
extern exponential_distribution<double> dist_idle_time;
extern exponential_distribution<double> dist_service_request_time;
extern uniform_int_distribution<int> uniform_distribution;
extern uniform_int_distribution<int> random_ue_nw_capability;
extern volatile bool execution_done;
extern vector<int> global_ports;
extern int globalPortsIndex;
extern pthread_mutex_t request_mutex;
extern int SINK_SERVER_STARTING_PORT;

/* Utility functions */
void report_error(int);
void print_message(string);
void print_message(string,int);
void print_message(string,unsigned long long);
const char* to_char_array(unsigned long long);
string longToString(unsigned long long);
void trim(string& );
vector<string> split(char *, const char *);
string hmacDigest(string, string);
string hmacDigest2(char [], string);
string aesEncrypt(string, string);
string aesDecrypt(string, string);
void handleErrors(void);
int encrypt(unsigned char *, int, unsigned char *, unsigned char *);
int decrypt(unsigned char *, int, unsigned char *, unsigned char *);