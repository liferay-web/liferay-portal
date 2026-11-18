/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {ClayButtonWithIcon} from '@clayui/button';
import {ClayInput} from '@clayui/form';
import classNames from 'classnames';
import {memo, useState} from 'react';
import i18n from '~/utils/I18n';

type SearchBarProps = {
	isBusinessEvent: boolean;
	onSearchSubmit: (searchTerm: string) => void;
};

const SearchBar = ({isBusinessEvent, onSearchSubmit}: SearchBarProps) => {
	const [term, setTerm] = useState('');
	const [loading, setLoading] = useState(true);

	const handleSearchSubmit = () => {
		if (loading) {
			onSearchSubmit(term);
			setLoading(false);

			return;
		}

		setTerm('');
		onSearchSubmit('');
		setLoading(true);
	};

	return (
		<ClayInput.Group
			className={classNames('m-0 mr-2', {
				'rounded shadow-lg': isBusinessEvent,
			})}
		>
			<ClayInput.GroupItem>
				<ClayInput
					className={classNames(
						'form-control input-group-inset input-group-inset-after',
						{
							'border-brand-primary-lighten-5 font-weight-semi-bold':
								isBusinessEvent,
						}
					)}
					onChange={(event) => {
						setTerm(event.target.value);
						setLoading(true);
					}}
					onKeyPress={(event) => {
						if (event.key === 'Enter') {
							handleSearchSubmit();
						}
					}}
					placeholder={
						isBusinessEvent
							? i18n.translate('search-event-name')
							: i18n.translate('search')
					}
					type="text"
					value={term}
				/>

				<ClayInput.GroupInsetItem
					after
					className={classNames({
						'border-brand-primary-lighten-5': isBusinessEvent,
					})}
					tag="span"
				>
					{loading || !term ? (
						<ClayButtonWithIcon
							aria-label={i18n.translate('search')}
							displayType="unstyled"
							onClick={() => handleSearchSubmit()}
							symbol="search"
						/>
					) : (
						<ClayButtonWithIcon
							aria-label={i18n.translate('clear')}
							className="navbar-breakpoint-d-none"
							displayType="unstyled"
							onClick={() => handleSearchSubmit()}
							symbol="times"
						/>
					)}
				</ClayInput.GroupInsetItem>
			</ClayInput.GroupItem>
		</ClayInput.Group>
	);
};

export default memo(SearchBar);
