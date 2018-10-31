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
import com.forrest.monitor.ForrestMonitor;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

public class ForrestDataDestRedis extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private Logger logger = Logger.getLogger(ForrestDataDestRedis.class);
	private String redisHost;
	private int redisPort;
	private String redisPasswd;
	private String redisKeyName;
	private String redisKeyType;

	private JedisPool jedisPool;

	public ForrestDataDestRedis(String redisHost, int redisPort, String redisPasswd, String redisKeyName) {
		this.redisHost = redisHost;
		this.redisPort = redisPort;
		this.redisPasswd = redisPasswd;
		this.redisKeyName = redisKeyName;
		initJedis();
	}

	public ForrestDataDestRedis(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		this.config = config;
		this.forrestMonitor = forrestMonitor;
		initRedisConfig();
		initJedis();
	}

	public ForrestDataDestRedis() {
		initRedisConfig();
		initJedis();
	}

	public void initRedisConfig() {
		Properties properties = new Properties();
		InputStream in = ForrestDataDestRabbitMQ.class.getClassLoader().getResourceAsStream("redis.conf");
		try {
			properties.load(in);
			this.redisHost = properties.getProperty("fd.ds.redis.host").trim();
			this.redisPort = Integer.valueOf(properties.getProperty("fd.ds.redis.port").trim());
			this.redisPasswd = properties.getProperty("fd.ds.redis.passwd").trim();
			// this.redisKeyType = properties.getProperty("fd.ds.redis.key.type").trim();
			this.redisKeyName = properties.getProperty("fd.ds.redis.key.name").trim();

			this.forrestMonitor.getMonitorMap().put("redis_host", redisHost);
			this.forrestMonitor.getMonitorMap().put("redis_port", String.valueOf(redisPort));
			this.forrestMonitor.getMonitorMap().put("redis_key", String.valueOf(redisKeyName));

		} catch (IOException e) {
			e.printStackTrace();
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

	public void initJedis() {
		try {
			JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
			jedisPoolConfig.setTestWhileIdle(true);
			jedisPoolConfig.setMinIdle(3);
			jedisPoolConfig.setMaxIdle(5);
			jedisPoolConfig.setMaxTotal(10);
			if (redisPasswd.length() == 0) {
				this.jedisPool = new JedisPool(jedisPoolConfig, redisHost, redisPort, 10000);
			} else {
				this.jedisPool = new JedisPool(jedisPoolConfig, redisHost, redisPort, 10000, redisPasswd);
			}

			//
			Jedis jedis = this.jedisPool.getResource();
			jedis.set("forrest_test", "1", "nx", "ex", 1);
			this.jedisPool.returnResource(jedis);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {
		// TODO Auto-generated method stub
		Jedis jedis = this.jedisPool.getResource();
		String binLongFileName = null;
		String binlogPosition = null;
		try {
			Pipeline pipeLine = jedis.pipelined();
			for (Map<String, Object> row : rowResultList) {
				binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
				binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
				if (((String) row.get(ForrestDataConfig.metaSqltypeName)).equals("DDL")) {
					this.flushMetaData(row);
					this.saveBinlogPos(binLongFileName, binlogPosition);
					continue;
				}
				jedis.rpush(this.redisKeyName, this.getJsonStringFromMap(row));
			}
			pipeLine.sync();
		} catch (Exception e) {
			logger.error("redis deliver exception: " + e.getMessage());
			return false;
		} finally {
			this.jedisPool.returnResource(jedis);
		}
		this.saveBinlogPos(binLongFileName, binlogPosition);
		return true;
	}

	public static void saveToDest() {
		// TODO Auto-generated method stub
		// for (Map<String, Object> row : rowResultList) {
		// jedis.rpush(this.redisKeyName, JSON.toJSONString(row));
		// BinlogPosProcessor.saveCurrentBinlogPosToCacheFile((String)
		// row.get(FLowDataConfig.metaBinLogFileName),
		// (String) row.get(FLowDataConfig.metaBinlogPositionName));
		// }

	}

	public String getRedisKeyType() {
		return redisKeyType;
	}

	public void setRedisKeyType(String redisKeyType) {
		this.redisKeyType = redisKeyType;
	}

	public void setRedisKeyName(String redisKeyName) {
		this.redisKeyName = redisKeyName;
	}

	public String getRedisHost() {
		return redisHost;
	}

	public void setRedisHost(String redisHost) {
		this.redisHost = redisHost;
	}

	public int getRedisPort() {
		return redisPort;
	}

	public void setRedisPort(int redisPort) {
		this.redisPort = redisPort;
	}

	public String getRedisPasswd() {
		return redisPasswd;
	}

	public void setRedisPasswd(String redisPasswd) {
		this.redisPasswd = redisPasswd;
	}

	public String getRedisKeyName() {
		return redisKeyName;
	}

	public void setRediKeyName(String redisKeyName) {
		this.redisKeyName = redisKeyName;
	}

}
