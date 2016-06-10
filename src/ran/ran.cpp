/***************************************************************************************
* This is the main file which creates UE threads and executes all the LTE procedures.  *
***************************************************************************************/

#include "ran.h"

// Boolean values corresponding to various LTE procedures
bool setUpTunnel = true;
bool doDetach = true;
bool sendData = false;
bool assignIpToInterface = false;
bool s1_release = true;		// UE initiated S1 Release
bool doEncryption = true;
bool checkIntegrity = true;
bool ueServiceRequest = true;	// UE initiated service request (uplink traffic)
bool networkServiceRequest = false;	// Network initiated service request (downlink traffic)

struct _threadArgs {
	int threadId;
	int serverPort;
	int port_gap;
	int starting_ue_id;
	int ue_id_gap;		// Number of ids of UE initially allocated to each thread
	int num_threads;
};

struct _ipArgs {
	string startingIp;
	long numIps;
};

// To store the statistics for the simulation
vector<unsigned long long> num_ue_per_thread;
vector<unsigned long long> ue_registration_response_time;

string rate;		// Rate at which each UE sends data
time_t endTime;		// Simulation end time
int cnt = 0;

// Thread function for each simulated UE
void* multithreading_func(void *arg){
	struct _threadArgs *args = (struct _threadArgs *)arg;
	int threadId = args->threadId;
	int startingPort = args->serverPort;
	int ue_num = args->starting_ue_id;
	int port_gap =  args->port_gap;
	int gap = args->ue_id_gap;
	int maxThreads = args->num_threads;
	time_t curTime;
	time(&curTime);
	int currentPort = startingPort;
	int endPort = startingPort + port_gap;
	pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
	pthread_setcanceltype(PTHREAD_CANCEL_ASYNCHRONOUS, NULL);
	struct timeval start, end;
	long mtime, useconds, seconds;
	size_t idleSleepTime, serviceRequestSleepTime;

	Client user, sink_user;
	vector<string> tmpArray;
	vector<string> service_response;

	
	while(curTime < endTime){
		UserEquipment ue(threadId+1);
		user.input_server_details(g_mme_port, g_mme_address);
		if(DO_DEBUG){
			cout<<"Attaching with MME"<<endl;
		}

		gettimeofday(&start, NULL);
		if(attach_with_mme(ue, user, checkIntegrity)){ 	// Authentication
			if(setUpTunnel){
				// Setup tunnel
				tmpArray = setup_tunnel(ue, user, doEncryption);
				//tmpArray: [0] = SEND_IP_SGW_TEID, [1] = IP Address of UE, [2] = SGW TEID, [3] = ue_teid, [4] = GUTI

				if(tmpArray.size()==6){
					if(s1_release){
						do{
							idleSleepTime = (size_t) dist_idle_time(generator);

						}while(idleSleepTime == 0);

						sleep(idleSleepTime); // 20 sec
						if(DO_DEBUG){
							cout<<"SLEEPING for "<<idleSleepTime<<" seconds"<<" for UE NUM="<<ue_num<<endl;
							cout<<"UE CONTEXT RELEASE INITIATED with UE NUM="<<ue_num<<endl;
						}
						ue_context_release(ue, user, ue_num, tmpArray[1], tmpArray[2], tmpArray[3], currentPort, networkServiceRequest);
						if(DO_DEBUG){
							cout<<"UE CONTEXT RELEASE DONE with UE NUM="<<ue_num<<endl;
						}

						if(ueServiceRequest){
							do{
								serviceRequestSleepTime = (size_t) dist_service_request_time(generator);
							}while(serviceRequestSleepTime == 0);
							sleep(serviceRequestSleepTime);

							// tmpArray[1] = UE IP   tmpArray[2]= SGW TEID  tmpArray[4]=GUTI
							if(DO_DEBUG){
								cout<<"UE SERVICE REQUEST INITIATED with UE NUM="<<ue_num<<" after "<<serviceRequestSleepTime<<" seconds"<<endl;
							}
							
							tmpArray[3] = ue_service_request(ue, user, ue_num, tmpArray[1]); //returns newly generated ue_teid
							if(DO_DEBUG){
								cout<<"UE SERVICE REQUEST DONE with UE NUM="<<ue_num<<endl;
							}
						}

						else if(networkServiceRequest){
							tmpArray[3] = network_service_request(ue, user, ue_num, tmpArray[1]); //returns newly generated ue_teid
							if(DO_DEBUG){
								cout<<"NETWORK SERVICE REQUEST DONE with UE NUM="<<ue_num<<endl;
							}
						}
					}
					if(sendData){
						// Send data
						currentPort = send_ue_data(ue, ue_num, rate, currentPort, startingPort, endPort, user, tmpArray);
					}

					if(doDetach){
						// Initiate detach
						detach_ue(ue, user, ue_num, tmpArray[1], tmpArray[2], tmpArray[4]);
					}else{
						cnt++;
					}
					num_ue_per_thread[threadId] += 1;
				}else{
					cout<<"ERROR in setting up tunnel for UE with key="<<ue_num<<endl;
					exit(1);
				}
			}else{
				doDetach = false;
				sendData = false;
				num_ue_per_thread[threadId] += 1;
			}

		}else{
			cout<<"Authentication Error"<<endl;
			exit(1);
		}
		gettimeofday(&end, NULL);
		seconds  = end.tv_sec  - start.tv_sec;
		useconds = end.tv_usec - start.tv_usec;
		mtime = ((seconds) * 1000000 + useconds);
		ue_registration_response_time[threadId] += mtime;

		if(sendData){
			currentPort++;
			if(currentPort >= endPort){
				currentPort = startingPort;
			}
		}
		time(&curTime);
	}
	free(args);
	pthread_exit(NULL);
}

bool attach_with_mme(UserEquipment &ue, Client &user, bool checkIntegrity){
	return ue.authenticate(user, checkIntegrity);
}

string get_starting_IP_Address(UserEquipment &ue, Client &user){
	return ue.getStartingIPAddress(user);
}

vector<string> setup_tunnel(UserEquipment &ue, Client &user, bool doEncryption){
	return ue.setupTunnel(user, doEncryption);
}

int send_ue_data(UserEquipment &ue, int ue_num, string rate, int serverPort, int startingPort, int endPort, Client &user, vector<string>& tmpArray){
	return ue.sendUserData(user, ue_num, rate, serverPort, startingPort, endPort, tmpArray);
}

void ue_context_release(UserEquipment &ue, Client &user, int ue_num, string ue_ip, string sgw_teid, string ue_teid, int currentPort, bool networkServiceRequest){
	ue.initiate_ue_context_release(user, ue_num, ue_ip, sgw_teid, ue_teid, currentPort, networkServiceRequest);
}

string ue_service_request(UserEquipment &ue, Client &user, int ue_num, string ue_ip){
	return ue.send_ue_service_request(user, ue_num, ue_ip);
}

string network_service_request(UserEquipment &ue, Client &user, int ue_num, string ue_ip){
	return ue.receive_paging_request(user, ue_num, ue_ip);
}

void detach_ue(UserEquipment &ue, Client &user, int ue_num, string ue_ip, string sgw_teid, string ue_teid){
	ue.initiate_detach(user, ue_num, ue_ip, sgw_teid, ue_teid);
}

// Generate UE IP addresses and assign to the network interface
void* multithreading_add_ip(void *arg){
	string c = "";
	struct _ipArgs *args = (struct _ipArgs *)arg;
	string ip = args->startingIp;
	long numIp = args->numIps;
	system(("sudo chmod +x ipgen.sh"+c+"").c_str());
	string s = "./ipgen.sh "+ip+" "+SINK_SERVER_NETMASK+" "+ to_string(numIp) +" "+DEFAULT_IF;
	if(DO_DEBUG){
		cout<<"Generating IPs: "<<s<<endl;
	}
	system(s.c_str());
}

int main(int argc, char *args[]){
	long maxThreads = 0;
	int status;
	stringstream ss;
	string data = "";
	std::ofstream outfile;

	if(argc != 4 && argc != 5){
		fprintf(stderr,"Usage: %s <max-threads> <program-run-time(in mins)> <rate[e.g. 20M (i.e. 20 Mbits/sec)  or 20K (i.e. 20 Kbits/sec)]> [<SINK_SERVER_STARTING_PORT (default:13001)>]\n", args[0]);
		exit(0);
	}

	maxThreads = atoi(args[1]);
	if(maxThreads <= 0){
		printf("Number of threads should be greater than 0\n");
		exit(0);
	}
	double tmp;
	ss << args[2];
	ss >> tmp;
	if(tmp <= 0.0){
		printf("Run time of each threads should be greater than 0.0\n");
		exit(0);
	}

	rate = args[3];
	if(rate.size() <= 1){
		printf("Rate should be of format XM or XK e.g. 20M (i.e. 20 Mbits/sec)  or 20K (i.e. 20 Kbits/sec)\n");
		exit(0);
	}

	if(argc == 5){
		int tmp_port = atoi(args[4]);
		if (tmp_port >= 65536 || tmp_port <= 1023){
			printf("Server port number should lie in [1024, 65535] only \n");
			exit(0);
		}
		SINK_SERVER_STARTING_PORT = tmp_port;
	}

	string c = "";
	system(("ifconfig"+c+" "+DEFAULT_IF+" mtu "+to_string(LINK_MTU)).c_str());

	if(sendData || networkServiceRequest){
		// Get the starting UE IP address from controller
		UserEquipment ue(1);
		Client user;
		user.input_server_details(g_mme_port, g_mme_address);
		string startingIp = get_starting_IP_Address(ue, user);

		if(assignIpToInterface){
			// Generate and assign UE IP addresses in advance
			cout<<"Allocating IPs. Please wait..."<<endl;
			time_t start;
			time(&start);
			pthread_t ipAddThread;
			struct _ipArgs * argss =   (struct _ipArgs *)malloc(sizeof(struct _ipArgs));
			argss->startingIp = startingIp;
			argss->numIps = 2 * maxThreads + 10;
			status = pthread_create(&ipAddThread,NULL, multithreading_add_ip, argss);
			report_error(status);
			pthread_join(ipAddThread, NULL);
			time_t end;
			time(&end);
			cout<<"time="<<(end-start)<<"sec"<<endl;
			sleep(5);
			if(DO_DEBUG){
				cout<<"Starting IP="<<startingIp<<endl;
			}
		}
	}

	num_ue_per_thread.resize(maxThreads, 0);
	ue_registration_response_time.resize(maxThreads, 0);

	cout<<"***************STARTING NOW***************"<<endl;
	tmp = tmp * 60;
	time_t curTime;
	time(&curTime);
	if(DO_DEBUG){
		cout<<"start time="<<curTime<<endl;
	}
	endTime = curTime + (int) tmp;
	if(DO_DEBUG){
		cout<<"end time="<<endTime<<endl;
	}
	int simulationTime = (int) tmp;

	pthread_t tid[maxThreads];
	int gap = 1;
	int port_gap = PER_THREAD_PORT_WINDOW;
	int total_non_global_ports = maxThreads * port_gap;
	int global_starting_port = SINK_SERVER_STARTING_PORT + total_non_global_ports;
	// Filling global ports
	for(int i = 0; i < NUM_GLOBAL_PORTS; i++){
		global_ports[i] = global_starting_port + i;
	}

	// Create UE threads
	for(int i = 0;i<maxThreads;i++){
		struct _threadArgs * args = (struct _threadArgs *)malloc(sizeof(struct _threadArgs));
		args->threadId = i;
		args->serverPort = SINK_SERVER_STARTING_PORT + i * port_gap;
		args->port_gap = port_gap;
		args->starting_ue_id = 1 + i * gap;
		args->ue_id_gap = gap;
		args->num_threads = maxThreads;

		status = pthread_create(&tid[i], NULL, multithreading_func, args);
		report_error(status);
	}

	string s = "";
	int total_ue = 0;
	unsigned long long total_reistration_time = 0;
	double average_registration_time = 0.0;
	double registrationThroughput = 0.0;

	// Sleep for the specified simulation time
	usleep(simulationTime * 1000000); // 1sec

	/* Wake up and cancel/join all the UE threads to end simulation */
	for(int i=0;i<maxThreads;i++){
		pthread_cancel(tid[i]);
		pthread_join(tid[i],NULL);
	}

	/* Calculate and display various metrics */
	time_t actual_endTime;
	time(&actual_endTime);
	for(int i=0;i<maxThreads;i++){
		total_ue += num_ue_per_thread[i];
		total_reistration_time += ue_registration_response_time[i];
		cout<<"num_ue_per_thread["<<i<<"] "<<num_ue_per_thread[i]<<endl;
		cout<<"ue_registration_response_time["<<i<<"] "<<((ue_registration_response_time[i]*1.0)/num_ue_per_thread[i])<<" us"<<endl;
	}
	average_registration_time = (total_reistration_time*1.0)/(total_ue*1.0);
	registrationThroughput = (total_ue*1.0)/(actual_endTime - curTime);

	cout<<"***************************************STATISTICS***************************************"<<endl;
	execution_done = true;
	double averageUEperThread = ((total_ue*1.0)/maxThreads);
	averageUEperThread = roundf(averageUEperThread * 100) / 100; 
	ostringstream strs;
	strs << averageUEperThread;
	string avUE = strs.str();
	printf("Total Number of Threads=%d\n", maxThreads);
	printf("Total Number of UE=%d\n", total_ue);
	cout<<"Average Number of UE per Thread="<<averageUEperThread<<endl;
	printf("Total Execution Time=%d sec\n", (actual_endTime - curTime));

	average_registration_time = average_registration_time/1000000.0;
	cout<<"Latency = "<<average_registration_time<<" secs"<<endl;
	cout<<"Registration Throughput="<<registrationThroughput<<" registrations/sec"<<endl;

	/* Write the metrics to the statistics file */
	if(!fileExists(STATISTIC_FILE)){
		data.append("#MaxThreads").append(COMMA).append("#UE").append(COMMA).append("AvUE/Thread").append(COMMA);
		data.append("Execution Time").append(COMMA);
		data.append("Registration Throughput(registrations/sec)").append(COMMA);
		data.append("Registration Latency (in sec)").append(COMMA);
		data.append("Setup Tunnel (Yes/No)").append(COMMA);
		data.append("DO Detach (Yes/No)").append(COMMA);
		data.append("Sending Data? (Yes/ No)").append(COMMA).append("Mean Data Sending Time").append(COMMA).append("Sending Rate").append("\n");
	}

	outfile.open(STATISTIC_FILE, std::ios_base::app);
	if (outfile.is_open()){
		data.append(to_string(maxThreads)).append(COMMA).append(to_string(total_ue)).append(COMMA).append(avUE).append(COMMA);
		data.append(to_string((actual_endTime - curTime))).append(COMMA);
		data.append(to_string(registrationThroughput)).append(COMMA);
		data.append(to_string(average_registration_time)).append(COMMA);
		data.append(setUpTunnel?"Yes":"No").append(COMMA);
		data.append(doDetach?"Yes":"No").append(COMMA);
		data.append(sendData?"Yes":"No").append(COMMA);
		data.append(to_string(UE_MEAN_DATA_SENDING_TIME)).append(COMMA).append(rate).append("\n");
		outfile << data;
		outfile.close();
	}

	/* Kill all the iperf3 client processes after the simulation ends */
	if(sendData){
		system(("sudo killall iperf3 "+c+"").c_str());
	}

	exit(0);
	return 0;
}

inline bool fileExists (const std::string& name) {
	struct stat buffer;   
	return (stat (name.c_str(), &buffer) == 0); 
}