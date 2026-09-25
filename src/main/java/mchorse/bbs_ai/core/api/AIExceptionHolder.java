package mchorse.bbs_ai.core.api;

import mchorse.bbs_ai.core.api.APIException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 异常解包工具
 *
 * <p>CompletableFuture 包装后的异常链中定位 {@link APIException}。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public final class AIExceptionHolder
{
    private AIExceptionHolder()
    {}

    /**
     * 从 CompletableFuture 的异常链中解出 APIException；
     * 找不到时包装为 INTERNAL 错误码的 APIException（绝不返回 null）
     */
    public static APIException unwrap(Throwable throwable)
    {
        Throwable current = throwable;

        while (current != null)
        {
            if (current instanceof APIException)
            {
                return (APIException) current;
            }

            if (current instanceof CompletionException && current.getCause() != null)
            {
                current = current.getCause();

                continue;
            }

            if (current instanceof ExecutionException && current.getCause() != null)
            {
                current = current.getCause();

                continue;
            }

            if (current instanceof RuntimeException && current.getCause() != null)
            {
                current = current.getCause();

                continue;
            }

            break;
        }

        return new APIException(APIException.ErrorCode.INVALID_RESPONSE,
            throwable == null ? "未知错误" : throwable.getMessage(), -1, throwable);
    }
}
