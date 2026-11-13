/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.events.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.events.LifecycleAction;
import com.liferay.portal.kernel.events.LifecycleEvent;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import jakarta.servlet.http.Cookie;

import java.io.InputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Gianmarco Brunialti Masera
 */
@RunWith(Arquillian.class)
public class CDNSignedInMarkerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_layout = LayoutTestUtil.addTypeContentPublishedLayout(
			_group, RandomTestUtil.randomString(),
			WorkflowConstants.STATUS_APPROVED);
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testLoginPostAction() throws Exception {
		MockHttpServletResponse mockHttpServletResponse =
			_processLifecycleEvent(_loginPostLifecycleAction, false, true);

		Cookie cookie = mockHttpServletResponse.getCookie(_COOKIE_NAME);

		Assert.assertNotNull(cookie);
		Assert.assertEquals("Lax", cookie.getAttribute("SameSite"));
		Assert.assertEquals(-1, cookie.getMaxAge());
		Assert.assertEquals("/", cookie.getPath());
		Assert.assertEquals("1", cookie.getValue());
		Assert.assertTrue(cookie.isHttpOnly());
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testLogoutPostAction() throws Exception {
		MockHttpServletResponse mockHttpServletResponse =
			_processLifecycleEvent(_logoutPostLifecycleAction, true, false);

		Cookie cookie = mockHttpServletResponse.getCookie(_COOKIE_NAME);

		Assert.assertNotNull(cookie);
		Assert.assertEquals(0, cookie.getMaxAge());
		Assert.assertEquals("/", cookie.getPath());
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testServicePreAction() throws Exception {

		// Guest without the marker

		Assert.assertNull(_getMarkerSetCookie(false));

		// Guest with a stale marker

		String setCookie = _getMarkerSetCookie(true);

		Assert.assertNotNull(setCookie);
		Assert.assertTrue(setCookie, setCookie.contains("Max-Age=0"));

		// Signed in user without the marker

		MockHttpServletResponse mockHttpServletResponse =
			_processLifecycleEvent(_servicePreLifecycleAction, false, true);

		Cookie cookie = mockHttpServletResponse.getCookie(_COOKIE_NAME);

		Assert.assertNotNull(cookie);
		Assert.assertEquals("1", cookie.getValue());

		// Signed in user with the marker

		mockHttpServletResponse = _processLifecycleEvent(
			_servicePreLifecycleAction, true, true);

		Assert.assertNull(mockHttpServletResponse.getCookie(_COOKIE_NAME));
	}

	@FeatureFlag(enable = false, value = "LPD-107638")
	@Test
	public void testWhenFeatureFlagIsDisabled() throws Exception {
		Assert.assertNull(_getMarkerSetCookie(true));

		MockHttpServletResponse mockHttpServletResponse =
			_processLifecycleEvent(_loginPostLifecycleAction, false, true);

		Assert.assertNull(mockHttpServletResponse.getCookie(_COOKIE_NAME));

		mockHttpServletResponse = _processLifecycleEvent(
			_logoutPostLifecycleAction, true, false);

		Assert.assertNull(mockHttpServletResponse.getCookie(_COOKIE_NAME));

		mockHttpServletResponse = _processLifecycleEvent(
			_servicePreLifecycleAction, false, true);

		Assert.assertNull(mockHttpServletResponse.getCookie(_COOKIE_NAME));
	}

	private String _getMarkerSetCookie(boolean marker) throws Exception {
		URL url = new URL(
			StringBundler.concat(
				"http://localhost:", PortalUtil.getPortalServerPort(false),
				"/web", _group.getFriendlyURL(), _layout.getFriendlyURL()));

		HttpURLConnection httpURLConnection =
			(HttpURLConnection)url.openConnection();

		httpURLConnection.setInstanceFollowRedirects(false);

		if (marker) {
			httpURLConnection.setRequestProperty("Cookie", _COOKIE_NAME + "=1");
		}

		try (InputStream inputStream = httpURLConnection.getInputStream()) {
			StreamUtil.toByteArray(inputStream);

			Map<String, List<String>> headerFields =
				httpURLConnection.getHeaderFields();

			List<String> setCookies = headerFields.getOrDefault(
				"Set-Cookie", new ArrayList<>());

			for (String setCookie : setCookies) {
				if (setCookie.startsWith(_COOKIE_NAME + "=")) {
					return setCookie;
				}
			}

			return null;
		}
		finally {
			httpURLConnection.disconnect();
		}
	}

	private MockHttpServletResponse _processLifecycleEvent(
			LifecycleAction lifecycleAction, boolean marker, boolean signedIn)
		throws Exception {

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setAttribute(
			WebKeys.COMPANY_ID, TestPropsValues.getCompanyId());

		if (marker) {
			mockHttpServletRequest.setCookies(new Cookie(_COOKIE_NAME, "1"));
		}

		if (signedIn) {
			mockHttpServletRequest.setAttribute(
				WebKeys.USER_ID, TestPropsValues.getUserId());
		}

		MockHttpServletResponse mockHttpServletResponse =
			new MockHttpServletResponse();

		lifecycleAction.processLifecycleEvent(
			new LifecycleEvent(
				mockHttpServletRequest, mockHttpServletResponse));

		return mockHttpServletResponse;
	}

	private static final String _COOKIE_NAME = "LFR_SESSION_LOGIN";

	@DeleteAfterTestRun
	private Group _group;

	private Layout _layout;

	@Inject(
		filter = "component.name=com.liferay.cdn.internal.events.CDNSignedInMarkerLoginPostAction"
	)
	private LifecycleAction _loginPostLifecycleAction;

	@Inject(
		filter = "component.name=com.liferay.cdn.internal.events.CDNSignedInMarkerLogoutPostAction"
	)
	private LifecycleAction _logoutPostLifecycleAction;

	@Inject(
		filter = "component.name=com.liferay.cdn.internal.events.CDNSignedInMarkerServicePreAction"
	)
	private LifecycleAction _servicePreLifecycleAction;

}