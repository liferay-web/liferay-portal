/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.configuration.admin.definition;

import com.liferay.cdn.template.CDNTemplate;
import com.liferay.configuration.admin.definition.ConfigurationFieldOptionsProvider;
import com.liferay.osgi.service.tracker.collections.list.ServiceTrackerList;
import com.liferay.osgi.service.tracker.collections.list.ServiceTrackerListFactory;
import com.liferay.petra.function.transform.TransformUtil;

import java.util.List;
import java.util.Locale;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * Lists the registered CDN templates in the template selector of the CDN
 * configuration.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(
	property = {
		"configuration.field.name=templateKey",
		"configuration.pid=com.liferay.cdn.configuration.CDNConfiguration"
	},
	service = ConfigurationFieldOptionsProvider.class
)
public class CDNTemplateKeyConfigurationFieldOptionsProvider
	implements ConfigurationFieldOptionsProvider {

	@Override
	public List<Option> getOptions() {
		return TransformUtil.transform(
			_serviceTrackerList.toList(),
			cdnTemplate -> new Option() {

				@Override
				public String getLabel(Locale locale) {
					return cdnTemplate.getName(locale);
				}

				@Override
				public String getValue() {
					return cdnTemplate.getKey();
				}

			});
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_serviceTrackerList = ServiceTrackerListFactory.open(
			bundleContext, CDNTemplate.class);
	}

	@Deactivate
	protected void deactivate() {
		_serviceTrackerList.close();
	}

	private ServiceTrackerList<CDNTemplate> _serviceTrackerList;

}