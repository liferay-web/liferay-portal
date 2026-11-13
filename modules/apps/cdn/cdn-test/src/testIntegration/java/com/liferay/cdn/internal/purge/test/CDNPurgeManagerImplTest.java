/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;

import java.net.InetSocketAddress;

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

	private CompanyConfigurationTemporarySwapper
			_getCDNCompanyConfigurationTemporarySwapper(long companyId)
		throws Exception {

		InetSocketAddress inetSocketAddress = _httpServer.getAddress();

		return new CompanyConfigurationTemporarySwapper(
			companyId, "com.liferay.cdn.configuration.CDNConfiguration",
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
			).build());
	}

	private static final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	private final BlockingQueue<String> _bodies = new LinkedBlockingQueue<>();

	@Inject
	private CDNPurgeManager _cdnPurgeManager;

	private HttpServer _httpServer;

}