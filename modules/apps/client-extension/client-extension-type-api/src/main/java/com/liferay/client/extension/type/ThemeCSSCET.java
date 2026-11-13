/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.client.extension.type;

import com.liferay.client.extension.type.annotation.CETProperty;
import com.liferay.client.extension.type.annotation.CETType;

import org.osgi.annotation.versioning.ProviderType;

/**
 * @author Iván Zaera Avellón
 */
@CETType(description = "This is a description.", name = "themeCSS")
@ProviderType
public interface ThemeCSSCET extends CET {

	public static final String CLAY_LOADING_ALWAYS = "always";

	public static final String CLAY_LOADING_NEVER = "never";

	public static final String CLAY_LOADING_SIGNED_IN = "signedIn";

	@CETProperty(
		defaultValue = "", name = "baselineURL", type = CETProperty.Type.URL
	)
	public String getBaselineURL();

	@CETProperty(
		defaultValue = "always", name = "clayLoading",
		type = CETProperty.Type.String
	)
	public String getClayLoading();

	@CETProperty(
		defaultValue = "", name = "clayRTLURL", type = CETProperty.Type.URL
	)
	public String getClayRTLURL();

	@CETProperty(
		defaultValue = "", name = "clayURL", type = CETProperty.Type.URL
	)
	public String getClayURL();

	@CETProperty(
		defaultValue = "", name = "frontendTokenDefinitionJSON",
		type = CETProperty.Type.String
	)
	public String getFrontendTokenDefinitionJSON();

	@CETProperty(
		defaultValue = "", name = "mainRTLURL", type = CETProperty.Type.URL
	)
	public String getMainRTLURL();

	@CETProperty(
		defaultValue = "", name = "mainURL", type = CETProperty.Type.URL
	)
	public String getMainURL();

	@CETProperty(
		defaultValue = "layout", label = "scope", name = "scope",
		type = CETProperty.Type.String
	)
	public String getScope();

}