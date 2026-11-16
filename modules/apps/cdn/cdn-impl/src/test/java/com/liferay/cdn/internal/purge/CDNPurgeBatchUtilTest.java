/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge;

import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.template.CDNHTTPRequestTemplate;
import com.liferay.cdn.template.CDNTemplate;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeBatchUtilTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testGetCDNPurgeRequests() {
		List<String> tags = new ArrayList<>();

		for (int i = 0; i < 600; i++) {
			tags.add("p" + i);
		}

		List<CDNPurgeRequest> cdnPurgeRequests =
			CDNPurgeBatchUtil.getCDNPurgeRequests(
				CDNPurgeRequest.builder(
				).tags(
					tags
				).urls(
					RandomTestUtil.randomString(), RandomTestUtil.randomString()
				).build(),
				_mockCDNTemplate(256, 1, false, true, true));

		Assert.assertEquals(
			cdnPurgeRequests.toString(), 5, cdnPurgeRequests.size());

		List<String> batchTags = new ArrayList<>();

		for (int i = 0; i < 3; i++) {
			CDNPurgeRequest cdnPurgeRequest = cdnPurgeRequests.get(i);

			Assert.assertTrue(cdnPurgeRequest.isSoft());
			Assert.assertEquals(
				Collections.emptySet(), cdnPurgeRequest.getURLs());

			batchTags.addAll(cdnPurgeRequest.getTags());
		}

		Assert.assertEquals(tags, batchTags);

		CDNPurgeRequest cdnPurgeRequest = cdnPurgeRequests.get(2);

		Set<String> lastTags = cdnPurgeRequest.getTags();

		Assert.assertEquals(lastTags.toString(), 88, lastTags.size());

		for (int i = 3; i < 5; i++) {
			cdnPurgeRequest = cdnPurgeRequests.get(i);

			Assert.assertEquals(
				Collections.emptySet(), cdnPurgeRequest.getTags());

			Set<String> urls = cdnPurgeRequest.getURLs();

			Assert.assertEquals(urls.toString(), 1, urls.size());
		}
	}

	@Test
	public void testGetCDNPurgeRequestsWithPurgeAll() {
		CDNPurgeRequest cdnPurgeRequest = CDNPurgeRequest.builder(
		).purgeAll(
			true
		).tags(
			RandomTestUtil.randomString()
		).build();

		Assert.assertEquals(
			Collections.emptyList(),
			CDNPurgeBatchUtil.getCDNPurgeRequests(
				cdnPurgeRequest, _mockCDNTemplate(0, 0, false, false, false)));

		List<CDNPurgeRequest> cdnPurgeRequests =
			CDNPurgeBatchUtil.getCDNPurgeRequests(
				cdnPurgeRequest, _mockCDNTemplate(0, 0, true, true, false));

		Assert.assertEquals(
			cdnPurgeRequests.toString(), 1, cdnPurgeRequests.size());

		cdnPurgeRequest = cdnPurgeRequests.get(0);

		Assert.assertTrue(cdnPurgeRequest.isPurgeAll());
		Assert.assertTrue(cdnPurgeRequest.isSoft());
		Assert.assertEquals(Collections.emptySet(), cdnPurgeRequest.getTags());
	}

	@Test
	public void testGetCDNPurgeRequestsWithUnsupportedTags() {
		List<CDNPurgeRequest> cdnPurgeRequests =
			CDNPurgeBatchUtil.getCDNPurgeRequests(
				CDNPurgeRequest.builder(
				).tags(
					RandomTestUtil.randomString()
				).urls(
					RandomTestUtil.randomString()
				).build(),
				_mockCDNTemplate(0, 1, false, false, true));

		Assert.assertEquals(
			cdnPurgeRequests.toString(), 1, cdnPurgeRequests.size());

		CDNPurgeRequest cdnPurgeRequest = cdnPurgeRequests.get(0);

		Assert.assertEquals(Collections.emptySet(), cdnPurgeRequest.getTags());
	}

	private CDNTemplate _mockCDNTemplate(
		int maxTagsPerRequest, int maxURLsPerRequest, boolean purgeAll,
		boolean tagPurge, boolean urlPurge) {

		CDNTemplate cdnTemplate = Mockito.mock(CDNTemplate.class);

		Mockito.when(
			cdnTemplate.getMaxTagsPerRequest()
		).thenReturn(
			maxTagsPerRequest
		);

		Mockito.when(
			cdnTemplate.getMaxURLsPerRequest()
		).thenReturn(
			maxURLsPerRequest
		);

		if (purgeAll) {
			Mockito.when(
				cdnTemplate.getPurgeAllCDNHTTPRequestTemplate()
			).thenReturn(
				CDNHTTPRequestTemplate.builder(
				).build()
			);
		}

		if (tagPurge) {
			Mockito.when(
				cdnTemplate.getTagPurgeCDNHTTPRequestTemplate()
			).thenReturn(
				CDNHTTPRequestTemplate.builder(
				).build()
			);
		}

		if (urlPurge) {
			Mockito.when(
				cdnTemplate.getURLPurgeCDNHTTPRequestTemplate()
			).thenReturn(
				CDNHTTPRequestTemplate.builder(
				).build()
			);
		}

		return cdnTemplate;
	}

}