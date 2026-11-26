/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Button as ClayButton} from '@clayui/core';
import {ClayCheckbox, ClayRadio} from '@clayui/form';
import ClayIcon from '@clayui/icon';
import classNames from 'classnames';
import {useEffect, useState} from 'react';
import {IJiraSearchParams} from '~/hooks/useJiraSearch';
import i18n from '~/utils/I18n';
import {JiraEnum} from '~/utils/constants/JiraEnum';
import {IFilterOptions} from '~/utils/constants/filterOptions';
import {IProps as ISortOptions} from '~/utils/constants/sortOptions';

import './SVFilter.scss';

interface IProps {
	defaultParams: IJiraSearchParams;
	filterOptions: IFilterOptions;
	onChange: (params: IJiraSearchParams) => void;
	params: URLSearchParams;
	sortOptions: ISortOptions;
}

const MOBILE_MEDIA_QUERY = '(max-width: 991.98px)';

const SVFilter = ({
	defaultParams,
	filterOptions,
	onChange,
	params,
	sortOptions,
}: IProps) => {
	const [expandedFilters, setExpandedFilters] = useState<{
		[key in keyof IFilterOptions]?: boolean;
	}>({});

	const [viewAllFilters, setViewAllFilters] = useState<{
		[key in keyof IFilterOptions]?: boolean;
	}>({});

	const [mobile, setMobile] = useState(
		() => window.matchMedia(MOBILE_MEDIA_QUERY).matches
	);

	useEffect(() => {
		const mediaQuery = window.matchMedia(MOBILE_MEDIA_QUERY);

		const handleChange = (event: MediaQueryListEvent) => {
			setMobile(event.matches);
		};

		mediaQuery.addEventListener('change', handleChange);

		return () => {
			mediaQuery.removeEventListener('change', handleChange);
		};
	}, []);

	const getSelectedValues = (param: keyof IFilterOptions) => {
		return params.get(param)?.split(',').filter(Boolean) ?? [];
	};

	const setFilterValues = (param: keyof IFilterOptions, values: string[]) => {
		onChange({
			[JiraEnum.FILTERS]: {
				[param]: values,
			},
		});
	};

	const toggleFilterValue = (param: keyof IFilterOptions, value: string) => {
		const selectedValues = getSelectedValues(param);

		setFilterValues(
			param,
			selectedValues.includes(value)
				? selectedValues.filter((item) => item !== value)
				: [...selectedValues, value]
		);
	};

	const handleSectionToggle = (
		filterKey: keyof IFilterOptions,
		expanded: boolean
	) => {
		setExpandedFilters((prevExpanded) => ({
			...prevExpanded,
			[filterKey]: expanded,
		}));
	};

	const handleViewAll = (filterKey: keyof IFilterOptions) => {
		setViewAllFilters((prevViewAll) => ({
			...prevViewAll,
			[filterKey]: !prevViewAll[filterKey],
		}));
	};

	const renderFilterSection = (
		filterKey: keyof IFilterOptions,
		languageKey: string
	) => {
		const isExpanded = expandedFilters[filterKey] ?? !mobile;

		const isViewAll = viewAllFilters[filterKey];

		const options = filterOptions[filterKey] ?? [];

		const displayedOptions = isViewAll ? options : options.slice(0, 8);

		const selectedValues = getSelectedValues(filterKey);

		return (
			<div
				className={classNames('mb-3 p-3 sv-filter-box', {
					'sv-filter-box-closed': !isExpanded,
				})}
			>
				<button
					aria-controls={`${filterKey}-options`}
					aria-expanded={isExpanded}
					className="align-items-center bg-transparent border-0 d-flex justify-content-between p-0 sv-filter-section-header w-100"
					onClick={() => handleSectionToggle(filterKey, !isExpanded)}
				>
					<h5 className="m-0">{i18n.translate(languageKey)}</h5>

					<ClayIcon symbol="angle-down" />
				</button>

				<div className="sv-filter-box-body" id={`${filterKey}-options`}>
					<div className="d-flex my-2">
						<ClayButton
							aria-label={i18n.translate('select-all')}
							className="font-weight-semi-bold mr-3 p-0 sv-link sv-select-all-button"
							displayType="link"
							onClick={() => setFilterValues(filterKey, options)}
						>
							{i18n.translate('select-all')}
						</ClayButton>

						<ClayButton
							aria-label={i18n.translate('clear')}
							className="font-weight-semi-bold p-0 sv-clear-button sv-link"
							displayType="link"
							onClick={() => setFilterValues(filterKey, [])}
						>
							{i18n.translate('clear')}
						</ClayButton>
					</div>

					{displayedOptions.map((value) => (
						<ClayCheckbox
							aria-label={i18n.translate(value)}
							checked={selectedValues.includes(value)}
							key={value}
							label={i18n.translate(value)}
							onChange={() => toggleFilterValue(filterKey, value)}
							value={value}
						/>
					))}

					{options.length > 8 && (
						<ClayButton
							aria-label={
								isViewAll
									? i18n.translate('view-less')
									: i18n.translate('view-all')
							}
							className="font-weight-semi-bold p-0 sv-link sv-view-all-button"
							displayType="link"
							onClick={() => handleViewAll(filterKey)}
						>
							{isViewAll
								? i18n.translate('view-less')
								: i18n.translate('view-all')}
						</ClayButton>
					)}
				</div>
			</div>
		);
	};

	const sortBy =
		params.get(JiraEnum.SORT_BY) || defaultParams[JiraEnum.SORT_BY];
	const sortOrder =
		params.get(JiraEnum.SORT_ORDER) || defaultParams[JiraEnum.SORT_ORDER];

	return (
		<div className="sv-filter-content">
			<div className="mb-3 p-3 sv-filter-box">
				<h5 className="pb-2">{i18n.translate('sort-by')}</h5>

				<div className="align-items-center justify-content-center">
					{sortOptions.sorts.map((sortOption) => (
						<ClayRadio
							aria-label={i18n.translate(sortOption.key)}
							checked={
								sortBy === sortOption[JiraEnum.SORT_BY] &&
								sortOrder === sortOption[JiraEnum.SORT_ORDER]
							}
							key={sortOption.key}
							label={i18n.translate(sortOption.key)}
							onChange={() => {
								onChange({
									[JiraEnum.SORT_BY]:
										sortOption[JiraEnum.SORT_BY],
									[JiraEnum.SORT_ORDER]:
										sortOption[JiraEnum.SORT_ORDER],
								});
							}}
							value={sortOption.key}
						/>
					))}
				</div>
			</div>

			{renderFilterSection(JiraEnum.SEVERITY, 'severity')}
			{renderFilterSection(JiraEnum.CATEGORIES, 'category')}
			{renderFilterSection(
				JiraEnum.ISSUE_CLASSIFICATION,
				'issue-classification'
			)}
			{renderFilterSection(
				JiraEnum.AFFECTED_VERSIONS,
				'affected-version'
			)}
		</div>
	);
};

export default SVFilter;
