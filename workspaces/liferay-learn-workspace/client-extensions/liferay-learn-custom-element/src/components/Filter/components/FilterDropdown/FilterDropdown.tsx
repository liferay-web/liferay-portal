/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayDropDown from '@clayui/drop-down';
import {useState} from 'react';

import FilterNavigation, {
	IFilterNavigationItem,
} from './components/FilterNavigation/FilterNavigation';

interface IProps {
	className?: string;
	initialActiveMenu: string;
	menus: Record<string, IFilterNavigationItem[]>;
	trigger: React.ReactElement;
}

const FilterDropdown = ({
	className,
	initialActiveMenu,
	menus,
	trigger,
}: IProps) => {
	const [active, setActive] = useState(false);
	const [activeMenu, setActiveMenu] = useState(initialActiveMenu);
	const [direction, setDirection] = useState<'prev' | 'next'>('next');
	const [history, setHistory] = useState<{id: string; title: string}[]>([]);

	const parentMenu = history[history.length - 1];

	const handleBack = () => {
		if (!parentMenu) {
			return;
		}

		setActiveMenu(parentMenu.id);
		setDirection('next');
		setHistory(history.slice(0, -1));
	};

	const handleForward = (title: string, menuKey: string) => {
		setActiveMenu(menuKey);
		setDirection('prev');
		setHistory([...history, {id: activeMenu, title}]);
	};

	return (
		<ClayDropDown
			active={active}
			className={className}
			hasRightSymbols
			menuElementAttrs={{
				className: 'drilldown drop-down-menu-items p-0',
			}}
			onActiveChange={setActive}
			trigger={trigger}
		>
			<div>
				{Object.entries(menus).map(([menuKey, items]) => (
					<FilterNavigation
						active={activeMenu === menuKey}
						direction={direction}
						header={
							activeMenu === menuKey
								? parentMenu?.title
								: undefined
						}
						items={items}
						key={menuKey}
						onBack={handleBack}
						onForward={handleForward}
					/>
				))}
			</div>
		</ClayDropDown>
	);
};

export default FilterDropdown;
