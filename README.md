## SDN-based LTE EPC

SDN EPC is an implementation of Long Term Evolution Evolved Packet Core (LTE EPC) using design principles of SDN (Software Defined Networking). It simulates the working of a typical EPC for handling signaling and data traffic. Current Version is 1.0, and it is released on June 10, 2016.

#### Outline
SDN is one of the recent technologies being considered to re-design the existing LTE architecture, especially the packet core network (i.e., EPC) because of scalability and flexibility issues. Using SDN-based approach, we have identified and separated out the control and data plane functionalities of the EPC components. Though the implementation is not exactly according to 3GPP standard specifications, we have considered the primary procedures that are important to evaluate system performance.
Although there have been several proposals in this field, but currently there does not exist any open-source framework for research and experimentation (to the best of our knowledge). This project can be used by researchers to compare and analyse different design choices on the basis of various performance metrics. Besides, new functionalities can be developed on top of the existing code corresponding to any new specifications.

We are in the process of incorporating more procedures, thus making it more standards compliant. We expect that this project will encourage more research and innovation in this space.

#### List of developed modules

- Mobility Management Entity
- Home Subscriber Server
- Serving Gateway
- Packet Data Network Gateway
- Radio Access Network Simulator
- Sink node

#### Supported LTE procedures

- Initial Attach
- Authentication
- Location update
- Bearer setup
- User plane data transfer
- S1 Release
- UE-initiated Service Request
- Network-initiated Service Request

#### Directory structure

- **src**: Contains source code files
- **doc**: Contains project documentation
- **scripts**: Contains necessary scripts for the setup

#### Contents ####

- Source code and scripts for various EPC components
- A [user guide](docs/README_User.md) containing the setup and installation instructions.
- A [developer guide](docs/README_Developer.md) which explains the structure of the source code.

#### Authors ####

* [Aman Jain](https://www.linkedin.com/in/aman-jain-04590515), Master's student (2014-2016), Dept. of Computer Science and Engineering, IIT Bombay.
* [Sunny Kumar Lohani](https://www.linkedin.com/in/sunny-lohani-a52a958b), Master's student (2014-2016), Dept. of Computer Science and Engineering, IIT Bombay.
* [Prof. Mythili Vutukuru](https://www.cse.iitb.ac.in/~mythili/), Dept. of Computer Science and Engineering, IIT Bombay.

#### Contact us

- Aman Jain, jain7aman[AT]gmail.com
- Sunny Kumar Lohani, sunny.lohani[AT]gmail.com
- Prof. Mythili Vutukuru, mythili[AT]cse.iitb.ac.in
