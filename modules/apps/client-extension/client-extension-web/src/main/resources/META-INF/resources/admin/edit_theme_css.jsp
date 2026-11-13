<%--
/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
--%>

<%@ include file="/admin/init.jsp" %>

<%
EditClientExtensionEntryDisplayContext<ThemeCSSCET> editClientExtensionEntryDisplayContext = (EditClientExtensionEntryDisplayContext)renderRequest.getAttribute(ClientExtensionAdminWebKeys.EDIT_CLIENT_EXTENSION_ENTRY_DISPLAY_CONTEXT);

ThemeCSSCET themeCSSCET = editClientExtensionEntryDisplayContext.getCET();
%>

<aui:field-wrapper cssClass="form-group">
	<aui:input label="main-css-url" name="mainURL" type="text" value="<%= themeCSSCET.getMainURL() %>" />

	<div class="form-text">
		<liferay-ui:message key="this-css-replaces-main-css" />
	</div>
</aui:field-wrapper>

<aui:field-wrapper cssClass="form-group">
	<aui:input label="main-rtl-css-url" name="mainRTLURL" type="text" value="<%= themeCSSCET.getMainRTLURL() %>" />

	<div class="form-text">
		<liferay-ui:message key="this-css-replaces-main-css-for-right-to-left-languages" />
	</div>
</aui:field-wrapper>

<aui:field-wrapper cssClass="form-group">
	<aui:input label="clay-css-url" name="clayURL" type="text" value="<%= themeCSSCET.getClayURL() %>" />

	<div class="form-text">
		<liferay-ui:message key="this-css-replaces-clay-css" />
	</div>
</aui:field-wrapper>

<aui:field-wrapper cssClass="form-group">
	<aui:input label="clay-rtl-css-url" name="clayRTLURL" type="text" value="<%= themeCSSCET.getClayRTLURL() %>" />

	<div class="form-text">
		<liferay-ui:message key="this-css-replaces-clay-css-for-right-to-left-languages" />
	</div>
</aui:field-wrapper>

<aui:field-wrapper cssClass="form-group">
	<aui:select label="clay-loading" name="clayLoading" value="<%= themeCSSCET.getClayLoading() %>">
		<aui:option label="always" value="<%= ThemeCSSCET.CLAY_LOADING_ALWAYS %>" />
		<aui:option label="signed-in-users-only" value="<%= ThemeCSSCET.CLAY_LOADING_SIGNED_IN %>" />
		<aui:option label="never" value="<%= ThemeCSSCET.CLAY_LOADING_NEVER %>" />
	</aui:select>

	<div class="form-text">
		<liferay-ui:message key="clay-css-is-loaded-in-a-cascade-layer-below-fragments-when-it-is-not-loaded-for-everyone" />
	</div>
</aui:field-wrapper>

<aui:field-wrapper cssClass="form-group">
	<aui:input label="baseline-css-url" name="baselineURL" type="text" value="<%= themeCSSCET.getBaselineURL() %>" />

	<div class="form-text">
		<liferay-ui:message key="this-css-provides-the-reset-and-baseline-styles-that-fragments-build-on" />
	</div>
</aui:field-wrapper>

<aui:field-wrapper cssClass="form-group">
	<react:component
		module="{FrontendTokenDefinitionFilePicker} from client-extension-web"
		props='<%=
			HashMapBuilder.<String, Object>put(
				"frontendTokenDefinitionJSON", themeCSSCET.getFrontendTokenDefinitionJSON()
			).put(
				"learnResources", LearnMessageUtil.getReactDataJSONObject("client-extension-web")
			).build()
		%>'
	/>
</aui:field-wrapper>