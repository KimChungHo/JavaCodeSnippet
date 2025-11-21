package com.chungho.snippet.common;

public final class MyPrint {
	private MyPrint() {
		// 유틸 클래스이므로 인스턴스 생성 방지
	}

	public static void printf(Object message) {
		// 현재 스레드의 스택 트레이스를 얻는다.
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

		if (stackTrace.length > 2) {
			StackTraceElement caller = stackTrace[2];

			String fileName = caller.getFileName();     // 예: "SomeClass.java"
			int lineNumber = caller.getLineNumber();    // 호출된 라인 번호
			String methodName = caller.getMethodName(); // 호출한 메서드 이름

			System.out.printf("[%s:%d#%s()] %s%n", fileName, lineNumber, methodName, String.valueOf(message));
		} else {
			// 혹시라도 스택이 너무 짧은 경우를 대비한 fallback
			System.out.println(String.valueOf(message));
		}
	}

}
