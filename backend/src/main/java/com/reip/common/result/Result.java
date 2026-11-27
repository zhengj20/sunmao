package com.reip.common.result;

import lombok.Data;

/**
 * 统一响应结构 {code:0成功, msg, data}
 */
@Data
public class Result<T> {
    public static final int SUCCESS = 0;
    public static final int FAIL = 1;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok() { return ok(null); }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = SUCCESS;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.code = FAIL;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> of(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
