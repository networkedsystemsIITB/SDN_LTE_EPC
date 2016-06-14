#!/bin/bash

if [ $# -gt 1 ]; then
	echo "Usage: ./config.sh [link mtu]"
	exit 1
fi

# Delete existing OVS bridge and create a new bridge
sudo ovs-vsctl --if-exists del-br br0
sudo ovs-vsctl add-br br0

# Add internal ports
sudo ovs-vsctl add-port br0 int1 -- set Interface int1 type=internal
sudo ovs-vsctl add-port br0 int2 -- set Interface int2 type=internal

# Set bridge configuration parameters
ovs-vsctl set bridge br0 protocols=OpenFlow10
ovs-vsctl set bridge br0 other-config:datapath-id=0000000000000002

# Add physical interfaces as ports
sudo ovs-vsctl add-port br0 eth2
sudo ovs-vsctl add-port br0 eth3

# Assign eth2 parameters to internal port int1
sudo ifconfig eth2 0
sudo ifconfig int1 10.126.41.4 netmask 255.255.0.0

# Assign eth3 parameters to internal port int2
sudo ifconfig eth3 0
sudo ifconfig int2 10.125.41.4 netmask 255.255.0.0

# Connect to controller
sudo ovs-vsctl set-controller br0 tcp:10.128.41.1:6653

# Set MTU values for the interfaces, default is 2500 bytes
if [ $# -ne 1 ]; then
	ifconfig eth2 mtu 2500
	ifconfig eth3 mtu 2500
else
	ifconfig eth2 mtu $1
	ifconfig eth3 mtu $1
fi