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
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.layout.display.page.LayoutDisplayPageProviderRegistry;
import com.liferay.layout.service.LayoutClassedModelUsageLocalService;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.function.UnsafeSupplier;
import com.liferay.petra.lang.CentralizedThreadLocal;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.cache.thread.local.Lifecycle;
import com.liferay.portal.kernel.cache.thread.local.ThreadLocalCacheManager;
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.ShardedModel;
import com.liferay.portal.kernel.model.change.tracking.CTModel;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.LayoutSetLocalService;
import com.liferay.portal.kernel.transaction.TransactionCallbackUtil;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.NamedThreadFactory;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;

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
		_purge(
			baseModel, () -> _cdnCacheTagBuilder.getPurgeCacheTags(baseModel),
			baseModel);
	}

	@Override
	public void purge(BaseModel<?> originalBaseModel, BaseModel<?> baseModel) {
		BaseModel<?> currentBaseModel = baseModel;

		if (currentBaseModel == null) {
			currentBaseModel = originalBaseModel;
		}

		if (currentBaseModel == null) {
			return;
		}

		_purge(
			currentBaseModel,
			() -> _cdnCacheTagBuilder.getPurgeCacheTags(
				originalBaseModel, baseModel),
			originalBaseModel, baseModel);
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

		_cdnPurgeURLResolver = new CDNPurgeURLResolver(
			_companyLocalService, _fragmentEntryLinkLocalService,
			_fragmentEntryLocalService, _groupLocalService, _language,
			_layoutClassedModelUsageLocalService,
			_layoutDisplayPageProviderRegistry, _layoutLocalService,
			_layoutSetLocalService, _objectDefinitionLocalService,
			_objectEntryLocalService, _portal,
			Http.HTTPS.equals(PropsUtil.get(PropsKeys.WEB_SERVER_PROTOCOL)));
	}

	@Deactivate
	protected void deactivate() {
		_cdnTemplateRegistry.close();

		_scheduledExecutorService.shutdownNow();
	}

	/**
	 * Dispatches the purge from the coalescer's thread. The thread lives as
	 * long as the component, so it drops its request scoped caches after each
	 * dispatch, as message bus threads do. Otherwise, the next dispatch could
	 * read a stale company configuration.
	 */
	private void _dispatch(long companyId, CDNPurgeRequest cdnPurgeRequest) {
		try {
			CDNConfiguration cdnConfiguration =
				_cdnTemplateRegistry.getCDNConfiguration(companyId);

			ConfiguredCDNTemplate configuredCDNTemplate =
				_cdnTemplateRegistry.getConfiguredCDNTemplate(
					companyId, cdnConfiguration);

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
						_getCDNPurgeRequest(
							companyId, cdnConfiguration, cdnPurgeRequest,
							configuredCDNTemplate),
						configuredCDNTemplate)) {

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
		finally {
			ThreadLocalCacheManager.clearAll(Lifecycle.REQUEST);

			CentralizedThreadLocal.clearShortLivedCentralizedThreadLocals();
		}
	}

	/**
	 * Returns the request to send to the CDN. When the CDN purges by URL only,
	 * the request's cache tags are converted to URLs. The conversion runs here,
	 * after the commit and outside the caller's thread, because it may read
	 * many pages.
	 */
	private CDNPurgeRequest _getCDNPurgeRequest(
		long companyId, CDNConfiguration cdnConfiguration,
		CDNPurgeRequest cdnPurgeRequest,
		ConfiguredCDNTemplate configuredCDNTemplate) {

		Set<String> tags = cdnPurgeRequest.getTags();

		if (tags.isEmpty() || !_isURLPurge(configuredCDNTemplate)) {
			return cdnPurgeRequest;
		}

		return _cdnPurgeURLResolver.getURLCDNPurgeRequest(
			companyId, cdnConfiguration.cacheTagNamespace(), cdnPurgeRequest,
			configuredCDNTemplate.getPurgeAllCDNHTTPRequestTemplate() != null);
	}

	/**
	 * Returns whether the CDN purges by URL only, so cache tags must be
	 * converted to URLs.
	 */
	private boolean _isURLPurge(ConfiguredCDNTemplate configuredCDNTemplate) {
		if ((configuredCDNTemplate.getTagPurgeCDNHTTPRequestTemplate() ==
				null) &&
			(configuredCDNTemplate.getURLPurgeCDNHTTPRequestTemplate() !=
				null)) {

			return true;
		}

		return false;
	}

	/**
	 * Purges the cache tags of the model. When the CDN purges by URL, the
	 * URLs of the pages among the models are read now, because a removed page
	 * cannot be read once the transaction commits, and a page whose friendly
	 * URL changed is still cached under the original friendly URL.
	 */
	private void _purge(
		BaseModel<?> baseModel,
		UnsafeSupplier<Set<String>, Exception> cacheTagsUnsafeSupplier,
		BaseModel<?>... urlBaseModels) {

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

		try {
			ConfiguredCDNTemplate configuredCDNTemplate =
				_cdnTemplateRegistry.getConfiguredCDNTemplate(
					companyId,
					_cdnTemplateRegistry.getCDNConfiguration(companyId));

			if (configuredCDNTemplate == null) {
				return;
			}

			Set<String> cacheTags = cacheTagsUnsafeSupplier.get();

			if (cacheTags.isEmpty()) {
				return;
			}

			CDNPurgeRequest.Builder builder = CDNPurgeRequest.builder(
			).tags(
				cacheTags
			);

			if (_isURLPurge(configuredCDNTemplate)) {
				for (BaseModel<?> urlBaseModel : urlBaseModels) {
					if (!(urlBaseModel instanceof Layout)) {
						continue;
					}

					builder.urls(
						_cdnPurgeURLResolver.getLayoutURLs(
							(Layout)urlBaseModel));
				}
			}

			purge(companyId, builder.build());
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

	private static final Log _log = LogFactoryUtil.getLog(
		CDNPurgeManagerImpl.class);

	@Reference
	private CDNCacheTagBuilder _cdnCacheTagBuilder;

	private CDNPurgeCoalescer _cdnPurgeCoalescer;
	private CDNPurgeURLResolver _cdnPurgeURLResolver;
	private CDNTemplateRegistry _cdnTemplateRegistry;

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@Reference
	private FragmentEntryLocalService _fragmentEntryLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private Language _language;

	@Reference
	private LayoutClassedModelUsageLocalService
		_layoutClassedModelUsageLocalService;

	@Reference
	private LayoutDisplayPageProviderRegistry
		_layoutDisplayPageProviderRegistry;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private LayoutSetLocalService _layoutSetLocalService;

	@Reference
	private MessageBus _messageBus;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private Portal _portal;

	private ScheduledExecutorService _scheduledExecutorService;

}