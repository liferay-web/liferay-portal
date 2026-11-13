/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * Configures the CDN of a company. The selected template describes how to
 * purge the CDN and which header it reads cache tags from. Every purge request
 * field, header field, and limit left empty, or set to <code>0</code>, uses
 * the template's value, and any other value overrides it.
 *
 * @author Gianmarco Brunialti Masera
 */
@ExtendedObjectClassDefinition(
	category = "cdn", featureFlagKey = "LPD-107638",
	scope = ExtendedObjectClassDefinition.Scope.COMPANY
)
@Meta.OCD(
	id = "com.liferay.cdn.configuration.CDNConfiguration",
	localization = "content/Language", name = "cdn-configuration-name"
)
public interface CDNConfiguration {

	@Meta.AD(
		description = "cdn-cache-tag-header-name-help",
		name = "cache-tag-header-name", required = false
	)
	public String cacheTagHeaderName();

	@Meta.AD(
		description = "cdn-cache-tag-header-separator-help",
		name = "cache-tag-header-separator", required = false
	)
	public String cacheTagHeaderSeparator();

	@Meta.AD(
		deflt = "2000", description = "cdn-coalesce-window-ms-help",
		name = "coalesce-window-ms", required = false
	)
	public long coalesceWindowMs();

	@Meta.AD(
		description = "cdn-content-type-help", name = "content-type",
		required = false
	)
	public String contentType();

	@Meta.AD(
		description = "cdn-credential-help", name = "credential",
		required = false, type = Meta.Type.Password
	)
	public String credential();

	@Meta.AD(
		deflt = "false", description = "cdn-emit-cache-tags-help",
		name = "emit-cache-tags", required = false
	)
	public boolean emitCacheTags();

	@Meta.AD(deflt = "false", name = "enabled", required = false)
	public boolean enabled();

	@Meta.AD(
		description = "cdn-endpoint-help", name = "endpoint", required = false
	)
	public String endpoint();

	@Meta.AD(
		deflt = "0", description = "cdn-max-cache-tag-header-bytes-help",
		name = "max-cache-tag-header-bytes", required = false
	)
	public int maxCacheTagHeaderBytes();

	@Meta.AD(
		deflt = "0", description = "cdn-max-tags-per-request-help",
		name = "max-tags-per-request", required = false
	)
	public int maxTagsPerRequest();

	@Meta.AD(
		deflt = "0", description = "cdn-max-urls-per-request-help",
		name = "max-urls-per-request", required = false
	)
	public int maxURLsPerRequest();

	@Meta.AD(
		description = "cdn-body-template-help",
		name = "purge-all-body-template", required = false
	)
	public String purgeAllBodyTemplate();

	@Meta.AD(
		description = "cdn-headers-help", name = "purge-all-headers",
		required = false
	)
	public String[] purgeAllHeaders();

	@Meta.AD(
		description = "cdn-purge-all-method-help", name = "purge-all-method",
		required = false
	)
	public String purgeAllMethod();

	@Meta.AD(
		description = "cdn-url-template-help", name = "purge-all-url-template",
		required = false
	)
	public String purgeAllURLTemplate();

	@Meta.AD(
		description = "cdn-service-id-help", name = "service-id",
		required = false
	)
	public String serviceId();

	@Meta.AD(
		deflt = "true", description = "cdn-signed-in-marker-enabled-help",
		name = "signed-in-marker-enabled", required = false
	)
	public boolean signedInMarkerEnabled();

	/**
	 * Soft purge marks cached objects as stale instead of evicting them, so
	 * the edge keeps serving them while it revalidates against the origin. The
	 * stale window that the edge grants to soft purged objects (for example,
	 * Fastly's stale-while-revalidate and grace) must be short. Otherwise, the
	 * old content stays visible long after the purge.
	 */
	@Meta.AD(
		deflt = "true", description = "cdn-soft-purge-help",
		name = "soft-purge", required = false
	)
	public boolean softPurge();

	@Meta.AD(
		description = "cdn-soft-purge-header-help", name = "soft-purge-header",
		required = false
	)
	public String softPurgeHeader();

	@Meta.AD(
		description = "cdn-body-template-help",
		name = "tag-purge-body-template", required = false
	)
	public String tagPurgeBodyTemplate();

	@Meta.AD(
		description = "cdn-headers-help", name = "tag-purge-headers",
		required = false
	)
	public String[] tagPurgeHeaders();

	@Meta.AD(
		description = "cdn-tag-purge-method-help", name = "tag-purge-method",
		required = false
	)
	public String tagPurgeMethod();

	@Meta.AD(
		description = "cdn-url-template-help", name = "tag-purge-url-template",
		required = false
	)
	public String tagPurgeURLTemplate();

	@Meta.AD(
		deflt = "custom", description = "cdn-template-key-help",
		name = "template", required = false
	)
	public String templateKey();

	@Meta.AD(
		description = "cdn-body-template-help",
		name = "url-purge-body-template", required = false
	)
	public String urlPurgeBodyTemplate();

	@Meta.AD(
		description = "cdn-headers-help", name = "url-purge-headers",
		required = false
	)
	public String[] urlPurgeHeaders();

	@Meta.AD(
		description = "cdn-url-purge-method-help", name = "url-purge-method",
		required = false
	)
	public String urlPurgeMethod();

	@Meta.AD(
		deflt = "", description = "cdn-url-purge-per-url-help",
		name = "url-purge-per-url",
		optionLabels = {"use-template-value", "yes", "no"},
		optionValues = {"", "true", "false"}, required = false
	)
	public String urlPurgePerURL();

	@Meta.AD(
		description = "cdn-url-template-help", name = "url-purge-url-template",
		required = false
	)
	public String urlPurgeURLTemplate();

}