/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.model.listener;

import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.document.library.kernel.model.DLFileVersion;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Purges a document when one of its versions is approved, expired, or moved
 * to the trash, because the document itself does not change then.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ModelListener.class)
public class DLFileVersionModelListener
	extends BaseModelListener<DLFileVersion> {

	@Override
	public void onAfterCreate(DLFileVersion dlFileVersion) {
		_cdnPurgeManager.purge(null, dlFileVersion);
	}

	@Override
	public void onAfterRemove(DLFileVersion dlFileVersion) {
		_cdnPurgeManager.purge(dlFileVersion, null);
	}

	@Override
	public void onAfterUpdate(
		DLFileVersion originalDLFileVersion, DLFileVersion dlFileVersion) {

		_cdnPurgeManager.purge(originalDLFileVersion, dlFileVersion);
	}

	@Reference
	private CDNPurgeManager _cdnPurgeManager;

}