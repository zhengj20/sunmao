package com.reip.framework.auth;

/**
 * 用户上下文（请求线程内）
 */
public class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) { HOLDER.set(user); }
    public static LoginUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static Long currentUserId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String currentRealName() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getRealName();
    }

    /** 未登录直接拒绝 */
    public static LoginUser require() {
        LoginUser u = HOLDER.get();
        if (u == null) throw new IllegalStateException("用户未登录");
        return u;
    }
}
