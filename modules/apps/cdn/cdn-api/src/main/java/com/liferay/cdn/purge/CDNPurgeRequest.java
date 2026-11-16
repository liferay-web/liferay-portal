/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.purge;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.Validator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeRequest {

	public static Builder builder() {
		return new Builder();
	}

	public Set<String> getTags() {
		return _tags;
	}

	public Set<String> getURLs() {
		return _urls;
	}

	public boolean isEmpty() {
		if (!_purgeAll && _tags.isEmpty() && _urls.isEmpty()) {
			return true;
		}

		return false;
	}

	public boolean isPurgeAll() {
		return _purgeAll;
	}

	/**
	 * Returns whether the request allows a soft purge. A soft purge is only
	 * sent when the company configuration and the provider also allow it.
	 */
	public boolean isSoft() {
		return _soft;
	}

	@Override
	public String toString() {
		return StringBundler.concat(
			"{purgeAll=", _purgeAll, ", soft=", _soft, ", tags=", _tags,
			", urls=", _urls, "}");
	}

	public static class Builder {

		public CDNPurgeRequest build() {
			return new CDNPurgeRequest(this);
		}

		public Builder purgeAll(boolean purgeAll) {
			_purgeAll = purgeAll;

			return this;
		}

		public Builder soft(boolean soft) {
			_soft = soft;

			return this;
		}

		public Builder tags(Collection<String> tags) {
			for (String tag : tags) {
				if (Validator.isNull(tag)) {
					continue;
				}

				_tags.add(tag);
			}

			return this;
		}

		public Builder tags(String... tags) {
			return tags(Arrays.asList(tags));
		}

		public Builder urls(Collection<String> urls) {
			for (String url : urls) {
				if (Validator.isNull(url)) {
					continue;
				}

				_urls.add(url);
			}

			return this;
		}

		public Builder urls(String... urls) {
			return urls(Arrays.asList(urls));
		}

		private boolean _purgeAll;
		private boolean _soft = true;
		private final Set<String> _tags = new LinkedHashSet<>();
		private final Set<String> _urls = new LinkedHashSet<>();

	}

	private CDNPurgeRequest(Builder builder) {
		_purgeAll = builder._purgeAll;
		_soft = builder._soft;
		_tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder._tags));
		_urls = Collections.unmodifiableSet(new LinkedHashSet<>(builder._urls));
	}

	private final boolean _purgeAll;
	private final boolean _soft;
	private final Set<String> _tags;
	private final Set<String> _urls;

}