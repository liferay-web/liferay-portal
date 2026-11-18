/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Link, useNavigate} from 'react-router-dom';
import i18n from '~/utils/I18n';

import SVSeverity from '../SVSeverity/SVSeverity';
import {ISVRow} from '../SVTable/SVTable';

import './SVList.scss';

const NAME_CLASS_NAME = 'font-weight-bold sv-name text-break text-wrap';

const getAffectedVersionsLabel = (affectedVersions: string[] = []) => {
	const [firstVersion, ...remainingVersions] = affectedVersions;

	if (remainingVersions.length > 1) {
		return `${firstVersion}, ${i18n.sub('x-others', [
			String(remainingVersions.length),
		])}`;
	}

	return affectedVersions.join(', ');
};

const SVListItemContent = ({row}: {row: ISVRow}) => {
	const affectedVersionsLabel = getAffectedVersionsLabel(
		row.affectedVersions
	);

	return (
		<>
			<div className="d-flex justify-content-between">
				<div className="align-items-center d-flex">
					{row.link ? (
						<Link
							className={NAME_CLASS_NAME}
							onClick={(event) => event.stopPropagation()}
							to={row.link}
						>
							{row.cveIds}
						</Link>
					) : (
						<span className={NAME_CLASS_NAME}>{row.cveIds}</span>
					)}

					<SVSeverity className="ml-2" severity={row.severity} />
				</div>

				<div className="ml-2 text-nowrap">{row.published}</div>
			</div>

			<div className="sv-summary text-break text-wrap">{row.summary}</div>

			<div className="sv-list-item-details">
				<div>
					<span className="mr-1 sv-list-item-label">
						{i18n.translate('category')}:
					</span>

					{row.category}
				</div>

				<div>
					<span className="mr-1 sv-list-item-label">
						{i18n.translate('classification')}:
					</span>

					{row.issueClassification}
				</div>

				{affectedVersionsLabel && (
					<div>
						<span className="mr-1 sv-list-item-label">
							{i18n.translate('affected-versions')}:
						</span>

						{affectedVersionsLabel}
					</div>
				)}
			</div>
		</>
	);
};

interface IProps {
	rows: ISVRow[];
}

const SVList = ({rows}: IProps) => {
	const navigate = useNavigate();

	const handleItemClick = (link?: string) => {
		if (link && !window.getSelection()?.toString()) {
			navigate(link);
		}
	};

	return (
		<ul className="list-unstyled mb-0 sv-list-items">
			{rows.map((row, index) => (
				<li
					className="sv-list-item"
					key={row.link ?? index}
					onClick={() => handleItemClick(row.link)}
				>
					<SVListItemContent row={row} />
				</li>
			))}
		</ul>
	);
};

export default SVList;
