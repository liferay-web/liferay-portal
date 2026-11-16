/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileVersion;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.journal.model.JournalArticle;
import com.liferay.layout.page.template.constants.LayoutPageTemplateEntryTypeConstants;
import com.liferay.layout.page.template.model.LayoutPageTemplateEntry;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMapFactory;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.model.WorkflowedModel;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.style.book.model.StyleBookEntry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
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
			companyId, StringBundler.concat("l", groupId, "-", classNameId));
	}

	@Override
	public String getCompanyCacheTag(long companyId) {
		return _getCacheTag(companyId, "c" + companyId);
	}

	@Override
	public String getGroupCacheTag(long companyId, long groupId) {
		return _getCacheTag(companyId, "s" + groupId);
	}

	@Override
	public Set<String> getLayoutCacheTags(Layout layout) {
		Set<String> cacheTags = new LinkedHashSet<>();

		cacheTags.add(
			_getCacheTag(layout.getCompanyId(), "p" + layout.getPlid()));

		if (layout.getMasterLayoutPlid() > 0) {
			cacheTags.add(
				_getCacheTag(
					layout.getCompanyId(), "p" + layout.getMasterLayoutPlid()));
		}

		return cacheTags;
	}

	@Override
	public String getModelCacheTag(
		long companyId, String className, long classPK) {

		CDNCacheTagModelResolver cdnCacheTagModelResolver =
			_serviceTrackerMap.getService(className);

		if (cdnCacheTagModelResolver == null) {
			return null;
		}

		String cacheTag = cdnCacheTagModelResolver.getCacheTag(classPK);

		if (cacheTag == null) {
			return null;
		}

		return _getCacheTag(companyId, cacheTag);
	}

	@Override
	public String getOverflowCacheTag(long companyId, long groupId) {
		return _getCacheTag(companyId, "x" + groupId);
	}

	@Override
	public Set<String> getPurgeCacheTags(BaseModel<?> baseModel) {
		if (baseModel instanceof WorkflowedModel) {
			WorkflowedModel workflowedModel = (WorkflowedModel)baseModel;

			Set<String> cacheTags = _getContentPurgeCacheTags(baseModel);

			if (cacheTags != null) {
				if (!_isVisibleStatus(workflowedModel.getStatus())) {
					return Collections.emptySet();
				}

				return cacheTags;
			}
		}

		if (baseModel instanceof DLFileEntry) {
			return _getContentPurgeCacheTags(baseModel);
		}

		if (baseModel instanceof FragmentEntry) {
			FragmentEntry fragmentEntry = (FragmentEntry)baseModel;

			if (!fragmentEntry.isHead()) {
				return Collections.emptySet();
			}

			return _getPurgeCacheTags(
				fragmentEntry.getCompanyId(), fragmentEntry.getGroupId(),
				getModelCacheTag(
					fragmentEntry.getCompanyId(), FragmentEntry.class.getName(),
					fragmentEntry.getFragmentEntryId()));
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

	@Override
	public Set<String> getPurgeCacheTags(
		BaseModel<?> originalBaseModel, BaseModel<?> baseModel) {

		BaseModel<?> currentBaseModel = baseModel;

		if (currentBaseModel == null) {
			currentBaseModel = originalBaseModel;
		}

		if (currentBaseModel == null) {
			return Collections.emptySet();
		}

		if (currentBaseModel instanceof Layout) {
			return _getPurgeCacheTags(
				(Layout)originalBaseModel, (Layout)baseModel);
		}

		if (currentBaseModel instanceof WorkflowedModel) {
			Set<String> cacheTags = _getContentPurgeCacheTags(currentBaseModel);

			if (cacheTags != null) {
				if (!_isApproved(originalBaseModel) &&
					!_isApproved(baseModel)) {

					return Collections.emptySet();
				}

				return cacheTags;
			}
		}

		return getPurgeCacheTags(currentBaseModel);
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

	private String _getCacheTag(long companyId, String cacheTag) {
		String cacheTagNamespace = _CACHE_TAG_NAMESPACE_DEFAULT;

		try {
			CDNConfiguration cdnConfiguration =
				_configurationProvider.getCompanyConfiguration(
					CDNConfiguration.class, companyId);

			cacheTagNamespace = GetterUtil.getString(
				cdnConfiguration.cacheTagNamespace());
		}
		catch (ConfigurationException configurationException) {
			_log.error(
				"Unable to get the CDN configuration for company " + companyId,
				configurationException);
		}

		return cacheTagNamespace + cacheTag;
	}

	/**
	 * Returns the cache tags of a content model, whatever its status, or
	 * <code>null</code> when the model is not a content model. Besides the
	 * model's own cache tag, the collections of the model's type in its group
	 * show the model, and so does any page whose cache tags overflowed.
	 */
	private Set<String> _getContentPurgeCacheTags(BaseModel<?> baseModel) {
		if (baseModel instanceof BlogsEntry) {
			BlogsEntry blogsEntry = (BlogsEntry)baseModel;

			return _getContentPurgeCacheTags(
				blogsEntry.getCompanyId(), blogsEntry.getGroupId(),
				BlogsEntry.class.getName(), blogsEntry.getEntryId(),
				BlogsEntry.class.getName());
		}

		// Asset publishers list documents by the file entry model, while info
		// collections list them by the repository file entry

		if (baseModel instanceof DLFileEntry) {
			DLFileEntry dlFileEntry = (DLFileEntry)baseModel;

			return _getContentPurgeCacheTags(
				dlFileEntry.getCompanyId(), dlFileEntry.getGroupId(),
				DLFileEntry.class.getName(), dlFileEntry.getFileEntryId(),
				DLFileEntry.class.getName(), FileEntry.class.getName());
		}

		if (baseModel instanceof DLFileVersion) {
			DLFileVersion dlFileVersion = (DLFileVersion)baseModel;

			return _getContentPurgeCacheTags(
				dlFileVersion.getCompanyId(), dlFileVersion.getGroupId(),
				DLFileEntry.class.getName(), dlFileVersion.getFileEntryId(),
				DLFileEntry.class.getName(), FileEntry.class.getName());
		}

		if (baseModel instanceof JournalArticle) {
			JournalArticle journalArticle = (JournalArticle)baseModel;

			return _getContentPurgeCacheTags(
				journalArticle.getCompanyId(), journalArticle.getGroupId(),
				JournalArticle.class.getName(),
				journalArticle.getResourcePrimKey(),
				JournalArticle.class.getName());
		}

		if (baseModel instanceof ObjectEntry) {
			ObjectEntry objectEntry = (ObjectEntry)baseModel;

			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.fetchObjectDefinition(
					objectEntry.getObjectDefinitionId());

			if ((objectDefinition == null) ||
				objectDefinition.isUnmodifiableSystemObject()) {

				return Collections.emptySet();
			}

			// Entries of company scoped object definitions belong to group 0,
			// which is also the group of their collection cache tag

			return _getContentPurgeCacheTags(
				objectEntry.getCompanyId(), objectEntry.getGroupId(),
				objectDefinition.getClassName(), objectEntry.getObjectEntryId(),
				objectDefinition.getClassName());
		}

		return null;
	}

	private Set<String> _getContentPurgeCacheTags(
		long companyId, long groupId, String className, long classPK,
		String... collectionClassNames) {

		String cacheTag = getModelCacheTag(companyId, className, classPK);

		if (cacheTag == null) {
			return Collections.emptySet();
		}

		Set<String> cacheTags = new LinkedHashSet<>();

		cacheTags.add(cacheTag);

		for (String collectionClassName : collectionClassNames) {
			cacheTags.add(
				getCollectionCacheTag(
					companyId, groupId,
					_portal.getClassNameId(collectionClassName)));
		}

		cacheTags.add(getOverflowCacheTag(companyId, groupId));

		return cacheTags;
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
			layout.getCompanyId(), layout.getGroupId(),
			_getCacheTag(layout.getCompanyId(), "p" + layout.getPlid()));
	}

	/**
	 * Returns the cache tags to purge when a page changes from the original
	 * page to the page. An added page has nothing cached yet, so it only
	 * purges its site when it appears in the navigation.
	 */
	private Set<String> _getPurgeCacheTags(
		Layout originalLayout, Layout layout) {

		Set<String> cacheTags = new LinkedHashSet<>();

		if (layout == null) {
			cacheTags.addAll(_getPurgeCacheTags(originalLayout));
		}
		else if (originalLayout != null) {
			cacheTags.addAll(_getPurgeCacheTags(layout));
		}

		if (_isNavigationChange(originalLayout, layout)) {
			Layout currentLayout = layout;

			if (currentLayout == null) {
				currentLayout = originalLayout;
			}

			cacheTags.add(
				getGroupCacheTag(
					currentLayout.getCompanyId(), currentLayout.getGroupId()));
		}

		return cacheTags;
	}

	private Set<String> _getPurgeCacheTags(
		long companyId, long groupId, String cacheTag) {

		if (cacheTag == null) {
			return Collections.emptySet();
		}

		Set<String> cacheTags = new LinkedHashSet<>();

		cacheTags.add(cacheTag);
		cacheTags.add(getOverflowCacheTag(companyId, groupId));

		return cacheTags;
	}

	private boolean _isApproved(BaseModel<?> baseModel) {
		if (baseModel == null) {
			return false;
		}

		WorkflowedModel workflowedModel = (WorkflowedModel)baseModel;

		return workflowedModel.isApproved();
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

	/**
	 * Returns whether the change affects the navigation that every page of the
	 * site renders: a page appears in or disappears from the navigation, or a
	 * page in the navigation is moved, reordered, renamed, or gets a new
	 * friendly URL.
	 */
	private boolean _isNavigationChange(Layout originalLayout, Layout layout) {
		boolean originalNavigationLayout = _isNavigationLayout(originalLayout);
		boolean navigationLayout = _isNavigationLayout(layout);

		if (!originalNavigationLayout && !navigationLayout) {
			return false;
		}

		if ((originalNavigationLayout != navigationLayout) ||
			(originalLayout.getParentLayoutId() !=
				layout.getParentLayoutId()) ||
			(originalLayout.getPriority() != layout.getPriority()) ||
			!Objects.equals(
				originalLayout.getFriendlyURL(), layout.getFriendlyURL()) ||
			!Objects.equals(originalLayout.getName(), layout.getName())) {

			return true;
		}

		return false;
	}

	private boolean _isNavigationLayout(Layout layout) {
		if ((layout == null) || layout.isDraftLayout() || layout.isHidden() ||
			layout.isPrivateLayout() || layout.isSystem()) {

			return false;
		}

		return true;
	}

	/**
	 * Returns whether guests see, or saw until the change, a model with the
	 * status. Approved models are visible, and expired and trashed models were
	 * visible until they expired or were trashed.
	 */
	private boolean _isVisibleStatus(int status) {
		if ((status == WorkflowConstants.STATUS_APPROVED) ||
			(status == WorkflowConstants.STATUS_EXPIRED) ||
			(status == WorkflowConstants.STATUS_IN_TRASH)) {

			return true;
		}

		return false;
	}

	private static final String _CACHE_TAG_NAMESPACE_DEFAULT = "lfr-";

	private static final Log _log = LogFactoryUtil.getLog(
		CDNCacheTagBuilderImpl.class);

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private LayoutPageTemplateEntryLocalService
		_layoutPageTemplateEntryLocalService;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private Portal _portal;

	private ServiceTrackerMap<String, CDNCacheTagModelResolver>
		_serviceTrackerMap;

}