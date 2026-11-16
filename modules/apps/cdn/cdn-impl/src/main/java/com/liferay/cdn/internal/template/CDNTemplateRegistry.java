/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.template;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.constants.CDNConstants;
import com.liferay.cdn.template.CDNTemplate;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMapFactory;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.feature.flag.FeatureFlagManagerUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.util.Validator;

import org.osgi.framework.BundleContext;

/**
 * Resolves the CDN template that applies to a company, with the company's
 * configuration applied on top of it.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNTemplateRegistry {

	public CDNTemplateRegistry(
		BundleContext bundleContext,
		ConfigurationProvider configurationProvider) {

		_configurationProvider = configurationProvider;

		_serviceTrackerMap = ServiceTrackerMapFactory.openSingleValueMap(
			bundleContext, CDNTemplate.class, null,
			(serviceReference, emitter) -> {
				CDNTemplate cdnTemplate = bundleContext.getService(
					serviceReference);

				try {
					emitter.emit(cdnTemplate.getKey());
				}
				finally {
					bundleContext.ungetService(serviceReference);
				}
			});
	}

	public void close() {
		_serviceTrackerMap.close();
	}

	public CDNConfiguration getCDNConfiguration(long companyId)
		throws ConfigurationException {

		return _configurationProvider.getCompanyConfiguration(
			CDNConfiguration.class, companyId);
	}

	/**
	 * Returns the template configured for the company, or <code>null</code>
	 * when the feature flag is disabled, the integration is disabled, or the
	 * configured template is not available.
	 */
	public ConfiguredCDNTemplate getConfiguredCDNTemplate(
		long companyId, CDNConfiguration cdnConfiguration) {

		if (!FeatureFlagManagerUtil.isEnabled(
				companyId, CDNConstants.FEATURE_FLAG_KEY) ||
			!cdnConfiguration.enabled()) {

			return null;
		}

		String templateKey = cdnConfiguration.templateKey();

		if (Validator.isNull(templateKey)) {
			return null;
		}

		CDNTemplate cdnTemplate = _serviceTrackerMap.getService(templateKey);

		if (cdnTemplate == null) {
			return null;
		}

		return new ConfiguredCDNTemplate(
			cdnConfiguration, cdnTemplate, companyId);
	}

	private final ConfigurationProvider _configurationProvider;
	private final ServiceTrackerMap<String, CDNTemplate> _serviceTrackerMap;

}