/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.kernel.repository.http.header.customizer;

import com.liferay.portal.kernel.repository.model.FileEntry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Customizes the headers of a file entry response whose names are only known
 * at request time, such as the cache tag header of the CDN a company uses. A
 * {@link FileEntryHttpHeaderCustomizer} customizes one header with a fixed
 * name.
 *
 * @author Gianmarco Brunialti Masera
 */
public interface FileEntryHttpHeadersCustomizer {

	public void customizeHttpHeaders(
		FileEntry fileEntry, HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse);

}