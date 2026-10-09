package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.httpPostJsonWithHeaders
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AnilistMetadataService {
    private val log = Logger.withTag("AnilistMetadataService")
    private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val headers = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
    )

    private val detailsCache = linkedMapOf<Int, MetaDetails>()
    private val searchCache = linkedMapOf<String, List<MetaPreview>>()
    private val cacheMutex = Mutex()

    private const val MEDIA_DETAILS_QUERY = """
        query (${'$'}id: Int, ${'$'}idMal: Int) {
          Media(id: ${'$'}id, idMal: ${'$'}idMal, type: ANIME) {
            id
            idMal
            title { romaji english native }
            format
            status
            description(asHtml: false)
            startDate { year month day }
            endDate { year month day }
            season
            seasonYear
            episodes
            duration
            countryOfOrigin
            isAdult
            genres
            synonyms
            averageScore
            meanScore
            popularity
            coverImage { extraLarge large medium color }
            bannerImage
            studios(isMain: true) { nodes { id name } }
            trailer { id site thumbnail }
            nextAiringEpisode { airingAt timeUntilAiring episode }
            characters(sort: [ROLE, RELEVANCE], perPage: 25) {
              edges {
                role
                node { id name { full native userPreferred } image { large medium } }
                voiceActors(language: JAPANESE) { id name { full userPreferred } image { large medium } }
              }
            }
            recommendations(sort: [RATING_DESC], perPage: 20) {
              nodes {
                mediaRecommendation {
                  id
                  title { romaji english native }
                  coverImage { extraLarge large medium }
                  bannerImage
                  format
                  averageScore
                  episodes
                  status
                  genres
                  seasonYear
                  startDate { year }
                }
              }
            }
            streamingEpisodes { title thumbnail url site }
            relations {
              edges {
                relationType
                node {
                  id
                  title { romaji english native }
                  format
                  coverImage { large }
                }
              }
            }
          }
        }
    """

    private const val MEDIA_DETAILS_BY_TITLE_QUERY = """
        query (${'$'}search: String) {
          Media(search: ${'$'}search, type: ANIME) {
            id
            idMal
            title { romaji english native }
            format
            status
            description(asHtml: false)
            startDate { year month day }
            endDate { year month day }
            season
            seasonYear
            episodes
            duration
            countryOfOrigin
            isAdult
            genres
            synonyms
            averageScore
            meanScore
            popularity
            coverImage { extraLarge large medium color }
            bannerImage
            studios(isMain: true) { nodes { id name } }
            trailer { id site thumbnail }
            nextAiringEpisode { airingAt timeUntilAiring episode }
            characters(sort: [ROLE, RELEVANCE], perPage: 25) {
              edges {
                role
                node { id name { full native userPreferred } image { large medium } }
                voiceActors(language: JAPANESE) { id name { full userPreferred } image { large medium } }
              }
            }
            recommendations(sort: [RATING_DESC], perPage: 20) {
              nodes {
                mediaRecommendation {
                  id
                  title { romaji english native }
                  coverImage { extraLarge large medium }
                  bannerImage
                  format
                  averageScore
                  episodes
                  status
                  genres
                  seasonYear
                  startDate { year }
                }
              }
            }
            streamingEpisodes { title thumbnail url site }
            relations {
              edges {
                relationType
                node {
                  id
                  title { romaji english native }
                  format
                  coverImage { large }
                }
              }
            }
          }
        }
    """

    private const val SEARCH_QUERY = """
        query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(search: ${'$'}search, type: ANIME, sort: [SEARCH_MATCH, POPULARITY_DESC]) {
              id
              title { romaji english native }
              format
              status
              episodes
              bannerImage
              coverImage { extraLarge large medium }
              averageScore
              startDate { year }
              seasonYear
              genres
            }
          }
        }
    """

    private const val TRENDING_QUERY = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: [TRENDING_DESC]) {
              id
              title { romaji english native }
              format
              status
              episodes
              bannerImage
              coverImage { extraLarge large medium }
              averageScore
              startDate { year }
              seasonYear
              genres
            }
          }
        }
    """

    private const val POPULAR_QUERY = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: [POPULARITY_DESC]) {
              id
              title { romaji english native }
              format
              status
              episodes
              bannerImage
              coverImage { extraLarge large medium }
              averageScore
              startDate { year }
              seasonYear
              genres
            }
          }
        }
    """

    suspend fun fetchAnimeDetails(id: Int? = null, idMal: Int? = null): MetaDetails? {
        if (id == null && idMal == null) return null
        val cacheKey = id ?: (-(idMal ?: 0))
        cacheMutex.withLock {
            detailsCache[cacheKey]?.let { return it }
        }

        val requestBody = buildJsonObject {
            put("query", MEDIA_DETAILS_QUERY)
            put("variables", buildJsonObject {
                if (id != null) put("id", id)
                if (idMal != null) put("idMal", idMal)
            })
        }

        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistMediaData>>(responseText)
            val details = parsed.data?.media?.toMetaDetails()
            if (details != null) {
                cacheMutex.withLock {
                    detailsCache[cacheKey] = details
                    details.id.removePrefix("anilist:").toIntOrNull()?.let { alId ->
                        detailsCache[alId] = details
                    }
                }
            }
            details
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to fetch AniList details for id=$id idMal=$idMal" }
            null
        }
    }

    suspend fun searchAnimeDetails(query: String): MetaDetails? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        val requestBody = buildJsonObject {
            put("query", MEDIA_DETAILS_BY_TITLE_QUERY)
            put("variables", buildJsonObject { put("search", trimmed) })
        }

        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistMediaData>>(responseText)
            val details = parsed.data?.media?.toMetaDetails()
            if (details != null) {
                val mediaId = parsed.data?.media?.id
                if (mediaId != null) {
                    cacheMutex.withLock {
                        detailsCache[mediaId] = details
                    }
                }
            }
            details
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to search AniList details for query=$trimmed" }
            null
        }
    }

    suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): List<MetaPreview> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val cacheKey = "$trimmed:$page:$perPage"
        cacheMutex.withLock {
            searchCache[cacheKey]?.let { return it }
        }

        val requestBody = buildJsonObject {
            put("query", SEARCH_QUERY)
            put("variables", buildJsonObject {
                put("search", trimmed)
                put("page", page)
                put("perPage", perPage)
            })
        }

        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistPageData>>(responseText)
            val items = parsed.data?.page?.media?.map { it.toMetaPreview() } ?: emptyList()
            cacheMutex.withLock {
                searchCache[cacheKey] = items
            }
            items
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to search anime: $trimmed" }
            emptyList()
        }
    }

    suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<MetaPreview> {
        val requestBody = buildJsonObject {
            put("query", TRENDING_QUERY)
            put("variables", buildJsonObject {
                put("page", page)
                put("perPage", perPage)
            })
        }

        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistPageData>>(responseText)
            parsed.data?.page?.media?.map { it.toMetaPreview() } ?: emptyList()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to fetch trending anime" }
            emptyList()
        }
    }

    suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<MetaPreview> {
        val requestBody = buildJsonObject {
            put("query", POPULAR_QUERY)
            put("variables", buildJsonObject {
                put("page", page)
                put("perPage", perPage)
            })
        }

        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistPageData>>(responseText)
            parsed.data?.page?.media?.map { it.toMetaPreview() } ?: emptyList()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to fetch popular anime" }
            emptyList()
        }
    }

    private const val STAFF_QUERY = """
        query (${'$'}id: Int) {
          Staff(id: ${'$'}id) {
            id
            name { full native userPreferred }
            image { large medium }
            description(asHtml: false)
            dateOfBirth { year month day }
            dateOfDeath { year month day }
            homeTown
            characterMedia(page: 1, perPage: 25) {
              media: nodes {
                id
                title { romaji english userPreferred }
                format
                coverImage { large extraLarge }
                bannerImage
                averageScore
                description(asHtml: false)
                startDate { year }
              }
            }
            staffMedia(page: 1, perPage: 25) {
              media: nodes {
                id
                title { romaji english userPreferred }
                format
                coverImage { large extraLarge }
                bannerImage
                averageScore
                description(asHtml: false)
                startDate { year }
              }
            }
          }
        }
    """

    private const val STUDIO_QUERY = """
        query (${'$'}id: Int) {
          Studio(id: ${'$'}id) {
            id
            name
            media(page: 1, perPage: 25) {
              media: nodes {
                id
                title { romaji english userPreferred }
                format
                coverImage { large extraLarge }
                bannerImage
                averageScore
                description(asHtml: false)
                startDate { year }
              }
            }
          }
        }
    """

    suspend fun fetchPersonDetail(personId: Int, preferCrewCredits: Boolean = false): com.nuvio.app.features.details.PersonDetail? {
        val requestBody = buildJsonObject {
            put("query", STAFF_QUERY)
            put("variables", buildJsonObject {
                put("id", personId)
            })
        }
        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistStaffData>>(responseText)
            parsed.data?.staff?.toPersonDetail()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to fetch person detail for $personId" }
            null
        }
    }

    suspend fun fetchEntityBrowse(
        entityKind: com.nuvio.app.features.details.EntityKind,
        entityId: Int,
        sourceType: String,
        fallbackName: String,
    ): com.nuvio.app.features.details.EntityBrowseData? {
        val requestBody = buildJsonObject {
            put("query", STUDIO_QUERY)
            put("variables", buildJsonObject {
                put("id", entityId)
            })
        }
        return try {
            val responseText = httpPostJsonWithHeaders(ANILIST_ENDPOINT, json.encodeToString(requestBody), headers)
            val parsed = json.decodeFromString<AnilistGraphQLResponse<AnilistStudioData>>(responseText)
            parsed.data?.studio?.toEntityBrowseData()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to fetch studio browse for $entityId" }
            null
        }
    }

    fun clearCache() {
        detailsCache.clear()
        searchCache.clear()
    }
}
