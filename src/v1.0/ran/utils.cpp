/****************************************************************************************
 * This file contains all the utility functions such as integrity and cipher functions. *
 ****************************************************************************************/

#include "utils.h"

int g_mme_port = 9876;
const char *g_mme_address = DGW_IP; // Packets should reach the default switch, so setting its IP to that of default switch
default_random_engine generator;
exponential_distribution<double> distribution(1.0/UE_MEAN_DATA_SENDING_TIME); //(1/lambda)->mean of distribution
exponential_distribution<double> dist_idle_time(1.0/UE_MEAN_IDLE_TIME); //(1/lambda)->mean of distribution
exponential_distribution<double> dist_service_request_time(1.0/UE_MEAN_SERVICE_REQUEST_TIME); //(1/lambda)->mean of distribution
uniform_int_distribution<int> uniform_distribution(MIN_TEID, MAX_TEID);
uniform_int_distribution<int> random_ue_nw_capability(MIN_NW_CAPABILITY, MAX_NW_CAPABILITY);
string SAMPLE_ENC_KEY = "ABCD1234EFGH5678IJKL9012MNOP3456";
volatile bool execution_done =  false;
vector<int> global_ports(NUM_GLOBAL_PORTS);
int globalPortsIndex = 0;
pthread_mutex_t request_mutex;
int SINK_SERVER_STARTING_PORT = 13001;

unordered_map<string, int> UE_IP_SGW_TEID_MAP;

void report_error(int arg){
	if(arg < 0){
		perror("ERROR");
		exit(EXIT_FAILURE);
	}
}

void print_message(string message){
	cout<<"***********************"<<endl;
	cout<<message<<endl;
	cout<<"***********************"<<endl;
}

void print_message(string message, int arg){
	cout<<"***********************"<<endl;
	cout<<message<<" "<<arg<<endl;
	cout<<"***********************"<<endl;
}

void print_message(string message, unsigned long long arg){
	cout<<"***********************"<<endl;
	cout<<message<<" "<<arg<<endl;
	cout<<"***********************"<<endl;
}

const char* to_char_array(unsigned long long arg){
	string tem;
	stringstream out;
	out<<arg;
	tem = out.str();
	const char *ans = tem.c_str();
	return ans;
}

string longToString(unsigned long long arg){
	stringstream out;
	out<<arg;
	return out.str();
}

void trim(string& s){
	s.erase(s.find_last_not_of(" \n\r\t")+1);
}

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

// Uses the algorithm HMAC SHA1
//Reference:- http://www.askyb.com/cpp/openssl-hmac-hasing-example-in-cpp/
string hmacDigest(string msg, string keyString) {
	// The secret key for hashing
	char key[keyString.size()+1];
	copy(keyString.begin(), keyString.end(), key);
	key[keyString.size()] = '\0';

    // The data that we're going to hash
	char data[msg.size()+1];
	copy(msg.begin(), msg.end(), data);
	data[msg.size()] = '\0';

	// const char *data = msg.c_str();

    // Be careful of the length of string with the choosen hash engine. SHA1 needed 20 characters.
    // Change the length accordingly with your choosen hash engine.     
	unsigned char* result;
	unsigned int len = 20;

	result = (unsigned char*)malloc(sizeof(char) * len);

	HMAC_CTX ctx;
	HMAC_CTX_init(&ctx);

    // Using sha1 hash engine here.
    // You may use other hash engines. e.g EVP_md5(), EVP_sha224, EVP_sha512, etc
	HMAC_Init_ex(&ctx, key, strlen(key), EVP_sha1(), NULL);
	HMAC_Update(&ctx, (unsigned char*)&data, strlen(data));
	HMAC_Final(&ctx, result, &len);
	HMAC_CTX_cleanup(&ctx);

	string output = "";
	char str[len];

	for (int i = 0; i != len; i++){
		sprintf(str, "%02x", (unsigned int)result[i]);
		string temp(str);
		output = output + temp;
	}
	return output;
}

string hmacDigest2(char data[], string keyString) {

	// The secret key for hashing
	char key[keyString.size()+1];
	copy(keyString.begin(), keyString.end(), key);
	key[keyString.size()] = '\0';

    // Be careful of the length of string with the choosen hash engine. SHA1 needed 20 characters.
    // Change the length accordingly with your choosen hash engine.     
	unsigned char* result;
	unsigned int len = 20;

	result = (unsigned char*)malloc(sizeof(char) * len);

	HMAC_CTX ctx;
	HMAC_CTX_init(&ctx);

    // Using sha1 hash engine here.
    // You may use other hash engines. e.g EVP_md5(), EVP_sha224, EVP_sha512, etc
	HMAC_Init_ex(&ctx, key, strlen(key), EVP_sha1(), NULL);
	HMAC_Update(&ctx, (unsigned char*)&data, strlen(data));
	HMAC_Final(&ctx, result, &len);
	HMAC_CTX_cleanup(&ctx);

	string output = "";
	char str[len];

	for (int i = 0; i != len; i++){
		sprintf(str, "%02x", (unsigned int)result[i]);
		string temp(str);
		output = output + temp;
	}
	return output;
}

void handleErrors(void) {
	ERR_print_errors_fp(stderr);
	abort();
}

int encrypt(unsigned char *plaintext, int plaintext_len, unsigned char *key,
	unsigned char *ciphertext) {
	
	EVP_CIPHER_CTX *ctx;

	int len;
	unsigned char *iv = (unsigned char *)"0123456789012345";
	int ciphertext_len;

  /* Create and initialise the context */
	if(!(ctx = EVP_CIPHER_CTX_new())) handleErrors();

  /* Initialise the encryption operation. IMPORTANT - ensure you use a key
   * and IV size appropriate for your cipher
   * In this example we are using 256 bit AES (i.e. a 256 bit key). The
   * IV size for *most* modes is the same as the block size. For AES this
   * is 128 bits */
   if(1 != EVP_EncryptInit_ex(ctx, EVP_aes_256_cbc(), NULL, key, iv))
   	handleErrors();

  /* Provide the message to be encrypted, and obtain the encrypted output.
   * EVP_EncryptUpdate can be called multiple times if necessary
   */
   if(1 != EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, plaintext_len))
   	handleErrors();
   ciphertext_len = len;

  /* Finalise the encryption. Further ciphertext bytes may be written at
   * this stage.
   */
   if(1 != EVP_EncryptFinal_ex(ctx, ciphertext + len, &len)) handleErrors();
   ciphertext_len += len;

  /* Clean up */
   EVP_CIPHER_CTX_free(ctx);

   return ciphertext_len;
}

int decrypt(unsigned char *ciphertext, int ciphertext_len, unsigned char *key,
	unsigned char *plaintext) {
 	// Initialise the library 
	ERR_load_crypto_strings();
	OpenSSL_add_all_algorithms();
	OPENSSL_config(NULL);
	EVP_CIPHER_CTX *ctx;

	int len;
	unsigned char *iv = (unsigned char *)"0123456789012345";
	int plaintext_len;

  /* Create and initialise the context */
	if(!(ctx = EVP_CIPHER_CTX_new())) handleErrors();

  /* Initialise the decryption operation. IMPORTANT - ensure you use a key
   * and IV size appropriate for your cipher
   * In this example we are using 256 bit AES (i.e. a 256 bit key). The
   * IV size for *most* modes is the same as the block size. For AES this
   * is 128 bits */
   if(1 != EVP_DecryptInit_ex(ctx, EVP_aes_256_cbc(), NULL, key, iv))
   	handleErrors();

  /* Provide the message to be decrypted, and obtain the plaintext output.
   * EVP_DecryptUpdate can be called multiple times if necessary
   */
   if(1 != EVP_DecryptUpdate(ctx, plaintext, &len, ciphertext, ciphertext_len))
   	handleErrors();
   plaintext_len = len;

  /* Finalise the decryption. Further plaintext bytes may be written at
   * this stage.
   */
   if(1 != EVP_DecryptFinal_ex(ctx, plaintext + len, &len)) handleErrors();
   plaintext_len += len;

  /* Clean up */
   EVP_CIPHER_CTX_free(ctx);

   return plaintext_len;
}

string aesEncrypt(string pText, string secretKey) {
	int bytes_read, bytes_written;
	unsigned char indata[AES_BLOCK_SIZE];
	unsigned char outdata[AES_BLOCK_SIZE + 1];
	string encText = "";

  /* ckey and ivec are the two 128-bits keys necesary to en- and recrypt your data.
  	 Note that ckey can be 192 or 256 bits as well */
	unsigned char *ckey =  (unsigned char*)secretKey.c_str();
	unsigned char ivec[] = "dontusethisinput";

  /* data structure that contains the key itself */
	AES_KEY key;

  /* set the encryption key */
	AES_set_encrypt_key(ckey, 128, &key);

  /* set where on the 128 bit encrypted block to begin encryption*/
	int num = 0;

	int ind = 0;
	for(int i=0;i<pText.length();i++){
		indata[ind++] = (unsigned char)pText[i];
		if(ind == AES_BLOCK_SIZE){
			AES_cfb128_encrypt(indata, outdata, ind, &key, ivec, &num, AES_ENCRYPT);
			outdata[AES_BLOCK_SIZE] = '\0';
			string str ((const char*)outdata);
			encText += str;
			bzero(indata, AES_BLOCK_SIZE);
			bzero(outdata, AES_BLOCK_SIZE);
			ind = 0;
		}
	}
	if(ind != 0){
		AES_cfb128_encrypt(indata, outdata, ind, &key, ivec, &num, AES_ENCRYPT);
		outdata[ind] = '\0';
		string str ((const char*)outdata);
		encText += str;
	}
	return encText;
}

string aesDecrypt(string encText, string secretKey) {
	int bytes_read, bytes_written;
	unsigned char indata[AES_BLOCK_SIZE];
	unsigned char outdata[AES_BLOCK_SIZE + 1];
	string decText = "";

  /* ckey and ivec are the two 128-bits keys necesary to
     en- and recrypt your data.  Note that ckey can be
     192 or 256 bits as well */
     unsigned char *ckey =  (unsigned char*)secretKey.c_str();
     unsigned char ivec[] = "dontusethisinput";

  /* data structure that contains the key itself */
     AES_KEY key;

  /* set the encryption key */
     AES_set_encrypt_key(ckey, 128, &key);

  /* set where on the 128 bit encrypted block to begin encryption*/
     int num = 0;

     int ind = 0;
     for(int i=0;i<encText.length();i++){
     	indata[ind++] = (unsigned char)encText[i];
     	if(ind == AES_BLOCK_SIZE){
     		AES_cfb128_encrypt(indata, outdata, ind, &key, ivec, &num, AES_DECRYPT);
     		outdata[AES_BLOCK_SIZE] = '\0';
     		string str ((const char*)outdata);
     		decText += str;
     		bzero(indata, AES_BLOCK_SIZE);
     		bzero(outdata, AES_BLOCK_SIZE);
     		ind = 0;
     	}
     }
     if(ind != 0){
     	AES_cfb128_encrypt(indata, outdata, ind, &key, ivec, &num, AES_DECRYPT);
     	outdata[ind] = '\0';
     	string str ((const char*)outdata);
     	decText += str;
     }
     return decText;
 }