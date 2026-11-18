/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.template;

import com.liferay.cdn.template.CDNHTTPRequestTemplate;
import com.liferay.cdn.template.CDNTemplate;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;

import java.util.Locale;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Purges Fastly through its API. Cache tags are purged with the bulk endpoint,
 * which takes the surrogate keys in a header, so keys that contain a slash
 * work. A URL is purged with one request per URL.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNTemplate.class)
public class FastlyCDNTemplate implements CDNTemplate {

	/**
	 * Leaves 2 KB of Fastly's 16 KB Surrogate-Key limit for the keys the edge
	 * appends to the ones the portal sends.
	 */
	@Override
	public int getCacheTagHeaderMaxBytes() {
		return 14336;
	}

	@Override
	public String getCacheTagHeaderName() {
		return "Surrogate-Key";
	}

	@Override
	public String getCacheTagHeaderSeparator() {
		return StringPool.SPACE;
	}

	@Override
	public String getDescription(Locale locale) {
		return _language.get(locale, "cdn-template-fastly-description");
	}

	@Override
	public String getDocumentationURL() {
		return "https://www.fastly.com/documentation/reference/api/purging";
	}

	@Override
	public String getEndpoint() {
		return "https://api.fastly.com";
	}

	@Override
	public String getKey() {
		return "fastly";
	}

	@Override
	public int getMaxTagsPerRequest() {
		return 256;
	}

	@Override
	public int getMaxURLsPerRequest() {
		return 1;
	}

	@Override
	public String getName(Locale locale) {
		return "Fastly";
	}

	@Override
	public CDNHTTPRequestTemplate getPurgeAllCDNHTTPRequestTemplate() {
		return CDNHTTPRequestTemplate.builder(
		).headers(
			_HEADERS
		).urlTemplate(
			"${endpoint}/service/${serviceId}/purge_all"
		).build();
	}

	@Override
	public String getSoftPurgeHeader() {
		return "Fastly-Soft-Purge: 1";
	}

	@Override
	public CDNHTTPRequestTemplate getTagPurgeCDNHTTPRequestTemplate() {
		return CDNHTTPRequestTemplate.builder(
		).headers(
			_HEADERS
		).headers(
			"Surrogate-Key: ${tagsHeader}"
		).urlTemplate(
			"${endpoint}/service/${serviceId}/purge"
		).build();
	}

	@Override
	public CDNHTTPRequestTemplate getURLPurgeCDNHTTPRequestTemplate() {
		return CDNHTTPRequestTemplate.builder(
		).headers(
			_HEADERS
		).urlTemplate(
			"${endpoint}/purge/${urlWithoutProtocol}"
		).build();
	}

	@Override
	public boolean isURLPurgePerURL() {
		return true;
	}

	private static final String[] _HEADERS = {
		"Accept: application/json", "Fastly-Key: ${credential}"
	};

	@Reference
	private Language _language;

}