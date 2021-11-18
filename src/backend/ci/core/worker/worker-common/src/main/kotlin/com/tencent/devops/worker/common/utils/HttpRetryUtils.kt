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

package com.tencent.devops.worker.common.utils

import java.net.HttpRetryException
import java.net.SocketTimeoutException

object HttpRetryUtils {

    @Throws(HttpRetryException::class, SocketTimeoutException::class)
    fun <T> retry(retryTime: Int = 100, retryPeriodMills: Long = 500, action: () -> T): T {
        return try {
            action()
        } catch (re: SocketTimeoutException) {
            if (retryTime - 1 < 0) {
                throw re
            }
            if (retryPeriodMills > 0) {
                Thread.sleep(retryPeriodMills)
            }
            retry(action = action, retryTime = retryTime - 1, retryPeriodMills = retryPeriodMills)
        } catch (re: HttpRetryException) {
            if (retryTime - 1 < 0) {
                throw re
            }
            if (retryPeriodMills > 0) {
                Thread.sleep(retryPeriodMills)
            }
            retry(action = action, retryTime = retryTime - 1, retryPeriodMills = retryPeriodMills)
        }
    }

    @Throws(SocketTimeoutException::class)
    fun <T> retryWhenSocketTimeException(retryTime: Int = 100, retryPeriodMills: Long = 500, action: () -> T): T {
        return try {
            action()
        } catch (re: SocketTimeoutException) {
            if (retryTime - 1 < 0) {
                throw re
            }
            if (retryPeriodMills > 0) {
                Thread.sleep(retryPeriodMills)
            }
            retry(action = action, retryTime = retryTime - 1, retryPeriodMills = retryPeriodMills)
        }
    }

    @Throws(HttpRetryException::class)
    fun <T> retryWhenHttpRetryException(retryTime: Int = 100, retryPeriodMills: Long = 500, action: () -> T): T {
        return try {
            action()
        } catch (re: HttpRetryException) {
            if (retryTime - 1 < 0) {
                throw re
            }
            if (retryPeriodMills > 0) {
                Thread.sleep(retryPeriodMills)
            }
            retry(action = action, retryTime = retryTime - 1, retryPeriodMills = retryPeriodMills)
        }
    }

    @Throws(RuntimeException::class)
    fun <T> retryWhenRuntimeException(retryTime: Int = 100, retryPeriodMills: Long = 500, action: () -> T): T {
        return try {
            action()
        } catch (re: RuntimeException) {
            if (retryTime - 1 < 0) {
                throw re
            }
            if (retryPeriodMills > 0) {
                Thread.sleep(retryPeriodMills)
            }
            retryWhenRuntimeException(action = action, retryTime = retryTime - 1, retryPeriodMills = retryPeriodMills)
        }
    }
}
