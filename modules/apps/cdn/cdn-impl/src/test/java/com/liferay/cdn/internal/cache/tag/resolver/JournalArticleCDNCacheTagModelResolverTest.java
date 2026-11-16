/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag.resolver;

import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleResource;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalArticleResourceLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class JournalArticleCDNCacheTagModelResolverTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		ReflectionTestUtil.setFieldValue(
			_journalArticleCDNCacheTagModelResolver,
			"_journalArticleLocalService", _journalArticleLocalService);
		ReflectionTestUtil.setFieldValue(
			_journalArticleCDNCacheTagModelResolver,
			"_journalArticleResourceLocalService",
			_journalArticleResourceLocalService);
	}

	@Test
	public void testGetCacheTag() {
		long resourcePrimKey = RandomTestUtil.randomLong();

		Mockito.when(
			_journalArticleResourceLocalService.fetchJournalArticleResource(
				resourcePrimKey)
		).thenReturn(
			Mockito.mock(JournalArticleResource.class)
		);

		Assert.assertEquals(
			"wc" + resourcePrimKey,
			_journalArticleCDNCacheTagModelResolver.getCacheTag(
				resourcePrimKey));

		Mockito.verifyNoInteractions(_journalArticleLocalService);

		JournalArticle journalArticle = Mockito.mock(JournalArticle.class);

		Mockito.when(
			journalArticle.getResourcePrimKey()
		).thenReturn(
			resourcePrimKey
		);

		long id = RandomTestUtil.randomLong();

		Mockito.when(
			_journalArticleLocalService.fetchJournalArticle(id)
		).thenReturn(
			journalArticle
		);

		Assert.assertEquals(
			"wc" + resourcePrimKey,
			_journalArticleCDNCacheTagModelResolver.getCacheTag(id));

		long classPK = RandomTestUtil.randomLong();

		Assert.assertEquals(
			"wc" + classPK,
			_journalArticleCDNCacheTagModelResolver.getCacheTag(classPK));
	}

	private final JournalArticleCDNCacheTagModelResolver
		_journalArticleCDNCacheTagModelResolver =
			new JournalArticleCDNCacheTagModelResolver();
	private final JournalArticleLocalService _journalArticleLocalService =
		Mockito.mock(JournalArticleLocalService.class);
	private final JournalArticleResourceLocalService
		_journalArticleResourceLocalService = Mockito.mock(
			JournalArticleResourceLocalService.class);

}