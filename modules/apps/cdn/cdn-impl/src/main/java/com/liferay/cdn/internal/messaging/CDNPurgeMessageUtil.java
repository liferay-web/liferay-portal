/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.messaging;

import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.portal.kernel.messaging.Message;

import java.util.Set;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeMessageUtil {

	public static CDNPurgeRequest toCDNPurgeRequest(Message message) {
		return CDNPurgeRequest.builder(
		).purgeAll(
			message.getBoolean("purgeAll")
		).soft(
			message.getBoolean("soft")
		).tags(
			(String[])message.get("tags")
		).urls(
			(String[])message.get("urls")
		).build();
	}

	public static Message toMessage(
		int attempt, long companyId, CDNPurgeRequest cdnPurgeRequest,
		String templateKey) {

		Message message = new Message();

		message.put("attempt", attempt);
		message.put("companyId", companyId);
		message.put("purgeAll", cdnPurgeRequest.isPurgeAll());
		message.put("soft", cdnPurgeRequest.isSoft());

		Set<String> tags = cdnPurgeRequest.getTags();

		message.put("tags", tags.toArray(new String[0]));

		message.put("templateKey", templateKey);

		Set<String> urls = cdnPurgeRequest.getURLs();

		message.put("urls", urls.toArray(new String[0]));

		return message;
	}

}