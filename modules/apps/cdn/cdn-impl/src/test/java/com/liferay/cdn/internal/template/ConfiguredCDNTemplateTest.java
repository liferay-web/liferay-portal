/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.template;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.template.CDNHTTPRequestTemplate;
import com.liferay.cdn.template.CDNTemplate;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class ConfiguredCDNTemplateTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		Mockito.when(
			_cdnTemplate.getCacheTagHeaderMaxBytes()
		).thenReturn(
			16384
		);

		Mockito.when(
			_cdnTemplate.getCacheTagHeaderSeparator()
		).thenReturn(
			" "
		);

		Mockito.when(
			_cdnTemplate.getEndpoint()
		).thenReturn(
			"https://template.test/"
		);

		Mockito.when(
			_cdnTemplate.getMaxTagsPerRequest()
		).thenReturn(
			256
		);

		Mockito.when(
			_cdnTemplate.getTagPurgeCDNHTTPRequestTemplate()
		).thenReturn(
			CDNHTTPRequestTemplate.builder(
			).headers(
				"Template-Header: 1"
			).urlTemplate(
				"${endpoint}/purge"
			).build()
		);

		Mockito.when(
			_cdnTemplate.isURLPurgePerURL()
		).thenReturn(
			true
		);
	}

	@Test
	public void testGetCDNHTTPRequestTemplate() {
		Assert.assertNull(
			_configuredCDNTemplate.getPurgeAllCDNHTTPRequestTemplate());

		CDNHTTPRequestTemplate cdnHTTPRequestTemplate =
			_configuredCDNTemplate.getTagPurgeCDNHTTPRequestTemplate();

		Assert.assertEquals(
			Collections.singletonList("Template-Header: 1"),
			cdnHTTPRequestTemplate.getHeaders());
		Assert.assertEquals("POST", cdnHTTPRequestTemplate.getMethod());
		Assert.assertEquals(
			"${endpoint}/purge", cdnHTTPRequestTemplate.getURLTemplate());

		Mockito.when(
			_cdnConfiguration.contentType()
		).thenReturn(
			"text/plain"
		);

		Mockito.when(
			_cdnConfiguration.purgeAllURLTemplate()
		).thenReturn(
			"${endpoint}/purge_all"
		);

		Mockito.when(
			_cdnConfiguration.tagPurgeHeaders()
		).thenReturn(
			new String[] {"Configured-Header: 2", ""}
		);

		Mockito.when(
			_cdnConfiguration.tagPurgeMethod()
		).thenReturn(
			"PUT"
		);

		cdnHTTPRequestTemplate =
			_configuredCDNTemplate.getPurgeAllCDNHTTPRequestTemplate();

		Assert.assertEquals("POST", cdnHTTPRequestTemplate.getMethod());
		Assert.assertEquals(
			"${endpoint}/purge_all", cdnHTTPRequestTemplate.getURLTemplate());

		cdnHTTPRequestTemplate =
			_configuredCDNTemplate.getTagPurgeCDNHTTPRequestTemplate();

		Assert.assertEquals(
			"text/plain", cdnHTTPRequestTemplate.getContentType());
		Assert.assertEquals(
			Arrays.asList("Configured-Header: 2"),
			cdnHTTPRequestTemplate.getHeaders());
		Assert.assertEquals("PUT", cdnHTTPRequestTemplate.getMethod());
		Assert.assertEquals(
			"${endpoint}/purge", cdnHTTPRequestTemplate.getURLTemplate());
	}

	@Test
	public void testGetValues() {
		Assert.assertEquals(
			16384, _configuredCDNTemplate.getCacheTagHeaderMaxBytes());
		Assert.assertEquals(
			" ", _configuredCDNTemplate.getCacheTagHeaderSeparator());
		Assert.assertEquals(
			"https://template.test", _configuredCDNTemplate.getEndpoint());
		Assert.assertEquals(256, _configuredCDNTemplate.getMaxTagsPerRequest());
		Assert.assertTrue(_configuredCDNTemplate.isURLPurgePerURL());

		Mockito.when(
			_cdnConfiguration.cacheTagHeaderSeparator()
		).thenReturn(
			","
		);

		Mockito.when(
			_cdnConfiguration.endpoint()
		).thenReturn(
			"https://configuration.test"
		);

		Mockito.when(
			_cdnConfiguration.maxCacheTagHeaderBytes()
		).thenReturn(
			8192
		);

		Mockito.when(
			_cdnConfiguration.maxTagsPerRequest()
		).thenReturn(
			10
		);

		Mockito.when(
			_cdnConfiguration.urlPurgePerURL()
		).thenReturn(
			"false"
		);

		Assert.assertEquals(
			8192, _configuredCDNTemplate.getCacheTagHeaderMaxBytes());
		Assert.assertEquals(
			",", _configuredCDNTemplate.getCacheTagHeaderSeparator());
		Assert.assertEquals(
			"https://configuration.test", _configuredCDNTemplate.getEndpoint());
		Assert.assertEquals(10, _configuredCDNTemplate.getMaxTagsPerRequest());
		Assert.assertFalse(_configuredCDNTemplate.isURLPurgePerURL());
	}

	private final CDNConfiguration _cdnConfiguration = Mockito.mock(
		CDNConfiguration.class);
	private final CDNTemplate _cdnTemplate = Mockito.mock(CDNTemplate.class);
	private final ConfiguredCDNTemplate _configuredCDNTemplate =
		new ConfiguredCDNTemplate(
			_cdnConfiguration, _cdnTemplate, RandomTestUtil.randomLong());

}