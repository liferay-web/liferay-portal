/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import DOMPurify from 'dompurify';
import {ReactNode} from 'react';
import {Link, useParams} from 'react-router-dom';
import {SVWaves} from '~/assets/SVWaves';
import useJiraIssue from '~/hooks/useJiraIssue';
import i18n from '~/utils/I18n';
import {JiraEnum} from '~/utils/constants/JiraEnum';

import SVSeverity from '../components/SVSeverity/SVSeverity';

import './SecurityVulnerabilitiesItem.scss';

type SVItemBadgesProps = {
	title: string;
	values: string[];
};

const SVItemBadges = ({title, values}: SVItemBadgesProps) => {
	if (!values.length) {
		return null;
	}

	return (
		<div className="mb-4">
			<h5 className="text-neutral-10">{title}</h5>

			<div className="c-gap-2 d-flex flex-wrap">
				{values.map((value) => (
					<span className="px-2 py-1 rounded sv-badge" key={value}>
						{value}
					</span>
				))}
			</div>
		</div>
	);
};

const SVItemMessage = ({children}: {children: ReactNode}) => (
	<div className="container-fluid container-xl py-4 sv-item">
		<Link to="/">{i18n.translate('all-security-reports')}</Link>

		<div className="mt-3">{children}</div>
	</div>
);

const SecurityVulnerabilitiesItem = () => {
	const {id} = useParams();

	const {error, jiraIssue, loading} = useJiraIssue(id);

	if (loading) {
		return (
			<SVItemMessage>
				<span
					className="cp-spinner spinner-border spinner-border-sm"
					role="status"
				></span>
			</SVItemMessage>
		);
	}

	if (error) {
		return (
			<SVItemMessage>
				{i18n.translate('an-unexpected-error-occurred')}
			</SVItemMessage>
		);
	}

	if (!jiraIssue) {
		return (
			<SVItemMessage>
				{i18n.translate('sorry-there-are-no-results-found')}
			</SVItemMessage>
		);
	}

	const fields = jiraIssue[JiraEnum.FIELDS] ?? {};

	const affects = Array.from(
		new Set(
			(fields[JiraEnum.AFFECTS] ?? '')
				.split(',')
				.map((affectedVersion) => affectedVersion.trim())
				.filter(Boolean)
		)
	);
	const classification = fields[JiraEnum.ISSUE_CLASSIFICATION];
	const summary = fields[JiraEnum.SUMMARY];
	const title = fields[JiraEnum.CVE_IDS] || jiraIssue[JiraEnum.KEY] || id;

	return (
		<div className="container-fluid container-xl py-4 sv-item">
			<div className="d-flex flex-column justify-content-between mb-3 p-4 position-relative sv-item-header">
				<div className="sv-breadcrumbs">
					<Link to="/">{i18n.translate('all-security-reports')}</Link>

					<span className="mx-2">/</span>

					{title}
				</div>

				<div className="align-items-center d-flex my-3">
					<h1 className="mb-0">{title}</h1>

					<SVSeverity
						className="ml-4"
						severity={fields[JiraEnum.SEVERITY]}
					/>
				</div>

				{summary && <span>{summary}</span>}

				<div
					aria-hidden="true"
					className="align-items-end d-flex justify-content-end position-absolute sv-gradient"
				>
					<SVWaves />
				</div>
			</div>

			<div className="mb-3 row">
				<div className="col-12 col-lg-9 col-md-8 col-xl-10">
					<h5 className="text-neutral-10">
						{i18n.translate('summary')}
					</h5>

					<div
						className="sv-structured-data"

						// XSS: the description is sanitized by DOMPurify.sanitize

						dangerouslySetInnerHTML={{
							__html: DOMPurify.sanitize(
								fields[JiraEnum.DESCRIPTION] ?? ''
							),
						}}
					/>
				</div>

				<div className="col-12 col-lg-3 col-md-4 col-xl-2">
					<div className="p-4 sv-item-details">
						<SVItemBadges
							title={i18n.translate('affects')}
							values={affects}
						/>

						<SVItemBadges
							title={i18n.translate('category')}
							values={fields[JiraEnum.CATEGORIES] ?? []}
						/>

						<SVItemBadges
							title={i18n.translate('classification')}
							values={classification ? [classification] : []}
						/>
					</div>
				</div>
			</div>
		</div>
	);
};

export default SecurityVulnerabilitiesItem;
