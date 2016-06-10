#!/bin/bash

# Add 6 linux bridges
sudo brctl addbr br0
sudo brctl addbr br1
sudo brctl addbr br2
sudo brctl addbr br3
sudo brctl addbr br4
sudo brctl addbr br5
sudo brctl addbr br6

# Bring the bridges up
sudo ip link set br0 up
sudo ip link set br1 up
sudo ip link set br2 up
sudo ip link set br3 up
sudo ip link set br4 up
sudo ip link set br5 up
sudo ip link set br6 up

# Assign IP to bridges
sudo ifconfig br0 10.127.41.10 netmask 255.255.0.0
sudo ifconfig br1 10.126.41.10 netmask 255.255.0.0
sudo ifconfig br2 10.125.41.10 netmask 255.255.0.0
sudo ifconfig br3 10.127.41.20 netmask 255.255.0.0
sudo ifconfig br4 10.128.41.10 netmask 255.255.0.0
sudo ifconfig br5 10.124.41.10 netmask 255.255.0.0
sudo ifconfig br6 10.123.41.10 netmask 255.255.0.0