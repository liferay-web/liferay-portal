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
public class FastlyCDNTemplateTest {

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

				response.setResponseCode(_responseCode);

				_optionsList.add(options);

				return null;
			}
		);

		_cdnHTTPPurger = new CDNHTTPPurger(_http, new JSONFactoryImpl());
	}

	@Test
	public void testGetCacheTagHeaderMaxBytes() {
		Assert.assertEquals(
			14336, _configuredCDNTemplate.getCacheTagHeaderMaxBytes());
		Assert.assertEquals(
			"Surrogate-Key", _configuredCDNTemplate.getCacheTagHeaderName());
		Assert.assertEquals(
			" ", _configuredCDNTemplate.getCacheTagHeaderSeparator());
	}

	@Test
	public void testPurge() throws Exception {
		Mockito.when(
			_cdnConfiguration.endpoint()
		).thenReturn(
			"https://api.fastly.test/"
		);

		List<String> tags = new ArrayList<>();

		for (int i = 0; i < 256; i++) {
			tags.add("path:/page-" + i);
		}

		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				tags
			).urls(
				"https://www.liferay.com/blog?a=b"
			).build());

		Assert.assertTrue(cdnPurgeResult.isSuccess());

		Assert.assertEquals(_optionsList.toString(), 2, _optionsList.size());

		Http.Options options = _optionsList.get(0);

		Assert.assertNull(options.getBody());
		Assert.assertEquals(
			"https://api.fastly.test/service/SERVICE_ID/purge",
			options.getLocation());
		Assert.assertTrue(options.isPost());

		Map<String, String> headers = options.getHeaders();

		Assert.assertEquals("TOKEN", headers.get("Fastly-Key"));
		Assert.assertEquals("1", headers.get("Fastly-Soft-Purge"));
		Assert.assertEquals(
			String.join(" ", tags), headers.get("Surrogate-Key"));

		options = _optionsList.get(1);

		Assert.assertEquals(
			"https://api.fastly.test/purge/www.liferay.com/blog?a=b",
			options.getLocation());
		Assert.assertTrue(options.isPost());

		headers = options.getHeaders();

		Assert.assertEquals("TOKEN", headers.get("Fastly-Key"));
		Assert.assertEquals("1", headers.get("Fastly-Soft-Purge"));
		Assert.assertNull(headers.get("Surrogate-Key"));
	}

	@Test
	public void testPurgeWithFailure() throws Exception {
		_responseCode = 503;

		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				"p1"
			).urls(
				"https://www.liferay.com"
			).build());

		Assert.assertFalse(cdnPurgeResult.isSuccess());
		Assert.assertTrue(cdnPurgeResult.isRetryable());
		Assert.assertEquals(503, cdnPurgeResult.getStatus());

		Assert.assertEquals(_optionsList.toString(), 1, _optionsList.size());

		_responseCode = 401;

		cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				"p1"
			).build());

		Assert.assertFalse(cdnPurgeResult.isRetryable());
		Assert.assertFalse(cdnPurgeResult.isSuccess());
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

		Http.Options options = _optionsList.get(0);

		Assert.assertEquals(
			"https://api.fastly.com/service/SERVICE_ID/purge_all",
			options.getLocation());

		Map<String, String> headers = options.getHeaders();

		Assert.assertEquals("TOKEN", headers.get("Fastly-Key"));
		Assert.assertNull(headers.get("Fastly-Soft-Purge"));
	}

	private final CDNConfiguration _cdnConfiguration = Mockito.mock(
		CDNConfiguration.class);
	private CDNHTTPPurger _cdnHTTPPurger;
	private final ConfiguredCDNTemplate _configuredCDNTemplate =
		new ConfiguredCDNTemplate(
			_cdnConfiguration, new FastlyCDNTemplate(),
			RandomTestUtil.randomLong());
	private final Http _http = Mockito.mock(Http.class);
	private final List<Http.Options> _optionsList = new ArrayList<>();
	private int _responseCode = 200;

}