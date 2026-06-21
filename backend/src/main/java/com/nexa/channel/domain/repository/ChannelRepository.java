package com.nexa.channel.domain.repository;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.channel.domain.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 渠道仓储接口（领域层定义，基础设施层实现，F-2016~F-2028）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，可在单测中用 mock 替换。实现见
 * {@code infrastructure.persistence.ChannelRepositoryImpl}。关联表：V7 {@code channels}。</p>
 */
public interface ChannelRepository {

    /**
     * 保存（新增或更新）渠道。
     *
     * <p>新建（id 为 null）保存后返回携带自增 id 的聚合。</p>
     *
     * @param channel 待保存的渠道聚合
     * @return 持久化后的渠道（新建含 id）
     */
    Channel save(Channel channel);

    /**
     * 按主键查渠道。
     *
     * @param id 渠道主键
     * @return 命中返回聚合，否则空
     */
    Optional<Channel> findById(long id);

    /**
     * 分页 + 多条件过滤查询渠道（F-2016 列表）。
     *
     * <p>group/type/tag/status 均为可空过滤条件（null 表示该维度不过滤）。
     * 返回当前页渠道列表（按 id 升序）。</p>
     *
     * @param group      分组过滤（可空）
     * @param type       type 过滤（可空）
     * @param tag        tag 过滤（可空）
     * @param status     状态过滤（可空）
     * @param pagination 分页参数
     * @return 当前页渠道列表
     */
    List<Channel> findPage(String group, Integer type, String tag, Integer status, Pagination pagination);

    /**
     * 统计满足多条件过滤的渠道总数（F-2016 列表 total）。
     *
     * @param group  分组过滤（可空）
     * @param type   type 过滤（可空）
     * @param tag    tag 过滤（可空）
     * @param status 状态过滤（可空）
     * @return 总条数
     */
    long count(String group, Integer type, String tag, Integer status);

    /**
     * 按关键词分页搜索渠道（F-2016 搜索，名称/模型/分组/标签等关键词匹配）。
     *
     * @param keyword    搜索关键词（可空白→等价无关键词全量）
     * @param pagination 分页参数
     * @return 当前页渠道列表
     */
    List<Channel> search(String keyword, Pagination pagination);

    /**
     * 统计关键词搜索命中的渠道总数（F-2016 搜索 total）。
     *
     * @param keyword 搜索关键词（可空白→全量）
     * @return 命中总条数
     */
    long countSearch(String keyword);

    /**
     * 列出全部渠道（F-2017 全量测试 / F-2018 全量余额更新遍历用）。
     *
     * @return 全部渠道列表
     */
    List<Channel> findAll();

    /**
     * 按 tag 列出渠道（F-2019 按 tag 批量启停）。
     *
     * @param tag 标签（非空白）
     * @return 该 tag 下的渠道列表（可能为空列表）
     */
    List<Channel> findByTag(String tag);

    /**
     * 按 id 集合批量加载渠道（F-2016 批量操作）。
     *
     * @param ids id 集合
     * @return 命中的渠道列表
     */
    List<Channel> findByIds(List<Long> ids);

    /**
     * 删除渠道（F-2016 DELETE）。
     *
     * @param id 渠道主键
     */
    void deleteById(long id);

    /**
     * 批量更新一组渠道的状态（F-2016 批量启停 / F-2019 按 tag 启停的高效落库）。
     *
     * @param ids    渠道 id 集合
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatusByIds(List<Long> ids, ChannelStatus status);
}
