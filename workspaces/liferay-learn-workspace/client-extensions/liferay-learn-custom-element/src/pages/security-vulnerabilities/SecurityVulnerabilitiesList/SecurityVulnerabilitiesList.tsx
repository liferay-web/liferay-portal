/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {ClayPaginationBarWithBasicItems} from '@clayui/pagination-bar';
import {useMemo} from 'react';
import CloudLockIcon from '~/assets/CloudLock';
import {SVWaves} from '~/assets/SVWaves';
import useJiraSearch, {IJiraSearchParams} from '~/hooks/useJiraSearch';
import useJiraVersions from '~/hooks/useJiraVersions';
import i18n from '~/utils/I18n';
import {JiraEnum} from '~/utils/constants/JiraEnum';
import {FILTER_OPTIONS} from '~/utils/constants/filterOptions';
import {
	paginationDeltas,
	paginationLabels,
} from '~/utils/constants/paginationOptions';
import {SORT_OPTIONS} from '~/utils/constants/sortOptions';
import {getFormattedDate} from '~/utils/getFormattedDate';

import SVFilter from '../components/SVFilter/SVFilter';
import SVHeader from '../components/SVHeader/SVHeader';
import SVList from '../components/SVList/SVList';
import SVSearch from '../components/SVSearch/SVSearch';
import SVTable, {ISVRow} from '../components/SVTable/SVTable';

import './SecurityVulnerabilitiesList.scss';

const DEFAULT_PARAMS: IJiraSearchParams = {
	[JiraEnum.PAGE]: 1,
	[JiraEnum.PAGE_SIZE]: 15,
	[JiraEnum.SORT_BY]: JiraEnum.PUBLISHED_DATE,
	[JiraEnum.SORT_ORDER]: 'desc',
};

const SecurityVulnerabilitiesList = () => {
	const {error, jiraSearch, loading, searchParams, updateSearchParams} =
		useJiraSearch(DEFAULT_PARAMS);

	const {jiraVersions} = useJiraVersions();

	const rows = useMemo<ISVRow[] | undefined>(() => {
		const issues = jiraSearch?.[JiraEnum.ISSUES];

		if (!issues) {
			return undefined;
		}

		return issues.map((issue) => {
			const fields = issue[JiraEnum.FIELDS];
			const key = issue[JiraEnum.KEY];

			return {
				affectedVersions: fields?.[JiraEnum.AFFECTED_VERSIONS] ?? [],
				category: fields?.[JiraEnum.CATEGORIES]?.join(', '),
				cveIds: fields?.[JiraEnum.CVE_IDS],
				issueClassification: fields?.[JiraEnum.ISSUE_CLASSIFICATION],
				link: key ? `/${key}` : undefined,
				published: getFormattedDate(
					fields?.[JiraEnum.PUBLISHED_DATE],
					'day2DMonthSYearN'
				),
				severity: fields?.[JiraEnum.SEVERITY],
				summary: fields?.[JiraEnum.SUMMARY],
			};
		});
	}, [jiraSearch]);

	return (
		<div className="sv-list">
			<div className="align-items-start d-flex flex-column justify-content-center mb-5 overflow-hidden pb-6 position-relative pt-5 sv-list-header text-left">
				<div className="container-fluid container-fluid-max-xl px-4 sv-search-container">
					<SVHeader
						icon={<CloudLockIcon className="cloud-lock-icon" />}
						label={i18n.translate('updates')}
						title="security-advisories"
					>
						<div className="sv-header-description text-white">
							{i18n.translate(
								'welcome-to-the-liferay-security-advisories-dashboard-use-this-tool-to-search-and-monitor-documented-security-vulnerabilities-cve-identifiers-and-classification-statuses-across-liferay-products-including-dxp-paas-and-saas-for-detailed-information-regarding-our-triage-protocols-patching-timelines-or-to-responsibly-report-a-security-issue-please-visit-the-official-liferay-security-statement'
							)}
						</div>

						<SVSearch
							keywords={searchParams.get(JiraEnum.KEYWORDS) || ''}
							onChange={(keywords) =>
								updateSearchParams({
									[JiraEnum.KEYWORDS]: keywords,
									[JiraEnum.PAGE]: 1,
								})
							}
						/>
					</SVHeader>
				</div>

				<div className="align-items-end d-flex justify-content-end position-absolute sv-gradient">
					<SVWaves />
				</div>
			</div>

			<div className="container-fluid container-fluid-max-xl px-4 sv-body-container">
				<div className="row sv-table-content">
					<div className="col-12 col-lg-3 sv-filter-column">
						<SVFilter
							defaultParams={DEFAULT_PARAMS}
							filterOptions={{
								...FILTER_OPTIONS,
								[JiraEnum.AFFECTED_VERSIONS]: jiraVersions,
							}}
							onChange={(params) =>
								updateSearchParams({
									...params,
									[JiraEnum.PAGE]: 1,
								})
							}
							params={searchParams}
							sortOptions={SORT_OPTIONS}
						/>
					</div>

					<div className="col-12 col-lg-9 sv-results-column">
						{loading ? (
							<span
								className="cp-spinner ml-2 spinner-border spinner-border-sm"
								role="status"
							/>
						) : error ? (
							<div className="py-2">
								{i18n.translate('an-unexpected-error-occurred')}
							</div>
						) : rows?.length ? (
							<>
								<div className="d-lg-block d-none">
									<SVTable rows={rows} />
								</div>

								<div className="d-lg-none mb-4">
									<SVList rows={rows} />
								</div>

								<ClayPaginationBarWithBasicItems
									active={jiraSearch?.[JiraEnum.PAGE]}
									activeDelta={
										jiraSearch?.[JiraEnum.PAGE_SIZE]
									}
									deltas={paginationDeltas}
									labels={paginationLabels}
									onActiveChange={(page: number) =>
										updateSearchParams({
											[JiraEnum.PAGE]: page,
										})
									}
									onDeltaChange={(pageSize: number) =>
										updateSearchParams({
											[JiraEnum.PAGE]: 1,
											[JiraEnum.PAGE_SIZE]: pageSize,
										})
									}
									totalItems={
										jiraSearch?.[JiraEnum.TOTAL] ?? 0
									}
								/>
							</>
						) : (
							<div className="py-2">
								{i18n.translate(
									'the-requested-search-does-not-exist-in-our-database-please-try-again-with-different-criteria'
								)}
							</div>
						)}
					</div>
				</div>
			</div>
		</div>
	);
};

export default SecurityVulnerabilitiesList;
