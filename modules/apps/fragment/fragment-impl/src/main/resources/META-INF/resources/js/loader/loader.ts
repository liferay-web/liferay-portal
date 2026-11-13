/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

type Cleanup = () => void;

interface FragmentContext {
	configuration: Record<string, unknown>;
	fragmentElement: HTMLElement;
	fragmentElementId: string;
	fragmentNamespace: string;
	input?: Record<string, unknown>;
	layoutMode: string;
}

type Init = (context: FragmentContext) => Cleanup | void;

const INTERACTION_EVENTS = [
	'click',
	'focusin',
	'keydown',
	'pointerdown',
	'pointerover',
];

const SELECTOR = '[data-lfr-fragment-module]';

const cleanups = new Map<HTMLElement, Cleanup | null>();

let intersectionObserver: IntersectionObserver | null = null;

function getIntersectionObserver() {
	if (!intersectionObserver) {
		intersectionObserver = new IntersectionObserver(
			(entries) => {
				for (const entry of entries) {
					if (entry.isIntersecting) {
						intersectionObserver!.unobserve(entry.target);

						hydrate(entry.target as HTMLElement);
					}
				}
			},
			{rootMargin: '200px'}
		);
	}

	return intersectionObserver;
}

function getContext(fragmentElement: HTMLElement): FragmentContext {
	let context: Partial<FragmentContext> = {};

	try {
		context = JSON.parse(
			fragmentElement.dataset.lfrFragmentContext || '{}'
		);
	}
	catch (error) {
		console.error(error);
	}

	return {
		configuration: context.configuration || {},
		fragmentElement,
		fragmentElementId: fragmentElement.id,
		fragmentNamespace: context.fragmentNamespace || '',
		input: context.input,
		layoutMode: context.layoutMode || 'view',
	};
}

async function hydrate(
	fragmentElement: HTMLElement,
	onHydrated?: () => EventTarget | null
) {
	if (!fragmentElement.isConnected || cleanups.has(fragmentElement)) {
		return;
	}

	cleanups.set(fragmentElement, null);

	let replayClickTarget: EventTarget | null = null;

	try {
		const module = await import(
			/* webpackIgnore: true */ fragmentElement.dataset
				.lfrFragmentModule as string
		);

		const init: Init | undefined = module.default;

		if (typeof init !== 'function' || !fragmentElement.isConnected) {
			return;
		}

		const cleanup = init(getContext(fragmentElement));

		if (typeof cleanup === 'function') {
			cleanups.set(fragmentElement, cleanup);
		}

		fragmentElement.dataset.lfrFragmentHydrated = 'true';
	}
	catch (error) {
		console.error(error);
	}
	finally {

		// Held clicks are released even when the module fails, so that the
		// server rendered markup keeps its native behavior

		replayClickTarget = onHydrated?.() ?? null;
	}

	if (
		replayClickTarget instanceof HTMLElement &&
		replayClickTarget.isConnected
	) {
		replayClickTarget.click();
	}
}

function release(fragmentElement: HTMLElement) {
	const cleanup = cleanups.get(fragmentElement);

	cleanups.delete(fragmentElement);

	delete fragmentElement.dataset.lfrFragmentHydrated;

	intersectionObserver?.unobserve(fragmentElement);

	if (cleanup) {
		try {
			cleanup();
		}
		catch (error) {
			console.error(error);
		}
	}
}

function schedule(fragmentElement: HTMLElement) {
	if (fragmentElement.dataset.lfrFragmentScheduled) {
		return;
	}

	fragmentElement.dataset.lfrFragmentScheduled = 'true';

	const hydrateStrategy = fragmentElement.dataset.lfrHydrate;

	if (hydrateStrategy === 'idle') {
		if ('requestIdleCallback' in window) {
			requestIdleCallback(() => hydrate(fragmentElement), {
				timeout: 2000,
			});
		}
		else {
			setTimeout(() => hydrate(fragmentElement), 200);
		}
	}
	else if (hydrateStrategy === 'interaction') {
		// Hovering or pressing starts hydration, but the click that follows
		// usually arrives before the module is ready, so clicks are held and
		// the last one is replayed once the instance is hydrated

		let replayClickTarget: EventTarget | null = null;

		const onClick = (event: Event) => {
			event.preventDefault();
			event.stopPropagation();

			replayClickTarget = event.target;
		};

		const onInteraction = () => {
			for (const eventName of INTERACTION_EVENTS) {
				fragmentElement.removeEventListener(eventName, onInteraction);
			}

			hydrate(fragmentElement, () => {
				fragmentElement.removeEventListener('click', onClick, true);

				return replayClickTarget;
			});
		};

		// In the page editor clicks select fragments, so they are never held

		if (getContext(fragmentElement).layoutMode === 'view') {
			fragmentElement.addEventListener('click', onClick, true);
		}

		for (const eventName of INTERACTION_EVENTS) {
			fragmentElement.addEventListener(eventName, onInteraction, {
				passive: true,
			});
		}
	}
	else if (hydrateStrategy === 'visible') {
		getIntersectionObserver().observe(fragmentElement);
	}
	else {
		hydrate(fragmentElement);
	}
}

function scan(root: ParentNode) {
	if (root instanceof HTMLElement && root.matches(SELECTOR)) {
		schedule(root);
	}

	root.querySelectorAll<HTMLElement>(SELECTOR).forEach(schedule);
}

function sweep() {
	for (const fragmentElement of Array.from(cleanups.keys())) {
		if (!fragmentElement.isConnected) {
			delete fragmentElement.dataset.lfrFragmentScheduled;

			release(fragmentElement);
		}
	}
}

function start() {
	scan(document);

	new MutationObserver((mutations) => {
		let removed = false;

		for (const mutation of mutations) {
			mutation.addedNodes.forEach((node) => {
				if (node instanceof HTMLElement) {
					scan(node);
				}
			});

			removed = removed || mutation.removedNodes.length > 0;
		}

		if (removed) {
			sweep();
		}
	}).observe(document.documentElement, {childList: true, subtree: true});
}

if (!(window as any).__lfrFragmentLoader) {
	(window as any).__lfrFragmentLoader = true;

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', start, {once: true});
	}
	else {
		start();
	}
}

export {};
