package com.fitnesspro.core.domain.calculator

/**
 * 用户输入 → FTS4 查询串。
 *
 * 两个必须处理的问题：
 *  1. **注入与语法错误**：FTS4 的 MATCH 语法对 `" * ( ) : ^ -` 敏感，原始输入直接拼进去会抛异常
 *  2. **中文分词**：FTS4 没有中文分词器，"卧推" 无法匹配 "平板卧推"（因为它按整词索引）。
 *     解决办法是对 CJK 字符逐字加前缀通配（"卧* 推*"），并在 Repository 层同时执行一次
 *     LIKE 子串查询做兜底合并（见 ExerciseDao.searchLike）。
 */
object FtsQueryBuilder {

    /** 判断是否包含 CJK 字符 */
    private fun containsCjk(text: String): Boolean =
        text.any { it.code in 0x2E80..0x9FFF || it.code in 0xF900..0xFAFF || it.code in 0xFF00..0xFFEF }

    /**
     * @return FTS4 MATCH 表达式；输入为空或全是特殊字符时返回 null（调用方应跳过 FTS 查询）
     */
    fun build(raw: String): String? {
        val cleaned = raw.trim()
            .replace(Regex("""["'*():\^\-+\s]+"""), " ")
            .trim()

        if (cleaned.isEmpty()) return null

        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val parts = tokens.map { token ->
            if (containsCjk(token)) {
                // 逐字前缀：保证 "卧推" 能命中 "平板卧推" 的索引项
                token.map { "$it*" }.joinToString(" ")
            } else {
                // 英文用前缀匹配，支持边打字边搜索
                "$token*"
            }
        }

        return parts.joinToString(" AND ")
    }

    /**
     * 中文场景下 FTS 前缀匹配仍可能漏（例如输入 "卧推" 而库中只有 "杠铃卧推" 时 FTS 可以命中，
     * 但输入 "推卧" 就命中不了）。因此只要查询里含 CJK，就同时走一遍 LIKE 兜底。
     */
    fun needsLikeFallback(raw: String): Boolean = containsCjk(raw.trim()) && raw.trim().length >= 2
}
