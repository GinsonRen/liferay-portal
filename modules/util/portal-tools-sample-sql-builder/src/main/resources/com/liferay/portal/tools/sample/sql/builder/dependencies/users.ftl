<#assign
	groupIds = dataFactory.getNewUserGroupIds(groupModel.groupId, guestGroupModel)
	roleIds = [dataFactory.administratorRoleModel.roleId, dataFactory.powerUserRoleModel.roleId, dataFactory.userRoleModel.roleId]

	userModels = dataFactory.newUserModels()
/>

${dataFactory.toInsertSQL(cTDataFactory.newCTPreferencesModel())}

<#list userModels as userModel>
	<#assign
		userGroupModel = dataFactory.newGroupModel(userModel)

		layoutModel = dataFactory.newLayoutModel(userGroupModel.groupId, "home", "", "")
	/>

	<#include "changelist_journal_article.ftl">

	<@insertLayout _layoutModel=layoutModel />

	<@insertGroup _groupModel=userGroupModel />

	<@insertUser
		_groupIds=groupIds
		_roleIds=roleIds
		_userModel=userModel
	/>
</#list>