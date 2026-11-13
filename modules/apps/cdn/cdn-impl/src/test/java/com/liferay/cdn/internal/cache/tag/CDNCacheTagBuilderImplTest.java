/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag;

import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

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

		Mockito.when(
			_serviceTrackerMap.getService(
				"com.liferay.journal.model.JournalArticle")
		).thenReturn(
			null
		);

		ReflectionTestUtil.setFieldValue(
			_cdnCacheTagBuilderImpl, "_serviceTrackerMap", _serviceTrackerMap);
	}

	@Test
	public void testGetCollectionCacheTag() {
		Assert.assertEquals(
			"lfr-l20-30",
			_cdnCacheTagBuilderImpl.getCollectionCacheTag(1, 20, 30));
	}

	@Test
	public void testGetCompanyCacheTag() {
		Assert.assertEquals(
			"lfr-c1", _cdnCacheTagBuilderImpl.getCompanyCacheTag(1));
	}

	@Test
	public void testGetGroupCacheTag() {
		Assert.assertEquals(
			"lfr-s20", _cdnCacheTagBuilderImpl.getGroupCacheTag(1, 20));
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
			SetUtil.fromArray("lfr-p40"),
			_cdnCacheTagBuilderImpl.getLayoutCacheTags(layout));

		Mockito.when(
			layout.getMasterLayoutPlid()
		).thenReturn(
			41L
		);

		Assert.assertEquals(
			SetUtil.fromArray("lfr-p40", "lfr-p41"),
			_cdnCacheTagBuilderImpl.getLayoutCacheTags(layout));
	}

	@Test
	public void testGetModelCacheTag() {
		Assert.assertEquals(
			"lfr-b5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.blogs.model.BlogsEntry", 5));
		Assert.assertEquals(
			"lfr-d5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.document.library.kernel.model.DLFileEntry", 5));
		Assert.assertEquals(
			"lfr-f5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.fragment.model.FragmentEntry", 5));
		Assert.assertEquals(
			"lfr-k5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.knowledge.base.Article", 5));
		Assert.assertEquals(
			"lfr-o5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.object.model.ObjectDefinition#C_TICKET", 5));
		Assert.assertEquals(
			"lfr-p5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.portal.kernel.model.Layout", 5));
		Assert.assertEquals(
			"lfr-wc5",
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, "com.liferay.journal.model.JournalArticle", 5));
		Assert.assertNull(
			_cdnCacheTagBuilderImpl.getModelCacheTag(
				1, RandomTestUtil.randomString(), 5));
	}

	@Test
	public void testGetOverflowCacheTag() {
		Assert.assertEquals(
			"lfr-x20", _cdnCacheTagBuilderImpl.getOverflowCacheTag(1, 20));
	}

	private final CDNCacheTagBuilderImpl _cdnCacheTagBuilderImpl =
		new CDNCacheTagBuilderImpl();
	private final ServiceTrackerMap<String, CDNCacheTagModelResolver>
		_serviceTrackerMap = Mockito.mock(ServiceTrackerMap.class);

}