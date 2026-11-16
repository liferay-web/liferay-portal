/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.object.deployer;

import com.liferay.cdn.cache.tag.CDNCacheTagModelResolver;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNObjectDefinitionDeployerTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		ReflectionTestUtil.setFieldValue(
			_cdnObjectDefinitionDeployer, "_bundleContext", _bundleContext);
	}

	@Test
	public void testDeploy() {
		ObjectDefinition objectDefinition = Mockito.mock(
			ObjectDefinition.class);

		String className =
			"com.liferay.object.model.ObjectDefinition#" +
				RandomTestUtil.randomString();

		Mockito.when(
			objectDefinition.getClassName()
		).thenReturn(
			className
		);

		List<ServiceRegistration<?>> serviceRegistrations =
			_cdnObjectDefinitionDeployer.deploy(objectDefinition);

		Assert.assertEquals(
			serviceRegistrations.toString(), 1, serviceRegistrations.size());

		ArgumentCaptor<CDNCacheTagModelResolver> argumentCaptor =
			ArgumentCaptor.forClass(CDNCacheTagModelResolver.class);

		Mockito.verify(
			_bundleContext
		).registerService(
			Mockito.eq(CDNCacheTagModelResolver.class),
			argumentCaptor.capture(), Mockito.isNull()
		);

		CDNCacheTagModelResolver cdnCacheTagModelResolver =
			argumentCaptor.getValue();

		Assert.assertEquals(className, cdnCacheTagModelResolver.getClassName());
		Assert.assertEquals("o5", cdnCacheTagModelResolver.getCacheTag(5));

		Mockito.when(
			objectDefinition.isUnmodifiableSystemObject()
		).thenReturn(
			true
		);

		serviceRegistrations = _cdnObjectDefinitionDeployer.deploy(
			objectDefinition);

		Assert.assertTrue(
			serviceRegistrations.toString(), serviceRegistrations.isEmpty());
	}

	private final BundleContext _bundleContext = Mockito.mock(
		BundleContext.class);
	private final CDNObjectDefinitionDeployer _cdnObjectDefinitionDeployer =
		new CDNObjectDefinitionDeployer();

}