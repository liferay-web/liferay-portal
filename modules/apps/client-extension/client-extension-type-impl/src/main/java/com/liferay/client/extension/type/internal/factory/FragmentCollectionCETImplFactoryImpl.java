/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.client.extension.type.internal.factory;

import com.liferay.client.extension.exception.ClientExtensionEntryTypeSettingsException;
import com.liferay.client.extension.type.FragmentCollectionCET;
import com.liferay.client.extension.type.internal.FragmentCollectionCETImpl;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.portal.kernel.util.Validator;

import jakarta.portlet.PortletRequest;

import java.util.Date;
import java.util.Properties;

/**
 * @author Gianmarco Brunialti
 */
public class FragmentCollectionCETImplFactoryImpl
	extends BaseCETImplFactoryImpl<FragmentCollectionCET> {

	public FragmentCollectionCETImplFactoryImpl() {
		super(FragmentCollectionCET.class);
	}

	@Override
	public FragmentCollectionCET create(
		String baseURL, long companyId, Date createDate, String description,
		String externalReferenceCode, Date modifiedDate, String name,
		Properties properties, boolean readOnly, String sourceCodeURL,
		int status, UnicodeProperties typeSettingsUnicodeProperties) {

		return new FragmentCollectionCETImpl(
			baseURL, companyId, createDate, description, externalReferenceCode,
			modifiedDate, name, properties, readOnly, sourceCodeURL, status,
			typeSettingsUnicodeProperties);
	}

	@Override
	public UnicodeProperties getUnicodeProperties(
		PortletRequest portletRequest) {

		return UnicodePropertiesBuilder.create(
			true
		).put(
			"fragmentsJSONPath",
			ParamUtil.getString(portletRequest, "fragmentsJSONPath")
		).put(
			"url", ParamUtil.getString(portletRequest, "url")
		).build();
	}

	@Override
	public void validate(
			FragmentCollectionCET newFragmentCollectionCET,
			FragmentCollectionCET oldFragmentCollectionCET)
		throws PortalException {

		String url = newFragmentCollectionCET.getURL();

		if (!Validator.isUrl(url, true)) {
			throw new ClientExtensionEntryTypeSettingsException(
				"Invalid URL: " + url, "url-x-is-invalid", url);
		}
	}

}