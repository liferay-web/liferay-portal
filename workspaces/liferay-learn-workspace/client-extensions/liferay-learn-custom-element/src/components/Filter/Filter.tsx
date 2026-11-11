/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import Button from '~/components/Button/Button';
import i18n from '~/utils/I18n';

import FilterDropdown from './components/FilterDropdown/FilterDropdown';
import FilterContent from './components/FilterDropdown/components/FilterContent/FilterContent';
import {IFilterNavigationItem} from './components/FilterDropdown/components/FilterNavigation/FilterNavigation';

export interface IFilterValue {
	key: string;
	name: string;
}

export interface IFilterOption {
	key: string;
	name: string;
	values: IFilterValue[];
}

type FilterProps = {
	availableFilters: IFilterOption[];
	onChange: (selectedFilters: IFilterOption[]) => void;
	selectedFilters: IFilterOption[];
};

const ROOT_MENU_KEY = 'root';

const getFilterMenuKey = (filter: IFilterOption) => `filter-${filter.key}`;

const Filter = ({availableFilters, onChange, selectedFilters}: FilterProps) => {
	if (!availableFilters.length) {
		return null;
	}

	const menus: Record<string, IFilterNavigationItem[]> = {
		[ROOT_MENU_KEY]: availableFilters.map((filter) => ({
			child: getFilterMenuKey(filter),
			title: i18n.translate(filter.name),
			type: 'item',
		})),
		...Object.fromEntries(
			availableFilters.map((filter) => [
				getFilterMenuKey(filter),
				[
					{
						child: (
							<FilterContent
								filter={filter}
								onChange={onChange}
								selectedFilters={selectedFilters}
							/>
						),
						type: 'component',
					},
				],
			])
		),
	};

	return (
		<FilterDropdown
			className="align-items-center d-flex"
			initialActiveMenu={ROOT_MENU_KEY}
			menus={menus}
			trigger={
				<Button
					borderless
					className="text-neutral-10"
					prependIcon="filter"
				>
					{i18n.translate('filters')}
				</Button>
			}
		/>
	);
};

export default Filter;
