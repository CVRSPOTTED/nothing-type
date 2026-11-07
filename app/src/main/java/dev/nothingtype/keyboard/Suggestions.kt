package dev.nothingtype.keyboard

import java.util.Locale

/** Authored offline lexicon with prefix completion and conservative one-edit correction. */
class Suggestions {
    private val words = mapOf(
        Language.RU to """привет приветик приветствие пока пожалуйста спасибо сегодня сейчас завтра вчера утро утром вечер вечером день дней ночь ночью добро добрый доброе добрая хорошо хороший хорошая хорошо отлично отлично давай давайте можно может можете могу можем нужно нужен нужна надо нет конечно конечно понятно понимаю понял поняла почему потому поэтому когда куда откуда здесь там домой дома работа работать работаю работы рабочий встреча встретиться встречаемся время времени минут минута минуты час часов сообщение сообщения написать написать напиши пишу письмо письма отправить отправлю получил получила получать звонок позвонить позвоню телефон телефона номер номера человек люди друг друзья подруга семья мама папа брат сестра ребёнок дети любовь люблю любимый любимая скучаю обнимаю целую поздравляю поздравления праздник подарки подарок рождения счастья здоровья успехов удачи спасибо благодарю извините извини простите прости простое просто вместе рядом далеко близко быстро быстрее медленно поздно рано скоро уже ещё только тоже очень больше меньше немного много сколько несколько первый второй третий последний новый новая новое новости старый большой маленький красивый интересный интересная интересно важный важно вопрос вопросы ответ ответы помощь помоги помочь помогите проблема проблемы решение решить работает работаете работаешь будет будем буду будешь были было была быть есть ем еда обед ужин завтрак кофе чай вода хлеб молоко магазин купить покупка продукты доставка заказ заказать адрес улица город поезд метро автобус машина дорога ехать еду идём идти пешком встречаем доставка интернет компьютер приложение клавиатура настройки настроить обновление скачать установить включить выключить пароль файл файлы папка документ проект программа программирование разработка тест тесты проверка проверить работает ошибка ошибки исправить исправлено готов готово готова готовить готовлю смотреть смотрю фильм фильмы музыка слушать слушаю читать читаю книга книги игра играть играю фото фотография фотографировать видео запись голос сообщение погода дождь снег солнце холодно тепло жарко лето зима весна осень отпуск выходные понедельник вторник среда четверг пятница суббота воскресенье январь февраль март апрель май июнь июль август сентябрь октябрь ноябрь декабрь российский русский английский язык раскладка безопасность приватность встречусь напомни напоминание согласен согласна думаю думать надеюсь кажется действительно обязательно возможно наверное необходимо удобно удобный удобно установить скорость быстрый мгновенно приветствую пример прекрасно предложение предложения предложить продолжить продолжение проверю пришли пришлю отправь отправлю спасибо спасибочки спокойной сладких добраться доберусь сообщи сообщу подожди подожду через после перед между около сразу снова всегда никогда иногда часто обычно обязательно бесплатно стоимость цена деньги карта оплатить оплата перевод перевести рубль рублей тысяча тысяч миллион один одна одно два две три четыре пять шесть семь восемь девять десять двадцать тридцать сорок пятьдесят сто двести триста сейчас скоро завтра вчера""",
        Language.EN to """hello hey hi help helpful helping thanks thank thankful welcome please sorry tomorrow today tonight yesterday morning afternoon evening night good great goodbye nice wonderful amazing awesome happy birthday congratulations wishes love lovely loved miss missing friend friends family mother father brother sister child children home house work working works worked meeting meet message messages write writing written send sending sent receive received call calling phone number contact email letter address street city country world travel train bus car road walk walking arrive arrival order ordering delivery delivered package buy buying bought shop shopping store price money payment pay paid card bank account coffee tea water milk bread breakfast lunch dinner food restaurant book booking read reading music listen listening movie movies video photo picture camera game play playing weather rainy rain snow sunny sun cold warm hot winter spring summer autumn holiday weekend monday tuesday wednesday thursday friday saturday sunday january february march april may june july august september october november december time minute minutes hour hours day days week weeks month months year years early late soon later now already again always never sometimes often usually before after between about above below together alone near far much many more most less little small large big new old first second third last next previous same different right left correct incorrect wrong maybe probably certainly definitely possible impossible important information question questions answer answers problem problems solution solve solved fix fixed error errors test tests testing check checked checking ready done complete completed understand understood know knowing think thinking thought believe hope hoping want wanted need needed have having has had would could should will shall can cannot must might make making made get getting got give giving gave take taking took come coming came go going went see seeing saw look looking watch watching wait waiting tell telling told speak speaking spoken start starting stop stopped continue change changed update updated download install installed open close closed save saved delete remove search find found choose select selected settings keyboard application apps computer code coding project program programming development developer software system network internet online offline secure security private privacy password file files folder document text word words language english russian speed fast faster instant voice sound microphone animation animated background beautiful simple clean smooth mobile device screen width height position button buttons touch input suggestion suggestions suggest support supported available enabled disabled activate turn welcome excellent sorry excuse promise remember remind reminder tomorrow together appointment schedule calendar report review request reply response let lets really very just only also even enough because although however therefore while where when which what why how who whose yes okay alright sure fine well best better enjoy enjoyed congratulations conversation connection performance quick quickly typing type typed nothing something anything everything everyone someone anyone nobody"""
    ).mapValues { (_, text) -> text.trim().split(Regex("\\s+")).distinct() }
    // Precompute a tiny prefix index once; no filtering of the dictionary on each tap.
    private val index = words.mapValues { (_, list) ->
        buildMap<String, MutableList<String>> {
            list.forEach { word ->
                for (length in 1 until word.length) {
                    val candidates = getOrPut(word.take(length)) { mutableListOf() }
                    if (candidates.size < 3) candidates.add(word)
                }
            }
        }
    }
    private val known = words.mapValues { (_, list) -> list.toHashSet() }

    fun candidates(input: String, language: Language): List<String> =
        (complete(input, language) + nearby(input, language)).distinct().take(3)

    fun correction(input: String, language: Language): String? =
        if (complete(input, language).isEmpty()) nearby(input, language).singleOrNull() else null

    private fun nearby(input: String, language: Language): List<String> {
        if (input.length !in 4..48 || !input.all(Char::isLetter)) return emptyList()
        val lower = input.lowercase(Locale.ROOT)
        if (lower in known.getValue(language)) return emptyList()
        return words.getValue(language).asSequence()
            .filter { kotlin.math.abs(it.length - lower.length) <= 1 && oneEditApart(lower, it) }
            .take(4)
            .map { matchCase(input, it) }
            .toList()
    }

    private fun oneEditApart(input: String, word: String): Boolean {
        val limit = minOf(input.length, word.length)
        var first = 0
        while (first < limit && input[first] == word[first]) first++
        if (first == limit) return input.length != word.length
        return when {
            input.length < word.length -> input.regionMatches(first, word, first + 1, input.length - first)
            input.length > word.length -> word.regionMatches(first, input, first + 1, word.length - first)
            input.regionMatches(first + 1, word, first + 1, input.length - first - 1) -> true
            first + 1 < limit && input[first] == word[first + 1] && input[first + 1] == word[first] ->
                input.regionMatches(first + 2, word, first + 2, input.length - first - 2)
            else -> false
        }
    }

    private fun matchCase(input: String, word: String): String = when {
        input.all(Char::isUpperCase) && input.length > 1 -> word.uppercase(Locale.ROOT)
        input.first().isUpperCase() -> word.replaceFirstChar(Char::titlecase)
        else -> word
    }
    fun complete(prefix: String, language: Language): List<String> {
        if (prefix.isEmpty() || prefix.length > 48 || !prefix.all(Char::isLetter)) return emptyList()
        return index[language]?.get(prefix.lowercase(Locale.ROOT)).orEmpty().map { matchCase(prefix, it) }
    }
}
