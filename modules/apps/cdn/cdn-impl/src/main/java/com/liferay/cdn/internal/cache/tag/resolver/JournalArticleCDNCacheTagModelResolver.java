/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag.resolver;

import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleResource;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalArticleResourceLocalService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Tags web content by its resource primary key, which every version of the
 * article shares. Asset entries, info items, and display pages identify web
 * content by its resource primary key, while publications identify a version
 * by its primary key, so both are accepted. Resource primary keys and article
 * primary keys come from the same counter, so they never collide.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNCacheTagModelResolver.class)
public class JournalArticleCDNCacheTagModelResolver
	implements CDNCacheTagModelResolver {

	@Override
	public String getCacheTag(long classPK) {
		return "wc" + _getResourcePrimKey(classPK);
	}

	@Override
	public String getClassName() {
		return JournalArticle.class.getName();
	}

	private long _getResourcePrimKey(long classPK) {
		JournalArticleResource journalArticleResource =
			_journalArticleResourceLocalService.fetchJournalArticleResource(
				classPK);

		if (journalArticleResource != null) {
			return classPK;
		}

		JournalArticle journalArticle =
			_journalArticleLocalService.fetchJournalArticle(classPK);

		if (journalArticle != null) {
			return journalArticle.getResourcePrimKey();
		}

		return classPK;
	}

	@Reference
	private JournalArticleLocalService _journalArticleLocalService;

	@Reference
	private JournalArticleResourceLocalService
		_journalArticleResourceLocalService;

}