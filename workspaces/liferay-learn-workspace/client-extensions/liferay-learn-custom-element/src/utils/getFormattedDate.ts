/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Liferay} from '~/services/liferay';

import {FORMAT_DATE_TYPES} from './constants/formatDateTypes';

export function getFormattedDate(
	dateString: string | undefined,
	formatType: keyof typeof FORMAT_DATE_TYPES,
	timeZone?: string
): string {
	if (!dateString) {
		return '';
	}

	try {
		const date = new Date(dateString);

		if (isNaN(date.getTime())) {
			return 'Invalid Date';
		}

		const dateTimeFormatOptions: Intl.DateTimeFormatOptions =
			FORMAT_DATE_TYPES[formatType];

		if (timeZone) {
			dateTimeFormatOptions.timeZone = timeZone;
		}

		return date.toLocaleDateString(
			Liferay.ThemeDisplay.getBCP47LanguageId(),
			dateTimeFormatOptions
		);
	}
	catch (error) {
		console.error('Error parsing date:', error);

		return 'Invalid Date';
	}
}
