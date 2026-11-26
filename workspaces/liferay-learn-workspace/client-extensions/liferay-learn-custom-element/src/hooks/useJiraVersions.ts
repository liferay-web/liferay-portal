/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {useEffect, useState} from 'react';
import {getSecurityVulnerabilityAffectedVersions} from '~/services/rest/jira';

const useJiraVersions = () => {
	const [jiraVersions, setJiraVersions] = useState<string[] | undefined>(
		undefined
	);
	useEffect(() => {
		const fetchJiraVersions = async () => {
			try {
				const response =
					await getSecurityVulnerabilityAffectedVersions();

				setJiraVersions(response);
			}
			catch (error) {
				console.error('Error fetching Jira data:', error);
			}
		};

		fetchJiraVersions();
	}, []);

	return {jiraVersions};
};

export default useJiraVersions;
