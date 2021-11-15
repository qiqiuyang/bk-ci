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
 */

package com.tencent.devops.dispatch.kubernetes.client

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.dispatch.sdk.BuildFailureException
import com.tencent.devops.common.dispatch.sdk.pojo.DispatchMessage
import com.tencent.devops.dispatch.kubernetes.kubernetes.client.DeploymentClient
import com.tencent.devops.dispatch.kubernetes.kubernetes.client.PodsClient
import com.tencent.devops.dispatch.kubernetes.common.ErrorCodeEnum
import com.tencent.devops.dispatch.kubernetes.pojo.Action
import com.tencent.devops.dispatch.kubernetes.pojo.BuildContainer
import com.tencent.devops.dispatch.kubernetes.pojo.resp.OperateContainerResult
import com.tencent.devops.dispatch.kubernetes.pojo.ContainerType
import com.tencent.devops.dispatch.kubernetes.pojo.Params
import com.tencent.devops.dispatch.kubernetes.utils.KubernetesClientUtil
import com.tencent.devops.dispatch.kubernetes.utils.KubernetesClientUtil.isSuccessful
import io.kubernetes.client.openapi.models.V1ContainerStatus
import kotlin.math.log
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ContainerClient @Autowired constructor(
    private val podsClient: PodsClient,
    private val deploymentClient: DeploymentClient
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ContainerClient::class.java)
        private const val MAX_WAIT = 10
    }

    fun getContainerStatus(
        containerName: String
    ): Result<V1ContainerStatus> {
        val result = podsClient.list(containerName)
        if (!result.isSuccessful()) {
            // 先不添加重试逻辑，看后续使用
            //           if (retryTime > 0) {
            //               val retryTimeLocal = retryTime - 1
            //               return getContainerStatus(buildId, vmSeqId, userId, name, retryTimeLocal)
            //           }
            throw BuildFailureException(
                ErrorCodeEnum.VM_STATUS_INTERFACE_ERROR.errorType,
                ErrorCodeEnum.VM_STATUS_INTERFACE_ERROR.errorCode,
                ErrorCodeEnum.VM_STATUS_INTERFACE_ERROR.formatErrorMessage,
                KubernetesClientUtil.getClientFailInfo(
                    "获取容器状态接口异常（Fail to get container status, http response code: ${result.statusCode}"
                )
            )
        }
        return Result(
            status = result.statusCode,
            message = null,
            data = result.data?.items?.ifEmpty { null }?.get(0)?.status?.containerStatuses?.ifEmpty { null }?.get(0)
        )
    }

    fun createContainer(
        dispatchMessage: DispatchMessage,
        buildContainer: BuildContainer
    ): String {
        val containerName = "${dispatchMessage.userId}${System.currentTimeMillis()}"

        logger.info("ContainerClient createContainer containerName: $containerName dispatchMessage: $dispatchMessage")

        when (buildContainer.type) {
            ContainerType.DEV -> {
                val resp = deploymentClient.create(buildContainer, containerName)
                if (!resp.isSuccessful()) {
                    throw BuildFailureException(
                        errorType = ErrorCodeEnum.CREATE_VM_INTERFACE_FAIL.errorType,
                        errorCode = ErrorCodeEnum.CREATE_VM_INTERFACE_FAIL.errorCode,
                        formatErrorMessage = ErrorCodeEnum.CREATE_VM_INTERFACE_FAIL.formatErrorMessage,
                        errorMessage = KubernetesClientUtil.getClientFailInfo(
                            "创建容器接口异常 （Fail to create container status, http response code: ${resp.statusCode}"
                        )
                    )
                }
            }
            ContainerType.STATEFUL -> {
                TODO()
            }
            ContainerType.STATELESS -> {
                TODO()
            }
        }
        return containerName
    }

    fun waitContainerStart(
        containerName: String
    ): OperateContainerResult {
        var state = try {
            getContainerStatus(containerName).data?.state
        } catch (e: Throwable) {
            return OperateContainerResult(containerName, false, e.message)
        }

        var max = MAX_WAIT
        while (state?.running == null && max != 0) {
            if (state?.terminated != null) {
                return OperateContainerResult(containerName, false, state.terminated?.message)
            } else {
                state = getContainerStatus(containerName).data?.state
            }
            Thread.sleep(1000)
            max--
        }
        return OperateContainerResult(containerName, state?.running != null)
    }

    fun operateContainer(
        containerName: String,
        action: Action,
        param: Params?
    ): OperateContainerResult {
        return when (action) {
            Action.DELETE -> {
                val result = deploymentClient.delete(containerName)
                if (!result.isSuccessful()) {
                    OperateContainerResult("", false, result.data?.message)
                }
                OperateContainerResult("", true)
            }
            Action.START -> {
                try {
                    deploymentClient.start(containerName, param)
                } catch (e: Exception) {
                    logger.error("start container $containerName error", e)
                    throw BuildFailureException(
                        errorType = ErrorCodeEnum.CREATE_VM_INTERFACE_FAIL.errorType,
                        errorCode = ErrorCodeEnum.CREATE_VM_INTERFACE_FAIL.errorCode,
                        formatErrorMessage = ErrorCodeEnum.CREATE_VM_INTERFACE_FAIL.formatErrorMessage,
                        errorMessage = KubernetesClientUtil.getClientFailInfo("启动容器接口错误, ${e.message}")
                    )
                }
                OperateContainerResult("", true)
            }
            Action.STOP -> {
                val result = deploymentClient.stop(containerName)
                OperateContainerResult("", result.isSuccessful())
            }
        }
    }

    fun waitContainerStop(
        containerName: String
    ): OperateContainerResult {
        var replicas = try {
            deploymentClient.list(containerName).data?.items?.ifEmpty { null }?.get(0)?.spec?.replicas
        } catch (e: Throwable) {
            return OperateContainerResult(containerName, false, e.message)
        }
        var max = MAX_WAIT
        while (replicas != 0 && max != 0) {
            replicas = deploymentClient.list(containerName).data?.items?.ifEmpty { null }?.get(0)?.spec?.replicas
            Thread.sleep(1000)
            max--
        }
        return OperateContainerResult(containerName, replicas == 0)
    }
}
