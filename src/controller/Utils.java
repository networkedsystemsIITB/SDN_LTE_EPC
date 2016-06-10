/****************************************************************
 * This class contains various utility methods used in our code *
 ****************************************************************/
package net.floodlightcontroller.MTP3;

import javax.crypto.Mac;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import java.security.*;

public class Utils {

	/*
	 * Method for generating a random integer between a specified range
	 */
	public static int randInt(int min, int max) {
		// Usually this can be a field rather than a method variable
		Random rand = new Random();
		// nextInt is normally exclusive of the top value,
		// so add 1 to make it inclusive
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}

	// Used for NAS integrity signaling to produce Hash Message Authentication
	// Code (HMAC)
	// Reference:
	// http://www.supermind.org/blog/1102/generating-hmac-md5-sha1-sha256-etc-in-java
	public static String hmacDigest(String msg, String keyString) {
		if (Constants.DEBUG) {
			System.out.println("HMAC Digest: msg--" + msg + "-- IntegrityKey--"
					+ keyString + "--");
		}
		String digest = null;
		String algo = "HmacSHA1"; // We are using HMAC SHA1 algorithm
		try {
			SecretKeySpec key = new SecretKeySpec(
					(keyString).getBytes("UTF-8"), algo);
			Mac mac = Mac.getInstance(algo);
			mac.init(key);

			byte[] bytes = mac.doFinal(msg.getBytes("ISO-8859-1")); // ISO-8859-1

			StringBuffer hash = new StringBuffer();
			for (int i = 0; i < bytes.length; i++) {
				String hex = Integer.toHexString(0xFF & bytes[i]);
				if (hex.length() == 1) {
					hash.append('0');
				}
				hash.append(hex);
			}
			digest = hash.toString();
		} catch (UnsupportedEncodingException e) {
		} catch (InvalidKeyException e) {
		} catch (NoSuchAlgorithmException e) {
		}
		return digest;
	}

	// 128 bit AES encryption algorithm
	public static byte[] aesEncrypt(String pText, String secretKey) {
		// pText: Plain text, secretKey: Encryption key of 128 bits (i.e. of
		// length 16)
		if (Constants.DEBUG) {
			System.out.println("aesEncrypt: plainText= " + pText
					+ " EncryptionKey= " + secretKey);
		}
		byte[] ptextBytes = null;
		try {
			ptextBytes = pText.getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		byte[] encBytes = null;
		try {
			String ivString = "0123456789012345";
			byte[] secret = secretKey.getBytes();
			byte[] iv = ivString.getBytes();
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			Key k = new SecretKeySpec(secret, "AES");
			c.init(Cipher.ENCRYPT_MODE, k, new IvParameterSpec(iv));

			encBytes = c.doFinal(ptextBytes);
		} catch (IllegalBlockSizeException ex) {
			System.out.println(ex.getMessage());
		} catch (BadPaddingException ex) {
			System.out.println(ex.getMessage());
		} catch (InvalidKeyException ex) {
			System.out.println(ex.getMessage());
		} catch (NoSuchAlgorithmException ex) {
			System.out.println(ex.getMessage());
		} catch (NoSuchPaddingException ex) {
			System.out.println(ex.getMessage());
		} catch (InvalidAlgorithmParameterException ex) {
			System.out.println(ex.getMessage());
		}
		return encBytes;
	}

	// 128 bit AES decryption algorithm
	public static String aesDecrypt(byte[] encBytes, String secretKey) {
		// encBytes: Cipher text byte array, secretKey: Decryption key of 128
		// bits (i.e. of length 16)
		if (Constants.DEBUG) {
			try {
				System.out.println("aesDecrypt: cipherText= "
						+ new String(encBytes, "ISO-8859-1")
						+ " DecryptionKey= " + secretKey);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		String decryptedValue = "";
		try {
			String ivString = "0123456789012345";
			byte[] secret = secretKey.getBytes();
			byte[] iv = ivString.getBytes();
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			Key k = new SecretKeySpec(secret, "AES");
			c.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(iv));

			byte[] decBytes = c.doFinal(encBytes);
			decryptedValue = new String(decBytes);
		} catch (IllegalBlockSizeException ex) {
			System.out.println(ex.getMessage());
		} catch (BadPaddingException ex) {
			System.out.println(ex.getMessage());
		} catch (InvalidKeyException ex) {
			System.out.println(ex.getMessage());
		} catch (NoSuchAlgorithmException ex) {
			System.out.println(ex.getMessage());
		} catch (NoSuchPaddingException ex) {
			System.out.println(ex.getMessage());
		} catch (InvalidAlgorithmParameterException ex) {
			System.out.println(ex.getMessage());
		}
		return decryptedValue;
	}
}
