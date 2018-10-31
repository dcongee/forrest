package com.forrest.data.load;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import com.forrest.data.ForrestDataUtil;
import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.queue.QueueConsumerThread;

public class ForrestLoadHistoryData {
	private static Logger logger = Logger.getLogger(ForrestLoadHistoryData.class);

	private BlockingQueue<List<Map<String, Object>>> queue;
	private ForrestDataConfig config;
	QueueConsumerThread consumer;

	public ForrestLoadHistoryData(BlockingQueue<List<Map<String, Object>>> queue, ForrestDataConfig config,
			QueueConsumerThread consumer) {
		this.queue = queue;
		this.config = config;
		this.consumer = consumer;

		Connection con = config.getConnection();
		if (!loadHistoryData(con)) {
			logger.error("load data failed.");
			// queue.clear();
			// consumer.currentThread().interrupt();
			System.exit(1);
		}
	}

	public boolean loadHistoryData(Connection con) {
		try {
			if (con.isClosed()) {
				con = config.getConnection();
			}

			if (!flushTablesAndGetTableLock(con)) {
				return false;
			}

			if (!startConsistentSnapshotTransaction(con)) {
				return false;
			}

			if (!getCurrentBinlogPosition(con)) {
				return false;
			}

			if (!unlockTables(con)) {
				return false;
			}

			if (!load(con)) {
				return false;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		} finally {
			config.close(con);
		}

		return true;

	}

	public boolean flushTablesAndGetTableLock(Connection con) {
		PreparedStatement ps = null;
		String sql = "FLUSH /*!40101 LOCAL */ TABLES";
		try {
			ps = con.prepareStatement(sql);
			ps.execute();
			config.close(ps);

			sql = "FLUSH TABLES WITH READ LOCK";
			ps = con.prepareStatement(sql);
			ps.setQueryTimeout(30);
			ps.execute();
		} catch (SQLException e) {
			config.close(con);
			logger.error("sql exec failed: " + sql);
			e.printStackTrace();
			return false;
		} finally {
			config.close(ps);
		}
		return true;

	}

	public boolean startConsistentSnapshotTransaction(Connection con) {
		PreparedStatement ps = null;
		String sql = "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ";
		try {
			ps = con.prepareStatement(sql);
			ps.execute();
			config.close(ps);

			con.setAutoCommit(false);

			sql = "START TRANSACTION /*!40100 WITH CONSISTENT SNAPSHOT */";
			ps = con.prepareStatement(sql);
			ps.execute();
		} catch (SQLException e) {
			config.close(con);
			logger.error("sql exec failed: " + sql);
			e.printStackTrace();
			return false;
		} finally {
			config.close(ps);
		}
		return true;

	}

	public boolean getCurrentBinlogPosition(Connection con) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		String sql = "SHOW MASTER STATUS";
		try {
			ps = con.prepareStatement(sql);
			rs = ps.executeQuery();

			while (rs.next()) {
				config.setBinlogFileName(rs.getString("File"));
				config.setBinlogPostion(rs.getLong("Position"));
			}

			logger.info("current binlog postion:" + config.getBinlogFileName() + ":" + config.getBinlogPostion());

		} catch (SQLException e) {
			config.close(con);
			logger.error("sql exec failed: " + sql);
			e.printStackTrace();
			return false;
		} finally {
			config.close(rs);
			config.close(ps);
		}
		return true;
	}

	public boolean unlockTables(Connection con) {
		PreparedStatement ps = null;
		String sql = "UNLOCK TABLES";
		try {
			ps = con.prepareStatement(sql);
			ps.execute();
		} catch (SQLException e) {
			config.close(con);
			logger.error("sql exec failed: " + sql);
			e.printStackTrace();
			return false;
		} finally {
			config.close(ps);
		}
		return true;

	}

	public boolean load(Connection con) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		String sql = "select * from ";
		Map<String, String> filterMap = ForrestDataConfig.filterMap;
		String binlogPostion = String.valueOf(config.getBinlogPostion());

		try {
			for (Map.Entry<String, String> entry : filterMap.entrySet()) {
				Set<String> tableColumnSet = new HashSet<String>();
				logger.info("load data for table : " + entry.getKey());
				sql = "SAVEPOINT sp";
				ps = con.prepareStatement(sql);
				ps.execute();
				config.close(ps);

				String[] databaseTableKeys = ForrestDataUtil.getDatabaseNameAndTableNameFromKey(entry.getKey());

				sql = "show fields from " + entry.getKey();
				ps = con.prepareStatement(sql);
				rs = ps.executeQuery();

				while (rs.next()) {
					tableColumnSet.add(rs.getString("Field").toUpperCase());
				}
				config.close(ps);
				config.close(rs);

				sql = "select * from " + entry.getKey();
				ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				ps.setFetchSize(Integer.MIN_VALUE);
				// ps.setFetchSize(5);
				rs = ps.executeQuery();
				List<Map<String, Object>> rowList = new ArrayList<Map<String, Object>>();
				while (rs.next()) {

					Map<String, Object> rowMap = new HashMap<String, Object>();
					for (String columnName : tableColumnSet) {
						String columnValue = rs.getString(columnName);
						rowMap.put(columnName, columnValue);
						
						
						// if (entry.getKey().equals("WUHP.T5")) {
						// System.out.println(columnName + " " + columnValue);
						// }

						// int columnIndex = rs.findColumn(columnName);
						// System.out.println(rs.getMetaData().getColumnTypeName(columnIndex));
						// if (columnName.toLowerCase().equals("t_date")) {
						// System.out.println(columnValue);
						// }
					}
					rowMap.put(ForrestDataConfig.metaBinLogFileName, config.getBinlogFileName());
					rowMap.put(ForrestDataConfig.metaBinlogPositionName, binlogPostion);
					rowMap.put(ForrestDataConfig.metaDatabaseName, databaseTableKeys[0]);
					rowMap.put(ForrestDataConfig.metaTableName, databaseTableKeys[1]);
					rowMap.put(ForrestDataConfig.metaSqltypeName, "INSERT");

					rowList.add(rowMap);
					if (rowList.size() == 100) {
						try {
							queue.put(rowList);
							rowList = new ArrayList<Map<String, Object>>();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}

				try {
					if (rowList.size() != 0) {
						queue.put(rowList);
						rowList = new ArrayList<Map<String, Object>>();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				sql = "ROLLBACK TO SAVEPOINT sp";
				ps = con.prepareStatement(sql); // 释放表DDL锁
				ps.execute();
				config.close(ps);
				config.close(rs);

				sql = "RELEASE SAVEPOINT sp";
				ps = con.prepareStatement(sql); // 释放表DDL锁
				ps.execute();
				config.close(ps);
			}
			logger.info("load data for all tables successed.");
			con.rollback();
		} catch (SQLException e1) {
			config.close(con);
			logger.error("sql exec failed: " + sql);
			e1.printStackTrace();
			return false;
		} finally {
			config.close(con, ps, rs);
		}
		return true;
	}

	public static void main(String args[]) {
		Map<String, Object> map = new HashMap<String, Object>();
		String d = "0000-00-00";
		map.put("1", d);
		System.out.println(map.get("1"));
	}

}
