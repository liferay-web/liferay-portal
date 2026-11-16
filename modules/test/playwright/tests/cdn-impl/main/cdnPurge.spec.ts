/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {expect, mergeTests, request} from '@playwright/test';
import {Server, createServer} from 'http';
import {AddressInfo} from 'net';

import {dataApiHelpersTest} from '../../../fixtures/dataApiHelpersTest';
import {featureFlagsTest} from '../../../fixtures/featureFlagsTest';
import {isolatedSiteTest} from '../../../fixtures/isolatedSiteTest';
import {loginTest} from '../../../fixtures/loginTest';
import {liferayConfig} from '../../../liferay.config';
import getRandomString from '../../../utils/getRandomString';
import getBasicWebContentStructureId from '../../../utils/structured-content/getBasicWebContentStructureId';
import getPageDefinition from '../../layout-content-page-editor-web/main/utils/getPageDefinition';
import getWidgetDefinition from '../../layout-content-page-editor-web/main/utils/getWidgetDefinition';

const CDN_CONFIGURATION_URL =
	'/o/headless-admin-configuration/v1.0/instance-configurations/com.liferay.cdn.configuration.CDNConfiguration';

const WEB_CONTENT_DISPLAY_WIDGET =
	'com_liferay_journal_content_web_portlet_JournalContentPortlet';

const test = mergeTests(
	dataApiHelpersTest,
	featureFlagsTest({
		'LPD-65399': {enabled: true},
		'LPD-107638': {enabled: true},
		'LPS-155284': {enabled: true},
		'LPS-178052': {enabled: true},
	}),
	isolatedSiteTest,
	loginTest()
);

/**
 * Starts a fake CDN API that records the body of every purge request. The
 * portal sends the purges, so it must reach this process on localhost.
 */
async function startFakeCDN(): Promise<{
	bodies: {tags: string[]; urls: string[]}[];
	server: Server;
	url: string;
}> {
	const bodies = [];

	const server = createServer((incomingMessage, serverResponse) => {
		let body = '';

		incomingMessage.on('data', (chunk) => {
			body += chunk;
		});

		incomingMessage.on('end', () => {
			bodies.push(JSON.parse(body));

			serverResponse.writeHead(200);
			serverResponse.end();
		});
	});

	await new Promise<void>((resolve) =>
		server.listen(0, '127.0.0.1', resolve)
	);

	const {port} = server.address() as AddressInfo;

	return {bodies, server, url: `http://127.0.0.1:${port}/purge`};
}

test(
	'Publishing a web content shown on a page purges its cache tag',
	{tag: '@LPD-115753'},
	async ({apiHelpers, site}) => {
		const fakeCDN = await startFakeCDN();

		const originalConfiguration = await apiHelpers.get(
			`${liferayConfig.environment.baseUrl}${CDN_CONFIGURATION_URL}`
		);

		try {
			await apiHelpers.put(
				`${liferayConfig.environment.baseUrl}${CDN_CONFIGURATION_URL}`,
				{
					data: {
						externalReferenceCode:
							'com.liferay.cdn.configuration.CDNConfiguration',
						properties: {
							coalesceWindowMs: 100,
							emitCacheTags: true,
							enabled: true,
							endpoint: fakeCDN.url,
							templateKey: 'custom',
						},
					},
					failOnStatusCode: true,
				}
			);

			// Show a web content on a page

			const webContent =
				await apiHelpers.headlessDelivery.postStructuredContent({
					contentStructureId:
						await getBasicWebContentStructureId(apiHelpers),
					datePublished: null,
					siteId: site.id,
					title: getRandomString(),
					viewableBy: 'Anyone',
				});

			const layout = await apiHelpers.headlessDelivery.createSitePage({
				pageDefinition: getPageDefinition([
					getWidgetDefinition({
						id: getRandomString(),
						widgetConfig: {
							articleId: webContent.key,
							groupId: String(site.id),
						},
						widgetName: WEB_CONTENT_DISPLAY_WIDGET,
					}),
				]),
				siteId: site.id,
				title: getRandomString(),
			});

			const webContentCacheTag = `lfr-wc${webContent.id}`;

			// The guest page carries the web content's cache tag

			const guestRequestContext = await request.newContext({
				baseURL: liferayConfig.environment.baseUrl,
			});

			try {
				const response = await guestRequestContext.get(
					`/web${site.friendlyUrlPath}${layout.friendlyUrlPath}`
				);

				expect(response.ok()).toBeTruthy();
				expect(response.headers()['cache-tag'].split(',')).toContain(
					webContentCacheTag
				);
			}
			finally {
				await guestRequestContext.dispose();
			}

			// Publishing a new version purges the web content's cache tag

			fakeCDN.bodies.length = 0;

			await apiHelpers.patch(
				`${liferayConfig.environment.baseUrl}/o/headless-delivery/v1.0/structured-contents/${webContent.id}`,
				{title: getRandomString()}
			);

			await expect
				.poll(() => fakeCDN.bodies.flatMap((body) => body.tags), {
					timeout: 30000,
				})
				.toContain(webContentCacheTag);
		}
		finally {
			await apiHelpers.put(
				`${liferayConfig.environment.baseUrl}${CDN_CONFIGURATION_URL}`,
				{
					data: {
						externalReferenceCode:
							'com.liferay.cdn.configuration.CDNConfiguration',
						properties: originalConfiguration?.properties ?? {
							enabled: false,
						},
					},
				}
			);

			await new Promise((resolve) => fakeCDN.server.close(resolve));
		}
	}
);
