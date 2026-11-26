/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;
import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.learn.service.CertmanagerService;
import com.liferay.learn.service.WebassessorService;
import com.liferay.petra.function.UnsafeBiFunction;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.Validator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
		).getString(
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
			OffsetDateTime startOffsetDateTime = _getStartOffsetDateTime(
				source);

			if (Objects.equals(source, "certmanager")) {
				examResultAmount += _reimportMissedPrerequisiteExamResults();

				examResultAmount += _synchronizeExamResults(
					_CERTMANAGER_DAYS, startOffsetDateTime,
					_certmanagerService::getCertmanagerExamResultsJSONArray);
			}
			else {
				examResultAmount += _synchronizeExamResults(
					7, startOffsetDateTime,
					_webassessorService::getWebassessorExamResultsJSONArray);
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

		patch(
			_getAuthorization(),
			new JSONObject(
			).put(
				"examResultAmount", examResultAmount
			).put(
				"executionTime", System.currentTimeMillis() - startTime
			).put(
				"synchronizationStatus", status
			).toString(),
			UriComponentsBuilder.fromPath(
				"/o/c/p2s3examresultssynchronizations/" + classPK
			).build(
			).toUri());

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

			JSONObject sourceJSONObject = itemJSONObject.getJSONObject(
				"source");

			String sourceKey = sourceJSONObject.getString("key");

			if (Validator.isNull(sourceKey)) {
				sourceKey = "webassessor";
			}

			if (!Objects.equals(sourceKey, source)) {
				continue;
			}

			OffsetDateTime offsetDateTime = OffsetDateTime.parse(
				itemJSONObject.getString("dateCreated"));

			return offsetDateTime.minusDays(1);
		}

		return OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
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
		JSONArray missedExamResultsJSONArray = new JSONArray();

		for (LocalDate localDate : localDates) {
			if (localDate.isBefore(endLocalDate)) {
				continue;
			}

			OffsetDateTime startOffsetDateTime = OffsetDateTime.of(
				localDate.minusDays(1), LocalTime.MIDNIGHT, ZoneOffset.UTC);

			OffsetDateTime endOffsetDateTime = startOffsetDateTime.plusDays(
				_CERTMANAGER_DAYS);

			JSONArray examResultsJSONArray =
				_certmanagerService.getCertmanagerExamResultsJSONArray(
					startOffsetDateTime, endOffsetDateTime);

			for (int i = 0; i < examResultsJSONArray.length(); i++) {
				JSONObject examResultJSONObject =
					examResultsJSONArray.getJSONObject(i);

				if (examResultJSONObject.getBoolean("missedPrerequisite") ||
					!externalReferenceCodes.contains(
						examResultJSONObject.getString(
							"externalReferenceCode"))) {

					continue;
				}

				missedExamResultsJSONArray.put(examResultJSONObject);
			}

			OffsetDateTime lastOffsetDateTime = endOffsetDateTime.minusDays(1);

			endLocalDate = lastOffsetDateTime.toLocalDate();
		}

		_putExamResults(missedExamResultsJSONArray);

		return missedExamResultsJSONArray.length();
	}

	private int _synchronizeExamResults(
			int days, OffsetDateTime offsetDateTime,
			UnsafeBiFunction
				<OffsetDateTime, OffsetDateTime, JSONArray, Exception>
					unsafeBiFunction)
		throws Exception {

		int examResultAmount = 0;

		while (offsetDateTime.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			OffsetDateTime endOffsetDateTime = offsetDateTime.plusDays(days);

			JSONArray jsonArray = unsafeBiFunction.apply(
				offsetDateTime, endOffsetDateTime);

			_putExamResults(jsonArray);

			examResultAmount += jsonArray.length();

			offsetDateTime = endOffsetDateTime;
		}

		return examResultAmount;
	}

	private static final int _CERTMANAGER_DAYS = 14;

	private static final Log _log = LogFactory.getLog(
		ObjectActionExamResultsSynchronizationRestController.class);

	@Autowired
	private CertmanagerService _certmanagerService;

	@Autowired
	private LiferayOAuth2AccessTokenManager _liferayOAuth2AccessTokenManager;

	@Autowired
	private WebassessorService _webassessorService;

}