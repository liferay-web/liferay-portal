/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.object.deployer;

import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.cdn.internal.cache.tag.resolver.ObjectEntryCDNCacheTagModelResolver;
import com.liferay.object.deployer.ObjectDefinitionDeployer;
import com.liferay.object.model.ObjectDefinition;

import java.util.Collections;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Registers a cache tag resolver for the entries of each custom object
 * definition, because every object definition has its own class name.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = ObjectDefinitionDeployer.class)
public class CDNObjectDefinitionDeployer implements ObjectDefinitionDeployer {

	@Override
	public List<ServiceRegistration<?>> deploy(
		ObjectDefinition objectDefinition) {

		if (objectDefinition.isUnmodifiableSystemObject()) {
			return Collections.emptyList();
		}

		return Collections.singletonList(
			_bundleContext.registerService(
				CDNCacheTagModelResolver.class,
				new ObjectEntryCDNCacheTagModelResolver(
					objectDefinition.getClassName()),
				null));
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;
	}

	private BundleContext _bundleContext;

}