#!/bin/bash

if [ "$#" -lt "4" ]; then
	echo "Usage: ./ipgen.sh <starting_ip_address> <netmask Eg. /16> <number_of_ip_addresses> <interface_name>"
	exit 1
fi

javac IPAddressGenerator.java

# Generate the given number of IP addresses
ipAddresses=`java IPAddressGenerator $1 $3`

# Assign all the IP addresses to the specified interface
array=(`echo ${ipAddresses}`);
num=1;
for element in "${array[@]}"
do
	sudo ip addr add "$element""$2" dev "$4" > /dev/null 2>&1
    num=$((num + 1))
done