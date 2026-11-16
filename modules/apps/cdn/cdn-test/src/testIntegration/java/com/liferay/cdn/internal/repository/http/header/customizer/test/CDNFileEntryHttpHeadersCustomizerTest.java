/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.repository.http.header.customizer.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.Base64;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
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
public class CDNFileEntryHttpHeadersCustomizerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				_group, TestPropsValues.getUserId());

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);

		String title = StringUtil.randomString();

		_fileEntry = _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, title + ".txt",
			ContentTypes.TEXT_PLAIN, title, null, null, null,
			RandomTestUtil.randomBytes(), null, null, null, serviceContext);
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testCustomizeHttpHeaders() throws Exception {
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

			Assert.assertEquals(
				SetUtil.fromArray(
					"lfr-c" + companyId, "lfr-d" + _fileEntry.getFileEntryId()),
				_getCacheTags(false));
			Assert.assertNull(_getCacheTags(true));
		}
	}

	@FeatureFlag(enable = false, value = "LPD-107638")
	@Test
	public void testCustomizeHttpHeadersWhenFeatureFlagIsDisabled()
		throws Exception {

		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					_getCompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId())) {

			Assert.assertNull(_getCacheTags(false));
		}
	}

	private Set<String> _getCacheTags(boolean signedIn) throws Exception {
		URL url = new URL(
			StringBundler.concat(
				"http://localhost:", PortalUtil.getPortalServerPort(false),
				"/documents/", _fileEntry.getGroupId(), "/",
				_fileEntry.getFolderId(), "/", _fileEntry.getTitle(), "/",
				_fileEntry.getUuid()));

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

	@Inject
	private DLAppLocalService _dlAppLocalService;

	private FileEntry _fileEntry;

	@DeleteAfterTestRun
	private Group _group;

}