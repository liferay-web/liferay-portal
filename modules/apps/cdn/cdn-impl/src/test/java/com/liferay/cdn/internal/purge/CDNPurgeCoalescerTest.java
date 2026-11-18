/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge;

import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.ArrayList;
import java.util.List;
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
public class CDNPurgeCoalescerTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		_cdnPurgeCoalescer = new CDNPurgeCoalescer(
			(companyId, cdnPurgeRequest) -> {
				_companyIds.add(companyId);
				_cdnPurgeRequests.add(cdnPurgeRequest);
			},
			_scheduledExecutorService);
	}

	@Test
	public void testAdd() {
		long companyId = RandomTestUtil.randomLong();

		_cdnPurgeCoalescer.add(
			companyId,
			CDNPurgeRequest.builder(
			).tags(
				"p1", "p2"
			).build(),
			2000);
		_cdnPurgeCoalescer.add(
			companyId,
			CDNPurgeRequest.builder(
			).tags(
				"p2", "p3"
			).urls(
				"https://www.liferay.com/home"
			).build(),
			2000);

		ArgumentCaptor<Runnable> argumentCaptor = ArgumentCaptor.forClass(
			Runnable.class);

		Mockito.verify(
			_scheduledExecutorService
		).schedule(
			argumentCaptor.capture(), Mockito.eq(2000L),
			Mockito.eq(TimeUnit.MILLISECONDS)
		);

		Assert.assertTrue(_cdnPurgeRequests.isEmpty());

		Runnable runnable = argumentCaptor.getValue();

		runnable.run();

		Assert.assertEquals(_companyIds.toString(), 1, _companyIds.size());
		Assert.assertEquals(Long.valueOf(companyId), _companyIds.get(0));

		CDNPurgeRequest cdnPurgeRequest = _cdnPurgeRequests.get(0);

		Assert.assertFalse(cdnPurgeRequest.isPurgeAll());
		Assert.assertTrue(cdnPurgeRequest.isSoft());
		Assert.assertEquals(
			SetUtil.fromArray("p1", "p2", "p3"), cdnPurgeRequest.getTags());
		Assert.assertEquals(
			SetUtil.fromArray("https://www.liferay.com/home"),
			cdnPurgeRequest.getURLs());

		runnable.run();

		Assert.assertEquals(
			_cdnPurgeRequests.toString(), 1, _cdnPurgeRequests.size());

		_cdnPurgeCoalescer.add(
			companyId,
			CDNPurgeRequest.builder(
			).tags(
				"p4"
			).build(),
			2000);

		Mockito.verify(
			_scheduledExecutorService, Mockito.times(2)
		).schedule(
			Mockito.any(Runnable.class), Mockito.eq(2000L),
			Mockito.eq(TimeUnit.MILLISECONDS)
		);
	}

	@Test
	public void testAddWithDifferentCompanies() {
		_cdnPurgeCoalescer.add(
			RandomTestUtil.randomLong(),
			CDNPurgeRequest.builder(
			).tags(
				RandomTestUtil.randomString()
			).build(),
			0);
		_cdnPurgeCoalescer.add(
			RandomTestUtil.randomLong(),
			CDNPurgeRequest.builder(
			).tags(
				RandomTestUtil.randomString()
			).build(),
			0);

		Mockito.verify(
			_scheduledExecutorService, Mockito.times(2)
		).schedule(
			Mockito.any(Runnable.class), Mockito.eq(0L),
			Mockito.eq(TimeUnit.MILLISECONDS)
		);
	}

	@Test
	public void testAddWithPurgeAllAndSoft() {
		long companyId = RandomTestUtil.randomLong();

		_cdnPurgeCoalescer.add(
			companyId,
			CDNPurgeRequest.builder(
			).soft(
				false
			).tags(
				"p1"
			).build(),
			100);
		_cdnPurgeCoalescer.add(
			companyId,
			CDNPurgeRequest.builder(
			).purgeAll(
				true
			).soft(
				false
			).build(),
			100);
		_cdnPurgeCoalescer.add(
			companyId,
			CDNPurgeRequest.builder(
			).tags(
				"p2"
			).build(),
			100);

		ArgumentCaptor<Runnable> argumentCaptor = ArgumentCaptor.forClass(
			Runnable.class);

		Mockito.verify(
			_scheduledExecutorService, Mockito.times(2)
		).schedule(
			argumentCaptor.capture(), Mockito.eq(100L),
			Mockito.eq(TimeUnit.MILLISECONDS)
		);

		for (Runnable runnable : argumentCaptor.getAllValues()) {
			runnable.run();
		}

		Assert.assertEquals(
			_cdnPurgeRequests.toString(), 2, _cdnPurgeRequests.size());

		CDNPurgeRequest hardCDNPurgeRequest = _cdnPurgeRequests.get(0);

		Assert.assertTrue(hardCDNPurgeRequest.isPurgeAll());
		Assert.assertFalse(hardCDNPurgeRequest.isSoft());

		CDNPurgeRequest softCDNPurgeRequest = _cdnPurgeRequests.get(1);

		Assert.assertFalse(softCDNPurgeRequest.isPurgeAll());
		Assert.assertTrue(softCDNPurgeRequest.isSoft());
		Assert.assertEquals(
			SetUtil.fromArray("p2"), softCDNPurgeRequest.getTags());
	}

	private CDNPurgeCoalescer _cdnPurgeCoalescer;
	private final List<CDNPurgeRequest> _cdnPurgeRequests = new ArrayList<>();
	private final List<Long> _companyIds = new ArrayList<>();
	private final ScheduledExecutorService _scheduledExecutorService =
		Mockito.mock(ScheduledExecutorService.class);

}