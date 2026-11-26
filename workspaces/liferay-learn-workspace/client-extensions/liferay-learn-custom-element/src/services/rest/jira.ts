/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Liferay} from '~/services/liferay';

const OAUTH2_APP = 'liferay-learn-etc-spring-boot-oaua';
const BASE_PATH = '/jira';

async function fetch<T = any>(path: string): Promise<T> {
	const oauth2Client =
		Liferay.OAuth2Client.FromUserAgentApplication(OAUTH2_APP);

	const response = await oauth2Client.fetch(`${BASE_PATH}${path}`);

	if (!response.ok) {
		throw new Error(`Jira API error: ${response.statusText}`);
	}

	return response.json();
}

export async function getIssue(issueKey: string) {
	return fetch(`/issue/${issueKey}`);
}

export async function getSecurityVulnerabilityAffectedVersions(): Promise<
	string[]
> {
	return fetch('/security-vulnerabilities/affected-versions');
}

export async function searchSecurityVulnerabilities(queryString: string) {
	return fetch(`/security-vulnerabilities/search?${queryString}`);
}
