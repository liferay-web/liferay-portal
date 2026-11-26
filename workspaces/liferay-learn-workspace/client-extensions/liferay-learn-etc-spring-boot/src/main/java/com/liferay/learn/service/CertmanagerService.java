/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn.service;

import com.liferay.client.extension.util.spring.boot3.service.BaseService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.HexFormat;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Caleb Hall
 */
@Component
public class CertmanagerService extends BaseService {

	public JSONArray getCertmanagerExamResultsJSONArray(
			OffsetDateTime startOffsetDateTime,
			OffsetDateTime endOffsetDateTime)
		throws Exception {

		JSONArray certmanagerExamResultsJSONArray = new JSONArray();

		OffsetDateTime lastOffsetDateTime = endOffsetDateTime.minusDays(1);
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
						lastOffsetDateTime.format(
							DateTimeFormatter.ISO_LOCAL_DATE)
					).queryParam(
						"examCode", "all"
					).queryParam(
						"page", page
					).queryParam(
						"pageSize", 100
					).queryParam(
						"startDate",
						startOffsetDateTime.format(
							DateTimeFormatter.ISO_LOCAL_DATE)
					).scheme(
						"https"
					).build(
					).toUri()));

			JSONArray examResultsJSONArray = jsonObject.getJSONArray(
				"examResults");

			for (int i = 0; i < examResultsJSONArray.length(); i++) {
				certmanagerExamResultsJSONArray.put(
					_getCertmanagerExamResultJSONObject(
						examResultsJSONArray.getJSONObject(i)));
			}

			int pageSize = jsonObject.getInt("pageSize");

			lastPage = (jsonObject.getInt("total") + pageSize - 1) / pageSize;
		}

		return certmanagerExamResultsJSONArray;
	}

	private String _getCertmanagerAuthorization() throws Exception {
		if ((_certmanagerAuthorization != null) &&
			(System.currentTimeMillis() <
				_certmanagerAuthorizationExpirationTime)) {

			return _certmanagerAuthorization;
		}

		JSONObject jsonObject = new JSONObject(
			post(
				UriComponentsBuilder.newInstance(
				).queryParam(
					"client_id", _certmanagerClientId
				).queryParam(
					"client_secret", _certmanagerClientSecret
				).queryParam(
					"grant_type", "client_credentials"
				).build(
				).getQuery(),
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

		String accessToken = jsonObject.getString("access_token");

		if (Validator.isNull(accessToken)) {
			throw new Exception("Unable to get the access token");
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

		String examName = StringUtil.trim(jsonObject.getString("examName"));

		if (examName.equals("Building Enterprise Websites with Liferay")) {
			examName =
				"Building Enterprise Websites with Liferay Certification Exam";
		}

		String emailAddress = StringUtil.trim(
			jsonObject.getString("emailAddress"));

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

	private String _certmanagerAuthorization;
	private long _certmanagerAuthorizationExpirationTime;

	@Value("${liferay.learn.certmanager.client.id}")
	private String _certmanagerClientId;

	@Value("${liferay.learn.certmanager.client.secret}")
	private String _certmanagerClientSecret;

}