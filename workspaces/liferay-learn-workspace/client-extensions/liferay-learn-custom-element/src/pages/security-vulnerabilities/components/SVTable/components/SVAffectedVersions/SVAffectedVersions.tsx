/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {ClayTooltipProvider} from '@clayui/tooltip';
import i18n from '~/utils/I18n';

import './SVAffectedVersions.scss';

type SVAffectedVersionsProps = {
	affectedVersions?: string[];
};

const LABEL_CLASS_NAME =
	'border-0 font-weight-semi-bold label px-2 py-1 sv-affected-version text-nowrap';

const SVAffectedVersions = ({affectedVersions}: SVAffectedVersionsProps) => {
	if (!affectedVersions?.length) {
		return null;
	}

	const [firstVersion, ...remainingVersions] = affectedVersions;

	return (
		<div>
			<span className={LABEL_CLASS_NAME}>{firstVersion}</span>

			{remainingVersions.length === 1 && (
				<span className={LABEL_CLASS_NAME}>{remainingVersions[0]}</span>
			)}

			{remainingVersions.length > 1 && (
				<ClayTooltipProvider>
					<span
						className={`${LABEL_CLASS_NAME} sv-affected-versions-remaining`}
						title={remainingVersions.join(', ')}
					>
						{i18n.sub('x-others', [
							String(remainingVersions.length),
						])}
					</span>
				</ClayTooltipProvider>
			)}
		</div>
	);
};

export default SVAffectedVersions;
