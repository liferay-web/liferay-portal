/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.collection.contributor.legacy.samples.internal;

import com.liferay.fragment.contributor.BaseFragmentCollectionContributor;
import com.liferay.fragment.contributor.FragmentCollectionContributor;

import jakarta.servlet.ServletContext;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Gianmarco Brunialti
 */
@Component(
	property = "fragment.collection.key=LEGACY_FRAGMENT_SAMPLES",
	service = FragmentCollectionContributor.class
)
public class LegacySamplesFragmentCollectionContributor
	extends BaseFragmentCollectionContributor {

	@Override
	public String getFragmentCollectionKey() {
		return "LEGACY_FRAGMENT_SAMPLES";
	}

	@Override
	public ServletContext getServletContext() {
		return _servletContext;
	}

	@Reference(
		target = "(osgi.web.symbolicname=com.liferay.fragment.collection.contributor.legacy.samples)"
	)
	private ServletContext _servletContext;

}