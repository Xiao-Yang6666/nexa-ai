package com.nexa.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 领域分页参数 → Spring Data {@link Pageable} 的统一转换工具（基础设施层共享构件）。
 *
 * <p>背景：各 bounded context 的领域 {@code Pagination} 值对象统一约定「页号从 1 起」，而 Spring Data
 * {@link PageRequest} 的页索引从 0 起。各 {@code XxxRepositoryImpl} 因此各自手写
 * {@code PageRequest.of(pagination.page() - 1, pagination.pageSize())} 做减一转换（≥12 份散落）。
 * 本工具把「1-based 页号 → 0-based PageRequest」这一纯转换逻辑收敛到一处，消除散落的 {@code -1}
 * 易错样板（漏减一/多减一是分页越界的常见根因）。</p>
 *
 * <p>定位：无状态纯函数工具（全 static），不持有任何依赖，不进 domain（domain 不感知 Spring Data）。
 * 各域领域 {@code Pagination} 形态同构（page/pageSize 两字段）但分属各自包，故本工具按基础类型
 * {@code (int page1Based, int pageSize)} 取参，不耦合任何具体 {@code Pagination} 类型。</p>
 */
public final class PageQueries {

    private PageQueries() {
    }

    /**
     * 1-based 页号 + 每页条数 → Spring Data {@link Pageable}（0-based）。
     *
     * @param page1Based 页号（从 1 起，领域约定；&lt;1 归一为 1，宽容处理避免越界异常）
     * @param pageSize   每页条数（&lt;1 归一为 1）
     * @return Spring Data 分页请求（页索引已减一）
     */
    public static Pageable of(int page1Based, int pageSize) {
        int pageIndex = Math.max(0, page1Based - 1);
        int size = Math.max(1, pageSize);
        return PageRequest.of(pageIndex, size);
    }

    /**
     * 带排序的 1-based 页号 → {@link Pageable}。
     *
     * @param page1Based 页号（从 1 起；&lt;1 归一为 1）
     * @param pageSize   每页条数（&lt;1 归一为 1）
     * @param sort       排序规则
     * @return Spring Data 分页请求（页索引已减一）
     */
    public static Pageable of(int page1Based, int pageSize, Sort sort) {
        int pageIndex = Math.max(0, page1Based - 1);
        int size = Math.max(1, pageSize);
        return PageRequest.of(pageIndex, size, sort);
    }
}
