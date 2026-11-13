/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.purge;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Purges content from the CDN configured for a company.
 *
 * @author Gianmarco Brunialti Masera
 */
@ProviderType
public interface CDNPurgeManager {

	public boolean isEnabled(long companyId);

	/**
	 * Requests a purge without blocking the caller. The purge is sent after
	 * the current transaction commits, or right away when there is no
	 * transaction, and it is discarded if the transaction rolls back. Requests
	 * received within the configured coalesce window are merged into one.
	 * This method never throws: failures are logged and retried
	 * asynchronously.
	 */
	public void purge(long companyId, CDNPurgeRequest cdnPurgeRequest);

}