/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.configuration.admin.category;

import com.liferay.configuration.admin.category.ConfigurationCategory;

import org.osgi.service.component.annotations.Component;

/**
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ConfigurationCategory.class)
public class CDNConfigurationCategory implements ConfigurationCategory {

	@Override
	public String getCategoryIcon() {
		return "cloud";
	}

	@Override
	public String getCategoryKey() {
		return "cdn";
	}

	@Override
	public String getCategorySection() {
		return "platform";
	}

}