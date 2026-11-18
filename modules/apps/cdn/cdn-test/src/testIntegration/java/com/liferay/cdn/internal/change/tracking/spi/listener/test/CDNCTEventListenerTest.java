/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.change.tracking.spi.listener.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.change.tracking.model.CTCollection;
import com.liferay.change.tracking.service.CTCollectionLocalService;
import com.liferay.change.tracking.service.CTCollectionService;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

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
public class CDNCTEventListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

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
	public void testOnAfterPublish() throws Exception {
		Layout layout = LayoutTestUtil.addTypeContentPublishedLayout(
			_group, RandomTestUtil.randomString(),
			WorkflowConstants.STATUS_APPROVED);

		InetSocketAddress inetSocketAddress = _httpServer.getAddress();

		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId(),
						"com.liferay.cdn.configuration.CDNConfiguration",
						HashMapDictionaryBuilder.<String, Object>put(
							"coalesceWindowMs", 100L
						).put(
							"enabled", true
						).put(
							"endpoint",
							StringBundler.concat(
								"http://127.0.0.1:",
								inetSocketAddress.getPort(), "/purge")
						).put(
							"templateKey", "custom"
						).build())) {

			// Modification

			CTCollection ctCollection = _addCTCollection();

			try (SafeCloseable safeCloseable =
					CTCollectionThreadLocal.setCTCollectionIdWithSafeCloseable(
						ctCollection.getCtCollectionId())) {

				Layout ctLayout = _layoutLocalService.getLayout(
					layout.getPlid());

				ctLayout.setTitle(RandomTestUtil.randomString());

				_layoutLocalService.updateLayout(ctLayout);
			}

			_assertNoPurge();

			_ctCollectionService.publishCTCollection(
				TestPropsValues.getUserId(), ctCollection.getCtCollectionId());

			_assertPurge(
				"lfr-p" + layout.getPlid(), "lfr-x" + _group.getGroupId());

			// Deletion

			ctCollection = _addCTCollection();

			try (SafeCloseable safeCloseable =
					CTCollectionThreadLocal.setCTCollectionIdWithSafeCloseable(
						ctCollection.getCtCollectionId())) {

				_layoutLocalService.deleteLayout(layout.getPlid());
			}

			_assertNoPurge();

			_ctCollectionService.publishCTCollection(
				TestPropsValues.getUserId(), ctCollection.getCtCollectionId());

			_assertPurge(
				"lfr-p" + layout.getPlid(), "lfr-x" + _group.getGroupId());

			Assert.assertNull(
				_layoutLocalService.fetchLayout(layout.getPlid()));
		}
	}

	private CTCollection _addCTCollection() throws Exception {
		return _ctCollectionLocalService.addCTCollection(
			null, TestPropsValues.getCompanyId(), TestPropsValues.getUserId(),
			0, StringUtil.randomString(), null);
	}

	private void _assertNoPurge() throws Exception {
		Assert.assertNull(_bodies.poll(2, TimeUnit.SECONDS));
	}

	private void _assertPurge(String... tags) throws Exception {
		String body = _bodies.poll(30, TimeUnit.SECONDS);

		Assert.assertNotNull(body);

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject(body);

		Assert.assertEquals(
			SetUtil.fromArray(tags),
			SetUtil.fromList(
				JSONUtil.toStringList(jsonObject.getJSONArray("tags"))));

		_assertNoPurge();
	}

	private final BlockingQueue<String> _bodies = new LinkedBlockingQueue<>();

	@Inject
	private CTCollectionLocalService _ctCollectionLocalService;

	@Inject
	private CTCollectionService _ctCollectionService;

	@DeleteAfterTestRun
	private Group _group;

	private HttpServer _httpServer;

	@Inject
	private LayoutLocalService _layoutLocalService;

}