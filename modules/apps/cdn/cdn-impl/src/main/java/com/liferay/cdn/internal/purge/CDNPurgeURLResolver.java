/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge;

import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.info.item.InfoItemReference;
import com.liferay.journal.model.JournalArticle;
import com.liferay.layout.display.page.LayoutDisplayPageObjectProvider;
import com.liferay.layout.display.page.LayoutDisplayPageProvider;
import com.liferay.layout.display.page.LayoutDisplayPageProviderRegistry;
import com.liferay.layout.model.LayoutClassedModelUsage;
import com.liferay.layout.service.LayoutClassedModelUsageLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.LayoutSetLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts cache tags to the URLs of the public pages that carry them, for
 * CDNs that purge by URL but not by cache tag. The conversion is coarser than
 * a tag purge and is bounded, because a site or a company tag stands for every
 * public page of the site or the company.
 *
 * <ul>
 * <li>
 * A page tag purges the page's URLs. A master page's tag purges the URLs of
 * every page of its site.
 * </li>
 * <li>
 * A content tag purges the URLs of the pages that use the content and the
 * URLs of the content's display page. Content types without a known tag prefix
 * are covered by the overflow tag that every content purge carries.
 * </li>
 * <li>
 * Site and collection tags purge the URLs of every public page of the site.
 * Group 0 holds company scoped object entries, so it stands for the whole
 * company.
 * </li>
 * <li>
 * Overflow tags are skipped. A page only carries one when its cache tags do
 * not fit in the header, which does not limit a conversion based on the
 * content's usages. Every page and content purge carries one, so converting
 * it would purge the whole site on every change.
 * </li>
 * <li>
 * The company tag, and group 0 when the CDN can purge everything, purge
 * everything.
 * </li>
 * </ul>
 *
 * <p>
 * Every page is purged under each URL a guest can reach it with: through the
 * site's virtual hosts, or through the company's virtual host and the site's
 * <code>/web</code> path, with and without each language prefix, with and
 * without a trailing slash, and at the site root for the site's home page.
 * </p>
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeURLResolver {

	public CDNPurgeURLResolver(
		CompanyLocalService companyLocalService,
		FragmentEntryLinkLocalService fragmentEntryLinkLocalService,
		FragmentEntryLocalService fragmentEntryLocalService,
		GroupLocalService groupLocalService, Language language,
		LayoutClassedModelUsageLocalService layoutClassedModelUsageLocalService,
		LayoutDisplayPageProviderRegistry layoutDisplayPageProviderRegistry,
		LayoutLocalService layoutLocalService,
		LayoutSetLocalService layoutSetLocalService,
		ObjectDefinitionLocalService objectDefinitionLocalService,
		ObjectEntryLocalService objectEntryLocalService, Portal portal,
		boolean secure) {

		_companyLocalService = companyLocalService;
		_fragmentEntryLinkLocalService = fragmentEntryLinkLocalService;
		_fragmentEntryLocalService = fragmentEntryLocalService;
		_groupLocalService = groupLocalService;
		_language = language;
		_layoutClassedModelUsageLocalService =
			layoutClassedModelUsageLocalService;
		_layoutDisplayPageProviderRegistry = layoutDisplayPageProviderRegistry;
		_layoutLocalService = layoutLocalService;
		_layoutSetLocalService = layoutSetLocalService;
		_objectDefinitionLocalService = objectDefinitionLocalService;
		_objectEntryLocalService = objectEntryLocalService;
		_portal = portal;
		_secure = secure;
	}

	/**
	 * Returns the URLs a guest can reach the public page with, or an empty set
	 * when guests cannot reach the page.
	 */
	public Set<String> getLayoutURLs(Layout layout) {
		URLCollector urlCollector = new URLCollector(layout.getCompanyId());

		urlCollector.addLayoutURLs(layout);

		return urlCollector.getURLs();
	}

	/**
	 * Returns the request with its cache tags replaced by URLs. The request
	 * purges everything instead when it carries the company tag, or a tag of
	 * group 0, and the CDN can purge everything.
	 */
	public CDNPurgeRequest getURLCDNPurgeRequest(
		long companyId, String cacheTagNamespace,
		CDNPurgeRequest cdnPurgeRequest, boolean purgeAll) {

		URLCollector urlCollector = new URLCollector(companyId);

		String namespace = GetterUtil.getString(cacheTagNamespace);

		for (String tag : cdnPurgeRequest.getTags()) {
			if (!tag.startsWith(namespace)) {
				continue;
			}

			if (urlCollector.addTagURLs(
					tag.substring(namespace.length()), purgeAll)) {

				return CDNPurgeRequest.builder(
				).purgeAll(
					true
				).soft(
					cdnPurgeRequest.isSoft()
				).build();
			}
		}

		Set<String> urls = new LinkedHashSet<>(cdnPurgeRequest.getURLs());

		urls.addAll(urlCollector.getURLs());

		return CDNPurgeRequest.builder(
		).purgeAll(
			cdnPurgeRequest.isPurgeAll()
		).soft(
			cdnPurgeRequest.isSoft()
		).urls(
			urls
		).build();
	}

	private static final int _MAX_GROUPS = 100;

	private static final int _MAX_LAYOUTS = 500;

	private static final int _MAX_URLS = 10000;

	private static final Log _log = LogFactoryUtil.getLog(
		CDNPurgeURLResolver.class);

	private static final Pattern _collectionTagPattern = Pattern.compile(
		"l(\\d+)-\\d+");
	private static final Map<String, String[]> _contentClassNames =
		HashMapBuilder.put(
			"b", new String[] {BlogsEntry.class.getName()}
		).put(
			"d",
			new String[] {
				DLFileEntry.class.getName(), FileEntry.class.getName()
			}
		).put(
			"wc", new String[] {JournalArticle.class.getName()}
		).build();
	private static final Pattern _tagPattern = Pattern.compile(
		"(b|c|d|f|o|p|s|wc|x)(\\d+)");

	private final CompanyLocalService _companyLocalService;
	private final FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;
	private final FragmentEntryLocalService _fragmentEntryLocalService;
	private final GroupLocalService _groupLocalService;
	private final Language _language;
	private final LayoutClassedModelUsageLocalService
		_layoutClassedModelUsageLocalService;
	private final LayoutDisplayPageProviderRegistry
		_layoutDisplayPageProviderRegistry;
	private final LayoutLocalService _layoutLocalService;
	private final LayoutSetLocalService _layoutSetLocalService;
	private final ObjectDefinitionLocalService _objectDefinitionLocalService;
	private final ObjectEntryLocalService _objectEntryLocalService;
	private final Portal _portal;
	private final boolean _secure;

	private static class Site {

		public Site(
			String companyURL, long homePlid, Set<Locale> locales,
			List<String[]> siteURLs) {

			_companyURL = companyURL;
			_homePlid = homePlid;
			_locales = locales;
			_siteURLs = siteURLs;
		}

		private final String _companyURL;
		private final long _homePlid;
		private final Set<Locale> _locales;
		private final List<String[]> _siteURLs;

	}

	private class URLCollector {

		public URLCollector(long companyId) {
			_companyId = companyId;
		}

		public void addLayoutURLs(Layout layout) {
			if ((layout == null) || layout.isDraftLayout() ||
				layout.isPrivateLayout() || layout.isSystem()) {

				return;
			}

			Site site = _getSite(layout.getGroupId());

			if (site == null) {
				return;
			}

			_addURLs(
				site._siteURLs, site._locales, layout.getFriendlyURLMap(),
				layout.getFriendlyURL());

			if (layout.getPlid() != site._homePlid) {
				return;
			}

			// The home page is also served at the site root, and the home page
			// of the company's default site at the root of the company's
			// virtual host

			_addURLs(
				site._siteURLs, site._locales, Collections.emptyMap(),
				StringPool.SLASH);

			if (site._companyURL != null) {
				_addURLs(
					Collections.singletonList(
						new String[] {site._companyURL, StringPool.BLANK}),
					site._locales, Collections.emptyMap(), StringPool.SLASH);
			}
		}

		/**
		 * Adds the URLs of the tag, which has no namespace, and returns
		 * whether everything must be purged instead.
		 */
		public boolean addTagURLs(String tag, boolean purgeAll) {
			Matcher matcher = _collectionTagPattern.matcher(tag);

			if (matcher.matches()) {
				return _addGroupURLs(
					GetterUtil.getLong(matcher.group(1)), purgeAll);
			}

			matcher = _tagPattern.matcher(tag);

			if (!matcher.matches()) {
				if (_log.isDebugEnabled()) {
					_log.debug("Unable to convert cache tag " + tag);
				}

				return false;
			}

			String prefix = matcher.group(1);

			if (prefix.equals("c")) {
				if (purgeAll) {
					return true;
				}

				_addCompanyURLs();

				return false;
			}

			long id = GetterUtil.getLong(matcher.group(2));

			if (prefix.equals("f")) {
				_addFragmentEntryURLs(id);
			}
			else if (prefix.equals("o")) {
				_addObjectEntryURLs(id);
			}
			else if (prefix.equals("p")) {
				_addPageURLs(id);
			}
			else if (prefix.equals("s")) {
				return _addGroupURLs(id, purgeAll);
			}
			else if (prefix.equals("x")) {
				return false;
			}
			else {
				for (String className : _contentClassNames.get(prefix)) {
					_addContentURLs(className, id);
				}
			}

			return false;
		}

		public Set<String> getURLs() {
			return _urls;
		}

		private void _addCompanyURLs() {
			for (Group group :
					_groupLocalService.getActiveGroups(
						_companyId, true, true, 0, _MAX_GROUPS, null)) {

				_addGroupURLs(group.getGroupId(), false);
			}
		}

		private void _addContentURLs(String className, long classPK) {
			for (LayoutClassedModelUsage layoutClassedModelUsage :
					_layoutClassedModelUsageLocalService.
						getLayoutClassedModelUsages(
							_portal.getClassNameId(className), classPK, 0,
							_MAX_LAYOUTS, null)) {

				_addPublishedLayoutURLs(layoutClassedModelUsage.getPlid());
			}

			LayoutDisplayPageProvider<?> layoutDisplayPageProvider =
				_layoutDisplayPageProviderRegistry.
					getLayoutDisplayPageProviderByClassName(
						_companyId, className);

			if (layoutDisplayPageProvider == null) {
				return;
			}

			LayoutDisplayPageObjectProvider<?> layoutDisplayPageObjectProvider =
				layoutDisplayPageProvider.getLayoutDisplayPageObjectProvider(
					new InfoItemReference(className, classPK));

			if (layoutDisplayPageObjectProvider == null) {
				return;
			}

			Site site = _getSite(layoutDisplayPageObjectProvider.getGroupId());

			if (site == null) {
				return;
			}

			String urlSeparator = layoutDisplayPageProvider.getURLSeparator();

			Map<Locale, String> paths = new HashMap<>();

			for (Locale locale : site._locales) {
				String urlTitle = layoutDisplayPageObjectProvider.getURLTitle(
					locale);

				if (Validator.isNull(urlTitle)) {
					continue;
				}

				paths.put(locale, urlSeparator + urlTitle);
			}

			_addURLs(site._siteURLs, site._locales, paths, null);
		}

		private void _addFragmentEntryURLs(long fragmentEntryId) {
			FragmentEntry fragmentEntry =
				_fragmentEntryLocalService.fetchFragmentEntry(fragmentEntryId);

			if (fragmentEntry == null) {
				return;
			}

			try {
				for (FragmentEntryLink fragmentEntryLink :
						_fragmentEntryLinkLocalService.
							getAllFragmentEntryLinksByFragmentEntry(
								fragmentEntry, 0, _MAX_LAYOUTS, null)) {

					_addPublishedLayoutURLs(fragmentEntryLink.getPlid());
				}
			}
			catch (Exception exception) {
				_log.error(
					"Unable to get the pages of fragment entry " +
						fragmentEntryId,
					exception);
			}
		}

		/**
		 * Adds the URLs of every public page of the group and returns whether
		 * everything must be purged instead.
		 */
		private boolean _addGroupURLs(long groupId, boolean purgeAll) {
			if (groupId == 0) {
				if (purgeAll) {
					return true;
				}

				_addCompanyURLs();

				return false;
			}

			if (!_groupIds.add(groupId)) {
				return false;
			}

			List<Layout> layouts = _layoutLocalService.getLayouts(
				groupId, false, 0, _MAX_LAYOUTS + 1, null);

			if (layouts.size() > _MAX_LAYOUTS) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						StringBundler.concat(
							"Purging the URLs of the first ", _MAX_LAYOUTS,
							" public pages of group ", groupId,
							" only. Use a CDN that purges by cache tag or ",
							"purges everything to purge the whole site."));
				}

				layouts = layouts.subList(0, _MAX_LAYOUTS);
			}

			for (Layout layout : layouts) {
				addLayoutURLs(layout);
			}

			return false;
		}

		private void _addObjectEntryURLs(long objectEntryId) {
			ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
				objectEntryId);

			if (objectEntry == null) {
				return;
			}

			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.fetchObjectDefinition(
					objectEntry.getObjectDefinitionId());

			if (objectDefinition != null) {
				_addContentURLs(objectDefinition.getClassName(), objectEntryId);
			}
		}

		private void _addPageURLs(long plid) {
			Layout layout = _layoutLocalService.fetchLayout(plid);

			if (layout == null) {
				return;
			}

			// Every page of a master page carries the master page's tag

			if (layout.isSystem() && !layout.isDraftLayout()) {
				_addGroupURLs(layout.getGroupId(), false);

				return;
			}

			addLayoutURLs(layout);
		}

		private void _addPath(Set<String> paths, String path) {
			if (Validator.isNull(path)) {
				return;
			}

			paths.add(path);

			// The CDN caches the URL with and without a trailing slash apart

			if (path.equals(StringPool.SLASH)) {
				paths.add(StringPool.BLANK);
			}
			else if (path.endsWith(StringPool.SLASH)) {
				paths.add(path.substring(0, path.length() - 1));
			}
			else {
				paths.add(path + StringPool.SLASH);
			}
		}

		private void _addPublishedLayoutURLs(long plid) {
			Layout layout = _layoutLocalService.fetchLayout(plid);

			if ((layout != null) && layout.isDraftLayout()) {
				layout = _layoutLocalService.fetchLayout(layout.getClassPK());
			}

			addLayoutURLs(layout);
		}

		private void _addURL(String url) {
			if (_urls.size() < _MAX_URLS) {
				_urls.add(url);

				return;
			}

			if (!_truncated && _log.isWarnEnabled()) {
				_log.warn(
					StringBundler.concat(
						"Purging the first ", _MAX_URLS, " URLs of company ",
						_companyId, " only"));
			}

			_truncated = true;
		}

		/**
		 * Adds the URLs of a path that can be localized. Without a language
		 * prefix, every localized path is served. With the prefix of a
		 * language, the path of that language is served, or the default path
		 * when the path is not localized to that language.
		 */
		private void _addURLs(
			List<String[]> siteURLs, Set<Locale> locales,
			Map<Locale, String> localizedPaths, String defaultPath) {

			Set<String> paths = new LinkedHashSet<>();

			for (String localizedPath : localizedPaths.values()) {
				_addPath(paths, localizedPath);
			}

			_addPath(paths, defaultPath);

			_addURLs(siteURLs, StringPool.BLANK, paths);

			for (Locale locale : locales) {
				String path = localizedPaths.get(locale);

				if (Validator.isNull(path)) {
					path = defaultPath;
				}

				paths = new LinkedHashSet<>();

				_addPath(paths, path);

				for (String languagePrefix : _getLanguagePrefixes(locale)) {
					_addURLs(siteURLs, languagePrefix, paths);
				}
			}
		}

		/**
		 * Adds the URLs of the paths. The language prefix goes between the
		 * portal URL and the site path.
		 */
		private void _addURLs(
			List<String[]> siteURLs, String languagePrefix, Set<String> paths) {

			for (String path : paths) {
				for (String[] siteURL : siteURLs) {
					_addURL(
						StringBundler.concat(
							siteURL[0], languagePrefix, siteURL[1], path));
				}
			}
		}

		private Set<String> _getLanguagePrefixes(Locale locale) {
			Set<String> languagePrefixes = new LinkedHashSet<>();

			String i18nPathLanguageId = _portal.getI18nPathLanguageId(
				locale, LocaleUtil.toLanguageId(locale));

			if (Validator.isNotNull(i18nPathLanguageId)) {
				languagePrefixes.add(StringPool.SLASH + i18nPathLanguageId);
			}

			languagePrefixes.add(StringPool.SLASH + locale.getLanguage());

			return languagePrefixes;
		}

		private String _getPortalURL(String hostname) {
			if (hostname == null) {
				Company company = _companyLocalService.fetchCompany(_companyId);

				if (company == null) {
					return null;
				}

				hostname = company.getVirtualHostname();
			}

			return _portal.getPortalURL(
				hostname, _portal.getPortalServerPort(_secure), _secure);
		}

		private Site _getSite(Group group) throws Exception {
			if ((group == null) || !group.isActive()) {
				return null;
			}

			List<String[]> siteURLs = new ArrayList<>();

			LayoutSet layoutSet = _layoutSetLocalService.fetchLayoutSet(
				group.getGroupId(), false);

			if (layoutSet != null) {
				Map<String, String> virtualHostnames =
					layoutSet.getVirtualHostnames();

				for (String virtualHostname : virtualHostnames.keySet()) {
					siteURLs.add(
						new String[] {
							_getPortalURL(virtualHostname), StringPool.BLANK
						});
				}
			}

			String companyURL = null;

			if (siteURLs.isEmpty()) {
				String portalURL = _getPortalURL(null);

				if (portalURL == null) {
					return null;
				}

				siteURLs.add(
					new String[] {
						portalURL,
						_portal.getPathFriendlyURLPublic() +
							group.getFriendlyURL()
					});

				if (group.isGuest()) {
					companyURL = portalURL;
				}
			}

			return new Site(
				companyURL,
				_layoutLocalService.getDefaultPlid(group.getGroupId(), false),
				_language.getAvailableLocales(group.getGroupId()), siteURLs);
		}

		private Site _getSite(long groupId) {
			Site site = _sites.get(groupId);

			if (site != null) {
				return site;
			}

			try {
				site = _getSite(_groupLocalService.fetchGroup(groupId));
			}
			catch (Exception exception) {
				_log.error(
					"Unable to get the URLs of group " + groupId, exception);
			}

			_sites.put(groupId, site);

			return site;
		}

		private final long _companyId;
		private final Set<Long> _groupIds = new LinkedHashSet<>();
		private final Map<Long, Site> _sites = new HashMap<>();
		private boolean _truncated;
		private final Set<String> _urls = new LinkedHashSet<>();

	}

}