/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag;

import com.liferay.portal.kernel.util.StringUtil;

import java.nio.charset.StandardCharsets;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNCacheTagHeaderUtil {

	/**
	 * Returns the cache tag header value. When all the tags do not fit in the
	 * byte budget, the model tags are replaced by the overflow tag, so the
	 * response is still purged by any model purge in its group. The company,
	 * group, and page tags are always kept.
	 */
	public static String getHeaderValue(
		Collection<String> cacheTags, int maxBytes,
		Collection<String> modelCacheTags, String overflowCacheTag,
		String separator) {

		Set<String> headerCacheTags = new LinkedHashSet<>(cacheTags);

		headerCacheTags.addAll(modelCacheTags);

		String headerValue = StringUtil.merge(headerCacheTags, separator);

		if (_getBytes(headerValue) <= maxBytes) {
			return headerValue;
		}

		headerCacheTags = new LinkedHashSet<>(cacheTags);

		headerCacheTags.add(overflowCacheTag);

		return StringUtil.merge(headerCacheTags, separator);
	}

	private static int _getBytes(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		return bytes.length;
	}

}