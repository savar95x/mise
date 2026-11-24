package dev.savar.mise

import java.time.LocalDate

/** Sample journal for tests and screenshots. */
object Fixtures {
    private fun day(daysAgo: Long) = LocalDate.now().minusDays(daysAgo).toEpochDay()

    val interstellar = Title("movie:157336", Kind.MOVIE, "Interstellar", 2014, 157336, credit = "Christopher Nolan", runtime = 169,
        genres = listOf("Adventure", "Drama", "Science Fiction"),
        overview = "The adventures of a group of explorers who make use of a newly discovered wormhole to surpass the limitations on human space travel.")
    val dunkirk = Title("movie:374720", Kind.MOVIE, "Dunkirk", 2017, 374720, credit = "Christopher Nolan", runtime = 107)
    val sinners = Title("movie:1233413", Kind.MOVIE, "Sinners", 2025, 1233413, credit = "Ryan Coogler", runtime = 138)
    val arcane = Title("tv:94605", Kind.TV, "Arcane", 2021, 94605, credit = "Christian Linke, Alex Yee", seasons = 2)
    val shutter = Title("movie:11324", Kind.MOVIE, "Shutter Island", 2010, 11324, credit = "Martin Scorsese", runtime = 138)
    val godfather = Title("movie:238", Kind.MOVIE, "The Godfather", 1972, 238, credit = "Francis Ford Coppola", watchlistAt = 5)
    val odyssey = Title("movie:62", Kind.MOVIE, "2001: A Space Odyssey", 1968, 62, credit = "Stanley Kubrick", watchlistAt = 4)
    val samurai = Title("movie:346", Kind.MOVIE, "Seven Samurai", 1954, 346, credit = "Akira Kurosawa", watchlistAt = 3)
    val severance = Title("tv:95396", Kind.TV, "Severance", 2022, 95396, credit = "Dan Erickson", watchlistAt = 2)
    val chinatown = Title("movie:829", Kind.MOVIE, "Chinatown", 1974, 829, credit = "Roman Polanski", watchlistAt = 1)

    val titles = listOf(interstellar, dunkirk, sinners, arcane, shutter, godfather, odyssey, samurai, severance, chinatown)

    val watchInterstellar = Watch(
        id = "w1", titleId = interstellar.id, day = day(0), tier = Tier.ALL_TIME,
        scores = mapOf(Axis.DIRECTION to 9.5f, Axis.SOUND to 10f, Axis.ENGAGEMENT to 9f),
        setup = setOf(Setup.UHD, Setup.SURROUND, Setup.DARK, Setup.ALONE),
        mood = "Wiped out after placement prep, wanted something enormous",
        thoughts = "Third time and the docking scene still wrecks me. The organ does half the work. People who say the love speech ruins it are watching for the plot, not the film.",
        line = "Still the docking scene.",
        memos = listOf(Memo("none.m4a", 214_000, 0)),
        createdAt = 3,
    )

    val watches = listOf(
        Watch(id = "w0", titleId = interstellar.id, day = day(400), tier = Tier.ALL_TIME, createdAt = 1),
        watchInterstellar,
        Watch(id = "w2", titleId = sinners.id, day = day(3), tier = Tier.GREAT, setup = setOf(Setup.CINEMA), thoughts = "Loud, sweaty, alive.", createdAt = 2),
        Watch(id = "w3", titleId = arcane.id, day = day(9), season = "Season 2", tier = Tier.ALL_TIME, createdAt = 4),
        Watch(id = "w4", titleId = dunkirk.id, day = day(40), tier = Tier.GREAT, createdAt = 5),
        Watch(id = "w5", titleId = shutter.id, day = day(44), tier = Tier.GOOD, createdAt = 6),
    )

    fun seed() {
        Images.offline = true
        Store.seed(titles, watches, Settings(tmdbToken = "x".repeat(40)))
    }
}
