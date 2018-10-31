package com.forrest.data;

import redis.clients.jedis.Jedis;

public class RedisClient {

	private static volatile RedisClient client;
	private Jedis jedis;

	public RedisClient() {
		this.jedis = new Jedis("172.16.0.39", 19000);
		this.jedis.auth("adminQQ123456");
	}

	public static RedisClient getInstance() {
		if (client == null) {
			synchronized (RedisClient.class) {
				if (client == null) {
					new RedisClient();
				}
			}
		}
		return client;
	}

	public void saveToRedis(String key, String value) {
		try {
			if (!this.jedis.isConnected()) {
				this.jedis = null;
				getInstance();
			}
			jedis.set(key, value, "nx", "ex", 60);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		} finally {
			jedis = null;
			getInstance();
		}
	}

	public Jedis getJedis() {
		return jedis;
	}

	public void setJedis(Jedis jedis) {
		this.jedis = jedis;
	}

	public static void main(String[] args) {
	}

}
