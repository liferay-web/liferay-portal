/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.change.tracking.spi.listener;

import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.change.tracking.constants.CTConstants;
import com.liferay.change.tracking.model.CTCollection;
import com.liferay.change.tracking.model.CTEntry;
import com.liferay.change.tracking.service.CTCollectionLocalService;
import com.liferay.change.tracking.service.CTEntryLocalService;
import com.liferay.change.tracking.spi.listener.CTEventListener;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
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
import java.util.Set;

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
 * are read before the publish and the other cache tags after it. Both run in
 * the publish transaction, so every purge waits for the publish to commit and
 * is discarded if the publish fails.
 * </p>
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CTEventListener.class)
public class CDNCTEventListener implements CTEventListener {

	@Override
	public void onAfterPublish(long ctCollectionId) {
		_purge(ctCollectionId, false);
	}

	@Override
	public void onBeforePublish(long ctCollectionId) {
		_purge(ctCollectionId, true);
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

	private Set<String> _getCacheTags(CTEntry ctEntry) throws Exception {
		String className = _portal.getClassName(ctEntry.getModelClassNameId());

		if (!_classNames.contains(className)) {
			return Collections.emptySet();
		}

		PersistedModelLocalService persistedModelLocalService =
			_serviceTrackerMap.getService(className);

		if (persistedModelLocalService == null) {
			return Collections.emptySet();
		}

		try {
			return _cdnCacheTagBuilder.getPurgeCacheTags(
				(BaseModel<?>)persistedModelLocalService.getPersistedModel(
					ctEntry.getModelClassPK()));
		}
		catch (NoSuchModelException noSuchModelException) {
			if (_log.isDebugEnabled()) {
				_log.debug(noSuchModelException);
			}

			return Collections.emptySet();
		}
	}

	private void _purge(long ctCollectionId, boolean deletion) {
		CTCollection ctCollection = _ctCollectionLocalService.fetchCTCollection(
			ctCollectionId);

		if ((ctCollection == null) ||
			!_cdnPurgeManager.isEnabled(ctCollection.getCompanyId())) {

			return;
		}

		Set<String> cacheTags = new LinkedHashSet<>();

		try (SafeCloseable safeCloseable =
				CTCollectionThreadLocal.setProductionModeWithSafeCloseable()) {

			for (CTEntry ctEntry :
					_ctEntryLocalService.getCTCollectionCTEntries(
						ctCollectionId)) {

				if (deletion !=
						(ctEntry.getChangeType() ==
							CTConstants.CT_CHANGE_TYPE_DELETION)) {

					continue;
				}

				cacheTags.addAll(_getCacheTags(ctEntry));
			}

			if (cacheTags.isEmpty()) {
				return;
			}

			_cdnPurgeManager.purge(
				ctCollection.getCompanyId(),
				CDNPurgeRequest.builder(
				).tags(
					cacheTags
				).build());
		}
		catch (Exception exception) {
			_log.error(
				"Unable to purge the CDN cache tags of publication " +
					ctCollectionId,
				exception);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CDNCTEventListener.class);

	private static final Set<String> _classNames = SetUtil.fromArray(
		FragmentEntry.class.getName(), FragmentEntryLink.class.getName(),
		Layout.class.getName(), LayoutSet.class.getName(),
		SiteNavigationMenu.class.getName(),
		SiteNavigationMenuItem.class.getName(), StyleBookEntry.class.getName());

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