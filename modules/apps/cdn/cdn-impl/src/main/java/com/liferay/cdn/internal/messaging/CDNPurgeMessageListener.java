/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.messaging;

import com.liferay.cdn.internal.constants.CDNConstants;
import com.liferay.cdn.internal.http.CDNHTTPPurger;
import com.liferay.cdn.internal.template.CDNTemplateRegistry;
import com.liferay.cdn.internal.template.ConfiguredCDNTemplate;
import com.liferay.cdn.purge.CDNPurgeRequest;
import com.liferay.cdn.purge.CDNPurgeResult;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.BaseMessageListener;
import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.DestinationConfiguration;
import com.liferay.portal.kernel.messaging.DestinationFactory;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.NamedThreadFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Sends each purge batch to the CDN as the company's CDN template describes. The destination is serial, so
 * batches reach the CDN API one at a time. A failure that is worth retrying is
 * sent again with exponential backoff, and every outcome is logged on one
 * line.
 *
 * @author Gianmarco Brunialti Masera
 */
@Component(
	property = "destination.name=" + CDNConstants.DESTINATION_NAME_CDN_PURGE,
	service = MessageListener.class
)
public class CDNPurgeMessageListener extends BaseMessageListener {

	@Activate
	protected void activate(BundleContext bundleContext) {
		_cdnHTTPPurger = new CDNHTTPPurger(_http, _jsonFactory);
		_cdnTemplateRegistry = new CDNTemplateRegistry(
			bundleContext, _configurationProvider);

		Destination destination = _destinationFactory.createDestination(
			new DestinationConfiguration(
				DestinationConfiguration.DESTINATION_TYPE_SERIAL,
				CDNConstants.DESTINATION_NAME_CDN_PURGE));

		_serviceRegistration = bundleContext.registerService(
			Destination.class, destination,
			MapUtil.singletonDictionary(
				"destination.name", destination.getName()));

		_scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
			new NamedThreadFactory(
				CDNPurgeMessageListener.class.getName(), Thread.NORM_PRIORITY,
				CDNPurgeMessageListener.class.getClassLoader()));
	}

	@Deactivate
	protected void deactivate() {
		_cdnTemplateRegistry.close();

		_scheduledExecutorService.shutdownNow();

		_serviceRegistration.unregister();
	}

	@Override
	protected void doReceive(Message message) throws Exception {
		long companyId = message.getLong("companyId");

		ConfiguredCDNTemplate configuredCDNTemplate =
			_cdnTemplateRegistry.getConfiguredCDNTemplate(
				companyId, _cdnTemplateRegistry.getCDNConfiguration(companyId));

		String templateKey = message.getString("templateKey");

		CDNPurgeRequest cdnPurgeRequest = CDNPurgeMessageUtil.toCDNPurgeRequest(
			message);

		if ((configuredCDNTemplate == null) ||
			!Objects.equals(configuredCDNTemplate.getKey(), templateKey)) {

			if (_log.isDebugEnabled()) {
				_log.debug(
					StringBundler.concat(
						"Skipping CDN purge ", cdnPurgeRequest,
						" because the CDN template \"", templateKey,
						"\" is no longer enabled for company ", companyId));
			}

			return;
		}

		int attempt = message.getInteger("attempt");
		CDNPurgeResult cdnPurgeResult = null;
		long startTime = System.currentTimeMillis();

		try {
			cdnPurgeResult = _cdnHTTPPurger.purge(
				configuredCDNTemplate, cdnPurgeRequest);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			cdnPurgeResult = CDNPurgeResult.failure(
				0, exception.toString(), true);
		}

		String logMessage = StringBundler.concat(
			"{attempt=", attempt + 1, ", companyId=", companyId,
			", durationMs=", System.currentTimeMillis() - startTime,
			", message=", cdnPurgeResult.getMessage(), ", template=",
			templateKey, ", request=", cdnPurgeRequest, ", status=",
			cdnPurgeResult.getStatus(), "}");

		if (cdnPurgeResult.isSuccess()) {
			if (_log.isInfoEnabled()) {
				_log.info("CDN purge succeeded " + logMessage);
			}

			return;
		}

		if (cdnPurgeResult.isRetryable() && (attempt < _RETRY_COUNT_MAX)) {
			long delay = _RETRY_DELAY_MS << attempt;

			if (_log.isWarnEnabled()) {
				_log.warn(
					StringBundler.concat(
						"CDN purge failed and will be retried in ", delay,
						" ms ", logMessage));
			}

			_scheduledExecutorService.schedule(
				() -> _messageBus.sendMessage(
					CDNConstants.DESTINATION_NAME_CDN_PURGE,
					CDNPurgeMessageUtil.toMessage(
						attempt + 1, companyId, cdnPurgeRequest, templateKey)),
				delay, TimeUnit.MILLISECONDS);

			return;
		}

		_log.error("CDN purge failed " + logMessage);
	}

	private static final int _RETRY_COUNT_MAX = 3;

	private static final long _RETRY_DELAY_MS = 1000;

	private static final Log _log = LogFactoryUtil.getLog(
		CDNPurgeMessageListener.class);

	private CDNHTTPPurger _cdnHTTPPurger;
	private CDNTemplateRegistry _cdnTemplateRegistry;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private DestinationFactory _destinationFactory;

	@Reference
	private Http _http;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private MessageBus _messageBus;

	private ScheduledExecutorService _scheduledExecutorService;
	private ServiceRegistration<Destination> _serviceRegistration;

}