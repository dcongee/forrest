package com.forrest.data;

import java.util.HashMap;
import java.util.Map;

public class RowResult {
	// List<ColumnResult> columnResultList;
	String sqlType;
	String databaseName;
	String tableName;
	String binLogFile;
	Long binLogPos;
	// String gtid;
	Map<String, String> gtidMap;
	private static volatile RowResult rowResult;

	public RowResult() {
		this.gtidMap = new HashMap<String, String>();
	}

	public static RowResult getInstance() {
		if (rowResult == null) {
			synchronized (ColumnResult.class) {
				if (rowResult == null) {
					rowResult = new RowResult();
				}
			}
		}
		return rowResult;
	}

	public String getSqlType() {
		return sqlType;
	}

	public void setSqlType(String sqlType) {
		this.sqlType = sqlType;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getBinLogFile() {
		return binLogFile;
	}

	public void setBinLogFile(String binLogFile) {
		this.binLogFile = binLogFile;
	}

	public Long getBinLogPos() {
		return binLogPos;
	}

	public void setBinLogPos(Long binLogPos) {
		this.binLogPos = binLogPos;
	}

	// public String getGtid() {
	// return gtid;
	// }
	//
	// public void setGtid(String gtid) {
	// this.gtid = gtid;
	// }

	public void setGtidMap(Map<String, String> gtidMap) {
		this.gtidMap = gtidMap;
	}

	public void addToGtidMap(String gtid) {
		String[] gtidArry = gtid.split(":");
		String uuid = gtidArry[0];
		String transactionID = gtidArry[1];
		this.gtidMap.put(uuid, "1-" + transactionID);
	}

	public Map<String, String> getGtidMap() {
		return this.gtidMap;
	}

	public static RowResult getRowResult() {
		return rowResult;
	}

	public static void setRowResult(RowResult rowResult) {
		RowResult.rowResult = rowResult;
	}

}
