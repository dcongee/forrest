package com.forrest.data;

public class ColumnResult {
	String databaseName;
	String tableName;
	String columnName;
	Object columnValue;

	private static volatile ColumnResult columnResult;

	public ColumnResult() {
	}

	public static ColumnResult getInstance() {
		if (columnResult == null) {
			synchronized (ColumnResult.class) {
				if (columnResult == null) {
					columnResult = new ColumnResult();
				}
			}
		}
		return columnResult;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	public Object getColumnValue() {
		return columnValue;
	}

	public void setColumnValue(Object columnValue) {
		this.columnValue = columnValue;
	}
}
