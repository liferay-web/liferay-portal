/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.layout;

import com.liferay.portal.kernel.model.Layout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds response headers to a page response. It is called after the page
 * content is rendered and before the theme writes the response, which is the
 * last point where headers that depend on the rendered content can still be
 * set.
 *
 * @author Gianmarco Brunialti Masera
 */
public interface LayoutResponseHeaderContributor {

	public void contribute(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, Layout layout)
		throws Exception;

}