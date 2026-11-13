/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge;

import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.template.CDNTemplate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Splits a purge request into the requests a CDN accepts in one call.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeBatchUtil {

	public static List<CDNPurgeRequest> getCDNPurgeRequests(
		CDNPurgeRequest cdnPurgeRequest, CDNTemplate cdnTemplate) {

		if (cdnPurgeRequest.isPurgeAll()) {
			if (cdnTemplate.getPurgeAllCDNHTTPRequestTemplate() != null) {
				return Collections.singletonList(
					CDNPurgeRequest.builder(
					).purgeAll(
						true
					).soft(
						cdnPurgeRequest.isSoft()
					).build());
			}

			if (_log.isWarnEnabled()) {
				_log.warn(
					"The CDN template \"" + cdnTemplate.getKey() +
						"\" does not support purging all");
			}

			return Collections.emptyList();
		}

		List<CDNPurgeRequest> cdnPurgeRequests = new ArrayList<>();

		Set<String> tags = cdnPurgeRequest.getTags();

		if (cdnTemplate.getTagPurgeCDNHTTPRequestTemplate() != null) {
			_addCDNPurgeRequests(
				cdnPurgeRequests, cdnTemplate.getMaxTagsPerRequest(),
				cdnPurgeRequest.isSoft(), tags,
				(builder, batchTags) -> builder.tags(batchTags));
		}
		else if (!tags.isEmpty() && _log.isWarnEnabled()) {
			_log.warn(
				StringBundler.concat(
					"The CDN template \"", cdnTemplate.getKey(),
					"\" does not support purging by tag. Skipped tags: ",
					tags));
		}

		Set<String> urls = cdnPurgeRequest.getURLs();

		if (cdnTemplate.getURLPurgeCDNHTTPRequestTemplate() != null) {
			_addCDNPurgeRequests(
				cdnPurgeRequests, cdnTemplate.getMaxURLsPerRequest(),
				cdnPurgeRequest.isSoft(), urls,
				(builder, batchURLs) -> builder.urls(batchURLs));
		}
		else if (!urls.isEmpty() && _log.isWarnEnabled()) {
			_log.warn(
				StringBundler.concat(
					"The CDN template \"", cdnTemplate.getKey(),
					"\" does not support purging by URL. Skipped URLs: ",
					urls));
		}

		return cdnPurgeRequests;
	}

	private static void _addCDNPurgeRequests(
		List<CDNPurgeRequest> cdnPurgeRequests, int maxSize, boolean soft,
		Set<String> values,
		BiConsumer<CDNPurgeRequest.Builder, List<String>> biConsumer) {

		List<String> batchValues = new ArrayList<>();

		for (String value : values) {
			batchValues.add(value);

			if (batchValues.size() < maxSize) {
				continue;
			}

			cdnPurgeRequests.add(_build(batchValues, biConsumer, soft));

			batchValues = new ArrayList<>();
		}

		if (!batchValues.isEmpty()) {
			cdnPurgeRequests.add(_build(batchValues, biConsumer, soft));
		}
	}

	private static CDNPurgeRequest _build(
		List<String> batchValues,
		BiConsumer<CDNPurgeRequest.Builder, List<String>> biConsumer,
		boolean soft) {

		CDNPurgeRequest.Builder builder = CDNPurgeRequest.builder(
		).soft(
			soft
		);

		biConsumer.accept(builder, batchValues);

		return builder.build();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CDNPurgeBatchUtil.class);

}