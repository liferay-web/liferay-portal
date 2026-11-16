/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.model.listener;

import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.object.model.ObjectEntry;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ModelListener.class)
public class ObjectEntryModelListener extends BaseModelListener<ObjectEntry> {

	@Override
	public void onAfterCreate(ObjectEntry objectEntry) {
		_cdnPurgeManager.purge(null, objectEntry);
	}

	@Override
	public void onAfterRemove(ObjectEntry objectEntry) {
		_cdnPurgeManager.purge(objectEntry, null);
	}

	@Override
	public void onAfterUpdate(
		ObjectEntry originalObjectEntry, ObjectEntry objectEntry) {

		_cdnPurgeManager.purge(originalObjectEntry, objectEntry);
	}

	@Reference
	private CDNPurgeManager _cdnPurgeManager;

}