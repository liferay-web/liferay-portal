/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag;

import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.layout.page.template.constants.LayoutPageTemplateEntryTypeConstants;
import com.liferay.layout.page.template.model.LayoutPageTemplateEntry;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalService;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMapFactory;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.style.book.model.StyleBookEntry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Builds cache tags from primary keys rather than external reference codes.
 * Primary keys never change and are short, while an external reference code
 * can be renamed through the headless APIs, which would leave the cached
 * objects tagged with the old value. Web content is tagged by its resource
 * primary key, which is stable across versions.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNCacheTagBuilder.class)
public class CDNCacheTagBuilderImpl implements CDNCacheTagBuilder {

	@Override
	public String getCollectionCacheTag(
		long companyId, long groupId, long classNameId) {

		return _getCacheTag(
			StringBundler.concat("l", groupId, "-", classNameId));
	}

	@Override
	public String getCompanyCacheTag(long companyId) {
		return _getCacheTag("c" + companyId);
	}

	@Override
	public String getGroupCacheTag(long companyId, long groupId) {
		return _getCacheTag("s" + groupId);
	}

	@Override
	public Set<String> getLayoutCacheTags(Layout layout) {
		Set<String> cacheTags = new LinkedHashSet<>();

		cacheTags.add(_getCacheTag("p" + layout.getPlid()));

		if (layout.getMasterLayoutPlid() > 0) {
			cacheTags.add(_getCacheTag("p" + layout.getMasterLayoutPlid()));
		}

		return cacheTags;
	}

	@Override
	public String getModelCacheTag(
		long companyId, String className, long classPK) {

		String cacheTag = null;

		CDNCacheTagModelResolver cdnCacheTagModelResolver =
			_serviceTrackerMap.getService(className);

		if (cdnCacheTagModelResolver != null) {
			cacheTag = cdnCacheTagModelResolver.getCacheTag(classPK);
		}
		else {
			cacheTag = _getModelCacheTag(className, classPK);
		}

		if (cacheTag == null) {
			return null;
		}

		return _getCacheTag(cacheTag);
	}

	@Override
	public String getOverflowCacheTag(long companyId, long groupId) {
		return _getCacheTag("x" + groupId);
	}

	@Override
	public Set<String> getPurgeCacheTags(BaseModel<?> baseModel) {
		if (baseModel instanceof FragmentEntry) {
			FragmentEntry fragmentEntry = (FragmentEntry)baseModel;

			if (!fragmentEntry.isHead()) {
				return Collections.emptySet();
			}

			return _getPurgeCacheTags(
				fragmentEntry.getCompanyId(), fragmentEntry.getGroupId(),
				FragmentEntry.class.getName(),
				fragmentEntry.getFragmentEntryId());
		}

		if (baseModel instanceof FragmentEntryLink) {
			FragmentEntryLink fragmentEntryLink = (FragmentEntryLink)baseModel;

			return _getPurgeCacheTags(
				_layoutLocalService.fetchLayout(fragmentEntryLink.getPlid()));
		}

		if (baseModel instanceof Layout) {
			return _getPurgeCacheTags((Layout)baseModel);
		}

		if (baseModel instanceof LayoutSet) {
			LayoutSet layoutSet = (LayoutSet)baseModel;

			if (layoutSet.isPrivateLayout()) {
				return Collections.emptySet();
			}

			return Collections.singleton(
				getGroupCacheTag(
					layoutSet.getCompanyId(), layoutSet.getGroupId()));
		}

		if (baseModel instanceof SiteNavigationMenu) {
			SiteNavigationMenu siteNavigationMenu =
				(SiteNavigationMenu)baseModel;

			return Collections.singleton(
				getGroupCacheTag(
					siteNavigationMenu.getCompanyId(),
					siteNavigationMenu.getGroupId()));
		}

		if (baseModel instanceof SiteNavigationMenuItem) {
			SiteNavigationMenuItem siteNavigationMenuItem =
				(SiteNavigationMenuItem)baseModel;

			return Collections.singleton(
				getGroupCacheTag(
					siteNavigationMenuItem.getCompanyId(),
					siteNavigationMenuItem.getGroupId()));
		}

		if (baseModel instanceof StyleBookEntry) {
			StyleBookEntry styleBookEntry = (StyleBookEntry)baseModel;

			if (!styleBookEntry.isHead()) {
				return Collections.emptySet();
			}

			return Collections.singleton(
				getGroupCacheTag(
					styleBookEntry.getCompanyId(),
					styleBookEntry.getGroupId()));
		}

		return Collections.emptySet();
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_serviceTrackerMap = ServiceTrackerMapFactory.openSingleValueMap(
			bundleContext, CDNCacheTagModelResolver.class, null,
			(serviceReference, emitter) -> {
				CDNCacheTagModelResolver cdnCacheTagModelResolver =
					bundleContext.getService(serviceReference);

				try {
					emitter.emit(cdnCacheTagModelResolver.getClassName());
				}
				finally {
					bundleContext.ungetService(serviceReference);
				}
			});
	}

	@Deactivate
	protected void deactivate() {
		_serviceTrackerMap.close();
	}

	private String _getCacheTag(String cacheTag) {
		return _CACHE_TAG_NAMESPACE + cacheTag;
	}

	private String _getModelCacheTag(String className, long classPK) {
		String prefix = _prefixes.get(className);

		if ((prefix == null) &&
			className.startsWith(_CLASS_NAME_PREFIX_OBJECT)) {

			prefix = "o";
		}

		if (prefix == null) {
			return null;
		}

		return prefix + classPK;
	}

	private Set<String> _getPurgeCacheTags(Layout layout) {
		if ((layout == null) || layout.isDraftLayout()) {
			return Collections.emptySet();
		}

		if ((layout.isPrivateLayout() || layout.isSystem()) &&
			!_isMasterLayout(layout)) {

			return Collections.emptySet();
		}

		return _getPurgeCacheTags(
			layout.getCompanyId(), layout.getGroupId(), Layout.class.getName(),
			layout.getPlid());
	}

	private Set<String> _getPurgeCacheTags(
		long companyId, long groupId, String className, long classPK) {

		Set<String> cacheTags = new LinkedHashSet<>();

		cacheTags.add(getModelCacheTag(companyId, className, classPK));
		cacheTags.add(getOverflowCacheTag(companyId, groupId));

		return cacheTags;
	}

	private boolean _isMasterLayout(Layout layout) {
		LayoutPageTemplateEntry layoutPageTemplateEntry =
			_layoutPageTemplateEntryLocalService.
				fetchLayoutPageTemplateEntryByPlid(layout.getPlid());

		if ((layoutPageTemplateEntry != null) &&
			(layoutPageTemplateEntry.getType() ==
				LayoutPageTemplateEntryTypeConstants.MASTER_LAYOUT)) {

			return true;
		}

		return false;
	}

	private static final String _CACHE_TAG_NAMESPACE = "lfr-";

	private static final String _CLASS_NAME_PREFIX_OBJECT =
		"com.liferay.object.model.ObjectDefinition#";

	private static final Map<String, String> _prefixes = HashMapBuilder.put(
		"com.liferay.blogs.model.BlogsEntry", "b"
	).put(
		"com.liferay.document.library.kernel.model.DLFileEntry", "d"
	).put(
		"com.liferay.fragment.model.FragmentEntry", "f"
	).put(
		"com.liferay.journal.model.JournalArticle", "wc"
	).put(
		"com.liferay.portal.kernel.model.Layout", "p"
	).build();

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private LayoutPageTemplateEntryLocalService
		_layoutPageTemplateEntryLocalService;

	private ServiceTrackerMap<String, CDNCacheTagModelResolver>
		_serviceTrackerMap;

}