/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;
import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.kernel.util.Validator;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Caleb Hall
 * @author Nilton Vieira
 */
@RequestMapping("/object/action/exam/results/synchronization")
@RestController
public class ObjectActionExamResultsSynchronizationRestController
	extends BaseRestController {

	@PostMapping
	public ResponseEntity<String> post(@RequestBody String json) {
		JSONObject jsonObject = new JSONObject(json);

		String source = jsonObject.getJSONObject(
			"objectEntry"
		).getJSONObject(
			"values"
		).optString(
			"source"
		);

		if (Validator.isNull(source)) {
			source = "webassessor";
		}

		long classPK = jsonObject.getLong("classPK");

		if (_log.isInfoEnabled()) {
			_log.info(
				StringBundler.concat(
					"Started ", source, " exam results synchronization ",
					classPK));
		}

		int examResultAmount = 0;
		long startTime = System.currentTimeMillis();
		String status = "Failed";

		try {
			OffsetDateTime offsetDateTime = _getStartOffsetDateTime(source);

			if (Objects.equals(source, "certmanager")) {
				examResultAmount += _reimportMissedPrerequisiteExamResults();

				while (offsetDateTime.isBefore(
							OffsetDateTime.now(ZoneOffset.UTC))) {

					JSONArray examResultsJSONArray =
						_getCertmanagerExamResultsJSONArray(offsetDateTime);

					_putExamResults(examResultsJSONArray);

					examResultAmount += examResultsJSONArray.length();

					offsetDateTime = offsetDateTime.plusDays(14);
				}
			}
			else {
				while (offsetDateTime.isBefore(
							OffsetDateTime.now(ZoneOffset.UTC))) {

					JSONArray examResultsJSONArray =
						_getWebassessorExamResultsJSONArray(offsetDateTime);

					_putExamResults(examResultsJSONArray);

					examResultAmount += examResultsJSONArray.length();

					offsetDateTime = offsetDateTime.plusDays(7);
				}
			}

			status = "Successful";
		}
		catch (WebClientResponseException webClientResponseException) {
			_log.error(
				"Unable to synchronize exam results: " +
					webClientResponseException.getResponseBodyAsString(),
				webClientResponseException);
		}
		catch (Exception exception) {
			_log.error("Unable to synchronize exam results", exception);
		}

		_updateExamResultsSynchronization(
			classPK, examResultAmount, System.currentTimeMillis() - startTime,
			status);

		if (_log.isInfoEnabled()) {
			_log.info(
				StringBundler.concat(
					"Finished ", source, " exam results synchronization ",
					classPK, " with status ", status));
		}

		return new ResponseEntity<>(json, HttpStatus.OK);
	}

	private String _getAuthorization() {
		return _liferayOAuth2AccessTokenManager.getAuthorization(
			"liferay-learn-etc-spring-boot-oahs");
	}

	private String _getCertmanagerAuthorization() throws Exception {
		if ((_certmanagerAuthorization != null) &&
			(System.currentTimeMillis() <
				_certmanagerAuthorizationExpirationTime)) {

			return _certmanagerAuthorization;
		}

		JSONObject jsonObject = new JSONObject(
			post(
				StringBundler.concat(
					"client_id=", URLCodec.encodeURL(_certmanagerClientId),
					"&client_secret=",
					URLCodec.encodeURL(_certmanagerClientSecret),
					"&grant_type=client_credentials"),
				Map.of(
					HttpHeaders.CONTENT_TYPE,
					MediaType.APPLICATION_FORM_URLENCODED_VALUE),
				UriComponentsBuilder.fromPath(
					"/o/oauth2/token"
				).host(
					"certmanager.liferay.com"
				).scheme(
					"https"
				).build(
				).toUri()));

		String accessToken = jsonObject.optString("access_token");

		if (Validator.isNull(accessToken)) {
			throw new Exception("Unable to get access token");
		}

		_certmanagerAuthorization = "Bearer " + accessToken;

		_certmanagerAuthorizationExpirationTime =
			System.currentTimeMillis() +
				((jsonObject.getLong("expires_in") - 60) * 1000);

		return _certmanagerAuthorization;
	}

	private JSONObject _getCertmanagerExamResultJSONObject(
			JSONObject jsonObject)
		throws Exception {

		String emailAddress = StringUtil.trim(
			jsonObject.getString("emailAddress"));

		String examName = StringUtil.trim(jsonObject.getString("examName"));

		Map<String, String> examNames = Map.of(
			"Building Enterprise Websites with Liferay",
			"Building Enterprise Websites with Liferay Certification Exam");

		examName = examNames.getOrDefault(examName, examName);

		OffsetDateTime offsetDateTime = OffsetDateTime.parse(
			jsonObject.getString("date"));

		offsetDateTime = offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);

		return new JSONObject(
		).put(
			"date", offsetDateTime.format(DateTimeFormatter.ISO_INSTANT)
		).put(
			"emailAddress", emailAddress
		).put(
			"examName", examName
		).put(
			"externalReferenceCode",
			_getExternalReferenceCode(
				emailAddress, examName, offsetDateTime.toLocalDate())
		).put(
			"firstName", jsonObject.getString("firstName")
		).put(
			"lastName", jsonObject.getString("lastName")
		).put(
			"missedPrerequisite",
			StringUtil.equalsIgnoreCase(
				jsonObject.optString("passedPrerequisite"), "false")
		).put(
			"result", StringUtil.toLowerCase(jsonObject.getString("result"))
		).put(
			"score", jsonObject.getDouble("score")
		).put(
			"source", "certmanager"
		).put(
			"testName", examName
		);
	}

	private JSONArray _getCertmanagerExamResultsJSONArray(
			OffsetDateTime offsetDateTime)
		throws Exception {

		JSONArray jsonArray = new JSONArray();

		OffsetDateTime endOffsetDateTime = offsetDateTime.plusDays(13);
		int lastPage = 1;

		for (int page = 1; page <= lastPage; page++) {
			JSONObject jsonObject = new JSONObject(
				get(
					_getCertmanagerAuthorization(),
					UriComponentsBuilder.fromPath(
						"/o/certification-exam/report"
					).host(
						"certmanager.liferay.com"
					).queryParam(
						"endDate",
						endOffsetDateTime.format(
							DateTimeFormatter.ISO_LOCAL_DATE)
					).queryParam(
						"examCode", "all"
					).queryParam(
						"page", page
					).queryParam(
						"pageSize", 100
					).queryParam(
						"startDate",
						offsetDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
					).scheme(
						"https"
					).build(
					).toUri()));

			JSONArray examResultsJSONArray = jsonObject.getJSONArray(
				"examResults");

			for (int i = 0; i < examResultsJSONArray.length(); i++) {
				jsonArray.put(
					_getCertmanagerExamResultJSONObject(
						examResultsJSONArray.getJSONObject(i)));
			}

			int pageSize = jsonObject.getInt("pageSize");

			lastPage = (jsonObject.getInt("total") + pageSize - 1) / pageSize;
		}

		return jsonArray;
	}

	private String _getExternalReferenceCode(
			String emailAddress, String examName, LocalDate localDate)
		throws Exception {

		String keyString = StringBundler.concat(
			StringUtil.toLowerCase(emailAddress), "|", examName, "|",
			localDate);

		HexFormat hexFormat = HexFormat.of();
		MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

		return hexFormat.formatHex(
			messageDigest.digest(keyString.getBytes(StandardCharsets.UTF_8)));
	}

	private String _getSource(JSONObject jsonObject) {
		JSONObject sourceJSONObject = jsonObject.optJSONObject("source");

		if ((sourceJSONObject == null) ||
			Validator.isNull(sourceJSONObject.optString("key"))) {

			return "webassessor";
		}

		return sourceJSONObject.getString("key");
	}

	private OffsetDateTime _getStartOffsetDateTime(String source) {
		JSONObject jsonObject = new JSONObject(
			get(
				_getAuthorization(),
				UriComponentsBuilder.fromPath(
					"/o/c/p2s3examresultssynchronizations"
				).queryParam(
					"fields", "dateCreated,source"
				).queryParam(
					"filter", "synchronizationStatus eq 'Successful'"
				).queryParam(
					"pageSize", 20
				).queryParam(
					"sort", "dateCreated:desc"
				).build(
				).toUri()));

		JSONArray itemsJSONArray = jsonObject.getJSONArray("items");

		for (int i = 0; i < itemsJSONArray.length(); i++) {
			JSONObject itemJSONObject = itemsJSONArray.getJSONObject(i);

			if (!Objects.equals(_getSource(itemJSONObject), source)) {
				continue;
			}

			OffsetDateTime offsetDateTime = OffsetDateTime.parse(
				itemJSONObject.getString("dateCreated"));

			return offsetDateTime.minusDays(1);
		}

		return OffsetDateTime.of(
			_examResultsSynchronizationStartYear, 1, 1, 0, 0, 0, 0,
			ZoneOffset.UTC);
	}

	private JSONObject _getWebassessorExamResultJSONObject(
		JSONObject jsonObject) {

		JSONObject simpleRegistrationJSONObject = jsonObject.getJSONObject(
			"simpleRegistration");

		JSONObject candidateJSONObject =
			simpleRegistrationJSONObject.getJSONObject("candidate");

		return new JSONObject(
		).put(
			"date",
			OffsetDateTime.parse(
				jsonObject.getString("date")
			).atZoneSameInstant(
				ZoneOffset.UTC
			).format(
				DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX")
			)
		).put(
			"emailAddress", candidateJSONObject.getString("email")
		).put(
			"examName", jsonObject.getString("examName")
		).put(
			"externalReferenceCode",
			GetterUtil.getString(jsonObject.getLong("id"))
		).put(
			"firstName", candidateJSONObject.getString("firstName")
		).put(
			"lastName", candidateJSONObject.getString("lastName")
		).put(
			"result", jsonObject.getString("passFail")
		).put(
			"score", jsonObject.getDouble("score")
		).put(
			"source", "webassessor"
		).put(
			"testName", simpleRegistrationJSONObject.getString("testName")
		);
	}

	private JSONArray _getWebassessorExamResultsJSONArray(
		OffsetDateTime offsetDateTime) {

		JSONArray jsonArray = new JSONArray(
			post(
				null,
				new JSONObject(
				).put(
					"endDate",
					offsetDateTime.plusDays(
						7
					).format(
						DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
					)
				).put(
					"requestType", "GET TRANSCRIPTS BY DATE RANGE"
				).put(
					"returnFormat", "JSON"
				).put(
					"securityToken", _webassessorSecurityToken
				).put(
					"startDate",
					offsetDateTime.format(
						DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"))
				).toString(),
				UriComponentsBuilder.fromPath(
					"/WebAssessorWebServices/jaxrs/wawebservices/processRequest"
				).host(
					"webassessor.com"
				).scheme(
					"https"
				).build(
				).toUri()));

		if (jsonArray.isEmpty()) {
			return new JSONArray();
		}

		if (jsonArray.get(0) instanceof String) {
			if (_log.isWarnEnabled()) {
				_log.warn("Webassessor returned " + jsonArray);
			}

			return new JSONArray();
		}

		JSONArray examResultsJSONArray = new JSONArray();

		for (int i = 0; i < jsonArray.length(); i++) {
			examResultsJSONArray.put(
				_getWebassessorExamResultJSONObject(
					jsonArray.getJSONObject(i)));
		}

		return examResultsJSONArray;
	}

	private void _putExamResults(JSONArray jsonArray) throws Exception {
		int failedAmount = 0;

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);

			try {
				put(
					_getAuthorization(), jsonObject.toString(),
					UriComponentsBuilder.fromPath(
						"/o/c/p2s3examresults/by-external-reference-code/" +
							jsonObject.getString("externalReferenceCode")
					).build(
					).toUri());
			}
			catch (WebClientResponseException webClientResponseException) {
				failedAmount++;

				_log.error(
					StringBundler.concat(
						"Unable to synchronize exam result ",
						jsonObject.getString("externalReferenceCode"), ": ",
						webClientResponseException.getResponseBodyAsString()),
					webClientResponseException);
			}
			catch (Exception exception) {
				failedAmount++;

				_log.error(
					"Unable to synchronize exam result " +
						jsonObject.getString("externalReferenceCode"),
					exception);
			}
		}

		if (failedAmount > 0) {
			throw new Exception(
				StringBundler.concat(
					"Unable to synchronize ", failedAmount, " of ",
					jsonArray.length(), " exam results"));
		}
	}

	private int _reimportMissedPrerequisiteExamResults() throws Exception {
		Set<String> externalReferenceCodes = new HashSet<>();
		Set<LocalDate> localDates = new TreeSet<>();

		int lastPage = 1;

		for (int page = 1; page <= lastPage; page++) {
			JSONObject jsonObject = new JSONObject(
				get(
					_getAuthorization(),
					UriComponentsBuilder.fromPath(
						"/o/c/p2s3examresults"
					).queryParam(
						"fields", "date,externalReferenceCode"
					).queryParam(
						"filter",
						"missedPrerequisite eq true and result eq 'pass'"
					).queryParam(
						"page", page
					).queryParam(
						"pageSize", 100
					).build(
					).toUri()));

			JSONArray itemsJSONArray = jsonObject.getJSONArray("items");

			for (int i = 0; i < itemsJSONArray.length(); i++) {
				JSONObject itemJSONObject = itemsJSONArray.getJSONObject(i);

				externalReferenceCodes.add(
					itemJSONObject.getString("externalReferenceCode"));

				OffsetDateTime offsetDateTime = OffsetDateTime.parse(
					itemJSONObject.getString("date"));

				localDates.add(
					LocalDate.ofInstant(
						offsetDateTime.toInstant(), ZoneOffset.UTC));
			}

			lastPage = jsonObject.getInt("lastPage");
		}

		LocalDate endLocalDate = LocalDate.MIN;
		JSONArray jsonArray = new JSONArray();

		for (LocalDate localDate : localDates) {
			if (localDate.isBefore(endLocalDate)) {
				continue;
			}

			JSONArray examResultsJSONArray =
				_getCertmanagerExamResultsJSONArray(
					OffsetDateTime.of(
						localDate.minusDays(1), LocalTime.MIDNIGHT,
						ZoneOffset.UTC));

			for (int i = 0; i < examResultsJSONArray.length(); i++) {
				JSONObject examResultJSONObject =
					examResultsJSONArray.getJSONObject(i);

				if (examResultJSONObject.getBoolean("missedPrerequisite") ||
					!externalReferenceCodes.contains(
						examResultJSONObject.getString(
							"externalReferenceCode"))) {

					continue;
				}

				jsonArray.put(examResultJSONObject);
			}

			endLocalDate = localDate.plusDays(12);
		}

		_putExamResults(jsonArray);

		return jsonArray.length();
	}

	private void _updateExamResultsSynchronization(
		long classPK, int examResultAmount, long executionTime,
		String synchronizationStatus) {

		patch(
			_getAuthorization(),
			new JSONObject(
			).put(
				"examResultAmount", examResultAmount
			).put(
				"executionTime", executionTime
			).put(
				"synchronizationStatus", synchronizationStatus
			).toString(),
			UriComponentsBuilder.fromPath(
				"/o/c/p2s3examresultssynchronizations/" + classPK
			).build(
			).toUri());
	}

	private static final Log _log = LogFactory.getLog(
		ObjectActionExamResultsSynchronizationRestController.class);

	private String _certmanagerAuthorization;
	private long _certmanagerAuthorizationExpirationTime;

	@Value("${liferay.learn.certmanager.client.id}")
	private String _certmanagerClientId;

	@Value("${liferay.learn.certmanager.client.secret}")
	private String _certmanagerClientSecret;

	@Value("${liferay.learn.exam.results.synchronization.start.year}")
	private int _examResultsSynchronizationStartYear;

	@Autowired
	private LiferayOAuth2AccessTokenManager _liferayOAuth2AccessTokenManager;

	@Value("${liferay.learn.webassessor.security.token}")
	private String _webassessorSecurityToken;

}