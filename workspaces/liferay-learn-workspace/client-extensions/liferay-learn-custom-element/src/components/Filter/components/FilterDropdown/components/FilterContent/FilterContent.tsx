/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayButton from '@clayui/button';
import {ClayCheckbox} from '@clayui/form';
import {useEffect, useState} from 'react';
import {IFilterOption, IFilterValue} from '~/components/Filter/Filter';
import i18n from '~/utils/I18n';

type FilterContentProps = {
	filter: IFilterOption;
	onChange: (selectedFilters: IFilterOption[]) => void;
	selectedFilters: IFilterOption[];
};

const getSelectedFilterValues = (
	filterKey: string,
	selectedFilters: IFilterOption[]
): IFilterValue[] => {
	const selectedFilter = selectedFilters.find(
		(selectedFilter) => selectedFilter.key === filterKey
	);

	return selectedFilter?.values ?? [];
};

const FilterContent = ({
	filter,
	onChange,
	selectedFilters,
}: FilterContentProps) => {
	const [selectedValues, setSelectedValues] = useState(() =>
		getSelectedFilterValues(filter.key, selectedFilters)
	);

	useEffect(() => {
		setSelectedValues(getSelectedFilterValues(filter.key, selectedFilters));
	}, [filter.key, selectedFilters]);

	const handleOnChange = (checked: boolean, value: IFilterValue) => {
		let values;

		if (checked) {
			values = [...selectedValues, value];
		}
		else {
			values = selectedValues.filter(
				(selectedValue) => selectedValue.key !== value.key
			);
		}

		setSelectedValues(values);
	};

	const onClick = () => {
		const filters = selectedFilters.filter(
			(selectedFilter) => selectedFilter.key !== filter.key
		);

		onChange(
			selectedValues.length
				? [...filters, {...filter, values: selectedValues}]
				: filters
		);
	};

	return (
		<div className="w-100">
			<div className="filter-content pt-2 px-3">
				{filter.values.map((value) => (
					<ClayCheckbox
						checked={selectedValues.some(
							(selectedValue) => selectedValue.key === value.key
						)}
						key={`${filter.key}-${value.key}`}
						label={i18n.translate(value.name)}
						onChange={(event) =>
							handleOnChange(event.target.checked, value)
						}
					/>
				))}
			</div>

			<div className="mb-3 mt-2 mx-3">
				<ClayButton className="w-100" onClick={onClick}>
					{i18n.translate('apply')}
				</ClayButton>
			</div>
		</div>
	);
};

export default FilterContent;
