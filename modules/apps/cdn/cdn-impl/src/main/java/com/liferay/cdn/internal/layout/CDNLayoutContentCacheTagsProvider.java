/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.layout;

import com.liferay.asset.kernel.AssetRendererFactoryRegistryUtil;
import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.publisher.constants.AssetPublisherPortletKeys;
import com.liferay.asset.publisher.util.AssetPublisherHelper;
import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructureRel;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureRelLocalService;
import com.liferay.layout.util.constants.LayoutDataItemTypeConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.portal.kernel.cdn.CDNCacheTagsUtil;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionList;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.PortletPreferences;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.service.PortletPreferenceValueLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Provides the cache tags of the content a page shows. The page's own data
 * says which fragments, mapped items, collections, and asset publishers it
 * renders, so the tags do not depend on whether a fragment came from the
 * fragment cache. The asset entries linked to the request add the content
 * that widgets and display pages render.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNLayoutContentCacheTagsProvider {

	public CDNLayoutContentCacheTagsProvider(
		AssetEntryLocalService assetEntryLocalService,
		AssetPublisherHelper assetPublisherHelper,
		CDNCacheTagBuilder cdnCacheTagBuilder,
		FragmentEntryLinkLocalService fragmentEntryLinkLocalService,
		LayoutPageTemplateStructureLocalService
			layoutPageTemplateStructureLocalService,
		LayoutPageTemplateStructureRelLocalService
			layoutPageTemplateStructureRelLocalService,
		Portal portal,
		PortletPreferencesLocalService portletPreferencesLocalService,
		PortletPreferenceValueLocalService portletPreferenceValueLocalService) {

		_assetEntryLocalService = assetEntryLocalService;
		_assetPublisherHelper = assetPublisherHelper;
		_cdnCacheTagBuilder = cdnCacheTagBuilder;
		_fragmentEntryLinkLocalService = fragmentEntryLinkLocalService;
		_layoutPageTemplateStructureLocalService =
			layoutPageTemplateStructureLocalService;
		_layoutPageTemplateStructureRelLocalService =
			layoutPageTemplateStructureRelLocalService;
		_portal = portal;
		_portletPreferencesLocalService = portletPreferencesLocalService;
		_portletPreferenceValueLocalService =
			portletPreferenceValueLocalService;
	}

	public Set<String> getCacheTags(
		HttpServletRequest httpServletRequest, Layout layout) {

		Set<String> cacheTags = new LinkedHashSet<>(
			CDNCacheTagsUtil.getCacheTags(httpServletRequest));

		_addLinkedAssetEntryCacheTags(
			cacheTags, layout.getCompanyId(),
			(Set<Long>)httpServletRequest.getAttribute(
				WebKeys.LINKED_ASSET_ENTRY_IDS));

		_addLayoutCacheTags(cacheTags, layout, layout.getPlid());

		if (layout.getMasterLayoutPlid() > 0) {
			_addLayoutCacheTags(
				cacheTags, layout, layout.getMasterLayoutPlid());
		}

		cacheTags.remove(null);

		return cacheTags;
	}

	private void _addAssetPublisherCacheTags(
		Set<String> cacheTags, Layout layout, long plid) {

		List<PortletPreferences> portletPreferencesList =
			_portletPreferencesLocalService.getPortletPreferences(
				PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, plid);

		for (PortletPreferences portletPreferences : portletPreferencesList) {
			if (!Objects.equals(
					PortletIdCodec.decodePortletName(
						portletPreferences.getPortletId()),
					AssetPublisherPortletKeys.ASSET_PUBLISHER)) {

				continue;
			}

			jakarta.portlet.PortletPreferences jakartaPortletPreferences =
				_portletPreferenceValueLocalService.getPreferences(
					portletPreferences);

			if (Objects.equals(
					jakartaPortletPreferences.getValue("selectionStyle", null),
					_SELECTION_STYLE_MANUAL)) {

				continue;
			}

			long[] groupIds = _assetPublisherHelper.getGroupIds(
				jakartaPortletPreferences, layout.getGroupId(), layout);

			for (long classNameId :
					_assetPublisherHelper.getClassNameIds(
						jakartaPortletPreferences,
						AssetRendererFactoryRegistryUtil.getClassNameIds(
							layout.getCompanyId(), true))) {

				_addCollectionCacheTags(
					cacheTags, layout.getCompanyId(), groupIds,
					_portal.getClassName(classNameId));
			}
		}
	}

	private void _addCollectionCacheTags(
		Set<String> cacheTags, Layout layout, JSONObject dataJSONObject) {

		JSONObject itemsJSONObject = dataJSONObject.getJSONObject("items");

		if (itemsJSONObject == null) {
			return;
		}

		for (String key : itemsJSONObject.keySet()) {
			JSONObject itemJSONObject = itemsJSONObject.getJSONObject(key);

			if ((itemJSONObject == null) ||
				!Objects.equals(
					itemJSONObject.getString("type"),
					LayoutDataItemTypeConstants.TYPE_COLLECTION)) {

				continue;
			}

			JSONObject configJSONObject = itemJSONObject.getJSONObject(
				"config");

			if (configJSONObject == null) {
				continue;
			}

			JSONObject collectionJSONObject = configJSONObject.getJSONObject(
				"collection");

			if (collectionJSONObject == null) {
				continue;
			}

			_addCollectionCacheTags(
				cacheTags, layout.getCompanyId(),
				new long[] {layout.getGroupId()},
				collectionJSONObject.getString("itemType"));
		}
	}

	private void _addCollectionCacheTags(
		Set<String> cacheTags, long companyId, long[] groupIds,
		String className) {

		if (Validator.isNull(className)) {
			return;
		}

		// A collection of any asset type shows asset entries

		if (className.equals(AssetEntry.class.getName())) {
			for (long classNameId :
					AssetRendererFactoryRegistryUtil.getClassNameIds(
						companyId, true)) {

				_addCollectionCacheTags(
					cacheTags, companyId, groupIds,
					_portal.getClassName(classNameId));
			}

			return;
		}

		long classNameId = _portal.getClassNameId(className);

		for (long groupId : groupIds) {
			cacheTags.add(
				_cdnCacheTagBuilder.getCollectionCacheTag(
					companyId, groupId, classNameId));
		}

		// Entries of company scoped object definitions belong to no group

		if (className.startsWith(ObjectDefinition.class.getName() + "#")) {
			cacheTags.add(
				_cdnCacheTagBuilder.getCollectionCacheTag(
					companyId, 0, classNameId));
		}
	}

	private void _addFragmentEntryLinkCacheTags(
		Set<String> cacheTags, Layout layout, long plid) {

		for (FragmentEntryLink fragmentEntryLink :
				_fragmentEntryLinkLocalService.getFragmentEntryLinksByPlid(
					layout.getGroupId(), plid)) {

			if (fragmentEntryLink.isDeleted()) {
				continue;
			}

			FragmentEntry fragmentEntry =
				fragmentEntryLink.fetchFragmentEntry();

			if (fragmentEntry != null) {
				cacheTags.add(
					_cdnCacheTagBuilder.getModelCacheTag(
						layout.getCompanyId(), FragmentEntry.class.getName(),
						fragmentEntry.getFragmentEntryId()));
			}

			_addMappedModelCacheTags(
				cacheTags, layout.getCompanyId(),
				fragmentEntryLink.getEditableValuesJSONObject());
		}
	}

	private void _addLayoutCacheTags(
		Set<String> cacheTags, Layout layout, long plid) {

		_addAssetPublisherCacheTags(cacheTags, layout, plid);
		_addFragmentEntryLinkCacheTags(cacheTags, layout, plid);
		_addLayoutStructureCacheTags(cacheTags, layout, plid);
	}

	private void _addLayoutStructureCacheTags(
		Set<String> cacheTags, Layout layout, long plid) {

		LayoutPageTemplateStructure layoutPageTemplateStructure =
			_layoutPageTemplateStructureLocalService.
				fetchLayoutPageTemplateStructure(layout.getGroupId(), plid);

		if (layoutPageTemplateStructure == null) {
			return;
		}

		for (LayoutPageTemplateStructureRel layoutPageTemplateStructureRel :
				_layoutPageTemplateStructureRelLocalService.
					getLayoutPageTemplateStructureRels(
						layoutPageTemplateStructure.
							getLayoutPageTemplateStructureId())) {

			JSONObject dataJSONObject =
				layoutPageTemplateStructureRel.getDataJSONObject();

			if (dataJSONObject == null) {
				continue;
			}

			_addCollectionCacheTags(cacheTags, layout, dataJSONObject);

			_addMappedModelCacheTags(
				cacheTags, layout.getCompanyId(), dataJSONObject);
		}
	}

	private void _addLinkedAssetEntryCacheTags(
		Set<String> cacheTags, long companyId, Set<Long> linkedAssetEntryIds) {

		if (SetUtil.isEmpty(linkedAssetEntryIds)) {
			return;
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
			cacheTags.add(
				_cdnCacheTagBuilder.getModelCacheTag(
					companyId, _portal.getClassName((Long)result[0]),
					(Long)result[1]));
		}
	}

	/**
	 * Adds the cache tags of the items mapped anywhere in the JSON object.
	 * Mapped editable values, mapped backgrounds and links, and item selector
	 * configuration values all store the item's class and class primary key.
	 */
	private void _addMappedModelCacheTags(
		Set<String> cacheTags, long companyId, JSONObject jsonObject) {

		long classPK = jsonObject.getLong("classPK");

		if (classPK > 0) {
			String className = jsonObject.getString("className", null);

			long classNameId = jsonObject.getLong("classNameId");

			if (classNameId > 0) {
				className = _portal.fetchClassName(classNameId);
			}

			if (className != null) {
				cacheTags.add(
					_cdnCacheTagBuilder.getModelCacheTag(
						companyId, className, classPK));
			}
		}

		for (String key : jsonObject.keySet()) {
			Object value = jsonObject.get(key);

			if (value instanceof JSONArray) {
				JSONArray jsonArray = (JSONArray)value;

				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject elementJSONObject = jsonArray.getJSONObject(i);

					if (elementJSONObject == null) {
						continue;
					}

					_addMappedModelCacheTags(
						cacheTags, companyId, elementJSONObject);
				}
			}
			else if (value instanceof JSONObject) {
				_addMappedModelCacheTags(
					cacheTags, companyId, (JSONObject)value);
			}
		}
	}

	private static final String _SELECTION_STYLE_MANUAL = "manual";

	private final AssetEntryLocalService _assetEntryLocalService;
	private final AssetPublisherHelper _assetPublisherHelper;
	private final CDNCacheTagBuilder _cdnCacheTagBuilder;
	private final FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;
	private final LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService;
	private final LayoutPageTemplateStructureRelLocalService
		_layoutPageTemplateStructureRelLocalService;
	private final Portal _portal;
	private final PortletPreferenceValueLocalService
		_portletPreferenceValueLocalService;
	private final PortletPreferencesLocalService
		_portletPreferencesLocalService;

}