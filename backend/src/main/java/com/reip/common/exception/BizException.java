package com.reip.common.exception;

import lombok.Getter;

/**
 * 业务异常：所有业务规则校验失败抛出，全局处理器转为 Result
 */
@Getter
public class BizException extends RuntimeException {
    public BizException(String message) { super(message); }
}
