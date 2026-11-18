/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Liferay} from '~/services/liferay';

import en_US from './Language.json';
import es_ES from './Language_es_ES.json';
import ja_JP from './Language_ja_JP.json';
import pt_BR from './Language_pt_BR.json';

type Languages = {[key: string]: {[key: string]: string}};

export const languages: Languages = {
	en_US,
	es_ES,
	ja_JP,
	pt_BR,
};

const translate = (key: string) => {
	const languageId = Liferay.ThemeDisplay.getLanguageId().toString();

	const languageEntry = languages[languageId] || languages.en_US;

	return languageEntry[key] || languages.en_US[key] || key;
};

const sub = (key: string, subValues: string[]) => {
	let value = translate(key);

	subValues.forEach((subValue, index) => {
		value = value.replaceAll(`{${index}}`, subValue);
	});

	return value;
};

const i18n = {
	sub,
	translate,
};

export default i18n;
