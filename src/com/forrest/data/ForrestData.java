package com.forrest.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.data.dest.impl.ForrestDataDestElasticSearch;
import com.forrest.data.dest.impl.ForrestDataDestFile;
import com.forrest.data.dest.impl.ForrestDataDestKafka;
import com.forrest.data.dest.impl.ForrestDataDestRabbitMQ;
import com.forrest.data.dest.impl.ForrestDataDestRedis;
import com.forrest.data.dest.impl.ForrestDataDestStdout;
import com.forrest.data.load.ForrestLoadHistoryData;
import com.forrest.data.queue.QueueConsumerThread;
import com.forrest.http.ForrestHttpServer;
import com.forrest.monitor.ForrestMonitor;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeader;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.FormatDescriptionEventData;
import com.github.shyiko.mysql.binlog.event.GtidEventData;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.RotateEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;

public class ForrestData {
	private static Logger logger = Logger.getLogger(ForrestData.class);
	private BlockingQueue<List<Map<String, Object>>> queue;

	public ForrestData(BlockingQueue<List<Map<String, Object>>> queue) {
		this.queue = queue;
	}

	public void setGtid(BinaryLogClient client, ForrestDataConfig config, RowResult rowResult) {
		if (config.getGtidMap() != null && config.getGtidMap().size() > 0) {
			StringBuffer gtid = new StringBuffer();
			int i = 0;
			Map<String, String> gtidMap = config.getGtidMap();
			int size = gtidMap.size();
			for (Map.Entry<String, String> entry : gtidMap.entrySet()) {
				i++;
				gtid.append(entry.getKey()).append(":").append(entry.getValue());
				if (i < size) {
					gtid.append(",");
				}
			}
			client.setGtidSet(gtid.toString());
			rowResult.setGtidMap(gtidMap);
		} else {

			// client.setGtidSet(config.getGetServerUUID() + ":1-1");
			logger.warn(
					"master Executed_Gtid_Set is empty. set default gtid = " + ForrestDataConfig.defaultGTID + ":1-1");
			client.setGtidSet(ForrestDataConfig.defaultGTID + ":1-1");
		}
	}

	public void startForrestData(BinaryLogClient client, ForrestDataConfig config, RowResult rowResult,
			ForrestMonitor forrestMonitor) {
		if (config.getGtidEnable()) {
			setGtid(client, config, rowResult);
		} else {
			if (config.getBinlogFileName().length() == 0 || config.getBinlogCacheFileName() == null) { // fd.mysql.binlog.log.file.name参数不给值，从最新的binlog位置开始
				client.setBinlogFilename(null);
			}
		}
		try {

			client.registerEventListener(new EventListener() {

				@Override
				public void onEvent(Event event) {
					EventHeader header = event.getHeader();
					BitSet columnSet = null;

					switch (header.getEventType()) {
					case FORMAT_DESCRIPTION:
						FormatDescriptionEventData formatData = (FormatDescriptionEventData) event.getData();
						logger.info("Server version:" + formatData.getServerVersion() + ". Binlog version: "
								+ formatData.getBinlogVersion() + ". binary log file: " + client.getBinlogFilename()
								+ ". binary log position: " + client.getBinlogPosition());
						rowResult.setBinLogFile(client.getBinlogFilename());
						rowResult.setBinLogPos(client.getBinlogPosition());
						// if (config.getGtidEnable()) {
						// }
						forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(), rowResult.getBinLogPos(),
								rowResult.getGtidMap());
						break;
					case GTID:
						if (config.getGtidEnable()) {
							GtidEventData gtidData = (GtidEventData) event.getData();
							rowResult.addToGtidMap(gtidData.getGtid());
						}
						break;
					case QUERY:
						QueryEventData queryData = (QueryEventData) event.getData();
						String sql = queryData.getSql();
						String ddlType = sql.trim().split(" ")[0].toUpperCase();
						if (ddlType.equals("CREATE") || ddlType.equals("ALTER") || ddlType.equals("DROP")) {
							List<Map<String, Object>> ddlResultList = parseExtQueryRowEvent(event, rowResult,
									config.getGtidEnable());
							if (ddlResultList != null) {
								try {
									queue.put(ddlResultList);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}

							// 重新加载表名称，字段名称，字段顺序
							config.getMetaDataInfo();

						}

						break;
					case TABLE_MAP:
						TableMapEventData tableData = (TableMapEventData) event.getData();
						rowResult.setDatabaseName(tableData.getDatabase().toUpperCase());
						rowResult.setTableName(tableData.getTable().toUpperCase());
						if (!ForrestDataConfig.filterMap
								.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
							break;
						}
						break;
					case WRITE_ROWS:
						parseInsertRowEvent(event, rowResult, forrestMonitor, config.getGtidEnable());
						break;
					case EXT_WRITE_ROWS:
						parseInsertRowEvent(event, rowResult, forrestMonitor, config.getGtidEnable());
						break;
					case UPDATE_ROWS:
						parseUpdateRowEvent(event, rowResult, columnSet, forrestMonitor, config.getGtidEnable());
						break;
					case EXT_UPDATE_ROWS:
						parseUpdateRowEvent(event, rowResult, columnSet, forrestMonitor, config.getGtidEnable());
						break;
					case DELETE_ROWS:
						parseDeleteRowEvent(event, rowResult, columnSet, forrestMonitor, config.getGtidEnable());
						break;
					case EXT_DELETE_ROWS:
						parseDeleteRowEvent(event, rowResult, columnSet, forrestMonitor, config.getGtidEnable());
						break;
					case XID:
						break;
					case ROTATE:
						RotateEventData rotateData = (RotateEventData) event.getData();
						rowResult.setBinLogFile(rotateData.getBinlogFilename());
						rowResult.setBinLogPos(rotateData.getBinlogPosition());
						// if (config.getGtidEnable()) {
						// rowResult.setGtid(client.getGtidSet());
						// }
						forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(), rowResult.getBinLogPos(),
								rowResult.getGtidMap());
						break;
					default:
						break;
					}
				}
			});
			client.connect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void parseGtidEvent() {

	}

	public void parseTableMapEvent() {

	}

	public void parseInsertRowEvent(Event event, RowResult rowResult, ForrestMonitor forrestMonitor,
			boolean gtidEnable) {
		List<Map<String, Object>> insertResultList = parseExtWriteRowEvent(event, rowResult, gtidEnable);
		if (insertResultList != null) {
			try {
				queue.put(insertResultList);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(), rowResult.getBinLogPos(),
					rowResult.getGtidMap());
		}
	}

	public void parseUpdateRowEvent(Event event, RowResult rowResult, BitSet columnSet, ForrestMonitor forrestMonitor,
			boolean gtidEnable) {
		List<Map<String, Object>> updateResultList = parseExtUpdateRowEvent(event, rowResult, columnSet, gtidEnable);
		if (updateResultList != null) {
			try {
				queue.put(updateResultList);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(), rowResult.getBinLogPos(),
					rowResult.getGtidMap());
		}
	}

	public void parseDeleteRowEvent(Event event, RowResult rowResult, BitSet columnSet, ForrestMonitor forrestMonitor,
			boolean gtidEnable) {
		List<Map<String, Object>> deleteResultList = parseExtDeleteRowEvent(event, rowResult, columnSet, gtidEnable);
		if (deleteResultList != null) {
			try {
				queue.put(deleteResultList);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(), rowResult.getBinLogPos(),
					rowResult.getGtidMap());
		}
	}

	List<Map<String, Object>> parseExtWriteRowEvent(Event event, RowResult rowResult, boolean gtidEnable) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());

		String databaseName = rowResult.getDatabaseName();
		String tableName = rowResult.getTableName();

		String tableKey = ForrestDataUtil.getMetaDataMapKey(databaseName, tableName);
		if (!ForrestDataConfig.filterMap.containsKey(tableKey)) {
			return null;
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
			rowMap.put(ForrestDataConfig.metaDatabaseName, databaseName);
			rowMap.put(ForrestDataConfig.metaTableName, tableName);
			rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
																											// pos点
			rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
			if (gtidEnable) {
				HashMap<String, String> gtidMap = new HashMap<String, String>();
				gtidMap.putAll(rowResult.getGtidMap());
				rowMap.put(ForrestDataConfig.metaGTIDName, gtidMap);
			}
			for (int i = 0; i < s.length; i++) {
				String columnName = null;
				if (columnSet.get(i)) {
					String key = ForrestDataUtil.getMetaDataMapKey(databaseName, tableName, i + 1);
					columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);

					// ignore table column
					if (ForrestDataConfig.ignoreTableColumnMap.containsKey(tableKey)) {
						if (ForrestDataConfig.ignoreTableColumnMap.get(tableKey).contains(columnName)) {
							continue;
						}
					}

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
			insertResultList.add(rowMap);
		}
		return insertResultList;
	}

	List<Map<String, Object>> parseExtUpdateRowEvent(Event event, RowResult rowResult, BitSet columnSet,
			boolean gtidEnable) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());

		String databaseName = rowResult.getDatabaseName();
		String tableName = rowResult.getTableName();

		String tableKey = ForrestDataUtil.getMetaDataMapKey(databaseName, tableName);

		if (!ForrestDataConfig.doUpdateData || !ForrestDataConfig.filterMap.containsKey(tableKey)) {
			return null;
		}
		UpdateRowsEventData updateData = (UpdateRowsEventData) event.getData();
		columnSet = updateData.getIncludedColumns();
		List<Map<String, Object>> updateResultList = new ArrayList<Map<String, Object>>();
		List<Entry<Serializable[], Serializable[]>> updateRows = updateData.getRows();
		for (Entry<Serializable[], Serializable[]> entry : updateRows) {
			Map<String, Object> rowMap = new HashMap<String, Object>();
			Map<String, String> afterMap = new HashMap<String, String>();
			Map<String, String> beforMap = new HashMap<String, String>();
			rowMap.put(ForrestDataConfig.metaSqltypeName, "UPDATE");
			rowMap.put(ForrestDataConfig.metaDatabaseName, databaseName);
			rowMap.put(ForrestDataConfig.metaTableName, tableName);
			rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
			rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
			if (gtidEnable) {
				HashMap<String, String> gtidMap = new HashMap<String, String>();
				gtidMap.putAll(rowResult.getGtidMap());
				rowMap.put(ForrestDataConfig.metaGTIDName, gtidMap);
			}
			Serializable[] beforValue = entry.getKey();
			Serializable[] afterValue = entry.getValue();
			for (int i = 0; i < beforValue.length; i++) {
				String columnName = null;
				if (columnSet.get(i)) {
					String key = ForrestDataUtil.getMetaDataMapKey(databaseName, tableName, i + 1);
					columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);

					if (ForrestDataConfig.ignoreTableColumnMap.containsKey(tableKey)) { // ignore table column
						if (ForrestDataConfig.ignoreTableColumnMap.get(tableKey).contains(columnName)) {
							continue;
						}
					}
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
					aftercolumnValue = (afterValue[i] == "null" ? "null" : String.valueOf(afterValue[i]));
				}
				afterMap.put(columnName, aftercolumnValue);
			}
			rowMap.put(ForrestDataConfig.updateAfterName, afterMap);
			rowMap.put(ForrestDataConfig.updateBeforName, beforMap);
			updateResultList.add(rowMap);
		}
		return updateResultList;
	}

	List<Map<String, Object>> parseExtDeleteRowEvent(Event event, RowResult rowResult, BitSet columnSet,
			boolean gtidEnable) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());

		String databaseName = rowResult.getDatabaseName();
		String tableName = rowResult.getTableName();

		String tableKey = ForrestDataUtil.getMetaDataMapKey(databaseName, tableName);

		if (!ForrestDataConfig.doDeleteData || !ForrestDataConfig.filterMap.containsKey(tableKey)) {
			return null;
		}
		DeleteRowsEventData deleteData = (DeleteRowsEventData) event.getData();
		columnSet = deleteData.getIncludedColumns();
		List<Serializable[]> deleteRows = deleteData.getRows();
		List<Map<String, Object>> deleteResultList = new ArrayList<Map<String, Object>>();
		for (Serializable[] s : deleteRows) {
			Map<String, Object> rowMap = new HashMap<String, Object>();
			rowMap.put(ForrestDataConfig.metaSqltypeName, "DELETE");
			rowMap.put(ForrestDataConfig.metaDatabaseName, databaseName);
			rowMap.put(ForrestDataConfig.metaTableName, tableName);
			rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
			rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
			if (gtidEnable) {
				HashMap<String, String> gtidMap = new HashMap<String, String>();
				gtidMap.putAll(rowResult.getGtidMap());
				rowMap.put(ForrestDataConfig.metaGTIDName, gtidMap);
			}
			for (int i = 0; i < s.length; i++) {
				String columnName = null;
				if (columnSet.get(i)) {
					String key = ForrestDataUtil.getMetaDataMapKey(databaseName, tableName, i + 1);
					columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);

					if (ForrestDataConfig.ignoreTableColumnMap.containsKey(tableKey)) { // ignore table column
						if (ForrestDataConfig.ignoreTableColumnMap.get(tableKey).contains(columnName)) {
							continue;
						}
					}

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
		return deleteResultList;
	}

	List<Map<String, Object>> parseExtQueryRowEvent(Event event, RowResult rowResult, boolean gtidEnable) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());
		List<Map<String, Object>> ddlResultList = new ArrayList<Map<String, Object>>();
		Map<String, Object> rowMap = new HashMap<String, Object>();
		rowMap.put(ForrestDataConfig.metaSqltypeName, "DDL");
		rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
		rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
		if (gtidEnable) {
			HashMap<String, String> gtidMap = new HashMap<String, String>();
			gtidMap.putAll(rowResult.getGtidMap());
			rowMap.put(ForrestDataConfig.metaGTIDName, gtidMap);
		}
		ddlResultList.add(rowMap);
		return ddlResultList;
	}

	public void addShutdownHook(QueueConsumerThread thread) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("begin to stop forrest consumer...");
				thread.setAlived(false);
				Long currentTime = System.currentTimeMillis();
				while (System.currentTimeMillis() - currentTime < 30 * 1000) {
					if (thread.getState() == State.TERMINATED) {
						logger.info("stop forrest consumer success.");
						return;
					}
					try {
						this.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				logger.warn("forrest consumer stop failed,force to close forrest data.");
			}
		});

	}

	public void loadHistoryData(ForrestDataConfig config, ForrestDataDestination dest) {
		new ForrestLoadHistoryData(queue, config);
		dest.saveBinlogPos(config.getBinlogFileName(), String.valueOf(config.getBinlogPostion()), config.getGtidMap());
	}

	public void addMonitorInfo(ForrestMonitor forrestMonitor, ForrestDataConfig config) {
		forrestMonitor.getMonitorMap().put("destination_data_source", config.getDsType());
		forrestMonitor.getMonitorMap().put("mysql_server", config.getMysqlHost());
		forrestMonitor.getMonitorMap().put("mysql_user", config.getMysqlUser());
		forrestMonitor.getMonitorMap().put("mysql_port", config.getMysqlPort());
		forrestMonitor.getMonitorMap().put("mysql_server_id", config.getMysqlServerID());
		forrestMonitor.getMonitorMap().put("mysql_gtid_enable", config.getGtidEnable());
		forrestMonitor.getMonitorMap().put("do_update", ForrestDataConfig.doUpdateData);
		forrestMonitor.getMonitorMap().put("do_delete", ForrestDataConfig.doDeleteData);
		forrestMonitor.getMonitorMap().put("queue_length", config.getQueueLength());

		forrestMonitor.getMonitorMap().put("do_table", config.getReplicaDBTables());
		forrestMonitor.getMonitorMap().put("load_history_data", config.isLoadHistoryData());
		forrestMonitor.getMonitorMap().put("binlog_cache_file", config.getBinlogCacheFileName());

		forrestMonitor.getMonitorMap().put("meta_data_tablename", ForrestDataConfig.metaTableName);
		forrestMonitor.getMonitorMap().put("meta_data_databasename", ForrestDataConfig.metaDatabaseName);
		forrestMonitor.getMonitorMap().put("meta_data_binlogfilename", ForrestDataConfig.metaBinLogFileName);
		forrestMonitor.getMonitorMap().put("meta_data_binlogposition", ForrestDataConfig.metaBinlogPositionName);
		forrestMonitor.getMonitorMap().put("meta_data_sqltype", ForrestDataConfig.metaSqltypeName);
		forrestMonitor.getMonitorMap().put("meta_data_gtidname", ForrestDataConfig.metaGTIDName);
		forrestMonitor.getMonitorMap().put("update_beforname", ForrestDataConfig.updateBeforName);
		forrestMonitor.getMonitorMap().put("update_aftername", ForrestDataConfig.updateAfterName);
	}

	public static void main(String[] args) {
		logger.info("start to forrest data...");
		ForrestDataConfig config = new ForrestDataConfig();
		config.initConfig();

		int serverID = config.getMysqlServerID();

		String host = config.getMysqlHost();
		int port = config.getMysqlPort();
		String user = config.getMysqlUser();
		String password = config.getMysqlPasswd();
		// String dbname = config.getMysqlDBname();

		BlockingQueue<List<Map<String, Object>>> queue = new LinkedBlockingQueue<>(config.getQueueLength());

		ForrestData forrestData = new ForrestData(queue);
		ForrestMonitor forrestMonitor = new ForrestMonitor();

		ForrestDataDestination dest = null;
		QueueConsumerThread consumer = null;
		switch (config.getDsType()) {
		case "REDIS":
			dest = new ForrestDataDestRedis(config, forrestMonitor);
			break;
		case "STDOUT":
			dest = new ForrestDataDestStdout(config, forrestMonitor);
			break;
		case "FILE":
			dest = new ForrestDataDestFile(config, forrestMonitor);
			break;
		case "RABBITMQ":
			dest = new ForrestDataDestRabbitMQ(config, forrestMonitor);
			break;
		case "ELASTICSEARCH":
			dest = new ForrestDataDestElasticSearch(config, forrestMonitor);
			break;
		case "KAFKA":
			dest = new ForrestDataDestKafka(config, forrestMonitor);
			break;
		default:
			logger.error("unknow fd.ds.type=" + config.getDsType());
			System.exit(1);
			break;
		}
		logger.info("destination is " + config.getDsType());
		forrestData.addMonitorInfo(forrestMonitor, config);

		consumer = new QueueConsumerThread(queue, dest, true);
		consumer.start();

		RowResult rowResult = RowResult.getInstance();

		// fd.load.history.data=true且binlogFileName为空，则加载历史数据
		if (config.isLoadHistoryData()) {
			if (config.getGtidEnable()) {
				if (config.getGtidMap() == null || config.getGtidMap().size() == 0) {
					forrestData.loadHistoryData(config, dest);
				}
			} else {
				if (config.getBinlogFileName().length() == 0)
					forrestData.loadHistoryData(config, dest);
			}
		} else {
			if (config.getGtidEnable()) {
				if (config.getGtidMap() == null || config.getGtidMap().size() == 0) {
					ForrestLoadHistoryData forrestloadHistData = new ForrestLoadHistoryData(config);
					forrestloadHistData.fetchCurrentBinlogPosition();
				}
			}
		}

		String binLogFile = config.getBinlogFileName();
		long binLogPos = config.getBinlogPostion();

		BinaryLogClient client = new BinaryLogClient(host, port, serverID, user, password, binLogFile, binLogPos, null);

		ForrestHttpServer forrestHttpServer = new ForrestHttpServer(config.getHttpServerHost(),
				config.getHttpServerPort(), forrestMonitor);
		forrestHttpServer.start();

		forrestData.addShutdownHook(consumer);
		forrestData.startForrestData(client, config, rowResult, forrestMonitor);
	}

}
