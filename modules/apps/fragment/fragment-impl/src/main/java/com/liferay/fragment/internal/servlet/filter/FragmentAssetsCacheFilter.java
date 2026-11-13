/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.internal.servlet.filter;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.BaseFilter;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.util.StringUtil;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Component;

/**
 * Marks the content hashed assets of module fragments, served from the
 * <code>dist</code> folder of a bundle or a client extension, as immutable.
 * Later filters, such as the language filter, cannot override the headers.
 *
 * @author Gianmarco Brunialti
 */
@Component(
	property = {
		"before-filter=Header Filter", "dispatcher=REQUEST",
		"servlet-context-name=",
		"servlet-filter-name=Fragment Assets Cache Filter", "url-pattern=/o/*"
	},
	service = Filter.class
)
public class FragmentAssetsCacheFilter extends BaseFilter {

	@Override
	public boolean isFilterEnabled(
		HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		Matcher matcher = _pattern.matcher(
			httpServletRequest.getRequestURI());

		return matcher.matches();
	}

	@Override
	protected Log getLog() {
		return _log;
	}

	@Override
	protected void processFilter(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, FilterChain filterChain)
		throws Exception {

		httpServletResponse.setHeader(
			HttpHeaders.CACHE_CONTROL, _CACHE_CONTROL_IMMUTABLE);

		filterChain.doFilter(
			httpServletRequest,
			new HttpServletResponseWrapper(httpServletResponse) {

				@Override
				public void addHeader(String name, String value) {
					if (!_isCacheHeader(name)) {
						super.addHeader(name, value);
					}
				}

				@Override
				public void setHeader(String name, String value) {
					if (!_isCacheHeader(name)) {
						super.setHeader(name, value);
					}
				}

			});
	}

	private static boolean _isCacheHeader(String name) {
		if (StringUtil.equalsIgnoreCase(name, HttpHeaders.CACHE_CONTROL) ||
			StringUtil.equalsIgnoreCase(name, HttpHeaders.EXPIRES) ||
			StringUtil.equalsIgnoreCase(name, HttpHeaders.PRAGMA)) {

			return true;
		}

		return false;
	}

	private static final String _CACHE_CONTROL_IMMUTABLE =
		"public, max-age=31536000, immutable";

	private static final Log _log = LogFactoryUtil.getLog(
		FragmentAssetsCacheFilter.class);

	private static final Pattern _pattern = Pattern.compile(
		"/o/[^/]+/dist/[^/]+\\.[0-9a-f]{8,}\\.(avif|css|gif|jpe?g|js|map|png|" +
			"svg|webp|woff2?)");

}
