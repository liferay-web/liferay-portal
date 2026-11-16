/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.model.listener;

import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Purges web content when guests could see the article version before or
 * after the change. Approving a version, including a scheduled one, and
 * expiring, trashing, or deleting an approved version purge the web content,
 * while saving a draft does not.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ModelListener.class)
public class JournalArticleModelListener
	extends BaseModelListener<JournalArticle> {

	@Override
	public void onAfterCreate(JournalArticle journalArticle) {
		_cdnPurgeManager.purge(null, journalArticle);
	}

	@Override
	public void onAfterRemove(JournalArticle journalArticle) {
		_cdnPurgeManager.purge(journalArticle, null);
	}

	@Override
	public void onAfterUpdate(
		JournalArticle originalJournalArticle, JournalArticle journalArticle) {

		_cdnPurgeManager.purge(originalJournalArticle, journalArticle);
	}

	@Reference
	private CDNPurgeManager _cdnPurgeManager;

}