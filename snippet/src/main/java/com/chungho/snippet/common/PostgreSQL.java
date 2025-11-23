package com.chungho.snippet.common;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PostgreSQL implements AutoCloseable {

	private Connection connection;
	private boolean inTransaction;
	private String queryErrorCode = "";

	// PostgreSQL duplicate_column 에러 코드 (C#의 PostgresErrorCodes.DuplicateColumn)
	private static final String DUPLICATE_COLUMN = "42701";

	// C#의 ref Result queryResult 를 대체하는 홀더
	public static class ResultHolder {
		public Result value;
	}

	public enum Result
	{
		OK,
		Error,
	}

	public class PostgreSqlQueryResult
	{
		public String user_id;
	}

	public PostgreSQL() {
		if (connection == null) {
			init();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		close();
	}

	public void init() {
		try {
			// 필요 시 드라이버 로드 (요즘은 생략 가능하지만 명시해도 무방)
			// Class.forName("org.postgresql.Driver");

			// C#의 NpgsqlConnection 문자열을 그대로 JDBC로 옮긴 예시
			// 실제로는 host/port/db/user/pass를 분리해서 관리하는 것이 좋습니다.
			String url = "";
			String user = "";
			String password = "";

			connection = DriverManager.getConnection(url, user, password);
//			connection.setAutoCommit(true);
		} catch (Exception e) {
			MyPrint.printf(e);
		}
	}

	public boolean query(String sql) {
		MyPrint.printf(sql);
		boolean isErrored = false;

		Statement stmt = null;
		ResultSet rs = null;

		try {
			ensureConnection();
			stmt = connection.createStatement();
			rs = stmt.executeQuery(sql);
		} catch (Exception e) {
			MyPrint.printf(e);
			isErrored = true;
		} finally {
			closeQuietly(rs);
			closeQuietly(stmt);
		}

		return isErrored;
	}

	/**
	 * C#의
	 * List<T> Query<T>(string query, ref Result queryResult)
	 * 를 자바로 포팅한 버전.
	 *
	 * @param sql         실행할 쿼리
	 * @param queryResult 에러 여부를 세팅할 ResultHolder
	 * @param myClass       리플렉션에 사용할 T의 Class
	 */
	public <T extends PostgreSqlQueryResult> List<T> query(String sql, ResultHolder queryResult, Class<T> myClass) {
		MyPrint.printf(sql);

		List<T> results = new ArrayList<>();
		ResultSet rs = null;
		PreparedStatement stmt = null;

		try {
			ensureConnection();

			stmt = connection.prepareStatement(sql);
			rs = stmt.executeQuery();

			ResultSetMetaData meta = rs.getMetaData();
			int columnCount = meta.getColumnCount();
			Field[] fields = myClass.getDeclaredFields();

			while (rs.next() == true) {
				T result = myClass.getDeclaredConstructor().newInstance();

				for (int i = 1; i <= columnCount; i++) {
					String fieldName = meta.getColumnLabel(i);

					if (rs.getObject(i) != null) {
						Field field = findFieldIgnoreCase(fields, fieldName);

						if (field != null && field.canAccess(result) == false) {
							field.setAccessible(true);
						}

						if (field != null) {
							Object fieldValue = rs.getObject(i);
							Object changedObj = convertValue(fieldValue, field.getType());
							field.set(result, changedObj);
						}
					}
				}

				results.add(result);
			}
		} catch (SQLException e) {
			MyPrint.printf(e);
			MyPrint.printf(sql);

			queryErrorCode = e.getSQLState();

			if (Objects.equals(queryErrorCode, DUPLICATE_COLUMN) == false) {
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
		} finally {
			closeQuietly(rs);
			closeQuietly(stmt);
		}

		return results;
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
	 * C#의
	 * if (fieldValue is DateTime dt && prop.PropertyType == typeof(long)) {...}
	 * + enum 처리 + ChangeType 을 자바로 옮긴 부분.
	 */
	private Object convertValue(Object value, Class<?> targetType) throws Exception {
		if (value == null) {
			return null;
		}

		// DateTime → long (Unix time seconds)
		if ((targetType == long.class || targetType == Long.class) && (value instanceof Timestamp || value instanceof java.util.Date)) {

			Instant instant;

			if (value instanceof Timestamp ts) {
				instant = ts.toInstant();
			} else {
				instant = ((java.util.Date) value).toInstant();
			}

			return instant.getEpochSecond();
		}

		// Enum
		if (targetType.isEnum() == true) {
			@SuppressWarnings("unchecked")
			Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;

			return Enum.valueOf(enumType, value.toString());
		}

		// 숫자 타입
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

		// String
		if (targetType == String.class) {
			return value.toString();
		}

		// 타입이 이미 맞는 경우
		if (targetType.isAssignableFrom(value.getClass()) == true) {
			return value;
		}

		// 마지막 fallback
		return value.toString();
	}

	public void beginTransaction() {
		try {
			ensureConnection();

			if (inTransaction == true) {
				return;
			}

			connection.setAutoCommit(false);
			inTransaction = true;
		} catch (SQLException e) {
			MyPrint.printf(e);
		}
	}

	/**
	 * C#의 Commit(bool isErrored).
	 */
	public void commit(boolean isErrored) {
		if (inTransaction == false || connection == null) {
			return;
		}

		try {
			if (isErrored == true) {
				rollback();

				return;
			}

			connection.commit();
		} catch (SQLException e) {
			MyPrint.printf(e);
		} finally {
			try {
				connection.setAutoCommit(true);
			} catch (SQLException e) {
				MyPrint.printf(e);
			}

			close();
			inTransaction = false;
		}
	}

	/**
	 * C#의 Rollback().
	 */
	public void rollback() {
		if (inTransaction == false || connection == null) {
			return;
		}

		try {
			connection.rollback();
		} catch (SQLException e) {
			MyPrint.printf(e);
		} finally {
			try {
				connection.setAutoCommit(true);
			} catch (SQLException e) {
				MyPrint.printf(e);
			}

			close();
			inTransaction = false;
		}
	}

	@Override
	public void close() {
		if (connection != null) {
			try {
				if (connection.isClosed() == false) {
					connection.close();
				}
			} catch (SQLException e) {
				MyPrint.printf(e);
			} finally {
				connection = null;
			}
		}
	}

	private void ensureConnection() throws SQLException {
		if (connection == null || connection.isClosed() == true) {
			init();
		}
	}

	private void closeQuietly(AutoCloseable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception e) {
				MyPrint.printf(e);
			}
		}
	}
}
