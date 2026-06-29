-- =============================================================================
-- V32: accounts 补 base_url — 上游 endpoint 进 account 凭证池（供应商账号化重构 / round7 阶段4）
--
-- 【背景】"group 汇合(原汁 sub2api)"让转发从 account 取凭证。sub2api 的 account 不仅含 credentials，
-- 还含上游 endpoint(base_url)——不同账号可指向不同上游地址。本迁移给 account 补 base_url，转发链
-- 用 account.base_url + account.credentials 调上游(channel 只管路由/映射/定价，不再持凭证/endpoint)。
--
-- 可空：base_url 为空时转发回落 channel.base_url(阶段4 过渡；阶段6 channel 去凭证后以 account 为准)。
-- =============================================================================

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS base_url VARCHAR(512);

COMMENT ON COLUMN accounts.base_url IS '上游 API base url(可空；转发用 account.base_url，空则回落 channel.base_url)';
