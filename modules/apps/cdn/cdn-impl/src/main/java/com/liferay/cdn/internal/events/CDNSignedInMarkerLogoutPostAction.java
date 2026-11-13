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
 * Clears the signed in marker when a user signs out.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(property = "key=logout.events.post", service = LifecycleAction.class)
public class CDNSignedInMarkerLogoutPostAction extends Action {

	@Override
	public void run(
		HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		if (!CDNSignedInMarkerUtil.isEnabled(
				_configurationProvider,
				_portal.getCompanyId(httpServletRequest))) {

			return;
		}

		CDNSignedInMarkerUtil.deleteCookie(
			httpServletResponse, _portal.isSecure(httpServletRequest));
	}

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private Portal _portal;

}