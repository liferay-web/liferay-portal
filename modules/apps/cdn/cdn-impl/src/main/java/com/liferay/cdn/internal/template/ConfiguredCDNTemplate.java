/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.template;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.template.CDNHTTPRequestTemplate;
import com.liferay.cdn.template.CDNTemplate;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.security.key.secret.SecretResolverUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies a company's CDN configuration on top of a template. A configuration
 * value that is empty, or <code>0</code> for a limit, falls back to the
 * template's value.
 *
 * @author Gianmarco Brunialti Masera
 */
public class ConfiguredCDNTemplate implements CDNTemplate {

	public ConfiguredCDNTemplate(
		CDNConfiguration cdnConfiguration, CDNTemplate cdnTemplate,
		long companyId) {

		_cdnConfiguration = cdnConfiguration;
		_cdnTemplate = cdnTemplate;
		_companyId = companyId;
	}

	@Override
	public int getCacheTagHeaderMaxBytes() {
		return _get(
			_cdnConfiguration.maxCacheTagHeaderBytes(),
			_cdnTemplate.getCacheTagHeaderMaxBytes());
	}

	@Override
	public String getCacheTagHeaderName() {
		return _get(
			_cdnConfiguration.cacheTagHeaderName(),
			_cdnTemplate.getCacheTagHeaderName());
	}

	/**
	 * Returns the configured separator even when it is blank, because a space
	 * is a valid separator.
	 */
	@Override
	public String getCacheTagHeaderSeparator() {
		String cacheTagHeaderSeparator =
			_cdnConfiguration.cacheTagHeaderSeparator();

		if ((cacheTagHeaderSeparator == null) ||
			cacheTagHeaderSeparator.isEmpty()) {

			return _cdnTemplate.getCacheTagHeaderSeparator();
		}

		return cacheTagHeaderSeparator;
	}

	/**
	 * Returns the credential with its key reference, if any, resolved to the
	 * stored secret.
	 */
	public String getCredential() {
		return SecretResolverUtil.resolve(
			_companyId, _cdnConfiguration.credential());
	}

	@Override
	public String getDescription(Locale locale) {
		return _cdnTemplate.getDescription(locale);
	}

	@Override
	public String getDocumentationURL() {
		return _cdnTemplate.getDocumentationURL();
	}

	@Override
	public String getEndpoint() {
		String endpoint = _get(
			_cdnConfiguration.endpoint(), _cdnTemplate.getEndpoint());

		if ((endpoint != null) && endpoint.endsWith(StringPool.SLASH)) {
			return endpoint.substring(0, endpoint.length() - 1);
		}

		return endpoint;
	}

	@Override
	public String getKey() {
		return _cdnTemplate.getKey();
	}

	@Override
	public int getMaxTagsPerRequest() {
		return _get(
			_cdnConfiguration.maxTagsPerRequest(),
			_cdnTemplate.getMaxTagsPerRequest());
	}

	@Override
	public int getMaxURLsPerRequest() {
		return _get(
			_cdnConfiguration.maxURLsPerRequest(),
			_cdnTemplate.getMaxURLsPerRequest());
	}

	@Override
	public String getName(Locale locale) {
		return _cdnTemplate.getName(locale);
	}

	@Override
	public CDNHTTPRequestTemplate getPurgeAllCDNHTTPRequestTemplate() {
		return _getCDNHTTPRequestTemplate(
			_cdnTemplate.getPurgeAllCDNHTTPRequestTemplate(),
			_cdnConfiguration.purgeAllBodyTemplate(),
			_cdnConfiguration.purgeAllHeaders(),
			_cdnConfiguration.purgeAllMethod(),
			_cdnConfiguration.purgeAllURLTemplate());
	}

	public String getServiceId() {
		return _cdnConfiguration.serviceId();
	}

	@Override
	public String getSoftPurgeHeader() {
		return _get(
			_cdnConfiguration.softPurgeHeader(),
			_cdnTemplate.getSoftPurgeHeader());
	}

	@Override
	public CDNHTTPRequestTemplate getTagPurgeCDNHTTPRequestTemplate() {
		return _getCDNHTTPRequestTemplate(
			_cdnTemplate.getTagPurgeCDNHTTPRequestTemplate(),
			_cdnConfiguration.tagPurgeBodyTemplate(),
			_cdnConfiguration.tagPurgeHeaders(),
			_cdnConfiguration.tagPurgeMethod(),
			_cdnConfiguration.tagPurgeURLTemplate());
	}

	@Override
	public CDNHTTPRequestTemplate getURLPurgeCDNHTTPRequestTemplate() {
		return _getCDNHTTPRequestTemplate(
			_cdnTemplate.getURLPurgeCDNHTTPRequestTemplate(),
			_cdnConfiguration.urlPurgeBodyTemplate(),
			_cdnConfiguration.urlPurgeHeaders(),
			_cdnConfiguration.urlPurgeMethod(),
			_cdnConfiguration.urlPurgeURLTemplate());
	}

	@Override
	public boolean isURLPurgePerURL() {
		String urlPurgePerURL = _cdnConfiguration.urlPurgePerURL();

		if (Validator.isNull(urlPurgePerURL)) {
			return _cdnTemplate.isURLPurgePerURL();
		}

		return GetterUtil.getBoolean(urlPurgePerURL);
	}

	private int _get(int configurationValue, int templateValue) {
		if (configurationValue > 0) {
			return configurationValue;
		}

		return templateValue;
	}

	private String _get(String configurationValue, String templateValue) {
		if (Validator.isNotNull(configurationValue)) {
			return configurationValue;
		}

		return templateValue;
	}

	/**
	 * Returns the template's request with the configured values applied, or
	 * <code>null</code> when neither the template nor the configuration
	 * defines a URL for the request.
	 */
	private CDNHTTPRequestTemplate _getCDNHTTPRequestTemplate(
		CDNHTTPRequestTemplate cdnHTTPRequestTemplate, String bodyTemplate,
		String[] headers, String method, String urlTemplate) {

		CDNHTTPRequestTemplate.Builder builder =
			CDNHTTPRequestTemplate.builder();

		List<String> configuredHeaders = new ArrayList<>();

		if (headers != null) {
			for (String header : headers) {
				if (Validator.isNull(header)) {
					continue;
				}

				configuredHeaders.add(header);
			}
		}

		if (cdnHTTPRequestTemplate != null) {
			builder.bodyTemplate(
				cdnHTTPRequestTemplate.getBodyTemplate()
			).contentType(
				cdnHTTPRequestTemplate.getContentType()
			).method(
				cdnHTTPRequestTemplate.getMethod()
			).urlTemplate(
				cdnHTTPRequestTemplate.getURLTemplate()
			);

			if (configuredHeaders.isEmpty()) {
				builder.headers(cdnHTTPRequestTemplate.getHeaders());
			}
		}

		if (Validator.isNotNull(bodyTemplate)) {
			builder.bodyTemplate(bodyTemplate);
		}

		if (Validator.isNotNull(_cdnConfiguration.contentType())) {
			builder.contentType(_cdnConfiguration.contentType());
		}

		builder.headers(configuredHeaders);

		if (Validator.isNotNull(method)) {
			builder.method(method);
		}

		if (Validator.isNotNull(urlTemplate)) {
			builder.urlTemplate(urlTemplate);
		}

		CDNHTTPRequestTemplate configuredCDNHTTPRequestTemplate =
			builder.build();

		if (Validator.isNull(
				configuredCDNHTTPRequestTemplate.getURLTemplate())) {

			return null;
		}

		return configuredCDNHTTPRequestTemplate;
	}

	private final CDNConfiguration _cdnConfiguration;
	private final CDNTemplate _cdnTemplate;
	private final long _companyId;

}