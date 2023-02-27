<#list dataFactory.getSequence(dataFactory.maxLanguagePageCount) as languagePageCount>
	<#assign
		layoutModel = dataFactory.newLayoutModel(groupId, groupId + "_language_page_" + languagePageCount, "", dataFactory.getLanguageLayoutColumn("com_liferay_site_navigation_language_web_portlet_SiteNavigationLanguagePortlet"))
	/>

	${csvFileWriter.write("language", virtualHostModel.hostname + "," + layoutModel.friendlyURL + "\n")}

	<@insertLayout _layoutModel=layoutModel />
</#list>