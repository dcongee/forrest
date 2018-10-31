package com.forrest.data;

import java.math.BigInteger;
import java.util.BitSet;

public class Test {
	public static String bitSetToBinary(BitSet bitSet) {
		System.out.println(bitSet.length());
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < bitSet.length(); i++) {
			if (bitSet.get(i)) {
				stringBuilder.append("1");
			} else {
				stringBuilder.append("0");
			}
		}
		return stringBuilder.toString();
	}

	public static BigInteger binaryToDecimal(String binarySource) {
		BigInteger bi = new BigInteger(binarySource, 2); // 转换为BigInteger类型
		// return Integer.parseInt(bi.toString()); //转换成十进制
		return bi; // 转换成十进制
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		BitSet bitSet = new BitSet();

		bitSet.set(1);
		bitSet.set(3);

		bitSet.set(125);
		StringBuffer s = new StringBuffer();
		for (int i = 0; i < bitSet.length(); i = i + 1) {

			System.out.print(bitSet.get(i) + "-");
			if (bitSet.get(i)) {
				s.append(i).append("-");
			}
		}
		System.out.println();
		System.out.println(bitSet.length());
		System.out.println(s.toString());

	}

}
