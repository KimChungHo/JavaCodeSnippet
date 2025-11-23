package com.chungho.snippet.common;

import lombok.Getter;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MySQL implements AutoCloseable {
	private Connection conn;
	private boolean inTransaction;
	@Getter
	private int queryErrorCode;
	public String stage;

	// MySQL Duplicate Key 에러 코드 (MySqlErrorCode.DuplicateKey 에 해당)
	private static final int MYSQL_DUPLICATE_KEY = 1062;

	public enum Result
	{
		OK,
		Error,
	}

	public class MySqlQueryResult
	{
		public String user_id;
	}

	public static class ResultHolder {
		public Result value;
	}

	public MySQL() {
		if (conn == null) {
			init();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			close();
		} finally {
			super.finalize();
		}
	}

	public void init() {
		try {
			// 필요 시 드라이버 로드 (신규 JDBC에서는 생략 가능)
			// Class.forName("com.mysql.cj.jdbc.Driver");
			String connectionString = "";

			conn = DriverManager.getConnection(connectionString);
//			conn.setAutoCommit(true);
		} catch (Exception e) {
			MyPrint.printf(e);
		}
	}

	public boolean query(String sql) {
		boolean isError = false;

		MyPrint.printf(sql);

		try (Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
		} catch (Exception e) {
			MyPrint.printf(e);
			isError = true;
		}

		return isError;
	}

	/**
	 * @param sql         실행할 SELECT 쿼리
	 * @param queryResult 에러 시 Result.Error 등을 세팅하기 위한 홀더
	 * @param myClass       결과로 매핑할 타입
	 */
	public <T extends MySqlQueryResult> List<T> query(String sql, ResultHolder queryResult, Class<T> myClass) {
		MyPrint.printf(sql);

		List<T> results = new ArrayList<>();
		queryErrorCode = 0;

		try (PreparedStatement stmt = conn.prepareStatement(sql);
		     ResultSet rs = stmt.executeQuery()) {

			ResultSetMetaData meta = rs.getMetaData();
			int columnCount = meta.getColumnCount();

			Field[] fields = myClass.getDeclaredFields();

			while (rs.next()) {
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
		} catch (SQLException e) {
			MyPrint.printf(e);
			MyPrint.printf(sql);

			queryErrorCode = e.getErrorCode();

			if (queryErrorCode != MYSQL_DUPLICATE_KEY) {
				if (queryResult != null) {
					queryResult.value = Result.Error;
				}
			}
		} catch (Exception e) {
			MyPrint.printf(e);
			MyPrint.printf(sql);

			if (queryResult != null) {
				queryResult.value = Result.Error;
			}
		}

		return results;
	}

	private Field findFieldIgnoreCase(Field[] fields, String name) {
		for (Field f : fields) {
			if (f.getName().equalsIgnoreCase(name)) {
				return f;
			}
		}

		return null;
	}

	/**
	 * C#의 Convert.ChangeType + Enum.Parse + DateTime → long 변환을 자바로 옮긴 헬퍼.
	 */
	private Object convertValue(Object value, Class<?> targetType) {
		if (value == null) {
			return null;
		}

		// Date/Time → long (UnixTimeSeconds) 매핑
		if ((targetType == long.class || targetType == Long.class) && (value instanceof java.sql.Timestamp || value instanceof java.util.Date)) {
			Instant instant;

			if (value instanceof java.sql.Timestamp ts) {
				instant = ts.toInstant();
			} else {
				instant = ((java.util.Date) value).toInstant();
			}

			return instant.getEpochSecond();
		}

		if (targetType.isEnum()) {
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
		if (targetType.isAssignableFrom(value.getClass())) {
			return value;
		}

		// 마지막 fallback: 그냥 toString
		return value.toString();
	}

	public void beginTransaction() {
		try {
			if (conn == null || conn.isClosed()) {
				init();
			}

			conn.setAutoCommit(false);
			inTransaction = true;
		} catch (SQLException e) {
			MyPrint.printf(e);
		}
	}

	public void commit(boolean isErrored) {
		if (conn != null && inTransaction) {
			try {
				if (isErrored) {
					rollback();
				} else {
					conn.commit();
				}
			} catch (SQLException e) {
				MyPrint.printf(e);
			} finally {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					MyPrint.printf(e);
				}

				close();
				inTransaction = false;
			}
		}
	}

	public void rollback() {
		if (conn != null && inTransaction) {
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

				close();
				inTransaction = false;
			}
		}
	}

	@Override
	public void close() {
		if (conn != null) {
			try {
				if (!conn.isClosed()) {
					conn.close();
				}
			} catch (SQLException e) {
				MyPrint.printf(e);
			}
		}
	}
}
