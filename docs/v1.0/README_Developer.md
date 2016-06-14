## SDN EPC Developer Guide

The manual contains a brief overview of the project source code. The RAN simulator is coded in `C/C++` and the controller uses `Java`. We start by describing sequence of procedures that take place in the RAN simulator (see Fig. 1).

<div align="center">
<img src="images/ue_life_cycle.png" alt="Fig. 1: Life cycle of a simulated UE" width="450" height="420" />
</div>
<p align="center">Fig. 1: Life cycle of simulated UE(s)</p>

Following are the steps:

1. **Authentication:** In this step, exchange of authentication messages takes place between RAN and the MME via default switch using `UDP`. Also, the MME communicates with HSS which in turn queries the `MySql` database using `JDBC` driver.

2. **Tunnel setup:** After successful authentication, the MME initiates tunnel setup procedure. During this step, exchange of control messages take place among MME, SGW-C and PGW-C via Java object calls. Besides, `OpenFlow` rules are installed into the concerned data plane switches, thus, establishing the tunnel.

3. **Data transfer:** This step is optional. During this step, `iperf3` at RAN generates data in the form of `TCP` traffic which flows through the EPC to the sink.

4. **Detach:** During this step, the control applications (MME, SGW-C and PGW-C) delete the `OpenFlow` rules from the respective data plane switches, thus disrupting the tunnel.

#### Architectural overview ####

**<u>RAN</u>:**  
RAN is a multi-threaded simulator in which each thread simulates the life cycle of a UE. Each iteration of the threads generates a new UE which follows the steps shown in Fig. 1. If sending data, each UE thread uses iperf3 client to connect with a corresponding iperf3 server running on the Sink (see Fig. 2). The structure of the RAN code has been depicted in Fig. 3.

<div align="center">
<img src="images/end_simulators.png" alt="Fig. 2: Overview of the setup" />
</div>
<p align="center">Fig. 2: Overview of the setup</p>

- The main file in the RAN simulator is *ran.cpp* which is responsible for creating threads for each UE and invoking the procedures for each UE.
- The file *ue.cpp* abstracts out all the functionalities associated with a UE (Attach, Tunnel setup, Data transfer, Detach, etc). Each thread created by ran.cpp instantiates a UE (*ue.cpp*) object. Each UE object exploits the methods associated with it to perform various LTE procedures.
- Further, each UE object instantiates a Client (*client.cpp*) object which contains all socket level attributes and methods. These methods are used by UE object to send/receive messages during various LTE procedures.
- The file *utils.cpp* contains various utility functions such as hash MAC (Message Authentication Code), Encryption/Decryption, etc.

<div align="center">
<img src="images/ran_code_structure.png" alt="Fig. 2: Overview of the setup" <img src="ue_life_cycle.png" alt="Fig. 3: Code structure of RAN simulator" width="390" height="350" />
</div>
<p align="center">Fig. 3: Code structure of RAN simulator</p>

**<u>Controller</u>:**  
The controller code contains 3 floodlight modules namely *MME.java*, *SGW.java* and *PGW.java*. Apart from these, other associated files are *HSS.java*, *Utils.java* and *Constants.java*.

- *MME.java* is the floodlight module which is responsible for interacting with the RAN simulator via the default switch. It contains various cases depending on the type of message received from RAN and a corresponding action. It delegates various events to *SGW.java* and is responsible for installing/uninstalling `OpenFlow` rules into the default switch. Besides, it also responsible for communicating with *HSS.java*.
- *SGW.java* performs the control plane functionalities of SGW including installing/uninstalling `OpenFlow` rules into the SGW switch. It also acts as an interface between *MME.java* and *PGW.java*.
- *PGW.java* performs the control plane functionalities of PGW including installing/uninstalling `OpenFlow` rules into the PGW switch.

#### Caveat ####

The cipher and integrity checks are not implemented as they should be. Instead, we have just included the cost of these operations by calling their specific methods on dummy data. We faced problems while performing the cipher/integrity checks between C/C++ (for `RAN`) and Java (for `Controller`).
