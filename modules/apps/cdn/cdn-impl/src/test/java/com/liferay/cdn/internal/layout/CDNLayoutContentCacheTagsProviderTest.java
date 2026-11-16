/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.layout;

import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructureRel;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureRelLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactory;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionList;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactory;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNLayoutContentCacheTagsProviderTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@BeforeClass
	public static void setUpClass() {
		JSONFactoryUtil jsonFactoryUtil = new JSONFactoryUtil();

		jsonFactoryUtil.setJSONFactory(new JSONFactoryImpl());
	}

	@Before
	public void setUp() {
		_cdnLayoutContentCacheTagsProvider =
			new CDNLayoutContentCacheTagsProvider(
				_assetEntryLocalService, null, _cdnCacheTagBuilder,
				_fragmentEntryLinkLocalService,
				_layoutPageTemplateStructureLocalService,
				_layoutPageTemplateStructureRelLocalService, _portal,
				_portletPreferencesLocalService, null);

		Mockito.when(
			_cdnCacheTagBuilder.getCollectionCacheTag(
				Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong())
		).thenAnswer(
			invocation -> StringBundler.concat(
				"l", invocation.<Long>getArgument(1), "-",
				invocation.<Long>getArgument(2))
		);

		Mockito.when(
			_cdnCacheTagBuilder.getModelCacheTag(
				Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong())
		).thenAnswer(
			invocation -> {
				String className = invocation.getArgument(1);

				if (className.equals("UntaggedModel")) {
					return null;
				}

				return className + "#" + invocation.getArgument(2);
			}
		);

		Mockito.when(
			_layout.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			_layout.getGroupId()
		).thenReturn(
			20L
		);

		Mockito.when(
			_layout.getPlid()
		).thenReturn(
			40L
		);

		Mockito.when(
			_portal.fetchClassName(100L)
		).thenReturn(
			"JournalArticle"
		);

		Mockito.when(
			_portal.getClassName(100L)
		).thenReturn(
			"JournalArticle"
		);

		Mockito.when(
			_portal.getClassNameId(Mockito.anyString())
		).thenAnswer(
			invocation -> {
				String className = invocation.getArgument(0);

				if (className.equals("JournalArticle")) {
					return 100L;
				}

				return 200L;
			}
		);
	}

	@Test
	public void testGetCacheTags() {
		Assert.assertEquals(
			Collections.emptySet(),
			_cdnLayoutContentCacheTagsProvider.getCacheTags(
				_httpServletRequest, _layout));

		// Request cache tags

		Mockito.when(
			_httpServletRequest.getAttribute("LIFERAY_SHARED_CDN_CACHE_TAGS")
		).thenReturn(
			SetUtil.fromArray("requestCacheTag")
		);

		// Linked asset entries

		_mockLinkedAssetEntries();

		// Fragment entry links

		FragmentEntryLink deletedFragmentEntryLink = _mockFragmentEntryLink(
			JSONUtil.put(
				"classNameId", 100L
			).put(
				"classPK", 1L
			),
			30L);

		Mockito.when(
			deletedFragmentEntryLink.isDeleted()
		).thenReturn(
			true
		);

		FragmentEntryLink fragmentEntryLink1 = _mockFragmentEntryLink(
			JSONUtil.put(
				"com.liferay.fragment.entry.processor.editable." +
					"EditableFragmentEntryProcessor",
				JSONUtil.put(
					"element-text",
					JSONUtil.put(
						"classNameId", 100L
					).put(
						"classPK", "5"
					).put(
						"fieldId", "title"
					))
			).put(
				"com.liferay.fragment.entry.processor.freemarker." +
					"FreeMarkerFragmentEntryProcessor",
				JSONUtil.put(
					"itemSelector",
					JSONUtil.put(
						"className", "BlogsEntry"
					).put(
						"classPK", 6L
					))
			),
			31L);
		FragmentEntryLink fragmentEntryLink2 = _mockFragmentEntryLink(
			JSONUtil.put(
				"element-text",
				JSONUtil.put(
					"className", "UntaggedModel"
				).put(
					"classPK", 7L
				)),
			0L);

		Mockito.when(
			_fragmentEntryLinkLocalService.getFragmentEntryLinksByPlid(20L, 40L)
		).thenReturn(
			Arrays.asList(
				deletedFragmentEntryLink, fragmentEntryLink1,
				fragmentEntryLink2)
		);

		// Layout structure

		_mockLayoutStructure(
			JSONUtil.put(
				"items",
				JSONUtil.put(
					"collection",
					JSONUtil.put(
						"children", JSONUtil.putAll()
					).put(
						"config",
						JSONUtil.put(
							"collection",
							JSONUtil.put(
								"itemType", "JournalArticle"
							).put(
								"type", "InfoListProviderItemSelectorReturnType"
							))
					).put(
						"itemId", "collection"
					).put(
						"type", "collection"
					)
				).put(
					"container",
					JSONUtil.put(
						"children", JSONUtil.putAll()
					).put(
						"config",
						JSONUtil.put(
							"styles",
							JSONUtil.put(
								"backgroundImage",
								JSONUtil.put(
									"className", "FileEntry"
								).put(
									"classPK", 8L
								)))
					).put(
						"itemId", "container"
					).put(
						"type", "container"
					)
				).put(
					"objectCollection",
					JSONUtil.put(
						"children", JSONUtil.putAll()
					).put(
						"config",
						JSONUtil.put(
							"collection",
							JSONUtil.put(
								"itemType",
								"com.liferay.object.model.ObjectDefinition#" +
									"C_TICKET"))
					).put(
						"itemId", "objectCollection"
					).put(
						"type", "collection"
					)
				)
			).put(
				"rootItems", JSONUtil.put("main", "container")
			));

		Assert.assertEquals(
			SetUtil.fromArray(
				"BlogsEntry#6", "FileEntry#8", "JournalArticle#5",
				"JournalArticle#9", "l0-200", "l20-100", "l20-200",
				"requestCacheTag", FragmentEntry.class.getName() + "#31"),
			_cdnLayoutContentCacheTagsProvider.getCacheTags(
				_httpServletRequest, _layout));

		// Master layout

		Mockito.when(
			_layout.getMasterLayoutPlid()
		).thenReturn(
			41L
		);

		FragmentEntryLink masterFragmentEntryLink = _mockFragmentEntryLink(
			JSONFactoryUtil.createJSONObject(), 32L);

		Mockito.when(
			_fragmentEntryLinkLocalService.getFragmentEntryLinksByPlid(20L, 41L)
		).thenReturn(
			Collections.singletonList(masterFragmentEntryLink)
		);

		Assert.assertTrue(
			_cdnLayoutContentCacheTagsProvider.getCacheTags(
				_httpServletRequest, _layout
			).contains(
				FragmentEntry.class.getName() + "#32"
			));
	}

	private FragmentEntryLink _mockFragmentEntryLink(
		JSONObject editableValuesJSONObject, long fragmentEntryId) {

		FragmentEntryLink fragmentEntryLink = Mockito.mock(
			FragmentEntryLink.class);

		Mockito.when(
			fragmentEntryLink.getEditableValuesJSONObject()
		).thenReturn(
			editableValuesJSONObject
		);

		if (fragmentEntryId > 0) {
			FragmentEntry fragmentEntry = Mockito.mock(FragmentEntry.class);

			Mockito.when(
				fragmentEntry.getFragmentEntryId()
			).thenReturn(
				fragmentEntryId
			);

			Mockito.when(
				fragmentEntryLink.fetchFragmentEntry()
			).thenReturn(
				fragmentEntry
			);
		}

		return fragmentEntryLink;
	}

	private void _mockLayoutStructure(JSONObject dataJSONObject) {
		LayoutPageTemplateStructure layoutPageTemplateStructure = Mockito.mock(
			LayoutPageTemplateStructure.class);

		Mockito.when(
			layoutPageTemplateStructure.getLayoutPageTemplateStructureId()
		).thenReturn(
			50L
		);

		Mockito.when(
			_layoutPageTemplateStructureLocalService.
				fetchLayoutPageTemplateStructure(20L, 40L)
		).thenReturn(
			layoutPageTemplateStructure
		);

		LayoutPageTemplateStructureRel layoutPageTemplateStructureRel =
			Mockito.mock(LayoutPageTemplateStructureRel.class);

		Mockito.when(
			layoutPageTemplateStructureRel.getDataJSONObject()
		).thenReturn(
			dataJSONObject
		);

		Mockito.when(
			_layoutPageTemplateStructureRelLocalService.
				getLayoutPageTemplateStructureRels(50L)
		).thenReturn(
			Collections.singletonList(layoutPageTemplateStructureRel)
		);
	}

	private void _mockLinkedAssetEntries() {
		Mockito.when(
			_httpServletRequest.getAttribute(WebKeys.LINKED_ASSET_ENTRY_IDS)
		).thenReturn(
			SetUtil.fromArray(60L)
		);

		Mockito.when(
			_assetEntryLocalService.dynamicQuery()
		).thenReturn(
			Mockito.mock(DynamicQuery.class)
		);

		Mockito.when(
			_assetEntryLocalService.dynamicQuery(
				Mockito.any(DynamicQuery.class))
		).thenReturn(
			Collections.singletonList(new Object[] {100L, 9L})
		);

		ProjectionFactory projectionFactory = Mockito.mock(
			ProjectionFactory.class);

		Mockito.when(
			projectionFactory.projectionList()
		).thenReturn(
			Mockito.mock(ProjectionList.class)
		);

		ProjectionFactoryUtil projectionFactoryUtil =
			new ProjectionFactoryUtil();

		projectionFactoryUtil.setProjectionFactory(projectionFactory);

		Property property = Mockito.mock(Property.class);

		Mockito.when(
			property.in(Mockito.anyCollection())
		).thenReturn(
			Mockito.mock(Criterion.class)
		);

		PropertyFactory propertyFactory = Mockito.mock(PropertyFactory.class);

		Mockito.when(
			propertyFactory.forName(Mockito.anyString())
		).thenReturn(
			property
		);

		PropertyFactoryUtil propertyFactoryUtil = new PropertyFactoryUtil();

		propertyFactoryUtil.setPropertyFactory(propertyFactory);
	}

	private final AssetEntryLocalService _assetEntryLocalService = Mockito.mock(
		AssetEntryLocalService.class);
	private final CDNCacheTagBuilder _cdnCacheTagBuilder = Mockito.mock(
		CDNCacheTagBuilder.class);
	private CDNLayoutContentCacheTagsProvider
		_cdnLayoutContentCacheTagsProvider;
	private final FragmentEntryLinkLocalService _fragmentEntryLinkLocalService =
		Mockito.mock(FragmentEntryLinkLocalService.class);
	private final HttpServletRequest _httpServletRequest = Mockito.mock(
		HttpServletRequest.class);
	private final Layout _layout = Mockito.mock(Layout.class);
	private final LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService = Mockito.mock(
			LayoutPageTemplateStructureLocalService.class);
	private final LayoutPageTemplateStructureRelLocalService
		_layoutPageTemplateStructureRelLocalService = Mockito.mock(
			LayoutPageTemplateStructureRelLocalService.class);
	private final Portal _portal = Mockito.mock(Portal.class);
	private final PortletPreferencesLocalService
		_portletPreferencesLocalService = Mockito.mock(
			PortletPreferencesLocalService.class);

}