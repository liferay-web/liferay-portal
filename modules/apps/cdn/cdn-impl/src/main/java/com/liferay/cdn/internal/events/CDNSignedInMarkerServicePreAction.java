/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.events;

import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.events.Action;
import com.liferay.portal.kernel.events.LifecycleAction;
import com.liferay.portal.kernel.util.Portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Keeps the signed in marker in line with the user on every portal request. A
 * signed in user without the marker gets it, and a guest with the marker loses
 * it. This covers the sign outs that fire no logout event, such as an expired
 * session or a single sign on logout, and a marker the browser dropped while
 * the session lived on. During impersonation the real user is signed in, so the
 * marker stays.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(
	property = "key=servlet.service.events.pre", service = LifecycleAction.class
)
public class CDNSignedInMarkerServicePreAction extends Action {

	@Override
	public void run(
		HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		if (!CDNSignedInMarkerUtil.isEnabled(
				_configurationProvider,
				_portal.getCompanyId(httpServletRequest))) {

			return;
		}

		if (_portal.getUserId(httpServletRequest) > 0) {
			CDNSignedInMarkerUtil.addCookie(
				httpServletRequest, httpServletResponse,
				_portal.isSecure(httpServletRequest));
		}
		else if (CDNSignedInMarkerUtil.hasCookie(httpServletRequest)) {
			CDNSignedInMarkerUtil.deleteCookie(
				httpServletResponse, _portal.isSecure(httpServletRequest));
		}
	}

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private Portal _portal;

}