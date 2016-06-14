#!/bin/bash

# Start the containers
sudo lxc-start -n controller -d
sudo lxc-start -n ran -d
sudo lxc-start -n default_switch -d
sudo lxc-start -n sgw -d
sudo lxc-start -n pgw -d
sudo lxc-start -n sink -d