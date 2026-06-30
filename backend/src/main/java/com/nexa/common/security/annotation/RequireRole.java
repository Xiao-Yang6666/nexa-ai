package com.nexa.common.security.annotation;

import com.nexa.common.security.rbac.AuthLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限注解（声明端点要求的最低鉴权级别，F-5031）。
 *
 * <p>标注在 controller 方法（或类）上，声明该端点需要的最低鉴权级别（{@link AuthLevel#USER}/
 * {@link AuthLevel#ADMIN}/{@link AuthLevel#ROOT}）。由 {@code RequireRoleInterceptor} 在请求进入
 * controller 前统一拦截判定：取当前认证主体 → {@code actor.requireAtLeast(level)}，不满足抛
 * {@code AccessDeniedException}（→403）/未认证抛 {@code AuthenticationRequiredException}（→401）。</p>
 *
 * <p>定位：与 {@code SecurityConfig} 的路径级粗粒度鉴权<b>互补</b>——路径级守住「整片端点」的底线，
 * 方法级注解提供<b>就近、可读、按方法精确</b>的声明（同一 controller 内不同方法可要求不同级别），
 * 是 ROLE-PERMISSION-MATRIX §6 F-5031「AdminAuth/RootAuth 中间件落地」的接口层表达。</p>
 *
 * <p>用法：
 * <pre>{@code
 *   @RequireRole(AuthLevel.ADMIN)
 *   @PostMapping("/manage")
 *   public ApiResponse<Void> manage(...) { ... }
 * }</pre>
 * 类级标注作为该 controller 所有方法的默认级别，方法级标注覆盖类级。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 端点要求的最低鉴权级别。
     *
     * @return 鉴权级别（默认 {@link AuthLevel#USER}，即至少需登录的 common+）
     */
    AuthLevel value() default AuthLevel.USER;
}
