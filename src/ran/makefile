all:	ran

RAN_P = ran.cpp ran.h client.cpp client.h ue.cpp ue.h utils.cpp utils.h

RAN_R = g++ ran.cpp -std=c++11 -w -o ran client.cpp ue.cpp utils.cpp -lpthread -lcrypto

ran:	$(RAN_P)
	$(RAN_R)

clean:
	rm -f ran *~
