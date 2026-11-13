/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.client.extension.type;

import com.liferay.client.extension.type.annotation.CETProperty;
import com.liferay.client.extension.type.annotation.CETType;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Delivers a collection of module fragments whose markup, configuration and
 * asset manifests are described by an index file served next to their built,
 * content hashed assets.
 *
 * @author Gianmarco Brunialti
 */
@CETType(
	description = "Delivers a collection of module fragments.",
	name = "fragmentCollection"
)
@ProviderType
public interface FragmentCollectionCET extends CET {

	@CETProperty(
		defaultValue = "fragments.json", name = "fragmentsJSONPath",
		type = CETProperty.Type.String
	)
	public String getFragmentsJSONPath();

	/**
	 * Returns the URL that relative asset references and the fragments JSON
	 * path are resolved against. It defaults to the client extension's base
	 * URL.
	 */
	@CETProperty(defaultValue = "", name = "url", type = CETProperty.Type.URL)
	public String getURL();

}
