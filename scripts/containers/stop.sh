#!/bin/bash

# Stop the containers
sudo lxc-stop -n controller
sudo lxc-stop -n ran
sudo lxc-stop -n default_switch
sudo lxc-stop -n sgw
sudo lxc-stop -n pgw
sudo lxc-stop -n sink