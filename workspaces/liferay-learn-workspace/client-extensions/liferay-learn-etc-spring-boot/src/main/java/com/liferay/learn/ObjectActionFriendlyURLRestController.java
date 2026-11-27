/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;
import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.TreeMapBuilder;
import com.liferay.portal.kernel.util.Validator;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Caleb Hall
 * @author Francesco Acciaro
 */
@RequestMapping("/object/action/friendly/url")
@RestController
public class ObjectActionFriendlyURLRestController extends BaseRestController {

	@PostMapping
	public ResponseEntity<String> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody String json) {

		log(jwt, _log, json);

		JSONObject jsonObject = new JSONObject(json);

		String objectName = _getObjectName(jsonObject);

		ObjectInfo objectInfo = _objectInfoMap.get(objectName);

		if (Objects.equals(
				jsonObject.getString("objectActionTriggerKey"),
				"onAfterUpdate") &&
			!_isChanged(jsonObject, objectInfo)) {

			return new ResponseEntity<>(json, HttpStatus.OK);
		}

		JSONObject objectEntryJSONObject = _getObjectEntryJSONObject(
			jsonObject.getLong("classPK"), objectInfo.getRESTPath());

		_propagateFriendlyURL(
			objectEntryJSONObject, objectName,
			_getParentFriendlyURLPath(objectEntryJSONObject, objectInfo));

		return new ResponseEntity<>(json, HttpStatus.OK);
	}

	private String _getAuthorization() {
		return _liferayOAuth2AccessTokenManager.getAuthorization(
			"liferay-learn-etc-spring-boot-oahs");
	}

	private String _getFriendlyURLPath(
		JSONObject objectEntryJSONObject, String parentFriendlyURLPath) {

		String urlTitle = StringUtil.replace(
			objectEntryJSONObject.optString("title"), CharPool.SLASH,
			CharPool.DASH);

		if (Validator.isNull(urlTitle)) {
			urlTitle = objectEntryJSONObject.getString("externalReferenceCode");
		}

		if (Validator.isNull(parentFriendlyURLPath)) {
			return urlTitle;
		}

		return parentFriendlyURLPath + StringPool.SLASH + urlTitle;
	}

	private JSONObject _getObjectEntryJSONObject(
		long objectEntryId, String restPath) {

		return new JSONObject(
			get(
				_getAuthorization(),
				UriComponentsBuilder.fromPath(
					restPath + StringPool.SLASH + objectEntryId
				).build(
				).toUri()));
	}

	private String _getObjectName(JSONObject jsonObject) {
		for (String objectName : _objectInfoMap.keySet()) {
			if (jsonObject.has("objectEntryDTO" + objectName)) {
				return objectName;
			}
		}

		return null;
	}

	private String _getParentFriendlyURLPath(
		JSONObject objectEntryJSONObject, ObjectInfo objectInfo) {

		long parentObjectEntryId = objectEntryJSONObject.optLong(
			objectInfo.getParentRelationshipFieldName());

		if (parentObjectEntryId == 0) {
			return StringPool.BLANK;
		}

		ObjectInfo parentObjectInfo = _objectInfoMap.get(
			objectInfo.getParentObjectName());

		JSONObject parentObjectEntryJSONObject = _getObjectEntryJSONObject(
			parentObjectEntryId, parentObjectInfo.getRESTPath());

		return parentObjectEntryJSONObject.getString("friendlyUrlPath");
	}

	private boolean _isChanged(JSONObject jsonObject, ObjectInfo objectInfo) {
		JSONObject originalValuesJSONObject = jsonObject.getJSONObject(
			"originalObjectEntry"
		).getJSONObject(
			"values"
		);
		JSONObject valuesJSONObject = jsonObject.getJSONObject(
			"objectEntry"
		).getJSONObject(
			"values"
		);

		if (!Objects.equals(
				originalValuesJSONObject.optString("title"),
				valuesJSONObject.optString("title"))) {

			return true;
		}

		String parentRelationshipFieldName =
			objectInfo.getParentRelationshipFieldName();

		if (originalValuesJSONObject.optLong(parentRelationshipFieldName) !=
				valuesJSONObject.optLong(parentRelationshipFieldName)) {

			return true;
		}

		return false;
	}

	private String _patch(
		String friendlyURLPath, JSONObject objectEntryJSONObject,
		ObjectInfo objectInfo) {

		return new JSONObject(
			patch(
				_getAuthorization(),
				new JSONObject(
				).put(
					"friendlyUrlPath_i18n",
					new JSONObject(
					).put(
						objectEntryJSONObject.getString("defaultLanguageId"),
						friendlyURLPath
					)
				).put(
					"status", objectEntryJSONObject.getJSONObject("status")
				).toString(),
				UriComponentsBuilder.fromPath(
					objectInfo.getRESTPath() + StringPool.SLASH +
						objectEntryJSONObject.getLong("id")
				).build(
				).toUri())
		).getString(
			"friendlyUrlPath"
		);
	}

	private void _propagateFriendlyURL(
		JSONObject objectEntryJSONObject, String objectName,
		String parentFriendlyURLPath) {

		String friendlyURLPath = _patch(
			_getFriendlyURLPath(objectEntryJSONObject, parentFriendlyURLPath),
			objectEntryJSONObject, _objectInfoMap.get(objectName));

		for (Map.Entry<String, ObjectInfo> entry : _objectInfoMap.entrySet()) {
			ObjectInfo objectInfo = entry.getValue();

			if (!Objects.equals(objectName, objectInfo.getParentObjectName())) {
				continue;
			}

			JSONArray itemsJSONArray = new JSONObject(
				get(
					_getAuthorization(),
					UriComponentsBuilder.fromPath(
						objectInfo.getRESTPath()
					).queryParam(
						"filter",
						StringBundler.concat(
							objectInfo.getParentRelationshipFieldName(),
							" eq '", objectEntryJSONObject.getLong("id"), "'")
					).queryParam(
						"pageSize", -1
					).build(
					).toUri())
			).getJSONArray(
				"items"
			);

			for (int i = 0; i < itemsJSONArray.length(); i++) {
				_propagateFriendlyURL(
					itemsJSONArray.getJSONObject(i), entry.getKey(),
					friendlyURLPath);
			}
		}
	}

	private static final Log _log = LogFactory.getLog(
		ObjectActionFriendlyURLRestController.class);

	private static final Map<String, ObjectInfo> _objectInfoMap =
		TreeMapBuilder.put(
			"P2S3Course", new ObjectInfo("/o/c/p2s3courses")
		).put(
			"P2S3Lesson",
			new ObjectInfo(
				"P2S3Module", "r_p2s3ModuleToP2S3Lessons_c_p2s3ModuleId",
				"/o/c/p2s3lessons")
		).put(
			"P2S3Module",
			new ObjectInfo(
				"P2S3Course", "r_p2s3CourseToP2S3Modules_c_p2s3CourseId",
				"/o/c/p2s3modules")
		).put(
			"P2S3Quiz",
			new ObjectInfo(
				"P2S3Module", "r_p2s3ModuleToP2S3Quizzes_c_p2s3ModuleId",
				"/o/c/p2s3quizes")
		).build();

	@Autowired
	private LiferayOAuth2AccessTokenManager _liferayOAuth2AccessTokenManager;

	private static class ObjectInfo {

		public ObjectInfo(String restPath) {
			this(null, null, restPath);
		}

		public ObjectInfo(
			String parentObjectName, String parentRelationshipFieldName,
			String restPath) {

			_parentObjectName = parentObjectName;
			_parentRelationshipFieldName = parentRelationshipFieldName;
			_restPath = restPath;
		}

		public String getParentObjectName() {
			return _parentObjectName;
		}

		public String getParentRelationshipFieldName() {
			return _parentRelationshipFieldName;
		}

		public String getRESTPath() {
			return _restPath;
		}

		private final String _parentObjectName;
		private final String _parentRelationshipFieldName;
		private final String _restPath;

	}

}