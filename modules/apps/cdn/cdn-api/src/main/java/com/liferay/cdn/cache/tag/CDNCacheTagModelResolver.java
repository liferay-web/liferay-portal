/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.cache.tag;

/**
 * Defines the CDN cache tag of the models of one class, so applications can
 * make their own content purgeable by tag. A resolver for a class replaces the
 * built in definition for that class.
 *
 * @author Gianmarco Brunialti Masera
 */
public interface CDNCacheTagModelResolver {

	/**
	 * Returns the cache tag of the model without the namespace, or
	 * <code>null</code> when the model has no cache tag. The value must be
	 * stable for the life of the model and short, because many tags share one
	 * response header.
	 */
	public String getCacheTag(long classPK);

	public String getClassName();

}