/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag.resolver;

import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.document.library.kernel.model.DLFileEntry;

import org.osgi.service.component.annotations.Component;

/**
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNCacheTagModelResolver.class)
public class DLFileEntryCDNCacheTagModelResolver
	implements CDNCacheTagModelResolver {

	@Override
	public String getCacheTag(long classPK) {
		return "d" + classPK;
	}

	@Override
	public String getClassName() {
		return DLFileEntry.class.getName();
	}

}