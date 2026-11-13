/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.purge;

import com.liferay.cdn.cache.tag.CDNCacheTagBuilder;
import com.liferay.cdn.configuration.CDNConfiguration;
import com.liferay.cdn.internal.constants.CDNConstants;
import com.liferay.cdn.internal.messaging.CDNPurgeMessageUtil;
import com.liferay.cdn.internal.template.CDNTemplateRegistry;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.cdn.purge.CDNPurgeManager;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.change.tracking.constants.CTConstants;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.ShardedModel;
import com.liferay.portal.kernel.model.change.tracking.CTModel;
import com.liferay.portal.kernel.transaction.TransactionCallbackUtil;
import com.liferay.portal.kernel.util.NamedThreadFactory;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Sends purge requests to the company's CDN through a pipeline that
 * never blocks or fails the caller. A request is registered after the current
 * transaction commits, merged with the requests of the same company within the
 * coalesce window, split by the template's limits, and dispatched to a serial
 * message bus destination that calls the CDN and retries failures.
 *
 * <p>
 * The purge must wait for the commit. A purge sent from inside the publishing
 * transaction lets a guest request that reaches the origin before the commit
 * read the old data and cache the old page again for the whole edge TTL.
 * </p>
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(service = CDNPurgeManager.class)
public class CDNPurgeManagerImpl implements CDNPurgeManager {

	@Override
	public boolean isEnabled(long companyId) {
		try {
			ConfiguredCDNTemplate configuredCDNTemplate =
				_cdnTemplateRegistry.getConfiguredCDNTemplate(
					companyId,
					_cdnTemplateRegistry.getCDNConfiguration(companyId));

			if (configuredCDNTemplate != null) {
				return true;
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to check if the CDN is enabled for company " +
					companyId,
				exception);
		}

		return false;
	}

	@Override
	public void purge(BaseModel<?> baseModel) {

		// A model removed inside a publication still carries the production
		// collection ID, so the thread's collection must be checked as well.

		if (!CTCollectionThreadLocal.isProductionMode()) {
			return;
		}

		if (baseModel instanceof CTModel) {
			CTModel<?> ctModel = (CTModel<?>)baseModel;

			if (ctModel.getCtCollectionId() !=
					CTConstants.CT_COLLECTION_ID_PRODUCTION) {

				return;
			}
		}

		ShardedModel shardedModel = (ShardedModel)baseModel;

		long companyId = shardedModel.getCompanyId();

		if (!isEnabled(companyId)) {
			return;
		}

		try {
			Set<String> cacheTags = _cdnCacheTagBuilder.getPurgeCacheTags(
				baseModel);

			if (cacheTags.isEmpty()) {
				return;
			}

			purge(
				companyId,
				CDNPurgeRequest.builder(
				).tags(
					cacheTags
				).build());
		}
		catch (Exception exception) {
			_log.error(
				StringBundler.concat(
					"Unable to purge the CDN cache tags of ",
					baseModel.getModelClassName(), " ",
					baseModel.getPrimaryKeyObj()),
				exception);
		}
	}

	@Override
	public void purge(long companyId, CDNPurgeRequest cdnPurgeRequest) {
		if ((cdnPurgeRequest == null) || cdnPurgeRequest.isEmpty()) {
			return;
		}

		try {
			CDNConfiguration cdnConfiguration =
				_cdnTemplateRegistry.getCDNConfiguration(companyId);

			ConfiguredCDNTemplate configuredCDNTemplate =
				_cdnTemplateRegistry.getConfiguredCDNTemplate(
					companyId, cdnConfiguration);

			if (configuredCDNTemplate == null) {
				return;
			}

			CDNPurgeRequest coalescedCDNPurgeRequest = CDNPurgeRequest.builder(
			).purgeAll(
				cdnPurgeRequest.isPurgeAll()
			).soft(
				cdnPurgeRequest.isSoft() && cdnConfiguration.softPurge()
			).tags(
				cdnPurgeRequest.getTags()
			).urls(
				cdnPurgeRequest.getURLs()
			).build();

			TransactionCallbackUtil.registerCommitCallback(
				() -> {
					_cdnPurgeCoalescer.add(
						companyId, coalescedCDNPurgeRequest,
						cdnConfiguration.coalesceWindowMs());

					return null;
				});
		}
		catch (Exception exception) {
			_log.error(
				StringBundler.concat(
					"Unable to purge ", cdnPurgeRequest, " for company ",
					companyId),
				exception);
		}
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_cdnTemplateRegistry = new CDNTemplateRegistry(
			bundleContext, _configurationProvider);

		_scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
			new NamedThreadFactory(
				CDNPurgeManagerImpl.class.getName(), Thread.NORM_PRIORITY,
				CDNPurgeManagerImpl.class.getClassLoader()));

		_cdnPurgeCoalescer = new CDNPurgeCoalescer(
			this::_dispatch, _scheduledExecutorService);
	}

	@Deactivate
	protected void deactivate() {
		_cdnTemplateRegistry.close();

		_scheduledExecutorService.shutdownNow();
	}

	private void _dispatch(long companyId, CDNPurgeRequest cdnPurgeRequest) {
		try {
			ConfiguredCDNTemplate configuredCDNTemplate =
				_cdnTemplateRegistry.getConfiguredCDNTemplate(
					companyId,
					_cdnTemplateRegistry.getCDNConfiguration(companyId));

			if (configuredCDNTemplate == null) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						StringBundler.concat(
							"Skipping CDN purge ", cdnPurgeRequest,
							" because the CDN is disabled for company ",
							companyId));
				}

				return;
			}

			for (CDNPurgeRequest batchCDNPurgeRequest :
					CDNPurgeBatchUtil.getCDNPurgeRequests(
						cdnPurgeRequest, configuredCDNTemplate)) {

				_messageBus.sendMessage(
					CDNConstants.DESTINATION_NAME_CDN_PURGE,
					CDNPurgeMessageUtil.toMessage(
						0, companyId, batchCDNPurgeRequest,
						configuredCDNTemplate.getKey()));
			}
		}
		catch (Exception exception) {
			_log.error(
				StringBundler.concat(
					"Unable to dispatch CDN purge ", cdnPurgeRequest,
					" for company ", companyId),
				exception);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CDNPurgeManagerImpl.class);

	@Reference
	private CDNCacheTagBuilder _cdnCacheTagBuilder;

	private CDNPurgeCoalescer _cdnPurgeCoalescer;
	private CDNTemplateRegistry _cdnTemplateRegistry;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private MessageBus _messageBus;

	private ScheduledExecutorService _scheduledExecutorService;

}