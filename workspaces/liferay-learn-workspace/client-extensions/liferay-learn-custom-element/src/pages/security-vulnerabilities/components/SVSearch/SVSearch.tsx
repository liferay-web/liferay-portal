/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {ClayInput} from '@clayui/form';
import ClayIcon from '@clayui/icon';
import {useEffect, useRef, useState} from 'react';
import i18n from '~/utils/I18n';

import './SVSearch.scss';

type SVSearchProps = {
	keywords?: string;
	onChange: (keywords: string) => void;
};

const SVSearch = ({keywords = '', onChange}: SVSearchProps) => {
	const [value, setValue] = useState(keywords);

	const timeoutRef = useRef<ReturnType<typeof setTimeout>>();

	useEffect(() => {
		clearTimeout(timeoutRef.current);

		setValue(keywords);
	}, [keywords]);

	useEffect(() => {
		return () => clearTimeout(timeoutRef.current);
	}, []);

	const handleChange = (newValue: string) => {
		setValue(newValue);

		clearTimeout(timeoutRef.current);

		timeoutRef.current = setTimeout(() => {
			onChange(newValue);
		}, 500);
	};

	return (
		<div className="flex-grow-1 position-relative sv-search">
			<ClayInput
				aria-label={i18n.translate(
					'search-for-a-security-vulnerability-by-keyword-or-cve-id'
				)}
				className="border border-brand-primary-lighten-4 font-weight-semi-bold px-5 py-3 rounded-pill shadow-lg sv-search-input w-100"
				onChange={(event) => handleChange(event.target.value)}
				placeholder={i18n.translate(
					'search-for-a-security-vulnerability-by-keyword-or-cve-id'
				)}
				type="text"
				value={value}
			/>

			<ClayIcon
				className="position-absolute sv-search-icon text-brand-primary"
				symbol="search"
			/>
		</div>
	);
};

export default SVSearch;
