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

import com.alibaba.fastjson.JSON;
import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.data.dest.impl.ForrestDataDestElasticSearch;
import com.forrest.data.dest.impl.ForrestDataDestFile;
import com.forrest.data.dest.impl.ForrestDataDestRabbitMQ;
import com.forrest.data.dest.impl.ForrestDataDestRedis;
import com.forrest.data.dest.impl.ForrestDataDestStdout;
import com.forrest.data.file.io.BinlogPosProcessor;
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

	public void toJsonStr(List<Map<String, Object>> rowResultList) {
		// String jsonString = JSON.toJSONString(rowResultList);
		for (Map<String, Object> row : rowResultList) {
			System.out.println(JSON.toJSONString(row));
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile((String) row.get(ForrestDataConfig.metaBinLogFileName),
					(String) row.get(ForrestDataConfig.metaBinlogPositionName));
		}
	}

	public void startForrestData(BinaryLogClient client, ForrestDataConfig config, RowResult rowResult,
			ForrestMonitor forrestMonitor) {
		if (config.getBinlogFileName().length() == 0 || config.getBinlogCacheFileName() == null) { // fd.mysql.binlog.log.file.name参数不给值，从最新的binlog位置开始
			client.setBinlogFilename(null);
		}
		try {

			client.registerEventListener(new EventListener() {

				@Override
				public void onEvent(Event event) {
					EventHeader header = event.getHeader();
					// EventHeaderV4 headerV4 = (EventHeaderV4) header;
					BitSet columnSet = null;
					// System.out.println(header1.getPosition());
					// System.out.println(header1.getNextPosition());

					switch (header.getEventType()) {
					case FORMAT_DESCRIPTION:
						FormatDescriptionEventData formatData = (FormatDescriptionEventData) event.getData();
						// System.out.println("Server version:" + formatData.getServerVersion() + ".
						// Binlog version: "
						// + formatData.getBinlogVersion());
						logger.info("Server version:" + formatData.getServerVersion() + ". Binlog version: "
								+ formatData.getBinlogVersion());
						rowResult.setBinLogFile(client.getBinlogFilename());
						rowResult.setBinLogPos(client.getBinlogPosition());
						BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
						forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(),
								String.valueOf(rowResult.getBinLogPos()));
						break;
					case GTID:
						// GtidEventData gtidData = (GtidEventData) event.getData();
						// System.out.println("gitid number: " + gtidData.getGtid());
						break;
					case QUERY:
						QueryEventData queryData = (QueryEventData) event.getData();
						String sql = queryData.getSql();
						// System.out.println("SQL INFO: " + sql);

						String ddlType = sql.trim().split(" ")[0].toUpperCase();
						if (ddlType.equals("CREATE") || ddlType.equals("ALTER") || ddlType.equals("DROP")) {
							List<Map<String, Object>> ddlResultList = parseExtQueryRowEvent(event, rowResult);
							if (ddlResultList != null) {
								try {
									queue.put(ddlResultList);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}

						// rowResult.setBinLogPos(headerV4.getNextPosition());
						// BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
						// System.out.println(sql);
						break;
					case TABLE_MAP:
						TableMapEventData tableData = (TableMapEventData) event.getData();
						rowResult.setDatabaseName(tableData.getDatabase().toUpperCase());
						rowResult.setTableName(tableData.getTable().toUpperCase());
						// rowResult.setBinLogPos(headerV4.getNextPosition());
						if (!ForrestDataConfig.filterMap
								.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
							// BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
							break;
						}

						// System.out.println("schema info: " + tableData.getDatabase() + "." +
						// tableData.getTable());
						break;
					case WRITE_ROWS:
						break;
					case EXT_WRITE_ROWS:
						List<Map<String, Object>> insertResultList = parseExtWriteRowEvent(event, rowResult);
						if (insertResultList != null) {
							// dest.deliverDest(insertResultList);
							try {
								queue.put(insertResultList);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(),
									String.valueOf(rowResult.getBinLogPos()));

						}
						break;
					case UPDATE_ROWS:
						break;
					case EXT_UPDATE_ROWS:
						List<Map<String, Object>> updateResultList = parseExtUpdateRowEvent(event, rowResult,
								columnSet);
						if (updateResultList != null) {
							// dest.deliverDest(updateResultList);
							try {
								queue.put(updateResultList);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(),
									String.valueOf(rowResult.getBinLogPos()));
						}
						break;
					case DELETE_ROWS:
						break;
					case EXT_DELETE_ROWS:
						List<Map<String, Object>> deleteResultList = parseExtDeleteRowEvent(event, rowResult,
								columnSet);
						if (deleteResultList != null) {
							// dest.deliverDest(deleteResultList);
							try {
								queue.put(deleteResultList);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(),
									String.valueOf(rowResult.getBinLogPos()));
						}
						break;
					case XID:
						// XidEventData xidData = (XidEventData) event.getData();
						// long xid = xidData.getXid();
						// System.out.println("XID number: " + xid);
						// rowResult.setBinLogPos(headerV4.getNextPosition());
						// BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
						break;
					case ROTATE:
						RotateEventData rotateData = (RotateEventData) event.getData();
						// System.out.println(
						// "ROATE: " + rotateData.getBinlogFilename() + " " +
						// rotateData.getBinlogPosition());
						rowResult.setBinLogFile(rotateData.getBinlogFilename());
						rowResult.setBinLogPos(rotateData.getBinlogPosition());
						forrestMonitor.putReadBinlogInfo(rowResult.getBinLogFile(),
								String.valueOf(rowResult.getBinLogPos()));
						// BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rotateData.getBinlogFilename(),
						// String.valueOf(rotateData.getBinlogPosition()));
						break;
					default:
						// System.out.println("Other type: " + header.getEventType().toString());
						break;
					}
				}
			});
			client.connect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	List<Map<String, Object>> parseExtWriteRowEvent(Event event, RowResult rowResult) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());
		if (!ForrestDataConfig.filterMap.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
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
			rowMap.put(ForrestDataConfig.metaDatabaseName, rowResult.getDatabaseName());
			rowMap.put(ForrestDataConfig.metaTableName, rowResult.getTableName());
			rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
																											// pos点
			rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
			for (int i = 0; i < s.length; i++) {
				String columnName = null;
				if (columnSet.get(i)) {
					String key = ForrestDataUtil.getMetaDataMapKey(rowResult.getDatabaseName(),
							rowResult.getTableName(), i + 1);
					columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);
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

	List<Map<String, Object>> parseExtUpdateRowEvent(Event event, RowResult rowResult, BitSet columnSet) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());
		if (!ForrestDataConfig.doUpdateData || !ForrestDataConfig.filterMap
				.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
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
			rowMap.put(ForrestDataConfig.metaDatabaseName, rowResult.getDatabaseName());
			rowMap.put(ForrestDataConfig.metaTableName, rowResult.getTableName());
			rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
			rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
			Serializable[] beforValue = entry.getKey();
			Serializable[] afterValue = entry.getValue();
			for (int i = 0; i < beforValue.length; i++) {
				String columnName = null;
				if (columnSet.get(i)) {
					String key = ForrestDataUtil.getMetaDataMapKey(rowResult.getDatabaseName(),
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

	List<Map<String, Object>> parseExtDeleteRowEvent(Event event, RowResult rowResult, BitSet columnSet) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());

		if (!ForrestDataConfig.doDeleteData || !ForrestDataConfig.filterMap
				.containsKey(rowResult.getDatabaseName() + "." + rowResult.getTableName())) {
			BinlogPosProcessor.saveCurrentBinlogPosToCacheFile(rowResult);
			return null;
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
				String columnName = null;
				if (columnSet.get(i)) {
					String key = ForrestDataUtil.getMetaDataMapKey(rowResult.getDatabaseName(),
							rowResult.getTableName(), i + 1);
					columnName = ForrestDataConfig.sourceMySQLMetaDataMap.get(key);
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

	List<Map<String, Object>> parseExtQueryRowEvent(Event event, RowResult rowResult) {
		EventHeader header = event.getHeader();
		EventHeaderV4 headerV4 = (EventHeaderV4) header;
		rowResult.setBinLogPos(headerV4.getNextPosition());
		List<Map<String, Object>> ddlResultList = new ArrayList<Map<String, Object>>();
		Map<String, Object> rowMap = new HashMap<String, Object>();
		rowMap.put(ForrestDataConfig.metaSqltypeName, "DDL");
		// rowMap.put(ForrestDataConfig.metaDatabaseName, rowResult.getDatabaseName());
		// rowMap.put(ForrestDataConfig.metaTableName, rowResult.getTableName());
		rowMap.put(ForrestDataConfig.metaBinlogPositionName, String.valueOf(rowResult.getBinLogPos())); // 下一个binlog
		rowMap.put(ForrestDataConfig.metaBinLogFileName, rowResult.getBinLogFile());
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
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				logger.warn("forrest consumer stop failed,force to close forrest data.");
			}
		});

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
		default:
			logger.error("unknow fd.ds.type=" + config.getDsType());
			System.exit(1);
			break;
		}
		logger.info("destination is " + config.getDsType());
		forrestMonitor.getMonitorMap().put("destination_data_source", config.getDsType());

		consumer = new QueueConsumerThread(queue, dest, true);
		consumer.start();

		if (config.isLoadHistoryData() && config.getBinlogFileName().length() == 0) {
			new ForrestLoadHistoryData(queue, config, consumer);
			dest.saveBinlogPos(config.getBinlogFileName(), String.valueOf(config.getBinlogPostion()));
		}

		String binLogFile = config.getBinlogFileName();
		long binLogPos = config.getBinlogPostion();

		RowResult rowResult = RowResult.getInstance();
		// rowResult.setBinLogFile(config.getBinlogFileName());

		BinaryLogClient client = new BinaryLogClient(host, port, serverID, user, password, binLogFile, binLogPos, null);

		// ForrestMonitor forrestMonitor = new ForrestMonitor(rowResult);
		ForrestHttpServer forrestHttpServer = new ForrestHttpServer(config.getHttpServerPort(), forrestMonitor);
		forrestHttpServer.start();

		forrestData.addShutdownHook(consumer);
		forrestData.startForrestData(client, config, rowResult, forrestMonitor);
	}

}
