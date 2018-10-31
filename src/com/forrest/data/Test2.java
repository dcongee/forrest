package com.forrest.data;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Test2 {

	public void writeFile() {
		
	}

	public static void main(String[] args) {
		// File file = new File("fd.conf");
		//
		// if (!file.exists()) {
		// System.out.println("fd.conf " + file.getAbsolutePath() + " not found!");
		// } else {
		// System.out.println("ok");
		// }

		InputStream in = Test2.class.getClassLoader().getResourceAsStream("fd.conf");
		System.out.println(System.getProperty("java.class.path"));
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		try {
			System.out.println(reader.readLine());
		} catch (IOException e) {
			e.printStackTrace();
		}
		StringBuffer str = null;
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream("cache.file");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		str = new StringBuffer();
		str.append("@1234567");
		try {
			fileOutputStream.write(str.toString().getBytes());
			str = null;
			// FLowDataConfig.cacheFileOutputStream.flush();
			fileOutputStream.flush();
			fileOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
