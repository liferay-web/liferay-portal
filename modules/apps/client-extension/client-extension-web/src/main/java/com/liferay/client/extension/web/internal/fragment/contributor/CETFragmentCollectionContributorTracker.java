/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.client.extension.web.internal.fragment.contributor;

import com.liferay.client.extension.type.FragmentCollectionCET;
import com.liferay.fragment.assets.FragmentAssets;
import com.liferay.fragment.contributor.FragmentCollectionContributor;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.petra.executor.PortalExecutorManager;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.URLUtil;
import com.liferay.portal.kernel.util.Validator;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Registers a {@link FragmentCollectionContributor} for each deployed fragment
 * collection client extension, and unregisters it when the client extension
 * is undeployed.
 *
 * <p>
 * The fragments JSON index of a client extension deployed to this portal is
 * read synchronously from its web application bundle, which is already active
 * when the client extension is deployed, so neither the network nor the
 * portal's own host name is involved. The index of a client extension served
 * from another host is fetched asynchronously with short timeouts, so a slow
 * or unavailable host never blocks the deployment. Either way, the index is
 * read once per deployment and kept in memory by the contributor.
 * </p>
 *
 * @author Gianmarco Brunialti
 */
@Component(service = {})
public class CETFragmentCollectionContributorTracker {

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		_executorService = _portalExecutorManager.getPortalExecutor(
			CETFragmentCollectionContributorTracker.class.getName());

		_serviceTracker = new ServiceTracker<>(
			bundleContext, FragmentCollectionCET.class,
			new FragmentCollectionCETServiceTrackerCustomizer());

		_serviceTracker.open();
	}

	@Deactivate
	protected void deactivate() {
		_serviceTracker.close();
	}

	private String _fetch(String url) throws Exception {
		HttpClient httpClient = HttpClient.newBuilder(
		).connectTimeout(
			Duration.ofSeconds(5)
		).followRedirects(
			HttpClient.Redirect.NORMAL
		).build();

		HttpResponse<String> httpResponse = httpClient.send(
			HttpRequest.newBuilder(
				URI.create(url)
			).timeout(
				Duration.ofSeconds(10)
			).build(),
			HttpResponse.BodyHandlers.ofString());

		if (httpResponse.statusCode() != 200) {
			throw new Exception(
				StringBundler.concat(
					"Unexpected status ", httpResponse.statusCode(), " for ",
					url));
		}

		return httpResponse.body();
	}

	/**
	 * Returns the content of a root relative URL served by a web application
	 * bundle of this portal, or <code>null</code> if no bundle serves it.
	 */
	private String _read(String url) throws Exception {
		String pathModule = _portal.getPathModule();

		if (!url.startsWith(pathModule + StringPool.SLASH)) {
			return null;
		}

		String path = url.substring(pathModule.length());

		for (Bundle bundle : _bundleContext.getBundles()) {
			String webContextPath = bundle.getHeaders(
				StringPool.BLANK
			).get(
				"Web-ContextPath"
			);

			if (Validator.isNull(webContextPath) ||
				!path.startsWith(webContextPath + StringPool.SLASH)) {

				continue;
			}

			URL entryURL = bundle.getEntry(
				"META-INF/resources" + path.substring(webContextPath.length()));

			if (entryURL == null) {
				continue;
			}

			return URLUtil.toString(entryURL);
		}

		return null;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CETFragmentCollectionContributorTracker.class);

	private BundleContext _bundleContext;
	private ExecutorService _executorService;

	@Reference
	private FragmentEntryLocalService _fragmentEntryLocalService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Portal _portal;

	@Reference
	private PortalExecutorManager _portalExecutorManager;

	private ServiceTracker<FragmentCollectionCET, Deployment> _serviceTracker;

	private class Deployment {

		public synchronized void close() {
			_closed = true;

			if (_future != null) {
				_future.cancel(true);
			}

			if (_serviceRegistration != null) {
				_serviceRegistration.unregister();

				_serviceRegistration = null;
			}
		}

		private Deployment(FragmentCollectionCET fragmentCollectionCET) {
			_fragmentCollectionCET = fragmentCollectionCET;

			_url = fragmentCollectionCET.getURL();

			_fragmentsJSONURL = FragmentAssets.baseURLResolver(
				_url
			).apply(
				fragmentCollectionCET.getFragmentsJSONPath()
			);
		}

		private String _getMessage(String action) {
			return StringBundler.concat(
				"Unable to ", action, " ", _fragmentsJSONURL,
				" of fragment collection client extension ",
				_fragmentCollectionCET.getExternalReferenceCode());
		}

		private void _open() {
			try {
				String json = _read(_fragmentsJSONURL);

				if (json != null) {
					_register(json);

					return;
				}
			}
			catch (Exception exception) {
				_log.error(_getMessage("read"), exception);

				return;
			}

			if (!_fragmentsJSONURL.startsWith("http://") &&
				!_fragmentsJSONURL.startsWith("https://")) {

				_log.error(
					_getMessage("find") + ": no deployed bundle serves it");

				return;
			}

			synchronized (this) {
				_future = _executorService.submit(
					() -> {
						try {
							_register(_fetch(_fragmentsJSONURL));
						}
						catch (InterruptedException interruptedException) {
							if (_log.isDebugEnabled()) {
								_log.debug(interruptedException);
							}
						}
						catch (Exception exception) {
							_log.error(_getMessage("fetch"), exception);
						}
					});
			}
		}

		private synchronized void _register(String json) throws Exception {
			if (_closed) {
				return;
			}

			CETFragmentCollectionContributor cetFragmentCollectionContributor =
				new CETFragmentCollectionContributor(
					_url, _fragmentEntryLocalService,
					_jsonFactory.createJSONObject(json));

			_serviceRegistration = _bundleContext.registerService(
				FragmentCollectionContributor.class,
				cetFragmentCollectionContributor,
				cetFragmentCollectionContributor.getDictionary());

			if (_log.isInfoEnabled()) {
				_log.info(
					StringBundler.concat(
						"Registered fragment collection ",
						cetFragmentCollectionContributor.
							getFragmentCollectionKey(),
						" of client extension ",
						_fragmentCollectionCET.getExternalReferenceCode(),
						" from ", _fragmentsJSONURL));
			}
		}

		private boolean _closed;
		private final FragmentCollectionCET _fragmentCollectionCET;
		private final String _fragmentsJSONURL;
		private Future<?> _future;
		private ServiceRegistration<FragmentCollectionContributor>
			_serviceRegistration;
		private final String _url;

	}

	private class FragmentCollectionCETServiceTrackerCustomizer
		implements ServiceTrackerCustomizer<FragmentCollectionCET, Deployment> {

		@Override
		public Deployment addingService(
			ServiceReference<FragmentCollectionCET> serviceReference) {

			Deployment deployment = new Deployment(
				_bundleContext.getService(serviceReference));

			deployment._open();

			return deployment;
		}

		@Override
		public void modifiedService(
			ServiceReference<FragmentCollectionCET> serviceReference,
			Deployment deployment) {
		}

		@Override
		public void removedService(
			ServiceReference<FragmentCollectionCET> serviceReference,
			Deployment deployment) {

			deployment.close();

			_bundleContext.ungetService(serviceReference);
		}

	}

}