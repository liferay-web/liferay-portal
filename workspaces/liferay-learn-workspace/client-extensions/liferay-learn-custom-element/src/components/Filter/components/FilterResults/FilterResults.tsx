/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import BadgeButton from '~/components/BadgeButton/BadgeButton';
import Button from '~/components/Button/Button';
import i18n from '~/utils/I18n';

import {IFilterOption} from '../../Filter';

interface IProps {
	onChange: (selectedFilters: IFilterOption[]) => void;
	searchResultsCount: number;
	searchTerm: string;
	selectedFilters: IFilterOption[];
}

const FilterResults = ({
	onChange,
	searchResultsCount,
	searchTerm,
	selectedFilters,
}: IProps) => {
	const activeFilters = selectedFilters.filter(
		(option) => option.values.length
	);

	const handleClearFilter = (filterKey: string) => {
		onChange(selectedFilters.filter((option) => option.key !== filterKey));
	};

	const handleClearAllFilters = () => {
		onChange([]);
	};

	return (
		<>
			{searchTerm !== '' && (
				<p className="font-weight-semi-bold m-0 mt-3 text-paragraph-sm">
					{i18n.sub(
						searchResultsCount === 1
							? 'x-result-for-x'
							: 'x-results-for-x',
						[searchResultsCount.toString(), `"${searchTerm}"`]
					)}
				</p>
			)}

			{!!activeFilters.length && (
				<div className="d-flex">
					<div className="col d-flex flex-wrap pl-0 pt-2">
						{activeFilters.map((option) => (
							<BadgeButton
								filterName={i18n.translate(option.name)}
								filterValue={option.values
									.map((value) => i18n.translate(value.name))
									.join(', ')}
								key={option.key}
								onClick={() => handleClearFilter(option.key)}
							/>
						))}
					</div>

					<div className="pt-2">
						<Button
							borderless
							className="link"
							onClick={handleClearAllFilters}
							prependIcon="times-circle"
							small
						>
							{i18n.translate('clear-all-filters')}
						</Button>
					</div>
				</div>
			)}
		</>
	);
};

export default FilterResults;
