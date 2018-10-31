package com.forrest.data.file.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;

import com.forrest.data.RowResult;
import com.forrest.data.config.ForrestDataConfig;

public class BinlogPosProcessor implements Runnable {
	private static Logger logger = Logger.getLogger(BinlogPosProcessor.class);
	public static RandomAccessFile randomAccessFile;

	public static boolean saveCurrentBinlogPosToCacheFile(String binLogFileName, String binLogPosition) {
		byte[] content = new StringBuffer().append(binLogFileName).append(" ").append(binLogPosition).toString()
				.getBytes();
		try {
			randomAccessFile.seek(0);
		} catch (IOException e) {
			// e.printStackTrace();
			logger.error(e.getMessage());
			return false;
		}

		if (content.length < 96) {
			try {
				randomAccessFile.write(content);
				randomAccessFile.write(new byte[ForrestDataConfig.binlogPosCharMaxLength - content.length]);
			} catch (IOException e) {
				// e.printStackTrace();
				logger.error(e.getMessage());
				return false;
			}
		} else {
			logger.error("char length of binlog file name and position is too long,than "
					+ (ForrestDataConfig.binlogPosCharMaxLength - 1));
			return false;
		}
		return true;
	}

	public static void saveCurrentBinlogPosToCacheFile(RowResult rowResult) {
		saveCurrentBinlogPosToCacheFile(rowResult.getBinLogFile(), String.valueOf(rowResult.getBinLogPos()));

	}

	public static void closeBinlogCacheFile(FileOutputStream fos1, FileOutputStream fos2) {
		if (fos1 != null) {
			try {
				fos1.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (fos2 != null) {
			try {
				fos2.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {

	}

	public static void getCurrentBinlogPosFromCacheFile(String cacheFilePath) {
		FileInputStream fin = null;
		try {
			fin = new FileInputStream(new File(cacheFilePath));
			FileChannel channel = fin.getChannel();
			int capacity = 100;// 字节
			ByteBuffer bf = ByteBuffer.allocate(capacity);
			System.out.println("限制是：" + bf.limit() + "容量是：" + bf.capacity() + "位置是：" + bf.position());
			int length = -1;
			while ((length = channel.read(bf)) != -1) {
				/*
				 * 注意，读取后，将位置置为0，将limit置为容量, 以备下次读入到字节缓冲中，从0开始存储
				 */
				bf.clear();
				byte[] bytes = bf.array();
				System.out.write(bytes, 0, length);
				System.out.println();
				System.out.println("限制是：" + bf.limit() + "容量是：" + bf.capacity() + "位置是：" + bf.position());

			}

			channel.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fin != null) {
				try {
					fin.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		// String cacheFilePath = "D:\\workspace\\flowdata\\cache.file";
		// // BinlogPosProcessor.getCurrentBinlogPosFromCacheFile(cacheFilePath);
		//
		// File f = new File(cacheFilePath);
		// BinlogPosProcessor.openBinlogCacheFile(f);
		// BinlogPosProcessor.saveCureentBinlogToCacheFile("mysql-bin.00123 999999999");
		// BinlogPosProcessor.saveCureentBinlogToCacheFile("mysql-bin.00123 888888888");
		// BinlogPosProcessor.save();
		// BinlogPosProcessor.save();
		// BinlogPosProcessor.binlogCacheFilePath =
		// "D:\\workspace\\flowdata\\cache.file";
		// BinlogPosProcessor.saveCurrentBinlogPosToCacheFile("mysql-bin.000020",
		// "7000000000000000000000000000000000000000000000000000000000110");

		try {
			BinlogPosProcessor.randomAccessFile = new RandomAccessFile(new File("D:\\workspace\\flowdata\\cache.file"),
					"rw");
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile("mysql-bin.000020",
					"700000000000000000000000000000000000000000000000000000000000000000000000000110");
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile("mysql-bin.000020", "1110");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
