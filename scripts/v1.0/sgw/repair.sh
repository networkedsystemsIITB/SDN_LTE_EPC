#!/bin/bash

# Disconnect OVS with controller
sudo ovs-vsctl del-controller br0

# Delete all ports of the OVS bridge
sudo ovs-vsctl --if-exists del-port br0 eth0
sudo ovs-vsctl --if-exists del-port br0 eth1
sudo ovs-vsctl --if-exists del-port br0 eth2
sudo ovs-vsctl --if-exists del-port br0 int1
sudo ovs-vsctl --if-exists del-port br0 int2

# Delete bridge
sudo ovs-vsctl --if-exists del-br br0

# Configure IP addresses of the network interfaces
sudo ifconfig eth0 10.125.41.4 netmask 255.255.0.0
sudo ifconfig eth1 10.126.41.4 netmask 255.255.0.0
sudo ifconfig eth2 10.128.41.4 netmask 255.255.0.0

# Set default MTU values for the interfaces
ifconfig eth0 mtu 1500
ifconfig eth1 mtu 1500
ifconfig eth2 mtu 1500
