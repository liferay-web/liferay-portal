/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.purge;

/**
 * @author Gianmarco Brunialti Masera
 */
public class CDNPurgeResult {

	public static CDNPurgeResult failure(
		int status, String message, boolean retryable) {

		return new CDNPurgeResult(message, retryable, status, false);
	}

	/**
	 * Returns the result for an HTTP response status. A 2xx status is a
	 * success, and a 429 or 5xx status is a failure worth retrying.
	 */
	public static CDNPurgeResult fromHTTPStatus(int status, String message) {
		if ((status >= 200) && (status < 300)) {
			return success(status);
		}

		if ((status == 429) || (status >= 500)) {
			return failure(status, message, true);
		}

		return failure(status, message, false);
	}

	public static CDNPurgeResult success(int status) {
		return new CDNPurgeResult(null, false, status, true);
	}

	public String getMessage() {
		return _message;
	}

	public int getStatus() {
		return _status;
	}

	public boolean isRetryable() {
		return _retryable;
	}

	public boolean isSuccess() {
		return _success;
	}

	private CDNPurgeResult(
		String message, boolean retryable, int status, boolean success) {

		_message = message;
		_retryable = retryable;
		_status = status;
		_success = success;
	}

	private final String _message;
	private final boolean _retryable;
	private final int _status;
	private final boolean _success;

}