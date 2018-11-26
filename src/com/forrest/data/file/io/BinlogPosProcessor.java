package com.forrest.data.file.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;

import org.apache.log4j.Logger;

import com.forrest.data.RowResult;
import com.forrest.data.config.ForrestDataConfig;

public class BinlogPosProcessor implements Runnable {
	private static Logger logger = Logger.getLogger(BinlogPosProcessor.class);
	public static RandomAccessFile randomAccessFile;

	private static boolean saveOK = false;
	private static int saveTryTimes = 0;
	private static byte[] contents = null;

	public static boolean saveCurrentBinlogPosToCacheFile(String binLogFileName, Object binLogPosition,
			Map<String, String> gtidMap) {
		if (gtidMap == null) {
			contents = new StringBuffer().append(binLogFileName).append(" ").append(binLogPosition).toString()
					.getBytes();
		} else {
			StringBuffer gtidStr = new StringBuffer().append(binLogFileName).append(" ").append(binLogPosition)
					.append(" ");

			int i = 0;
			int size = gtidMap.size();
			for (Map.Entry<String, String> entry : gtidMap.entrySet()) {
				i++;
				gtidStr.append(entry.getKey()).append(":").append(entry.getValue());
				if (i < size) {
					gtidStr.append(",");
				}

			}
			contents = gtidStr.toString().getBytes();
		}
		try {
			randomAccessFile.seek(0);
		} catch (IOException e) {
			// e.printStackTrace();
			logger.error(e.getMessage());
			return false;
		}

		if (contents.length < ForrestDataConfig.binlogPosCharMaxLength) {
			try {
				randomAccessFile.write(contents);
				randomAccessFile.write(new byte[ForrestDataConfig.binlogPosCharMaxLength - contents.length]);
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
		saveOK = false;
		saveTryTimes = 0;
		while (!saveOK) {
			saveOK = saveCurrentBinlogPosToCacheFile(rowResult.getBinLogFile(), rowResult.getBinLogPos(), null);
			saveTryTimes++;
			if (saveTryTimes > 10) { // 超过10次，就间隔一秒再重试，防止产生大量的日志，将磁盘刷满
				logger.error("save binlog position to file failed, retry times: " + saveTryTimes);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static boolean saveGtid(String binLogFileName, Object binLogPosition, String gtid) {
		byte[] content = new StringBuffer().append(binLogFileName).append(" ").append(binLogPosition).append(" ")
				.append(gtid).toString().getBytes();
		try {
			randomAccessFile.seek(0);
		} catch (IOException e) {
			// e.printStackTrace();
			logger.error(e.getMessage());
			return false;
		}

		if (content.length < ForrestDataConfig.binlogPosCharMaxLength) {
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
		String a = "123" + " ";
		a = a + " 345,123";
		System.out.println(a);

	}

}
