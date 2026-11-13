/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.internal.servlet.filter;

import com.liferay.fragment.internal.renderer.FragmentAssetsRegistrar;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.BaseFilter;
import com.liferay.portal.kernel.servlet.HttpMethods;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.annotations.Component;

/**
 * Remembers the resource hints of each page composition after it is rendered,
 * and sends them as a <code>103 Early Hints</code> response on the next
 * requests, before the page starts rendering, so that browsers fetch Clay, the
 * theme, and the module fragment resources during the server think time.
 *
 * <p>
 * Spike experiment, enabled through the <code>early-hints</code> value of the
 * <code>fragmentAssets</code> parameter. Browsers only act on early hints over
 * HTTP/2 or later.
 * </p>
 *
 * @author Gianmarco Brunialti
 */
@Component(
	property = {
		"before-filter=Header Filter", "dispatcher=REQUEST",
		"servlet-context-name=",
		"servlet-filter-name=Fragment Assets Early Hints Filter",
		"url-pattern=/*"
	},
	service = Filter.class
)
public class FragmentAssetsEarlyHintsFilter extends BaseFilter {

	@Override
	public boolean isFilterEnabled(
		HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		if (!HttpMethods.GET.equals(httpServletRequest.getMethod())) {
			return false;
		}

		String queryString = httpServletRequest.getQueryString();

		if ((queryString == null) || !queryString.contains("early-hints") ||
			!ArrayUtil.contains(
				StringUtil.split(
					httpServletRequest.getParameter("fragmentAssets")),
				"early-hints")) {

			return false;
		}

		return true;
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

		String key = StringBundler.concat(
			httpServletRequest.getRequestURI(), StringPool.QUESTION,
			httpServletRequest.getQueryString());

		Set<String> links = _links.get(key);

		if (links != null) {
			httpServletResponse.setHeader(
				"Link", StringUtil.merge(links, StringPool.COMMA_AND_SPACE));
			httpServletResponse.sendError(_SC_EARLY_HINTS);
		}

		filterChain.doFilter(httpServletRequest, httpServletResponse);

		if ((links != null) ||
			(httpServletResponse.getStatus() != HttpServletResponse.SC_OK)) {

			return;
		}

		Set<String> fragmentAssetsLinks = FragmentAssetsRegistrar.getLinks(
			httpServletRequest);

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		if (SetUtil.isEmpty(fragmentAssetsLinks) || (themeDisplay == null) ||
			themeDisplay.isSignedIn()) {

			return;
		}

		links = new LinkedHashSet<>();

		for (String url :
				new String[] {
					themeDisplay.getClayCSSURL(), themeDisplay.getMainCSSURL()
				}) {

			if (Validator.isNotNull(url)) {
				links.add(
					StringBundler.concat(
						"<", url, ">; rel=preload; as=style"));
			}
		}

		links.addAll(fragmentAssetsLinks);

		if (_links.size() >= _MAX_SIZE) {
			_links.clear();
		}

		_links.put(key, links);
	}

	private static final int _MAX_SIZE = 1000;

	private static final int _SC_EARLY_HINTS = 103;

	private static final Log _log = LogFactoryUtil.getLog(
		FragmentAssetsEarlyHintsFilter.class);

	private final Map<String, Set<String>> _links = new ConcurrentHashMap<>();

}
