/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag.resolver;

import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;

/**
 * @author Gianmarco Brunialti Masera
 */
public class ObjectEntryCDNCacheTagModelResolver
	implements CDNCacheTagModelResolver {

	public ObjectEntryCDNCacheTagModelResolver(String className) {
		_className = className;
	}

	@Override
	public String getCacheTag(long classPK) {
		return "o" + classPK;
	}

	@Override
	public String getClassName() {
		return _className;
	}

	private final String _className;

}