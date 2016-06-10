#!/bin/bash

###################################################################################################
# It starts the specified number of UDP servers to generate NETWORK INITIATED SERVICE REQUEST.    #
# All the UDP servers listen on the same IP address i.e., of the sink machine but different		  #
# port numbers (in increasing order).								  							  #
###################################################################################################

if [ $# != 3 ]; then
	echo "usage ./service_request.sh <server-ip> <starting-port> <num-servers>"
	exit 1
elif [ $2 -le 0 ]; then
	echo "Starting port number should be greater than 0"
	exit 1
elif [ $3 -le 0 ]; then
	echo "Number of servers should be greater than 0"
	exit 1
else
	g++ udp_server.cpp -o udp_server -lpthread -std=c++11  -Wno-write-strings
	./udp_server $1 $2 $3
fi