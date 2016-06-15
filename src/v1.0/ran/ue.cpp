/********************************************************************
 * This file contains all the functionalities associated with a UE. *
 ********************************************************************/

#include "ue.h"
#include <time.h>

/* UE Authentication and tunnel setup codes */
string AUTHENTICATION_FAILURE = "-1";
string AUTHENTICATION_STEP_ONE = "1"; 	// Attach request
string AUTHENTICATION_STEP_TWO = "2";
string AUTHENTICATION_STEP_THREE = "3";
string NAS_STEP_ONE = "4";
string SEND_APN = "5";
string SEND_IP_SGW_TEID = "6";
string SEND_UE_TEID = "7";
string ATTACH_ACCEPT = "8";
string DETACH_REQUEST = "9";
string DETACH_ACCEPT = "10";
string REQUEST_STARTING_IP = "12";
string SEND_STARTING_IP = "13";
string UE_CONTEXT_RELEASE_REQUEST = "14";
string UE_CONTEXT_RELEASE_COMMAND = "15";
string UE_CONTEXT_RELEASE_COMPLETE = "16";
string UE_SERVICE_REQUEST = "17";
string INITIAL_CONTEXT_SETUP_REQUEST = "18";
string INITIAL_CONTEXT_SETUP_RESPONSE = "19";
string NAS_STEP_TWO = "20";
string PAGING_REQUEST = "21";
string INITIATE_NETWORK_SERVICE_REQUEST = "22";
string SINK_SERVICE_REQUEST = "23";


char * SEPARATOR = "@:##:@";

int SN_ID = 1; // Serving Network Id of MME
int teid = 1;

/*
 * Constructor: Create a UE object.
 */
UserEquipment::UserEquipment(int ue_num){
	key = key_generation(ue_num);
	imsi = key*1000;
	msisdn = 9000000000 + key;
	apn = key;
	tai = key + 10; 	// Tracking area ID
	ue_nw_capability = random_ue_nw_capability(generator); 	// Random
}

/*
 * This function generates UE key (same as UE ID in this case).
 */
unsigned long long UserEquipment::key_generation(int ue_num){
	return ue_num;
}

/*
 * This function contains all the steps that take place during the authentication procedure.
 * A mutual authentication takes place between the UE and the MME (i.e. the network).
 */
bool UserEquipment::authenticate(Client &user, bool checkIntegrity){
	string send, receive;
	unsigned long long autn;
	unsigned long long rand;
	unsigned long long res;
	vector<string> tmpArray;
	vector<unsigned long long> authenticationVector;
	time_t curTime;
	time(&curTime);
	string KSI_ASME = "7";
	int SQN = 1;

	// KSI: Key Selection Identifier, ASME: Access Security Management Entity (MME in this case), SQN: Sequence number

	// Authentication Step 1
	send = AUTHENTICATION_STEP_ONE + SEPARATOR + longToString(imsi) + SEPARATOR + longToString(ue_nw_capability) + SEPARATOR + KSI_ASME + SEPARATOR + to_string(SQN) + SEPARATOR + longToString(tai);
	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	user.write_data();
	time(&curTime);

	// Receive reply from MME: Authentication Step 2
	user.read_data();
	time(&curTime);
	receive = (string) (user.client_buffer);
	if (receive.find(SEPARATOR) != std::string::npos) {
		tmpArray = split(user.client_buffer, SEPARATOR);
		if(tmpArray[0] == AUTHENTICATION_STEP_TWO){
			if(DO_DEBUG){
				cout <<"AUTHENTICATION_STEP_TWO: RAND = "<<tmpArray[1]<<" autn = "<<tmpArray[2]<<" KSI_ASME = "<<tmpArray[3]<< " for UE with key = "<<key<<endl;
			}
			rand = stoull(tmpArray[1]);
			autn = stoull(tmpArray[2]);
			KSI_ASME = tmpArray[3];
			authenticationVector = EPS_AKA_algorithm(key, rand, SQN, SN_ID);
			// authenticationVector: [0] = autn, [1] = res, [2] = K_ASME

			if(DO_DEBUG){
				cout<<"EPS_AKA_algorithm autn = "<<authenticationVector[0]<<" res ="<<authenticationVector[1]<<" K_ASME = "<<authenticationVector[2]<<endl;
			}
			tmpArray.clear();
			time(&curTime);

			// Authentication Step 3
			send = AUTHENTICATION_STEP_THREE + SEPARATOR + longToString(imsi) + SEPARATOR + longToString(authenticationVector[1]);
			if(DO_DEBUG){
				cout<<"NOTICE: autn= "<<autn<<" rand= "<<rand<<endl;
			}
			bzero(user.client_buffer, BUFFER_SIZE);
			sprintf(user.client_buffer, send.c_str());
			user.write_data();
			time(&curTime);
			// Receive reply from MME: Authentication Step 4
			user.read_data();
			time(&curTime);
			receive = (string) (user.client_buffer);
			
			if (receive.find(SEPARATOR) != std::string::npos) {
				tmpArray = split(user.client_buffer, SEPARATOR);
				if(tmpArray[0] == NAS_STEP_ONE){
					if(DO_DEBUG){
						cout<<"SUCCESS: UE with key = "<<key<<" imsi= "<<imsi<<" AUTHENTICATED"<<endl;
						cout<<"NAS Parameters: KSI_ASME = "<<tmpArray[1]<<" UE Network Capability "<<tmpArray[2]<<
						" Cipher algorithm ID = "<<tmpArray[3]<<" Integrity Algorithm ID = "<<tmpArray[4]<<" NAS MAC = "<<tmpArray[5]<<
						" UE with key = "<<key<<endl;
					}
					NAS_KEYS = KDF_NAS(authenticationVector[2], stoi(tmpArray[4]), stoi(tmpArray[3]));
					// NAS_KEYS[0] = Integrity key and NAS_KEYS[1] = Cipher Key
					if(DO_DEBUG){
						cout<<" Integrity Key = "<<NAS_KEYS[0]<<" Cipher key ="<<NAS_KEYS[1]<<endl;
					}
					string integrity_text = tmpArray[0] + SEPARATOR + tmpArray[1] + SEPARATOR + tmpArray[2] + SEPARATOR + tmpArray[3] + SEPARATOR + tmpArray[4]; 
					if(checkIntegrity){
						string NAS_MAC_UE = hmacDigest(integrity_text, NAS_KEYS[0]);
						if(DO_DEBUG){
							cout<<"NAS_MAC_UE ="<<NAS_MAC_UE<<" Controller NAS MAC ="<<tmpArray[5]<<endl;
						}
						if (NAS_MAC_UE == tmpArray[5]){ // Check if generated NAS MAC = received NAS MAC 
							string plain_text = NAS_STEP_TWO;
							unsigned char *key = (unsigned char *)NAS_KEYS[1].c_str();//"01234567890123456789012345678901";
							unsigned char *plaintext = (unsigned char *)plain_text.c_str();
							unsigned char ciphertext[BUFFER_SIZE];
							
							/* Buffer for the decrypted text */
							unsigned char decryptedtext[128];
							int decryptedtext_len, ciphertext_len;

	 						// Encrypt the plaintext
							ciphertext_len = encrypt (plaintext, strlen ((char *)plaintext), key, ciphertext);

							char tmpCiphertext[ciphertext_len];
							for (int i = 0; i < ciphertext_len; i++){
								tmpCiphertext[i] = (char) ciphertext[i];
							}
							string encrypted_hash = hmacDigest2(tmpCiphertext, NAS_KEYS[0]);
							if(DO_DEBUG){
								cout<<"NAS STEP ONE: Generated NAS MAC= " << encrypted_hash <<endl;
							}
							send = SEPARATOR + encrypted_hash + SEPARATOR + longToString(imsi);

							for (int i = ciphertext_len; i < ciphertext_len+send.size(); i++){
								ciphertext[i] = (unsigned char)send[i-ciphertext_len];
							}
							ciphertext[ciphertext_len+send.size()] = '\0';

							bzero(user.client_byte_buffer, BUFFER_SIZE);
							memcpy (user.client_byte_buffer, ciphertext , sizeof(ciphertext));
							user.write_byte();
						} else{
							cout<<"ERROR:: Sending NAS Security Mode NOT Complete for UE with imsi = "<<longToString(imsi)<<endl;
							exit(1);				
							return false;
						}
					} else{
						send = NAS_STEP_TWO + SEPARATOR + "encrypted_hash" + SEPARATOR + longToString(imsi);
						bzero(user.client_buffer, BUFFER_SIZE);
						sprintf(user.client_buffer, send.c_str());
						user.write_data();
					}
					return true;
				}else{
					cout<<"**ERROR: Step FOUR authentication failure for UE with key = " <<key<<endl;
					exit(1);				
					return false;
				}
			}else{
				cout<<"ERROR: Step FOUR authentication failure for UE with key = " <<key<<endl;
				exit(1);				
				return false;
			}
		}else{
			cout<<"ERROR: Step ONE authentication failure for UE with key = "<<key<<endl;
			exit(1);
			return false;
		}
	}else{
		cout<<"*ERROR: Step ONE authentication failure for UE with key = "<<key<<endl;
		exit(1);
		return false;
	}
}

/*
 * This function initiates the tunnel setup procedure.
 */
vector<string> UserEquipment::setupTunnel(Client &user, bool doEncryption){
	string send, receive, initial;
	vector<string> tmpArray;
	vector<string> clearToSend;
	int ue_teid;

	ue_teid = uniform_distribution(generator);
	if(DO_DEBUG){
		cout<<"UE with key="<<key<<" GENERATED teid="<<ue_teid<<endl;
	}

	// Send APN to the MME	
	send = SEND_APN + SEPARATOR + longToString(apn) + SEPARATOR + longToString(key);
	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	user.write_data();
	if(doEncryption) {
		sendEncryptedData(user, SEND_APN + SEPARATOR,  longToString(apn) + SEPARATOR + longToString(key),"SEND_APN");
	}

	// Receive UE IP address and SGW TEID from MME
	user.read_data();
	receive = (string) (user.client_buffer);
	
	tmpArray = split(user.client_buffer, SEPARATOR);
	if(doEncryption) {
		receiveEncryptedData(user, tmpArray[0], "string method");
	}
	if(tmpArray[0] == SEND_IP_SGW_TEID){
		if(DO_DEBUG){
			cout<<"IP Address of UE="<<tmpArray[1]<<" and SGW TEID="<<tmpArray[2]<<" ue TEID="<<ue_teid<<endl;
		}
		tmpArray.push_back(to_string(ue_teid));

		// Send UE TEID and key for identifying UE at MME (for SGW)
		send = SEND_UE_TEID + SEPARATOR + longToString(ue_teid) + SEPARATOR + longToString(key);
		bzero(user.client_buffer, BUFFER_SIZE);
		sprintf(user.client_buffer, send.c_str());
		user.write_data();
		if(doEncryption) {
			sendEncryptedData(user, SEND_UE_TEID + SEPARATOR, longToString(ue_teid) + SEPARATOR + longToString(key), "SEND_UE_TEID");
		}

		user.read_data();
		receive = (string) (user.client_buffer);
		clearToSend = split(user.client_buffer, SEPARATOR);
		if(doEncryption) {
			receiveEncryptedData(user, clearToSend[0], "string method");
		}
		if(clearToSend[0] == ATTACH_ACCEPT){
			tmpArray.push_back(clearToSend[1]); // clearToSend[1] => GUTI
			if(DO_DEBUG){
				cout<<"CLEAR TO SEND DATA"<<endl;
			}
		}else{
			cout<<"ERROR: NOT CLEAR TO SEND DATA"<<endl;
			exit(1);
		}

		time_t seconds;
		seconds = time (NULL);
		if(DO_DEBUG){
			printf ("%ld seconds since January 1, 1970 kk=%d\n", seconds, stoi(tmpArray[2]));
		}
	}
	return tmpArray; 
}

/*
 * This function is used to send data from a UE in the form of TCP traffic generated using iperf3.
 */
int UserEquipment::sendUserData(Client &user, int ue_num, string rate, int serverPort, int startingPort, int endPort, vector<string>& tmpArray){
	size_t meanTime;
	// Generate a random non-zero time duration with the specified mean
	do{
		meanTime = (size_t) distribution(generator);
	}while(meanTime == 0);

	struct timeb start;
	ftime(&start);
	string dstIp(SINK_IP);
	int returnedPort = 0;

	returnedPort = user.sendUEData(ue_num, tmpArray[1], dstIp, serverPort, startingPort, endPort, rate, meanTime);

	struct timeb end;
	ftime(&end);
	double diff= (end.time - start.time) + (end.millitm - start.millitm)/1000.0;

	if(DO_DEBUG){
		cout<<"UE ID="<<key<<" meanTime= "<<meanTime<<" Time for Data Transfer = "<<diff<<"sec"<<endl;
	}
	return returnedPort;
}

/*
 * This function initiates the detach procedure which is invoked by UE.
 */
void UserEquipment::initiate_detach(Client &user, int ue_num, string ue_ip, string sgw_teid, string ue_teid){
	string send, receive;
	vector<string> tmpArray;

	// Initiate detach procedure
	send = DETACH_REQUEST + SEPARATOR + ue_ip + SEPARATOR + ue_teid + SEPARATOR + sgw_teid + SEPARATOR + to_string(ue_num);
	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	user.write_data();
	sendEncryptedData(user, DETACH_REQUEST + SEPARATOR,  ue_ip + SEPARATOR + ue_teid + SEPARATOR + sgw_teid + SEPARATOR + to_string(ue_num),"DETACH_REQUEST");
	if(DO_DEBUG){
		cout<<"UE INITIATED DETACH: UE Key="<<key<<" IP Address of UE="<<ue_ip<<" and UE TEID="<<ue_teid<<" SGW TEID"<<sgw_teid<<endl;
	}

	// Receive UE IP address and SGW TEID
	user.read_data();
	receive = (string) (user.client_buffer);
	tmpArray = split(user.client_buffer, SEPARATOR);
	receiveEncryptedData(user, tmpArray[0], "string method");
	if(tmpArray[0] == DETACH_ACCEPT){
		// TODO: delete the ip from the network interface
		string dstNetwork(SINK_SERVER_NETMASK);
		if(DO_DEBUG){
			cout<<"UE DETACH ACCEPTED: UE Key="<<key<<" IP Address of UE="<<ue_ip<<" and UE TEID="<<ue_teid<<" SGW TEID"<<sgw_teid<<endl;
		}
	}else{
		cout<<"ERROR: UE DETACH ACCEPT ERROR: UE Key="<<key<<" IP Address of UE="<<ue_ip<<" and UE TEID="<<ue_teid<<" SGW TEID"<<sgw_teid<<endl;
		exit(1);
	}
}

/*
 * This function initiates the context release procedure. It is invoked by a UE (eNodeB in real life) after it
 * remains idle for a particular duration of time. The tunnel is then ruptured with the help of RAN and controller.
 */
void UserEquipment::initiate_ue_context_release(Client &user, int ue_num, string ue_ip, string sgw_teid, string ue_teid, int sinkUDPServerPort, bool networkServiceRequest){
	string send, receive;
	vector<string> tmpArray;

	// Initiate ue context release procedure
	send = UE_CONTEXT_RELEASE_REQUEST + SEPARATOR + ue_ip + SEPARATOR + ue_teid + SEPARATOR + sgw_teid + SEPARATOR + to_string(ue_num);
	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	user.write_data();
	if(DO_DEBUG){
		cout<<"UE INITIATED CONTEXT RELEASE REQUEST: UE Key="<<key<<" IP Address of UE="<<ue_ip<<" and UE TEID="<<ue_teid<<" SGW TEID"<<sgw_teid<<endl;
	}

	// Receive UE_CONTEXT_RELEASE_COMMAND from MME
	user.read_data();
	receive = (string) (user.client_buffer);
	tmpArray = split(user.client_buffer, SEPARATOR);
	if(tmpArray[0] == UE_CONTEXT_RELEASE_COMMAND){

		if(DO_DEBUG){
			cout<<"UE CONTEXT RELEASE COMMAND: UE Key="<<key<<" IP Address of UE="<<ue_ip<<" and UE TEID="<<ue_teid<<" SGW TEID"<<sgw_teid<<endl;
		}

	}else{
		cout<<"ERROR: UE CONTEXT RELEASE ACCEPT ERROR: UE Key="<<key<<" IP Address of UE="<<ue_ip<<" and UE TEID="<<ue_teid<<" SGW TEID"<<sgw_teid<<endl;
		exit(1);
	}

	// Context release complete
	if(networkServiceRequest) {
		send = UE_CONTEXT_RELEASE_COMPLETE + SEPARATOR + to_string(ue_num) + SEPARATOR + ue_ip + SEPARATOR + "1" + SEPARATOR + to_string(sinkUDPServerPort);
	}else{
		send = UE_CONTEXT_RELEASE_COMPLETE + SEPARATOR + to_string(ue_num) + SEPARATOR + ue_ip + SEPARATOR + "0";
	}

	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	user.write_data();
	if(DO_DEBUG){
		cout<<"UE INITIATED CONTEXT RELEASE COMPLETE: UE Key="<<ue_num<<endl;
	}
}

/*
 * This function initiates for the tunnel resetup when a UE wishes to send data after remaining idle for a long time.
 * Ideally, no re-authentication needs to be done in this case. We just need to re-establish the tunnel for traffic flow.
 */
string UserEquipment::send_ue_service_request(Client& user, int ue_num, string ue_ip){
	vector<string> clearToSend;
	int ue_teid;
	string send, receive, response= "";
	vector<string> tmpArray;
	string KSI_ASME = "7";

	// Send UE Service request
	send = UE_SERVICE_REQUEST + SEPARATOR + longToString(ue_num) + SEPARATOR + KSI_ASME + SEPARATOR + ue_ip;
	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	// sendEncryptedData(user, UE_SERVICE_REQUEST + SEPARATOR, longToString(ue_num) + SEPARATOR + KSI_ASME + SEPARATOR + ue_ip, "UE_SERVICE_REQUEST");
	user.write_data();
		
	// Receive reply from MME
	user.read_data();
	receive = (string) (user.client_buffer);
	receiveEncryptedData(user, receive, "string method");
	if (receive.find(SEPARATOR) != std::string::npos) {
		tmpArray = split(user.client_buffer, SEPARATOR);

		if(tmpArray[0] == INITIAL_CONTEXT_SETUP_REQUEST){
			if(DO_DEBUG){
				cout<<"Received INITIAL_CONTEXT_SETUP_REQUEST for UE with key="<<ue_num<<" SGW TEID="<<tmpArray[1]<<endl;
			}

			ue_teid = uniform_distribution(generator);
			if(DO_DEBUG){
				cout<<"UE with key="<<key<<" GENERATED teid="<<ue_teid<<endl;
			}
			// Send INITIAL_CONTEXT_SETUP_RESPONSE
			send = INITIAL_CONTEXT_SETUP_RESPONSE + SEPARATOR + longToString(ue_teid) + SEPARATOR + longToString(key) + SEPARATOR + ue_ip;
			bzero(user.client_buffer, BUFFER_SIZE);
			sprintf(user.client_buffer, send.c_str());
			sendEncryptedData(user, UE_SERVICE_REQUEST + SEPARATOR, longToString(ue_num), "INITIAL_CONTEXT_SETUP_RESPONSE");
			user.write_data();

			user.read_data();
			receive = (string) (user.client_buffer);
			receiveEncryptedData(user, receive, "string method");
			tmpArray = split(user.client_buffer, SEPARATOR);
			if(tmpArray[0] == ATTACH_ACCEPT){
				// tmpArray[1] => ue key
				if(DO_DEBUG){
					cout<<"CLEAR TO SEND DATA for ue with key ="<<tmpArray[1]<<endl;
				}
			}
		}
	}

	return to_string(ue_teid);
}

/*
 * This function blocks on UDP read socket call and waits for paging request from MME.
 */
string UserEquipment::receive_paging_request(Client& user, int ue_num, string ue_ip){
	string receive, ue_teid= "";
	vector<string> tmpArray;
	// Receive paging request from MME
	user.read_data();
	receive = (string) (user.client_buffer);
	if (receive.find(SEPARATOR) != std::string::npos) {
		tmpArray = split(user.client_buffer, SEPARATOR);
		if(tmpArray[0] == PAGING_REQUEST){
			if(DO_DEBUG){
				cout<<"Received PAGING_REQUEST for UE with key="<<ue_num<<" SGW TEID = "<<tmpArray[1]<<endl;
			}
			ue_teid = send_ue_service_request(user, ue_num, ue_ip);
		}
	}
	return ue_teid;
}

/*
 * This function is used to contact the controller (MME) to fetch the starting UE IP address.
 */
string UserEquipment::getStartingIPAddress(Client& user){
	string send, receive, ip= "";
	vector<string> tmpArray;
	send = REQUEST_STARTING_IP + SEPARATOR + "";
	bzero(user.client_buffer, BUFFER_SIZE);
	sprintf(user.client_buffer, send.c_str());
	user.write_data();
	user.read_data();
	receive = (string) (user.client_buffer);
	if (receive.find(SEPARATOR) != std::string::npos) {
		tmpArray = split(user.client_buffer, SEPARATOR);
		if(tmpArray[0] == SEND_STARTING_IP && tmpArray.size()==2){
			ip = tmpArray[1];
			if(DO_DEBUG){
				cout<<"Received starting ip="<<ip<<endl;
			}
		}else{
			cout<<"ERROR:: error occured in fetching starting ip address";
			exit(1);
		}
	}else{
		cout<<"*ERROR:: error occured in fetching starting ip address";
		exit(1);
	}
	return ip;
}

/*
 * Authentication and Key Agreement Algorithm
 */
vector<unsigned long long> UserEquipment::EPS_AKA_algorithm(unsigned long long key, unsigned long long rand, int SQN, int SN_ID){
	unsigned long long res, autn, CK, IK, K_ASME; // CK: Cipher Key, IK : Integrity Key, ASME: Access Security Management Entity (in this case MME)
	vector<unsigned long long> authenticationVector(3);
	res = rand * key + SQN;
	// cout<<" key="<<key<<" rand="<<rand<<" SQN ="<<SQN<<" SN_ID= "<<SN_ID<<endl;
	autn = (rand - 1) * (key + 1) - SQN;
	// cout<<"AUTN="<<autn<<endl;
	CK = (rand + 1) * (key - 1) - (SQN + 1);
	IK = (rand + 1) * (key + 1) - (SQN - 1);
	K_ASME = KDF(SQN, SN_ID, CK, IK);
	authenticationVector[0] = autn;
	authenticationVector[1] = res;
	authenticationVector[2] = K_ASME;
	return authenticationVector;
}

/*
 * KDF: Key Derivation Function
 */
unsigned long long UserEquipment::KDF(int SQN, int SN_ID, unsigned long long CK, unsigned long long IK) { 
	unsigned long long K_ASME; //ASME: Access Security Management Entity (in this case MME)
	K_ASME = SQN * CK + SN_ID * IK;
	return K_ASME;
}

/*
 * KDF used in NAS setup
 */
vector<string> UserEquipment::KDF_NAS(int K_ASME, int NAS_integrity_algo_id, int NAS_cipher_algo_id){ 
	vector<string> NAS_keys(2);
	// NAS_keys[0]: Integrity key K_NAS_int, NAS_keys[1]: Encryption key K_NAS_enc 
	long K_NAS_int = K_ASME * 2 + NAS_integrity_algo_id; 
	long K_NAS_enc = K_ASME * 4 + NAS_cipher_algo_id; 
	NAS_keys[0] = to_string(K_NAS_int); 	// Integrity key
	NAS_keys[1] = to_string(K_NAS_enc); 	// Cipher key
	if (NAS_keys[1].size() >= ENC_KEY_LENGTH){
		NAS_keys[1] = NAS_keys[1].substr(0, ENC_KEY_LENGTH);
	} else{
		NAS_keys[1] = NAS_keys[1] + SAMPLE_ENC_KEY.substr(0, (ENC_KEY_LENGTH-NAS_keys[1].size()));
	}
	return NAS_keys; 
}


/*
 * This function is used to simulate decryption of received data.
 */
void UserEquipment::receiveEncryptedData(Client& user, string text, string method){
	unsigned char *cipherkey = (unsigned char *)NAS_KEYS[1].c_str();
	unsigned char *encrypted_text;
	unsigned char deryptedtext[BUFFER_SIZE];
	int plaintext_len;
	string send;
	text = "dddddddd";
	
	encrypted_text = (unsigned char *)(text).c_str();
	// Decrypt the encrypted_text
	plaintext_len = encrypt (encrypted_text, strlen ((char *)encrypted_text), cipherkey, deryptedtext);

	char tmpPlaintext[plaintext_len];
	for (int i = 0; i < plaintext_len; i++){
		tmpPlaintext[i] = (char) deryptedtext[i];
	}
	hmacDigest2(tmpPlaintext, NAS_KEYS[0]);
}

/*
 * This function is used to simulate encryption of the data to be sent.
 */
void UserEquipment::sendEncryptedData(Client& user, string initial, string text, string method){
	unsigned char *cipherkey = (unsigned char *)NAS_KEYS[1].c_str();
	unsigned char *plaintext;
	unsigned char ciphertext[BUFFER_SIZE];
	int ciphertext_len;
	string send;
	plaintext = (unsigned char *)(text).c_str();

	// Encrypt the plaintext
	ciphertext_len = encrypt (plaintext, strlen ((char *)plaintext), cipherkey, ciphertext);

	char tmpCiphertext[ciphertext_len];
	for (int i = 0; i < ciphertext_len; i++){
		tmpCiphertext[i] = (char) ciphertext[i];
	}
	hmacDigest2(tmpCiphertext, NAS_KEYS[0]);
}


UserEquipment::~UserEquipment(){
	// Dummy destructor
}
