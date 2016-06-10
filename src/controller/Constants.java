/***********************************************************************
 * This file contains all the constants used in the controller's code  *
 ***********************************************************************/
package net.floodlightcontroller.MTP3;

import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;

public class Constants {
	
	// Boolean flag to do the logging of events
	static boolean DEBUG = false;
	
	/***************************Configurable parameters**********************************/
	
	final static String UE_MAC = "40:8d:5c:76:d2:67"; // MAC Address of UE/eNodeB Node  *
	final static String DEFAULT_GW_MAC = "1e:12:62:1d:67:a5"; // MAC Address of DGW     *
	final static String SINK_MAC = "40:8d:5c:76:d2:fa"; // MAC Address of SINK Node     *
	
	// IP address of various component interfaces										*
	final static String RAN_IP = "10.127.41.2";			// RAN							*
	final static String DSWITCH_IP_UPLINK = "10.127.41.3";	// DGW						*
	final static String DSWITCH_IP_DOWNLINK = "10.126.41.3";	// DGW					*
	final static String SGW_IP_UPLINK = "10.126.41.4";	// SGW							*
	final static String SGW_IP_DOWNLINK = "10.125.41.4";	// SGW						*
	final static String PGW_IP_UPLINK = "10.125.41.5";	// PGW							*
	final static String PGW_IP_DOWNLINK = "10.127.41.5";	// PGW						*
	final static String SINK_IP = "10.127.41.6";	// SINK								*

	// The starting IP address which is allocated to the first UE connecting to our		*
	// network. After this addresses are assigned in monotonically increasing order.	*
	final static String STARTING_UE_IP = "10.127.41.7";
	
	/************************************************************************************/

	/*********************************************
	 * Authentication and UE Tunnel Setup Codes  *
	 *********************************************/
	final static String AUTHENTICATION_FAILURE = "-1";
	final static String AUTHENTICATION_STEP_ONE = "1";
	final static String AUTHENTICATION_STEP_TWO = "2";
	final static String AUTHENTICATION_STEP_THREE = "3";
	final static String NAS_STEP_ONE = "4";
	final static String SEND_APN = "5";
	final static String SEND_IP_SGW_TEID = "6";
	final static String SEND_UE_TEID = "7";
	final static String ATTACH_ACCEPT = "8";
	final static String SEPARATOR = "@:##:@";
	final static String DETACH_REQUEST = "9";
	final static String DETACH_ACCEPT = "10";
	final static String DETACH_FAILURE = "11";
	final static String REQUEST_STARTING_IP = "12";
	final static String SEND_STARTING_IP = "13";
	final static String UE_CONTEXT_RELEASE_REQUEST = "14";
	final static String UE_CONTEXT_RELEASE_COMMAND = "15";
	final static String UE_CONTEXT_RELEASE_COMPLETE = "16";
	final static String UE_SERVICE_REQUEST = "17";
	final static String INITIAL_CONTEXT_SETUP_REQUEST = "18";
	final static String INITIAL_CONTEXT_SETUP_RESPONSE = "19";
	final static String NAS_STEP_TWO = "20";
	final static String PAGING_REQUEST = "21";
	final static String INITIATE_NETWORK_SERVICE_REQUEST = "22";
	final static String SINK_SERVICE_REQUEST = "23";

	// Serving Network ID of the MME
	final static int SN_ID = 1;

	// Network type identifier
	final static String NW_TYPE = "UMTS";

	// Key length for AES encryption/decryption algorithm
	final static int ENC_KEY_LENGTH = 32;

	// Sample AES encryption/decryption key
	final static String SAMPLE_ENC_KEY = "ABCD1234EFGH5678IJKL9012MNOP3456";

	// Range of Tunnel IDs from 1 to 4095 depending upon the length of VLAN field of ethernet header
	final static int MIN_TEID = 1;
	final static int MAX_TEID = 4095;

	// DPID or unique ID of SGW switch (assuming only one sgw in the network)
	final static int SGW_DPID = 2;

	// boolean flags which control whether encryption and integrity checks needs to be performed or not.
	static boolean DO_ENCRYPTION = true;
	static boolean CHECK_INTEGRITY = true;

	// DPID or unique ID of default switch 
	final static int DEFAULT_SWITCH_ID = 1;
	
	// DPID of SGW
	final static int SGW_ID = 2;
	
	// DPID of PGW
	final static int PGW_ID = 4;
	
	// its the source port to be used by MME while sending UDP packets to UE
	final static int DEFAULT_CONTROL_TRAFFIC_UDP_PORT = 9876;

	// Port of Default switch with which UE is connected
	final static int DEFAULT_SWITCH_UE_PORT = 3;
	
	// Port of pgw with which sink is connected
	final static int PGW_SINK_PORT = 4;

	// We have assumed there are two SGWs viz. SGW-1 and SGW-2 between our default switch and PGW.
	// Note: One one SGW would also work, just the port number with with it is connected to default
	// switch and PGW should be correct.
	@SuppressWarnings("serial")
	final static HashMap<String, Integer> PGW_SGW_PORT_MAP = new HashMap<String, Integer>()
	{{
		put("4" + SEPARATOR + "2", 3); // for switch S2(SGW-1) connected to S4(PGW) via port 4 of PGW
		put("4" + SEPARATOR + "3", 1); // for switch S3(SGW-2) connected to S4(PGW) via port 1 of PGW
	}};

	@SuppressWarnings("serial")
	final static HashMap<DatapathId, int[]> SGW_PORT_MAP = new HashMap<DatapathId, int[]>()
	{{
		put(DatapathId.of(2), new int[]{3,4}); // new int[]{SGW-INPORT, SGW-OUTPORT}
		put(DatapathId.of(3), new int[]{3,4}); // new int[]{SGW-INPORT, SGW-OUTPORT}
	}};

	final static int UE_PORT = 3; //port with which default switch is connected to UE
	@SuppressWarnings("serial")
	final static HashMap<String, Integer> ENODEB_SGW_PORT_MAP = new HashMap<String, Integer>()
	{{
		put(DEFAULT_SWITCH_ID + SEPARATOR + "2", 4);// , 3// for switch S2(SGW-1) connected to S1(ENODEB) via port 3 of Default Switch
		put(DEFAULT_SWITCH_ID + SEPARATOR + "3", 1);// , 4// for switch S3(SGW-2) connected to S1(ENODEB) via port 4 of Default Switch
	}};

	// Stores Algorithm Id for encryption/integrity algorithms
	@SuppressWarnings("serial")
	final static HashMap<String, Integer> CIPHER_ALGO_MAP = new HashMap<String, Integer>()	
	{{
		put("AES", 1);
		put("HmacSHA1", 2);
	}};

}
