/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {ReactNode} from 'react';
import i18n from '~/utils/I18n';

import './SVHeader.scss';

type SVHeaderProps = {
	children: ReactNode;
	icon: JSX.Element;
	label: string;
	title: string;
};

const SVHeader = ({children, icon, label, title}: SVHeaderProps) => {
	return (
		<div className="d-flex flex-column sv-header">
			<span className="font-weight-semi-bold text-white">{label}</span>

			<div className="align-items-center d-flex m-0">
				<span className="align-items-center d-flex mr-3 sv-header-icon">
					{icon}
				</span>

				<h1 className="font-weight-bold m-0 sv-header-title text-neutral-0">
					{i18n.translate(title)}
				</h1>
			</div>

			{children}
		</div>
	);
};

export default SVHeader;
