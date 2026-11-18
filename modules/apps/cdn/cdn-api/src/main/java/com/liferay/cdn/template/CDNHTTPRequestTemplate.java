/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Describes one HTTP request of a {@link CDNTemplate}. The URL template, the
 * header values, and the body template are rendered with the variables that
 * {@link CDNTemplate} documents.
 *
 * @author Gianmarco Brunialti Masera
 */
public class CDNHTTPRequestTemplate {

	public static Builder builder() {
		return new Builder();
	}

	public String getBodyTemplate() {
		return _bodyTemplate;
	}

	public String getContentType() {
		return _contentType;
	}

	/**
	 * Returns the headers in the form <code>Name: value</code>.
	 */
	public List<String> getHeaders() {
		return _headers;
	}

	public String getMethod() {
		return _method;
	}

	public String getURLTemplate() {
		return _urlTemplate;
	}

	public static class Builder {

		public Builder bodyTemplate(String bodyTemplate) {
			_bodyTemplate = bodyTemplate;

			return this;
		}

		public CDNHTTPRequestTemplate build() {
			return new CDNHTTPRequestTemplate(this);
		}

		public Builder contentType(String contentType) {
			_contentType = contentType;

			return this;
		}

		public Builder headers(List<String> headers) {
			_headers.addAll(headers);

			return this;
		}

		public Builder headers(String... headers) {
			return headers(Arrays.asList(headers));
		}

		public Builder method(String method) {
			_method = method;

			return this;
		}

		public Builder urlTemplate(String urlTemplate) {
			_urlTemplate = urlTemplate;

			return this;
		}

		private String _bodyTemplate;
		private String _contentType = "application/json";
		private final List<String> _headers = new ArrayList<>();
		private String _method = "POST";
		private String _urlTemplate;

	}

	private CDNHTTPRequestTemplate(Builder builder) {
		_bodyTemplate = builder._bodyTemplate;
		_contentType = builder._contentType;
		_headers = Collections.unmodifiableList(
			new ArrayList<>(builder._headers));
		_method = builder._method;
		_urlTemplate = builder._urlTemplate;
	}

	private final String _bodyTemplate;
	private final String _contentType;
	private final List<String> _headers;
	private final String _method;
	private final String _urlTemplate;

}