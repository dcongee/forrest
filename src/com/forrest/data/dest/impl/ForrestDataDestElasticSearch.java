package com.forrest.data.dest.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
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
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.monitor.ForrestMonitor;

public class ForrestDataDestElasticSearch extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private Logger logger = Logger.getLogger(ForrestDataDestElasticSearch.class);
	private String esHost;
	private int esPort;
	private String esClusterName;
	private boolean esTransactionSniff;
	TransportClient client;

	public ForrestDataDestElasticSearch(String esHost, int esPort) {
		this.esHost = esHost;
		this.esPort = esPort;
		initElasticSearchConfig();
	}

	public ForrestDataDestElasticSearch(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		// super();
		this.forrestMonitor = forrestMonitor;
		this.config = config;
		initElasticSearchConfig();
		initElasticSearchClient();
	}

	public void initElasticSearchConfig() {
		Properties properties = new Properties();
		InputStream in = ForrestDataDestRabbitMQ.class.getClassLoader().getResourceAsStream("elasticSearch.conf");
		try {
			properties.load(in);
			this.esHost = properties.getProperty("fd.ds.elasticsearch.host").trim();
			this.esPort = Integer.valueOf(properties.getProperty("fd.ds.elasticsearch.port").trim());
			this.esClusterName = properties.getProperty("fd.ds.elasticsearch.cluster.name").trim();
			this.esTransactionSniff = Boolean
					.valueOf(properties.getProperty("fd.ds.elasticsearch.client.transport.sniff").trim());

			this.forrestMonitor.getMonitorMap().put("elasticsearch_host", esHost);
			this.forrestMonitor.getMonitorMap().put("elasticsearch_port", String.valueOf(esPort));
			this.forrestMonitor.getMonitorMap().put("elasticsearch_clustername", String.valueOf(esClusterName));

		} catch (IOException e) {
			logger.error("elasticSearch.conf load failed,please check. " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void initElasticSearchClient() {
		Settings settings = Settings.builder().put("cluster.name", this.esClusterName)
				.put("client.transport.sniff", this.esTransactionSniff)
				.put("client.transport.ignore_cluster_name", false).build();
		try {
			this.client = new PreBuiltTransportClient(settings)
					.addTransportAddresses(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));
		} catch (UnknownHostException e) {
			logger.error("elasticSearch client init failed" + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		logger.info("elasticSearch client init success: " + esHost + ":" + esPort + ".");
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {
		String binLongFileName = null;
		String binlogPosition = null;
		String databaseName = null;
		String tableName = null;
		String esIndex = null;
		String esType = null;
		String sqlType = null;
		String esID = null;
		Map<String, String> gtid = null;

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
			esIndex = databaseName.toLowerCase();
			esType = tableName.toLowerCase();

			// 删除meta data info
			if (ForrestDataConfig.ignoreMetaDataName) {
				this.removeMetadataData(row);
			}

			this.deliverTryTimes = 0;
			this.deliverOK = false;
			switch (sqlType) {
			case "INSERT":
				esID = this.getPrimaryKeyValueFromRow(databaseName, tableName, row);
				if (!checkPrimaryKey(esIndex, esType, esID, binLongFileName, binlogPosition, gtid, row)) // 如果表不存在主键，则忽略该数据。
					continue;
				while (!deliverOK) {
					deliverOK = saveByID(esIndex, esType, esID, row);
					isWait();
				}
				break;
			case "UPDATE":
				while (!deliverOK) {
					deliverOK = updateByID(esIndex, esType, esID, binLongFileName, binlogPosition, gtid, row);
					isWait();
				}
				break;
			case "DELETE":
				esID = this.getPrimaryKeyValueFromRow(databaseName, tableName, row);
				if (!checkPrimaryKey(esIndex, esType, esID, binLongFileName, binlogPosition, gtid, row))
					continue;
				while (!deliverOK) {
					deliverOK = deleteByID(esIndex, esType, esID, row);
					isWait();
				}
				break;
			default:
				break;
			}
			this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
		}
		return true;
	}

	public boolean saveByID(String esIndex, String esType, String esID, Map<String, Object> row) {
		try {

			IndexResponse response = client.prepareIndex(esIndex, esType, esID).setSource(row).get();
			// XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
			// IndexRequestBuilder indexRequestBuilder = client.prepareIndex(esIndex,
			// esType, esID);
			// if (response.status().getStatus() != 201) {
			// logger.warn("elasticsearch invalid save operation: " + row);
			// }

		} catch (Exception e) {
			logger.error("elasticsearch save failed: " + e.getMessage() + " " + row);
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean updateByID(String esIndex, String esType, String esID,
			String binLongFileName, String binlogPosition, Map<String, String> gtid, Map<String, Object> row) {
		Map<String, Object> beforMap = (Map<String, Object>) row.get("BEFOR_VALUE");
		Map<String, Object> afterMap = (Map<String, Object>) row.get("AFTER_VALUE");

		esID = this.getPrimaryKeyValueFromRow(esIndex.toUpperCase(), esType.toUpperCase(), beforMap);
		if (!checkPrimaryKey(esIndex, esType, esID, binLongFileName, binlogPosition, gtid, row))
			return true;

		String afterEsID = this.getPrimaryKeyValueFromRow(esIndex.toUpperCase(), esType.toUpperCase(), afterMap);
		if (!checkPrimaryKey(esIndex, esType, esID, binLongFileName, binlogPosition, gtid, row))
			return true;

		if (!deleteByID(esIndex, esType, esID, beforMap)) {
			return false;
		}

		if (!ForrestDataConfig.ignoreMetaDataName) {
			afterMap.put(ForrestDataConfig.metaDatabaseName, row.get(ForrestDataConfig.metaDatabaseName));
			afterMap.put(ForrestDataConfig.metaTableName, row.get(ForrestDataConfig.metaTableName));
			afterMap.put(ForrestDataConfig.metaBinLogFileName, row.get(ForrestDataConfig.metaBinLogFileName));
			afterMap.put(ForrestDataConfig.metaBinlogPositionName, row.get(ForrestDataConfig.metaBinlogPositionName));
			afterMap.put(ForrestDataConfig.metaSqltypeName, row.get(ForrestDataConfig.metaSqltypeName));
		}

		if (!saveByID(esIndex, esType, afterEsID, afterMap)) {
			return false;
		}
		return true;
	}

	public boolean updateByID_(String esIndex, String esType, String esID, Map<String, Object> row) {
		Map<String, Object> beforMap = (Map<String, Object>) row.get("BEFOR_VALUE");
		Map<String, Object> afterMap = (Map<String, Object>) row.get("AFTER_VALUE");
		esID = (String) beforMap.get("ID");
		String afterEsID = (String) afterMap.get("ID");
		if (!esID.equals(afterEsID)) {
			if (!deleteByID(esIndex, esType, esID, beforMap)) {
				return false;
			}

			afterMap.put(ForrestDataConfig.metaDatabaseName, row.get(ForrestDataConfig.metaDatabaseName));
			afterMap.put(ForrestDataConfig.metaTableName, row.get(ForrestDataConfig.metaTableName));
			afterMap.put(ForrestDataConfig.metaBinLogFileName, row.get(ForrestDataConfig.metaBinLogFileName));
			afterMap.put(ForrestDataConfig.metaBinlogPositionName, row.get(ForrestDataConfig.metaBinlogPositionName));
			afterMap.put(ForrestDataConfig.metaSqltypeName, row.get(ForrestDataConfig.metaSqltypeName));

			if (!saveByID(esIndex, esType, afterEsID, afterMap)) {
				return false;
			}
		} else {
			try {
				XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
				for (Map.Entry<String, Object> entry : afterMap.entrySet()) {
					builder.field(entry.getKey(), entry.getValue());
				}
				builder.endObject();
				UpdateRequest updateRequest = new UpdateRequest(esIndex, esType, esID);
				updateRequest.doc(builder);
				UpdateResponse response = client.update(updateRequest).get();
				// if (response.status().getStatus() != 201) {
				// logger.warn("elasticsearch Invalid update operation: " + row);
				// }
			} catch (InterruptedException e) {
				logger.error("elasticsearch update failed: " + e.getMessage() + " " + row);
				e.printStackTrace();
				return false;
			} catch (ExecutionException e) {
				logger.error("elasticsearch update failed: " + e.getMessage() + " " + row);
				e.printStackTrace();
				return false;
			} catch (IOException e) {
				logger.error("elasticsearch update failed: " + e.getMessage() + " " + row);
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	public boolean deleteByID(String esIndex, String esType, String esID, Map<String, Object> row) {
		try {
			DeleteResponse response = client.prepareDelete(esIndex, esType, esID).get();
			// if (response.status().getStatus() != 201) {
			// logger.warn("elasticsearch invalid delete operation: " + row);
			// }
		} catch (Exception e) {
			logger.error("elasticsearch delete failed: " + e.getMessage() + " " + row);
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean save(String esIndex, String esType, Map<String, Object> row) {
		try {
			IndexResponse response = client.prepareIndex(esIndex, esType).setSource(row).get();
			if (response.status().getStatus() != 201) {
				return false;
			}
		} catch (Exception e) {
			logger.error("elasticsearch save failed: " + e.getMessage() + " " + row);
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean update(String esIndex, String esType, Map<String, Object> row) {
		Map<String, String> beforMap = (Map<String, String>) row.get("BEFOR_VALUE");
		Map<String, String> afterMap = (Map<String, String>) row.get("AFTER_VALUE");
		BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
		for (Map.Entry<String, String> entry : beforMap.entrySet()) {
			queryBuilder.must(QueryBuilders.termQuery(entry.getKey(), entry.getValue()));
		}
		try {
			BulkByScrollResponse response = UpdateByQueryAction.INSTANCE.newRequestBuilder(client).filter(queryBuilder)
					.source(esIndex).script(new Script(ScriptType.INLINE, esType, esType, row))
					.abortOnVersionConflict(false).get();
			// response.getUpdated();
		} catch (Exception e) {
			logger.error("elasticsearch update failed: " + e.getMessage() + " " + row);
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public boolean delete(String esIndex, String esType, Map<String, Object> row) {
		BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
		for (Map.Entry<String, Object> entry : row.entrySet()) {
			queryBuilder.must(QueryBuilders.termQuery(entry.getKey(), entry.getValue()));
		}
		try {
			BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(client).filter(queryBuilder)
					.source(esIndex).get();
			// response.getUpdated();
		} catch (Exception e) {
			logger.error("elasticsearch delete failed: " + e.getMessage() + " " + row);
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void close() {
		if (client != null) {
			client.close();
		}
	}

	/**
	 *
	 * @param esIndex
	 * @param esType
	 * @param esID
	 * @param row
	 * @return
	 */
	public boolean checkPrimaryKey(String esIndex, String esType, String esID, Map<String, Object> row) {
		if (null == esID) {
			String binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
			String binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
			logger.warn("not found primary key in table " + esIndex + "." + esType + ",ignore this data: " + row);
			if (config.getGtidEnable()) {
				this.saveBinlogPos(binLongFileName, binlogPosition,
						(Map<String, String>) row.get(ForrestDataConfig.metaGTIDName));
			} else {
				this.saveBinlogPos(binLongFileName, binlogPosition, null);
			}
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param esIndex
	 * @param esType
	 * @param esID
	 * @param binLongFileName
	 * @param binlogPosition
	 * @param gtid
	 * @param row
	 * @return
	 */
	public boolean checkPrimaryKey(String esIndex, String esType, String esID, String binLongFileName,
			String binlogPosition, Map<String, String> gtid, Map<String, Object> row) {
		if (null == esID) {
			logger.warn("not found primary key in table " + esIndex + "." + esType + ",ignore this data: " + row);
			this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
			return false;
		}
		return true;
	}

	public void isWait() {
		deliverTryTimes++;
		if (deliverTryTimes > 10) { // 超过10次，就间隔一秒再重试，防止产生大量的日志，将磁盘刷满
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
