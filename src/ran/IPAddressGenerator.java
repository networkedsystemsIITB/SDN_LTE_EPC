class IPAddressGenerator {
    
    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        IPAddressGenerator ipGen = new IPAddressGenerator();
        String ipStartingSeq = args[0];
        int numberOfIP = Integer.parseInt(args[1]);
        ipGen.generateNextIPSequence(ipStartingSeq, numberOfIP);
    }

    public void generateNextIPSequence(String address, int count)
    throws Exception {
        String nextIpAddress = address;
        for (int i = 0; i < count; i++) {
            System.out.println(nextIpAddress);
            nextIpAddress = getNextIPAddress(nextIpAddress);

        }
    }

    public String[] getPartsOfIpAddress(String ipAddress) {
        String[] elements = ipAddress.split("\\.");

        return elements;
    }

    public String getNextIPAddress(String ipAddress) throws Exception {

        String[] elements = getPartsOfIpAddress(ipAddress);
        if (elements != null && elements.length == 4) {
            Integer part1 = Integer.parseInt(elements[0]);
            Integer part2 = Integer.parseInt(elements[1]);
            Integer part3 = Integer.parseInt(elements[2]);
            Integer part4 = Integer.parseInt(elements[3]);
            if (part4 < 255) {
                String ip = part1 + "." + part2 + "." + part3 + "." + (++part4);
                return ip;
            } else if (part4 == 255) {
                if (part3 < 255) {
                    String ip = part1 + "." + part2 + "." + (++part3) + "."
                    + (0);
                    return ip;
                } else if (part3 == 255) {
                    if (part2 < 255) {
                        String ip = part1 + "." + (++part2) + "." + (0) + "."
                        + (0);
                        return ip;
                    } else if (part2 == 255) {
                        if (part1 < 255) {
                            String ip = (++part1) + "." + (0) + "." + (0) + "."
                            + (0);
                            return ip;
                        } else if (part1 == 255) {
                            throw new Exception("IP Range Exceeded -> "+ipAddress);
                        }
                    }
                }
            }
        }
        return null;
    }
}