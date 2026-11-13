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
 * Sends every purge as one POST to the configured endpoint, with a JSON body
 * that carries the cache tags, the URLs, and the purge flags. It is the
 * template for a client extension or a proxy that translates the purge for a
 * CDN without a template, or signs it, as Akamai (EdgeGrid) and Amazon
 * CloudFront (Signature Version 4) require. Every request can be overridden in
 * the configuration.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNTemplate.class)
public class CustomCDNTemplate implements CDNTemplate {

	@Override
	public int getCacheTagHeaderMaxBytes() {
		return 16384;
	}

	@Override
	public String getCacheTagHeaderName() {
		return "Cache-Tag";
	}

	@Override
	public String getCacheTagHeaderSeparator() {
		return StringPool.COMMA;
	}

	@Override
	public String getDescription(Locale locale) {
		return _language.get(locale, "cdn-template-custom-description");
	}

	@Override
	public String getDocumentationURL() {
		return null;
	}

	@Override
	public String getEndpoint() {
		return null;
	}

	@Override
	public String getKey() {
		return "custom";
	}

	@Override
	public int getMaxTagsPerRequest() {
		return 256;
	}

	@Override
	public int getMaxURLsPerRequest() {
		return 256;
	}

	@Override
	public String getName(Locale locale) {
		return _language.get(locale, "custom-or-proxy");
	}

	@Override
	public CDNHTTPRequestTemplate getPurgeAllCDNHTTPRequestTemplate() {
		return _cdnHTTPRequestTemplate;
	}

	@Override
	public String getSoftPurgeHeader() {
		return null;
	}

	@Override
	public CDNHTTPRequestTemplate getTagPurgeCDNHTTPRequestTemplate() {
		return _cdnHTTPRequestTemplate;
	}

	@Override
	public CDNHTTPRequestTemplate getURLPurgeCDNHTTPRequestTemplate() {
		return _cdnHTTPRequestTemplate;
	}

	@Override
	public boolean isURLPurgePerURL() {
		return false;
	}

	private static final CDNHTTPRequestTemplate _cdnHTTPRequestTemplate =
		CDNHTTPRequestTemplate.builder(
		).bodyTemplate(
			"{\"purgeAll\": ${purgeAll}, \"soft\": ${soft}, \"tags\": " +
				"${tags}, \"urls\": ${urls}}"
		).urlTemplate(
			"${endpoint}"
		).build();

	@Reference
	private Language _language;

}