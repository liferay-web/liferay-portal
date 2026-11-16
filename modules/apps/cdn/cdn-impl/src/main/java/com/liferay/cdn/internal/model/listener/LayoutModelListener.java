/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.model.listener;

import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.ModelListener;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ModelListener.class)
public class LayoutModelListener extends BaseModelListener<Layout> {

	@Override
	public void onAfterRemove(Layout layout) {
		_cdnPurgeManager.purge(layout);
	}

	@Override
	public void onAfterUpdate(Layout originalLayout, Layout layout) {
		_cdnPurgeManager.purge(layout);
	}

	@Reference
	private CDNPurgeManager _cdnPurgeManager;

}