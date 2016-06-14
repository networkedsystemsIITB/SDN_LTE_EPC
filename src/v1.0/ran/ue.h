#include "client.h"

class UserEquipment{
	public:
		unsigned long long key;		// UE key
		unsigned long long imsi;	// International Mobile Subscriber Identity
		unsigned long long msisdn;	// Mobile Station International Subscriber Directory Number
		unsigned long long apn;		// Access Point Name
		unsigned long long ue_nw_capability; 	// Varies between 0 and 10; 0-> BAD connectivity, 10-> EXCELLENT connectivity
		vector<string> NAS_KEYS;	// Contains the keys generated at the time of NAS setup

		unsigned long long tai; 	// Tracking area ID
		
		int type;
		
		// Constructor
		UserEquipment(int);

		/* Functions for various LTE procedures */
		bool authenticate(Client&, bool);
		vector<string> setupTunnel(Client&, bool);
		int sendUserData(Client&, int, string, int, int, int, vector<string>&);
		void initiate_detach(Client&, int, string, string, string);
		void initiate_ue_context_release(Client&, int, string, string, string, int, bool);
		string send_ue_service_request(Client&, int, string);
		string receive_paging_request(Client&, int, string);
		
		/* High-level functions for encryption/decryption */
		void sendEncryptedData(Client&, string, string, string);
		void receiveEncryptedData(Client&, string, string);

		/* Functions for NAS security setup */
		unsigned long long key_generation(int);
		vector<unsigned long long> EPS_AKA_algorithm(unsigned long long, unsigned long long, int, int);
		unsigned long long KDF(int, int, unsigned long long, unsigned long long);
		vector<string> KDF_NAS(int, int, int);

		// Function to retrieve starting UE IP address from the controller
		string getStartingIPAddress(Client&);

		// Destructor
		~UserEquipment();		
};
