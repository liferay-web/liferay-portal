/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.assets;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Describes the external, cacheable resources of a module fragment: style
 * sheets, an ES module and its static dependencies, and resource hints. URLs
 * are already resolved against the channel that delivers the fragment.
 *
 * @author Gianmarco Brunialti
 */
public class FragmentAssets {

	public static final String HYDRATE_EAGER = "eager";

	public static final String HYDRATE_IDLE = "idle";

	public static final String HYDRATE_INTERACTION = "interaction";

	public static final String HYDRATE_VISIBLE = "visible";

	public static UnaryOperator<String> baseURLResolver(String baseURL) {
		return url -> {
			if (Validator.isNull(url) || url.startsWith("@") ||
				url.startsWith("/") || url.startsWith("http://") ||
				url.startsWith("https://")) {

				return url;
			}

			if (url.startsWith("./")) {
				url = url.substring(2);
			}

			if (baseURL.endsWith("/")) {
				return baseURL + url;
			}

			return baseURL + "/" + url;
		};
	}

	public static FragmentAssets fromJSONObject(
		JSONObject jsonObject, UnaryOperator<String> urlResolver) {

		return fromJSONObject(jsonObject, urlResolver, null);
	}

	/**
	 * @param styleSheetReader returns the content of a resolved style sheet
	 *        URL, or <code>null</code> when the channel cannot read it locally
	 */
	public static FragmentAssets fromJSONObject(
		JSONObject jsonObject, UnaryOperator<String> urlResolver,
		Function<String, String> styleSheetReader) {

		if (jsonObject == null) {
			return null;
		}

		List<String> styleSheetURLs = new ArrayList<>();

		JSONArray cssJSONArray = jsonObject.getJSONArray("css");

		if (cssJSONArray != null) {
			for (int i = 0; i < cssJSONArray.length(); i++) {
				JSONObject cssJSONObject = cssJSONArray.getJSONObject(i);

				String href = cssJSONObject.getString("href");

				if (Validator.isNotNull(href)) {
					styleSheetURLs.add(urlResolver.apply(href));
				}
			}
		}

		String hydrate = HYDRATE_VISIBLE;
		List<String> moduleDependencyURLs = new ArrayList<>();
		String moduleURL = null;

		JSONObject jsJSONObject = jsonObject.getJSONObject("js");

		if (jsJSONObject != null) {
			hydrate = jsJSONObject.getString("hydrate", HYDRATE_VISIBLE);

			JSONArray depsJSONArray = jsJSONObject.getJSONArray("deps");

			if (depsJSONArray != null) {
				for (int i = 0; i < depsJSONArray.length(); i++) {
					moduleDependencyURLs.add(
						urlResolver.apply(depsJSONArray.getString(i)));
				}
			}

			String module = jsJSONObject.getString("module");

			if (Validator.isNotNull(module)) {
				moduleURL = urlResolver.apply(module);
			}
		}

		List<Preconnect> preconnects = new ArrayList<>();

		JSONArray preconnectJSONArray = jsonObject.getJSONArray("preconnect");

		if (preconnectJSONArray != null) {
			for (int i = 0; i < preconnectJSONArray.length(); i++) {
				JSONObject preconnectJSONObject =
					preconnectJSONArray.getJSONObject(i);

				preconnects.add(
					new Preconnect(
						preconnectJSONObject.getBoolean("crossorigin"),
						preconnectJSONObject.getString("origin")));
			}
		}

		List<Preload> preloads = new ArrayList<>();

		JSONArray preloadJSONArray = jsonObject.getJSONArray("preload");

		if (preloadJSONArray != null) {
			for (int i = 0; i < preloadJSONArray.length(); i++) {
				JSONObject preloadJSONObject = preloadJSONArray.getJSONObject(
					i);

				preloads.add(
					new Preload(
						preloadJSONObject.getString("as"),
						preloadJSONObject.getBoolean("crossorigin"),
						preloadJSONObject.getString("fetchpriority"),
						urlResolver.apply(preloadJSONObject.getString("href")),
						preloadJSONObject.getString("type"),
						preloadJSONObject.getString("when")));
			}
		}

		return new FragmentAssets(
			hydrate, moduleDependencyURLs, moduleURL, preconnects, preloads,
			styleSheetReader, styleSheetURLs);
	}

	public String getHydrate() {
		return _hydrate;
	}

	public List<String> getModuleDependencyURLs() {
		return _moduleDependencyURLs;
	}

	public String getModuleURL() {
		return _moduleURL;
	}

	public List<Preconnect> getPreconnects() {
		return _preconnects;
	}

	public List<Preload> getPreloads() {
		return _preloads;
	}

	/**
	 * Returns the content of one of the fragment's style sheets, so that it can
	 * be combined with others or inlined, or <code>null</code> when it is only
	 * available through its URL.
	 */
	public String getStyleSheet(String styleSheetURL) {
		if ((_styleSheetReader == null) ||
			!_styleSheetURLs.contains(styleSheetURL)) {

			return null;
		}

		return _styleSheetReader.apply(styleSheetURL);
	}

	public List<String> getStyleSheetURLs() {
		return _styleSheetURLs;
	}

	public boolean hasModule() {
		return Validator.isNotNull(_moduleURL);
	}

	public static class Preconnect {

		public Preconnect(boolean crossOrigin, String origin) {
			_crossOrigin = crossOrigin;
			_origin = origin;
		}

		public String getOrigin() {
			return _origin;
		}

		public boolean isCrossOrigin() {
			return _crossOrigin;
		}

		private final boolean _crossOrigin;
		private final String _origin;

	}

	public static class Preload {

		public Preload(
			String as, boolean crossOrigin, String fetchPriority, String href,
			String type, String when) {

			_as = as;
			_crossOrigin = crossOrigin;
			_fetchPriority = fetchPriority;
			_href = href;
			_type = type;
			_when = when;
		}

		public String getAs() {
			return _as;
		}

		public String getFetchPriority() {
			return _fetchPriority;
		}

		public String getHref() {
			return _href;
		}

		public String getType() {
			return _type;
		}

		public String getWhen() {
			return _when;
		}

		public boolean isAboveTheFold() {
			return Objects.equals(_when, "aboveTheFold");
		}

		public boolean isCrossOrigin() {
			return _crossOrigin;
		}

		private final String _as;
		private final boolean _crossOrigin;
		private final String _fetchPriority;
		private final String _href;
		private final String _type;
		private final String _when;

	}

	private FragmentAssets(
		String hydrate, List<String> moduleDependencyURLs, String moduleURL,
		List<Preconnect> preconnects, List<Preload> preloads,
		Function<String, String> styleSheetReader,
		List<String> styleSheetURLs) {

		_hydrate = hydrate;
		_moduleDependencyURLs = Collections.unmodifiableList(
			moduleDependencyURLs);
		_moduleURL = moduleURL;
		_preconnects = Collections.unmodifiableList(preconnects);
		_preloads = Collections.unmodifiableList(preloads);
		_styleSheetReader = styleSheetReader;
		_styleSheetURLs = Collections.unmodifiableList(styleSheetURLs);
	}

	private final String _hydrate;
	private final List<String> _moduleDependencyURLs;
	private final String _moduleURL;
	private final List<Preconnect> _preconnects;
	private final List<Preload> _preloads;
	private final Function<String, String> _styleSheetReader;
	private final List<String> _styleSheetURLs;

}