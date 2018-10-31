package com.forrest.data.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.forrest.data.ForrestDataUtil;
import com.forrest.data.file.io.BinlogPosProcessor;

public class ForrestDataConfig {

	private static Logger logger = Logger.getLogger(ForrestDataConfig.class);

	private String mysqlHost;
	private int mysqlPort;
	private String mysqlUser;
	private String mysqlPasswd;
	private String mysqlDBname;

	private int mysqlServerID;
	private String binlogFileName;
	private long binlogPostion;

	private String dsType;
	private boolean loadHistoryData = false;
	private int queueLength = 1024;

	private int httpServerPort = 808;

	private String binlogCacheFilePath;
	private String[] replicaDBTables;

	public static int binlogPosCharMaxLength = 96;
	private static final String DRIVER = "com.mysql.jdbc.Driver";

	public static String metaTableName = "TABLE_NAME";
	public static String metaDatabaseName = "DATABASE_NAME";
	public static String metaBinLogFileName = "BINLOG_FILE";
	public static String metaBinlogPositionName = "BINLOG_POS";
	public static String metaSqltypeName = "SQL_TYPE";

	public static boolean doUpdateData = false;
	public static boolean doDeleteData = false;

	public static String updateBeforName = "BEFOR_VALUE";
	public static String updateAfterName = "AFTER_VALUE";

	public static Map<String, String> sourceMySQLMetaDataMap = new HashMap<String, String>();
	public static Map<String, String> filterMap = new HashMap<String, String>();
	public static Map<String, List<String>> tablePrimary = new HashMap<String, List<String>>();

	public ForrestDataConfig() {

	}

	public Connection getConnection() {
		Connection conn = null;
		try {
			Class.forName(DRIVER);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		// &noDatetimeStringSync=true 可以将0000-00-00的日期转换成string，但毫秒与微秒会出现乱码
		// &zeroDateTimeBehavior=convertToNull

		try {
			conn = DriverManager.getConnection("jdbc:mysql://" + this.mysqlHost + ":" + this.mysqlPort + "/"
					+ this.mysqlDBname
					+ "?useCursorFetch=true&zeroDateTimeBehavior=convertToNull&verifyServerCertificate=false&useSSL=false",
					this.mysqlUser, this.mysqlPasswd);
		} catch (SQLException e) {
			// e.printStackTrace();
			logger.error(e.getMessage());
			System.exit(1);
		}
		return conn;
	}

	public void close(Connection con, PreparedStatement ps, ResultSet rs) {
		if (con != null) {
			try {
				con.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (ps != null) {
			try {
				ps.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void close(ResultSet rs) {

		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void close(PreparedStatement ps) {

		if (ps != null) {
			try {
				ps.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public void close(Connection con) {
		if (con != null) {
			try {
				con.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	/**
	 * 得到源mysql需要同步的所有meta data
	 * 
	 * @return
	 */
	public Map<String, String> getMetaDataInfo() {
		// grant replication slave,replication client,select on *.* to 'qktx_repl'@'%'
		// identified by 'qktx_repl';

		Connection con = this.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		StringBuffer sql = new StringBuffer();
		sql.append(
				"select upper(table_Schema) as table_schema,upper(table_name) as table_name,upper(column_name) as column_name,ORDINAL_POSITION from information_Schema.columns where ( 1=1 ) ");
		if (replicaDBTables.length != 0) {
			for (int i = 0; i < replicaDBTables.length; i++) {
				if (i == 0) {
					sql.append(" and  concat(table_schema,'.',table_name)  like '").append(replicaDBTables[i]);
				} else if (i == replicaDBTables.length - 1) {
					sql.append(" or  concat(table_schema,'.',table_name)  like  '").append(replicaDBTables[i]);
				} else {
					sql.append(" or concat(table_schema,'.',table_name)  like '").append(replicaDBTables[i]);
				}
				sql.append("'");
			}
		}
		// System.out.println(sql.toString());
		try {
			ps = con.prepareStatement(sql.toString());
			// ps.setString(1, DBNAME);
			rs = ps.executeQuery();
			if (sourceMySQLMetaDataMap != null) {
				sourceMySQLMetaDataMap = new HashMap<String, String>();
			}
			while (rs.next()) {
				String tableSchema = rs.getString("table_schema").toUpperCase();
				String tableName = rs.getString("table_name").toUpperCase();
				String key = ForrestDataUtil.getMetaDataMapKey(tableSchema, tableName, rs.getInt("ORDINAL_POSITION"));
				String value = rs.getString("column_name").toUpperCase();
				sourceMySQLMetaDataMap.put(key, value);
				filterMap.put(ForrestDataUtil.getMetaDataMapKey(tableSchema, tableName), "1");
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			System.exit(1);
		} finally {
			close(con, ps, rs);
		}
		return sourceMySQLMetaDataMap;
	}

	/**
	 * 得到源mysql需要同步的所有meta data
	 * 
	 * @return
	 */
	Map<String, String> getMetaDataInfo(String[] relicaDBTables) {
		// grant replication slave,replication client,select on *.* to 'qktx_repl'@'%'
		// identified by 'qktx_repl';
		Connection con = this.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		StringBuffer sql = new StringBuffer();
		sql.append(
				"select upper(table_Schema) as table_schema,upper(table_name) as table_name,upper(column_name) as column_name,ORDINAL_POSITION from information_Schema.columns where ( 1=1 ) ");
		if (relicaDBTables.length != 0) {
			for (int i = 0; i < relicaDBTables.length; i++) {
				if (i == 0) {
					sql.append(" and  concat(table_schema,'.',table_name)  like '").append(relicaDBTables[i]);
				} else if (i == relicaDBTables.length - 1) {
					sql.append("' or  concat(table_schema,'.',table_name)  like  '").append(relicaDBTables[i]);
				} else {
					sql.append("' or concat(table_schema,'.',table_name)  like '").append(relicaDBTables[i]);
				}
				sql.append("'");
			}
		}

		try {
			ps = con.prepareStatement(sql.toString());
			// ps.setString(1, DBNAME);
			rs = ps.executeQuery();
			if (sourceMySQLMetaDataMap != null) {
				sourceMySQLMetaDataMap = new HashMap<String, String>();
			}
			while (rs.next()) {
				String key = ForrestDataUtil.getMetaDataMapKey(rs.getString("table_schema"), rs.getString("table_name"),
						rs.getInt("ORDINAL_POSITION"));
				String value = rs.getString("column_name");
				sourceMySQLMetaDataMap.put(key, value);

				key = ForrestDataUtil.getMetaDataMapKey(rs.getString("table_schema"), rs.getString("table_name"));
				filterMap.put(key, value);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			close(con, ps, rs);
		}
		return sourceMySQLMetaDataMap;
	}

	/**
	 * 从information_Schema.KEY_COLUMN_USAGE表中获取各个表的主键，联合主键，按字段进行排序
	 */
	public void getTablePrimary() {
		// TODO Auto-generated method stub
		Connection con = this.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		StringBuffer sql = new StringBuffer();
		sql.append(
				"select upper(table_Schema) as table_schema,upper(table_name) as table_name,upper(column_name) as column_name,ORDINAL_POSITION from information_Schema.KEY_COLUMN_USAGE where CONSTRAINT_NAME ='PRIMARY'  ");
		if (replicaDBTables.length != 0) {
			for (int i = 0; i < replicaDBTables.length; i++) {
				if (i == 0) {
					sql.append(" and  concat(table_schema,'.',table_name)  like '").append(replicaDBTables[i]);
				} else if (i == replicaDBTables.length - 1) {
					sql.append(" or  concat(table_schema,'.',table_name)  like  '").append(replicaDBTables[i]);
				} else {
					sql.append(" or concat(table_schema,'.',table_name)  like '").append(replicaDBTables[i]);
				}
				sql.append("'");
			}
		}
		sql.append(" order by table_schema,table_name,ORDINAL_POSITION");

		try {
			ps = con.prepareStatement(sql.toString());
			// ps.setString(1, DBNAME);
			rs = ps.executeQuery();
			if (tablePrimary != null) {
				tablePrimary = new HashMap<String, List<String>>();
			}
			while (rs.next()) {
				String tableSchema = rs.getString("table_schema").toUpperCase();
				String tableName = rs.getString("table_name").toUpperCase();
				String tableNameKey = ForrestDataUtil.getMetaDataMapKey(tableSchema, tableName);
				String primaryColumnName = rs.getString("column_name").toUpperCase();
				if (tablePrimary.containsKey(tableNameKey)) {
					List<String> primaryColumnNameList = tablePrimary.get(tableNameKey);
					primaryColumnNameList.add(primaryColumnName);
				} else {
					List<String> primaryColumnNameList = new ArrayList<String>();
					primaryColumnNameList.add(primaryColumnName);
					tablePrimary.put(tableNameKey, primaryColumnNameList);
				}
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			System.exit(1);
		} finally {
			close(con, ps, rs);
		}
	}

	public void initConfig() {
		Properties properties = new Properties();
		InputStream in = ForrestDataConfig.class.getClassLoader().getResourceAsStream("forrest.conf");
		try {
			properties.load(in);
			this.mysqlHost = properties.getProperty("fd.mysql.host").trim();
			this.mysqlPort = Integer.valueOf(properties.getProperty("fd.mysql.port").trim());
			this.mysqlUser = properties.getProperty("fd.mysql.user").trim();
			this.mysqlPasswd = properties.getProperty("fd.mysql.passwd").trim();
			this.mysqlDBname = properties.getProperty("fd.mysql.dbname").trim();
			this.mysqlServerID = Integer.valueOf(properties.getProperty("fd.mysql.serverid").trim());
			this.binlogFileName = properties.getProperty("fd.mysql.binlog.log.file.name").trim();

			// this.binlogPostion =
			// Long.valueOf(properties.getProperty("fd.mysql.binlog.log.pos").trim());
			String position = properties.getProperty("fd.mysql.binlog.log.pos").trim();
			this.binlogPostion = Long.valueOf(position.length() == 0 ? "4" : position);

			this.dsType = properties.getProperty("fd.ds.type").toUpperCase().trim();
			loadHistoryData = properties.getProperty("fd.load.history.data").trim().equals("true") ? true : false;

			this.queueLength = Integer.valueOf(properties.getProperty("fd.queue.length").trim());

			this.binlogCacheFilePath = properties.getProperty("fd.mysql.binlog.cache.file");

			metaTableName = properties.getProperty("fd.meta.data.tablename").trim();
			metaDatabaseName = properties.getProperty("fd.meta.data.databasename").trim();
			metaBinLogFileName = properties.getProperty("fd.meta.data.binlogfilename").trim();
			metaBinlogPositionName = properties.getProperty("fd.meta.data.binlogposition").trim();
			metaSqltypeName = properties.getProperty("fd.meta.data.sqltype").trim();

			File binlogCacheFile = new File(this.binlogCacheFilePath);
			in.close();
			BinlogPosProcessor.randomAccessFile = new RandomAccessFile(binlogCacheFile, "rw");
			if (binlogCacheFile.exists()) {
				String str = BinlogPosProcessor.randomAccessFile.readLine();
				if (str != null && str != "") {
					if (str.length() > binlogPosCharMaxLength) {
						System.err.println("char length of binlog file name and position is too long,than "
								+ (binlogPosCharMaxLength - 1));
					}
					this.binlogFileName = str.split(" ")[0];
					this.binlogPostion = Long.valueOf(str.split(" ")[1].trim());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		String str = properties.getProperty("fd.replica.do.db.table").trim();
		doUpdateData = properties.getProperty("fd.replica.do.update.data").trim().equals("true") ? true : false;
		doDeleteData = properties.getProperty("fd.replica.do.delete.data").trim().equals("true") ? true : false;
		updateBeforName = properties.getProperty("fd.result.data.update.beforname").trim();
		updateAfterName = properties.getProperty("fd.result.data.update.aftername").trim();

		httpServerPort = Integer.valueOf(properties.getProperty("fd.http.server.port").trim());

		initDoDBTable(str);
		getMetaDataInfo();
		getTablePrimary();
	}

	public void initDoDBTable(String str) {
		// fd.replica.do.db.table 配置没空的情况下，同步所有库的表与数据
		if (str == null || str.length() == 0) {
			this.replicaDBTables = new String[] { "%.%" };
			return;
		}
		str = str.replace("*", "%");
		// System.out.println(str);
		this.replicaDBTables = str.split(",");
	}

	public void loadData() {
		Connection con = this.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		String sql = "select * from wuhp.text_test";
		// String sql = "select * from qktx_db.hhz_task_user";

		// Statement statement = null;
		// statement=con.createStatement();

		try {
			con.setAutoCommit(false);
			ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ps.setFetchSize(Integer.MIN_VALUE);
			// ps.setFetchSize(5);

			rs = ps.executeQuery();

			// System.out.println(rs.getFetchSize());
			// System.out.println(rs.getRow());

			while (rs.next()) {
				System.out.println(rs.getString(1));
			}
			con.commit();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			this.close(con, ps, rs);
		}
	}

	public String getMysqlHost() {
		return mysqlHost;
	}

	public void setMysqlHost(String mysqlHost) {
		this.mysqlHost = mysqlHost;
	}

	public int getMysqlPort() {
		return mysqlPort;
	}

	public void setMysqlPort(int mysqlPort) {
		this.mysqlPort = mysqlPort;
	}

	public String getMysqlUser() {
		return mysqlUser;
	}

	public void setMysqlUser(String mysqlUser) {
		this.mysqlUser = mysqlUser;
	}

	public String getMysqlPasswd() {
		return mysqlPasswd;
	}

	public void setMysqlPasswd(String mysqlPasswd) {
		this.mysqlPasswd = mysqlPasswd;
	}

	public String getMysqlDBname() {
		return mysqlDBname;
	}

	public void setMysqlDBname(String mysqlDBname) {
		this.mysqlDBname = mysqlDBname;
	}

	public int getMysqlServerID() {
		return mysqlServerID;
	}

	public void setMysqlServerID(int mysqlServerID) {
		this.mysqlServerID = mysqlServerID;
	}

	public String getBinlogFileName() {
		return binlogFileName;
	}

	public void setBinlogFileName(String binlogFileName) {
		this.binlogFileName = binlogFileName;
	}

	public long getBinlogPostion() {
		return binlogPostion;
	}

	public void setBinlogPostion(long binlogPostion) {
		this.binlogPostion = binlogPostion;
	}

	public String getDsType() {
		return dsType;
	}

	public void setDsType(String dsType) {
		this.dsType = dsType;
	}

	public String getBinlogCacheFileName() {
		return binlogCacheFilePath;
	}

	public void setBinlogCacheFile(String binlogCacheFilePath) {
		this.binlogCacheFilePath = binlogCacheFilePath;
	}

	public String[] getReplicaDBTables() {
		return replicaDBTables;
	}

	public void setReplicaDBTables(String[] replicaDBTables) {
		this.replicaDBTables = replicaDBTables;
	}

	public boolean isLoadHistoryData() {
		return loadHistoryData;
	}

	public void setLoadHistoryData(boolean loadHistoryData) {
		this.loadHistoryData = loadHistoryData;
	}

	public int getQueueLength() {
		return queueLength;
	}

	public void setQueueLength(int queueLength) {
		this.queueLength = queueLength;
	}

	public int getHttpServerPort() {
		return httpServerPort;
	}

	public void setHttpServerPort(int httpServerPort) {
		this.httpServerPort = httpServerPort;
	}

	public static void main(String[] args) {
		ForrestDataConfig config = new ForrestDataConfig();
		config.initConfig();
		config.loadData();

	}

}
