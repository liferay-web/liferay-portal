/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {useEffect, useState} from 'react';
import {getIssue} from '~/services/rest/jira';

import {JiraEnum} from '../utils/constants/JiraEnum';

export interface IJiraFields {
	[JiraEnum.AFFECTED_VERSIONS]?: string[];
	[JiraEnum.AFFECTS]?: string;
	[JiraEnum.CATEGORIES]?: string[];
	[JiraEnum.CVE_IDS]?: string;
	[JiraEnum.DESCRIPTION]?: string;
	[JiraEnum.FIX_VERSIONS]?: string[];
	[JiraEnum.ISSUE_CLASSIFICATION]?: string;
	[JiraEnum.PUBLISHED_DATE]?: string;
	[JiraEnum.SEVERITY]?: string;
	[JiraEnum.SUMMARY]?: string;
}
export interface IJiraIssue {
	[JiraEnum.FIELDS]?: IJiraFields;
	[JiraEnum.KEY]?: string;
}

const useJiraIssue = (issueKey?: string) => {
	const [jiraIssue, setJiraIssue] = useState<IJiraIssue | undefined>(
		undefined
	);
	const [error, setError] = useState(false);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!issueKey) {
			setError(false);
			setJiraIssue(undefined);
			setLoading(false);

			return;
		}

		let stale = false;

		const fetchJiraIssue = async () => {
			setLoading(true);

			let failed = false;
			let response: IJiraIssue | undefined;

			try {
				response = await getIssue(issueKey);
			}
			catch (error) {
				console.error('Error fetching Jira data:', error);

				failed = (error as {status?: number}).status !== 404;
			}

			if (stale) {
				return;
			}

			setError(failed);
			setJiraIssue(response);
			setLoading(false);
		};

		fetchJiraIssue();

		return () => {
			stale = true;
		};
	}, [issueKey]);

	return {error, jiraIssue, loading};
};

export default useJiraIssue;
