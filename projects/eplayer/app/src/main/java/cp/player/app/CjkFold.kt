package cp.player.app

/**
 * Folds Traditional/Japanese/variant Han glyphs and Katakana onto one spelling so cross-catalog
 * identity checks compare the same word. Only one-to-one glyph mappings are listed: no word-level
 * translation is attempted, so a romanized performer name ("Kenshi Yonezu", "Utada Hikaru") still
 * stays distinct from its Han spelling instead of being invented as the same artist.
 */
internal object CjkFold {
    // Traditional -> Simplified plus common variants seen in performer and album names.
    private const val PAIRS =
        "週周倫伦傑杰葉叶陳陈張张劉刘趙赵黃黄吳吴鄭郑馮冯許许鄧邓盧卢韓韩楊杨馬马羅罗鍾钟謝谢" +
        "蘇苏蔣蒋莊庄錢钱孫孙顏颜顧顾歐欧蕭萧陽阳陰阴雙双雲云岡冈樂乐視视聽听聲声鳴鸣愛爱" +
        "戀恋夢梦願愿時时問问間间開开關关門门園园國国團团圓圆圖图書书畫画筆笔寫写說说語语話话" +
        "詩诗誰谁讀读東东車车軌轨轉转遠远遞递進进運运過过達达邊边還还這这連连選选鄉乡醫医釋释" +
        "劍剑劇剧動动務务勞劳勢势區区賣卖買买貴贵質质賽赛贊赞趕赶跡迹蹤踪軍军較较輕轻載载農农" +
        "遊游遙遥鄰邻們们個个來来後后從从會会傳传傷伤僅仅億亿價价儀仪優优兒儿兩两冊册淨净凍冻" +
        "幾几勸劝華华協协單单號号蟲虫術术襲袭誠诚誤误請请論论諸诸謀谋講讲貝贝負负財财貢贡貧贫" +
        "貨货販贩貪贪貫贯責责費费資资賓宾賤贱賞赏賜赐賠赔賢贤贈赠贏赢軟软軸轴輔辅輛辆輪轮輸输" +
        "轟轰辭辞適适遲迟遷迁遺遗鋼钢鏡镜鐵铁鑑鉴長长閉闭閱阅闊阔陸陆隊队階阶隨随險险隱隐雜杂" +
        "雞鸡離离難难電电靈灵靜静響响頁页頭头題题類类風风飛飞飯饭餘余館馆驗验體体鬥斗鳥鸟麗丽" +
        "點点黨党齊齐龍龙龜龟鳳凤衛卫"

    private val MAP: Map<Char, Char> = buildMap {
        var index = 0
        while (index + 1 < PAIRS.length) {
            val from = PAIRS[index]
            val to = PAIRS[index + 1]
            if (from != to) put(from, to)
            index += 2
        }
    }

    /** Katakana -> Hiragana so mixed-script Japanese titles compare equal. */
    private fun kana(value: String): String = buildString(value.length) {
        for (ch in value) append(if (ch in '\u30A1'..'\u30F6') (ch.code - 0x60).toChar() else ch)
    }

    fun apply(value: String): String {
        val source = kana(value)
        val builder = StringBuilder(source.length)
        for (ch in source) builder.append(MAP[ch] ?: ch)
        return builder.toString()
    }
}