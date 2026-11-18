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
 * Purges Cloudflare through its API. Cloudflare has no soft purge, so every
 * purge evicts the cached objects. Cloudflare accepts 100 cache tags or 100
 * URLs per request (500 URLs on the Enterprise plan, which the configuration
 * can set) and a Cache-Tag header of up to 16 KB.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNTemplate.class)
public class CloudflareCDNTemplate implements CDNTemplate {

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
		return _language.get(locale, "cdn-template-cloudflare-description");
	}

	@Override
	public String getDocumentationURL() {
		return "https://developers.cloudflare.com/cache/how-to/purge-cache";
	}

	@Override
	public String getEndpoint() {
		return "https://api.cloudflare.com";
	}

	@Override
	public String getKey() {
		return "cloudflare";
	}

	@Override
	public int getMaxTagsPerRequest() {
		return 100;
	}

	@Override
	public int getMaxURLsPerRequest() {
		return 100;
	}

	@Override
	public String getName(Locale locale) {
		return "Cloudflare";
	}

	@Override
	public CDNHTTPRequestTemplate getPurgeAllCDNHTTPRequestTemplate() {
		return _getCDNHTTPRequestTemplate("{\"purge_everything\": true}");
	}

	@Override
	public String getSoftPurgeHeader() {
		return null;
	}

	@Override
	public CDNHTTPRequestTemplate getTagPurgeCDNHTTPRequestTemplate() {
		return _getCDNHTTPRequestTemplate("{\"tags\": ${tags}}");
	}

	@Override
	public CDNHTTPRequestTemplate getURLPurgeCDNHTTPRequestTemplate() {
		return _getCDNHTTPRequestTemplate("{\"files\": ${urls}}");
	}

	@Override
	public boolean isURLPurgePerURL() {
		return false;
	}

	private CDNHTTPRequestTemplate _getCDNHTTPRequestTemplate(
		String bodyTemplate) {

		return CDNHTTPRequestTemplate.builder(
		).bodyTemplate(
			bodyTemplate
		).headers(
			"Authorization: Bearer ${credential}"
		).urlTemplate(
			"${endpoint}/client/v4/zones/${serviceId}/purge_cache"
		).build();
	}

	@Reference
	private Language _language;

}