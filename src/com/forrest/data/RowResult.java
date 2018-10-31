package com.forrest.data;

public class RowResult {
	// List<ColumnResult> columnResultList;
	String sqlType;
	String databaseName;
	String tableName;
	String binLogFile;
	Long binLogPos;

	private static volatile RowResult rowResult;

	public RowResult() {
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

}
