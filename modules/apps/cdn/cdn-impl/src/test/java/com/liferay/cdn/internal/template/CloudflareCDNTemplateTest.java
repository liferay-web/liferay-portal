/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.template;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.http.CDNHTTPPurger;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.purge.CDNPurgeResult;
import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CloudflareCDNTemplateTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		Mockito.when(
			_cdnConfiguration.credential()
		).thenReturn(
			"TOKEN"
		);

		Mockito.when(
			_cdnConfiguration.serviceId()
		).thenReturn(
			"SERVICE_ID"
		);

		Mockito.when(
			_http.URLtoString(Mockito.any(Http.Options.class))
		).thenAnswer(
			invocation -> {
				Http.Options options = invocation.getArgument(0);

				Http.Response response = options.getResponse();

				response.setResponseCode(200);

				_optionsList.add(options);

				return null;
			}
		);

		_cdnHTTPPurger = new CDNHTTPPurger(_http, new JSONFactoryImpl());
	}

	@Test
	public void testGetCacheTagHeaderMaxBytes() {
		Assert.assertEquals(
			16384, _configuredCDNTemplate.getCacheTagHeaderMaxBytes());
		Assert.assertEquals(
			"Cache-Tag", _configuredCDNTemplate.getCacheTagHeaderName());
		Assert.assertEquals(
			",", _configuredCDNTemplate.getCacheTagHeaderSeparator());
		Assert.assertEquals(100, _configuredCDNTemplate.getMaxTagsPerRequest());
		Assert.assertEquals(100, _configuredCDNTemplate.getMaxURLsPerRequest());
	}

	@Test
	public void testPurge() throws Exception {
		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				"lfr-p1", "lfr-x2"
			).urls(
				"https://www.liferay.com/blog", "https://www.liferay.com/home"
			).build());

		Assert.assertTrue(cdnPurgeResult.isSuccess());

		Assert.assertEquals(_optionsList.toString(), 2, _optionsList.size());

		_assertOptions(
			"{\"tags\": [\"lfr-p1\",\"lfr-x2\"]}", _optionsList.get(0));
		_assertOptions(
			"{\"files\": [\"https://www.liferay.com/blog\"," +
				"\"https://www.liferay.com/home\"]}",
			_optionsList.get(1));
	}

	@Test
	public void testPurgeWithPurgeAll() throws Exception {
		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).purgeAll(
				true
			).build());

		Assert.assertTrue(cdnPurgeResult.isSuccess());

		Assert.assertEquals(_optionsList.toString(), 1, _optionsList.size());

		_assertOptions("{\"purge_everything\": true}", _optionsList.get(0));
	}

	private void _assertOptions(String content, Http.Options options) {
		Http.Body body = options.getBody();

		Assert.assertEquals(content, body.getContent());
		Assert.assertEquals("application/json", body.getContentType());

		Assert.assertEquals(
			"https://api.cloudflare.com/client/v4/zones/SERVICE_ID/purge_cache",
			options.getLocation());
		Assert.assertTrue(options.isPost());

		Map<String, String> headers = options.getHeaders();

		Assert.assertEquals(headers.toString(), 1, headers.size());
		Assert.assertEquals("Bearer TOKEN", headers.get("Authorization"));
	}

	private final CDNConfiguration _cdnConfiguration = Mockito.mock(
		CDNConfiguration.class);
	private CDNHTTPPurger _cdnHTTPPurger;
	private final ConfiguredCDNTemplate _configuredCDNTemplate =
		new ConfiguredCDNTemplate(
			_cdnConfiguration, new CloudflareCDNTemplate(),
			RandomTestUtil.randomLong());
	private final Http _http = Mockito.mock(Http.class);
	private final List<Http.Options> _optionsList = new ArrayList<>();

}