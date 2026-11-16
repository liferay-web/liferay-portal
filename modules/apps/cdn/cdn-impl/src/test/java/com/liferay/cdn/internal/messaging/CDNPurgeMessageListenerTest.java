/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.messaging;

import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.constants.CDNConstants;
import com.liferay.cdn.internal.http.CDNHTTPPurger;
import com.liferay.cdn.internal.template.CDNTemplateRegistry;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.purge.CDNPurgeResult;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.io.IOException;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeMessageListenerTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		Mockito.when(
			_configuredCDNTemplate.getKey()
		).thenReturn(
			"test"
		);

		Mockito.when(
			_cdnTemplateRegistry.getCDNConfiguration(_COMPANY_ID)
		).thenReturn(
			_cdnConfiguration
		);

		Mockito.when(
			_cdnTemplateRegistry.getConfiguredCDNTemplate(
				_COMPANY_ID, _cdnConfiguration)
		).thenReturn(
			_configuredCDNTemplate
		);

		ReflectionTestUtil.setFieldValue(
			_cdnPurgeMessageListener, "_cdnHTTPPurger", _cdnHTTPPurger);
		ReflectionTestUtil.setFieldValue(
			_cdnPurgeMessageListener, "_cdnTemplateRegistry",
			_cdnTemplateRegistry);
		ReflectionTestUtil.setFieldValue(
			_cdnPurgeMessageListener, "_messageBus", _messageBus);
		ReflectionTestUtil.setFieldValue(
			_cdnPurgeMessageListener, "_scheduledExecutorService",
			_scheduledExecutorService);
	}

	@Test
	public void testReceive() throws Exception {
		_mockPurge(CDNPurgeResult.success(200));

		_cdnPurgeMessageListener.receive(_toMessage(0, "test"));

		ArgumentCaptor<CDNPurgeRequest> argumentCaptor =
			ArgumentCaptor.forClass(CDNPurgeRequest.class);

		Mockito.verify(
			_cdnHTTPPurger
		).purge(
			Mockito.eq(_configuredCDNTemplate), argumentCaptor.capture()
		);

		CDNPurgeRequest cdnPurgeRequest = argumentCaptor.getValue();

		Assert.assertEquals(
			SetUtil.fromArray("p1", "p2"), cdnPurgeRequest.getTags());

		_assertNoRetry();
	}

	@Test
	public void testReceiveWithChangedTemplate() throws Exception {
		_cdnPurgeMessageListener.receive(
			_toMessage(0, RandomTestUtil.randomString()));

		Mockito.verify(
			_cdnHTTPPurger, Mockito.never()
		).purge(
			Mockito.any(), Mockito.any()
		);
	}

	@Test
	public void testReceiveWithFailure() throws Exception {
		_mockPurge(CDNPurgeResult.failure(503, null, true));

		_cdnPurgeMessageListener.receive(_toMessage(0, "test"));

		_assertRetry(1, 1000);

		_cdnPurgeMessageListener.receive(_toMessage(2, "test"));

		_assertRetry(3, 4000);

		_cdnPurgeMessageListener.receive(_toMessage(3, "test"));

		_assertNoRetry();

		_mockPurge(CDNPurgeResult.failure(403, null, false));

		_cdnPurgeMessageListener.receive(_toMessage(0, "test"));

		_assertNoRetry();

		Mockito.when(
			_cdnHTTPPurger.purge(Mockito.any(), Mockito.any())
		).thenThrow(
			new IOException()
		);

		_cdnPurgeMessageListener.receive(_toMessage(1, "test"));

		_assertRetry(2, 2000);
	}

	private void _assertNoRetry() {
		Mockito.verify(
			_scheduledExecutorService, Mockito.never()
		).schedule(
			Mockito.any(Runnable.class), Mockito.anyLong(),
			Mockito.any(TimeUnit.class)
		);
	}

	private void _assertRetry(int attempt, long delay) {
		ArgumentCaptor<Runnable> runnableArgumentCaptor =
			ArgumentCaptor.forClass(Runnable.class);

		Mockito.verify(
			_scheduledExecutorService
		).schedule(
			runnableArgumentCaptor.capture(), Mockito.eq(delay),
			Mockito.eq(TimeUnit.MILLISECONDS)
		);

		Runnable runnable = runnableArgumentCaptor.getValue();

		runnable.run();

		ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(
			Message.class);

		Mockito.verify(
			_messageBus
		).sendMessage(
			Mockito.eq(CDNConstants.DESTINATION_NAME_CDN_PURGE),
			messageArgumentCaptor.capture()
		);

		Message message = messageArgumentCaptor.getValue();

		Assert.assertEquals(attempt, message.getInteger("attempt"));

		Mockito.clearInvocations(_messageBus, _scheduledExecutorService);
	}

	private void _mockPurge(CDNPurgeResult cdnPurgeResult) throws Exception {
		Mockito.reset(_cdnHTTPPurger);

		Mockito.when(
			_cdnHTTPPurger.purge(Mockito.any(), Mockito.any())
		).thenReturn(
			cdnPurgeResult
		);
	}

	private Message _toMessage(int attempt, String templateKey) {
		return CDNPurgeMessageUtil.toMessage(
			attempt, _COMPANY_ID,
			CDNPurgeRequest.builder(
			).tags(
				"p1", "p2"
			).build(),
			templateKey);
	}

	private static final long _COMPANY_ID = RandomTestUtil.randomLong();

	private final CDNConfiguration _cdnConfiguration = Mockito.mock(
		CDNConfiguration.class);
	private final CDNHTTPPurger _cdnHTTPPurger = Mockito.mock(
		CDNHTTPPurger.class);
	private final CDNPurgeMessageListener _cdnPurgeMessageListener =
		new CDNPurgeMessageListener();
	private final CDNTemplateRegistry _cdnTemplateRegistry = Mockito.mock(
		CDNTemplateRegistry.class);
	private final ConfiguredCDNTemplate _configuredCDNTemplate = Mockito.mock(
		ConfiguredCDNTemplate.class);
	private final MessageBus _messageBus = Mockito.mock(MessageBus.class);
	private final ScheduledExecutorService _scheduledExecutorService =
		Mockito.mock(ScheduledExecutorService.class);

}