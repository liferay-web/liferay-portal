/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.fragment.collection.contributor.module.samples.internal.osgi.commands;

import com.liferay.client.extension.constants.ClientExtensionEntryConstants;
import com.liferay.client.extension.service.ClientExtensionEntryRelLocalService;
import com.liferay.client.extension.type.CET;
import com.liferay.client.extension.type.manager.CETManager;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.vulcan.pagination.Pagination;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Attaches a theme CSS client extension to a page of the default company, for
 * the sample pages that compare module fragments on a theme baseline.
 *
 * @author Gianmarco Brunialti
 */
@Component(
	property = {
		"osgi.command.function=attachThemeCSS",
		"osgi.command.scope=moduleSamples"
	},
	service = ModuleSamplesOSGiCommands.class
)
public class ModuleSamplesOSGiCommands {

	public String attachThemeCSS(
			String groupFriendlyURL, String layoutFriendlyURL,
			String cetExternalReferenceCode)
		throws Exception {

		Group group = _groupLocalService.getFriendlyURLGroup(
			_portal.getDefaultCompanyId(), groupFriendlyURL);

		Layout layout = _layoutLocalService.getFriendlyURLLayout(
			group.getGroupId(), false, layoutFriendlyURL);

		long classNameId = _portal.getClassNameId(Layout.class);

		for (Layout curLayout :
				new Layout[] {layout, layout.fetchDraftLayout()}) {

			if (curLayout == null) {
				continue;
			}

			_clientExtensionEntryRelLocalService.deleteClientExtensionEntryRels(
				classNameId, curLayout.getPlid(),
				ClientExtensionEntryConstants.TYPE_THEME_CSS);

			_clientExtensionEntryRelLocalService.addClientExtensionEntryRel(
				curLayout.getUserId(), group.getGroupId(), classNameId,
				curLayout.getPlid(), cetExternalReferenceCode,
				ClientExtensionEntryConstants.TYPE_THEME_CSS, StringPool.BLANK,
				new ServiceContext());
		}

		StringBundler sb = new StringBundler();

		sb.append("Attached ");
		sb.append(cetExternalReferenceCode);
		sb.append(" to ");
		sb.append(layoutFriendlyURL);
		sb.append(" in company ");
		sb.append(group.getCompanyId());
		sb.append(", theme CSS client extensions:");

		for (CET cet :
				_cetManager.getCETs(
					group.getCompanyId(), null,
					ClientExtensionEntryConstants.TYPE_THEME_CSS,
					Pagination.of(1, 100), (Sort)null)) {

			sb.append(StringPool.SPACE);
			sb.append(cet.getExternalReferenceCode());
		}

		return sb.toString();
	}

	@Reference
	private CETManager _cetManager;

	@Reference
	private ClientExtensionEntryRelLocalService
		_clientExtensionEntryRelLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private Portal _portal;

}