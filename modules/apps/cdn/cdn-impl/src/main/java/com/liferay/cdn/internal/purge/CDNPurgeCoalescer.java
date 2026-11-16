/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge;

import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Merges the purge requests a company receives within a time window into one
 * request. A single publication rewrites many rows, and each row change asks
 * for a purge of the same content, so sending them one by one floods the CDN
 * API with duplicates. Hard and soft requests are merged separately because
 * they cannot share one CDN call.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeCoalescer {

	public CDNPurgeCoalescer(
		BiConsumer<Long, CDNPurgeRequest> biConsumer,
		ScheduledExecutorService scheduledExecutorService) {

		_biConsumer = biConsumer;
		_scheduledExecutorService = scheduledExecutorService;
	}

	public void add(
		long companyId, CDNPurgeRequest cdnPurgeRequest, long windowMs) {

		Map<Long, CDNPurgeRequest.Builder> builders = _hardBuilders;

		if (cdnPurgeRequest.isSoft()) {
			builders = _softBuilders;
		}

		synchronized (this) {
			CDNPurgeRequest.Builder builder = builders.get(companyId);

			if (builder == null) {
				builder = CDNPurgeRequest.builder(
				).soft(
					cdnPurgeRequest.isSoft()
				);

				builders.put(companyId, builder);

				Map<Long, CDNPurgeRequest.Builder> scheduledBuilders = builders;

				_scheduledExecutorService.schedule(
					() -> _flush(companyId, scheduledBuilders),
					Math.max(0, windowMs), TimeUnit.MILLISECONDS);
			}

			if (cdnPurgeRequest.isPurgeAll()) {
				builder.purgeAll(true);
			}

			builder.tags(
				cdnPurgeRequest.getTags()
			).urls(
				cdnPurgeRequest.getURLs()
			);
		}
	}

	private void _flush(
		long companyId, Map<Long, CDNPurgeRequest.Builder> builders) {

		CDNPurgeRequest.Builder builder = null;

		synchronized (this) {
			builder = builders.remove(companyId);
		}

		if (builder == null) {
			return;
		}

		try {
			_biConsumer.accept(companyId, builder.build());
		}
		catch (Exception exception) {
			_log.error(
				"Unable to dispatch the CDN purge for company " + companyId,
				exception);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CDNPurgeCoalescer.class);

	private final BiConsumer<Long, CDNPurgeRequest> _biConsumer;
	private final Map<Long, CDNPurgeRequest.Builder> _hardBuilders =
		new HashMap<>();
	private final ScheduledExecutorService _scheduledExecutorService;
	private final Map<Long, CDNPurgeRequest.Builder> _softBuilders =
		new HashMap<>();

}