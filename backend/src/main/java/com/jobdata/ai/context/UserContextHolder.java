package com.jobdata.ai.context;

/**
 * 用户上下文持有者：使用 ThreadLocal 保存当前请求的用户 ID。
 */
public class UserContextHolder {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 设置当前线程的用户 ID。
     *
     * @param userId 用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前线程的用户 ID。
     *
     * @return 用户 ID（未设置时为 null）
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 清理当前线程的用户上下文。
     */
    public static void clear() {
        USER_ID.remove();
    }
}
