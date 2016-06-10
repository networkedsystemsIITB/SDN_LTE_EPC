/*****************************************************************
 * This class contains the code of Home Subscriber Server (HSS). *
 * This class connects to the MySql database containing UE      *
 * specific data for verifying the UE details and establish a    *
 * secure communication thereafter                               *
 *****************************************************************/

package net.floodlightcontroller.MTP3;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.projectfloodlight.openflow.types.DatapathId;

public class HSS {

	private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
	private static final String DB_CONNECTION = "jdbc:mysql://localhost:3306/HSS";
	private static final String DB_USER = "root";
	private static final String DB_PASSWORD = "root";
	private Connection dbConnection;

	HSS() {
		dbConnection = getDBConnection();
	}
	
	@SuppressWarnings("resource")
	/*
	 * This method validates the UE on the various parameters like IMSI.
	 */
	public String validateUser(String imsi, int SN_ID, String nw_type, int SQN,
			String tai) {
		// imsi: International mobile subscriber identity, SN_ID: Serving network
		// Id, nw_type: Network type identifier, SQN: UE Sequence number, tai:
		// Tracking area identifier
		long key = 0, rand;
		PreparedStatement preparedStatement = null;
		ResultSet rs = null;
		String selectSQL = "SELECT key_id from ue_data where imsi = ?";
		try {
			selectSQL = "SELECT key_id from ue_data where imsi = ?";
			preparedStatement = dbConnection.prepareStatement(selectSQL);
			preparedStatement.setLong(1, Long.parseLong(imsi));
			for (int i = 0; i < 1; i++) {
				rs = preparedStatement.executeQuery();
			}
			if (rs.next()) {
				key = rs.getLong("key_id");
			} else {
				System.out.println("Error in selecting UE tracking area");
				return null;
			}

			rand = Utils.randInt(25, 84); // Any range of numbers
			long tempArray[] = EPS_AKA_algorithm(key, rand, SQN, SN_ID); 
			// tempArray[0]: autn, tempArray[1]: xres, tempArray[2]: K_ASME
			
			return tempArray[1] + Constants.SEPARATOR + tempArray[0]
					+ Constants.SEPARATOR + rand + Constants.SEPARATOR
					+ tempArray[2];
		} catch (SQLException e) {
			System.out.println(e.getMessage());
			return null;
		} finally {
			try {
				if (preparedStatement != null) {
					preparedStatement.close();
				}
				if (rs != null) {
					rs.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * This method featches the ID of PGW based on the APN (Access Point Name) specified by the UE
	 */
	@SuppressWarnings("resource")
	public DatapathId getPGateway(String apn) {
		PreparedStatement preparedStatement = null;
		DatapathId dpid = null;
		ResultSet rs = null;
		String selectSQL = "SELECT pgw_dpid from apn_to_pgw where apn = ?";
		try {
			preparedStatement = dbConnection.prepareStatement(selectSQL);
			preparedStatement.setLong(1, Long.parseLong(apn));
			rs = preparedStatement.executeQuery();
			if (rs.next()) {
				dpid = DatapathId.of(rs.getLong("pgw_dpid"));
			} else {
				return null;
			}
			return dpid;
		} catch (SQLException e) {
			System.out.println(e.getMessage());
			return null;
		} finally {
			try {
				if (preparedStatement != null) {
					preparedStatement.close();
				}
				if (rs != null) {
					rs.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Authentication & Key Agreement algorithm
	 */
	private long[] EPS_AKA_algorithm(long key, long rand, int SQN, int SN_ID) { 
		long xres, autn, CK, IK, K_ASME; 
		// CK: Cipher key, IK: Integrity key,
		// ASME: Access Security Management
		// Entity (in this case MME)
		
		xres = rand * key + SQN;
		autn = (rand - 1) * (key + 1) - SQN;
		CK = (rand + 1) * (key - 1) - (SQN + 1);
		IK = (rand + 1) * (key + 1) - (SQN - 1);
		K_ASME = KDF(SQN, SN_ID, CK, IK);
		return new long[] { autn, xres, K_ASME };
	}

	/*
	 * Key derivation function
	 */
	private long KDF(int SQN, int SN_ID, long CK, long IK) { 
		long K_ASME; // ASME: Access Security Management Entity (in this case MME)
		K_ASME = SQN * CK + SN_ID * IK;
		return K_ASME;
	}

	/*
	 * Method to get a database connection to the mysql database holding HSS data
	 */
	private Connection getDBConnection() {
		Connection dbConnection = null;
		try {
			Class.forName(DB_DRIVER);

		} catch (ClassNotFoundException e) {
			System.out.println(e.getMessage());
		}

		try {
			dbConnection = DriverManager.getConnection(DB_CONNECTION, DB_USER,
					DB_PASSWORD);
			return dbConnection;
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		return dbConnection;
	}
}