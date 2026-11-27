/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.learn;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;
import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.TreeMapBuilder;

import java.text.Normalizer;

import java.util.Map;
import java.util.Objects;

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

		JSONObject jsonObject = new JSONObject(json);

		String objectName = _getObjectName(jsonObject);

		JSONObject objectEntryJSONObject = jsonObject.getJSONObject(
			"objectEntry");

		ObjectInfo objectInfo = _objectInfoMap.get(objectName);

		String friendlyURLPath = _buildFriendlyURLPath(
			objectInfo, objectEntryJSONObject.getJSONObject("values"));

		if (friendlyURLPath.isEmpty()) {
			return new ResponseEntity<>(json, HttpStatus.OK);
		}

		_updateFriendlyURLPath(
			friendlyURLPath, jsonObject.getLong("classPK"), objectName);

		return new ResponseEntity<>(json, HttpStatus.OK);
	}

	private String _buildFriendlyURLPath(
		ObjectInfo objectInfo, JSONObject valuesJSONObject) {

		String normalizedTitle = _normalizeTitle(
			valuesJSONObject.optString("title"));

		if (normalizedTitle.isEmpty() || !objectInfo.hasParent()) {
			return normalizedTitle;
		}

		long parentObjectEntryId = valuesJSONObject.optLong(
			objectInfo.getParentRelationshipFieldName());

		if (parentObjectEntryId == 0) {
			return normalizedTitle;
		}

		ObjectInfo parentObjectInfo = _objectInfoMap.get(
			objectInfo.getParentObjectName());

		JSONObject parentObjectEntryJSONObject = new JSONObject(
			get(
				_getAuthorization(),
				UriComponentsBuilder.fromPath(
					parentObjectInfo.getRESTPath()
				).pathSegment(
					String.valueOf(parentObjectEntryId)
				).build(
				).toUri()));

		return parentObjectEntryJSONObject.getString("friendlyUrlPath") + "/" +
			normalizedTitle;
	}

	private String _getAuthorization() {
		return _liferayOAuth2AccessTokenManager.getAuthorization(
			"liferay-learn-etc-spring-boot-oahs");
	}

	private String _getObjectName(JSONObject jsonObject) {
		for (String objectName : _objectInfoMap.keySet()) {
			if (jsonObject.has("objectEntryDTO" + objectName)) {
				return objectName;
			}
		}

		return null;
	}

	private String _normalizeTitle(String title) {
		title = Normalizer.normalize(title, Normalizer.Form.NFD);

		title = title.replaceAll("\\p{M}", "");
		title = StringUtil.toLowerCase(title);
		title = title.replaceAll("[^a-z0-9_]+", "-");

		return title.replaceAll("^-|-$", "");
	}

	private void _updateFriendlyURLPath(
		String friendlyURLPath, long objectEntryId, String objectName) {

		ObjectInfo objectInfo = _objectInfoMap.get(objectName);

		JSONObject objectEntryJSONObject = new JSONObject(
			patch(
				_getAuthorization(),
				new JSONObject(
				).put(
					"friendlyUrlPath", friendlyURLPath
				).toString(),
				UriComponentsBuilder.fromPath(
					objectInfo.getRESTPath()
				).pathSegment(
					String.valueOf(objectEntryId)
				).build(
				).toUri()));

		for (Map.Entry<String, ObjectInfo> entry : _objectInfoMap.entrySet()) {
			ObjectInfo value = entry.getValue();

			if (!Objects.equals(objectName, value.getParentObjectName())) {
				continue;
			}

			JSONArray itemsJSONArray = new JSONObject(
				get(
					_getAuthorization(),
					UriComponentsBuilder.fromPath(
						value.getRESTPath()
					).queryParam(
						"filter",
						StringBundler.concat(
							value.getParentRelationshipFieldName(), " eq '",
							objectEntryId, "'")
					).queryParam(
						"pageSize", -1
					).build(
					).toUri())
			).getJSONArray(
				"items"
			);

			for (int i = 0; i < itemsJSONArray.length(); i++) {
				JSONObject itemJSONObject = itemsJSONArray.getJSONObject(i);

				String normalizedTitle = _normalizeTitle(
					itemJSONObject.optString("title"));

				if (normalizedTitle.isEmpty()) {
					continue;
				}

				_updateFriendlyURLPath(
					objectEntryJSONObject.getString("friendlyUrlPath") + "/" +
						normalizedTitle,
					itemJSONObject.getLong("id"), entry.getKey());
			}
		}
	}

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

		public boolean hasParent() {
			if (_parentObjectName == null) {
				return false;
			}

			return true;
		}

		private final String _parentObjectName;
		private final String _parentRelationshipFieldName;
		private final String _restPath;

	}

}