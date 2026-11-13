<%--
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */
--%>

<%@ include file="/admin/init.jsp" %>

<%
EditClientExtensionEntryDisplayContext<FragmentCollectionCET> editClientExtensionEntryDisplayContext = (EditClientExtensionEntryDisplayContext)renderRequest.getAttribute(ClientExtensionAdminWebKeys.EDIT_CLIENT_EXTENSION_ENTRY_DISPLAY_CONTEXT);

FragmentCollectionCET fragmentCollectionCET = editClientExtensionEntryDisplayContext.getCET();
%>

<aui:input ignoreRequestValue="<%= true %>" label="url" name="url" required="<%= true %>" type="text" value="<%= fragmentCollectionCET.getURL() %>" />

<aui:input ignoreRequestValue="<%= true %>" label="fragmentsJSONPath" name="fragmentsJSONPath" placeholder="fragments.json" type="text" value="<%= fragmentCollectionCET.getFragmentsJSONPath() %>" />