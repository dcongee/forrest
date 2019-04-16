package com.forrest.data.dest.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.Logger;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.monitor.ForrestMonitor;

public class ForrestDataDestKafka extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private static Logger logger = Logger.getLogger(ForrestDataDestKafka.class);

	// https://tuozixuan.iteye.com/blog/2433961
	private String kafkaHost;
	private String ack = "1";
	private int retries = 0;
	private int batchSize = 16384;
	private String topic;
	private long bufferMemory = 33554432; //
	private String compressionType = "none"; // GZIP、Snappy和LZ4 default none
	private long ingerMS = 0; // 消息延迟发送时间(ms)
	private int maxRequestSize = 10485760; // 最大message大小
	private int requestTimeoutMS = 30000; // 最大的请求超时时间

	private KafkaProducer<String, String> producer;

	public ForrestDataDestKafka(String kafkaHost, int kafkaPort, String kafkaUser, String kafkaPasswd,
			String queueName) {
	}

	public ForrestDataDestKafka(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		this.forrestMonitor = forrestMonitor;
		this.config = config;
		initKafkaConfig();
		initKafka();
	}

	public ForrestDataDestKafka() {
		initKafkaConfig();
		initKafka();
	}

	public void initKafkaConfig() {
		logger.error("start to init kafka config.");
		Properties properties = new Properties();
		InputStream in = ForrestDataDestKafka.class.getClassLoader().getResourceAsStream("kafka.conf");
		try {
			properties.load(in);
			this.kafkaHost = properties.getProperty("fd.ds.kafka.host").trim();
			this.ack = properties.getProperty("fd.ds.kafka.ack").trim();
			this.retries = Integer.valueOf(properties.getProperty("fd.ds.kafka.retries").trim());
			this.batchSize = Integer.valueOf(properties.getProperty("fd.ds.kafka.batchSize").trim());
			this.topic = properties.getProperty("fd.ds.kafka.topic").trim();
			this.bufferMemory = Long.valueOf(properties.getProperty("fd.ds.kafka.buffer.memory").trim());
			this.compressionType = properties.getProperty("fd.ds.kafka.compression.type").trim();
			this.ingerMS = Long.valueOf(properties.getProperty("fd.ds.kafka.inger.ms").trim());
			this.maxRequestSize = Integer.valueOf(properties.getProperty("fd.ds.kafka.max.request.size").trim());
			this.requestTimeoutMS = Integer.valueOf(properties.getProperty("fd.ds.kafka.request.timeout.ms").trim());

			logger.info("kafka servers: " + this.kafkaHost);
			logger.info("kafka ack type: " + ack);
			logger.info("kafka retries: " + retries);
			logger.info("kafka batch size: " + batchSize);

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void initKafka() {
		Properties props = new Properties();
		props.put("bootstrap.servers", kafkaHost);
		props.put("acks", ack);
		props.put("retries", retries);
		props.put("batch.size", batchSize);
		props.put("key.serializer", StringSerializer.class.getName());
		props.put("value.serializer", StringSerializer.class.getName());
		props.put("linger.ms", this.ingerMS);
		props.put("buffer.memory", this.bufferMemory);
		props.put("compression.type", this.compressionType);
		props.put("max.request.size", this.maxRequestSize);
		props.put("request.timeout.ms", this.requestTimeoutMS);
		this.producer = new KafkaProducer<String, String>(props);
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {
		// TODO Auto-generated method stub
		String binLongFileName = null;
		String binlogPosition = null;
		String sqlType = null;
		Map<String, String> gtid = null;

		for (Map<String, Object> row : rowResultList) {
			this.deliverTryTimes = 0;
			this.deliverOK = false;

			binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
			binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
			sqlType = ((String) row.get(ForrestDataConfig.metaSqltypeName));

			if (config.getGtidEnable()) {
				gtid = (Map<String, String>) row.get(ForrestDataConfig.metaGTIDName);
			}

			if (sqlType.equals("DDL")) {
				// this.flushMetaData(row); //目标数据源为消息队列，无需刷新metadata
				this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
				continue;
			}

			// 删除meta data info
			if (ForrestDataConfig.ignoreMetaDataName) {
				this.removeMetadataData(row);
			}

			while (!deliverOK) {
				deliverOK = this.deliver(row);
				this.isWait();
			}
		}
		/*
		 * delete range，update range,insert multi,共用一个binlog posistion,
		 * 在for循环中持久化position信息，可能会导致数据丢失。在for循环外持久化position信息，可能会导致数据重复。
		 *
		 */
		this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
		return true;
	}

	public boolean deliver(Map<String, Object> row) {
		// channel.basicPublish(this.exchangeName, this.routingKeyName,
		// MessageProperties.PERSISTENT_TEXT_PLAIN,
		// this.getByteArrayFromMapJson(row));
		try {
			String tableKey = (String) row.get(ForrestDataConfig.metaTableName);
			this.producer.send(new ProducerRecord<String, String>(topic, tableKey, this.getJsonStringFromMap(row)));
		} catch (Exception e) {
			logger.error("send message failed: " + row + " retry to send.");
			return false;
		}
		return true;
	}

	public String getkafkaHost() {
		return kafkaHost;
	}

	public void setkafkaHost(String kafkaHost) {
		this.kafkaHost = kafkaHost;
	}

	public static void main(String[] args) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("1", 1);
		System.out.println(map.toString());
	}

}
