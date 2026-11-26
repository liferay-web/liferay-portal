/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import Button, {ClayButtonWithIcon} from '@clayui/button';
import ClayIcon from '@clayui/icon';
import classNames from 'classnames';
import React from 'react';
import {CSSTransition} from 'react-transition-group';

export interface IFilterNavigationItem {
	child?: React.ReactNode;
	className?: string;
	disabled?: boolean;
	onClick?: (event: React.MouseEvent) => void;
	symbol?: string;
	title?: string;
	type?: 'divider' | 'component' | 'item';
}

interface IItemProps {
	item: IFilterNavigationItem;
	onForward: (title: string, menuKey: string) => void;
}

interface IProps {
	active: boolean;
	direction: 'prev' | 'next';
	header?: string;
	items?: IFilterNavigationItem[];
	onBack: () => void;
	onForward: (title: string, menuKey: string) => void;
}

const FilterNavigationItem = ({item, onForward}: IItemProps) => {
	const {child, className, disabled, onClick, symbol, title, type} = item;

	if (type === 'divider') {
		return (
			<li
				aria-hidden="true"
				className="dropdown-divider"
				role="presentation"
			/>
		);
	}

	if (type === 'component') {
		return <li>{child}</li>;
	}

	if (type !== 'item' || !title) {
		return null;
	}

	const handleClick = (event: React.MouseEvent) => {
		onClick?.(event);

		if (typeof child === 'string') {
			onForward(title, child);
		}
	};

	return (
		<li>
			<Button
				className={classNames('dropdown-item', className)}
				disabled={disabled}
				displayType="unstyled"
				onClick={handleClick}
			>
				{symbol && (
					<span className="dropdown-item-indicator-start">
						<ClayIcon symbol={symbol} />
					</span>
				)}

				<span className="dropdown-item-indicator-text-end">
					{title}
				</span>

				{child && (
					<span className="dropdown-item-indicator-end">
						<ClayIcon symbol="angle-right" />
					</span>
				)}
			</Button>
		</li>
	);
};

const FilterNavigation = ({
	active,
	direction,
	header,
	items,
	onBack,
	onForward,
}: IProps) => {
	const initialClasses = classNames('transitioning', {
		'drilldown-prev-initial': direction === 'prev',
	});

	const transitionClasses = `drilldown-transition drilldown-${direction}-active`;

	return (
		<CSSTransition
			aria-hidden={!active}
			className={classNames('drilldown-item', {
				'drilldown-current': active,
			})}
			classNames={{
				enter: initialClasses,
				enterActive: transitionClasses,
				exit: initialClasses,
				exitActive: transitionClasses,
			}}
			in={active}
			timeout={250}
		>
			<div className="drilldown-item-inner">
				{header && (
					<div
						className="dropdown-header text-neutral-8"
						onClick={onBack}
					>
						<ClayButtonWithIcon
							aria-label={header}
							className="component-action dropdown-item-indicator-start text-neutral-2"
							symbol="angle-left-small"
						/>

						<span className="dropdown-item-indicator-text-start pl-3 text-capitalize text-neutral-8">
							{header}
						</span>
					</div>
				)}

				{items && (
					<ul className="inline-scroller">
						{items.map((item, index) => (
							<FilterNavigationItem
								item={item}
								key={index}
								onForward={onForward}
							/>
						))}
					</ul>
				)}
			</div>
		</CSSTransition>
	);
};

export default FilterNavigation;
