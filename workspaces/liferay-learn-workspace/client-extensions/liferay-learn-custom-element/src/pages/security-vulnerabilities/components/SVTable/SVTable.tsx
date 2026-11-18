/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {useMemo} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import Table, {IColumn, IRow} from '~/components/Table/Table';
import i18n from '~/utils/I18n';

import SVSeverity from '../SVSeverity/SVSeverity';
import SVAffectedVersions from './components/SVAffectedVersions/SVAffectedVersions';

import './SVTable.scss';

export interface ISVRow {
	affectedVersions: string[];
	category?: string;
	cveIds?: string;
	issueClassification?: string;
	link?: string;
	published?: string;
	severity?: string;
	summary?: string;
}

interface IProps {
	rows: ISVRow[];
}

const SVTable = ({rows}: IProps) => {
	const navigate = useNavigate();

	const columns: IColumn[] = [
		{
			className: 'sv-priority-summary-column',
			columnKey: 'prioritySummary',
			label: i18n.translate('priority-summary'),
		},
		{
			className: 'text-nowrap',
			columnKey: 'severity',
			label: i18n.translate('severity'),
		},
		{
			columnKey: 'category',
			label: i18n.translate('category'),
		},
		{
			columnKey: 'issueClassification',
			label: i18n.translate('classification'),
		},
		{
			columnKey: 'affectedVersion',
			label: i18n.translate('affected-version'),
		},
		{
			className: 'text-nowrap',
			columnKey: 'published',
			label: i18n.translate('published'),
		},
	];

	const tableRows = useMemo(
		() =>
			rows.map((row) => ({
				affectedVersion: (
					<SVAffectedVersions
						affectedVersions={row.affectedVersions}
					/>
				),
				category: row.category,
				issueClassification: row.issueClassification,
				link: row.link,
				prioritySummary: (
					<div>
						<Link
							className="font-weight-bold sv-name text-break text-wrap"
							onClick={(event) => event.stopPropagation()}
							to={row.link ?? ''}
						>
							{row.cveIds}
						</Link>

						<div className="sv-summary text-break text-neutral-8 text-wrap">
							{row.summary}
						</div>
					</div>
				),
				published: row.published,
				severity: (
					<SVSeverity
						className="font-weight-semi-bold"
						severity={row.severity}
					/>
				),
			})),
		[rows]
	);

	const handleRowClick = (row: IRow) => {
		if (row.link && !window.getSelection()?.toString()) {
			navigate(row.link);
		}
	};

	return (
		<Table
			className="sv"
			columns={columns}
			onRowClick={handleRowClick}
			rows={tableRows}
		/>
	);
};

export default SVTable;
