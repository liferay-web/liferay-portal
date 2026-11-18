/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.model.listener;

import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ModelListener.class)
public class FragmentEntryLinkModelListener
	extends BaseModelListener<FragmentEntryLink> {

	@Override
	public void onAfterCreate(FragmentEntryLink fragmentEntryLink) {
		_cdnPurgeManager.purge(fragmentEntryLink);
	}

	@Override
	public void onAfterRemove(FragmentEntryLink fragmentEntryLink) {
		_cdnPurgeManager.purge(fragmentEntryLink);
	}

	@Override
	public void onAfterUpdate(
		FragmentEntryLink originalFragmentEntryLink,
		FragmentEntryLink fragmentEntryLink) {

		_cdnPurgeManager.purge(fragmentEntryLink);
	}

	@Reference
	private CDNPurgeManager _cdnPurgeManager;

}