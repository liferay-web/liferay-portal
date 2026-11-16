/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.http;

import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.purge.CDNPurgeResult;
import com.liferay.cdn.template.CDNHTTPRequestTemplate;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.Objects;
import java.util.Set;

/**
 * Purges any CDN that exposes an HTTP API, by rendering the requests that the
 * company's CDN template describes.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNHTTPPurger {

	public CDNHTTPPurger(Http http, JSONFactory jsonFactory) {
		_http = http;
		_jsonFactory = jsonFactory;
	}

	public CDNPurgeResult purge(
			ConfiguredCDNTemplate configuredCDNTemplate,
			CDNPurgeRequest cdnPurgeRequest)
		throws Exception {

		if (cdnPurgeRequest.isPurgeAll()) {
			return _send(
				configuredCDNTemplate,
				configuredCDNTemplate.getPurgeAllCDNHTTPRequestTemplate(),
				cdnPurgeRequest, null);
		}

		CDNPurgeResult cdnPurgeResult = CDNPurgeResult.success(0);

		Set<String> tags = cdnPurgeRequest.getTags();

		if (!tags.isEmpty()) {
			cdnPurgeResult = _send(
				configuredCDNTemplate,
				configuredCDNTemplate.getTagPurgeCDNHTTPRequestTemplate(),
				cdnPurgeRequest, null);

			if (!cdnPurgeResult.isSuccess()) {
				return cdnPurgeResult;
			}
		}

		Set<String> urls = cdnPurgeRequest.getURLs();

		if (urls.isEmpty()) {
			return cdnPurgeResult;
		}

		CDNHTTPRequestTemplate cdnHTTPRequestTemplate =
			configuredCDNTemplate.getURLPurgeCDNHTTPRequestTemplate();

		if (!configuredCDNTemplate.isURLPurgePerURL()) {
			return _send(
				configuredCDNTemplate, cdnHTTPRequestTemplate, cdnPurgeRequest,
				null);
		}

		for (String url : urls) {
			cdnPurgeResult = _send(
				configuredCDNTemplate, cdnHTTPRequestTemplate, cdnPurgeRequest,
				url);

			if (!cdnPurgeResult.isSuccess()) {
				return cdnPurgeResult;
			}
		}

		return cdnPurgeResult;
	}

	private void _addHeader(
		Http.Options options, String header, String[] variableNames,
		String[] variableValues) {

		if (header == null) {
			return;
		}

		int index = header.indexOf(StringPool.COLON);

		if (index <= 0) {
			return;
		}

		options.addHeader(
			StringUtil.trim(header.substring(0, index)),
			StringUtil.trim(
				StringUtil.replace(
					header.substring(index + 1), variableNames,
					variableValues)));
	}

	private Http.Method _getMethod(String methodName) {
		for (Http.Method method : Http.Method.values()) {
			if (Objects.equals(
					method.name(), StringUtil.toUpperCase(methodName))) {

				return method;
			}
		}

		return null;
	}

	private CDNPurgeResult _send(
			ConfiguredCDNTemplate configuredCDNTemplate,
			CDNHTTPRequestTemplate cdnHTTPRequestTemplate,
			CDNPurgeRequest cdnPurgeRequest, String url)
		throws Exception {

		if (cdnHTTPRequestTemplate == null) {
			return CDNPurgeResult.failure(
				0,
				StringBundler.concat(
					"The CDN template \"", configuredCDNTemplate.getKey(),
					"\" does not describe a request for ", cdnPurgeRequest),
				false);
		}

		String urlWithoutProtocol = StringPool.BLANK;

		if (url != null) {
			urlWithoutProtocol = HttpComponentsUtil.removeProtocol(url);
		}

		String[] variableNames = {
			"${credential}", "${endpoint}", "${purgeAll}", "${serviceId}",
			"${soft}", "${tags}", "${tagsHeader}", "${urlWithoutProtocol}",
			"${url}", "${urls}"
		};
		String[] variableValues = {
			GetterUtil.getString(configuredCDNTemplate.getCredential()),
			GetterUtil.getString(configuredCDNTemplate.getEndpoint()),
			String.valueOf(cdnPurgeRequest.isPurgeAll()),
			GetterUtil.getString(configuredCDNTemplate.getServiceId()),
			String.valueOf(cdnPurgeRequest.isSoft()),
			String.valueOf(
				_jsonFactory.createJSONArray(cdnPurgeRequest.getTags())),
			StringUtil.merge(
				cdnPurgeRequest.getTags(),
				configuredCDNTemplate.getCacheTagHeaderSeparator()),
			urlWithoutProtocol, GetterUtil.getString(url),
			String.valueOf(
				_jsonFactory.createJSONArray(cdnPurgeRequest.getURLs()))
		};

		String location = StringUtil.replace(
			cdnHTTPRequestTemplate.getURLTemplate(), variableNames,
			variableValues);

		if (Validator.isNull(location)) {
			return CDNPurgeResult.failure(
				0, "The CDN purge URL is not configured", false);
		}

		Http.Method method = _getMethod(cdnHTTPRequestTemplate.getMethod());

		if (method == null) {
			return CDNPurgeResult.failure(
				0,
				"The CDN purge method \"" + cdnHTTPRequestTemplate.getMethod() +
					"\" is not supported",
				false);
		}

		Http.Options options = new Http.Options();

		for (String header : cdnHTTPRequestTemplate.getHeaders()) {
			_addHeader(options, header, variableNames, variableValues);
		}

		if (cdnPurgeRequest.isSoft() && !cdnPurgeRequest.isPurgeAll()) {
			_addHeader(
				options, configuredCDNTemplate.getSoftPurgeHeader(),
				variableNames, variableValues);
		}

		String bodyTemplate = cdnHTTPRequestTemplate.getBodyTemplate();

		if (Validator.isNotNull(bodyTemplate) && (method != Http.Method.GET) &&
			(method != Http.Method.HEAD)) {

			options.setBody(
				StringUtil.replace(bodyTemplate, variableNames, variableValues),
				cdnHTTPRequestTemplate.getContentType(), StringPool.UTF8);
		}

		options.setLocation(location);
		options.setMethod(method);
		options.setTimeout(_TIMEOUT);

		String responseBody = _http.URLtoString(options);

		Http.Response response = options.getResponse();

		return CDNPurgeResult.fromHTTPStatus(
			response.getResponseCode(), responseBody);
	}

	private static final int _TIMEOUT = 10000;

	private final Http _http;
	private final JSONFactory _jsonFactory;

}