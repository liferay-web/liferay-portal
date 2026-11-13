/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.events;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.constants.CDNConstants;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.feature.flag.FeatureFlagManagerUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Objects;

/**
 * Manages the cookie that tells the CDN a request comes from a signed in user,
 * so the edge never serves or caches a signed in response for guests.
 *
 * <p>
 * The cookie is written directly rather than through the cookies manager. The
 * CDN reads it on every path, while the cookies manager sets the portal's
 * context path, and the cookies manager adds no cookie at all when
 * <code>session.enable.persistent.cookies</code> is <code>false</code>. The
 * cookie is a session cookie that is strictly necessary for the portal to
 * work behind a CDN, so it needs no consent.
 * </p>
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNSignedInMarkerUtil {

	public static final String COOKIE_NAME = "LFR_SESSION_LOGIN";

	/**
	 * Adds the marker unless the request already carries it or an earlier
	 * action added it to this response.
	 */
	public static void addCookie(
		HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse, boolean secure) {

		if (hasCookie(httpServletRequest) ||
			(httpServletRequest.getAttribute(_COOKIE_ADDED) != null)) {

			return;
		}

		httpServletResponse.addCookie(_createCookie("1", -1, secure));

		httpServletRequest.setAttribute(_COOKIE_ADDED, Boolean.TRUE);
	}

	public static void deleteCookie(
		HttpServletResponse httpServletResponse, boolean secure) {

		httpServletResponse.addCookie(
			_createCookie(StringPool.BLANK, 0, secure));
	}

	public static boolean hasCookie(HttpServletRequest httpServletRequest) {
		Cookie[] cookies = httpServletRequest.getCookies();

		if (cookies == null) {
			return false;
		}

		for (Cookie cookie : cookies) {
			if (Objects.equals(cookie.getName(), COOKIE_NAME)) {
				return true;
			}
		}

		return false;
	}

	public static boolean isEnabled(
		ConfigurationProvider configurationProvider, long companyId) {

		if (!FeatureFlagManagerUtil.isEnabled(
				companyId, CDNConstants.FEATURE_FLAG_KEY)) {

			return false;
		}

		try {
			CDNConfiguration cdnConfiguration =
				configurationProvider.getCompanyConfiguration(
					CDNConfiguration.class, companyId);

			return cdnConfiguration.signedInMarkerEnabled();
		}
		catch (ConfigurationException configurationException) {
			_log.error(
				"Unable to get the CDN configuration for company " + companyId,
				configurationException);

			return false;
		}
	}

	private static Cookie _createCookie(
		String value, int maxAge, boolean secure) {

		Cookie cookie = new Cookie(COOKIE_NAME, value);

		cookie.setAttribute("SameSite", "Lax");
		cookie.setHttpOnly(true);
		cookie.setMaxAge(maxAge);
		cookie.setPath(StringPool.SLASH);
		cookie.setSecure(secure);

		return cookie;
	}

	private static final String _COOKIE_ADDED =
		CDNSignedInMarkerUtil.class.getName() + "#COOKIE_ADDED";

	private static final Log _log = LogFactoryUtil.getLog(
		CDNSignedInMarkerUtil.class);

}