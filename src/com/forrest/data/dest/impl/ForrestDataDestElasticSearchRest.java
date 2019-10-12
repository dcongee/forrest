package com.forrest.data.dest.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.log4j.Logger;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.monitor.ForrestMonitor;

public class ForrestDataDestElasticSearchRest extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private Logger logger = Logger.getLogger(ForrestDataDestElasticSearchRest.class);
	private String esHosts;
	private String esClusterName;
	private RestClient restClient;

	RestHighLevelClient highClient;

	private boolean bulkEnable;
	private int maxBulkSize;
	private long bulkWaitMillis;
	private long beginMillis;

	private BulkRequest bulkRequest;

	private boolean ignore404Error;
	private Response response;

	private static final String POST = "POST";
	private static final String DELETE = "DELETE";

	private String binLongFileName = null;
	private String binlogPosition = null;
	private String databaseName = null;
	private String tableName = null;
	private String esIndex = null;
	private String esType = null;
	private String sqlType = null;
	private String esID = null;
	private Map<String, String> gtid = null;

	public ForrestDataDestElasticSearchRest(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		// super();
		this.forrestMonitor = forrestMonitor;
		this.config = config;
		initElasticSearchConfig();
		initElasticSearchRestClient();
		checkTablePrimaryKey();
	}

	public void initElasticSearchConfig() {
		Properties properties = new Properties();
		InputStream in = ForrestDataDestRabbitMQ.class.getClassLoader().getResourceAsStream("elasticSearch.conf");
		try {
			properties.load(in);
			this.esHosts = properties.getProperty("fd.ds.elasticsearch.servers").trim();

			this.bulkEnable = Boolean.valueOf(properties.getProperty("fd.ds.elasticsearch.bulk.enable").trim());
			this.maxBulkSize = Integer.valueOf(properties.getProperty("fd.ds.elasticsearch.bulk.max.size").trim());
			this.bulkWaitMillis = Long.valueOf(properties.getProperty("fd.ds.elasticsearch.bulk.wait.millis").trim());

			this.esClusterName = properties.getProperty("fd.ds.elasticsearch.cluster.name").trim();
			this.ignore404Error = Boolean.valueOf(properties.getProperty("fd.ds.elasticsearch.ignore.404").trim());

			this.forrestMonitor.getMonitorMap().put("elasticsearch_rest_server", esHosts);
			this.forrestMonitor.getMonitorMap().put("elasticsearch_clustername", String.valueOf(esClusterName));

		} catch (IOException e) {
			logger.error("elasticSearch.conf load failed,please check. " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void initElasticSearchRestClient() {

		RestClientBuilder restClientBuilder = null;

		String[] esServers = esHosts.split(",");

		for (int i = 0; i < esServers.length; i++) {
			String host = esServers[i].split(":")[0];
			String port = esServers[i].split(":")[1];
			HttpHost httpHost = new HttpHost(host, Integer.valueOf(port), "http");
			restClientBuilder = RestClient.builder(httpHost);
		}

		if (this.bulkEnable) {
			this.bulkRequest = new BulkRequest();
			this.beginMillis = System.currentTimeMillis();
			this.highClient = new RestHighLevelClient(restClientBuilder);
		} else {
			this.restClient = restClientBuilder.build();
		}

		logger.info("elasticSearch client init success: " + esHosts + ".");
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {
		if (rowResultList != null) {
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
					this.checkTablePrimaryKey();

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
						break;

					if (this.bulkEnable) {
						this.bulkRequest.add(new IndexRequest(esIndex, esType, esID).source(row));
						break;
					}

					while (!deliverOK) {
						deliverOK = saveByID(esIndex, esType, esID, row);
						isWait();
					}
					break;
				case "UPDATE":
					// if (this.bulkEnable) {
					// this.bulkRequest.add(new UpdateRequest());
					// break;
					// }

					while (!deliverOK) {
						deliverOK = updateByID(esIndex, esType, binLongFileName, binlogPosition, gtid, row);
						isWait();
					}
					break;
				case "DELETE":
					esID = this.getPrimaryKeyValueFromRow(databaseName, tableName, row);
					if (!checkPrimaryKey(esIndex, esType, esID, binLongFileName, binlogPosition, gtid, row))
						break;

					if (this.bulkEnable) {
						this.bulkRequest.add(new DeleteRequest(esIndex, esType, esID));
						break;
					}

					while (!deliverOK) {
						deliverOK = deleteByID(esIndex, esType, esID, row);
						isWait();
					}
					break;
				default:
					break;
				}

				// 非bulk模式下，每次都保存binlog信息
				if (!this.bulkEnable) {
					this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
				}
			}

		}

		if (this.bulkEnable) {
			if (System.currentTimeMillis() - this.beginMillis >= this.bulkWaitMillis
					|| bulkRequest.numberOfActions() >= this.maxBulkSize) {
				bulkhandle();
				this.beginMillis = System.currentTimeMillis();
				this.bulkRequest = new BulkRequest();

				if (this.binLongFileName != null && this.binlogPosition != null) {
					this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
				}

			} else {
				return true;
			}
		}

		return true;
	}

	void bulkhandle() {
		if (this.bulkRequest.numberOfActions() > 0) {
			try {
				BulkResponse bulkResponse = this.highClient.bulk(bulkRequest);

				if (bulkResponse.hasFailures()) {
					bulkResponseHandle(bulkResponse);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	void bulkResponseHandle(BulkResponse bulkResponse) {

		for (BulkItemResponse bulkItemResponse : bulkResponse) {
			DocWriteResponse itemResponse = bulkItemResponse.getResponse();

			if (bulkItemResponse.getOpType() == DocWriteRequest.OpType.INDEX
					|| bulkItemResponse.getOpType() == DocWriteRequest.OpType.CREATE) {
				IndexResponse indexResponse = (IndexResponse) itemResponse;
				logger.error(" index reponse." + indexResponse.getIndex());

			} else if (bulkItemResponse.getOpType() == DocWriteRequest.OpType.UPDATE) {
				UpdateResponse updateResponse = (UpdateResponse) itemResponse;
				logger.error("update response." + updateResponse.getIndex());
			} else if (bulkItemResponse.getOpType() == DocWriteRequest.OpType.DELETE) {
				DeleteResponse deleteResponse = (DeleteResponse) itemResponse;
				logger.error("delete response." + deleteResponse.getIndex());
			}
		}

	}

	public void indexTypeOperation(Map<String, Object> row) {

		String sql = (String) row.get(ForrestDataConfig.metaSqlContentName);
		sql = sql.toUpperCase().trim().replace(";", "");
		if (sql.startsWith("TRUNCATE ")) {
			// 删除TYPE
			return;
		}
		if (sql.startsWith("DROP ")) {

		}

	}

	public boolean saveByID(String esIndex, String esType, String esID, Map<String, Object> row) {
		try {

			String rowString = this.getJsonStringFromMap(row);
			HttpEntity entity = new NStringEntity(rowString, ContentType.APPLICATION_JSON);
			String str = new String(esIndex + "/" + esType + "/" + esID);

			Map<String, String> params = Collections.emptyMap();

			// Request request = new Request(POST, str);
			// request.setEntity(entity);
			// this.response = this.restClient.performRequest(request);

			this.response = this.restClient.performRequest(POST, str, params, entity);

		} catch (Exception e) {
			logger.error("elasticsearch save failed: " + e.getMessage() + " " + row, e);
			return false;
		}
		return true;
	}

	public boolean updateByID(String esIndex, String esType, String binLongFileName, String binlogPosition,
			Map<String, String> gtid, Map<String, Object> row) {
		Map<String, Object> afterMap = (Map<String, Object>) row.get("AFTER_VALUE");
		Map<String, Object> beforMap = (Map<String, Object>) row.get("BEFOR_VALUE");

		String beforEsID = this.getPrimaryKeyValueFromRow(esIndex.toUpperCase(), esType.toUpperCase(), beforMap);
		if (!checkPrimaryKey(esIndex, esType, beforEsID, binLongFileName, binlogPosition, gtid, row))
			return true;

		String afterEsID = this.getPrimaryKeyValueFromRow(esIndex.toUpperCase(), esType.toUpperCase(), afterMap);
		if (!checkPrimaryKey(esIndex, esType, afterEsID, binLongFileName, binlogPosition, gtid, row))
			return true;

		if (!beforEsID.equals(afterEsID)) {
			if (this.bulkEnable) {
				this.bulkRequest.add(new DeleteRequest(esIndex, esType, beforEsID));
			} else {
				if (!deleteByID(esIndex, esType, beforEsID, beforMap)) {
					return false;
				}
			}
		}

		if (!ForrestDataConfig.ignoreMetaDataName) {
			afterMap.put(ForrestDataConfig.metaDatabaseName, row.get(ForrestDataConfig.metaDatabaseName));
			afterMap.put(ForrestDataConfig.metaTableName, row.get(ForrestDataConfig.metaTableName));
			afterMap.put(ForrestDataConfig.metaBinLogFileName, row.get(ForrestDataConfig.metaBinLogFileName));
			afterMap.put(ForrestDataConfig.metaBinlogPositionName, row.get(ForrestDataConfig.metaBinlogPositionName));
			afterMap.put(ForrestDataConfig.metaSqltypeName, row.get(ForrestDataConfig.metaSqltypeName));
		}

		if (this.bulkEnable) {
			this.bulkRequest.add(new IndexRequest(esIndex, esType, afterEsID).source(afterMap));
		} else {
			if (!saveByID(esIndex, esType, afterEsID, afterMap)) {
				return false;
			}
		}
		return true;
	}

	public boolean deleteByID(String esIndex, String esType, String esID, Map<String, Object> row) {
		String str = new String(esIndex + "/" + esType + "/" + esID);
		// Request request = new Request(DELETE, str);
		Map<String, String> params = Collections.emptyMap();

		try {
			// this.response = this.restClient.performRequest(request);
			this.response = this.restClient.performRequest(DELETE, str, params);

		} catch (IOException e) {
			logger.error("elasticsearch delete failed: " + e.getMessage() + " " + row, e);
			ResponseException re = null;
			if (e instanceof ResponseException) {
				re = (ResponseException) e;
			}
			this.response = re.getResponse();
			if (this.ignore404Error) {
				if (this.response.getStatusLine().getStatusCode() == 404) {
					return true;
				}
			} else {
				return false;
			}
		}
		return true;
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

}
