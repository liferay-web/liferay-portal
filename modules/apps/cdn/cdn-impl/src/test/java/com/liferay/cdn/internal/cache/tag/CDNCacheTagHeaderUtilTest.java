/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.cache.tag;

import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNCacheTagHeaderUtilTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testGetHeaderValue() {
		List<String> cacheTags = Arrays.asList("c1", "s2", "p3");
		List<String> modelCacheTags = Arrays.asList("wc4", "b5", "p3");

		Assert.assertEquals(
			"c1 s2 p3 wc4 b5",
			CDNCacheTagHeaderUtil.getHeaderValue(
				cacheTags, 15, modelCacheTags, "x2", " "));
		Assert.assertEquals(
			"c1,s2,p3,x2",
			CDNCacheTagHeaderUtil.getHeaderValue(
				cacheTags, 14, modelCacheTags, "x2", ","));

		Assert.assertEquals(
			"c1,s2,p3",
			CDNCacheTagHeaderUtil.getHeaderValue(
				cacheTags, 14, Arrays.asList(), "x2", ","));
	}

}