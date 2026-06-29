package com.nexa.domain.telegram.vo;

import com.nexa.domain.telegram.exception.InvalidTelegramAuthException;

import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Telegram Login Widget 回传授权数据（不可变值对象）。
 *
 * <p>承载 Telegram 登录控件回调带回的全部字段（{@code id/auth_date/hash} 及可选
 * {@code first_name/last_name/username/photo_url} 等）。Telegram 的 HMAC 校验规则要求：
 * 把<b>除 {@code hash} 外</b>的所有收到字段，按<b>键名字典序</b>拼成 {@code key=value} 行、
 * 以换行符 {@code \n} 连接，得到 data-check-string，再用 {@code HMAC-SHA256(secretKey, dcs)}
 * 与回传 {@code hash} 比对（secretKey = SHA256(botToken)）。</p>
 *
 * <p>本 VO 的职责（充血、零框架依赖）：① 校验并暴露关键字段（id/auth_date/hash 必备，F-1051）；
 * ② 提供 {@link #dataCheckString()} 构造规范化校验串（实现 Telegram 协议的「sorted params」），
 * 供 {@link com.nexa.domain.telegram.service.TelegramHmacVerifier} 计算 HMAC（F-1053 防伪的核心）。
 * 字段一旦构造即不可变，避免校验后被改导致 TOCTOU。</p>
 *
 * <p>领域规则来源：BACKLOG T-051/F-1051、T-053/F-1053；openapi {@code GET /api/oauth/telegram/login}
 * 的 {@code id/hash/auth_date} 查询参数。</p>
 */
public final class TelegramAuthData {

    /** Telegram 协议保留键：被签名方计算时<b>排除</b>的字段（它本身即签名值）。 */
    public static final String HASH_KEY = "hash";

    /** Telegram 协议字段：授权时间戳（epoch 秒），用于重放窗口校验。 */
    public static final String AUTH_DATE_KEY = "auth_date";

    /** Telegram 协议字段：用户 id。 */
    public static final String ID_KEY = "id";

    /** 参与签名的全部字段（不可变副本，已排除空值，含 id/auth_date 等）。 */
    private final SortedMap<String, String> signedFields;

    /** 回传的签名值（hex 小写，64 字符的 HMAC-SHA256）。 */
    private final String hash;

    /** 解析后的用户 id 值对象。 */
    private final TelegramId telegramId;

    /** 解析后的授权时间戳（epoch 秒）。 */
    private final long authDate;

    private TelegramAuthData(SortedMap<String, String> signedFields, String hash,
                             TelegramId telegramId, long authDate) {
        this.signedFields = signedFields;
        this.hash = hash;
        this.telegramId = telegramId;
        this.authDate = authDate;
    }

    /**
     * 由 Login Widget 回传的原始参数表构造授权数据（工厂方法，校验不变量）。
     *
     * <p>规则：① {@code hash} 必须存在且为非空 hex（缺失即非法回调）；② {@code id} 必须存在且为合法
     * {@link TelegramId}；③ {@code auth_date} 必须存在且可解析为非负整数（重放窗口校验依赖它）。
     * 其余字段（first_name/username/photo_url 等）原样纳入签名集合——Telegram 要求<b>对收到的所有
     * 非 hash 字段</b>签名，不能只挑 id/auth_date，否则攻击者可注入未签名字段绕过校验。空值字段被剔除
     * （Telegram 不发空值字段；保留空值会污染 data-check-string 导致误判）。</p>
     *
     * @param params Login Widget 回传的全部查询参数（键→值；值可为 null/空，将被剔除）
     * @return 不可变授权数据 VO
     * @throws InvalidTelegramAuthException 当 hash/id/auth_date 缺失或非法时
     */
    public static TelegramAuthData fromParams(Map<String, String> params) {
        Objects.requireNonNull(params, "params");

        String hash = trimToNull(params.get(HASH_KEY));
        if (hash == null) {
            // 无签名值无法校验来源真伪，直接拒绝（F-1053）。
            throw new InvalidTelegramAuthException("telegram auth hash is missing");
        }
        // hash 必须是 64 位 hex（HMAC-SHA256 十六进制）；格式不符即视为伪造。
        if (!isHex64(hash)) {
            throw new InvalidTelegramAuthException("telegram auth hash format is invalid");
        }

        TelegramId telegramId = TelegramId.of(params.get(ID_KEY));

        String authDateRaw = trimToNull(params.get(AUTH_DATE_KEY));
        if (authDateRaw == null) {
            throw new InvalidTelegramAuthException("telegram auth_date is missing");
        }
        long authDate;
        try {
            authDate = Long.parseLong(authDateRaw);
        } catch (NumberFormatException e) {
            // wrap 带上下文，不吞错——auth_date 非数字是篡改/伪造信号。
            throw new InvalidTelegramAuthException("telegram auth_date must be a unix timestamp");
        }
        if (authDate < 0) {
            throw new InvalidTelegramAuthException("telegram auth_date must be non-negative");
        }

        // 构造签名集合：纳入除 hash 外的所有非空字段，按键字典序（TreeMap）。
        SortedMap<String, String> signed = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            if (key == null || HASH_KEY.equals(key)) {
                continue;
            }
            String value = e.getValue();
            if (value == null || value.isEmpty()) {
                continue; // Telegram 不发空值字段，剔除以免污染 data-check-string。
            }
            signed.put(key, value);
        }

        return new TelegramAuthData(java.util.Collections.unmodifiableSortedMap(signed),
                hash.toLowerCase(), telegramId, authDate);
    }

    /**
     * 构造 Telegram 协议的 data-check-string（签名输入）。
     *
     * <p>实现规范：把参与签名的字段按<b>键名字典序</b>排列，逐行拼成 {@code key=value}，
     * 行间用换行符 {@code \n} 连接（末尾无多余换行）。这是 Telegram 校验 {@code hash} 的唯一约定输入，
     * 「sorted params」即指此。本方法与 {@link #fromParams} 的字段集合一致（不含 hash）。</p>
     *
     * @return 规范化的 data-check-string
     */
    public String dataCheckString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : signedFields.entrySet()) {
            if (!first) {
                sb.append('\n');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    /** @return 回传的签名值（hex 小写） */
    public String hash() {
        return hash;
    }

    /** @return 用户 id 值对象 */
    public TelegramId telegramId() {
        return telegramId;
    }

    /** @return 授权时间戳 epoch 秒 */
    public long authDate() {
        return authDate;
    }

    /** @return 参与签名的字段只读视图（调试/审计用，已不可变） */
    public Map<String, String> signedFields() {
        return signedFields;
    }

    /**
     * trim 后归一空白为 null（便于「缺失」判定）。
     *
     * @param s 原始值
     * @return trim 后的非空串，或 null
     */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 是否为恰好 64 位的十六进制串（HMAC-SHA256 的 hex 表示长度）。
     *
     * @param s 待判定串
     * @return 是返回 true
     */
    private static boolean isHex64(String s) {
        if (s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
