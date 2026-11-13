/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.layout;

import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.cache.tag.CDNCacheTagHeaderUtil;
import com.liferay.cdn.internal.template.CDNTemplateRegistry;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.cdn.CDNCacheTagsUtil;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionList;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.layout.LayoutResponseHeaderContributor;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.servlet.HttpMethods;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashSet;
import java.util.List;
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
				_getModelCacheTags(companyId, httpServletRequest),
				_cdnCacheTagBuilder.getOverflowCacheTag(
					companyId, layout.getGroupId()),
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

	private Set<String> _getModelCacheTags(
		long companyId, HttpServletRequest httpServletRequest) {

		Set<String> modelCacheTags = new LinkedHashSet<>(
			CDNCacheTagsUtil.getCacheTags(httpServletRequest));

		Set<Long> linkedAssetEntryIds =
			(Set<Long>)httpServletRequest.getAttribute(
				WebKeys.LINKED_ASSET_ENTRY_IDS);

		if (SetUtil.isEmpty(linkedAssetEntryIds)) {
			return modelCacheTags;
		}

		DynamicQuery dynamicQuery = _assetEntryLocalService.dynamicQuery();

		Property entryIdProperty = PropertyFactoryUtil.forName("entryId");

		dynamicQuery.add(entryIdProperty.in(linkedAssetEntryIds));

		ProjectionList projectionList = ProjectionFactoryUtil.projectionList();

		projectionList.add(PropertyFactoryUtil.forName("classNameId"));
		projectionList.add(PropertyFactoryUtil.forName("classPK"));

		dynamicQuery.setProjection(projectionList);

		List<Object[]> results = _assetEntryLocalService.dynamicQuery(
			dynamicQuery);

		for (Object[] result : results) {
			String modelCacheTag = _cdnCacheTagBuilder.getModelCacheTag(
				companyId, _portal.getClassName((Long)result[0]),
				(Long)result[1]);

			if (modelCacheTag == null) {
				continue;
			}

			modelCacheTags.add(modelCacheTag);
		}

		return modelCacheTags;
	}

	@Reference
	private AssetEntryLocalService _assetEntryLocalService;

	@Reference
	private CDNCacheTagBuilder _cdnCacheTagBuilder;

	private CDNTemplateRegistry _cdnTemplateRegistry;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private Portal _portal;

}