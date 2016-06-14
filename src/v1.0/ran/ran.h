#include "ue.h"

// Thread function for each UE
void* multithreading_func(void*);

/* High-level functions for various LTE procedures */
bool attach_with_mme(UserEquipment&, Client&, bool);
int send_ue_data(UserEquipment&, int, string, int, int, int, Client&, vector<string>&);
vector<string> setup_tunnel(UserEquipment&, Client&, bool);
void detach_ue(UserEquipment&, Client&, int, string, string, string);
void ue_context_release(UserEquipment&, Client&, int, string, string, string, int, bool);
string ue_service_request(UserEquipment&, Client&, int, string);
string network_service_request(UserEquipment&, Client&, int, string);

// Retrieves the starting UE IP address from the controller
string get_starting_IP_Address(UserEquipment&, Client&);

// Generates UE IP addresses and assigns to the network interface
void* multithreading_add_ip(void *);

// Utility function to check if a file already exists
inline bool fileExists (const std::string& );