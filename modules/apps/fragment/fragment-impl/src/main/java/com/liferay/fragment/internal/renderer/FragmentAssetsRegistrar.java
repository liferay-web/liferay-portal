/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.internal.renderer;

import com.liferay.fragment.assets.FragmentAssets;
import com.liferay.fragment.renderer.FragmentRendererContext;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.content.security.policy.ContentSecurityPolicyNonceProviderUtil;
import com.liferay.portal.kernel.servlet.taglib.util.OutputData;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.url.builder.AbsolutePortalURLBuilderFactory;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashSet;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Registers the resources of module fragments once per page. In view mode
 * the tags go to the page head through {@link WebKeys#PAGE_TOP}. When the head
 * is not going to be written (edit, preview, and index modes, and isolated,
 * resource, exclusive, or PJAX requests) they are returned to be written next
 * to the fragment instead. It also flags the request so that the page head
 * declares the CSS cascade layer order before any style sheet.
 *
 * <p>
 * Registration runs on every render, including fragment cache hits, so it
 * must never be part of the cached fragment content.
 * </p>
 *
 * @author Gianmarco Brunialti
 */
@Component(service = FragmentAssetsRegistrar.class)
public class FragmentAssetsRegistrar {

	public String register(
		FragmentAssets fragmentAssets,
		FragmentRendererContext fragmentRendererContext,
		HttpServletRequest httpServletRequest) {

		httpServletRequest.setAttribute(_CSS_LAYERS, Boolean.TRUE);

		Set<String> links = _getLinks(httpServletRequest);

		String nonce = ContentSecurityPolicyNonceProviderUtil.getNonceAttribute(
			httpServletRequest);

		if (_isInline(fragmentRendererContext, httpServletRequest)) {
			StringBundler sb = new StringBundler();

			for (String styleSheetURL : fragmentAssets.getStyleSheetURLs()) {
				sb.append(_getStyleSheetTag(nonce, styleSheetURL));
			}

			if (fragmentAssets.hasModule()) {
				sb.append(_getLoaderTag(httpServletRequest, nonce));
			}

			return sb.toString();
		}

		OutputData outputData = _getOutputData(httpServletRequest);

		for (String styleSheetURL : fragmentAssets.getStyleSheetURLs()) {
			if (_add(
					"fragment-assets-css-" + styleSheetURL,
					_getStyleSheetTag(nonce, styleSheetURL), outputData)) {

				links.add(
					StringBundler.concat(
						"<", styleSheetURL, ">; rel=preload; as=style"));
			}
		}

		for (FragmentAssets.Preconnect preconnect :
				fragmentAssets.getPreconnects()) {

			if ((_getCount("preconnect", httpServletRequest) >=
					_MAX_PRECONNECTS) ||
				!_add(
					"fragment-assets-preconnect-" + preconnect.getOrigin(),
					_getPreconnectTag(preconnect), outputData)) {

				continue;
			}

			_incrementCount("preconnect", httpServletRequest);

			links.add(
				StringBundler.concat(
					"<", preconnect.getOrigin(), ">; rel=preconnect",
					preconnect.isCrossOrigin() ? "; crossorigin" :
						StringPool.BLANK));
		}

		for (FragmentAssets.Preload preload : fragmentAssets.getPreloads()) {
			if (!preload.isAboveTheFold() ||
				(_getCount("preload", httpServletRequest) >= _MAX_PRELOADS) ||
				!_add(
					"fragment-assets-preload-" + preload.getHref(),
					_getPreloadTag(preload), outputData)) {

				continue;
			}

			_incrementCount("preload", httpServletRequest);

			links.add(
				StringBundler.concat(
					"<", preload.getHref(), ">; rel=preload; as=",
					preload.getAs()));
		}

		if (!fragmentAssets.hasModule()) {
			return StringPool.BLANK;
		}

		if (FragmentAssets.HYDRATE_EAGER.equals(fragmentAssets.getHydrate())) {
			if (_add(
					"fragment-assets-modulepreload-" +
						fragmentAssets.getModuleURL(),
					_getModulePreloadTag(nonce, fragmentAssets.getModuleURL()),
					outputData)) {

				links.add(
					StringBundler.concat(
						"<", fragmentAssets.getModuleURL(),
						">; rel=modulepreload"));
			}

			for (String moduleDependencyURL :
					fragmentAssets.getModuleDependencyURLs()) {

				if (_add(
						"fragment-assets-modulepreload-" + moduleDependencyURL,
						_getModulePreloadTag(nonce, moduleDependencyURL),
						outputData)) {

					links.add(
						StringBundler.concat(
							"<", moduleDependencyURL, ">; rel=modulepreload"));
				}
			}
		}

		_add(
			"fragment-assets-loader",
			_getLoaderTag(httpServletRequest, nonce), outputData);

		return StringPool.BLANK;
	}

	private boolean _add(String key, String html, OutputData outputData) {
		if (!outputData.addOutputKey(key)) {
			return false;
		}

		outputData.addDataSB(key, WebKeys.PAGE_TOP, new StringBundler(html));

		return true;
	}

	private int _getCount(String name, HttpServletRequest httpServletRequest) {
		return GetterUtil.getInteger(
			httpServletRequest.getAttribute(_COUNT_PREFIX + name));
	}

	private String _getLoaderTag(
		HttpServletRequest httpServletRequest, String nonce) {

		return StringBundler.concat(
			"<script data-senna-track=\"permanent\" ", nonce, " src=\"",
			HtmlUtil.escapeAttribute(
				_absolutePortalURLBuilderFactory.getAbsolutePortalURLBuilder(
					httpServletRequest
				).forESModule(
					"fragment-impl", "loader.js"
				).build()),
			"\" type=\"module\"></script>");
	}

	private String _getModulePreloadTag(String nonce, String url) {
		return StringBundler.concat(
			"<link data-senna-track=\"temporary\" href=\"",
			HtmlUtil.escapeAttribute(url), "\" ", nonce,
			" rel=\"modulepreload\" />");
	}

	private OutputData _getOutputData(HttpServletRequest httpServletRequest) {
		OutputData outputData = (OutputData)httpServletRequest.getAttribute(
			WebKeys.OUTPUT_DATA);

		if (outputData == null) {
			outputData = new OutputData();

			httpServletRequest.setAttribute(WebKeys.OUTPUT_DATA, outputData);
		}

		return outputData;
	}

	private String _getPreconnectTag(FragmentAssets.Preconnect preconnect) {
		return StringBundler.concat(
			"<link ", preconnect.isCrossOrigin() ? "crossorigin " :
				StringPool.BLANK,
			"data-senna-track=\"temporary\" href=\"",
			HtmlUtil.escapeAttribute(preconnect.getOrigin()),
			"\" rel=\"preconnect\" />");
	}

	private String _getPreloadTag(FragmentAssets.Preload preload) {
		StringBundler sb = new StringBundler(14);

		sb.append("<link as=\"");
		sb.append(HtmlUtil.escapeAttribute(preload.getAs()));
		sb.append("\" ");

		if (preload.isCrossOrigin()) {
			sb.append("crossorigin ");
		}

		sb.append("data-senna-track=\"temporary\" ");

		if (Validator.isNotNull(preload.getFetchPriority())) {
			sb.append("fetchpriority=\"");
			sb.append(HtmlUtil.escapeAttribute(preload.getFetchPriority()));
			sb.append("\" ");
		}

		sb.append("href=\"");
		sb.append(HtmlUtil.escapeAttribute(preload.getHref()));
		sb.append("\" rel=\"preload\"");

		if (Validator.isNotNull(preload.getType())) {
			sb.append(" type=\"");
			sb.append(HtmlUtil.escapeAttribute(preload.getType()));
			sb.append("\"");
		}

		sb.append(" />");

		return sb.toString();
	}

	private String _getStyleSheetTag(String nonce, String url) {
		return StringBundler.concat(
			"<link data-senna-track=\"temporary\" href=\"",
			HtmlUtil.escapeAttribute(url), "\" ", nonce,
			" rel=\"stylesheet\" />");
	}

	@SuppressWarnings("unchecked")
	private Set<String> _getLinks(HttpServletRequest httpServletRequest) {
		Set<String> links = (Set<String>)httpServletRequest.getAttribute(
			FragmentAssetsRegistrar.class.getName());

		if (links == null) {
			links = new LinkedHashSet<>();

			httpServletRequest.setAttribute(
				FragmentAssetsRegistrar.class.getName(), links);
		}

		return links;
	}

	private void _incrementCount(
		String name, HttpServletRequest httpServletRequest) {

		httpServletRequest.setAttribute(
			_COUNT_PREFIX + name, _getCount(name, httpServletRequest) + 1);
	}

	private boolean _isInline(
		FragmentRendererContext fragmentRendererContext,
		HttpServletRequest httpServletRequest) {

		if (!fragmentRendererContext.isViewMode() ||
			GetterUtil.getBoolean(httpServletRequest.getHeader("X-PJAX"))) {

			return true;
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		if ((themeDisplay == null) || themeDisplay.isIsolated() ||
			themeDisplay.isLifecycleResource() ||
			themeDisplay.isStateExclusive()) {

			return true;
		}

		return false;
	}

	private static final String _COUNT_PREFIX =
		FragmentAssetsRegistrar.class.getName() + "#";

	private static final String _CSS_LAYERS = "LIFERAY_SHARED_CSS_LAYERS";

	private static final int _MAX_PRECONNECTS = 4;

	private static final int _MAX_PRELOADS = 2;

	@Reference
	private AbsolutePortalURLBuilderFactory _absolutePortalURLBuilderFactory;

}
