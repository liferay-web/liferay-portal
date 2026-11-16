/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.change.tracking.spi.listener;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.change.tracking.constants.CTConstants;
import com.liferay.change.tracking.model.CTCollection;
import com.liferay.change.tracking.model.CTEntry;
import com.liferay.change.tracking.service.CTCollectionLocalService;
import com.liferay.change.tracking.service.CTEntryLocalService;
import com.liferay.change.tracking.spi.listener.CTEventListener;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileVersion;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.journal.model.JournalArticle;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMapFactory;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.exception.NoSuchModelException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.service.PersistedModelLocalService;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.style.book.model.StyleBookEntry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Purges the cache tags of the models a publication publishes. Publishing
 * copies the rows of the publication into production without calling the
 * model listeners, so the published changes are read from the publication's
 * entries instead.
 *
 * <p>
 * Publishing removes the deleted models from production, so their cache tags
 * are read before the publish, together with the page changes that change the
 * navigation, which need the production page to compare with. The other cache
 * tags are read after the publish, and all of them are purged with one request
 * that waits for the publish to commit.
 * </p>
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CTEventListener.class)
public class CDNCTEventListener implements CTEventListener {

	@Override
	public void onAfterPublish(long ctCollectionId) {
		Set<String> cacheTags = _getCacheTags(ctCollectionId, false);

		Set<String> beforePublishCacheTags = _beforePublishCacheTagsMap.remove(
			ctCollectionId);

		if (beforePublishCacheTags != null) {
			cacheTags.addAll(beforePublishCacheTags);
		}

		if (cacheTags.isEmpty()) {
			return;
		}

		CTCollection ctCollection = _ctCollectionLocalService.fetchCTCollection(
			ctCollectionId);

		if (ctCollection == null) {
			return;
		}

		_cdnPurgeManager.purge(
			ctCollection.getCompanyId(),
			CDNPurgeRequest.builder(
			).tags(
				cacheTags
			).build());
	}

	/**
	 * Reads the cache tags that need production as it was before the publish
	 * and keeps them until the publish completes, so the publication is purged
	 * with one request. A failed publish leaves them behind until the
	 * publication is published again, which replaces them.
	 */
	@Override
	public void onBeforePublish(long ctCollectionId) {
		_beforePublishCacheTagsMap.put(
			ctCollectionId, _getCacheTags(ctCollectionId, true));
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_serviceTrackerMap = ServiceTrackerMapFactory.openSingleValueMap(
			bundleContext, PersistedModelLocalService.class,
			"model.class.name");
	}

	@Deactivate
	protected void deactivate() {
		_serviceTrackerMap.close();
	}

	private BaseModel<?> _fetchBaseModel(
			PersistedModelLocalService persistedModelLocalService,
			long primaryKey)
		throws Exception {

		try {
			return (BaseModel<?>)persistedModelLocalService.getPersistedModel(
				primaryKey);
		}
		catch (NoSuchModelException noSuchModelException) {
			if (_log.isDebugEnabled()) {
				_log.debug(noSuchModelException);
			}

			return null;
		}
	}

	private Set<String> _getCacheTags(
		long ctCollectionId, boolean beforePublish) {

		Set<String> cacheTags = new LinkedHashSet<>();

		CTCollection ctCollection = _ctCollectionLocalService.fetchCTCollection(
			ctCollectionId);

		if ((ctCollection == null) ||
			!_cdnPurgeManager.isEnabled(ctCollection.getCompanyId())) {

			return cacheTags;
		}

		try (SafeCloseable safeCloseable =
				CTCollectionThreadLocal.setProductionModeWithSafeCloseable()) {

			for (CTEntry ctEntry :
					_ctEntryLocalService.getCTCollectionCTEntries(
						ctCollectionId)) {

				cacheTags.addAll(
					_getCacheTags(ctCollectionId, ctEntry, beforePublish));
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to get the CDN cache tags of publication " +
					ctCollectionId,
				exception);
		}

		return cacheTags;
	}

	/**
	 * Returns the cache tags of the entry's change. Before the publish,
	 * production still has the removed models, and the page changes are
	 * compared with the production pages to find the ones that change the
	 * navigation. After the publish, production has the added and modified
	 * models.
	 */
	private Set<String> _getCacheTags(
			long ctCollectionId, CTEntry ctEntry, boolean beforePublish)
		throws Exception {

		String className = _portal.getClassName(ctEntry.getModelClassNameId());

		if (!_classNames.contains(className)) {
			return Collections.emptySet();
		}

		PersistedModelLocalService persistedModelLocalService =
			_serviceTrackerMap.getService(className);

		if (persistedModelLocalService == null) {
			return Collections.emptySet();
		}

		int changeType = ctEntry.getChangeType();

		if (beforePublish) {
			if (changeType == CTConstants.CT_CHANGE_TYPE_DELETION) {
				return _cdnCacheTagBuilder.getPurgeCacheTags(
					_fetchBaseModel(
						persistedModelLocalService, ctEntry.getModelClassPK()),
					null);
			}

			if ((changeType != CTConstants.CT_CHANGE_TYPE_MODIFICATION) ||
				!className.equals(Layout.class.getName())) {

				return Collections.emptySet();
			}

			BaseModel<?> baseModel = _fetchBaseModel(
				persistedModelLocalService, ctEntry.getModelClassPK());

			try (SafeCloseable safeCloseable =
					CTCollectionThreadLocal.setCTCollectionIdWithSafeCloseable(
						ctCollectionId)) {

				return _cdnCacheTagBuilder.getPurgeCacheTags(
					baseModel,
					_fetchBaseModel(
						persistedModelLocalService, ctEntry.getModelClassPK()));
			}
		}

		if (changeType == CTConstants.CT_CHANGE_TYPE_ADDITION) {
			return _cdnCacheTagBuilder.getPurgeCacheTags(
				null,
				_fetchBaseModel(
					persistedModelLocalService, ctEntry.getModelClassPK()));
		}

		if (changeType == CTConstants.CT_CHANGE_TYPE_MODIFICATION) {
			return _cdnCacheTagBuilder.getPurgeCacheTags(
				_fetchBaseModel(
					persistedModelLocalService, ctEntry.getModelClassPK()));
		}

		return Collections.emptySet();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CDNCTEventListener.class);

	private static final Set<String> _classNames = SetUtil.fromArray(
		BlogsEntry.class.getName(), DLFileEntry.class.getName(),
		DLFileVersion.class.getName(), FragmentEntry.class.getName(),
		FragmentEntryLink.class.getName(), JournalArticle.class.getName(),
		Layout.class.getName(), LayoutSet.class.getName(),
		SiteNavigationMenu.class.getName(),
		SiteNavigationMenuItem.class.getName(), StyleBookEntry.class.getName());

	private final Map<Long, Set<String>> _beforePublishCacheTagsMap =
		new ConcurrentHashMap<>();

	@Reference
	private CDNCacheTagBuilder _cdnCacheTagBuilder;

	@Reference
	private CDNPurgeManager _cdnPurgeManager;

	@Reference
	private CTCollectionLocalService _ctCollectionLocalService;

	@Reference
	private CTEntryLocalService _ctEntryLocalService;

	@Reference
	private Portal _portal;

	private ServiceTrackerMap<String, PersistedModelLocalService>
		_serviceTrackerMap;

}