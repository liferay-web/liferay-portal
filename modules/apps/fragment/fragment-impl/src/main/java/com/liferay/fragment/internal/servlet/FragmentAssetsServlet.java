/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.internal.servlet;

import com.liferay.fragment.internal.renderer.FragmentAssetsStyleSheets;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.Validator;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Serves the combined style sheets of page compositions at
 * <code>/o/fragment-assets/{hash}.css</code>.
 *
 * @author Gianmarco Brunialti
 */
@Component(
	property = {
		"osgi.http.whiteboard.servlet.name=com.liferay.fragment.internal.servlet.FragmentAssetsServlet",
		"osgi.http.whiteboard.servlet.pattern=/fragment-assets/*"
	},
	service = Servlet.class
)
public class FragmentAssetsServlet extends HttpServlet {

	@Override
	protected void doGet(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException {

		String pathInfo = httpServletRequest.getPathInfo();

		String content = null;

		if ((pathInfo != null) && pathInfo.endsWith(".css")) {
			content = _fragmentAssetsStyleSheets.get(
				pathInfo.substring(1, pathInfo.length() - 4));
		}

		if (Validator.isNull(content)) {
			httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);

			return;
		}

		httpServletResponse.setCharacterEncoding(StringPool.UTF8);
		httpServletResponse.setContentType(ContentTypes.TEXT_CSS_UTF8);
		httpServletResponse.setHeader(
			HttpHeaders.CACHE_CONTROL, "immutable, max-age=31536000, public");

		PrintWriter printWriter = httpServletResponse.getWriter();

		printWriter.write(content);
	}

	@Reference
	private FragmentAssetsStyleSheets _fragmentAssetsStyleSheets;

}
