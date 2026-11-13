/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.internal.renderer;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.DigesterUtil;
import com.liferay.portal.kernel.util.StringUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Component;

/**
 * Combines the style sheets of the module fragments of a page composition into
 * one content addressed style sheet, and keeps it so that it can be served as
 * an immutable resource.
 *
 * <p>
 * Relative URLs inside the style sheets are rebased on the URL of the style
 * sheet they come from, because the combined content is served, or inlined,
 * from a different location.
 * </p>
 *
 * @author Gianmarco Brunialti
 */
@Component(service = FragmentAssetsStyleSheets.class)
public class FragmentAssetsStyleSheets {

	public String combine(Map<String, String> styleSheets) {
		StringBundler sb = new StringBundler(styleSheets.size() * 2);

		for (Map.Entry<String, String> entry : styleSheets.entrySet()) {
			sb.append(rebase(entry.getKey(), entry.getValue()));
			sb.append(StringPool.NEW_LINE);
		}

		return sb.toString();
	}

	public String get(String hash) {
		return _combinedStyleSheets.get(hash);
	}

	public String put(String content) {
		String hash = StringUtil.toLowerCase(
			DigesterUtil.digestHex(DigesterUtil.SHA_256, content)
		).substring(
			0, 16
		);

		_combinedStyleSheets.putIfAbsent(hash, content);

		return hash;
	}

	public String rebase(String styleSheetURL, String content) {
		String baseURL = styleSheetURL.substring(
			0, styleSheetURL.lastIndexOf(StringPool.SLASH) + 1);

		Matcher matcher = _urlPattern.matcher(content);

		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			String url = matcher.group(2);

			if (url.startsWith("./")) {
				url = url.substring(2);
			}

			matcher.appendReplacement(
				sb,
				Matcher.quoteReplacement(
					StringBundler.concat(
						"url(", matcher.group(1), baseURL, url,
						matcher.group(1), ")")));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	private static final Pattern _urlPattern = Pattern.compile(
		"url\\(\\s*(['\"]?)(?![a-zA-Z][a-zA-Z0-9+.-]*:|/|#)([^'\")\\s]+)\\1" +
			"\\s*\\)");

	private final Map<String, String> _combinedStyleSheets =
		new ConcurrentHashMap<>();

}