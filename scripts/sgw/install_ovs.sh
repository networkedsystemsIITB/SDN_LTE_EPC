#!/bin/bash

apt-get install -y git python-simplejson python-qt4 python-twisted-conch automake autoconf gcc uml-utilities libtool build-essential git pkg-config linux-headers-`uname -r`
tar -zxvf openvswitch-2.3.2.tar.gz
cd openvswitch-2.3.2
./boot.sh
./configure --with-linux=/lib/modules/`uname -r`/build
make && make install
cd datapath/linux
modprobe openvswitch
lsmod | grep openvswitch
touch /usr/local/etc/ovs-vswitchd.conf
mkdir -p /usr/local/etc/openvswitch
cd ../..
ovsdb-tool create /usr/local/etc/openvswitch/conf.db  vswitchd/vswitch.ovsschema
ovsdb-server /usr/local/etc/openvswitch/conf.db \
--remote=punix:/usr/local/var/run/openvswitch/db.sock \
--remote=db:Open_vSwitch,Open_vSwitch,manager_options \
--private-key=db:Open_vSwitch,SSL,private_key \
--certificate=db:Open_vSwitch,SSL,certificate \
--bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert --pidfile --detach --log-file
ovs-vsctl --no-wait init
ovs-vswitchd --pidfile --detach
ovs-vsctl show
ovs-vsctl --version
ps -ea | grep ovs
