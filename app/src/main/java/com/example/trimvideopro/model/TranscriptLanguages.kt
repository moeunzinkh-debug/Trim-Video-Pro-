package com.example.trimvideopro.model

import java.util.Locale

data class TranscriptLanguage(val code: String, val name: String)

/** Whisper's core multilingual language set. App UI language is independent of source language. */
object TranscriptLanguages {
    const val AUTO = "auto"

    val supported: List<TranscriptLanguage> = """
        af|Afrikaans
        sq|Albanian
        am|Amharic
        ar|Arabic
        hy|Armenian
        as|Assamese
        az|Azerbaijani
        ba|Bashkir
        eu|Basque
        be|Belarusian
        bn|Bengali
        bs|Bosnian
        br|Breton
        bg|Bulgarian
        my|Burmese
        ca|Catalan
        zh|Chinese
        hr|Croatian
        cs|Czech
        da|Danish
        nl|Dutch
        en|English
        et|Estonian
        fo|Faroese
        fi|Finnish
        fr|French
        gl|Galician
        ka|Georgian
        de|German
        el|Greek
        gu|Gujarati
        ht|Haitian Creole
        ha|Hausa
        haw|Hawaiian
        he|Hebrew
        hi|Hindi
        hu|Hungarian
        is|Icelandic
        id|Indonesian
        it|Italian
        ja|Japanese
        jw|Javanese
        kn|Kannada
        kk|Kazakh
        km|Khmer
        ko|Korean
        lo|Lao
        la|Latin
        lv|Latvian
        ln|Lingala
        lt|Lithuanian
        lb|Luxembourgish
        mk|Macedonian
        mg|Malagasy
        ms|Malay
        ml|Malayalam
        mt|Maltese
        mi|Maori
        mr|Marathi
        mn|Mongolian
        ne|Nepali
        no|Norwegian
        nn|Norwegian Nynorsk
        oc|Occitan
        ps|Pashto
        fa|Persian
        pl|Polish
        pt|Portuguese
        pa|Punjabi
        ro|Romanian
        ru|Russian
        sa|Sanskrit
        sr|Serbian
        sn|Shona
        sd|Sindhi
        si|Sinhala
        sk|Slovak
        sl|Slovenian
        so|Somali
        es|Spanish
        su|Sundanese
        sw|Swahili
        sv|Swedish
        tl|Tagalog
        tg|Tajik
        ta|Tamil
        tt|Tatar
        te|Telugu
        th|Thai
        bo|Tibetan
        tr|Turkish
        tk|Turkmen
        uk|Ukrainian
        ur|Urdu
        uz|Uzbek
        vi|Vietnamese
        cy|Welsh
        yi|Yiddish
        yo|Yoruba
    """.trimIndent().lineSequence().map { line ->
        val (code, name) = line.split('|', limit = 2)
        TranscriptLanguage(code, name)
    }.toList()

    private val byCode = supported.associateBy { it.code }
    private val byName = supported.associateBy { it.name.lowercase(Locale.ROOT) }
    private val aliases = mapOf(
        "burmese" to "my", "myanmar" to "my", "mandarin" to "zh", "farsi" to "fa",
        "filipino" to "tl", "javanese" to "jw", "jv" to "jw", "iw" to "he"
    )

    /** Unknown values fall back to detection instead of forcing English or another wrong language. */
    fun normalize(value: String): String {
        val cleaned = value.trim().lowercase(Locale.ROOT)
        return byCode[cleaned]?.code ?: byName[cleaned]?.code ?: aliases[cleaned] ?: AUTO
    }

    /** Omit the API language hint entirely for auto-detection. Never send language=auto. */
    fun apiHint(value: String): String? = normalize(value).takeUnless { it == AUTO }

    fun displayName(value: String): String = byCode[normalize(value)]?.name ?: value
}
