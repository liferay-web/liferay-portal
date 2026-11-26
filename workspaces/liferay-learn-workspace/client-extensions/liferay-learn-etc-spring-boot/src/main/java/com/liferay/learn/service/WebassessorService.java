/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn.service;

import com.liferay.client.extension.util.spring.boot3.service.BaseService;
import com.liferay.portal.kernel.util.GetterUtil;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Caleb Hall
 */
@Component
public class WebassessorService extends BaseService {

	public JSONArray getWebassessorExamResultsJSONArray(
		OffsetDateTime startOffsetDateTime, OffsetDateTime endOffsetDateTime) {

		JSONArray webassessorTranscriptsJSONArray = new JSONArray(
			post(
				null,
				new JSONObject(
				).put(
					"endDate",
					endOffsetDateTime.format(
						DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"))
				).put(
					"requestType", "GET TRANSCRIPTS BY DATE RANGE"
				).put(
					"returnFormat", "JSON"
				).put(
					"securityToken", _webassessorSecurityToken
				).put(
					"startDate",
					startOffsetDateTime.format(
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

		if (webassessorTranscriptsJSONArray.isEmpty()) {
			return new JSONArray();
		}

		if (webassessorTranscriptsJSONArray.get(0) instanceof String) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Webassessor returned " + webassessorTranscriptsJSONArray);
			}

			return new JSONArray();
		}

		JSONArray examResultsJSONArray = new JSONArray();

		for (int i = 0; i < webassessorTranscriptsJSONArray.length(); i++) {
			examResultsJSONArray.put(
				_getWebassessorExamResultJSONObject(
					webassessorTranscriptsJSONArray.getJSONObject(i)));
		}

		return examResultsJSONArray;
	}

	private JSONObject _getWebassessorExamResultJSONObject(
		JSONObject jsonObject) {

		JSONObject simpleRegistrationJSONObject = jsonObject.getJSONObject(
			"simpleRegistration");

		JSONObject candidateJSONObject =
			simpleRegistrationJSONObject.getJSONObject("candidate");

		OffsetDateTime offsetDateTime = OffsetDateTime.parse(
			jsonObject.getString("date"));

		ZonedDateTime zonedDateTime = offsetDateTime.atZoneSameInstant(
			ZoneOffset.UTC);

		return new JSONObject(
		).put(
			"date",
			zonedDateTime.format(
				DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX"))
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

	private static final Log _log = LogFactory.getLog(WebassessorService.class);

	@Value("${liferay.learn.webassessor.security.token}")
	private String _webassessorSecurityToken;

}