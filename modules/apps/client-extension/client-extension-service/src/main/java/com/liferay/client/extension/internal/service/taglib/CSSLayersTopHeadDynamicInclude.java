/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.client.extension.internal.service.taglib;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.content.security.policy.ContentSecurityPolicyNonceProviderUtil;
import com.liferay.portal.kernel.servlet.taglib.DynamicInclude;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import org.osgi.service.component.annotations.Component;

/**
 * Declares the CSS cascade layer order as the first style sheet of the page,
 * and loads Clay and the theme baseline into their layers. It is active when a
 * module fragment was rendered on the page, or when the page's theme CSS
 * client extension does not load Clay for everyone or provides a baseline.
 *
 * <p>
 * Unlayered style sheets, such as the theme's main CSS and global CSS client
 * extensions, always win over layered ones, so fragments build on the reset,
 * Clay, and the baseline while the theme can still override them.
 * </p>
 *
 * @author Gianmarco Brunialti
 */
@Component(
	property = "service.ranking:Integer=" + (Integer.MAX_VALUE - 1),
	service = DynamicInclude.class
)
public class CSSLayersTopHeadDynamicInclude implements DynamicInclude {

	public static final String BASELINE_CSS_URL =
		"LIFERAY_SHARED_BASELINE_CSS_URL";

	public static final String CLAY_CSS_DISABLED =
		"LIFERAY_SHARED_CLAY_CSS_DISABLED";

	public static final String CSS_LAYERS = "LIFERAY_SHARED_CSS_LAYERS";

	public static final String CSS_LAYERS_WRITTEN =
		"LIFERAY_SHARED_CSS_LAYERS_WRITTEN";

	@Override
	public void include(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, String key)
		throws IOException {

		if (!GetterUtil.getBoolean(
				httpServletRequest.getAttribute(CSS_LAYERS)) ||
			GetterUtil.getBoolean(
				httpServletRequest.getAttribute(CSS_LAYERS_WRITTEN))) {

			return;
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		if (themeDisplay == null) {
			return;
		}

		String nonce = ContentSecurityPolicyNonceProviderUtil.getNonceAttribute(
			httpServletRequest);

		PrintWriter printWriter = httpServletResponse.getWriter();

		printWriter.print("<style data-senna-track=\"temporary\"");
		printWriter.print(nonce);
		printWriter.print(">@layer dxp.reset, dxp.clay, dxp.baseline, ");
		printWriter.print("fragments;</style>");

		if (!GetterUtil.getBoolean(
				httpServletRequest.getAttribute(CLAY_CSS_DISABLED)) &&
			_isClayLink(httpServletRequest)) {

			// Spike experiment: load Clay unlayered to isolate the cost of the
			// layered import

			printWriter.print(
				"<link data-senna-track=\"temporary\" href=\"");
			printWriter.print(
				HtmlUtil.escapeAttribute(themeDisplay.getClayCSSURL()));
			printWriter.print("\" id=\"liferayAUICSS\"");
			printWriter.print(nonce);
			printWriter.print(" rel=\"stylesheet\" type=\"text/css\" />");
		}
		else if (!GetterUtil.getBoolean(
					httpServletRequest.getAttribute(CLAY_CSS_DISABLED))) {

			printWriter.print(
				"<style data-senna-track=\"temporary\" id=\"liferayAUICSS\"");
			printWriter.print(nonce);
			printWriter.print(">@import url(\"");
			printWriter.print(_encodeURL(themeDisplay.getClayCSSURL()));
			printWriter.print("\") layer(dxp.clay);</style>");
		}

		String baselineCSSURL = GetterUtil.getString(
			httpServletRequest.getAttribute(BASELINE_CSS_URL));

		if (Validator.isNotNull(baselineCSSURL)) {
			printWriter.print("<link data-senna-track=\"temporary\" href=\"");
			printWriter.print(HtmlUtil.escapeAttribute(baselineCSSURL));
			printWriter.print(StringPool.QUOTE);
			printWriter.print(nonce);
			printWriter.print(" rel=\"stylesheet\" type=\"text/css\" />");
		}

		httpServletRequest.setAttribute(CSS_LAYERS_WRITTEN, Boolean.TRUE);
	}

	@Override
	public void register(DynamicIncludeRegistry dynamicIncludeRegistry) {
		dynamicIncludeRegistry.register("/html/common/themes/top_head.jsp#pre");
	}

	private boolean _isClayLink(HttpServletRequest httpServletRequest) {
		HttpServletRequest originalHttpServletRequest =
			PortalUtil.getOriginalServletRequest(httpServletRequest);

		return ArrayUtil.contains(
			StringUtil.split(
				originalHttpServletRequest.getParameter("fragmentAssets")),
			"clay-link");
	}

	private String _encodeURL(String url) {

		// Percent-encode instead of CSS escaping so that preload scanners can
		// still read the URL of the import

		return StringUtil.replace(
			url,
			new String[] {
				StringPool.QUOTE, StringPool.BACK_SLASH,
				StringPool.LESS_THAN, StringPool.GREATER_THAN,
				StringPool.NEW_LINE, StringPool.RETURN
			},
			new String[] {"%22", "%5C", "%3C", "%3E", "%0A", "%0D"});
	}

}
