#!/bin/bash

######################################################################
# It starts the specified number of iperf3 servers in daemon mode.   #
# All the iperf3 servers listen on the same IP address but different #
# port numbers (in increasing order).								 #
######################################################################

if [ $# != 3 ]; then
	echo "Usage: sh sink.sh <server-ip> <starting-port> <num-servers>"
	exit 1
elif [ $2 -le 0 ]; then
	echo "Starting port number should be greater than 0"
	exit 1
elif [ $3 -le 0 ]; then
	echo "Number of servers should be greater than 0"
	exit 1
else
	for i in $(seq 0 `expr "$3" - 1`); 
	do
		port=`expr "$2" + "$i"`
		iperf3 -s -B $1 -p $port -D
	done
fi
