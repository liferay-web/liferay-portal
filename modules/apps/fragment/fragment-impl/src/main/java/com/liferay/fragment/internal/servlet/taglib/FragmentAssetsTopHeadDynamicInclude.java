/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.internal.servlet.taglib;

import com.liferay.fragment.internal.renderer.FragmentAssetsRegistrar;
import com.liferay.fragment.internal.renderer.FragmentAssetsStyleSheets;
import com.liferay.portal.kernel.content.security.policy.ContentSecurityPolicyNonceProviderUtil;
import com.liferay.portal.kernel.servlet.taglib.DynamicInclude;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StringUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Writes the style sheets of the module fragments of the page composition at
 * the top of the head, after the CSS cascade layer order, either as one
 * combined, immutable style sheet or inline.
 *
 * @author Gianmarco Brunialti
 */
@Component(
	property = "service.ranking:Integer=" + (Integer.MAX_VALUE - 2),
	service = DynamicInclude.class
)
public class FragmentAssetsTopHeadDynamicInclude implements DynamicInclude {

	@Override
	public void include(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, String key)
		throws IOException {

		Map<String, String> styleSheets =
			FragmentAssetsRegistrar.getStyleSheets(httpServletRequest);

		if (MapUtil.isEmpty(styleSheets)) {
			return;
		}

		String content = _fragmentAssetsStyleSheets.combine(styleSheets);
		String nonce = ContentSecurityPolicyNonceProviderUtil.getNonceAttribute(
			httpServletRequest);

		PrintWriter printWriter = httpServletResponse.getWriter();

		if (FragmentAssetsRegistrar.isInlineStyleSheets(httpServletRequest)) {
			printWriter.print("<style data-senna-track=\"temporary\"");
			printWriter.print(nonce);
			printWriter.print(">");
			printWriter.print(StringUtil.replace(content, "</", "<\\/"));
			printWriter.print("</style>");

			return;
		}

		printWriter.print("<link data-senna-track=\"temporary\" href=\"");
		printWriter.print(
			HtmlUtil.escapeAttribute(
				_portal.getPathProxy() + "/o/fragment-assets/" +
					_fragmentAssetsStyleSheets.put(content) + ".css"));
		printWriter.print("\"");
		printWriter.print(nonce);
		printWriter.print(" rel=\"stylesheet\" />");
	}

	@Override
	public void register(DynamicIncludeRegistry dynamicIncludeRegistry) {
		dynamicIncludeRegistry.register("/html/common/themes/top_head.jsp#pre");
	}

	@Reference
	private FragmentAssetsStyleSheets _fragmentAssetsStyleSheets;

	@Reference
	private Portal _portal;

}
