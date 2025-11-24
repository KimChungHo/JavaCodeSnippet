package com.chungho.snippet.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MySQL implements AutoCloseable {

	// ***** 커넥션 풀 (앱 전체 공유) *****
	private static HikariDataSource dataSource;

	// ***** 트랜잭션 전용 커넥션 *****
	private Connection conn;
	private boolean inTransaction;

	@Getter
	private int queryErrorCode;
	public String stage;

	// MySQL Duplicate Key 에러 코드 (MySqlErrorCode.DuplicateKey 에 해당)
	private static final int MYSQL_DUPLICATE_KEY = 1062;

	public enum Result {
		OK,
		Error,
	}

	public class MySqlQueryResult {
		public String user_id;
	}

	public static class ResultHolder {
		public Result value;
	}

	// 정적 블록에서 풀 초기화
	static {
		initDataSource();
	}

	public MySQL() {
		// 인스턴스 생성 시 별도 작업 없음 (풀은 static으로 준비)
	}

	// ***** 커넥션 풀 초기화 *****
	private static void initDataSource() {
		try {
			HikariConfig config = new HikariConfig();

			// TODO: 실제 연결 문자열 / 계정 정보로 교체
			// 예시: jdbc:mysql://host:3306/dbname?useSSL=false&serverTimezone=UTC
			config.setJdbcUrl("jdbc:mysql://localhost:3306/db?useSSL=false&serverTimezone=UTC+9");
			config.setUsername("user");
			config.setPassword("password");

			// 풀 옵션 (필요 시 조정)
			config.setMaximumPoolSize(10);
			config.setMinimumIdle(2);
			config.setConnectionTimeout(30000);
			config.setIdleTimeout(600000);
			config.setMaxLifetime(1800000);

			dataSource = new HikariDataSource(config);
		} catch (Exception e) {
			MyPrint.printf(e);
		}
	}

	public boolean query(String sql) {
		MyPrint.printf(sql);
		boolean isError = false;

		// 트랜잭션 중이면 필드 conn 사용
		if (inTransaction == true && conn != null) {
			try (Statement stmt = conn.createStatement()) {
				stmt.execute(sql);
			} catch (Exception e) {
				MyPrint.printf(e);
				isError = true;
			}
		} else {
			// 트랜잭션이 아니면 풀에서 커넥션 하나 빌려서 try-with-resources
			try (Connection c = dataSource.getConnection();
			     Statement stmt = c.createStatement()) {
				stmt.execute(sql);
			} catch (Exception e) {
				MyPrint.printf(e);
				isError = true;
			}
		}

		return isError;
	}

	/**
	 * @param sql         실행할 SELECT 쿼리
	 * @param queryResult 에러 시 Result.Error 등을 세팅하기 위한 홀더
	 * @param myClass     결과로 매핑할 타입
	 */
	public <T extends MySqlQueryResult> List<T> query(String sql, ResultHolder queryResult, Class<T> myClass) {
		MyPrint.printf(sql);

		List<T> results = new ArrayList<>();
		queryErrorCode = 0;

		// 트랜잭션 도중
		if (inTransaction == true && conn != null) {
			try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
				mapResultSet(rs, myClass, results);
			} catch (SQLException e) {
				handleSqlException(e, sql, queryResult);
			} catch (Exception e) {
				handleGeneralException(e, sql, queryResult);
			}
		} else {
			// 트랜잭션 아님: 풀에서 커넥션 빌려와서 Connection까지 try-with-resources
			try (Connection c = dataSource.getConnection(); PreparedStatement stmt = c.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
				mapResultSet(rs, myClass, results);
			} catch (SQLException e) {
				handleSqlException(e, sql, queryResult);
			} catch (Exception e) {
				handleGeneralException(e, sql, queryResult);
			}
		}

		return results;
	}

	// ***** ResultSet → T 리스트 매핑 *****
	private <T extends MySqlQueryResult> void mapResultSet(ResultSet rs, Class<T> myClass, List<T> results) throws Exception {
		ResultSetMetaData meta = rs.getMetaData();
		int columnCount = meta.getColumnCount();
		Field[] fields = myClass.getDeclaredFields();

		while (rs.next() == true) {
			T result = myClass.getDeclaredConstructor().newInstance();

			for (int i = 1; i <= columnCount; i++) {
				String columnName = meta.getColumnLabel(i);
				Object fieldValue = rs.getObject(i);

				if (fieldValue != null) {
					Field field = findFieldIgnoreCase(fields, columnName);

					if (field != null) {
						field.setAccessible(true);
						Object converted = convertValue(fieldValue, field.getType());
						field.set(result, converted);
					}
				}
			}

			results.add(result);
		}
	}

	private void handleSqlException(SQLException e, String sql, ResultHolder queryResult) {
		MyPrint.printf(e);
		MyPrint.printf(sql);

		queryErrorCode = e.getErrorCode();

		if (queryErrorCode != MYSQL_DUPLICATE_KEY && queryResult != null) {
			queryResult.value = Result.Error;
		}
	}

	private void handleGeneralException(Exception e, String sql, ResultHolder queryResult) {
		MyPrint.printf(e);
		MyPrint.printf(sql);

		if (queryResult != null) {
			queryResult.value = Result.Error;
		}
	}

	private Field findFieldIgnoreCase(Field[] fields, String name) {
		for (Field f : fields) {
			if (f.getName().equalsIgnoreCase(name) == true) {
				return f;
			}
		}

		return null;
	}

	/**
	 * C#의 Convert.ChangeType + Enum.Parse + DateTime → long 변환을 자바로 옮긴 함수.
	 */
	private Object convertValue(Object value, Class<?> targetType) {
		if (value == null) {
			return null;
		}

		// Date/Time → long (UnixTimeSeconds) 매핑
		if ((targetType == long.class || targetType == Long.class) && value instanceof java.util.Date) {

			Instant instant;

			if (value instanceof Timestamp ts) {
				instant = ts.toInstant();
			} else {
				instant = ((java.util.Date) value).toInstant();
			}

			return instant.getEpochSecond();
		}

		if (targetType.isEnum() == true) {
			@SuppressWarnings("unchecked")
			Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;

			return Enum.valueOf(enumType, value.toString());
		}

		// 숫자 타입 변환
		if (value instanceof Number number) {
			if (targetType == int.class || targetType == Integer.class) {
				return number.intValue();
			}

			if (targetType == long.class || targetType == Long.class) {
				return number.longValue();
			}

			if (targetType == double.class || targetType == Double.class) {
				return number.doubleValue();
			}

			if (targetType == float.class || targetType == Float.class) {
				return number.floatValue();
			}
		}

		// 문자열
		if (targetType == String.class) {
			return value.toString();
		}

		// 그 외는 그대로 리턴 (타입이 이미 맞는다고 가정)
		if (targetType.isAssignableFrom(value.getClass()) == true) {
			return value;
		}

		// 마지막 fallback: 그냥 toString
		return value.toString();
	}

	// ***** 트랜잭션 관리 *****

	public void beginTransaction() {
		try {
			if (inTransaction == true && conn != null && conn.isClosed() == false) {
				return;
			}

			conn = dataSource.getConnection();
			conn.setAutoCommit(false);
			inTransaction = true;
		} catch (SQLException e) {
			MyPrint.printf(e);
		}
	}

	public void commit(boolean isErrored) {
		if (conn != null && inTransaction == true) {
			try {
				if (isErrored == true) {
					rollback();
					return;
				}

				conn.commit();
			} catch (SQLException e) {
				MyPrint.printf(e);
			} finally {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					MyPrint.printf(e);
				}

				closeInternal();
			}
		}
	}

	public void rollback() {
		if (conn != null && inTransaction == true) {
			try {
				conn.rollback();
			} catch (SQLException e) {
				MyPrint.printf(e);
			} finally {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					MyPrint.printf(e);
				}

				closeInternal();
			}
		}
	}

	// AutoCloseable 구현
	@Override
	public void close() {
		// 이 인스턴스가 들고 있는 트랜잭션용 커넥션만 정리
		closeInternal();
	}

	private void closeInternal() {
		if (conn != null) {
			try {
				if (conn.isClosed() == false) {
					conn.close();
				}
			} catch (SQLException e) {
				MyPrint.printf(e);
			} finally {
				conn = null;
				inTransaction = false;
			}
		}
	}
}
