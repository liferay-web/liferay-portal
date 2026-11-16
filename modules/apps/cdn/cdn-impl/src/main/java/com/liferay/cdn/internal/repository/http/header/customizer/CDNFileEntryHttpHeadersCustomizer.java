/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.repository.http.header.customizer;

import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.cache.tag.CDNCacheTagHeaderUtil;
import com.liferay.cdn.internal.template.CDNTemplateRegistry;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.repository.http.header.customizer.FileEntryHttpHeadersCustomizer;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.servlet.HttpMethods;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;
import java.util.Objects;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Adds the CDN cache tag header to the documents a CDN may cache: documents
 * and their adaptive media requested by guests with GET, outside of a
 * publication.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = FileEntryHttpHeadersCustomizer.class)
public class CDNFileEntryHttpHeadersCustomizer
	implements FileEntryHttpHeadersCustomizer {

	@Override
	public void customizeHttpHeaders(
		FileEntry fileEntry, HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		if (!Objects.equals(httpServletRequest.getMethod(), HttpMethods.GET) ||
			!CTCollectionThreadLocal.isProductionMode()) {

			return;
		}

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		if ((permissionChecker != null) && permissionChecker.isSignedIn()) {
			return;
		}

		long companyId = fileEntry.getCompanyId();

		CDNConfiguration cdnConfiguration = null;

		try {
			cdnConfiguration = _cdnTemplateRegistry.getCDNConfiguration(
				companyId);
		}
		catch (ConfigurationException configurationException) {
			_log.error(
				"Unable to get the CDN configuration for company " + companyId,
				configurationException);

			return;
		}

		if (!cdnConfiguration.emitCacheTags()) {
			return;
		}

		ConfiguredCDNTemplate configuredCDNTemplate =
			_cdnTemplateRegistry.getConfiguredCDNTemplate(
				companyId, cdnConfiguration);

		if (configuredCDNTemplate == null) {
			return;
		}

		httpServletResponse.setHeader(
			configuredCDNTemplate.getCacheTagHeaderName(),
			CDNCacheTagHeaderUtil.getHeaderValue(
				Collections.singleton(
					_cdnCacheTagBuilder.getCompanyCacheTag(companyId)),
				configuredCDNTemplate.getCacheTagHeaderMaxBytes(),
				Collections.singleton(
					_cdnCacheTagBuilder.getModelCacheTag(
						companyId, FileEntry.class.getName(),
						fileEntry.getFileEntryId())),
				_cdnCacheTagBuilder.getOverflowCacheTag(
					companyId, fileEntry.getGroupId()),
				configuredCDNTemplate.getCacheTagHeaderSeparator()));
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_cdnTemplateRegistry = new CDNTemplateRegistry(
			bundleContext, _configurationProvider);
	}

	@Deactivate
	protected void deactivate() {
		_cdnTemplateRegistry.close();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CDNFileEntryHttpHeadersCustomizer.class);

	@Reference
	private CDNCacheTagBuilder _cdnCacheTagBuilder;

	private CDNTemplateRegistry _cdnTemplateRegistry;

	@Reference
	private ConfigurationProvider _configurationProvider;

}