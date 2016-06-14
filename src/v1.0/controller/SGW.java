/*************************************************************
 * This class contains the control plane code of SGW.        *
 * It is used to install/delete uplink and downlink          *
 * flow rules on  dataplane switch of SGW (SGW-D). It also   *
 * contacts PGW-C on behalf of MME for allocating IP address *
 * and for install/deleting rules on PGW-D                   *
 *************************************************************/
package net.floodlightcontroller.sdnepc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.module.ModuleLoaderResource;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SGW implements IFloodlightModule, IOFMessageListener {
	private IFloodlightProviderService floodlightProvider;
	protected static Logger log = LoggerFactory.getLogger(SGW.class);
	PGW pg = new PGW();
	ModuleLoaderResource module_resource = new ModuleLoaderResource();
	int tunnelId = 1;
	Map<Integer, Integer> SGW_PGW_TEID_MAP = new HashMap<Integer, Integer>();
	Queue<Integer> reuseable_teids = new LinkedList<Integer>();

	public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
		this.floodlightProvider = floodlightProvider;
	}

	/*
	 * This method is invoked by MME to establish the data tunnel between SGW-D and PGW-D
	 * by installing flow rules specific to the UE on SGW-D (for Uplink Data traffic)
	 * Here PGW_C also allocated an IP address for the UE. This IP address will be passed on to UE via SGW-C,
	 * MME and default switch in sequence.
	 */
	String contactPGW(IOFSwitch sgw, IOFSwitch pgw, DatapathId sgw_dpId, DatapathId pgw_dpId, String apn){
		String []tmpArray = null;
		int pgw_teid, sgw_teid;
		//code for reusing the used tunnel ids. Because we only have 4000 available
		if(reuseable_teids.isEmpty()){
			if(tunnelId > 4000){
				tunnelId = 1;
			}
			sgw_teid = tunnelId;
			tunnelId++;
		}else{
			sgw_teid = reuseable_teids.remove();
			if(Constants.DEBUG){
				System.out.println("SGW reusing TEID = "+sgw_teid);
			}
		}

		if(Constants.DEBUG){
			System.out.println("PGATEWAY="+pgw_dpId.getLong());
			System.out.println("S GW TUNNEL ID of UE "+apn+" is "+sgw_teid);
		}
		// contacting pgw to allocate an ip address for UE. PGW_C also provides its tunnel endpoint identifier to SGW_C.
		String ip_pgw = pg.allocateIPForUE(pgw, sgw_teid, sgw_dpId, pgw_dpId,  apn);
		//ip_pgw format is UE_IP_ADDRESS + SEPARATOR + PGW tunnel ID for this UE

		tmpArray = ip_pgw.split(Constants.SEPARATOR); // tmpArray[0] => IP for UE. tmpArray[1] => PGW tunnel ID for this UE

		//uplink rule (SGW to PGW)
		if(Constants.DEBUG){
			System.out.println("SGW installing uplink rule on S-GW dpid = "+sgw_dpId.getLong()+" inport="+Constants.SGW_PORT_MAP.get(sgw_dpId)[0]+" in teid = "+sgw_teid+
					" outPort = "+Constants.SGW_PORT_MAP.get(sgw_dpId)[1]+" out teid= "+tmpArray[1]+" of UE apn = "+apn);
		}
		pgw_teid = Integer.parseInt(tmpArray[1]);
		SGW_PGW_TEID_MAP.put(sgw_teid, pgw_teid);

		//uplink rule (SGW to PGW)
		installFlowRule(sgw, sgw_dpId, Constants.SGW_PORT_MAP.get(sgw_dpId)[0], sgw_teid, Constants.SGW_PORT_MAP.get(sgw_dpId)[1], pgw_teid, tmpArray[0], Constants.PGW_IP_UPLINK);

		return tmpArray[0] + Constants.SEPARATOR + sgw_teid;
	}

	/*
	 * This method installs the downlink flow rule between SGW-D and default switch after knowing the UE generated tunnel for default switch
	 */
	public void modifyBearerRequest(IOFSwitch sgw, DatapathId sgw_dpId, int sgw_teId, int ue_teId, String key){
		if(Constants.DEBUG){
			System.out.println("teid="+ue_teId);
			//downlink rule (SGW to ENodeB)
			System.out.println("SGW installing downlink rule on S-GW dpid = "+sgw_dpId.getLong()+" inport="+Constants.SGW_PORT_MAP.get(sgw_dpId)[1]+" in teid = "+sgw_teId+
					" outPort = "+Constants.SGW_PORT_MAP.get(sgw_dpId)[0]+" out teid= "+ue_teId+" of UE key = "+key);
		}
		installFlowRule(sgw, sgw_dpId, Constants.SGW_PORT_MAP.get(sgw_dpId)[1], sgw_teId, Constants.SGW_PORT_MAP.get(sgw_dpId)[0], ue_teId, Constants.SINK_IP, Constants.DSWITCH_IP_DOWNLINK);

	}

	/*
	 * This is a utility method requiring to know the starting IP address PGW-C will be using.
	 * This method is needed so that we can preallocate the IP addresses that will be used in this 
	 * experiment onto the interface of UE machine. This is to avoid wasting time and CPU on this later
	 * that is, during the main experiment.
	 */
	public String getStartingIPAddress(){
		return pg.returnStartingIPAddress();
	}

	/*
	 * This method detaches the tunnel between SGW-D and default switch, by deleting the uplink and downlink rules on SGW-D
	 */
	public boolean detachUEFromSGW(IOFSwitch sgw, IOFSwitch pgw, DatapathId sgw_dpId, DatapathId pgw_dpId, int sgw_teid, String ue_ip){
		int pgw_teid;
		pgw_teid = SGW_PGW_TEID_MAP.get(sgw_teid);
		SGW_PGW_TEID_MAP.remove(sgw_teid);

		//delete uplink rule
		deleteFlowRuleWithTEID(sgw, Constants.SGW_PORT_MAP.get(sgw_dpId)[0], sgw_teid, ue_ip);

		if(Constants.DEBUG){
			System.out.println("SGW deleting uplink rule with PGW TEID="+pgw_teid+" for UE with IP="+ue_ip);
		}

		//delete downlink rule
		deleteFlowRuleWithTEID(sgw, Constants.SGW_PORT_MAP.get(sgw_dpId)[1], sgw_teid, Constants.SINK_IP);
		if(Constants.DEBUG){
			System.out.println("SGW deleting downlink rule with SGW TEID="+sgw_teid+" for UE with IP="+ue_ip);
		}

		return pg.detachUEFromPGW(pgw, sgw_dpId, pgw_dpId, pgw_teid, ue_ip);
	}

	/*
	 * This method is used to simulate UE idle timeout. When UE remains idle for specified amount of time, we delete the downlink rule between SDG-D
	 * and default switch. 
	 */
	public void releaseAccessBearersRequest(IOFSwitch sgw, DatapathId sgw_dpId, int sgw_teid, String ue_ip){
		//delete downlink rule
		deleteFlowRuleWithTEID(sgw, Constants.SGW_PORT_MAP.get(sgw_dpId)[1], sgw_teid, Constants.SINK_IP);
		if(Constants.DEBUG){
			System.out.println("SGW deleting downlink rule with SGW TEID="+sgw_teid+" for UE with IP="+ue_ip);
		}
	}

	/*
	 * This method helps to delete the flow rule on SGW-D using TEID as the matching criteria
	 */
	private void deleteFlowRuleWithTEID(IOFSwitch sw, int inPort, int ue_teid, String srcIP){
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(srcIP))
		.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(VlanVid.ofVlan(ue_teid)));

		fmb.setMatch(mb.build());

		//delete the rule from the switch
		sw.write(fmb.build());
	}

	/*
	 * This method helps to install the flow rule on SGW-D using TEID as the matching criteria
	 */
	private void installFlowRule(IOFSwitch sw, DatapathId dpId, int inPort, int inTunnelId, int outPort, int outTunnelId, String srcIP, String dstIP){
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(srcIP))
		.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(VlanVid.ofVlan(inTunnelId)));

		List<OFAction> actions = new ArrayList<OFAction>();

		actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan(outTunnelId)));
		if(dstIP != "")
			actions.add(sw.getOFFactory().actions().setNwDst(IPv4Address.of(dstIP)));
		actions.add(sw.getOFFactory().actions().output(OFPort.of(outPort), Integer.MAX_VALUE)); // FLOOD is a more selective/efficient version of ALL

		fmb.setActions(actions);

		fmb.setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(1)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch(mb.build());

		sw.write(fmb.build());
	}

	@Override
	public String getName() {
		return SGW.class.getPackage().getName();
	}

	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			if(DatapathId.of(Constants.SGW_DPID).equals(sw.getId())) {
				//System.out.println("SGW: Received PACKET_IN request");
				//+return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
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

	@SuppressWarnings("unused")
	private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));

		/* Read packet header attributes into Match */
		Match m = createMatchFromPacket(sw, inPort, cntx);
		MacAddress sourceMac = m.get(MatchField.ETH_SRC);
		MacAddress destMac = m.get(MatchField.ETH_DST);
		VlanVid vlan = m.get(MatchField.VLAN_VID) == null ? VlanVid.ZERO : m.get(MatchField.VLAN_VID).getVlanVid();
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

			System.out.println("Src IP = "+ srcIp+" Dst Ip = "+ dstIp);

			//mme.downlinkDataNotification(srcIp.toString());
		}
		return Command.CONTINUE;
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
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

}
