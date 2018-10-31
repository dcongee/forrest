package com.forrest.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.alibaba.fastjson.JSON;
import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.data.dest.impl.ForrestDataDestRedis;
import com.forrest.data.file.io.BinlogPosProcessor;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeader;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.FormatDescriptionEventData;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.RotateEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;

import redis.clients.jedis.Jedis;

public class FlowData2 {

	Jedis jedis;

	public FlowData2() {
		// this.jedis = new Jedis("192.168.80.23", 6379);
		// this.jedis.auth("123456");
	}

	static String getMetaDataMapKey(String table_schema, String table_name, int ORDINAL_POSITION) {
		if (table_schema == null || table_name == null) {
			System.err.println("Error: meta data info must be not null");
			System.exit(1);
		}
		StringBuffer key = new StringBuffer();
		key.append(table_schema).append(".").append(table_name).append(".").append(ORDINAL_POSITION);
		return key.toString();
	}

	static String getMetaDataMapKey(String table_schema, String table_name) {
		if (table_schema == null || table_name == null) {
			System.err.println("Error: meta data info must be not null");
			System.exit(1);
		}
		StringBuffer key = new StringBuffer();
		key.append(table_schema).append(".").append(table_name);
		return key.toString();
	}

	public String[] makeRowByColumnAndValue(String column, String value) {
		return new String[] { column, value };
	}

	public void toJsonStr(Map<String, String> rowMap, EventHeader header) {
		String jsonString = JSON.toJSONString(rowMap);
		// String key = rowMap.get("tableName") + "_" +
		// rowMap.get("click_id".toUpperCase());
		// EventHeaderV4 header1 = (EventHeaderV4) header;

		// Pipeline pl = this.jedis.pipelined();
		// pl.set(key, jsonString.toString(), "nx", "ex", 60);
		// pl.hset("mysql_log_pos", "postion",
		// String.valueOf(header1.getNextPosition()));
		// pl.incr("mysql_log_size");
		// pl.sync();
		System.out.println(jsonString.toString());
		// System.out.println(jsonString.toString());
		// RedisClient.getInstance().saveToRedis(key, jsonString.toString());
	}

	public void toJsonStr(List<Map<String, Object>> rowResultList) {
		// String jsonString = JSON.toJSONString(rowResultList);

		for (Map<String, Object> row : rowResultList) {
			System.out.println(JSON.toJSONString(row));
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile((String) row.get(ForrestDataConfig.metaBinLogFileName),
					(String) row.get(ForrestDataConfig.metaBinlogPositionName));
		}
	}

	public static void main(String[] args) {
		ForrestDataConfig config = new ForrestDataConfig();
		config.initConfig();
		// System.exit(1);

		String binLogFile = config.getBinlogFileName();
		long binLogPos = config.getBinlogPostion();
		int serverID = config.getMysqlServerID();

		String host = config.getMysqlHost();
		int port = config.getMysqlPort();
		String user = config.getMysqlUser();
		String password = config.getMysqlPasswd();
		// String dbname = config.getMysqlDBname();

		FlowData2 flowData = new FlowData2();

		BinaryLogClient client = new BinaryLogClient(host, port, serverID, user, password, binLogFile, binLogPos, null);
		RowResult rowResult = RowResult.getInstance();
		rowResult.setBinLogFile(binLogFile);

		ForrestDataDestination dest = null;
		switch (config.getDsType()) {
		case "redis":
			dest = new ForrestDataDestRedis();
			break;
		default:
			break;
		}

		try {

			client.registerEventListener(new EventListener() {

				@Override
				public void onEvent(Event event) {
					EventHeader header = event.getHeader();
					EventHeaderV4 headerV4 = (EventHeaderV4) header;
					// System.out.println(header1.getPosition());
					// System.out.println(header1.getNextPosition());

					switch (header.getEventType()) {
					case FORMAT_DESCRIPTION:
						FormatDescriptionEventData formatData = (FormatDescriptionEventData) event.getData();
						System.out.println("Server version:" + formatData.getServerVersion() + ". Binlog version: "
								+ formatData.getBinlogVersion());
						break;
					case GTID:
						// GtidEventData gtidData = (GtidEventData) event.getData();
						// System.out.println("gitid number: " + gtidData.getGtid());
						break;
					case WRITE_ROWS:
						break;
					case EXT_WRITE_ROWS:
						// if (!rowResult.getDatabaseName().equals("QKTX_DB")
						// || !rowResult.getTableName().equals("HHZ_TASK_CLICK")) {
						// break;
						// }
						rowResult.setBinLogPos(headerV4.getNextPosition());
						if (!ForrestDataConfig.filterMap
								.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
							BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
							break;
						}
						WriteRowsEventData writeData = (WriteRowsEventData) event.getData();
						BitSet columnSet = writeData.getIncludedColumns();
						List<Serializable[]> rows = writeData.getRows();
						List<Map<String, Object>> insertResultList = new ArrayList<Map<String, Object>>();
						// System.out.println("insert row values: ");
						// insert ... select
						// create table ... select
						// insert into table values(),(),(),...
						for (Serializable[] s : rows) {
							Map<String, Object> rowMap = new HashMap<String, Object>();
							rowMap.put(ForrestDataConfig.metaSqltypeName, "INSERT");
							rowMap.put(ForrestDataConfig.metaDatabaseName, rowResult.getDatabaseName());
							rowMap.put(ForrestDataConfig.metaTableName, rowResult.getTableName());
							rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
																															// pos点
							rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
							for (int i = 0; i < s.length; i++) {
								String columnName = null;
								if (columnSet.get(i)) {
									String key = FlowData2.getMetaDataMapKey(rowResult.getDatabaseName(),
											rowResult.getTableName(), i + 1);
									columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);
									// System.out.println(columnName);
								}
								String columnValue = null;
								if (s[i] instanceof byte[]) {
									// 处理text,blob类型
									byte[] b = (byte[]) s[i];
									columnValue = new String(b);
								} else {
									columnValue = (s[i] == "null" ? "null" : String.valueOf(s[i]));
								}
								rowMap.put(columnName, columnValue);
							}
							// test.toJsonStr(rowMap, header);
							insertResultList.add(rowMap);
						}
						flowData.toJsonStr(insertResultList);
						break;
					case UPDATE_ROWS:
						break;
					case EXT_UPDATE_ROWS:
						rowResult.setBinLogPos(headerV4.getNextPosition());
						if (!ForrestDataConfig.doUpdateData || !ForrestDataConfig.filterMap
								.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
							BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
							break;
						}
						UpdateRowsEventData updateData = (UpdateRowsEventData) event.getData();
						columnSet = updateData.getIncludedColumns();
						// BitSet beforeColumnSet = updateData.getIncludedColumnsBeforeUpdate();
						List<Map<String, Object>> updateResultList = new ArrayList<Map<String, Object>>();
						List<Entry<Serializable[], Serializable[]>> updateRows = updateData.getRows();
						for (Entry<Serializable[], Serializable[]> entry : updateRows) {
							Map<String, Object> rowMap = new HashMap<String, Object>();
							Map<String, String> afterMap = new HashMap<String, String>();
							Map<String, String> beforMap = new HashMap<String, String>();
							rowMap.put(ForrestDataConfig.metaSqltypeName, "UPDATE");
							rowMap.put(ForrestDataConfig.metaDatabaseName, rowResult.getDatabaseName());
							rowMap.put(ForrestDataConfig.metaTableName, rowResult.getTableName());
							rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
							rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
							Serializable[] beforValue = entry.getKey();
							Serializable[] afterValue = entry.getValue();
							for (int i = 0; i < beforValue.length; i++) {
								String columnName = null;
								if (columnSet.get(i)) {
									String key = FlowData2.getMetaDataMapKey(rowResult.getDatabaseName(),
											rowResult.getTableName(), i + 1);
									columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);
								}
								String columnValue = null;
								if (beforValue[i] instanceof byte[]) {
									// 处理text,blob类型
									byte[] b = (byte[]) beforValue[i];
									columnValue = new String(b);
								} else {
									columnValue = (beforValue[i] == "null" ? "null" : String.valueOf(beforValue[i]));
								}
								beforMap.put(columnName, columnValue);

								String aftercolumnValue = null;
								if (afterValue[i] instanceof byte[]) {
									// 处理text,blob类型
									byte[] b = (byte[]) afterValue[i];
									aftercolumnValue = new String(b);
								} else {
									aftercolumnValue = (afterValue[i] == "null" ? "null"
											: String.valueOf(afterValue[i]));
								}
								afterMap.put(columnName, aftercolumnValue);
							}
							rowMap.put(ForrestDataConfig.updateAfterName, afterMap);
							rowMap.put(ForrestDataConfig.updateBeforName, beforMap);
							updateResultList.add(rowMap);
						}
						flowData.toJsonStr(updateResultList);
						break;
					case DELETE_ROWS:
						break;
					case XID:
						// XidEventData xidData = (XidEventData) event.getData();
						// long xid = xidData.getXid();
						// System.out.println("XID number: " + xid);
						rowResult.setBinLogPos(headerV4.getNextPosition());
						BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
						break;
					case EXT_DELETE_ROWS:
						rowResult.setBinLogPos(headerV4.getNextPosition());

						if (!ForrestDataConfig.doDeleteData || !ForrestDataConfig.filterMap
								.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
							BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
							break;
						}
						DeleteRowsEventData deleteData = (DeleteRowsEventData) event.getData();
						columnSet = deleteData.getIncludedColumns();
						List<Serializable[]> deleteRows = deleteData.getRows();
						List<Map<String, Object>> deleteResultList = new ArrayList<Map<String, Object>>();
						for (Serializable[] s : deleteRows) {
							Map<String, Object> rowMap = new HashMap<String, Object>();
							rowMap.put(ForrestDataConfig.metaSqltypeName, "DELETE");
							rowMap.put(ForrestDataConfig.metaDatabaseName, rowResult.getDatabaseName());
							rowMap.put(ForrestDataConfig.metaTableName, rowResult.getTableName());
							rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
							rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
							for (int i = 0; i < s.length; i++) {
								// System.out.println("delete row values: " + s[i]);
								String columnName = null;
								if (columnSet.get(i)) {
									String key = FlowData2.getMetaDataMapKey(rowResult.getDatabaseName(),
											rowResult.getTableName(), i + 1);
									columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);
									// System.out.println(columnName);
								}
								String columnValue = null;
								if (s[i] instanceof byte[]) {
									// 处理text,blob类型
									byte[] b = (byte[]) s[i];
									columnValue = new String(b);
								} else {
									columnValue = (s[i] == "null" ? "null" : String.valueOf(s[i]));
								}
								rowMap.put(columnName, columnValue);
							}
							deleteResultList.add(rowMap);
						}
						flowData.toJsonStr(deleteResultList);
						break;
					case TABLE_MAP:
						TableMapEventData tableData = (TableMapEventData) event.getData();
						rowResult.setDatabaseName(tableData.getDatabase().toUpperCase());
						rowResult.setTableName(tableData.getTable().toUpperCase());
						rowResult.setBinLogPos(headerV4.getNextPosition());
						if (!ForrestDataConfig.filterMap
								.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
							BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
							break;
						}
						// System.out.println("schema info: " + tableData.getDatabase() + "." +
						// tableData.getTable());
						break;
					case QUERY:
						QueryEventData queryData = (QueryEventData) event.getData();
						String sql = queryData.getSql();
						// System.out.println("SQL INFO: " + sql);
						String ddlType = sql.trim().split(" ")[0].toUpperCase();
						if (ddlType.equals("CREATE") || ddlType.equals("ALTER") || ddlType.equals("DROP")) {
							config.getMetaDataInfo(); // 刷新数据库元数据
						}
						rowResult.setBinLogPos(headerV4.getNextPosition());
						BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
						// System.out.println(sql);
						break;
					case ROTATE:
						RotateEventData rotateData = (RotateEventData) event.getData();
						// System.out.println(
						// "ROATE: " + rotateData.getBinlogFilename() + " " +
						// rotateData.getBinlogPosition());
						rowResult.setBinLogFile(rotateData.getBinlogFilename());
						rowResult.setBinLogPos(rotateData.getBinlogPosition());
						BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rotateData.getBinlogFilename(),
								String.valueOf(rotateData.getBinlogPosition()));
						break;
					default:
						// System.out.println("Other type: " + header.getEventType().toString());
						break;
					}
				}
			});
			client.connect();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
