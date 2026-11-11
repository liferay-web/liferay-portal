/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import classNames from 'classnames';

import './SVSeverity.scss';

type SVSeverityProps = {
	className?: string;
	severity?: string;
};

const SVSeverity = ({className, severity}: SVSeverityProps) => {
	if (!severity) {
		return null;
	}

	return (
		<div
			className={classNames(
				'px-2 py-1 rounded sv-severity text-center',
				`sv-severity-${severity.toLowerCase()}`,
				className
			)}
		>
			{severity}
		</div>
	);
};

export default SVSeverity;
