/****************************************************************
 * This file contains code of MME and also contains code which  *
 * install/deletes flow rules from the default switch           *
 ****************************************************************/

package net.floodlightcontroller.MTP3;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.PacketParsingException;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MME implements IFloodlightModule, IOFMessageListener, IOFSwitchListener {
	protected static Logger log = LoggerFactory.getLogger(MME.class);
	private IFloodlightProviderService floodlightProvider;
	private HashMap<String, String> imsi_xres_mapping = new HashMap<String, String>();
	private HashMap<String, String> uekey_sgw_teid_map = new HashMap<String, String>();
	private Hashtable<String, String> sgw_teid_uekey_map = new Hashtable<String, String>();
	private HashMap<String, String> uekey_ueip_map = new HashMap<String, String>();
	private HashMap<DatapathId, IOFSwitch> switch_mapping = new HashMap<DatapathId, IOFSwitch>();
	private HashMap<String, Boolean> ue_state = new HashMap<String, Boolean>();	// Key: UE_Key, Value: State (TRUE: Active, FALSE: Idle)
	private HashMap<String, String> uekey_guti_map = new HashMap<String, String>();	// Key: UE_Key, Value: GUTI
	private HashMap<String, String[]> uekey_nas_keys_map = new HashMap<String, String[]>();		// Key = IMSI, Value = [0]: K_ASME, [1]: NAS Integrity key, NAS Encryption key
	private HashMap<String, TransportPort> uekey_udp_src_port_map = new HashMap<String, TransportPort>(); // key = ue key, Value = UE UDP port number 

	HashMap<DatapathId, Long> switchStats =  new HashMap<DatapathId, Long>();
	DatapathId defaultSwitch = DatapathId.of(Constants.DEFAULT_SWITCH_ID); 
	int uePort;
	protected IThreadPoolService threadPoolService;
	protected IOFSwitchService switchService;
	protected SingletonTask discoveryTask;
	Set<DatapathId> switches=null;
	SGW sgw = new SGW();
	IOFSwitch sw = null;
	HSS hss = new HSS();

	public MME(){
		uePort = Constants.UE_PORT;
	}

	public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
		this.floodlightProvider = floodlightProvider;
	}

	@Override
	public String getName() {
		return MME.class.getPackage().getName();
	}

	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			if(DatapathId.of(Constants.DEFAULT_SWITCH_ID).equals(sw.getId())) {
				return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
			}
			else if(DatapathId.of(Constants.SGW_DPID).equals(sw.getId())) {
				return this.processPacketInMessageFromSGW(sw, (OFPacketIn) msg, cntx);
			}
			return Command.CONTINUE;
		case ERROR:
			log.info("received an error {} from switch {}", msg, sw);
			return Command.CONTINUE;
		default:
			log.error("received an unexpected message {} from switch {}", msg, sw);
			return Command.CONTINUE;
		}
	}

	private Command processPacketInMessageFromSGW(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		String payloadArray[];
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		
		/* Read packet header attributes into Match */
		Match m = createMatchFromPacket(sw, inPort, cntx);
		VlanVid vlan = m.get(MatchField.VLAN_VID) == null ? VlanVid.ZERO : m.get(MatchField.VLAN_VID).getVlanVid();
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if(eth.getEtherType() == EthType.IPv4){
			IPv4 ipPkt = (IPv4)eth.getPayload();
			if(ipPkt.getProtocol().equals(IpProtocol.UDP)){
				UDP udpPkt = (UDP)ipPkt.getPayload();
				Data dataPkt = null;
				if(Data.class.isInstance(udpPkt.getPayload())){
					dataPkt = (Data)udpPkt.getPayload();
					byte[] arr = dataPkt.getData();
					String payload = "";
					try {
						payload = new String(arr, "ISO-8859-1");
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
					if(payload.contains(Constants.SEPARATOR)){
						payloadArray = payload.split(Constants.SEPARATOR);
						// payloadArray[0]: SINK_SERVICE_REQUEST code, payloadArray[1]: UE Key
						if(payloadArray[0].equals(Constants.SINK_SERVICE_REQUEST)){
							downlinkDataNotification(payloadArray[1], vlan); // payloadArray[1]: UE Key
						}else{
							System.out.println("ERROR: Unknown message code received from sink, received: "+payload+" expected: "+Constants.SEPARATOR);
							System.exit(1);
						}
					}else{
						System.out.println("ERROR: Unknown packet received from sink, received: "+payload);
						System.exit(1);
					}
				}
			}
		}
		return Command.CONTINUE;
	}

	public String byteArrayToHex(byte[] a) {
		StringBuilder sb = new StringBuilder(a.length * 2);
		for(byte b: a)
			sb.append(String.format("%02x", b & 0xff));
		return sb.toString();
	}

	private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		/* Read packet header attributes into Match */
		Match m = createMatchFromPacket(sw, inPort, cntx);
		DatapathId sgw_dpId = null;
		MacAddress sourceMac = m.get(MatchField.ETH_SRC);
		MacAddress destMac = m.get(MatchField.ETH_DST);
		VlanVid vlan = m.get(MatchField.VLAN_VID) == null ? VlanVid.ZERO : m.get(MatchField.VLAN_VID).getVlanVid();
		TransportPort srcPort=TransportPort.of(67), dstPort=TransportPort.of(67);
		IPv4Address srcIp, dstIp;

		if (sourceMac == null) {
			sourceMac = MacAddress.NONE;
		}

		if (destMac == null) {
			destMac = MacAddress.NONE;
		}
		if (vlan == null) {
			vlan = VlanVid.ZERO;
		}

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if(eth.getEtherType() == EthType.IPv4){

			IPv4 ipPkt = (IPv4)eth.getPayload();
			srcIp = ipPkt.getSourceAddress();
			dstIp = ipPkt.getDestinationAddress();
			if(ipPkt.getProtocol().equals(IpProtocol.UDP)){
				UDP udpPkt = (UDP)ipPkt.getPayload();
				srcPort = udpPkt.getSourcePort();
				dstPort = udpPkt.getDestinationPort();

				Data dataPkt = null;
				if(Data.class.isInstance(udpPkt.getPayload())){
					dataPkt = (Data)udpPkt.getPayload();
					byte[] arr = dataPkt.getData();
					String payload = "";
					try {
						payload = new String(arr, "ISO-8859-1");
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}

					if(Constants.DEBUG){
						if(!payload.startsWith("{") && !payload.startsWith("_ipps") && !payload.startsWith("�") && !payload.contains("arpa")){
							System.out.println("RECEIVED: "+payload);
						}
					}

					@SuppressWarnings("unused")
					String tmpArray[], tmpArray2[], decArray[], NAS_Keys[];
					String NAS_MAC = null;
					String res, xres, autn, rand, K_ASME, imsi, ue_nw_capability, KSI_ASME, SQN, tai;
					StringBuilder response;
					if(payload.contains(Constants.SEPARATOR)){
						tmpArray = payload.split(Constants.SEPARATOR);
						Date d1 = null, d2 = null;
						int step = 0;
						switch(tmpArray[0]){
						case Constants.AUTHENTICATION_STEP_ONE:
							if(Constants.DEBUG){
								System.out.println("Inside case AUTHENTICATION_STEP_ONE");
								step = 1;
								d1 = d2 = null;
								d1 = new Date();
							}
							//validating user in HSS
							imsi = tmpArray[1];
							ue_nw_capability = tmpArray[2];
							KSI_ASME = tmpArray[3];
							SQN = tmpArray[4];	// UE sequence number
							tai = tmpArray[5];	// Tracking area ID
							Date d3 = new Date();
							payload = hss.validateUser(imsi, Constants.SN_ID, Constants.NW_TYPE, Integer.parseInt(SQN), tai);
							if(Constants.DEBUG){
								Date d4 = new Date();
								timeDiff(d3, d4, 11);
							}

							if(payload != null && payload.contains(Constants.SEPARATOR)){
								tmpArray2 = payload.split(Constants.SEPARATOR);
								// tmpArray2[0]: xres, tmpArray2[1]: autn, tmpArray2[2]: rand, tmpArray2[3]: K_ASME
								xres = tmpArray2[0];
								autn = tmpArray2[1];
								rand = tmpArray2[2];
								K_ASME = tmpArray2[3];
								if(Constants.DEBUG){
									System.out.println("INITIAL imsi="+imsi+" msisdn="+ ue_nw_capability);
								}
								imsi_xres_mapping.put(imsi, xres);
								uekey_nas_keys_map.put(imsi, new String[]{K_ASME, "", ""});
								KSI_ASME = "1";
								response = new StringBuilder();
								response.append(Constants.AUTHENTICATION_STEP_TWO).append(Constants.SEPARATOR).append(rand).append(Constants.SEPARATOR);
								response.append(autn);
								response.append(Constants.SEPARATOR).append(KSI_ASME);
								sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp, IpProtocol.UDP, dstPort, srcPort, response.toString());
								response = null;
							}else{
								System.out.println("ERROR:: STEP ONE AUTHENTICATION failure with imsi="+imsi+" and msisdn="+ue_nw_capability+" temp="+payload);
								sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, Constants.AUTHENTICATION_FAILURE);
								System.exit(1);
							}
							if(Constants.DEBUG)
								d2 = new Date();
							break;
						case Constants.AUTHENTICATION_STEP_THREE:
							if(Constants.DEBUG){
								System.out.println("Inside case AUTHENTICATION_STEP_THREE");
								step = 2;
								d1 = d2 = null;
								d1 = new Date();
							}
							imsi = tmpArray[1];
							res = tmpArray[2];	// RES from UE
							if(Constants.DEBUG){
								System.out.println("imsi="+imsi + " res="+res);
							}
							if(imsi_xres_mapping.containsKey(imsi)){
								if(imsi_xres_mapping.get(imsi).equals(res)){ // UE Authentication (RES == XRES)
									imsi_xres_mapping.remove(imsi);
									//UE is authenticated
									KSI_ASME = "1";
									int replayed_nw_capability = Utils.randInt(0, 10);		// Replayed UE Network Capability decided by MME
									int NAS_integrity_algo_id = Constants.CIPHER_ALGO_MAP.get("HmacSHA1");
									int NAS_cipher_algo_id = Constants.CIPHER_ALGO_MAP.get("AES");
									if(uekey_nas_keys_map.containsKey(imsi)){
										K_ASME = uekey_nas_keys_map.get(imsi)[0];
									}
									else{
										System.out.println("AUTHENTICATION_STEP_THREE: imsi not found");
										System.exit(1);
									}
									K_ASME = uekey_nas_keys_map.get(imsi)[0];

									String NAS_keys[] = KDF_NAS(Integer.parseInt(K_ASME), NAS_integrity_algo_id, NAS_cipher_algo_id);		// NAS_keys[0]: Integrity key K_NAS_int, NAS_keys[1]: Encryption key K_NAS_enc

									if(Constants.DEBUG){
										System.out.println("AUTHENTICATION_STEP_THREE: INT_KEY= " + NAS_keys[0] + " ENC_KEY= " + NAS_keys[1]);
									}
									uekey_nas_keys_map.put(imsi, new String[]{K_ASME, NAS_keys[0], NAS_keys[1]});
									response = new StringBuilder();
									response.append(Constants.NAS_STEP_ONE).append(Constants.SEPARATOR).append(KSI_ASME).append(Constants.SEPARATOR).append(replayed_nw_capability).append(Constants.SEPARATOR).append(NAS_cipher_algo_id).append(Constants.SEPARATOR).append(NAS_integrity_algo_id);

									NAS_MAC = Utils.hmacDigest(response.toString(), NAS_keys[0] + "");	// Generate Message Authentication Code using the hash function
									if(Constants.DEBUG){
										System.out.println("AUTHENTICATION_STEP_THREE: Generated NAS MAC= " + NAS_MAC);
									}
									response.append(Constants.SEPARATOR).append(NAS_MAC);
									sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
									response = null;
								}else{
									System.out.println(imsi_xres_mapping.get(imsi).equals(res) +" ### "+ imsi_xres_mapping.get(imsi) +" ue_res= "+ res+" ****imsi "+ imsi);
									sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, Constants.AUTHENTICATION_FAILURE);
									System.exit(1);
								}
							}else{
								System.out.println("AUTHENTICATION_STEP_THREE failure");
								sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, Constants.AUTHENTICATION_FAILURE);
								System.exit(1);
							}
							if(Constants.DEBUG)
								d2 = new Date();
							break;
						case Constants.SEND_APN:
							if(Constants.DEBUG){
								System.out.println("Inside case SEND_APN");
								step = 3;
								d1 = d2 = null;
								d1 = new Date();
							}
							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							//tmpArray[1] => ue apn and tmpArray[2] => ue key
							if(Constants.DEBUG){
								System.out.println("received apn="+tmpArray[1]);
							}
							// storing src port of udp packet to identify the specific UE, when MME wants to initiate connection with this UE.
							uekey_udp_src_port_map.put(tmpArray[2], srcPort);

							DatapathId pgw_dpId = hss.getPGateway(tmpArray[1]);
							sgw_dpId = DatapathId.of(Constants.SGW_DPID);

							String ip_sgw = sgw.contactPGW(switch_mapping.get(sgw_dpId),switch_mapping.get(pgw_dpId), sgw_dpId, pgw_dpId, tmpArray[1]); //tmpArray[1] => apn of UE
							response = new StringBuilder();
							response.append(Constants.SEND_IP_SGW_TEID).append(Constants.SEPARATOR).append(ip_sgw);

							if(Constants.DO_ENCRYPTION){
								Utils.aesEncrypt(response.toString(), Constants.SAMPLE_ENC_KEY);
								Utils.hmacDigest(response.toString(), Constants.SAMPLE_ENC_KEY);
							}

							tmpArray2 = ip_sgw.split(Constants.SEPARATOR);
							int sgw_teid = Integer.parseInt(tmpArray2[1]);

							//install uplink rule on default switch
							if(Constants.DEBUG){
								System.out.println("DEFAULT SWITCH installing uplink rule on default switch dpid = "+defaultSwitch.getLong()+" inport="+uePort+" and SRC IP = "+tmpArray2[0]+
										" outPort = "+Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + sgw_dpId.getLong())+" and out SRC IP = "+Constants.RAN_IP+" out teid= "+sgw_teid+" of UE key = "+tmpArray[2]);
							}

							installFlowRuleWithIP(defaultSwitch, uePort, Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + sgw_dpId.getLong()), sgw_teid, tmpArray2[0], Constants.RAN_IP, Constants.SGW_IP_UPLINK, Constants.SINK_MAC);
							uekey_ueip_map.put(tmpArray[2], tmpArray2[0]); // key: tmpArray[2] => UE Key, Value: tmpArray2[0] => UE IP 

							//MAP key = UE KEY,  MAP value = SGW_DPID + SEPARATOR + SGW_TEID
							uekey_sgw_teid_map.put(tmpArray[2], sgw_dpId.toString() + Constants.SEPARATOR + tmpArray2[1]);
							sgw_teid_uekey_map.put(tmpArray2[1], tmpArray[2]);

							sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.SEND_UE_TEID:
							if(Constants.DEBUG){
								System.out.println("Inside case SEND_UE_TEID");
								step = 4;
								d1 = d2 = null;
								d1 = new Date();
							}
							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							//tmpArray[1] => ue_teId and tmpArray[2] => ue key
							if(Constants.DEBUG){
								System.out.println("received teid="+tmpArray[1]);
							}
							//MAP key = UE KEY,  MAP value = SGW_DPID + SEPARATOR + SGW_TEID
							String tmp = uekey_sgw_teid_map.get(tmpArray[2]); // tmpArray[2] => ue key
							tmpArray2 = tmp.split(Constants.SEPARATOR);

							sgw.modifyBearerRequest(switch_mapping.get(DatapathId.of(tmpArray2[0])), DatapathId.of(tmpArray2[0]), Integer.parseInt(tmpArray2[1]), Integer.parseInt(tmpArray[1]), tmpArray[2]);

							String ue_ip = uekey_ueip_map.get(tmpArray[2]); // tmpArray[2] => ue key
							uekey_ueip_map.remove(tmpArray[2]);		// key is UE Key and value is UE IP

							if(Constants.DEBUG){
								//install downlink rule on default switch
								System.out.println("DEFAULT SWITCH installing downlink rule on default switch dpid = "+defaultSwitch.getLong()+" inport="+Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + DatapathId.of(tmpArray2[0]).getLong())+
										" in teid = " + Integer.parseInt(tmpArray[1]) +
										" outPort = " + uePort + " out teid= " + Integer.parseInt(tmpArray[1]) + " of UE key = "+tmpArray[2]);
							}

							installFlowRule(defaultSwitch, Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + DatapathId.of(tmpArray2[0]).getLong()), Integer.parseInt(tmpArray[1]), uePort, Integer.parseInt(tmpArray[1]), Constants.SINK_IP, ue_ip, Constants.UE_MAC);

							response = new StringBuilder();
							uekey_guti_map.put(tmpArray[2], (Integer.parseInt(tmpArray[2])+1000)+"");
							ue_state.put(tmpArray[2], true);
							response.append(Constants.ATTACH_ACCEPT).append(Constants.SEPARATOR).append(Integer.parseInt(tmpArray[2])+1000);	// Sending GUTI

							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.DETACH_REQUEST:
							if(Constants.DEBUG){
								System.out.println("Inside case DETACH_REQUEST");
								step = 5;
								d1 = d2 = null;
								d1 = new Date();
							}
							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							//tmpArray[1] => UE IP, tmpArray[2] => UE TEID, tmpArray[3] => SGW TEID, tmpArray[4] => UE_KEY
							if(Constants.DEBUG){
								System.out.println("RECEIVED DETACH REQUEST from UE with ip=" + tmpArray[1] + " TEID=" + tmpArray[2] + " corresponding SGW TEID=" + tmpArray[3] + " UE_KEY=" + tmpArray[4]);
							}
							//removing the port mapping between UE key and its source port (UDP) used for control traffic
							uekey_udp_src_port_map.remove(tmpArray[4]); // tmpArray[4] => UE KEY

							DatapathId sgw_dpid = DatapathId.of(Constants.SGW_ID), pgw_dpid = DatapathId.of(Constants.PGW_ID);

							uekey_sgw_teid_map.remove(tmpArray[4]);// newly added.. because can't remove in SEND_UE_TEID step.. due to re-establishment of tunnel

							sgw_teid_uekey_map.remove(tmpArray[3]); // key is sgw teid and value is ue key

							//delete uplink rule
							deleteFlowRuleWithIP(defaultSwitch, uePort, tmpArray[1]); // tmpArray[1] => UE IP
							if(Constants.DEBUG){
								System.out.println("DEFAULT SWITCH deleting uplink rule with for UE with IP="+tmpArray[1]);
							}
							int DEFAULT_S_SGW_PORT = Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + sgw_dpid.getLong()); //dpids[0] ==> SGW DPID
							//delete downlink rule
							deleteFlowRuleWithTEID(defaultSwitch, DEFAULT_S_SGW_PORT, Integer.parseInt(tmpArray[2]), Constants.SINK_IP); //tmpArray[2] ==> UE VLAN ID
							if(Constants.DEBUG){
								System.out.println("DEFAULT SWITCH deleting downlink rule with for UE with IP="+tmpArray[1]+" and UE TEID="+tmpArray[2]);
							}

							// dpids[0] ==> SGW DPID   & dpids[1]==> PGW DPID 
							boolean status = sgw.detachUEFromSGW(switch_mapping.get(sgw_dpid), switch_mapping.get(pgw_dpid), sgw_dpid, pgw_dpid, Integer.parseInt(tmpArray[3]), tmpArray[1]);
							response = new StringBuilder();
							if(status){
								response.append(Constants.DETACH_ACCEPT).append(Constants.SEPARATOR).append("");
								if(Constants.DO_ENCRYPTION){
									Utils.aesEncrypt(response.toString(), Constants.SAMPLE_ENC_KEY);
									Utils.hmacDigest(response.toString(), Constants.SAMPLE_ENC_KEY);
								}
								sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							}else{
								response.append(Constants.DETACH_FAILURE).append(Constants.SEPARATOR).append("");
								if(Constants.DO_ENCRYPTION){
									Utils.aesEncrypt(response.toString(), Constants.SAMPLE_ENC_KEY);
									Utils.hmacDigest(response.toString(), Constants.SAMPLE_ENC_KEY);
								}
								sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
								System.out.println("ERROR: DETACH_FAILURE");
								System.exit(1);
							}
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.REQUEST_STARTING_IP:
							if(Constants.DEBUG){
								System.out.println("Inside case REQUEST_STARTING_IP");
								System.out.println("Request Starting IP address from PGW via SGW");
								step = 6;
								d1 = d2 = null;
								d1 = new Date();
							}
							String ip = sgw.getStartingIPAddress();
							if(Constants.DEBUG){
								System.out.println("Starting IP="+ip);
							}
							response = new StringBuilder();
							response.append(Constants.SEND_STARTING_IP).append(Constants.SEPARATOR).append(ip);
							sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.UE_CONTEXT_RELEASE_REQUEST:
							if(Constants.DEBUG){
								System.out.println("Inside case UE_CONTEXT_RELEASE_REQUEST");
								//tmpArray[1]==> UE IP  &  tmpArray[2] ==> UE TEID  &  tmpArray[3] ==> SGW TEID &  tmpArray[4] ==> UE_KEY
								System.out.println("RECEIVED UE CONTEXT RELEASE REQUEST from UE with ip=" + tmpArray[1] + " TEID=" + tmpArray[2] + " corresponding SGW TEID=" + tmpArray[3] + " UE KEY=" + tmpArray[4]);
								step = 7;
								d1 = d2 = null;
								d1 = new Date();
							}
							sgw_dpid = DatapathId.of(Constants.SGW_ID);

							//delete uplink rule
							deleteFlowRuleWithIP(defaultSwitch, uePort, tmpArray[1]); //tmpArray[1] ==> UE IP
							if(Constants.DEBUG){
								System.out.println("DEFAULT SWITCH deleting uplink rule with for UE with IP="+tmpArray[1]);
							}
							DEFAULT_S_SGW_PORT = Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + sgw_dpid.getLong()); //dpids[0] ==> SGW DPID
							//delete downlink rule
							deleteFlowRuleWithTEID(defaultSwitch, DEFAULT_S_SGW_PORT, Integer.parseInt(tmpArray[2]), Constants.SINK_IP); //tmpArray[2] ==> UE VLAN ID
							if(Constants.DEBUG){
								System.out.println("DEFAULT SWITCH deleting downlink rule with for UE with IP="+tmpArray[1]+" and UE TEID="+tmpArray[2]);
							}

							// dpids[0] ==> SGW DPID   & dpids[1]==> PGW DPID 
							sgw.releaseAccessBearersRequest(switch_mapping.get(sgw_dpid), sgw_dpid, Integer.parseInt(tmpArray[3]), tmpArray[1]);

							response = new StringBuilder();
							ue_state.put(tmpArray[4], false);
							response.append(Constants.UE_CONTEXT_RELEASE_COMMAND).append(Constants.SEPARATOR).append("");
							sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.UE_CONTEXT_RELEASE_COMPLETE:
							if(Constants.DEBUG){
								System.out.println("Inside case UE_CONTEXT_RELEASE_COMPLETE");
								System.out.println("RECEIVED UE_CONTEXT_RELEASE_COMPLETE from UE with UE Key=" + tmpArray[1] + " UE IP=" + tmpArray[2] +" Network_Service_Request_Boolean = "+tmpArray[3]+" sink UDP server port = "+tmpArray[4]);
							}	
							// tmpArray[1] => UE_Key, tmpArray[2] => UE_IP, tmpArray[3] => Network_Service_Request_Boolean, tmpArray[4] => Sink_UDP_Server_Port
							// tmpArray[3] is a boolean flag to perform network initiated service request
							if(tmpArray[3].equals("1")){		// Inform Sink to initiate network service request
								int serverPort = Integer.parseInt(tmpArray[4]);

								srcPort = TransportPort.of(serverPort);
								dstPort = TransportPort.of(serverPort);
								srcIp = IPv4Address.of(tmpArray[2]);
								dstIp = IPv4Address.of(Constants.SINK_IP);
								MacAddress srcMac = MacAddress.of(Constants.UE_MAC), dstMac =  MacAddress.of(Constants.SINK_MAC);
								sw = switchService.getSwitch(DatapathId.of(Constants.PGW_ID));
								response = new StringBuilder();
								response.append(Constants.INITIATE_NETWORK_SERVICE_REQUEST).append(Constants.SEPARATOR).append(tmpArray[1]);
								if(Constants.DEBUG){
									System.out.println("Sending Network Service Request to Sink for UE Key = " + tmpArray[1] + " UE IP = " + tmpArray[2]);
								}

								sendPacket(sw, OFPort.of(Constants.PGW_SINK_PORT), srcMac, dstMac, srcIp, dstIp,  IpProtocol.UDP, srcPort, dstPort, response.toString());
							}

							step = 8;
							d1 = d2 = null;
							d1 = new Date();

							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.UE_SERVICE_REQUEST:
							if(Constants.DEBUG){
								System.out.println("Inside case UE_SERVICE_REQUEST");
								step = 9;
								d1 = d2 = null;
								d1 = new Date();
							}
							//tmpArray[1]==> UE_KEY  &  tmpArray[2] ==> KSI_ASME & tmpArray[3] ==> UE_IP
							if(Constants.DEBUG){
								System.out.println("RECEIVED UE_SERVICE_REQUEST from UE with key=" + tmpArray[1] + " KSI_ASME=" + tmpArray[2]);
							}
							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}
							sgw_dpId = DatapathId.of(Constants.SGW_DPID);
							ue_ip = tmpArray[3];
							String sgw_dpid_sgw_teid = uekey_sgw_teid_map.get(tmpArray[1]); // MAP key = UE KEY,  MAP value = SGW_DPID + SEPARATOR + SGW_TEID
							tmpArray2 = sgw_dpid_sgw_teid.split(Constants.SEPARATOR);
							sgw_teid = Integer.parseInt(tmpArray2[1]);

							//install uplink rule on default switch
							if(Constants.DEBUG){
								System.out.println("DEFAULT SWITCH installing uplink rule on default switch dpid = "+defaultSwitch.getLong()+" inport="+uePort+" and SRC IP = "+ue_ip+
										" outPort = "+Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + sgw_dpId.getLong())+" and out SRC IP = "+Constants.RAN_IP+" out teid= "+sgw_teid+" of UE key = "+tmpArray[1]);
							}
							installFlowRuleWithIP(defaultSwitch, uePort, Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + sgw_dpId.getLong()), sgw_teid, ue_ip, Constants.RAN_IP, Constants.SGW_IP_UPLINK, Constants.SINK_MAC);

							response = new StringBuilder();
							response.append(Constants.INITIAL_CONTEXT_SETUP_REQUEST).append(Constants.SEPARATOR).append(sgw_teid);

							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.INITIAL_CONTEXT_SETUP_RESPONSE:
							if(Constants.DEBUG){
								System.out.println("Inside case INITIAL_CONTEXT_SETUP_RESPONSE");
								step = 10;
								d1 = d2 = null;
								d1 = new Date();
							}
							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							//tmpArray[1] => ue_teId, tmpArray[2] => ue key & tmpArray[3] => ue ip
							if(Constants.DEBUG){
								System.out.println("received teid="+tmpArray[1]);
							}
							//MAP key = UE KEY,  MAP value = SGW_DPID + SEPARATOR + SGW_TEID
							while(uekey_sgw_teid_map.get(tmpArray[2]) == null);

							tmp = uekey_sgw_teid_map.get(tmpArray[2]); // tmpArray[2] => ue key
							tmpArray2 = tmp.split(Constants.SEPARATOR);

							sgw.modifyBearerRequest(switch_mapping.get(DatapathId.of(tmpArray2[0])), DatapathId.of(tmpArray2[0]), Integer.parseInt(tmpArray2[1]), Integer.parseInt(tmpArray[1]), tmpArray[2]);

							ue_ip = tmpArray[3];
							if(Constants.DEBUG){
								//install downlink rule on default switch
								System.out.println("DEFAULT SWITCH installing downlink rule on default switch dpid = "+defaultSwitch.getLong()+" inport="+Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + DatapathId.of(tmpArray2[0]).getLong())+
										" in teid = " + Integer.parseInt(tmpArray[1]) +
										" outPort = " + uePort + " out teid= " + Integer.parseInt(tmpArray[1]) + " of UE key = "+tmpArray[2]);
							}
							installFlowRule( defaultSwitch, Constants.ENODEB_SGW_PORT_MAP.get(Constants.DEFAULT_SWITCH_ID + Constants.SEPARATOR + DatapathId.of(tmpArray2[0]).getLong()), Integer.parseInt(tmpArray[1]), uePort, Integer.parseInt(tmpArray[1]), Constants.SINK_IP, ue_ip, Constants.UE_MAC);

							response = new StringBuilder();
							uekey_guti_map.put(tmpArray[2], (Integer.parseInt(tmpArray[2])+1000)+"");
							ue_state.put(tmpArray[2], true);
							response.append(Constants.ATTACH_ACCEPT).append(Constants.SEPARATOR).append(tmpArray[2]);

							if(Constants.DO_ENCRYPTION){
								decArray = receiveDecryptedArray(tmpArray);
							}

							sendPacket(sw, inPort, destMac, sourceMac, dstIp, srcIp,  IpProtocol.UDP, dstPort, srcPort, response.toString());
							response = null;
							if(Constants.DEBUG)
								d2 = new Date();
							break;

						case Constants.SINK_SERVICE_REQUEST:
							if(tmpArray[0].equals(Constants.SINK_SERVICE_REQUEST)){
								downlinkDataNotification(tmpArray[1], vlan); // payloadArray[1]: UE Key
							}else{
								System.out.println("** ERROR: Unknown message code received from sink, received: "+payload+" expected: "+Constants.SEPARATOR);
								System.exit(1);
							}
							break;
							
						default:
							if(Constants.DEBUG){
								System.out.println("Inside case default");
								step = 11;
								d1 = d2 = null;
								d1 = new Date();
							}

							if(tmpArray.length == 3){
								if(Constants.DEBUG){
									System.out.println("NAS STEP TWO: arr length = "+ arr.length +" Received encrypted text=" + tmpArray[0] + " NAS-MAC= " + tmpArray[1] + " IMSI=" + tmpArray[2]);
									System.out.println("Encrypted length=" + tmpArray[0].length() + " NAS-MAC length=" + tmpArray[1].length() + " IMSI length=" + tmpArray[2].length());
								}
								if(!Constants.CHECK_INTEGRITY){
									d2 = new Date();
									break;
								}

								NAS_Keys = uekey_nas_keys_map.get(tmpArray[2]);
								NAS_MAC = Utils.hmacDigest(tmpArray[0], NAS_Keys[1]);
								if(Constants.DEBUG){
									System.out.println("NAS_STEP_TWO: Generated NAS MAC= " + NAS_MAC);
								}
							}
							else{
								System.out.println("ERROR: NAS STEP TWO: unknown command. Array length = " + tmpArray.length + " Received: " + payload);
								System.exit(1);
							}
							d2 = new Date();
						}
						if(Constants.DEBUG)
							timeDiff(d1, d2, step);
					}
				}
			}else if(ipPkt.getProtocol().equals(IpProtocol.IPIP)){ //IP within IP
				if(Constants.DEBUG){
					System.out.println("srcIp="+srcIp.toString()+" dstIp="+dstIp.toString());
					System.out.println("proto="+ipPkt.getProtocol()+" jjj="+EthType.IPv4+" kkk="+IpProtocol.IPX_IN_IP);
					System.out.println("proto="+ipPkt.getProtocol().toString());
				}
				Data dataPkt1 = (Data) ipPkt.getPayload();
				byte[] arr1 = dataPkt1.getData();
				try {
					IPv4 ipPkt1 = (IPv4) ipPkt.deserialize(arr1, 0, arr1.length);
					srcIp = ipPkt1.getSourceAddress();
					dstIp = ipPkt1.getDestinationAddress();

					UDP udpPkt1 = (UDP)ipPkt1.getPayload();
					srcPort = udpPkt1.getSourcePort();
					dstPort = udpPkt1.getDestinationPort();
					if(Constants.DEBUG){
						System.out.println("srcIp="+srcIp.toString()+" dstIp="+dstIp.toString());
					}
				} catch (PacketParsingException e) {
					e.printStackTrace();
				}

			}
		}
		return Command.CONTINUE;
	}

	long case_sum[] = new long[20];
	long case_cnt[] = new long[20];
	long MOD = 10;

	// Used for measuring execution time of procedures; uncomment it if needed for debugging purpose 
	private void timeDiff(Date d1, Date d2, int step){
		/*if(d1 == null || d2 == null){
			System.out.println("Step " + step + " ****** " + d1 + " ----------- " + d2);
			System.exit(1);
		}
		long diff = d2.getTime() - d1.getTime();
		case_cnt[step-1]++;
		case_sum[step-1] += diff;

		if (case_cnt[step-1] % MOD == 0){
			System.out.println(getStepName(step) + " Average over "+ MOD + " no of steps is " + (case_sum[step-1]*1.0/case_cnt[step-1]) +" ms");
			case_cnt[step-1] = case_sum[step-1] = 0;
			if(step == 5)
				System.out.println();
		}*/
	}

	protected boolean downlinkDataNotification(String uekey, VlanVid vlan){
		TransportPort srcPort = TransportPort.of(Constants.DEFAULT_CONTROL_TRAFFIC_UDP_PORT), dstPort = TransportPort.of(67);
		IPv4Address srcIp = IPv4Address.of(Constants.DSWITCH_IP_UPLINK), dstIp = IPv4Address.of(Constants.RAN_IP);
		MacAddress srcMac = MacAddress.of(Constants.DEFAULT_GW_MAC), dstMac =  MacAddress.of(Constants.UE_MAC);
		IOFSwitch sw = switchService.getSwitch(DatapathId.of(Constants.DEFAULT_SWITCH_ID));
		StringBuilder response = new StringBuilder();

		if(uekey_udp_src_port_map.containsKey(uekey)){
			dstPort = uekey_udp_src_port_map.get(uekey);
		}else{
			System.out.println("ERROR: UE UDP port not found for the UE key = " + uekey);
			System.exit(1);
		}

		response.append(Constants.PAGING_REQUEST).append(Constants.SEPARATOR).append(vlan.getVlan());
		if(Constants.DEBUG){
			System.out.println("Sending paging request to UE with UE Key = " + uekey);
		}
		sendPacket(sw, OFPort.of(Constants.DEFAULT_SWITCH_UE_PORT), srcMac, dstMac, srcIp, dstIp,  IpProtocol.UDP, srcPort, dstPort, response.toString());
		return true;
	}

	@SuppressWarnings("unused")
	private String getStepName(int step){
		switch(step){
		case 1: return "AUTHENTICATION_STEP_ONE: ";
		case 2: return "AUTHENTICATION_STEP_THREE: ";
		case 3: return "SEND_APN: ";
		case 4: return "SEND_UE_TEID: ";
		case 5: return "DETACH_REQUEST: ";
		case 6: return "REQUEST_STARTING_IP: ";
		case 7: return "UE_CONTEXT_RELEASE_REQUEST: ";
		case 8: return "UE_CONTEXT_RELEASE_COMPLETE";
		case 9: return "UE_SERVICE_REQUEST: ";
		case 10: return "TUNNEL_SETUP_ACCEPT: ";
		case 11: return "DEFAULT: ";
		case 12: return "HSS call: ";
		default: return "Invalid";
		}
	}

	private String[] receiveDecryptedArray(String tmpArray[]){
		//tmpArray[1] => Encrypted text, tmpArray[2] => HMAC, tmpArray[3] => IMSI
		Utils.hmacDigest(tmpArray[1], Constants.SAMPLE_ENC_KEY);
		String decText = "";
		Utils.aesEncrypt(tmpArray[1], Constants.SAMPLE_ENC_KEY);
		return decText.split(Constants.SEPARATOR);
	}

	private String[] KDF_NAS(int K_ASME, int NAS_integrity_algo_id, int NAS_cipher_algo_id){
		String NAS_keys[] = new String[2];	// NAS_keys[0]: Integrity key K_NAS_int, NAS_keys[1]: Encryption key K_NAS_enc
		long K_NAS_int = K_ASME * 2 + NAS_integrity_algo_id;
		long K_NAS_enc = K_ASME * 4 + NAS_cipher_algo_id;
		NAS_keys[0] = K_NAS_int + "";
		NAS_keys[1] = K_NAS_enc + "";

		if(NAS_keys[1].length() > Constants.ENC_KEY_LENGTH){
			NAS_keys[1].substring(0, Constants.ENC_KEY_LENGTH);
		}
		else if(NAS_keys[1].length() < Constants.ENC_KEY_LENGTH){
			NAS_keys[1] += Constants.SAMPLE_ENC_KEY.substring(0, Constants.ENC_KEY_LENGTH - NAS_keys[1].length());
		}
		return NAS_keys;
	}

	//delete uplink rule
	private void deleteFlowRuleWithIP(DatapathId dpId, int inPort, String ue_ip){
		if(sw == null){
			sw = switchService.getSwitch(dpId);
		}
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(ue_ip));

		fmb.setMatch(mb.build());
		sw.write(fmb.build());
	}

	//delete downlink rule
	private void deleteFlowRuleWithTEID(DatapathId dpId, int inPort, int ue_teid, String srcIP){
		if(sw == null){
			sw = switchService.getSwitch(dpId);
		}
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(srcIP))
		.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(VlanVid.ofVlan(ue_teid)));

		fmb.setMatch(mb.build());
		sw.write(fmb.build());
	}

	// uplink rule
	private void installFlowRuleWithIP(DatapathId dpId, int inPort, int outPort, int outTunnelId, String UE_IP, String srcIP, String dstIP, String dstMac){
		if(sw == null){
			sw = switchService.getSwitch(dpId);
		}
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw.getOFFactory().buildMatch();


		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
		mb.setExact(MatchField.IN_PORT, OFPort.of(inPort));
		mb.setExact(MatchField.IPV4_SRC, IPv4Address.of(UE_IP));

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan(outTunnelId)));

		if(dstIP != "")
			actions.add(sw.getOFFactory().actions().setNwDst(IPv4Address.of(dstIP)));

		actions.add(sw.getOFFactory().actions().setDlDst(MacAddress.of(dstMac)));
		actions.add(sw.getOFFactory().actions().output(OFPort.of(outPort), Integer.MAX_VALUE)); // FLOOD is a more selective/efficient version of ALL
		fmb.setActions(actions);

		fmb.setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(1)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch(mb.build());

		sw.write(fmb.build());
	}

	//downlink rule
	private void installFlowRule(DatapathId dpId, int inPort, int inTunnelId, int outPort, int outTunnelId, String srcIP, String dstIP, String dstMac){
		if(sw == null){
			sw = switchService.getSwitch(dpId);
		}
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(srcIP))
		.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(VlanVid.ofVlan(inTunnelId)));

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ZERO));//ofVlan(outTunnelId)
		actions.add(sw.getOFFactory().actions().setNwDst(IPv4Address.of(dstIP)));
		actions.add(sw.getOFFactory().actions().setDlDst(MacAddress.of(dstMac)));
		actions.add(sw.getOFFactory().actions().output(OFPort.of(outPort), Integer.MAX_VALUE)); // FLOOD is a more selective/efficient version of ALL
		fmb.setActions(actions);

		fmb.setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(1)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch(mb.build());

		sw.write(fmb.build());
	}

	public DatapathId selectS_GW(){
		DatapathId minLoadedSwitch = null;
		Long minBytes = 0l;
		boolean first_time = true;
		for (Map.Entry<DatapathId, Long> entry : switchStats.entrySet()) {
			if(first_time){
				minLoadedSwitch = entry.getKey();
				minBytes = entry.getValue();
				first_time = false;
			}else{
				if(entry.getValue() < minBytes){
					minBytes = entry.getValue();
					minLoadedSwitch = entry.getKey();
				}
			}
		}
		return minLoadedSwitch;
	}

	/*
	 * create and send packet to default switch on port in which it arrived.
	 */
	public boolean sendPacket(IOFSwitch sw, OFPort outPort, MacAddress srcMac, MacAddress dstMac, 
			IPv4Address srcIP, IPv4Address dstIP, IpProtocol proto, 
			TransportPort srcPort, TransportPort dstPort, String data){

		try{
			//sending packet in response
			OFPacketOut.Builder pktNew = sw.getOFFactory().buildPacketOut();
			pktNew.setBufferId(OFBufferId.NO_BUFFER);

			Ethernet ethNew = new Ethernet();
			ethNew.setSourceMACAddress(srcMac);
			ethNew.setDestinationMACAddress(dstMac);
			ethNew.setEtherType(EthType.IPv4);

			IPv4 ipNew = new IPv4();
			ipNew.setSourceAddress(srcIP);
			ipNew.setDestinationAddress(dstIP);

			ipNew.setProtocol(proto);
			ipNew.setTtl((byte) 64);

			UDP updNew = new UDP();
			updNew.setSourcePort(srcPort);
			updNew.setDestinationPort(dstPort);

			Data dataNew = new Data();
			dataNew.setData(data.getBytes());

			//putting it all together
			ethNew.setPayload(ipNew.setPayload(updNew.setPayload(dataNew)));

			// set in-port to OFPP_NONE
			pktNew.setInPort(OFPort.ZERO);
			List<OFAction> actions = new ArrayList<OFAction>();
			actions.add(sw.getOFFactory().actions().output(outPort, 0xffFFffFF));

			pktNew.setActions(actions);
			pktNew.setData(ethNew.serialize());

			sw.write(pktNew.build());
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}

	protected Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, FloodlightContext cntx) {
		// The packet in match will only contain the port number.
		// We need to add in specifics for the hosts we're routing between.
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();

		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.IN_PORT, inPort)
		.setExact(MatchField.ETH_SRC, srcMac)
		.setExact(MatchField.ETH_DST, dstMac);

		if (!vlan.equals(VlanVid.ZERO)) {
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
		}

		return mb.build();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	// IFloodlightModule

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// We don't provide any services, return null
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls() {
		// We don't provide any services, return null
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>>
	getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		switchService.addOFSwitchListener(this);
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		switch_mapping.put(switchId, switchService.getSwitch(switchId));

		// Install ARP rule
		IOFSwitch sw = switchService.getSwitch(switchId);

		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.ARP);

		OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
		actionBuilder.setPort(OFPort.NORMAL);
		fmb.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

		fmb.setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(1)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch(mb.build());

		sw.write(fmb.build());
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		System.out.println("SWITCH REMOVED: "+switchId);
		switchStats.remove(switchId);
		switch_mapping.remove(switchId);

	}

	@Override
	public void switchActivated(DatapathId switchId) {
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
	}

	@Override
	public void switchChanged(DatapathId switchId) {
	}
}
