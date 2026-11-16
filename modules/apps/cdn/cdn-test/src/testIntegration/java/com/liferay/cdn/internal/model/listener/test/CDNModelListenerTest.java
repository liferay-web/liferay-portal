/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cdn.internal.model.listener.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLTrashLocalService;
import com.liferay.fragment.constants.FragmentConstants;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.journal.constants.JournalArticleConstants;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.layout.test.util.ContentLayoutTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.object.field.builder.TextObjectFieldBuilder;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.test.util.ObjectDefinitionTestUtil;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.LayoutSetLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.segments.service.SegmentsExperienceLocalService;
import com.liferay.site.navigation.constants.SiteNavigationConstants;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;

import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.Serializable;

import java.net.InetSocketAddress;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gianmarco Brunialti Masera
 */
@RunWith(Arquillian.class)
public class CDNModelListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_httpServer = HttpServer.create(
			new InetSocketAddress("127.0.0.1", 0), 0);

		_httpServer.createContext(
			"/purge",
			httpExchange -> {
				try (InputStream inputStream = httpExchange.getRequestBody()) {
					_bodies.add(
						new String(StreamUtil.toByteArray(inputStream)));
				}

				httpExchange.sendResponseHeaders(200, -1);

				httpExchange.close();
			});

		_httpServer.start();

		_serviceContext = ServiceContextTestUtil.getServiceContext(
			_group.getGroupId(), TestPropsValues.getUserId());
	}

	@After
	public void tearDown() {
		_httpServer.stop(0);
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testBlogsEntryModelListener() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			ServiceContext serviceContext =
				(ServiceContext)_serviceContext.clone();

			serviceContext.setWorkflowAction(
				WorkflowConstants.ACTION_SAVE_DRAFT);

			BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
				TestPropsValues.getUserId(), RandomTestUtil.randomString(),
				RandomTestUtil.randomString(), serviceContext);

			_assertNoPurge();

			String[] tags = {
				"lfr-b" + blogsEntry.getEntryId(),
				_getCollectionTag(_group.getGroupId(), BlogsEntry.class),
				"lfr-x" + _group.getGroupId()
			};

			_blogsEntryLocalService.updateStatus(
				TestPropsValues.getUserId(), blogsEntry.getEntryId(),
				WorkflowConstants.STATUS_APPROVED, _serviceContext,
				new HashMap<>());

			_assertPurge(tags);

			_blogsEntryLocalService.moveEntryToTrash(
				TestPropsValues.getUserId(), blogsEntry.getEntryId());

			_assertPurge(tags);

			_blogsEntryLocalService.deleteEntry(blogsEntry.getEntryId());

			_assertNoPurge();
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testDLFileEntryModelListener() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			FileEntry fileEntry = _dlAppLocalService.addFileEntry(
				null, TestPropsValues.getUserId(), _group.getGroupId(),
				DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
				RandomTestUtil.randomString() + ".txt", ContentTypes.TEXT_PLAIN,
				RandomTestUtil.randomBytes(), null, null, null,
				_serviceContext);

			String[] tags = {
				"lfr-d" + fileEntry.getFileEntryId(),
				_getCollectionTag(_group.getGroupId(), DLFileEntry.class),
				_getCollectionTag(_group.getGroupId(), FileEntry.class),
				"lfr-x" + _group.getGroupId()
			};

			_assertPurge(tags);

			_dlAppLocalService.updateFileEntry(
				TestPropsValues.getUserId(), fileEntry.getFileEntryId(),
				fileEntry.getFileName(), ContentTypes.TEXT_PLAIN,
				fileEntry.getTitle(), fileEntry.getTitle(), StringPool.BLANK,
				StringPool.BLANK, DLVersionNumberIncrease.MAJOR,
				RandomTestUtil.randomBytes(), null, null, null,
				_serviceContext);

			_assertPurge(tags);

			_dlTrashLocalService.moveFileEntryToTrash(
				TestPropsValues.getUserId(), _group.getGroupId(),
				fileEntry.getFileEntryId());

			_assertPurge(tags);

			_dlAppLocalService.deleteFileEntry(fileEntry.getFileEntryId());

			_assertPurge(tags);
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testFragmentEntryModelListener() throws Exception {
		FragmentEntry fragmentEntry =
			_fragmentEntryLocalService.addFragmentEntry(
				null, TestPropsValues.getUserId(), _group.getGroupId(), 0,
				StringUtil.randomString(), RandomTestUtil.randomString(),
				StringPool.BLANK,
				"<div>" + RandomTestUtil.randomString() + "</div>",
				StringPool.BLANK, false, "{fieldSets: []}", null, 0, false,
				false, FragmentConstants.TYPE_COMPONENT, null,
				WorkflowConstants.STATUS_APPROVED, _serviceContext);

		Layout layout = LayoutTestUtil.addTypeContentPublishedLayout(
			_group, RandomTestUtil.randomString(),
			WorkflowConstants.STATUS_APPROVED);

		ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
			StringPool.BLANK, fragmentEntry.getCss(),
			fragmentEntry.getConfiguration(),
			fragmentEntry.getExternalReferenceCode(), null,
			fragmentEntry.getHtml(), fragmentEntry.getJs(), layout,
			fragmentEntry.getFragmentEntryKey(),
			_segmentsExperienceLocalService.fetchDefaultSegmentsExperienceId(
				layout.getPlid()),
			fragmentEntry.getType());

		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper();
			CompanyConfigurationTemporarySwapper
				fragmentServiceCompanyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId(),
						"com.liferay.fragment.configuration." +
							"FragmentServiceConfiguration",
						HashMapDictionaryBuilder.<String, Object>put(
							"propagateChanges", true
						).build())) {

			FragmentEntry draftFragmentEntry =
				_fragmentEntryLocalService.getDraft(fragmentEntry);

			draftFragmentEntry.setHtml(
				"<div>" + RandomTestUtil.randomString() + "</div>");

			draftFragmentEntry = _fragmentEntryLocalService.updateDraft(
				draftFragmentEntry);

			_assertNoPurge();

			_fragmentEntryLocalService.publishDraft(draftFragmentEntry);

			_assertPurge(
				"lfr-f" + fragmentEntry.getFragmentEntryId(),
				"lfr-p" + layout.getPlid(), "lfr-x" + _group.getGroupId());
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testJournalArticleModelListener() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			// Saving a draft does not purge

			JournalArticle journalArticle = JournalTestUtil.addArticle(
				_group.getGroupId(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
				JournalArticleConstants.CLASS_NAME_ID_DEFAULT,
				RandomTestUtil.randomString(), RandomTestUtil.randomString(),
				RandomTestUtil.randomString(), LocaleUtil.getSiteDefault(),
				true, false, _serviceContext);

			_assertNoPurge();

			String[] tags = {
				"lfr-wc" + journalArticle.getResourcePrimKey(),
				_getCollectionTag(_group.getGroupId(), JournalArticle.class),
				"lfr-x" + _group.getGroupId()
			};

			// Publishing purges

			journalArticle = JournalTestUtil.updateArticle(
				journalArticle, RandomTestUtil.randomString(),
				journalArticle.getContent(), true, true, _serviceContext);

			_assertPurge(tags);

			// A draft on top of the published version does not purge

			JournalTestUtil.updateArticle(
				journalArticle, RandomTestUtil.randomString(),
				journalArticle.getContent(), true, false, _serviceContext);

			_assertNoPurge();

			// Expiring purges

			_journalArticleLocalService.expireArticle(
				TestPropsValues.getUserId(), _group.getGroupId(),
				journalArticle.getArticleId(), journalArticle.getVersion(),
				null, _serviceContext);

			_assertPurge(tags);

			// Deleting a published article purges

			journalArticle = JournalTestUtil.addArticle(
				_group.getGroupId(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

			tags = new String[] {
				"lfr-wc" + journalArticle.getResourcePrimKey(),
				_getCollectionTag(_group.getGroupId(), JournalArticle.class),
				"lfr-x" + _group.getGroupId()
			};

			_assertPurge(tags);

			_journalArticleLocalService.deleteArticle(
				journalArticle.getGroupId(), journalArticle.getArticleId(),
				_serviceContext);

			_assertPurge(tags);
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testLayoutModelListener() throws Throwable {
		Layout layout = LayoutTestUtil.addTypeContentPublishedLayout(
			_group, RandomTestUtil.randomString(),
			WorkflowConstants.STATUS_APPROVED);

		Layout draftLayout = layout.fetchDraftLayout();

		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
				StringPool.BLANK, StringPool.BLANK, StringPool.BLANK, null,
				null, "<div>" + RandomTestUtil.randomString() + "</div>",
				StringPool.BLANK, draftLayout, StringPool.BLANK,
				_segmentsExperienceLocalService.
					fetchDefaultSegmentsExperienceId(draftLayout.getPlid()),
				FragmentConstants.TYPE_COMPONENT);

			draftLayout = _layoutLocalService.getLayout(draftLayout.getPlid());

			draftLayout.setStatus(WorkflowConstants.STATUS_DRAFT);

			Layout updatedDraftLayout = _layoutLocalService.updateLayout(
				draftLayout);

			_assertNoPurge();

			TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					ContentLayoutTestUtil.publishLayout(
						updatedDraftLayout, layout);

					Assert.assertNull(_bodies.poll(1, TimeUnit.SECONDS));

					return null;
				});

			_assertPurge(
				"lfr-p" + layout.getPlid(), "lfr-x" + _group.getGroupId());
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testLayoutModelListenerWithPageTreeChanges() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			// Adding a page changes the navigation of every page

			Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

			_assertPurge("lfr-s" + _group.getGroupId());

			String[] tags = {
				"lfr-p" + layout.getPlid(), "lfr-s" + _group.getGroupId(),
				"lfr-x" + _group.getGroupId()
			};

			// Renaming a page changes the navigation of every page

			layout = _layoutLocalService.updateName(
				layout, RandomTestUtil.randomString(),
				LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault()));

			_assertPurge(tags);

			// Editing a page only purges the page

			layout.setTitle(RandomTestUtil.randomString());

			layout = _layoutLocalService.updateLayout(layout);

			_assertPurge(
				"lfr-p" + layout.getPlid(), "lfr-x" + _group.getGroupId());

			// Hiding a page changes the navigation of every page

			layout.setHidden(true);

			layout = _layoutLocalService.updateLayout(layout);

			_assertPurge(tags);

			layout.setHidden(false);

			layout = _layoutLocalService.updateLayout(layout);

			_assertPurge(tags);

			// Moving a page changes the navigation of every page

			Layout parentLayout = LayoutTestUtil.addTypePortletLayout(_group);

			_assertPurge("lfr-s" + _group.getGroupId());

			layout = _layoutLocalService.updateParentLayoutId(
				layout.getPlid(), parentLayout.getPlid());

			_assertPurge(tags);

			// Deleting a page changes the navigation of every page

			_layoutLocalService.deleteLayout(layout);

			_assertPurge(tags);

			// Private pages are not cached

			LayoutTestUtil.addTypePortletLayout(_group, true);

			_assertNoPurge();
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testLayoutSetModelListener() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			_layoutSetLocalService.updateSettings(
				_group.getGroupId(), true, RandomTestUtil.randomString());

			_assertNoPurge();

			_layoutSetLocalService.updateSettings(
				_group.getGroupId(), false, RandomTestUtil.randomString());

			_assertPurge("lfr-s" + _group.getGroupId());
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testObjectEntryModelListener() throws Exception {
		_objectDefinition = ObjectDefinitionTestUtil.publishObjectDefinition(
			Collections.singletonList(
				new TextObjectFieldBuilder(
				).labelMap(
					RandomTestUtil.randomLocaleStringMap()
				).name(
					"name"
				).build()));

		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			ObjectEntry objectEntry = _objectEntryLocalService.addObjectEntry(
				0, TestPropsValues.getUserId(),
				_objectDefinition.getObjectDefinitionId(), 0, null,
				HashMapBuilder.<String, Serializable>put(
					"name", RandomTestUtil.randomString()
				).build(),
				_serviceContext);

			// Entries of company scoped object definitions belong to group 0

			String[] tags = {
				"lfr-o" + objectEntry.getObjectEntryId(),
				"lfr-l0-" +
					_portal.getClassNameId(_objectDefinition.getClassName()),
				"lfr-x0"
			};

			_assertPurge(tags);

			_objectEntryLocalService.updateObjectEntry(
				TestPropsValues.getUserId(), objectEntry.getObjectEntryId(), 0,
				HashMapBuilder.<String, Serializable>put(
					"name", RandomTestUtil.randomString()
				).build(),
				_serviceContext);

			_assertPurge(tags);

			_objectEntryLocalService.deleteObjectEntry(
				objectEntry.getObjectEntryId());

			_assertPurge(tags);
		}
	}

	@FeatureFlag("LPD-107638")
	@Test
	public void testSiteNavigationMenuItemModelListener() throws Exception {
		SiteNavigationMenu siteNavigationMenu =
			_siteNavigationMenuLocalService.addSiteNavigationMenu(
				null, TestPropsValues.getUserId(), _group.getGroupId(),
				RandomTestUtil.randomString(),
				SiteNavigationConstants.TYPE_DEFAULT, _serviceContext);

		try (CompanyConfigurationTemporarySwapper
				cdnCompanyConfigurationTemporarySwapper =
					_getCDNCompanyConfigurationTemporarySwapper()) {

			_siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
				null, TestPropsValues.getUserId(), _group.getGroupId(),
				siteNavigationMenu.getSiteNavigationMenuId(), 0, "url",
				"url=https://" + RandomTestUtil.randomString() + ".com",
				_serviceContext);

			_assertPurge("lfr-s" + _group.getGroupId());
		}
	}

	private void _assertNoPurge() throws Exception {
		Assert.assertNull(_bodies.poll(2, TimeUnit.SECONDS));
	}

	private void _assertPurge(String... tags) throws Exception {
		String body = _bodies.poll(30, TimeUnit.SECONDS);

		Assert.assertNotNull(body);

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject(body);

		Assert.assertEquals(
			SetUtil.fromArray(tags),
			SetUtil.fromList(
				JSONUtil.toStringList(jsonObject.getJSONArray("tags"))));

		_assertNoPurge();
	}

	private CompanyConfigurationTemporarySwapper
			_getCDNCompanyConfigurationTemporarySwapper()
		throws Exception {

		InetSocketAddress inetSocketAddress = _httpServer.getAddress();

		return new CompanyConfigurationTemporarySwapper(
			TestPropsValues.getCompanyId(),
			"com.liferay.cdn.configuration.CDNConfiguration",
			HashMapDictionaryBuilder.<String, Object>put(
				"coalesceWindowMs", 100L
			).put(
				"enabled", true
			).put(
				"endpoint",
				StringBundler.concat(
					"http://127.0.0.1:", inetSocketAddress.getPort(), "/purge")
			).put(
				"templateKey", "custom"
			).build());
	}

	private String _getCollectionTag(long groupId, Class<?> clazz) {
		return StringBundler.concat(
			"lfr-l", groupId, "-", _portal.getClassNameId(clazz));
	}

	private static final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	@Inject
	private BlogsEntryLocalService _blogsEntryLocalService;

	private final BlockingQueue<String> _bodies = new LinkedBlockingQueue<>();

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@Inject
	private DLTrashLocalService _dlTrashLocalService;

	@Inject
	private FragmentEntryLocalService _fragmentEntryLocalService;

	@DeleteAfterTestRun
	private Group _group;

	private HttpServer _httpServer;

	@Inject
	private JournalArticleLocalService _journalArticleLocalService;

	@Inject
	private LayoutLocalService _layoutLocalService;

	@Inject
	private LayoutSetLocalService _layoutSetLocalService;

	@DeleteAfterTestRun
	private ObjectDefinition _objectDefinition;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private Portal _portal;

	@Inject
	private SegmentsExperienceLocalService _segmentsExperienceLocalService;

	private ServiceContext _serviceContext;

	@Inject
	private SiteNavigationMenuItemLocalService
		_siteNavigationMenuItemLocalService;

	@Inject
	private SiteNavigationMenuLocalService _siteNavigationMenuLocalService;

}