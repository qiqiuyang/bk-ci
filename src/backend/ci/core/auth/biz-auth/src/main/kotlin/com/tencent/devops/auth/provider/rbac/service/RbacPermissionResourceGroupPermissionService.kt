/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.tencent.devops.auth.provider.rbac.service

import com.tencent.bk.sdk.iam.dto.InstancesDTO
import com.tencent.bk.sdk.iam.service.v2.V2ManagerService
import com.tencent.devops.auth.constant.AuthI18nConstants
import com.tencent.devops.auth.constant.AuthMessageCode
import com.tencent.devops.auth.pojo.RelatedResourceInfo
import com.tencent.devops.auth.pojo.vo.GroupPermissionDetailVo
import com.tencent.devops.auth.service.AuthMonitorSpaceService
import com.tencent.devops.auth.service.iam.PermissionResourceGroupPermissionService
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.web.utils.I18nUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

@Suppress("LongParameterList", "IMPLICIT_CAST_TO_ANY")
class RbacPermissionResourceGroupPermissionService constructor(
    private val v2ManagerService: V2ManagerService,
    private val rbacCacheService: RbacCacheService,
    private val monitorSpaceService: AuthMonitorSpaceService
) : PermissionResourceGroupPermissionService {
    @Value("\${auth.iamSystem:}")
    private val systemId = ""

    @Value("\${monitor.register:false}")
    private val registerMonitor: Boolean = false

    @Value("\${monitor.iamSystem:}")
    private val monitorSystemId = ""

    companion object {
        private val logger = LoggerFactory.getLogger(RbacPermissionResourceGroupPermissionService::class.java)
    }

    override fun getGroupPermissionDetail(groupId: Int): Map<String, List<GroupPermissionDetailVo>> {
        val groupPermissionMap = mutableMapOf<String, List<GroupPermissionDetailVo>>()
        groupPermissionMap[I18nUtil.getCodeLanMessage(AuthI18nConstants.BK_DEVOPS_NAME)] =
            getGroupPermissionDetailBySystem(systemId, groupId)
        if (registerMonitor) {
            val monitorGroupPermissionDetail = getGroupPermissionDetailBySystem(monitorSystemId, groupId)
            if (monitorGroupPermissionDetail.isNotEmpty()) {
                groupPermissionMap[I18nUtil.getCodeLanMessage(AuthI18nConstants.BK_MONITOR_NAME)] =
                    getGroupPermissionDetailBySystem(monitorSystemId, groupId)
            }
        }
        return groupPermissionMap
    }

    override fun getGroupPermissionDetailBySystem(iamSystemId: String, groupId: Int): List<GroupPermissionDetailVo> {
        val iamGroupPermissionDetailList = try {
            v2ManagerService.getGroupPermissionDetail(groupId, iamSystemId)
        } catch (e: Exception) {
            throw ErrorCodeException(
                errorCode = AuthMessageCode.GET_GROUP_PERMISSION_DETAIL_FAIL,
                params = arrayOf(groupId.toString()),
                defaultMessage = "Failed to get group($groupId) permission info"
            )
        }
        return iamGroupPermissionDetailList.map { detail ->
            val actionId = detail.id
            val relatedResourceTypesDTO = detail.resourceGroups[0].relatedResourceTypesDTO[0]
            val instances = relatedResourceTypesDTO.condition[0].instances
            val relatedResourceInfos = mutableListOf<RelatedResourceInfo>()
            instances.forEach { instance ->
                // 将resourceType转化为对应的资源类型名称
                buildRelatedResourceTypesName(
                    iamSystemId = iamSystemId,
                    instancesDTO = instance
                )
                relatedResourceInfos.add(
                    RelatedResourceInfo(
                        type = instance.type,
                        name = I18nUtil.getCodeLanMessage(
                            instance.type + AuthI18nConstants.RESOURCE_TYPE_NAME_SUFFIX
                        ),
                        instance = instance.path
                    )
                )
            }
            val (actionName, actionRelatedResourceType) = if (iamSystemId == monitorSystemId) {
                Pair(monitorSpaceService.getMonitorActionName(action = actionId), monitorSystemId)
            } else {
                val actionInfo = rbacCacheService.getActionInfo(action = actionId)
                Pair(actionInfo.actionName, actionInfo.relatedResourceType)
            }
            GroupPermissionDetailVo(
                actionId = actionId,
                name = actionName!!,
                actionRelatedResourceType = actionRelatedResourceType,
                relatedResourceInfos = relatedResourceInfos
            )
        }.sortedBy { it.actionId }
    }

    override fun syncGroupPermission(groupId: Int) {
        // 查询用户组详情信息

    }

    private fun buildRelatedResourceTypesName(iamSystemId: String, instancesDTO: InstancesDTO) {
        instancesDTO.let {
            val resourceTypeName = if (iamSystemId == systemId) {
                rbacCacheService.getResourceTypeInfo(it.type).name
            } else {
                I18nUtil.getCodeLanMessage(AuthI18nConstants.BK_MONITOR_SPACE)
            }
            it.name = resourceTypeName
            it.path.forEach { element1 ->
                element1.forEach { element2 ->
                    element2.typeName = resourceTypeName
                }
            }
        }
    }
}
