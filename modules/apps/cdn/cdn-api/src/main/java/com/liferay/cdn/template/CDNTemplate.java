/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.template;

import java.util.Locale;

/**
 * Describes how to purge one CDN through its HTTP API, and which header the
 * CDN reads cache tags from. The CDN integration has a single generic HTTP
 * purger, and a template only supplies the values it sends, so supporting
 * another CDN takes a template rather than code. Register an implementation as
 * an OSGi service to make it selectable in the CDN configuration. Every value
 * the administrator fills in the configuration overrides the template's value.
 *
 * <p>
 * The URL templates, the header values, and the body templates accept the
 * variables <code>${credential}</code>, <code>${endpoint}</code>,
 * <code>${purgeAll}</code>, <code>${serviceId}</code>, <code>${soft}</code>,
 * <code>${tags}</code> and <code>${urls}</code> (JSON arrays),
 * <code>${tagsHeader}</code> (the tags joined by the cache tag header
 * separator), and, when each URL is purged with its own request,
 * <code>${url}</code> and <code>${urlWithoutProtocol}</code>.
 * </p>
 *
 * @author Gianmarco Brunialti Masera
 */
public interface CDNTemplate {

	public int getCacheTagHeaderMaxBytes();

	public String getCacheTagHeaderName();

	public String getCacheTagHeaderSeparator();

	public String getDescription(Locale locale);

	public String getDocumentationURL();

	/**
	 * Returns the API endpoint used when the configuration does not set one.
	 */
	public String getEndpoint();

	public String getKey();

	public int getMaxTagsPerRequest();

	public int getMaxURLsPerRequest();

	public String getName(Locale locale);

	/**
	 * Returns the request that purges everything, or <code>null</code> when
	 * the CDN cannot purge everything.
	 */
	public CDNHTTPRequestTemplate getPurgeAllCDNHTTPRequestTemplate();

	/**
	 * Returns the header, in the form <code>Name: value</code>, that every tag
	 * or URL request of a soft purge carries, or <code>null</code> when the
	 * CDN expresses soft purges in the body through <code>${soft}</code> or
	 * does not support them. The header is left out of hard purges and of
	 * purges of everything, because some CDNs soft purge whenever the header
	 * is present, whatever its value.
	 */
	public String getSoftPurgeHeader();

	/**
	 * Returns the request that purges a batch of cache tags, or
	 * <code>null</code> when the CDN cannot purge by tag.
	 */
	public CDNHTTPRequestTemplate getTagPurgeCDNHTTPRequestTemplate();

	/**
	 * Returns the request that purges URLs, or <code>null</code> when the CDN
	 * cannot purge by URL.
	 */
	public CDNHTTPRequestTemplate getURLPurgeCDNHTTPRequestTemplate();

	/**
	 * Returns whether each URL is purged with its own request, for CDNs whose
	 * API takes the URL in the path.
	 */
	public boolean isURLPurgePerURL();

}