/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.http;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.purge.CDNPurgeResult;
import com.liferay.cdn.template.CDNHTTPRequestTemplate;
import com.liferay.cdn.template.CDNTemplate;
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
public class CDNHTTPPurgerTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		_cdnHTTPPurger = new CDNHTTPPurger(_http, new JSONFactoryImpl());

		Mockito.when(
			_cdnConfiguration.credential()
		).thenReturn(
			"TOKEN"
		);

		Mockito.when(
			_cdnConfiguration.serviceId()
		).thenReturn(
			"ZONE"
		);

		Mockito.when(
			_cdnTemplate.getCacheTagHeaderSeparator()
		).thenReturn(
			" "
		);

		Mockito.when(
			_cdnTemplate.getEndpoint()
		).thenReturn(
			"https://cdn.test/"
		);

		Mockito.when(
			_cdnTemplate.getKey()
		).thenReturn(
			"test"
		);

		Mockito.when(
			_cdnTemplate.getSoftPurgeHeader()
		).thenReturn(
			"Soft-Purge: 1"
		);

		Mockito.when(
			_cdnTemplate.getTagPurgeCDNHTTPRequestTemplate()
		).thenReturn(
			CDNHTTPRequestTemplate.builder(
			).bodyTemplate(
				"{\"objects\": ${tags}, \"purgeAll\": ${purgeAll}, \"soft\": " +
					"${soft}}"
			).headers(
				"Authorization: Bearer ${credential}",
				"xkey-purge: ${tagsHeader}", "invalid"
			).method(
				"put"
			).urlTemplate(
				"${endpoint}/zones/${serviceId}/purge"
			).build()
		);

		Mockito.when(
			_cdnTemplate.getURLPurgeCDNHTTPRequestTemplate()
		).thenReturn(
			CDNHTTPRequestTemplate.builder(
			).bodyTemplate(
				"{\"files\": ${urls}}"
			).urlTemplate(
				"${endpoint}/purge/${urlWithoutProtocol}"
			).build()
		);

		Mockito.when(
			_http.URLtoString(Mockito.any(Http.Options.class))
		).thenAnswer(
			invocation -> {
				Http.Options options = invocation.getArgument(0);

				_optionsList.add(options);

				Http.Response response = options.getResponse();

				response.setResponseCode(_responseCode);

				return null;
			}
		);
	}

	@Test
	public void testPurge() throws Exception {
		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				"p1", "wc2"
			).urls(
				"https://www.liferay.com/home"
			).build());

		Assert.assertTrue(cdnPurgeResult.isSuccess());

		Assert.assertEquals(_optionsList.toString(), 2, _optionsList.size());

		Http.Options options = _optionsList.get(0);

		Assert.assertEquals(
			"https://cdn.test/zones/ZONE/purge", options.getLocation());
		Assert.assertEquals(Http.Method.PUT, options.getMethod());

		Map<String, String> headers = options.getHeaders();

		Assert.assertEquals(headers.toString(), 3, headers.size());
		Assert.assertEquals("Bearer TOKEN", headers.get("Authorization"));
		Assert.assertEquals("1", headers.get("Soft-Purge"));
		Assert.assertEquals("p1 wc2", headers.get("xkey-purge"));

		Http.Body body = options.getBody();

		Assert.assertEquals(
			"{\"objects\": [\"p1\",\"wc2\"], \"purgeAll\": false, \"soft\": " +
				"true}",
			body.getContent());
		Assert.assertEquals("application/json", body.getContentType());

		options = _optionsList.get(1);

		body = options.getBody();

		Assert.assertEquals(
			"{\"files\": [\"https://www.liferay.com/home\"]}",
			body.getContent());

		Assert.assertEquals("https://cdn.test/purge/", options.getLocation());
		Assert.assertEquals(Http.Method.POST, options.getMethod());
	}

	@Test
	public void testPurgeWithFailure() throws Exception {
		_responseCode = 429;

		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				"p1"
			).urls(
				"https://www.liferay.com/home"
			).build());

		Assert.assertTrue(cdnPurgeResult.isRetryable());
		Assert.assertEquals(429, cdnPurgeResult.getStatus());

		Assert.assertEquals(_optionsList.toString(), 1, _optionsList.size());

		cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).purgeAll(
				true
			).build());

		Assert.assertFalse(cdnPurgeResult.isRetryable());
		Assert.assertFalse(cdnPurgeResult.isSuccess());

		Mockito.when(
			_cdnConfiguration.tagPurgeMethod()
		).thenReturn(
			"BAN"
		);

		cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).tags(
				"p1"
			).build());

		Assert.assertFalse(cdnPurgeResult.isRetryable());
		Assert.assertFalse(cdnPurgeResult.isSuccess());

		Assert.assertEquals(_optionsList.toString(), 1, _optionsList.size());
	}

	@Test
	public void testPurgeWithURLPurgePerURL() throws Exception {
		Mockito.when(
			_cdnTemplate.isURLPurgePerURL()
		).thenReturn(
			true
		);

		CDNPurgeResult cdnPurgeResult = _cdnHTTPPurger.purge(
			_configuredCDNTemplate,
			CDNPurgeRequest.builder(
			).soft(
				false
			).urls(
				"https://www.liferay.com/home", "https://www.liferay.com/blog"
			).build());

		Assert.assertTrue(cdnPurgeResult.isSuccess());

		Assert.assertEquals(_optionsList.toString(), 2, _optionsList.size());

		Http.Options options = _optionsList.get(0);

		Assert.assertEquals(
			"https://cdn.test/purge/www.liferay.com/home",
			options.getLocation());

		Assert.assertNull(options.getHeaders());

		options = _optionsList.get(1);

		Assert.assertEquals(
			"https://cdn.test/purge/www.liferay.com/blog",
			options.getLocation());
	}

	private final CDNConfiguration _cdnConfiguration = Mockito.mock(
		CDNConfiguration.class);
	private CDNHTTPPurger _cdnHTTPPurger;
	private final CDNTemplate _cdnTemplate = Mockito.mock(CDNTemplate.class);
	private final ConfiguredCDNTemplate _configuredCDNTemplate =
		new ConfiguredCDNTemplate(
			_cdnConfiguration, _cdnTemplate, RandomTestUtil.randomLong());
	private final Http _http = Mockito.mock(Http.class);
	private final List<Http.Options> _optionsList = new ArrayList<>();
	private int _responseCode = 200;

}