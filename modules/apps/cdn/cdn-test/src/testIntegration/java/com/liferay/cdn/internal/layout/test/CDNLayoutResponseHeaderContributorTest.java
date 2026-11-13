/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.layout.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.journal.constants.JournalContentPortletKeys;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.Base64;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.io.InputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gianmarco Brunialti Masera
 */
@RunWith(Arquillian.class)
public class CDNLayoutResponseHeaderContributorTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		UserTestUtil.setUser(TestPropsValues.getUser());
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testContribute() throws Exception {
		Layout contentLayout = LayoutTestUtil.addTypeContentPublishedLayout(
			_group, RandomTestUtil.randomString(),
			WorkflowConstants.STATUS_APPROVED);

		JournalArticle journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Layout portletLayout = LayoutTestUtil.addTypePortletLayout(_group);

		LayoutTestUtil.addPortletToLayout(
			portletLayout, JournalContentPortletKeys.JOURNAL_CONTENT,
			HashMapBuilder.put(
				"articleId", new String[] {journalArticle.getArticleId()}
			).put(
				"groupId",
				new String[] {String.valueOf(journalArticle.getGroupId())}
			).build());

		long companyId = TestPropsValues.getCompanyId();

		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					_getCompanyConfigurationTemporarySwapper(companyId);
			CompanyConfigurationTemporarySwapper
				basicAuthCompanyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						companyId,
						"com.liferay.portal.security.auto.login.basic.auth." +
							"header",
						HashMapDictionaryBuilder.<String, Object>put(
							"enabled", true
						).build())) {

			String companyCacheTag = "lfr-c" + companyId;
			String groupCacheTag = "lfr-s" + _group.getGroupId();

			Assert.assertEquals(
				SetUtil.fromArray(
					companyCacheTag, groupCacheTag,
					"lfr-p" + contentLayout.getPlid()),
				_getCacheTags(contentLayout, false));
			Assert.assertEquals(
				SetUtil.fromArray(
					companyCacheTag, groupCacheTag,
					"lfr-p" + portletLayout.getPlid(),
					"lfr-wc" + journalArticle.getResourcePrimKey()),
				_getCacheTags(portletLayout, false));

			Assert.assertNull(_getCacheTags(contentLayout, true));
		}
	}

	@FeatureFlag(enable = false, value = "LPD-107638")
	@Test
	public void testContributeWhenFeatureFlagIsDisabled() throws Exception {
		Layout layout = LayoutTestUtil.addTypeContentPublishedLayout(
			_group, RandomTestUtil.randomString(),
			WorkflowConstants.STATUS_APPROVED);

		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					_getCompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId())) {

			Assert.assertNull(_getCacheTags(layout, false));
		}
	}

	private Set<String> _getCacheTags(Layout layout, boolean signedIn)
		throws Exception {

		URL url = new URL(
			StringBundler.concat(
				"http://localhost:", PortalUtil.getPortalServerPort(false),
				"/web", _group.getFriendlyURL(), layout.getFriendlyURL()));

		HttpURLConnection httpURLConnection =
			(HttpURLConnection)url.openConnection();

		if (signedIn) {
			httpURLConnection.setRequestProperty(
				"Authorization",
				"Basic " + Base64.encode("test@liferay.com:test".getBytes()));
		}

		try (InputStream inputStream = httpURLConnection.getInputStream()) {
			Assert.assertEquals(200, httpURLConnection.getResponseCode());

			StreamUtil.toByteArray(inputStream);

			String cacheTagHeaderValue = httpURLConnection.getHeaderField(
				"Cache-Tag");

			if (cacheTagHeaderValue == null) {
				return null;
			}

			return SetUtil.fromArray(StringUtil.split(cacheTagHeaderValue));
		}
		finally {
			httpURLConnection.disconnect();
		}
	}

	private CompanyConfigurationTemporarySwapper
			_getCompanyConfigurationTemporarySwapper(long companyId)
		throws Exception {

		return new CompanyConfigurationTemporarySwapper(
			companyId, "com.liferay.cdn.configuration.CDNConfiguration",
			HashMapDictionaryBuilder.<String, Object>put(
				"emitCacheTags", true
			).put(
				"enabled", true
			).put(
				"templateKey", "custom"
			).build());
	}

	@DeleteAfterTestRun
	private Group _group;

}