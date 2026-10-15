package com.fitnesspro.core.database.seed

import com.fitnesspro.core.database.entity.FoodDefinitionEntity

/** 食物分类（领域层同步维护一份同名枚举，TEXT 存名） */
object FoodCategories {
    const val MEAT = "MEAT"              // 肉禽鱼虾
    const val EGG_DAIRY = "EGG_DAIRY"    // 蛋奶豆
    const val STAPLE = "STAPLE"          // 主食
    const val VEG_FRUIT = "VEG_FRUIT"    // 蔬菜水果
    const val NUT_OIL = "NUT_OIL"        // 坚果油脂
    const val SUPPLEMENT = "SUPPLEMENT"  // 蛋白补剂
}

/**
 * 常用食物种子（每 50g 营养标尺，蛋白补剂额外带 scoopGrams / proteinPerScoop）。
 *
 * 数值来源：中国食物成分表常见值 + 网络常见外卖/零食/网红食品的约数。
 * 做训练/饮食记录用，精度到"够算盈亏"即可，不冒充医学级营养库。
 * 热量按 蛋白/碳水 4 kcal、脂肪 9 kcal 折算后取整校准。
 */
object FoodSeed {

    /**
     * 基础食物种子。导入时按名称判重 —— 已有的同名食物（用户自建）保留不动。
     * 本列表涵盖：
     *  - 肉禽鱼虾（含常见做法：煎/烤/红烧/卤/外卖常见）
     *  - 蛋奶豆（豆制品、奶制品完整列表）
     *  - 主食（南北通吃 + 网红主食：蛋炒饭、螺蛳粉、煎饼果子、饭团）
     *  - 蔬菜水果（常见 + 网红水果）
     *  - 坚果油脂（含各种食用油）
     *  - 网红零食/外卖/火锅/饮料
     *  - **蛋白补剂**（乳清/浓缩/分离/水解、酪蛋白、鸡蛋蛋白、酵母 等，含一勺克数与一勺蛋白克数）
     */
    val foods: List<FoodDefinitionEntity> = listOf(
        // ---- 肉禽鱼虾（鸡鸭猪牛羊鱼虾，常见做法） ----
        food("鸡胸肉", FoodCategories.MEAT, p = 15.5, f = 1.7, c = 0.4),
        food("鸡胸肉（煎/烤）", FoodCategories.MEAT, p = 16.0, f = 2.5, c = 0.3),
        food("鸡腿肉（去皮）", FoodCategories.MEAT, p = 14.0, f = 4.0, c = 0.0),
        food("鸡腿肉（带皮）", FoodCategories.MEAT, p = 12.0, f = 8.0, c = 0.0),
        food("鸡翅", FoodCategories.MEAT, p = 11.8, f = 11.2, c = 1.0),
        food("鸡翅根", FoodCategories.MEAT, p = 12.5, f = 9.5, c = 0.5),
        food("鸭肉", FoodCategories.MEAT, p = 12.0, f = 10.0, c = 0.5),
        food("鸭胸肉", FoodCategories.MEAT, p = 13.5, f = 5.0, c = 0.0),
        food("牛肉（瘦）", FoodCategories.MEAT, p = 16.0, f = 4.5, c = 0.8),
        food("牛肉（肥瘦）", FoodCategories.MEAT, p = 12.0, f = 15.0, c = 0.5),
        food("牛排（眼肉）", FoodCategories.MEAT, p = 13.5, f = 12.0, c = 0.5),
        food("牛排（西冷）", FoodCategories.MEAT, p = 14.5, f = 10.5, c = 0.5),
        food("牛腩", FoodCategories.MEAT, p = 12.5, f = 16.5, c = 0.0),
        food("牛肉干", FoodCategories.MEAT, p = 18.0, f = 4.5, c = 6.0),
        food("猪里脊", FoodCategories.MEAT, p = 14.0, f = 6.0, c = 0.7),
        food("猪瘦肉", FoodCategories.MEAT, p = 14.5, f = 8.0, c = 0.5),
        food("五花肉", FoodCategories.MEAT, p = 10.0, f = 20.0, c = 0.5),
        food("排骨", FoodCategories.MEAT, p = 10.5, f = 17.0, c = 0.5),
        food("腊肉", FoodCategories.MEAT, p = 11.0, f = 27.0, c = 1.0),
        food("羊肉（瘦）", FoodCategories.MEAT, p = 13.0, f = 8.5, c = 0.0),
        food("羊肉卷（火锅）", FoodCategories.MEAT, p = 12.0, f = 13.0, c = 0.0),
        food("培根", FoodCategories.MEAT, p = 9.0, f = 25.0, c = 1.0),
        food("午餐肉", FoodCategories.MEAT, p = 8.5, f = 15.0, c = 4.5),
        food("火腿", FoodCategories.MEAT, p = 9.5, f = 11.0, c = 4.0),
        food("香肠", FoodCategories.MEAT, p = 8.5, f = 19.0, c = 4.0),
        food("三文鱼", FoodCategories.MEAT, p = 11.0, f = 7.5, c = 0.0),
        food("金枪鱼（罐头/水浸）", FoodCategories.MEAT, p = 12.5, f = 1.0, c = 0.0),
        food("带鱼", FoodCategories.MEAT, p = 13.0, f = 5.0, c = 1.5),
        food("鳕鱼", FoodCategories.MEAT, p = 9.0, f = 0.4, c = 0.0),
        food("草鱼", FoodCategories.MEAT, p = 10.0, f = 4.0, c = 0.5),
        food("罗非鱼", FoodCategories.MEAT, p = 10.5, f = 4.5, c = 0.5),
        food("黄花鱼", FoodCategories.MEAT, p = 12.0, f = 4.5, c = 0.5),
        food("鲈鱼", FoodCategories.MEAT, p = 11.0, f = 3.5, c = 0.0),
        food("龙利鱼", FoodCategories.MEAT, p = 9.0, f = 2.0, c = 0.5),
        food("鳗鱼", FoodCategories.MEAT, p = 11.0, f = 12.0, c = 0.0),
        food("巴沙鱼", FoodCategories.MEAT, p = 9.0, f = 2.0, c = 0.0),
        food("虾", FoodCategories.MEAT, p = 9.5, f = 0.6, c = 0.4),
        food("虾仁", FoodCategories.MEAT, p = 11.0, f = 0.5, c = 0.0),
        food("虾滑", FoodCategories.MEAT, p = 8.0, f = 2.0, c = 2.0),
        food("扇贝肉", FoodCategories.MEAT, p = 6.0, f = 0.4, c = 1.5),
        food("鱿鱼", FoodCategories.MEAT, p = 12.0, f = 2.0, c = 1.0),
        food("章鱼", FoodCategories.MEAT, p = 8.0, f = 1.0, c = 1.5),
        food("螃蟹", FoodCategories.MEAT, p = 9.0, f = 2.0, c = 1.0),

        // ---- 蛋奶豆 ----
        food("鸡蛋（全蛋）", FoodCategories.EGG_DAIRY, p = 6.4, f = 5.6, c = 0.6),
        food("蛋白（蛋清）", FoodCategories.EGG_DAIRY, p = 5.2, f = 0.0, c = 0.4),
        food("蛋黄", FoodCategories.EGG_DAIRY, p = 7.8, f = 26.0, c = 1.6),
        food("卤蛋", FoodCategories.EGG_DAIRY, p = 7.0, f = 6.5, c = 1.0),
        food("茶叶蛋", FoodCategories.EGG_DAIRY, p = 6.5, f = 6.0, c = 1.5),
        food("牛奶（全脂）", FoodCategories.EGG_DAIRY, p = 1.6, f = 1.9, c = 2.4),
        food("牛奶（脱脂）", FoodCategories.EGG_DAIRY, p = 1.7, f = 0.1, c = 2.5),
        food("无糖酸奶", FoodCategories.EGG_DAIRY, p = 1.7, f = 1.6, c = 2.5),
        food("希腊酸奶", FoodCategories.EGG_DAIRY, p = 4.8, f = 2.5, c = 1.8),
        food("奶酪（切达）", FoodCategories.EGG_DAIRY, p = 12.5, f = 16.5, c = 0.6),
        food("奶酪（马苏里拉）", FoodCategories.EGG_DAIRY, p = 11.0, f = 14.0, c = 1.0),
        food("奶酪片", FoodCategories.EGG_DAIRY, p = 6.5, f = 8.5, c = 3.0),
        food("黄油奶酪", FoodCategories.EGG_DAIRY, p = 4.5, f = 18.0, c = 1.0),
        food("豆腐（北）", FoodCategories.EGG_DAIRY, p = 4.0, f = 2.5, c = 1.5),
        food("豆腐（内酯）", FoodCategories.EGG_DAIRY, p = 3.5, f = 2.0, c = 1.5),
        food("豆腐（南）", FoodCategories.EGG_DAIRY, p = 5.0, f = 4.5, c = 1.0),
        food("豆浆（无糖）", FoodCategories.EGG_DAIRY, p = 1.8, f = 0.8, c = 0.7),
        food("豆浆（加糖）", FoodCategories.EGG_DAIRY, p = 1.5, f = 0.8, c = 3.0),
        food("腐竹", FoodCategories.EGG_DAIRY, p = 22.0, f = 10.5, c = 6.5),
        food("豆腐干", FoodCategories.EGG_DAIRY, p = 8.0, f = 3.5, c = 2.5),
        food("豆腐皮", FoodCategories.EGG_DAIRY, p = 20.0, f = 9.0, c = 5.0),
        food("千张", FoodCategories.EGG_DAIRY, p = 12.0, f = 5.0, c = 4.5),

        // ---- 主食（南北通吃 + 网红 / 快餐） ----
        food("米饭（熟）", FoodCategories.STAPLE, p = 1.3, f = 0.2, c = 13.0),
        food("糙米饭（熟）", FoodCategories.STAPLE, p = 1.5, f = 0.4, c = 12.0),
        food("小米饭（熟）", FoodCategories.STAPLE, p = 1.8, f = 0.4, c = 12.5),
        food("面条（熟）", FoodCategories.STAPLE, p = 1.8, f = 0.3, c = 12.5),
        food("挂面（干）", FoodCategories.STAPLE, p = 5.5, f = 0.7, c = 35.0),
        food("馒头", FoodCategories.STAPLE, p = 3.2, f = 0.5, c = 23.0),
        food("花卷", FoodCategories.STAPLE, p = 3.0, f = 1.0, c = 22.0),
        food("包子（猪肉馅）", FoodCategories.STAPLE, p = 4.0, f = 5.0, c = 14.0),
        food("饺子（猪肉馅）", FoodCategories.STAPLE, p = 3.0, f = 5.0, c = 12.0),
        food("馄饨", FoodCategories.STAPLE, p = 2.5, f = 3.0, c = 10.0),
        food("全麦面包", FoodCategories.STAPLE, p = 4.1, f = 1.7, c = 21.0),
        food("白面包", FoodCategories.STAPLE, p = 4.2, f = 1.5, c = 24.0),
        food("吐司", FoodCategories.STAPLE, p = 4.0, f = 2.0, c = 23.0),
        food("贝果", FoodCategories.STAPLE, p = 5.0, f = 1.0, c = 24.0),
        food("可颂", FoodCategories.STAPLE, p = 3.5, f = 10.0, c = 18.0),
        food("法棍", FoodCategories.STAPLE, p = 4.5, f = 1.5, c = 25.0),
        food("燕麦（干）", FoodCategories.STAPLE, p = 6.8, f = 3.5, c = 30.0),
        food("即食燕麦", FoodCategories.STAPLE, p = 4.0, f = 2.0, c = 18.0),
        food("意面（干）", FoodCategories.STAPLE, p = 6.0, f = 0.8, c = 35.0),
        food("红薯", FoodCategories.STAPLE, p = 0.6, f = 0.1, c = 12.0),
        food("土豆", FoodCategories.STAPLE, p = 1.0, f = 0.1, c = 8.3),
        food("玉米（鲜）", FoodCategories.STAPLE, p = 2.1, f = 1.0, c = 11.0),
        food("紫薯", FoodCategories.STAPLE, p = 0.7, f = 0.1, c = 13.5),
        food("藜麦（干）", FoodCategories.STAPLE, p = 7.0, f = 3.0, c = 31.0),
        food("玉米饼", FoodCategories.STAPLE, p = 2.5, f = 1.0, c = 18.0),
        // 网红 / 快餐主食
        food("蛋炒饭", FoodCategories.STAPLE, p = 3.0, f = 4.5, c = 15.0),
        food("炒面", FoodCategories.STAPLE, p = 3.0, f = 5.0, c = 14.0),
        food("螺蛳粉", FoodCategories.STAPLE, p = 3.0, f = 5.0, c = 18.0),
        food("酸辣粉", FoodCategories.STAPLE, p = 1.5, f = 4.5, c = 18.0),
        food("凉皮", FoodCategories.STAPLE, p = 1.0, f = 2.0, c = 12.0),
        food("煎饼果子", FoodCategories.STAPLE, p = 4.5, f = 6.0, c = 18.0),
        food("饭团（紫菜）", FoodCategories.STAPLE, p = 3.0, f = 3.5, c = 18.0),
        food("三明治（火腿）", FoodCategories.STAPLE, p = 5.0, f = 6.0, c = 18.0),

        // ---- 蔬菜水果 ----
        food("西兰花", FoodCategories.VEG_FRUIT, p = 2.0, f = 0.2, c = 2.4),
        food("菠菜", FoodCategories.VEG_FRUIT, p = 1.4, f = 0.2, c = 2.2),
        food("生菜", FoodCategories.VEG_FRUIT, p = 0.7, f = 0.1, c = 1.0),
        food("大白菜", FoodCategories.VEG_FRUIT, p = 0.7, f = 0.1, c = 1.5),
        food("娃娃菜", FoodCategories.VEG_FRUIT, p = 0.8, f = 0.1, c = 1.6),
        food("番茄", FoodCategories.VEG_FRUIT, p = 0.5, f = 0.1, c = 1.8),
        food("圣女果", FoodCategories.VEG_FRUIT, p = 0.6, f = 0.1, c = 2.0),
        food("黄瓜", FoodCategories.VEG_FRUIT, p = 0.4, f = 0.1, c = 1.5),
        food("胡萝卜", FoodCategories.VEG_FRUIT, p = 0.5, f = 0.1, c = 4.3),
        food("白萝卜", FoodCategories.VEG_FRUIT, p = 0.5, f = 0.1, c = 2.5),
        food("蘑菇（鲜）", FoodCategories.VEG_FRUIT, p = 1.4, f = 0.1, c = 1.4),
        food("香菇（鲜）", FoodCategories.VEG_FRUIT, p = 1.2, f = 0.1, c = 1.8),
        food("金针菇", FoodCategories.VEG_FRUIT, p = 1.4, f = 0.1, c = 2.7),
        food("豆芽", FoodCategories.VEG_FRUIT, p = 1.5, f = 0.1, c = 1.5),
        food("茄子", FoodCategories.VEG_FRUIT, p = 0.6, f = 0.1, c = 2.5),
        food("青椒", FoodCategories.VEG_FRUIT, p = 0.7, f = 0.1, c = 3.0),
        food("土豆丝（炒）", FoodCategories.VEG_FRUIT, p = 0.8, f = 2.5, c = 7.0),
        food("地三鲜", FoodCategories.VEG_FRUIT, p = 1.0, f = 6.0, c = 5.5),
        food("麻婆豆腐", FoodCategories.VEG_FRUIT, p = 5.0, f = 7.0, c = 4.0),
        food("苹果", FoodCategories.VEG_FRUIT, p = 0.2, f = 0.1, c = 7.0),
        food("香蕉", FoodCategories.VEG_FRUIT, p = 0.6, f = 0.1, c = 11.4),
        food("橙子", FoodCategories.VEG_FRUIT, p = 0.4, f = 0.1, c = 4.7),
        food("葡萄", FoodCategories.VEG_FRUIT, p = 0.3, f = 0.1, c = 8.0),
        food("蓝莓", FoodCategories.VEG_FRUIT, p = 0.4, f = 0.2, c = 7.2),
        food("草莓", FoodCategories.VEG_FRUIT, p = 0.5, f = 0.1, c = 5.5),
        food("牛油果", FoodCategories.VEG_FRUIT, p = 1.0, f = 7.7, c = 4.3),
        food("芒果", FoodCategories.VEG_FRUIT, p = 0.4, f = 0.1, c = 7.5),
        food("榴莲", FoodCategories.VEG_FRUIT, p = 1.0, f = 0.5, c = 13.5),
        food("猕猴桃", FoodCategories.VEG_FRUIT, p = 0.5, f = 0.1, c = 6.5),
        food("西瓜", FoodCategories.VEG_FRUIT, p = 0.3, f = 0.1, c = 5.5),

        // ---- 坚果油脂 + 网红零食/外卖/饮料 ----
        food("花生", FoodCategories.NUT_OIL, p = 12.0, f = 25.0, c = 6.0),
        food("混合坚果", FoodCategories.NUT_OIL, p = 8.5, f = 28.0, c = 7.0),
        food("核桃", FoodCategories.NUT_OIL, p = 7.5, f = 32.0, c = 4.0),
        food("腰果", FoodCategories.NUT_OIL, p = 9.0, f = 24.0, c = 9.0),
        food("杏仁", FoodCategories.NUT_OIL, p = 10.0, f = 25.0, c = 5.0),
        food("开心果", FoodCategories.NUT_OIL, p = 9.5, f = 24.0, c = 7.0),
        food("瓜子", FoodCategories.NUT_OIL, p = 11.0, f = 23.0, c = 5.0),
        food("芝麻", FoodCategories.NUT_OIL, p = 9.0, f = 23.0, c = 11.0),
        food("食用油（植物油）", FoodCategories.NUT_OIL, p = 0.0, f = 50.0, c = 0.0),
        food("橄榄油", FoodCategories.NUT_OIL, p = 0.0, f = 50.0, c = 0.0),
        food("黄油", FoodCategories.NUT_OIL, p = 0.4, f = 42.0, c = 0.4),
        food("沙拉酱", FoodCategories.NUT_OIL, p = 0.5, f = 35.0, c = 6.0),
        food("蛋黄酱", FoodCategories.NUT_OIL, p = 1.0, f = 35.0, c = 4.5),
        food("巧克力（70%）", FoodCategories.NUT_OIL, p = 3.9, f = 21.0, c = 22.0),
        food("巧克力（牛奶）", FoodCategories.NUT_OIL, p = 3.5, f = 17.0, c = 28.0),
        food("奥利奥饼干", FoodCategories.NUT_OIL, p = 2.5, f = 11.0, c = 31.0),
        food("薯片", FoodCategories.NUT_OIL, p = 2.5, f = 17.0, c = 26.0),
        food("锅巴", FoodCategories.NUT_OIL, p = 4.0, f = 12.0, c = 30.0),
        food("辣条", FoodCategories.NUT_OIL, p = 5.0, f = 15.0, c = 24.0),
        food("饼干（原味）", FoodCategories.NUT_OIL, p = 3.0, f = 10.0, c = 30.0),
        food("蛋糕（普通）", FoodCategories.NUT_OIL, p = 2.5, f = 8.0, c = 22.0),
        food("泡芙", FoodCategories.NUT_OIL, p = 2.5, f = 12.0, c = 18.0),
        // 外卖 / 网红套餐
        food("汉堡（牛肉）", FoodCategories.STAPLE, p = 7.0, f = 7.0, c = 15.0),
        food("汉堡（鸡腿）", FoodCategories.STAPLE, p = 6.5, f = 7.5, c = 15.0),
        food("披萨（芝心）", FoodCategories.STAPLE, p = 5.0, f = 6.5, c = 18.0),
        food("炸鸡腿", FoodCategories.STAPLE, p = 8.5, f = 12.0, c = 4.0),
        food("炸鸡翅", FoodCategories.STAPLE, p = 8.0, f = 13.0, c = 4.0),
        food("薯条（快餐）", FoodCategories.STAPLE, p = 2.0, f = 8.0, c = 18.0),
        food("鸡米花", FoodCategories.STAPLE, p = 6.5, f = 10.0, c = 9.0),
        food("麻辣烫（汤）", FoodCategories.STAPLE, p = 2.0, f = 4.0, c = 4.0),
        food("关东煮（汤+料）", FoodCategories.STAPLE, p = 1.5, f = 2.0, c = 5.0),
        food("烤冷面", FoodCategories.STAPLE, p = 3.0, f = 4.5, c = 18.0),
        food("麻辣香锅", FoodCategories.STAPLE, p = 5.0, f = 9.0, c = 6.0),
        food("火锅丸子拼盘", FoodCategories.STAPLE, p = 5.0, f = 9.0, c = 7.0),
        food("火锅（清汤涮肉）", FoodCategories.STAPLE, p = 6.0, f = 4.0, c = 1.0),
        food("烧烤（羊肉串）", FoodCategories.STAPLE, p = 7.0, f = 9.0, c = 1.5),
        food("烧烤（鸡翅）", FoodCategories.STAPLE, p = 8.0, f = 9.0, c = 2.0),
        // 饮料
        food("可口可乐", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 5.5),
        food("零度可乐", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 0.0),
        food("雪碧", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 5.0),
        food("芬达", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 6.0),
        food("奶茶（标准糖）", FoodCategories.VEG_FRUIT, p = 0.6, f = 1.4, c = 5.5),
        food("奶茶（无糖）", FoodCategories.VEG_FRUIT, p = 0.8, f = 1.5, c = 1.2),
        food("柠檬茶", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 4.5),
        food("咖啡（美式）", FoodCategories.VEG_FRUIT, p = 0.2, f = 0.0, c = 0.0),
        food("拿铁", FoodCategories.VEG_FRUIT, p = 1.0, f = 1.5, c = 1.5),
        food("星冰乐", FoodCategories.VEG_FRUIT, p = 0.8, f = 2.5, c = 8.0),
        food("果汁（100%）", FoodCategories.VEG_FRUIT, p = 0.2, f = 0.0, c = 5.5),
        food("啤酒", FoodCategories.VEG_FRUIT, p = 0.2, f = 0.0, c = 1.8),
        food("白酒（50°）", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 0.0),
        food("椰子水", FoodCategories.VEG_FRUIT, p = 0.2, f = 0.1, c = 2.5),
        food("苏打水", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 0.0),
        food("气泡水", FoodCategories.VEG_FRUIT, p = 0.0, f = 0.0, c = 0.0),

        // ---- 蛋白补剂：每 50g 标尺 + 每勺 (scoopGrams / proteinPerScoop) ----
        //
        // 数值参考口径：
        //  - 蛋白粉标称"X g / 100g 粉"中的 X → 这里填进 proteinPer50g（= X / 2）
        //  - 每勺克数（scoopGrams）按主流品牌标配 ≈ 30g（一满勺）；少数小勺装 25g
        //  - 每勺蛋白（proteinPerScoop）= scoopGrams × (X / 100)
        // 乳清/酪/分离/水解差异：
        //  - Whey Concentrate(WPC) 80% / Isolate(WPI) 90% / Hydrolysate(WPH) 95%
        //  - Casein 80% / Micellar Casein 80%
        //  - Egg White Protein 80% / Yeast Protein 65% / Plant Blend 70%
        // 进阶项（混合 BCAA / 增肌粉）放到后面：粉末肌酸、BCAA、EAA、增肌粉、酪蛋白缓释。

        // —— 主流乳清分三档：浓缩 80%、分离 90%、水解 95% ——
        supplement(
            name = "乳清蛋白粉（标准浓缩 80%）",
            p50 = 40.0,    // 每 50g 粉含 40g 蛋白 = 80%
            f50 = 2.5,
            c50 = 5.0,
            kcal50 = 188.0,  // 校准 ≈ 40×4 + 5×4 + 2.5×9
            scoopGrams = 30,
            proteinPerScoop = 24.0, // = 30 × 0.80
        ),
        supplement(
            name = "浓缩乳清蛋白粉 WPC 80%",
            p50 = 40.0, f50 = 3.0, c50 = 4.5, kcal50 = 187.0,
            scoopGrams = 30, proteinPerScoop = 24.0,
        ),
        supplement(
            name = "分离乳清蛋白粉 WPI 90%",
            p50 = 45.0, f50 = 0.5, c50 = 2.0, kcal50 = 181.0,
            scoopGrams = 30, proteinPerScoop = 27.0,
        ),
        supplement(
            name = "水解乳清蛋白粉 WPH 95%",
            p50 = 47.5, f50 = 0.3, c50 = 1.0, kcal50 = 187.0,
            scoopGrams = 30, proteinPerScoop = 28.5,
        ),
        // —— 酪蛋白类 ——
        supplement(
            name = "酪蛋白（Casein 80%）",
            p50 = 40.0, f50 = 2.0, c50 = 5.0, kcal50 = 187.0,
            scoopGrams = 30, proteinPerScoop = 24.0,
        ),
        supplement(
            name = "胶束酪蛋白（Micellar Casein 80%）",
            p50 = 40.0, f50 = 2.5, c50 = 4.5, kcal50 = 188.0,
            scoopGrams = 32, proteinPerScoop = 25.6, // 32g 一勺（部分品牌）
        ),
        // —— 鸡蛋蛋白 / 酵母蛋白 / 植物混合 ——
        supplement(
            name = "鸡蛋蛋白粉（Egg White Protein 80%）",
            p50 = 40.0, f50 = 1.5, c50 = 4.0, kcal50 = 182.0,
            scoopGrams = 25, proteinPerScoop = 20.0, // 小勺
        ),
        supplement(
            name = "酵母蛋白（Yeast Protein 65%）",
            p50 = 32.5, f50 = 2.0, c50 = 7.0, kcal50 = 168.0,
            scoopGrams = 30, proteinPerScoop = 19.5,
        ),
        supplement(
            name = "植物蛋白混合粉（Plant Blend 70%）",
            p50 = 35.0, f50 = 3.0, c50 = 6.0, kcal50 = 176.0,
            scoopGrams = 30, proteinPerScoop = 21.0,
        ),
        // —— 增肌粉 / 缓释 / 其它 ——
        supplement(
            name = "增肌粉（Mass Gainer）",
            p50 = 15.0, f50 = 2.5, c50 = 30.0, kcal50 = 200.0, // 蛋白只占 ~30%、碳水和糖为主
            scoopGrams = 60, proteinPerScoop = 18.0, // 一勺即一份 60g
        ),
        supplement(
            name = "缓释蛋白粉（Blend 80%）",
            p50 = 40.0, f50 = 2.5, c50 = 4.5, kcal50 = 186.0,
            scoopGrams = 30, proteinPerScoop = 24.0,
        ),
        supplement(
            name = "牛初乳蛋白粉（Bovine Colostrum 70%）",
            p50 = 35.0, f50 = 2.0, c50 = 8.0, kcal50 = 181.0,
            scoopGrams = 30, proteinPerScoop = 21.0,
        ),
    )

    /** 折算常量（与 calorie = p*4 + c*4 + f*9 一致） */
    private const val KCAL_PROTEIN = 4.0
    private const val KCAL_CARB = 4.0
    private const val KCAL_FAT = 9.0

    /**
     * 普通食物快捷构建：每 50g 的蛋白/脂肪/碳水，热量自动校准。
     * scoopGrams / proteinPerScoop 留空（普通食物不需要）。
     */
    private fun food(
        name: String,
        category: String,
        p: Double,
        f: Double,
        c: Double,
    ): FoodDefinitionEntity = FoodDefinitionEntity(
        name = name,
        category = category,
        proteinPer50g = p,
        fatPer50g = f,
        carbPer50g = c,
        kcalPer50g = Math.round(p * KCAL_PROTEIN * 2 + c * KCAL_CARB * 2 + f * KCAL_FAT * 2) / 2.0,
    )

    /**
     * 补剂快捷构建：自带 kcal 校准（厂商标称，避免算法与标称偏差）。
     * scoopGrams / proteinPerScoop 是补剂的关键标识 —— 没有它就不算补剂。
     */
    private fun supplement(
        name: String,
        p50: Double,
        f50: Double,
        c50: Double,
        kcal50: Double,
        scoopGrams: Int,
        proteinPerScoop: Double,
    ): FoodDefinitionEntity = FoodDefinitionEntity(
        name = name,
        category = FoodCategories.SUPPLEMENT,
        proteinPer50g = p50,
        fatPer50g = f50,
        carbPer50g = c50,
        kcalPer50g = kcal50,
        scoopGrams = scoopGrams,
        proteinPerScoop = proteinPerScoop,
    )
}
