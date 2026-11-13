/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.cache.tag;

import com.liferay.portal.kernel.model.Layout;

import java.util.Set;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Turns models into CDN cache tags. It is the only source of cache tags, so
 * the tags a response carries and the tags a purge targets cannot drift apart.
 * Every tag starts with the company's cache tag namespace.
 *
 * @author Gianmarco Brunialti Masera
 */
@ProviderType
public interface CDNCacheTagBuilder {

	public String getCollectionCacheTag(
		long companyId, long groupId, long classNameId);

	public String getCompanyCacheTag(long companyId);

	public String getGroupCacheTag(long companyId, long groupId);

	/**
	 * Returns the cache tags of the page and, when it has one, of its master
	 * page.
	 */
	public Set<String> getLayoutCacheTags(Layout layout);

	/**
	 * Returns the cache tag of the model, or <code>null</code> when no cache
	 * tag is defined for the model's class.
	 */
	public String getModelCacheTag(
		long companyId, String className, long classPK);

	/**
	 * Returns the cache tag a response carries instead of its model cache tags
	 * when they do not fit in the cache tag header. Every model purge in the
	 * group also purges it.
	 */
	public String getOverflowCacheTag(long companyId, long groupId);

}