package com.forrest.data.dest;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.forrest.data.ForrestDataUtil;
import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.impl.ForrestDataDestStdout;
import com.forrest.data.file.io.BinlogPosProcessor;
import com.forrest.monitor.ForrestMonitor;

public abstract class ForrestDataAbstractDestination {

	public ForrestDataConfig config;
	public ForrestMonitor forrestMonitor;

	public long deliverTryTimes = 0;
	public boolean deliverOK = false;

	public boolean saveOK = false;
	public long saveTryTimes = 0;

	public ForrestDataAbstractDestination() {

	}

	public byte[] getByteArrayFromMapJson(Map<String, Object> row) {
		if (row.size() == 0 || row == null) {
			return null;
		}
		byte[] rowByte = null;
		try {
			rowByte = JSON.toJSONString(row).getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return rowByte;
	}

	public String getJsonStringFromMap(Map<String, Object> row) {
		if (row.size() == 0 || row == null) {
			return null;
		}
		return JSON.toJSONString(row);
	}

	/**
	 * 保证binlog pos写成功
	 * 
	 * @param binLongFileName
	 * @param binlogPosition
	 */
	public void saveBinlogPos(String binLongFileName, String binlogPosition) {
		saveOK = false;
		saveTryTimes = 0;
		while (!saveOK) {
			saveOK = BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(binLongFileName, binlogPosition);
			saveTryTimes++;
			if (saveTryTimes > 10) { // 超过10次，就间隔一秒再重试，防止产生大量的日志，将磁盘刷满
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		// forrestMonitor.getMonitorMap().put("exec_master_binlog_position",
		// binlogPosition);
		// forrestMonitor.getMonitorMap().put("exec_master_binlog_file",
		// binLongFileName);
		forrestMonitor.putExecBinlogInfo(binLongFileName, binlogPosition);
	}

	public void flushMetaData(Map<String, Object> row) {
		config.getMetaDataInfo();
		config.getTablePrimary();
	}

	/**
	 * 获取row中表名称的主键(主键有可能是联合主键)
	 * 
	 * @param row
	 * @return
	 */
	public String getPrimaryKeyValueFromRow(Map<String, Object> row) {
		String schemName = (String) row.get(ForrestDataConfig.metaDatabaseName);
		String tableName = (String) row.get(ForrestDataConfig.metaTableName);

		// if
		// (!ForrestDataConfig.tablePrimary.containsKey(ForrestDataUtil.getMetaDataMapKey(schemName,
		// tableName))) {
		// return null;
		// }
		// StringBuffer sb = new StringBuffer();
		// List<String> tablePrimaryColumnList = ForrestDataConfig.tablePrimary
		// .get(ForrestDataUtil.getMetaDataMapKey(schemName, tableName));
		//
		// if (tablePrimaryColumnList.size() == 1) {
		// sb.append(row.get(tablePrimaryColumnList.get(0)));
		// } else {
		// for (int i = 0; i < tablePrimaryColumnList.size(); i++) {
		// if (i == tablePrimaryColumnList.size() - 1) {
		// sb.append(row.get(tablePrimaryColumnList.get(i)));
		// } else {
		// sb.append(row.get(tablePrimaryColumnList.get(i))).append("_");
		// }
		// }
		// }

		return getPrimaryKeyValueFromRow(schemName, tableName, row);
	}

	public String getPrimaryKeyValueFromRow(String schemName, String tableName, Map<String, Object> row) {
		if (!ForrestDataConfig.tablePrimary.containsKey(ForrestDataUtil.getMetaDataMapKey(schemName, tableName))) {
			return null;
		}
		StringBuffer sb = new StringBuffer();
		List<String> tablePrimaryColumnList = ForrestDataConfig.tablePrimary
				.get(ForrestDataUtil.getMetaDataMapKey(schemName, tableName));
		if (tablePrimaryColumnList.size() == 1) {
			String primaryKeyValue = (String) row.get(tablePrimaryColumnList.get(0));
			if (primaryKeyValue == null) {
				return null;
			}
			sb.append(primaryKeyValue);
		} else {
			for (int i = 0; i < tablePrimaryColumnList.size(); i++) {
				String primaryKeyValue = (String) row.get(tablePrimaryColumnList.get(i));
				if (primaryKeyValue.equals(null)) {
					return null;
				}
				if (i == tablePrimaryColumnList.size() - 1) {
					sb.append(primaryKeyValue);
				} else {
					sb.append(primaryKeyValue).append("_");
				}
			}
		}
		return sb.toString();
	}

	/**
	 * 删除数据中的元数据
	 * 
	 * @param row
	 *            数据行
	 */
	public void removeMetadataData(Map<String, Object> row) {
		if (row != null && row.size() > 0) {
			row.remove(ForrestDataConfig.metaDatabaseName);
			row.remove(ForrestDataConfig.metaTableName);
			row.remove(ForrestDataConfig.metaBinLogFileName);
			row.remove(ForrestDataConfig.metaBinlogPositionName);
			row.remove(ForrestDataConfig.metaSqltypeName);
		}
	}

	public static void main(String args[]) {
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		long length = 5000000;
		long begin = System.currentTimeMillis();
		for (long i = 0; i < length; i++) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("TABLE_NAME", "W1w1w1w1w1w1w1w1w1w1w1w1");
			map.put("BLOB_C", 123456789 + i);
			map.put("DECIMAL_TEST", "W1w1w1w1w1w1w1w1w1w1w1w1");
			map.put("DATABASE_NAME", "W1w1w1w1w1w1w11w1w1w1");
			map.put("BINLOG_FILE", "mysql-bin.000035");
			map.put("FLOAT_TEST", "W1w1w1w1w1w1w1w1w1w1w1w1");
			map.put("SQL_TYPE", "W1w1w1w1w1w1w1w1w1w1w1w1");
			map.put("BINLOG_POS", 1234567890 + i);
			map.put("DOUBLE_TEST", "W1w1w1w1w1w1w1w1w1w1w1w1");
			map.put("TITLE", "通过Base64 将String转换成byte[]或者byte[]转换成String[Java 8]");
			map.put("id", "asdiwere");
			list.add(map);
		}

		System.out.println("data init time: " + (System.currentTimeMillis() - begin) + "ms.");
		ForrestDataAbstractDestination f = new ForrestDataDestStdout();
		System.out.println("begin to parse json.");
		begin = System.currentTimeMillis();
		for (Map<String, Object> row : list) {
			f.getJsonStringFromMap(row);
		}
		long end = System.currentTimeMillis();
		System.out.println("parse json numbers: " + length + " time: " + (end - begin) + "ms. avg(ms): "
				+ (length / (end - begin)) + " ");

	}
}
