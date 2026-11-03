package com.sarim.app.data

/**
 * The content that is blocked from the first launch, in every language, and that nothing in the
 * app can remove.
 *
 * The user's own additions live next to these — a browser list, or a word typed into the
 * blocked-words screen — but the entries here are re-applied on every launch by
 * [com.sarim.app.security.MandatoryProtection], so deleting them from the UI only makes them come
 * back on the next start (the screens also refuse to save a list with them missing).
 */
object ProtectionDefaults {

    /** Device-owner private DNS: filters adult domains before a browser ever resolves them. */
    const val FILTERING_DNS_HOST = "family.cloudflare-dns.com"

    /**
     * Hosts the private-DNS picker offers. Every one of them is a family/adult-filtering
     * resolver; there is deliberately no "automatic" or "off", because a resolver of the user's
     * choosing is the shortest route around URL blocking, and a VPN is blocked separately by
     * [com.sarim.app.security.MandatoryProtection.RESTRICTIONS].
     */
    val FILTERING_DNS_HOSTS: List<String> = listOf(
        "family.cloudflare-dns.com" to "Cloudflare Family",
        "family.adguard-dns.com" to "AdGuard Family",
        "adult-filter-dns.cleanbrowsing.org" to "CleanBrowsing Adult"
    ).let { it.map { (host, _) -> host } }

    val FILTERING_DNS_LABELS: Map<String, String> = mapOf(
        "family.cloudflare-dns.com" to "Cloudflare Family",
        "family.adguard-dns.com" to "AdGuard Family",
        "adult-filter-dns.cleanbrowsing.org" to "CleanBrowsing Adult"
    )

    /**
     * The always-present `URLBlocklist` list pushed into every browser that supports managed
     * restrictions. Kept as bare hosts: `URLBlocklist` matches the host and its subdomains, so
     * `pornhub.com` covers `www.pornhub.com` and `m.pornhub.com` without extra entries.
     */
    const val MANDATORY_BROWSER_LIST_ID = "sarim-mandatory-adult-sites"

    val ADULT_SITES: List<String> = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "spankbang.com", "tube8.com", "porntrex.com", "txxx.com",
        "eporner.com", "beeg.com", "brazzers.com", "bangbros.com", "realitykings.com",
        "mofos.com", "naughtyamerica.com", "digitalplayground.com", "evilangel.com",
        "private.com", "21sextury.com", "dorcelclub.com", "sexart.com", "blacked.com",
        "vixen.com", "tushy.com", "deeper.com", "hentaihaven.org", "hanime.tv",
        "nhentai.net", "e-hentai.org", "exhentai.org", "hentai2read.com", "hitomi.la",
        "rule34.xxx", "rule34.paheal.net", "gelbooru.com", "danbooru.donmai.us",
        "sankakucomplex.com", "fakku.net", "tsumino.com", "literotica.com", "nifty.org",
        "asstr.org", "chaturbate.com", "stripchat.com", "cam4.com", "bongacams.com",
        "livejasmin.com", "myfreecams.com", "onlyfans.com", "fansly.com", "manyvids.com",
        "clips4sale.com", "justfor.fans", "adultfriendfinder.com", "ashleymadison.com",
        "seeking.com", "escortdirectory.com", "eros.com", "skipthegames.com",
        "motherless.com", "efukt.com", "bestgore.com", "theync.com", "8kun.top",
        "pornmd.com", "porn.com", "sex.com", "adultdvdtalk.com", "tubegalore.com",
        "thumbzilla.com", "porn300.com", "pornone.com", "hqporner.com", "yespornplease.com",
        "drtuber.com", "sexvid.xxx", "pornhat.com", "porn00.org", "hotmovs.com"
    )

    /**
     * Words the accessibility service refuses to let across the keyboard, or — once a per-app
     * passive list has been added — on screen. Written in lowercase; matching is
     * case-insensitive and word-boundary based.
     *
     * The English entries skew to unambiguous adult vocabulary: a broad list (the word "sex" on
     * its own, for example) would block health and news content the user still needs, and the
     * adult *sites* are covered by DNS and URL blocking instead.
     */
    val FORBIDDEN_WORDS: Set<String> = setOf(
        // English
        "porn", "porno", "pornography", "pornstar", "xxx", "nsfw", "hentai", "rule34",
        "nude", "nudes", "nudity", "cumshot", "blowjob", "handjob", "anal", "creampie",
        "gangbang", "threesome", "orgy", "milf", "dildo", "vibrator", "masturbation",
        "masturbate", "orgasm", "camgirl", "camsex", "sexting", "incest", "lolicon",
        "shotacon", "bukakke", "bukkake", "deepthroat", "facial", "fisting", "fetish",
        "bdsm", "escort", "brothel", "stripper", "striptease", "onlyfans", "nympho",
        "squirting", "shemale", "transsexual porn", "bareback", "gloryhole", "upskirt",
        // Arabic
        "إباحي", "إباحية", "اباحي", "اباحية", "عاري", "عارية", "سكس", "جنس صريح",
        "شهواني", "شهوانية", "مومس", "دعارة", "بورنو", "هنتاي",
        // Portuguese
        "pornô", "pornografia", "pornográfico", "pornôs", "putaria", "nudes", "pelada",
        "pelado", "sexo explícito", "prostituição", "prostituta", "hentai", "punheta",
        "orgia", "suruba", "xoxota", "pau duro", "boquete"
    )

    /** The browser list object that [MANDATORY_BROWSER_LIST_ID] always resolves to. */
    fun mandatoryBrowserListTitle(): String = "Sarim — adult sites (always on)"
}
