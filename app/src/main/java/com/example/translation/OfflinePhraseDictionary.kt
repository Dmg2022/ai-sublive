package com.example.translation

import java.util.Locale

object OfflinePhraseDictionary {

    private val enToVi = mapOf(
        "hello" to "Xin chào",
        "hi" to "Chào bạn",
        "hey" to "Này",
        "good morning" to "Chào buổi sáng",
        "good afternoon" to "Chào buổi chiều",
        "good evening" to "Chào buổi tối",
        "good night" to "Chúc ngủ ngon",
        "how are you" to "Bạn có khỏe không?",
        "how are you doing" to "Dạo này bạn thế nào?",
        "i am fine" to "Tôi khỏe",
        "thank you" to "Cảm ơn bạn",
        "thanks" to "Cảm ơn",
        "thanks a lot" to "Cảm ơn rất nhiều",
        "thank you very much" to "Cảm ơn bạn rất nhiều",
        "you are welcome" to "Không có chi",
        "welcome" to "Chào mừng",
        "please" to "Làm ơn / Xin vui lòng",
        "sorry" to "Xin lỗi",
        "excuse me" to "Xin lỗi cho hỏi",
        "yes" to "Có / Vâng",
        "no" to "Không",
        "okay" to "Được rồi",
        "ok" to "Được",
        "good" to "Tốt",
        "very good" to "Rất tốt",
        "great" to "Tuyệt vời",
        "awesome" to "Thật tuyệt vời",
        "bad" to "Tệ",
        "not bad" to "Không tệ",
        "see you" to "Hẹn gặp lại",
        "see you later" to "Hẹn gặp lại sau",
        "see you soon" to "Hẹn sớm gặp lại",
        "goodbye" to "Tạm biệt",
        "bye" to "Tạm biệt",
        "bye bye" to "Tạm biệt",
        "what" to "Cái gì",
        "what is this" to "Đây là cái gì?",
        "where" to "Ở đâu",
        "when" to "Khi nào",
        "why" to "Tại sao",
        "how" to "Như thế nào",
        "who" to "Ai",
        "subscribe" to "Đăng ký kênh",
        "like and subscribe" to "Thích và đăng ký kênh",
        "share" to "Chia sẻ",
        "video" to "Video",
        "today" to "Hôm nay",
        "tomorrow" to "Ngày mai",
        "yesterday" to "Hôm qua",
        "now" to "Bây giờ",
        "let's go" to "Đi thôi / Bắt đầu nào",
        "let us start" to "Chúng ta hãy bắt đầu",
        "welcome back" to "Chào mừng quay trở lại",
        "in this video" to "Trong video này",
        "today we are going to" to "Hôm nay chúng ta sẽ",
        "first" to "Đầu tiên",
        "second" to "Thứ hai",
        "finally" to "Cuối cùng",
        "important" to "Quan trọng",
        "problem" to "Vấn đề",
        "question" to "Câu hỏi",
        "answer" to "Câu trả lời"
    )

    fun translate(text: String, sourceLang: String, targetLang: String): String? {
        val normalized = text.trim().lowercase(Locale.ROOT).replace(Regex("[.,!?]+$"), "")
        if (sourceLang == "en" && targetLang == "vi") {
            return enToVi[normalized]
        }
        return null
    }
}
