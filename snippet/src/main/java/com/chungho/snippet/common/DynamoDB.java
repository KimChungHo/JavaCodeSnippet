package com.chungho.snippet.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.HashMap;
import java.util.Map;

public class DynamoDB {
	private static DynamoDbClient dynamoDbClient;
	private static DynamoDbEnhancedClient enhancedClient;
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static class BoolRef {
		public boolean value;
	}

	public DynamoDB() {
		if (dynamoDbClient == null) {
			init();
		}
	}

	private void init() {
		try {
			if ("AccessKeyId".equals("")) {
				AwsBasicCredentials creds = AwsBasicCredentials.create("AccessKeyId", "Secret");

				dynamoDbClient = DynamoDbClient
						.builder()
						.region(Region.of("Region"))
						.credentialsProvider(StaticCredentialsProvider.create(creds))
						.build();
			} else {
				dynamoDbClient = DynamoDbClient
						.builder()
						.region(Region.of("Region"))
						.build();
			}

			enhancedClient = DynamoDbEnhancedClient
					.builder()
					.dynamoDbClient(dynamoDbClient)
					.build();
		} catch (Exception e) {
			MyPrint.printf(e);
		}
	}

	public <T> String put(String tableName, String primaryKeyName, String primaryKeyValue, String json, boolean toPrettyText, Class<T> myClass) {
		try {
			// JSON -> Map
			Map<String, Object> dict = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });

			// Map -> JSON -> POJO
			String normalizedJson = OBJECT_MAPPER.writeValueAsString(dict);
			T item = OBJECT_MAPPER.readValue(normalizedJson, myClass);

			// Enhanced Client 테이블 핸들
			DynamoDbTable<T> table = enhancedClient.table(
					tableName,
					TableSchema.fromBean(myClass)
			);

			// 저장
			table.putItem(item);

			if (toPrettyText == true) {
				return OBJECT_MAPPER
						.writerWithDefaultPrettyPrinter()
						.writeValueAsString(item);
			} else {
				return OBJECT_MAPPER.writeValueAsString(item);
			}
		} catch (Exception e) {
			MyPrint.printf(e);

			return "";
		}
	}

	public <T> T get(String tableName, String keyName, String keyValue, BoolRef isErrored, Class<T> myClass) {
		try {
			Map<String, AttributeValue> key = new HashMap<>();

			key.put(keyName,
					AttributeValue.builder()
					.s(keyValue)
					.build()
			);

			GetItemRequest request = GetItemRequest
					.builder()
					.tableName(tableName)
					.key(key)
					.build();

			GetItemResponse response = dynamoDbClient.getItem(request);

			if (response == null || response.hasItem() == false || response.item().isEmpty() == true) {
				if (isErrored != null) {
					isErrored.value = true;
				}

				return null;
			}

			Map<String, AttributeValue> itemMap = response.item();

			Map<String, Object> simpleMap = toSimpleMap(itemMap);
			String docJson = OBJECT_MAPPER.writeValueAsString(simpleMap);

			MyPrint.printf(docJson);

			T result = OBJECT_MAPPER.readValue(docJson, myClass);

			return result;
		} catch (Exception e) {
			MyPrint.printf(e);

			if (isErrored != null) {
				isErrored.value = true;
			}

			return null;
		}
	}

	// --------- AttributeValue Map → 단순 Map 변환 함수 ---------
	private Map<String, Object> toSimpleMap(Map<String, AttributeValue> item) {
		Map<String, Object> map = new HashMap<>();

		for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
			String key = entry.getKey();
			AttributeValue av = entry.getValue();
			Object value = null;

			if (av.s() != null) {
				value = av.s();
			} else if (av.n() != null) {
				// 숫자는 일단 문자열 그대로 두거나, 필요시 Long/Double로 파싱해서 사용할 수 있음
				value = av.n();
			} else if (av.bool() != null) {
				value = av.bool();
			} else if (av.hasSs()) {
				value = av.ss();
			} else if (av.hasNs()) {
				value = av.ns();
			} else if (av.hasL()) {
				// 리스트 등 복합 타입이 필요한 경우 여기서 재귀 처리 가능
				value = av.l();
			} else if (av.hasM()) {
				value = toSimpleMap(av.m());
			}

			map.put(key, value);
		}

		return map;
	}
}
