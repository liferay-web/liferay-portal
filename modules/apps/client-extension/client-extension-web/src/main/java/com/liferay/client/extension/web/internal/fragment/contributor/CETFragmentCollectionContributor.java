/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.client.extension.web.internal.fragment.contributor;

import com.liferay.fragment.assets.FragmentAssets;
import com.liferay.fragment.constants.FragmentConstants;
import com.liferay.fragment.contributor.FragmentCollectionContributor;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.CompanyConstants;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Exposes the fragments of a fragment collection client extension. The
 * fragments are built once from the client extension's fragments JSON index,
 * so they are read-only and resolved company-wide by renderer key, like those
 * of any other fragment collection contributor.
 *
 * @author Gianmarco Brunialti
 */
public class CETFragmentCollectionContributor
	implements FragmentCollectionContributor {

	public CETFragmentCollectionContributor(
		String assetsBaseURL,
		FragmentEntryLocalService fragmentEntryLocalService,
		JSONObject fragmentsJSONObject) {

		JSONObject collectionJSONObject = fragmentsJSONObject.getJSONObject(
			"collection");

		if (collectionJSONObject == null) {
			throw new IllegalArgumentException("Collection is null");
		}

		_fragmentCollectionKey = collectionJSONObject.getString("key");

		if (Validator.isNull(_fragmentCollectionKey)) {
			throw new IllegalArgumentException("Collection key is null");
		}

		_name = collectionJSONObject.getString("name", _fragmentCollectionKey);

		JSONArray fragmentsJSONArray = fragmentsJSONObject.getJSONArray(
			"fragments");

		if (fragmentsJSONArray == null) {
			return;
		}

		String origin = _getCrossOrigin(assetsBaseURL);
		UnaryOperator<String> urlResolver = FragmentAssets.baseURLResolver(
			assetsBaseURL);

		for (int i = 0; i < fragmentsJSONArray.length(); i++) {
			JSONObject fragmentJSONObject = fragmentsJSONArray.getJSONObject(i);

			String fragmentEntryKey = fragmentJSONObject.getString(
				"fragmentEntryKey");

			if (Validator.isNull(fragmentEntryKey)) {
				continue;
			}

			fragmentEntryKey =
				_fragmentCollectionKey + StringPool.DASH + fragmentEntryKey;

			String name = fragmentJSONObject.getString(
				"name", fragmentEntryKey);

			FragmentEntry fragmentEntry =
				fragmentEntryLocalService.createFragmentEntry(0L);

			fragmentEntry.setCompanyId(CompanyConstants.SYSTEM);
			fragmentEntry.setFragmentEntryKey(fragmentEntryKey);
			fragmentEntry.setName(name);
			fragmentEntry.setCss(StringPool.BLANK);
			fragmentEntry.setHtml(fragmentJSONObject.getString("html"));
			fragmentEntry.setJs(StringPool.BLANK);
			fragmentEntry.setCacheable(
				fragmentJSONObject.getBoolean("cacheable"));
			fragmentEntry.setConfiguration(
				fragmentJSONObject.getString("configuration"));
			fragmentEntry.setIcon(fragmentJSONObject.getString("icon", "code"));
			fragmentEntry.setType(
				FragmentConstants.getTypeFromLabel(
					fragmentJSONObject.getString("type")));
			fragmentEntry.setTypeOptions(
				fragmentJSONObject.getString("typeOptions"));
			fragmentEntry.setImagePreviewURL(
				urlResolver.apply(fragmentJSONObject.getString("thumbnail")));

			List<FragmentEntry> fragmentEntries =
				_fragmentEntries.computeIfAbsent(
					fragmentEntry.getType(), type -> new ArrayList<>());

			fragmentEntries.add(fragmentEntry);

			_fragmentEntryNames.put(fragmentEntryKey, name);

			JSONObject assetsJSONObject = fragmentJSONObject.getJSONObject(
				"assets");

			if (assetsJSONObject == null) {
				continue;
			}

			if (origin != null) {
				_addPreconnect(assetsJSONObject, origin);
			}

			_fragmentAssets.put(
				fragmentEntryKey,
				FragmentAssets.fromJSONObject(assetsJSONObject, urlResolver));
		}
	}

	public Dictionary<String, Object> getDictionary() {
		return HashMapDictionaryBuilder.<String, Object>put(
			"fragment.collection.key", _fragmentCollectionKey
		).build();
	}

	@Override
	public FragmentAssets getFragmentAssets(String fragmentEntryKey) {
		return _fragmentAssets.get(fragmentEntryKey);
	}

	@Override
	public String getFragmentCollectionKey() {
		return _fragmentCollectionKey;
	}

	@Override
	public List<FragmentEntry> getFragmentEntries() {
		List<FragmentEntry> fragmentEntries = new ArrayList<>();

		for (List<FragmentEntry> curFragmentEntries :
				_fragmentEntries.values()) {

			fragmentEntries.addAll(curFragmentEntries);
		}

		return fragmentEntries;
	}

	@Override
	public List<FragmentEntry> getFragmentEntries(int type) {
		return Collections.unmodifiableList(
			_fragmentEntries.getOrDefault(type, Collections.emptyList()));
	}

	@Override
	public List<FragmentEntry> getFragmentEntries(int[] types) {
		List<FragmentEntry> fragmentEntries = new ArrayList<>();

		for (int type : types) {
			fragmentEntries.addAll(
				_fragmentEntries.getOrDefault(type, Collections.emptyList()));
		}

		return fragmentEntries;
	}

	@Override
	public String getFragmentEntryName(String fragmentEntryKey, Locale locale) {
		return _fragmentEntryNames.get(fragmentEntryKey);
	}

	@Override
	public String getName() {
		return _name;
	}

	@Override
	public Map<Locale, String> getNames() {
		Set<Locale> locales = new HashSet<>(LanguageUtil.getAvailableLocales());

		locales.add(LocaleUtil.getDefault());

		Map<Locale, String> names = new HashMap<>();

		for (Locale locale : locales) {
			names.put(locale, _name);
		}

		return names;
	}

	private void _addPreconnect(JSONObject assetsJSONObject, String origin) {
		JSONArray preconnectJSONArray = assetsJSONObject.getJSONArray(
			"preconnect");

		if (preconnectJSONArray == null) {
			preconnectJSONArray = JSONFactoryUtil.createJSONArray();

			assetsJSONObject.put("preconnect", preconnectJSONArray);
		}

		for (int i = 0; i < preconnectJSONArray.length(); i++) {
			JSONObject preconnectJSONObject = preconnectJSONArray.getJSONObject(
				i);

			if (Objects.equals(
					origin, preconnectJSONObject.getString("origin"))) {

				return;
			}
		}

		JSONObject preconnectJSONObject = JSONFactoryUtil.createJSONObject();

		preconnectJSONObject.put(
			"crossorigin", true
		).put(
			"origin", origin
		);

		preconnectJSONArray.put(preconnectJSONObject);
	}

	/**
	 * Returns the origin of the assets base URL when it is absolute, or
	 * <code>null</code> when it is root relative. A locally deployed client
	 * extension has a root relative base URL because <code>${portalURL}</code>
	 * is replaced with the portal's path context, so an absolute base URL
	 * always points to a different host.
	 */
	private String _getCrossOrigin(String assetsBaseURL) {
		if (!assetsBaseURL.startsWith("http://") &&
			!assetsBaseURL.startsWith("https://")) {

			return null;
		}

		int index = assetsBaseURL.indexOf(
			StringPool.SLASH, assetsBaseURL.indexOf("//") + 2);

		if (index == -1) {
			return assetsBaseURL;
		}

		return assetsBaseURL.substring(0, index);
	}

	private final Map<String, FragmentAssets> _fragmentAssets = new HashMap<>();
	private final String _fragmentCollectionKey;
	private final Map<Integer, List<FragmentEntry>> _fragmentEntries =
		new HashMap<>();
	private final Map<String, String> _fragmentEntryNames = new HashMap<>();
	private final String _name;

}