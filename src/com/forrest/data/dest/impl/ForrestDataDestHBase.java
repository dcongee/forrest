package com.forrest.data.dest.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.monitor.ForrestMonitor;

public class ForrestDataDestHBase extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private Logger logger = Logger.getLogger(ForrestDataDestHBase.class);

	String binLongFileName = null;
	String binlogPosition = null;
	String databaseName = null;
	String tableName = null;
	String sqlType = null;
	Map<String, String> gtid = null;

	public ForrestDataDestHBase(String esHost, int esPort) {
		initHBaseConfig();
	}

	public ForrestDataDestHBase(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		// super();
		this.forrestMonitor = forrestMonitor;
		this.config = config;
		initHBaseConfig();
		initHBaseClient();
	}

	public void initHBaseConfig() {
		Properties properties = new Properties();
		InputStream in = ForrestDataDestRabbitMQ.class.getClassLoader().getResourceAsStream("elasticSearch.conf");
		try {
			properties.load(in);

			this.forrestMonitor.getMonitorMap().put("elasticsearch_host", "");
			// this.forrestMonitor.getMonitorMap().put("elasticsearch_port",
			// String.valueOf(esPort));
			// this.forrestMonitor.getMonitorMap().put("elasticsearch_clustername",
			// String.valueOf(esClusterName));

		} catch (IOException e) {
			logger.error("hbase.conf load failed,please check. " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void initHBaseClient() {
		// logger.info("elasticSearch client init success: " + esHost + ":" + esPort +
		// ".");
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {

		for (Map<String, Object> row : rowResultList) {
			binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
			binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
			sqlType = ((String) row.get(ForrestDataConfig.metaSqltypeName));
			if (config.getGtidEnable()) {
				gtid = (Map<String, String>) row.get(ForrestDataConfig.metaGTIDName);
			}

			// DDL操作刷新表数据。
			if (sqlType.equals("DDL")) {
				this.reloadTablePrimary(row);
				this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
				continue;
			}

			databaseName = (String) row.get(ForrestDataConfig.metaDatabaseName);
			tableName = (String) row.get(ForrestDataConfig.metaTableName);

			// 删除meta data info
			if (ForrestDataConfig.ignoreMetaDataName) {
				this.removeMetadataData(row);
			}

			this.deliverTryTimes = 0;
			this.deliverOK = false;
			switch (sqlType) {
			case "INSERT":
				break;
			case "UPDATE":
				break;
			case "DELETE":
				break;
			default:
				break;
			}
			this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
		}
		return true;
	}
}
