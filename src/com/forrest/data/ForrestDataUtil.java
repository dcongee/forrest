package com.forrest.data;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.Logger;

public class ForrestDataUtil {
	private static Logger logger = Logger.getLogger(ForrestDataUtil.class);

	private static final char[] HEX_CHAR = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f' };

	public static int byteArrayToInt(byte[] b) {
		if (b.length == 4) {
			return b[3] & 0xFF | (b[2] & 0xFF) << 8 | (b[1] & 0xFF) << 16 | (b[0] & 0xFF) << 24;
		} else if (b.length == 3) {
			return b[2] & 0xFF | (b[1] & 0xFF) << 8 | (b[0] & 0xFF) << 16;
		} else if (b.length == 2) {
			return b[1] & 0xFF | (b[0] & 0xFF) << 8;
		} else {
			return b[0] & 0xFF;
		}
	}

	public static int byte2ArrayToInt(byte[] b) {
		return b[1] & 0xFF | (b[0] & 0xFF) << 8;
	}

	public static byte[] intToByteArray(int a) {
		return new byte[] { (byte) ((a >> 24) & 0xFF), (byte) ((a >> 16) & 0xFF), (byte) ((a >> 8) & 0xFF),
				(byte) (a & 0xFF) };
	}

	public static String bytesToHexFun2(byte[] bytes) {
		char[] buf = new char[bytes.length * 2];
		int index = 0;
		for (byte b : bytes) { // 利用位运算进行转换，可以看作方法一的变种
			buf[index++] = HEX_CHAR[b >>> 4 & 0xf];
			buf[index++] = HEX_CHAR[b & 0xf];
		}
		return new String(buf);
	}

	public static String hexStr2Str(String hexStr) {
		String str = "0123456789ABCDEF";
		char[] hexs = hexStr.toCharArray();
		byte[] bytes = new byte[hexStr.length() / 2];
		int n;
		for (int i = 0; i < bytes.length; i++) {
			n = str.indexOf(hexs[2 * i]) * 16;
			n += str.indexOf(hexs[2 * i + 1]);
			bytes[i] = (byte) (n & 0xff);
		}
		return new String(bytes);
	}

	/**
	 * 十六进制转中文
	 * 
	 * @param s
	 * @return
	 */
	public static String toStringHex2(String s) {
		byte[] baKeyword = new byte[s.length() / 2];
		for (int i = 0; i < baKeyword.length; i++) {
			try {
				baKeyword[i] = (byte) (0xff & Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			s = new String(baKeyword, "utf-8");// UTF-16le:Not
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		return s;
	}

	/**
	 * 判断是否是十六进制
	 */
	public static boolean isHexNumber(String str) {
		boolean flag = false;
		for (int i = 0; i < str.length(); i++) {
			char cc = str.charAt(i);
			if (cc == '0' || cc == '1' || cc == '2' || cc == '3' || cc == '4' || cc == '5' || cc == '6' || cc == '7'
					|| cc == '8' || cc == '9' || cc == 'A' || cc == 'B' || cc == 'C' || cc == 'D' || cc == 'E'
					|| cc == 'F' || cc == 'a' || cc == 'b' || cc == 'c' || cc == 'c' || cc == 'd' || cc == 'e'
					|| cc == 'f') {
				flag = true;
			}
		}
		return flag;
	}

	public static String byteArrayToStr(byte[] byteArray) {
		if (byteArray == null) {
			return null;
		}
		String str = new String(byteArray);
		return str;
	}

	public static String getMetaDataMapKey(String schemaName, String tableName, int ordinalPosition) {
		if (schemaName == null || tableName == null) {
			logger.error("Error: meta data info must be not null");
			System.exit(1);
		}
		StringBuffer key = new StringBuffer();
		key.append(schemaName).append(".").append(tableName).append(".").append(ordinalPosition);
		return key.toString();
	}

	public static String getMetaDataMapKey(String schemaName, String tableName) {
		if (schemaName == null || tableName == null) {
			logger.error("Error: meta data info must be not null");
			System.exit(1);
		}
		StringBuffer key = new StringBuffer();
		key.append(schemaName).append(".").append(tableName);
		return key.toString();
	}

	public static String[] getDatabaseNameAndTableNameFromKey(String tableFullName) {
		if (tableFullName == null) {
			logger.error("Error: tableFullName must be not null");
			System.exit(1);
		}
		String[] str = tableFullName.split("\\.");
		return str;
	}

	public static <T> T checkParam(Properties properties, String paramName, Class<?> clazz) {
		T paramValue = null;
		try {
			paramValue = (T) properties.getProperty(paramName).trim();
			paramValue = (T) clazz.newInstance();
			// System.out.println(paramValue);
		} catch (Exception e) {
			logger.error("read parameter " + paramName + " failed: " + e.getMessage());
			System.exit(1);
		}
		return paramValue;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int var = 1236;
		// String hex = Integer.toHexString(var);
		// System.out.println(hex);
		// System.out.println(Integer.parseInt("04d4", 16));
		//
		// byte[] b = Test1.intToByteArray(1236);
		// for (int i = 0; i < b.length; i++) {
		// System.out.println("b[i]==" + b[i]);
		// }
		// System.out.println(Test1.byteArrayToInt(b));

		NumberFormat nf = NumberFormat.getInstance();
		System.out.println(nf.format(3.3000000));

		Date d = new Date("2018-08-06 04:36:45");
		System.out.println(d.toString());
	}

}
