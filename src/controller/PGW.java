/*************************************************************
 * This class contains the control plane code of PGW.        *
 * It is used to install/delete uplink and downlink          *
 * flow rules on  dataplane switch of PGW (PGW-D). It also   *
 * allocates IP address for the UE.                          *
 *************************************************************/
package net.floodlightcontroller.sdnepc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.VlanVid;

public class PGW implements IFloodlightModule {

	volatile String ip = Constants.STARTING_UE_IP;
	int tunnelId = 4000;
	int numIps = 0;
	int pgw_sink_port;
	protected static IOFSwitchService switchService;
	Queue<Integer> reuseable_teids = new LinkedList<Integer>();
	Queue<String> reuseable_ips = new LinkedList<String>();

	public PGW() {
		// port of pgw which is connected to sink
		pgw_sink_port = Constants.PGW_SINK_PORT; 
	}

	public String[] getPartsOfIpAddress(String ipAddress) {
		String[] elements = ipAddress.split("\\.");
		return elements;
	}
	
	public String returnStartingIPAddress(){
		return ip;
	}
	
	public static int randInt(int min, int max) {
	    // Usually this can be a field rather than a method variable
	    Random rand = new Random();
	    // nextInt is normally exclusive of the top value,
	    // so add 1 to make it inclusive
	    int randomNum = rand.nextInt((max - min) + 1) + min;

	    return randomNum;
	}

	/*
	 * This method generates the next IP address in sequence given the current IP address. 
	 */
	public String getNextIPAddress(String ipAddress) throws Exception {
		String[] elements = getPartsOfIpAddress(ipAddress);
		if (elements != null && elements.length == 4) {
			Integer part1 = Integer.parseInt(elements[0]);
			Integer part2 = Integer.parseInt(elements[1]);
			Integer part3 = Integer.parseInt(elements[2]);
			Integer part4 = Integer.parseInt(elements[3]);
			if (part4 < 255) {
				String ip = part1 + "." + part2 + "." + part3 + "." + (++part4);
				return ip;
			} else if (part4 == 255) {
				if (part3 < 255) {
					String ip = part1 + "." + part2 + "." + (++part3) + "."
							+ (0);
					return ip;
				} else if (part3 == 255) {
					if (part2 < 255) {
						String ip = part1 + "." + (++part2) + "." + (0) + "."
								+ (0);
						return ip;
					} else if (part2 == 255) {
						if (part1 < 255) {
							String ip = (++part1) + "." + (0) + "." + (0) + "."
									+ (0);
							return ip;
						} else if (part1 == 255) {
							throw new Exception("IP Range Exceeded -> "+ipAddress);
						}
					}
				}
			}
		}

		return null;
	}

	/*
	 * This method generates and allocates the UE IP address and PGW Tunnel ID (for this specific UE session)
	 */
	String allocateIPForUE(IOFSwitch pgw, int sgw_tunnelId, DatapathId sgw_dpId, DatapathId pgw_dpId, String apn){
		int pgw_teid;
		// resuing old tunnel ids
		if(reuseable_teids.isEmpty()){
			if(tunnelId < 0 || tunnelId > 4000){
				tunnelId = 4000;
			}
			pgw_teid = tunnelId;
			tunnelId--;
		}else{
			pgw_teid = reuseable_teids.remove();
			if(Constants.DEBUG){
				System.out.println("PGW reusing TEID = "+pgw_teid);
			}
		}
		try {
			if(reuseable_ips.isEmpty()){
				numIps++;
				this.ip = getNextIPAddress(ip);
			}else{
				this.ip = reuseable_ips.remove();
				if(Constants.DEBUG){
					System.out.println("PGW reusing IP = "+ip+" numips="+numIps);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(Constants.DEBUG){
			System.out.println("P GW TUNNEL ID of UE "+apn+" is "+pgw_teid);
		}
		
		//install uplink and downlink rules
		installPGWRules(pgw, pgw_dpId, Constants.PGW_SGW_PORT_MAP.get(pgw_dpId.getLong() + Constants.SEPARATOR + sgw_dpId.getLong()), pgw_teid, sgw_tunnelId, pgw_sink_port, apn, ip);
		
		return ip + Constants.SEPARATOR + pgw_teid;
	}

	/*
	 * this method deletes uplink and downlink flow rules from PGW-D
	 */
	public boolean detachUEFromPGW(IOFSwitch pgw, DatapathId sgw_dpId, DatapathId pgw_dpId, int pgw_teid, String ue_ip){
		int S_P_inPort = Constants.PGW_SGW_PORT_MAP.get(pgw_dpId.getLong() + Constants.SEPARATOR + sgw_dpId.getLong());
		//delete uplink rule
		deleteFlowRuleWithTEID(pgw, S_P_inPort, pgw_teid, ue_ip);
			
		if(Constants.DEBUG){
			System.out.println("PGW deleting uplink rule with PGW TEID="+pgw_teid+" inPort="+S_P_inPort+" for UE with IP="+ue_ip);
		}

		//delete downlink rule
		deleteFlowRuleWithIP(pgw, pgw_sink_port, ue_ip);
		if(Constants.DEBUG){
			System.out.println("PGW deleting downlink rule with PGW TEID="+pgw_teid+" inPort="+pgw_sink_port+" for UE with IP="+ue_ip);
		}
		
		reuseable_ips.add(ue_ip);
		
		return true;
	}
	
	// Method for deleting downlink rule
	private void deleteFlowRuleWithIP(IOFSwitch sw, int inPort, String ue_ip){
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_DST, IPv4Address.of(ue_ip));

		fmb.setMatch(mb.build());
		sw.write(fmb.build());
	}
	
	// Method for deleting uplink rule
	private void deleteFlowRuleWithTEID(IOFSwitch sw, int inPort, int pgw_teid, String srcIP){
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(srcIP))
		.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(VlanVid.ofVlan(pgw_teid)));

		fmb.setMatch(mb.build());
		sw.write(fmb.build());
	}

	/*
	 * this method installs uplink and downlink flow rules on PGW-D
	 */
	private void installPGWRules(IOFSwitch pgw, DatapathId pgw_dpId, int inPort, int pgw_tunnelId, int sgw_tunnelId, int outPort, String apn, String UE_IP){
		//uplink rule (PGW to SINK)
		if(Constants.DEBUG){
			System.out.println("PGW installing uplink rule on P-GW dpid = "+pgw_dpId.getLong()+" inport="+inPort+" in teid = "+pgw_tunnelId+
					" outPort = "+outPort+" out teid= "+pgw_tunnelId+" of UE apn = "+apn);
		}
		
		installFlowRule(pgw, pgw_dpId, inPort, pgw_tunnelId, outPort, pgw_tunnelId, UE_IP, Constants.SINK_IP);

		//downlink rule (SINK to PGW)
		if(Constants.DEBUG){
			System.out.println("PGW installing downlink rule on P-GW dpid = "+pgw_dpId.getLong()+" inport="+outPort+" in SRC IP = "+UE_IP+
					" outPort = "+inPort+" out teid= "+sgw_tunnelId+" and SRC IP = "+ Constants.SINK_IP +" of UE apn = "+apn);
		}
		installFlowRuleWithIP(pgw, pgw_dpId, outPort, inPort, sgw_tunnelId, UE_IP, Constants.SINK_IP, Constants.SGW_IP_DOWNLINK);
	}

	// Method for installing uplink rule
	private void installFlowRule(IOFSwitch sw, DatapathId dpId, int inPort, int inTunnelId, int outPort, int outTunnelId, String srcIP, String dstIP){
		if(Constants.DEBUG){
			System.out.println("switchService "+ switchService + " dpId "+ dpId);
		}
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_SRC, IPv4Address.of(srcIP))
		.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(VlanVid.ofVlan(inTunnelId)));

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().stripVlan());//outTunnelId
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

	// Method for installing downlink rule
	private void installFlowRuleWithIP(IOFSwitch sw, DatapathId dpId, int inPort, int outPort, int outTunnelId, String UE_IP, String sink_ip, String sgw_ip){
		if(Constants.DEBUG){
			System.out.println("switchService "+ switchService + " dpId "+ dpId);
		}

		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw.getOFFactory().buildMatch();

		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IN_PORT, OFPort.of(inPort))
		.setExact(MatchField.IPV4_DST, IPv4Address.of(UE_IP));

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan(outTunnelId)));
		actions.add(sw.getOFFactory().actions().setNwDst(IPv4Address.of(sgw_ip)));
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
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService = context.getServiceImpl(IOFSwitchService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
	}
}
