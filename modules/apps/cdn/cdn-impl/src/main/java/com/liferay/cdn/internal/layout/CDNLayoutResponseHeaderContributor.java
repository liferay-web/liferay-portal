/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.layout;

import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.publisher.util.AssetPublisherHelper;
import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.cache.tag.CDNCacheTagHeaderUtil;
import com.liferay.cdn.internal.template.CDNTemplateRegistry;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureRelLocalService;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.layout.LayoutResponseHeaderContributor;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.PortletPreferenceValueLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.servlet.HttpMethods;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Adds the CDN cache tag header to the pages a CDN may cache: public pages
 * requested by guests with GET, outside of a publication. A signed in response
 * never carries cache tags, so even a misconfigured CDN has nothing to index.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = LayoutResponseHeaderContributor.class)
public class CDNLayoutResponseHeaderContributor
	implements LayoutResponseHeaderContributor {

	@Override
	public void contribute(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, Layout layout)
		throws Exception {

		if (!Objects.equals(httpServletRequest.getMethod(), HttpMethods.GET) ||
			layout.isDraftLayout() || layout.isPrivateLayout() ||
			!CTCollectionThreadLocal.isProductionMode()) {

			return;
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		if ((themeDisplay == null) || themeDisplay.isSignedIn()) {
			return;
		}

		long companyId = layout.getCompanyId();

		CDNConfiguration cdnConfiguration =
			_cdnTemplateRegistry.getCDNConfiguration(companyId);

		if (!cdnConfiguration.emitCacheTags()) {
			return;
		}

		ConfiguredCDNTemplate configuredCDNTemplate =
			_cdnTemplateRegistry.getConfiguredCDNTemplate(
				companyId, cdnConfiguration);

		if (configuredCDNTemplate == null) {
			return;
		}

		Set<String> cacheTags = new LinkedHashSet<>();

		cacheTags.add(_cdnCacheTagBuilder.getCompanyCacheTag(companyId));
		cacheTags.add(
			_cdnCacheTagBuilder.getGroupCacheTag(
				companyId, layout.getGroupId()));
		cacheTags.addAll(_cdnCacheTagBuilder.getLayoutCacheTags(layout));

		httpServletResponse.setHeader(
			configuredCDNTemplate.getCacheTagHeaderName(),
			CDNCacheTagHeaderUtil.getHeaderValue(
				cacheTags, configuredCDNTemplate.getCacheTagHeaderMaxBytes(),
				_cdnLayoutContentCacheTagsProvider.getCacheTags(
					httpServletRequest, layout),
				_cdnCacheTagBuilder.getOverflowCacheTag(
					companyId, layout.getGroupId()),
				configuredCDNTemplate.getCacheTagHeaderSeparator()));
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_cdnLayoutContentCacheTagsProvider =
			new CDNLayoutContentCacheTagsProvider(
				_assetEntryLocalService, _assetPublisherHelper,
				_cdnCacheTagBuilder, _fragmentEntryLinkLocalService,
				_layoutPageTemplateStructureLocalService,
				_layoutPageTemplateStructureRelLocalService, _portal,
				_portletPreferencesLocalService,
				_portletPreferenceValueLocalService);
		_cdnTemplateRegistry = new CDNTemplateRegistry(
			bundleContext, _configurationProvider);
	}

	@Deactivate
	protected void deactivate() {
		_cdnTemplateRegistry.close();
	}

	@Reference
	private AssetEntryLocalService _assetEntryLocalService;

	@Reference
	private AssetPublisherHelper _assetPublisherHelper;

	@Reference
	private CDNCacheTagBuilder _cdnCacheTagBuilder;

	private CDNLayoutContentCacheTagsProvider
		_cdnLayoutContentCacheTagsProvider;
	private CDNTemplateRegistry _cdnTemplateRegistry;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@Reference
	private LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService;

	@Reference
	private LayoutPageTemplateStructureRelLocalService
		_layoutPageTemplateStructureRelLocalService;

	@Reference
	private Portal _portal;

	@Reference
	private PortletPreferenceValueLocalService
		_portletPreferenceValueLocalService;

	@Reference
	private PortletPreferencesLocalService _portletPreferencesLocalService;

}