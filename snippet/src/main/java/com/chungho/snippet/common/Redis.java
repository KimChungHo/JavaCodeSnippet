package com.chungho.snippet.common;

import redis.clients.jedis.*;

import java.util.List;
import java.util.Set;

public class Redis {
	public enum DbNumber {
		ap_northeast_2,
		us_east_1,
		eu_central_1,
	}

	private static JedisPool jedisPool;
	private static DbNumber dbNum; // Profile.RedisDbNumber 대응 (사용 여부는 선택)

	// 생성자에서 한 번만 초기화 (스레드 세이프하게)
	public Redis() {
		if (jedisPool == null) {
			synchronized (Redis.class) {
				if (jedisPool == null) {
					init();
				}
			}
		}
	}

	private void init() {
		String redisEnv = System.getenv("redis");  // 예: "localhost:6379"

		if (redisEnv == null || redisEnv.isEmpty() == true) {
			redisEnv = "localhost:6379";
		}

		String[] parts = redisEnv.split(":");
		String host = parts[0];
		int port = 6379;

		if (parts.length > 1) {
			try {
				port = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				port = 6379;
			}
		}

		boolean ssl = false;
		if (redisEnv.equals("localhost:6379") == false) {
			ssl = true;
		}

		JedisPoolConfig config = new JedisPoolConfig();

		// password가 없다는 가정 (있다면 JedisPool 생성자에 password 추가 필요)
		jedisPool = new JedisPool(config, host, port, Protocol.DEFAULT_TIMEOUT, ssl);
		dbNum = DbNumber.ap_northeast_2;
	}

	public static void setValue(int dbNum, String key, String value) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.select(dbNum);
			jedis.set(key, value);
		}
	}

	public static String getValue(int dbNum, String key) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.select(dbNum);
			return jedis.get(key);
		}
	}

	public static List<String> getKeys(int dbNum) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.select(dbNum);

			Set<String> keys = jedis.keys("*");

			return keys.stream().toList();
		}
	}

	public static boolean deleteKey(int dbNum, String key) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.select(dbNum);
			long deleted = jedis.del(key);

			return deleted > 0;
		}
	}
}
