/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.LayoutSetLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;

import java.net.InetSocketAddress;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
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
public class CDNPurgeManagerImplTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_httpServer = HttpServer.create(
			new InetSocketAddress("127.0.0.1", 0), 0);

		_httpServer.createContext(
			"/purge",
			httpExchange -> {
				try (InputStream inputStream = httpExchange.getRequestBody()) {
					_bodies.add(
						new String(StreamUtil.toByteArray(inputStream)));
				}

				httpExchange.sendResponseHeaders(200, -1);

				httpExchange.close();
			});

		_httpServer.start();
	}

	@After
	public void tearDown() {
		_httpServer.stop(0);
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testPurge() throws Throwable {
		long companyId = TestPropsValues.getCompanyId();

		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper(companyId)) {

			Assert.assertTrue(_cdnPurgeManager.isEnabled(companyId));

			TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					_cdnPurgeManager.purge(
						companyId,
						CDNPurgeRequest.builder(
						).tags(
							"p1"
						).build());
					_cdnPurgeManager.purge(
						companyId,
						CDNPurgeRequest.builder(
						).tags(
							"p2"
						).build());

					Assert.assertNull(_bodies.poll(1, TimeUnit.SECONDS));

					return null;
				});

			String body = _bodies.poll(30, TimeUnit.SECONDS);

			Assert.assertNotNull(body);

			JSONObject jsonObject = JSONFactoryUtil.createJSONObject(body);

			JSONArray jsonArray = jsonObject.getJSONArray("tags");

			Assert.assertEquals("p1", jsonArray.getString(0));
			Assert.assertEquals("p2", jsonArray.getString(1));

			Assert.assertNull(_bodies.poll(2, TimeUnit.SECONDS));

			Assert.assertThrows(
				IllegalStateException.class,
				() -> TransactionInvokerUtil.invoke(
					_transactionConfig,
					() -> {
						_cdnPurgeManager.purge(
							companyId,
							CDNPurgeRequest.builder(
							).tags(
								"p3"
							).build());

						throw new IllegalStateException();
					}));

			Assert.assertNull(_bodies.poll(2, TimeUnit.SECONDS));
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testPurgeByURL() throws Exception {
		Group group = GroupTestUtil.addGroup();

		Layout homeLayout = LayoutTestUtil.addTypePortletLayout(group);

		Layout layout = LayoutTestUtil.addTypePortletLayout(group);

		try {
			_testPurgeByURL(group, homeLayout, layout);
		}
		finally {
			_waitForCDNDisabled();

			_groupLocalService.deleteGroup(group);
		}
	}

	@FeatureFlag(enable = false, value = "LPD-107638")
	@Test
	public void testPurgeWhenFeatureFlagIsDisabled() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper(companyId)) {

			Assert.assertFalse(_cdnPurgeManager.isEnabled(companyId));

			_cdnPurgeManager.purge(
				companyId,
				CDNPurgeRequest.builder(
				).tags(
					"p1"
				).build());

			Assert.assertNull(_bodies.poll(2, TimeUnit.SECONDS));
		}
	}

	private boolean _containsURL(Set<String> urls, String path) {
		for (String url : urls) {
			if (url.endsWith(path)) {
				return true;
			}
		}

		return false;
	}

	private void _drainPurges() throws Exception {
		String body = _bodies.poll(2, TimeUnit.SECONDS);

		while (body != null) {
			body = _bodies.poll(2, TimeUnit.SECONDS);
		}
	}

	private CompanyConfigurationTemporarySwapper
			_getCDNCompanyConfigurationTemporarySwapper(long companyId)
		throws Exception {

		return _getCDNCompanyConfigurationTemporarySwapper(
			companyId, Collections.emptyMap());
	}

	private CompanyConfigurationTemporarySwapper
			_getCDNCompanyConfigurationTemporarySwapper(
				long companyId, Map<String, Object> properties)
		throws Exception {

		InetSocketAddress inetSocketAddress = _httpServer.getAddress();

		Dictionary<String, Object> dictionary =
			HashMapDictionaryBuilder.<String, Object>put(
				"coalesceWindowMs", 100L
			).put(
				"enabled", true
			).put(
				"endpoint",
				StringBundler.concat(
					"http://127.0.0.1:", inetSocketAddress.getPort(), "/purge")
			).put(
				"templateKey", "custom"
			).build();

		for (Map.Entry<String, Object> entry : properties.entrySet()) {
			dictionary.put(entry.getKey(), entry.getValue());
		}

		return new CompanyConfigurationTemporarySwapper(
			companyId, "com.liferay.cdn.configuration.CDNConfiguration",
			dictionary);
	}

	/**
	 * Returns the URLs of the next purge by URL.
	 */
	private Set<String> _pollURLs() throws Exception {
		String body = _bodies.poll(30, TimeUnit.SECONDS);

		Assert.assertNotNull(body);

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject(body);

		JSONArray jsonArray = jsonObject.getJSONArray("tags");

		Assert.assertEquals(body, 0, jsonArray.length());

		return SetUtil.fromList(
			JSONUtil.toStringList(jsonObject.getJSONArray("urls")));
	}

	private void _testPurgeByURL(Group group, Layout homeLayout, Layout layout)
		throws Exception {

		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId(),
						HashMapBuilder.<String, Object>put(
							"urlPurgeOnly", true
						).build())) {

			_waitForURLPurgeOnly();

			// A page change purges the page's URLs

			layout.setTitle(RandomTestUtil.randomString());

			layout = _layoutLocalService.updateLayout(layout);

			String sitePath = "/web" + group.getFriendlyURL();

			Set<String> urls = _pollURLs();

			Assert.assertTrue(
				urls.toString(),
				_containsURL(urls, sitePath + layout.getFriendlyURL()));
			Assert.assertFalse(
				urls.toString(),
				_containsURL(urls, sitePath + homeLayout.getFriendlyURL()));

			// A site change purges the URLs of every page of the site

			_layoutSetLocalService.updateSettings(
				group.getGroupId(), false, RandomTestUtil.randomString());

			urls = _pollURLs();

			Assert.assertTrue(
				urls.toString(),
				_containsURL(urls, sitePath + layout.getFriendlyURL()));
			Assert.assertTrue(
				urls.toString(),
				_containsURL(urls, sitePath + homeLayout.getFriendlyURL()));

			// The home page is also purged at the site root

			Assert.assertTrue(urls.toString(), _containsURL(urls, sitePath));
		}
	}

	/**
	 * Waits until the CDN is disabled again, so the removal of the test data
	 * is not purged while the configuration changes.
	 */
	private void _waitForCDNDisabled() throws Exception {
		long deadline = System.currentTimeMillis() + 10000;

		while (_cdnPurgeManager.isEnabled(TestPropsValues.getCompanyId())) {
			Assert.assertTrue(System.currentTimeMillis() < deadline);

			Thread.sleep(100);
		}
	}

	/**
	 * Waits until the configuration that purges by URL only is applied, and
	 * discards the purges sent before.
	 */
	private void _waitForURLPurgeOnly() throws Exception {
		long deadline = System.currentTimeMillis() + 10000;

		while (!_configurationProvider.getCompanyConfiguration(
					CDNConfiguration.class, TestPropsValues.getCompanyId()
				).urlPurgeOnly()) {

			Assert.assertTrue(System.currentTimeMillis() < deadline);

			Thread.sleep(100);
		}

		_drainPurges();
	}

	private static final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	private final BlockingQueue<String> _bodies = new LinkedBlockingQueue<>();

	@Inject
	private CDNPurgeManager _cdnPurgeManager;

	@Inject
	private ConfigurationProvider _configurationProvider;

	@Inject
	private GroupLocalService _groupLocalService;

	private HttpServer _httpServer;

	@Inject
	private LayoutLocalService _layoutLocalService;

	@Inject
	private LayoutSetLocalService _layoutSetLocalService;

}