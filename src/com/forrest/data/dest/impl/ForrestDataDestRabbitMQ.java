package com.forrest.data.dest.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.data.dest.listen.RabbitMQRecoveryListener;
import com.forrest.data.dest.listen.RabbitMQShutdownListener;
import com.forrest.monitor.ForrestMonitor;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Recoverable;

public class ForrestDataDestRabbitMQ extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private static Logger logger = Logger.getLogger(ForrestDataDestRabbitMQ.class);
	private String rabbitMQHost;
	private int rabbitMQPort;
	private String rabbitMQUser;
	private String rabbitMQPasswd;
	private String exchangeName;
	private String exchangeType;
	private String routingKeyName;
	private String queueName;
	private boolean exchangeDurable;
	private boolean queueDurable;
	private Channel channel;
	private String virtualHost;

	public ForrestDataDestRabbitMQ(String rabbitMQHost, int rabbitMQPort, String rabbitMQUser, String rabbitMQPasswd,
			String queueName) {
	}

	public ForrestDataDestRabbitMQ(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		this.forrestMonitor = forrestMonitor;
		this.config = config;
		initRabbitMQConfig();
		initRabbitMQ();
	}

	public ForrestDataDestRabbitMQ() {
		initRabbitMQConfig();
		initRabbitMQ();
	}

	public void initRabbitMQConfig() {
		logger.error("start to init rabbitmq config.");
		Properties properties = new Properties();
		InputStream in = ForrestDataDestRabbitMQ.class.getClassLoader().getResourceAsStream("rabbitmq.conf");
		try {
			properties.load(in);

			this.rabbitMQHost = properties.getProperty("fd.ds.rabbitmq.host").trim();
			this.rabbitMQPort = Integer.valueOf(properties.getProperty("fd.ds.rabbitmq.port").trim());
			this.rabbitMQUser = properties.getProperty("fd.ds.rabbitmq.user").trim();
			this.rabbitMQPasswd = properties.getProperty("fd.ds.rabbitmq.passwd").trim();
			this.exchangeName = properties.getProperty("fd.ds.rabbitmq.exchange.name").trim();
			this.exchangeType = properties.getProperty("fd.ds.rabbitmq.exchange.type").trim();
			this.routingKeyName = properties.getProperty("fd.ds.rabbitmq.routing.key").trim();
			this.queueName = properties.getProperty("fd.ds.rabbitmq.queue.name").trim();
			this.exchangeDurable = Boolean.valueOf(properties.getProperty("fd.ds.rabbitmq.exchange.durable").trim());
			this.queueDurable = Boolean.valueOf(properties.getProperty("fd.ds.rabbitmq.queue.durable").trim());
			this.virtualHost = properties.getProperty("fd.ds.rabbitmq.virtual.host").trim();

			this.forrestMonitor.getMonitorMap().put("rabbitmq_host", rabbitMQHost);
			this.forrestMonitor.getMonitorMap().put("rabbitmq_port", String.valueOf(rabbitMQPort));
			this.forrestMonitor.getMonitorMap().put("rabbitmq_user", rabbitMQUser);
			this.forrestMonitor.getMonitorMap().put("rabbitmq_exchangename", exchangeName);
			this.forrestMonitor.getMonitorMap().put("rabbitmq_exchangetype", exchangeType);
			this.forrestMonitor.getMonitorMap().put("rabbitmq_routingkeyname", routingKeyName);
			this.forrestMonitor.getMonitorMap().put("rabbitmq_queuename", queueName);
			this.forrestMonitor.getMonitorMap().put("rabbitmq_exchangedurable", String.valueOf(exchangeDurable));
			this.forrestMonitor.getMonitorMap().put("rabbitmq_queuedurable", String.valueOf(queueDurable));
			this.forrestMonitor.getMonitorMap().put("rabbitmq_virtual_host",
					virtualHost.length() == 0 ? "/" : virtualHost);

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

	public void initRabbitMQ() {
		try {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost(this.rabbitMQHost);
			factory.setPort(this.rabbitMQPort);
			factory.setUsername(rabbitMQUser);
			factory.setPassword(rabbitMQPasswd);
			if (this.virtualHost.length() > 0) {
				factory.setVirtualHost(this.virtualHost);
			}
			factory.setAutomaticRecoveryEnabled(true);

			Connection connection = factory.newConnection();
			connection.addShutdownListener(new RabbitMQShutdownListener());
			((Recoverable) connection).addRecoveryListener(new RabbitMQRecoveryListener());

			this.channel = connection.createChannel();

			this.channel.addShutdownListener(new RabbitMQShutdownListener());
			((Recoverable) this.channel).addRecoveryListener(new RabbitMQRecoveryListener());

			this.channel.exchangeDeclare(this.exchangeName, this.exchangeType, exchangeDurable);
			// DeclareOk okStr = channel.queueDeclarePassive(queueName); // 判断队列是否存在。
			this.channel.queueDeclare(this.queueName, this.queueDurable, false, false, null);
			this.channel.queueBind(this.queueName, this.exchangeName, this.routingKeyName);
			logger.info("bind rabbitmq exchange " + this.exchangeName + " and queue  " + this.queueName
					+ " and routing key " + this.routingKeyName + " success.");
			// this.channel.close();
			// this.channel = connection.createChannel();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
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
		try {
			channel.basicPublish(this.exchangeName, this.routingKeyName, MessageProperties.PERSISTENT_TEXT_PLAIN,
					this.getByteArrayFromMapJson(row));
		} catch (Exception e) { // 必须捕获Expcetion的异常，不能是IOException，否则rabbitmq框架中抛出的AlreadyClosedException捕获不到。
			logger.error("publish message failed: " + row);
			logger.error(e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void deliverDest(String str) {
		// TODO Auto-generated method stub
		try {
			channel.basicPublish(this.exchangeName, this.routingKeyName, MessageProperties.PERSISTENT_TEXT_PLAIN,
					str.getBytes("utf-8"));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	public String getRabbitMQHost() {
		return rabbitMQHost;
	}

	public void setRabbitMQHost(String rabbitMQHost) {
		this.rabbitMQHost = rabbitMQHost;
	}

	public int getRabbitMQPort() {
		return rabbitMQPort;
	}

	public void setRabbitMQPort(int rabbitMQPort) {
		this.rabbitMQPort = rabbitMQPort;
	}

	public String getRabbitMQUser() {
		return rabbitMQUser;
	}

	public void setRabbitMQUser(String rabbitMQUser) {
		this.rabbitMQUser = rabbitMQUser;
	}

	public String getRabbitMQPasswd() {
		return rabbitMQPasswd;
	}

	public void setRabbitMQPasswd(String rabbitMQPasswd) {
		this.rabbitMQPasswd = rabbitMQPasswd;
	}

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public String getExchangeName() {
		return exchangeName;
	}

	public void setExchangeName(String exchangeName) {
		this.exchangeName = exchangeName;
	}

	public String getExchangeType() {
		return exchangeType;
	}

	public void setExchangeType(String exchangeType) {
		this.exchangeType = exchangeType;
	}

	public boolean isExchangeDurable() {
		return exchangeDurable;
	}

	public void setExchangeDurable(boolean exchangeDurable) {
		this.exchangeDurable = exchangeDurable;
	}

	public boolean isQueueDurable() {
		return queueDurable;
	}

	public void setQueueDurable(boolean queueDurable) {
		this.queueDurable = queueDurable;
	}

	public String getRoutingKeyName() {
		return routingKeyName;
	}

	public void setRoutingKeyName(String routingKeyName) {
		this.routingKeyName = routingKeyName;
	}

	public static void main(String[] args) {
		ForrestDataDestRabbitMQ mq = new ForrestDataDestRabbitMQ();
		// mq.setRabbitMQHost("192.168.137.101");
		// mq.setRabbitMQPort(5672);
		// mq.setRabbitMQUser("test");
		// mq.setRabbitMQPasswd("test");
		// mq.setExchangeName("exchange_test");
		// mq.setExchangeType("direct");
		// mq.setExchangeDurable(true);
		// mq.setQueueDurable(true);
		// mq.setQueueName("queue_test");
		// mq.setRoutingKeyName("routingkey_test");
		// mq.setQueueDurable(true);
		// mq.initRabbitMQ();

		int i = 0;
		while (true) {
			mq.deliverDest("test" + i);
			System.out.println("test" + i);
			i++;
			try {
				Thread.sleep(2 * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
