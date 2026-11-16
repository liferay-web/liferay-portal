/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.cache.tag.resolver.BlogsEntryCDNCacheTagModelResolver;
import com.liferay.cdn.internal.cache.tag.resolver.DLFileEntryCDNCacheTagModelResolver;
import com.liferay.cdn.internal.cache.tag.resolver.FileEntryCDNCacheTagModelResolver;
import com.liferay.cdn.internal.cache.tag.resolver.FragmentEntryCDNCacheTagModelResolver;
import com.liferay.cdn.internal.cache.tag.resolver.ObjectEntryCDNCacheTagModelResolver;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileVersion;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.journal.model.JournalArticle;
import com.liferay.layout.page.template.constants.LayoutPageTemplateEntryTypeConstants;
import com.liferay.layout.page.template.model.LayoutPageTemplateEntry;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.GroupedModel;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.model.ShardedModel;
import com.liferay.portal.kernel.model.WorkflowedModel;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.LiferayUnitTestRule;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.style.book.model.StyleBookEntry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNCacheTagBuilderImplTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		Mockito.when(
			_cdnConfiguration.cacheTagNamespace()
		).thenReturn(
			"ns-"
		);

		Mockito.when(
			_configurationProvider.getCompanyConfiguration(
				CDNConfiguration.class, 1L)
		).thenReturn(
			_cdnConfiguration
		);

		CDNCacheTagModelResolver cdnCacheTagModelResolver = Mockito.mock(
			CDNCacheTagModelResolver.class);

		Mockito.when(
			cdnCacheTagModelResolver.getCacheTag(Mockito.anyLong())
		).thenAnswer(
			invocation -> "k" + invocation.getArgument(0)
		);

		Mockito.when(
			_serviceTrackerMap.getService("com.liferay.knowledge.base.Article")
		).thenReturn(
			cdnCacheTagModelResolver
		);

		_registerCDNCacheTagModelResolver(
			new BlogsEntryCDNCacheTagModelResolver());
		_registerCDNCacheTagModelResolver(
			new DLFileEntryCDNCacheTagModelResolver());
		_registerCDNCacheTagModelResolver(
			new FileEntryCDNCacheTagModelResolver());
		_registerCDNCacheTagModelResolver(
			new FragmentEntryCDNCacheTagModelResolver());
		_registerCDNCacheTagModelResolver(
			new ObjectEntryCDNCacheTagModelResolver(
				"com.liferay.object.model.ObjectDefinition#C_TICKET"));

		CDNCacheTagModelResolver journalArticleCDNCacheTagModelResolver =
			Mockito.mock(CDNCacheTagModelResolver.class);

		Mockito.when(
			journalArticleCDNCacheTagModelResolver.getCacheTag(
				Mockito.anyLong())
		).thenAnswer(
			invocation -> "wc" + invocation.getArgument(0)
		);

		Mockito.when(
			_serviceTrackerMap.getService(JournalArticle.class.getName())
		).thenReturn(
			journalArticleCDNCacheTagModelResolver
		);

		Mockito.when(
			_portal.getClassNameId(Mockito.anyString())
		).thenAnswer(
			invocation -> _classNameIds.get(invocation.getArgument(0))
		);

		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_configurationProvider",
			_configurationProvider);
		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_layoutLocalService",
			_layoutLocalService);
		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_layoutPageTemplateEntryLocalService",
			_layoutPageTemplateEntryLocalService);
		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_objectDefinitionLocalService",
			_objectDefinitionLocalService);
		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_portal", _portal);
		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_serviceTrackerMap", _serviceTrackerMap);
	}

	@Test
	public void testGetCollectionCacheTag() {
		Assert.assertEquals(
			"ns-l20-30",
			_cdnCacheTagBuilderImpl.getCollectionCacheTag(1, 20, 30));
	}

	@Test
	public void testGetCompanyCacheTag() {
		Assert.assertEquals(
			"ns-c1", _cdnCacheTagBuilderImpl.getCompanyCacheTag(1));
	}

	@Test
	public void testGetGroupCacheTag() {
		Assert.assertEquals(
			"ns-s20", _cdnCacheTagBuilderImpl.getGroupCacheTag(1, 20));
	}

	@Test
	public void testGetLayoutCacheTags() {
		Layout layout = Mockito.mock(Layout.class);

		Mockito.when(
			layout.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			layout.getPlid()
		).thenReturn(
			40L
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-p40"),
			_cdnCacheTagBuilderImpl.getLayoutCacheTags(layout));

		Mockito.when(
			layout.getMasterLayoutPlid()
		).thenReturn(
			41L
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-p41"),
			_cdnCacheTagBuilderImpl.getLayoutCacheTags(layout));
	}

	@Test
	public void testGetModelCacheTag() {
		Assert.assertEquals(
			"ns-b5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.blogs.model.BlogsEntry", 5));
		Assert.assertEquals(
			"ns-d5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.document.library.kernel.model.DLFileEntry", 5));
		Assert.assertEquals(
			"ns-d5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.portal.kernel.repository.model.FileEntry", 5));
		Assert.assertEquals(
			"ns-f5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.fragment.model.FragmentEntry", 5));
		Assert.assertEquals(
			"ns-k5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.knowledge.base.Article", 5));
		Assert.assertEquals(
			"ns-o5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.object.model.ObjectDefinition#C_TICKET", 5));
		Assert.assertNull(
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.portal.kernel.model.Layout", 5));
		Assert.assertNull(
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, RandomTestUtil.randomString(), 5));
	}

	@Test
	public void testGetOverflowCacheTag() {
		Assert.assertEquals(
			"ns-x20", _cdnCacheTagBuilderImpl.getOverflowCacheTag(1, 20));
	}

	@Test
	public void testGetPurgeCacheTags() {

		// Fragment entry

		FragmentEntry fragmentEntry = Mockito.mock(FragmentEntry.class);

		Mockito.when(
			fragmentEntry.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			fragmentEntry.getFragmentEntryId()
		).thenReturn(
			30L
		);

		Mockito.when(
			fragmentEntry.getGroupId()
		).thenReturn(
			20L
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(fragmentEntry));

		Mockito.when(
			fragmentEntry.isHead()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-f30", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(fragmentEntry));

		// Fragment entry link

		FragmentEntryLink fragmentEntryLink = Mockito.mock(
			FragmentEntryLink.class);

		Mockito.when(
			fragmentEntryLink.getPlid()
		).thenReturn(
			40L
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(fragmentEntryLink));

		Layout layout = _mockLayout(40L);

		Mockito.when(
			_layoutLocalService.fetchLayout(40L)
		).thenReturn(
			layout
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(fragmentEntryLink));

		// Layout

		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(layout));

		Layout draftLayout = _mockLayout(41L);

		Mockito.when(
			draftLayout.isDraftLayout()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(draftLayout));

		Layout privateLayout = _mockLayout(42L);

		Mockito.when(
			privateLayout.isPrivateLayout()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(privateLayout));

		Layout masterLayout = _mockLayout(43L);

		Mockito.when(
			masterLayout.isPrivateLayout()
		).thenReturn(
			true
		);

		Mockito.when(
			masterLayout.isSystem()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(masterLayout));

		LayoutPageTemplateEntry layoutPageTemplateEntry = Mockito.mock(
			LayoutPageTemplateEntry.class);

		Mockito.when(
			layoutPageTemplateEntry.getType()
		).thenReturn(
			LayoutPageTemplateEntryTypeConstants.MASTER_LAYOUT
		);

		Mockito.when(
			_layoutPageTemplateEntryLocalService.
				fetchLayoutPageTemplateEntryByPlid(43L)
		).thenReturn(
			layoutPageTemplateEntry
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-p43", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(masterLayout));

		// Layout set

		LayoutSet layoutSet = Mockito.mock(LayoutSet.class);

		Mockito.when(
			layoutSet.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			layoutSet.getGroupId()
		).thenReturn(
			20L
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(layoutSet));

		Mockito.when(
			layoutSet.isPrivateLayout()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(layoutSet));

		// Site navigation menu item

		SiteNavigationMenuItem siteNavigationMenuItem = Mockito.mock(
			SiteNavigationMenuItem.class);

		Mockito.when(
			siteNavigationMenuItem.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			siteNavigationMenuItem.getGroupId()
		).thenReturn(
			20L
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(siteNavigationMenuItem));

		// Style book entry

		StyleBookEntry styleBookEntry = Mockito.mock(StyleBookEntry.class);

		Mockito.when(
			styleBookEntry.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			styleBookEntry.getGroupId()
		).thenReturn(
			20L
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(styleBookEntry));

		Mockito.when(
			styleBookEntry.isHead()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(styleBookEntry));
	}

	@Test
	public void testGetPurgeCacheTagsOfContent() {

		// Blogs entry

		BlogsEntry blogsEntry = _mockWorkflowedModel(BlogsEntry.class);

		Mockito.when(
			blogsEntry.getEntryId()
		).thenReturn(
			50L
		);

		_assertPurgeCacheTags(blogsEntry, "ns-b50", "ns-l20-101", "ns-x20");

		// Document

		DLFileEntry dlFileEntry = Mockito.mock(DLFileEntry.class);

		Mockito.when(
			dlFileEntry.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			dlFileEntry.getFileEntryId()
		).thenReturn(
			51L
		);

		Mockito.when(
			dlFileEntry.getGroupId()
		).thenReturn(
			20L
		);

		Assert.assertEquals(
			SetUtil.fromArray("ns-d51", "ns-l20-102", "ns-l20-103", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(dlFileEntry));

		DLFileVersion dlFileVersion = _mockWorkflowedModel(DLFileVersion.class);

		Mockito.when(
			dlFileVersion.getFileEntryId()
		).thenReturn(
			51L
		);

		_assertPurgeCacheTags(
			dlFileVersion, "ns-d51", "ns-l20-102", "ns-l20-103", "ns-x20");

		// Journal article

		JournalArticle journalArticle = _mockWorkflowedModel(
			JournalArticle.class);

		Mockito.when(
			journalArticle.getResourcePrimKey()
		).thenReturn(
			52L
		);

		_assertPurgeCacheTags(
			journalArticle, "ns-wc52", "ns-l20-104", "ns-x20");

		// Object entry

		ObjectDefinition objectDefinition = Mockito.mock(
			ObjectDefinition.class);

		Mockito.when(
			objectDefinition.getClassName()
		).thenReturn(
			"com.liferay.object.model.ObjectDefinition#C_TICKET"
		);

		Mockito.when(
			_objectDefinitionLocalService.fetchObjectDefinition(60L)
		).thenReturn(
			objectDefinition
		);

		ObjectEntry objectEntry = _mockWorkflowedModel(ObjectEntry.class);

		Mockito.when(
			objectEntry.getObjectDefinitionId()
		).thenReturn(
			60L
		);

		Mockito.when(
			objectEntry.getObjectEntryId()
		).thenReturn(
			53L
		);

		_assertPurgeCacheTags(objectEntry, "ns-o53", "ns-l20-105", "ns-x20");

		Mockito.when(
			objectEntry.getGroupId()
		).thenReturn(
			0L
		);

		_assertPurgeCacheTags(objectEntry, "ns-o53", "ns-l0-105", "ns-x0");

		Mockito.when(
			objectDefinition.isUnmodifiableSystemObject()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(objectEntry));
	}

	@Test
	public void testGetPurgeCacheTagsOfContentChange() {
		JournalArticle approvedJournalArticle = _mockJournalArticle(
			WorkflowConstants.STATUS_APPROVED);
		JournalArticle draftJournalArticle = _mockJournalArticle(
			WorkflowConstants.STATUS_DRAFT);
		JournalArticle expiredJournalArticle = _mockJournalArticle(
			WorkflowConstants.STATUS_EXPIRED);
		JournalArticle scheduledJournalArticle = _mockJournalArticle(
			WorkflowConstants.STATUS_SCHEDULED);

		Set<String> cacheTags = SetUtil.fromArray(
			"ns-wc52", "ns-l20-104", "ns-x20");

		// Add

		Assert.assertEquals(
			cacheTags,
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				null, approvedJournalArticle));

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				null, draftJournalArticle));

		// Update

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				draftJournalArticle, draftJournalArticle));
		Assert.assertEquals(
			cacheTags,
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				draftJournalArticle, approvedJournalArticle));
		Assert.assertEquals(
			cacheTags,
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				scheduledJournalArticle, approvedJournalArticle));
		Assert.assertEquals(
			cacheTags,
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				approvedJournalArticle, expiredJournalArticle));
		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				draftJournalArticle, scheduledJournalArticle));

		// Remove

		Assert.assertEquals(
			cacheTags,
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				approvedJournalArticle, null));
		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				draftJournalArticle, null));
		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(null, null));
	}

	@Test
	public void testGetPurgeCacheTagsOfLayoutChange() {
		Layout layout = _mockNavigationLayout(40L, "/a", "a", 1, 0);

		// Add

		Assert.assertEquals(
			SetUtil.fromArray("ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(null, layout));

		Layout hiddenLayout = _mockNavigationLayout(40L, "/a", "a", 1, 0);

		Mockito.when(
			hiddenLayout.isHidden()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(null, hiddenLayout));

		Layout privateLayout = _mockNavigationLayout(40L, "/a", "a", 1, 0);

		Mockito.when(
			privateLayout.isPrivateLayout()
		).thenReturn(
			true
		);

		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(null, privateLayout));

		// Update

		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				layout, _mockNavigationLayout(40L, "/a", "a", 1, 0)));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				layout, _mockNavigationLayout(40L, "/b", "a", 1, 0)));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				layout, _mockNavigationLayout(40L, "/a", "b", 1, 0)));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				layout, _mockNavigationLayout(40L, "/a", "a", 2, 0)));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				layout, _mockNavigationLayout(40L, "/a", "a", 1, 3)));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(layout, hiddenLayout));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(hiddenLayout, layout));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(
				hiddenLayout,
				_mockNavigationLayout(40L, "/b", "b", 2, 3, true)));

		// Remove

		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20", "ns-s20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(layout, null));
		Assert.assertEquals(
			SetUtil.fromArray("ns-p40", "ns-x20"),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(hiddenLayout, null));
		Assert.assertEquals(
			Collections.emptySet(),
			_cdnCacheTagBuilderImpl.getPurgeCacheTags(privateLayout, null));
	}

	private void _assertPurgeCacheTags(
		WorkflowedModel workflowedModel, String... cacheTags) {

		BaseModel<?> baseModel = (BaseModel<?>)workflowedModel;

		for (int status :
				new int[] {
					WorkflowConstants.STATUS_APPROVED,
					WorkflowConstants.STATUS_EXPIRED,
					WorkflowConstants.STATUS_IN_TRASH
				}) {

			Mockito.when(
				workflowedModel.getStatus()
			).thenReturn(
				status
			);

			Assert.assertEquals(
				SetUtil.fromArray(cacheTags),
				_cdnCacheTagBuilderImpl.getPurgeCacheTags(baseModel));
		}

		for (int status :
				new int[] {
					WorkflowConstants.STATUS_DRAFT,
					WorkflowConstants.STATUS_PENDING,
					WorkflowConstants.STATUS_SCHEDULED
				}) {

			Mockito.when(
				workflowedModel.getStatus()
			).thenReturn(
				status
			);

			Assert.assertEquals(
				Collections.emptySet(),
				_cdnCacheTagBuilderImpl.getPurgeCacheTags(baseModel));
		}
	}

	private JournalArticle _mockJournalArticle(int status) {
		JournalArticle journalArticle = _mockWorkflowedModel(
			JournalArticle.class);

		Mockito.when(
			journalArticle.getResourcePrimKey()
		).thenReturn(
			52L
		);

		Mockito.when(
			journalArticle.getStatus()
		).thenReturn(
			status
		);

		Mockito.when(
			journalArticle.isApproved()
		).thenReturn(
			status == WorkflowConstants.STATUS_APPROVED
		);

		return journalArticle;
	}

	private Layout _mockLayout(long plid) {
		Layout layout = Mockito.mock(Layout.class);

		Mockito.when(
			layout.getCompanyId()
		).thenReturn(
			1L
		);

		Mockito.when(
			layout.getGroupId()
		).thenReturn(
			20L
		);

		Mockito.when(
			layout.getPlid()
		).thenReturn(
			plid
		);

		return layout;
	}

	private Layout _mockNavigationLayout(
		long plid, String friendlyURL, String name, int priority,
		long parentLayoutId) {

		return _mockNavigationLayout(
			plid, friendlyURL, name, priority, parentLayoutId, false);
	}

	private Layout _mockNavigationLayout(
		long plid, String friendlyURL, String name, int priority,
		long parentLayoutId, boolean hidden) {

		Layout layout = _mockLayout(plid);

		Mockito.when(
			layout.getFriendlyURL()
		).thenReturn(
			friendlyURL
		);

		Mockito.when(
			layout.getName()
		).thenReturn(
			name
		);

		Mockito.when(
			layout.getParentLayoutId()
		).thenReturn(
			parentLayoutId
		);

		Mockito.when(
			layout.getPriority()
		).thenReturn(
			priority
		);

		Mockito.when(
			layout.isHidden()
		).thenReturn(
			hidden
		);

		return layout;
	}

	private <T extends WorkflowedModel> T _mockWorkflowedModel(Class<T> clazz) {
		T workflowedModel = Mockito.mock(clazz);

		ShardedModel shardedModel = (ShardedModel)workflowedModel;

		Mockito.when(
			shardedModel.getCompanyId()
		).thenReturn(
			1L
		);

		GroupedModel groupedModel = (GroupedModel)workflowedModel;

		Mockito.when(
			groupedModel.getGroupId()
		).thenReturn(
			20L
		);

		return workflowedModel;
	}

	private void _registerCDNCacheTagModelResolver(
		CDNCacheTagModelResolver cdnCacheTagModelResolver) {

		Mockito.when(
			_serviceTrackerMap.getService(
				cdnCacheTagModelResolver.getClassName())
		).thenReturn(
			cdnCacheTagModelResolver
		);
	}

	private static final Map<String, Long> _classNameIds = HashMapBuilder.put(
		BlogsEntry.class.getName(), 101L
	).put(
		DLFileEntry.class.getName(), 102L
	).put(
		FileEntry.class.getName(), 103L
	).put(
		JournalArticle.class.getName(), 104L
	).put(
		"com.liferay.object.model.ObjectDefinition#C_TICKET", 105L
	).build();

	private final CDNCacheTagBuilderImpl _cdnCacheTagBuilderImpl =
		new CDNCacheTagBuilderImpl();
	private final CDNConfiguration _cdnConfiguration = Mockito.mock(
		CDNConfiguration.class);
	private final ConfigurationProvider _configurationProvider = Mockito.mock(
		ConfigurationProvider.class);
	private final LayoutLocalService _layoutLocalService = Mockito.mock(
		LayoutLocalService.class);
	private final LayoutPageTemplateEntryLocalService
		_layoutPageTemplateEntryLocalService = Mockito.mock(
			LayoutPageTemplateEntryLocalService.class);
	private final ObjectDefinitionLocalService _objectDefinitionLocalService =
		Mockito.mock(ObjectDefinitionLocalService.class);
	private final Portal _portal = Mockito.mock(Portal.class);
	private final ServiceTrackerMap<String, CDNCacheTagModelResolver>
		_serviceTrackerMap = Mockito.mock(ServiceTrackerMap.class);

}