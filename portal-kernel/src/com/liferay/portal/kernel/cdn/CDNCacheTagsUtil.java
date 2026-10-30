/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.cdn;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects the CDN cache tags of the content rendered for a request, so the
 * response can tell the CDN which content it holds. The tags live in a shared
 * request attribute, so tags added while rendering a portlet reach the portal
 * request.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNCacheTagsUtil {

	public static void addCacheTags(
		HttpServletRequest httpServletRequest, String... cacheTags) {

		Set<String> requestCacheTags =
			(Set<String>)httpServletRequest.getAttribute(_CACHE_TAGS);

		if (requestCacheTags == null) {
			requestCacheTags = new LinkedHashSet<>();

			httpServletRequest.setAttribute(_CACHE_TAGS, requestCacheTags);
		}

		for (String cacheTag : cacheTags) {
			if (cacheTag == null) {
				continue;
			}

			requestCacheTags.add(cacheTag);
		}
	}

	public static Set<String> getCacheTags(
		HttpServletRequest httpServletRequest) {

		Set<String> requestCacheTags =
			(Set<String>)httpServletRequest.getAttribute(_CACHE_TAGS);

		if (requestCacheTags == null) {
			return Collections.emptySet();
		}

		return Collections.unmodifiableSet(requestCacheTags);
	}

	private static final String _CACHE_TAGS = "LIFERAY_SHARED_CDN_CACHE_TAGS";

}