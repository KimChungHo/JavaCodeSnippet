package com.chungho.snippet.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.BadRequestException;
import software.amazon.awssdk.services.sesv2.model.BulkEmailContent;
import software.amazon.awssdk.services.sesv2.model.BulkEmailEntry;
import software.amazon.awssdk.services.sesv2.model.BulkEmailEntryResult;
import software.amazon.awssdk.services.sesv2.model.BulkEmailStatus;
import software.amazon.awssdk.services.sesv2.model.CreateEmailTemplateRequest;
import software.amazon.awssdk.services.sesv2.model.CreateEmailTemplateResponse;
import software.amazon.awssdk.services.sesv2.model.DeleteEmailTemplateRequest;
import software.amazon.awssdk.services.sesv2.model.DeleteEmailTemplateResponse;
import software.amazon.awssdk.services.sesv2.model.EmailTemplateContent;
import software.amazon.awssdk.services.sesv2.model.EmailTemplateMetadata;
import software.amazon.awssdk.services.sesv2.model.GetEmailTemplateRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailTemplateResponse;
import software.amazon.awssdk.services.sesv2.model.ListEmailTemplatesRequest;
import software.amazon.awssdk.services.sesv2.model.ListEmailTemplatesResponse;
import software.amazon.awssdk.services.sesv2.model.ReplacementEmailContent;
import software.amazon.awssdk.services.sesv2.model.ReplacementTemplate;
import software.amazon.awssdk.services.sesv2.model.SendBulkEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendBulkEmailResponse;
import software.amazon.awssdk.services.sesv2.model.Template;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.util.*;

public class SES {

	private SesV2Client sesClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public enum Result
	{
		OK,
		Error,
		AlreadyExist,
	}

	public SES() {
		if (sesClient == null) {
			init();
		}
	}

	private void init() {
		if (!Objects.equals("AccessKeyId", "")) {
			AwsBasicCredentials creds = AwsBasicCredentials.create("AccessKeyId", "Secret");

			sesClient = SesV2Client
					.builder()
					.region(Region.of("Region"))
					.credentialsProvider(StaticCredentialsProvider.create(creds))
					.build();
		} else {
			sesClient = SesV2Client
					.builder()
					.region(Region.of("Region"))
					.build();
		}
	}

	/**
	 * HtmlPart에 등록할 거라고 해도 무조건 TextPart에 빈 값이라도 추가해야 함.
	 */
	public boolean createEmailTemplate(boolean isPlainText,	String templateName, String subject, String text, ResultHolder resultHolder) {
		if (resultHolder != null && resultHolder.value == Result.Error) {
			return false;
		}

		EmailTemplateContent.Builder contentBuilder = EmailTemplateContent.builder().subject(subject);

		if (isPlainText == true) {
			contentBuilder.text(text);
		} else {
			contentBuilder.text(" ");
			contentBuilder.html(text);
		}

		CreateEmailTemplateRequest request = CreateEmailTemplateRequest
				.builder()
				.templateName(templateName)
				.templateContent(contentBuilder.build())
				.build();

		try {
			CreateEmailTemplateResponse response = sesClient.createEmailTemplate(request);

			if (response.sdkHttpResponse().isSuccessful() == true) {
				String requestId = response.responseMetadata().requestId();
				MyPrint.printf(requestId);

				if (resultHolder != null) {
					resultHolder.value = Result.OK;
				}

				return true;
			}
		} catch (AlreadyExistsException ex) {
			if (resultHolder != null) {
				resultHolder.value = Result.AlreadyExist;
			}

			return false;
		} catch (BadRequestException ex) {
			MyPrint.printf(ex.getMessage());
		} catch (SesV2Exception ex) {
			MyPrint.printf(ex.awsErrorDetails().errorMessage());
		}

		if (resultHolder != null) {
			resultHolder.value = Result.Error;
		}

		return false;
	}

	/**
	 * HtmlPart에 등록할 거라고 해도 무조건 TextPart에 빈 값이라도 추가해야 함.
	 */
	public boolean updateEmailTemplate(boolean isPlainText, String templateName, String subject, String text, ResultHolder resultHolder) {
		EmailTemplateContent.Builder contentBuilder = EmailTemplateContent.builder().subject(subject);

		if (isPlainText == true) {
			contentBuilder.text(text);
		} else {
			contentBuilder.text(" ");
			contentBuilder.html(text);
		}

		software.amazon.awssdk.services.sesv2.model.UpdateEmailTemplateRequest request =
				software.amazon.awssdk.services.sesv2.model.UpdateEmailTemplateRequest
						.builder()
						.templateName(templateName)
						.templateContent(contentBuilder.build())
						.build();

		try {
			software.amazon.awssdk.services.sesv2.model.UpdateEmailTemplateResponse response = sesClient.updateEmailTemplate(request);

			if (response.sdkHttpResponse().isSuccessful() == true) {
				String requestId = response.responseMetadata().requestId();
				MyPrint.printf(requestId);

				if (resultHolder != null) {
					resultHolder.value = Result.OK;
				}

				return true;
			}
		} catch (BadRequestException ex) {
			MyPrint.printf(ex.getMessage());
		} catch (SesV2Exception ex) {
			MyPrint.printf(ex.awsErrorDetails().errorMessage());
		}

		if (resultHolder != null) {
			resultHolder.value = Result.Error;
		}

		return false;
	}

	public boolean deleteMailTemplate(String templateName, ResultHolder resultHolder) {
		DeleteEmailTemplateRequest request = DeleteEmailTemplateRequest
				.builder()
				.templateName(templateName)
				.build();

		try {
			DeleteEmailTemplateResponse response = sesClient.deleteEmailTemplate(request);

			if (response.sdkHttpResponse().isSuccessful() == true) {
				String requestId = response.responseMetadata().requestId();
				MyPrint.printf(requestId);

				if (resultHolder != null) {
					resultHolder.value = Result.OK;
				}

				return true;
			}
		} catch (SesV2Exception ex) {
			MyPrint.printf(ex.awsErrorDetails().errorMessage());
		}

		if (resultHolder != null) {
			resultHolder.value = Result.Error;
		}

		return false;
	}

	public List<String> getEmailTemplateNames(ResultHolder resultHolder) {
		List<String> resultList = new ArrayList<>();

		if (resultHolder != null && resultHolder.value == Result.Error) {
			return resultList;
		}

		String nextToken = null;

		do {
			ListEmailTemplatesRequest.Builder requestBuilder = ListEmailTemplatesRequest.builder().pageSize(50);

			if (nextToken != null && nextToken.isEmpty() == false) {
				requestBuilder.nextToken(nextToken);
			}

			try {
				ListEmailTemplatesResponse response = sesClient.listEmailTemplates(requestBuilder.build());

				if (response.sdkHttpResponse().isSuccessful() == false || response.templatesMetadata() == null) {
					if (resultHolder != null) {
						resultHolder.value = Result.Error;
					}

					return resultList;
				}

				for (EmailTemplateMetadata meta : response.templatesMetadata()) {
					resultList.add(meta.templateName());
				}

				nextToken = response.nextToken();
			} catch (SesV2Exception ex) {
				MyPrint.printf(ex.awsErrorDetails().errorMessage());

				if (resultHolder != null) {
					resultHolder.value = Result.Error;
				}

				return resultList;
			}

		} while (nextToken != null && nextToken.isEmpty() == false);

		return resultList;
	}

	public TemplateContentResult getEmailTemplate(String templateName, ResultHolder resultHolder) {
		GetEmailTemplateRequest request = GetEmailTemplateRequest
				.builder()
				.templateName(templateName)
				.build();

		try {
			GetEmailTemplateResponse response = sesClient.getEmailTemplate(request);

			if (response.sdkHttpResponse().isSuccessful() == false || response.templateContent() == null) {
				if (resultHolder != null) {
					resultHolder.value = Result.Error;
				}

				return new TemplateContentResult("", "");
			}

			EmailTemplateContent content = response.templateContent();

			if (content.text() != null && content.text().equals(" ") == false) {
				return new TemplateContentResult(content.subject(), content.text());
			} else {
				return new TemplateContentResult(content.subject(), content.html());
			}
		} catch (SesV2Exception ex) {
			MyPrint.printf(ex.awsErrorDetails().errorMessage());

			if (resultHolder != null) {
				resultHolder.value = Result.Error;
			}

			return new TemplateContentResult("", "");
		}
	}

	public Map<String, Boolean> sendEmail(String from, String templateName, List<Recipient> recipients, String replyToAddress) {
		Map<String, Boolean> result = new HashMap<>();
		List<BulkEmailEntry> entries = new ArrayList<>();

		for (Recipient item : recipients) {
			Destination destination = Destination
					.builder()
					.toAddresses(item.email)
					.build();

			String replacementJson;

			try {
				Map<String, Object> replacementMap = new HashMap<>();

				replacementMap.put("name", item.name);
				replacementJson = objectMapper.writeValueAsString(replacementMap);
			} catch (JsonProcessingException e) {
				MyPrint.printf(e.getMessage());

				continue;
			}

			ReplacementTemplate replacementTemplate = ReplacementTemplate
					.builder()
					.replacementTemplateData(replacementJson)
					.build();

			ReplacementEmailContent replacementEmailContent = ReplacementEmailContent
					.builder()
					.replacementTemplate(replacementTemplate)
					.build();

			BulkEmailEntry entry = BulkEmailEntry
					.builder()
					.destination(destination)
					.replacementEmailContent(replacementEmailContent)
					.build();

			entries.add(entry);
		}

		String defaultTemplateData;
		try {
			Map<String, Object> defaultMap = new HashMap<>();

			defaultMap.put("brand", " ");
			defaultMap.put("price", 0);
			defaultTemplateData = objectMapper.writeValueAsString(defaultMap);
		} catch (JsonProcessingException e) {
			MyPrint.printf(e.getMessage());

			return result;
		}

		Template template = Template
				.builder()
				.templateName(templateName)
				.templateData(defaultTemplateData)
				.build();

		BulkEmailContent defaultContent = BulkEmailContent
				.builder()
				.template(template)
				.build();

		SendBulkEmailRequest request = SendBulkEmailRequest
				.builder()
				.fromEmailAddress(from)
				.replyToAddresses(replyToAddress)
				.defaultContent(defaultContent)
				.bulkEmailEntries(entries)
				.build();

		try {
			SendBulkEmailResponse response = sesClient.sendBulkEmail(request);

			try {
				MyPrint.printf(objectMapper.writeValueAsString(response.bulkEmailEntryResults()));
			} catch (JsonProcessingException e) {
				MyPrint.printf(e.getMessage());
			}

			List<BulkEmailEntryResult> entryResults = response.bulkEmailEntryResults();
			int index = 0;

			for (Recipient item : recipients) {
				if (index < entryResults.size()) {
					BulkEmailEntryResult entryResult = entryResults.get(index);

					if (entryResult.status() == BulkEmailStatus.SUCCESS) {
						result.put(item.email, Boolean.TRUE);
					}
				}

				index++;
			}
		} catch (SesV2Exception ex) {
			MyPrint.printf(ex.awsErrorDetails().errorMessage());
			return result;
		} catch (Exception ex) {
			MyPrint.printf(ex.getMessage());
			return result;
		}

		return result;
	}

	public static class ResultHolder {
		public Result value;
	}

	public static class TemplateContentResult {
		public final String subject;
		public final String text;

		public TemplateContentResult(String subject, String text) {
			this.subject = subject;
			this.text = text;
		}
	}

	public static class Recipient {
		public final String email;
		public final String name;

		public Recipient(String email, String name) {
			this.email = email;
			this.name = name;
		}
	}
}
