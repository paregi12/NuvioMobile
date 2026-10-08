package com.nuvio.app.features.anilist

import com.nuvio.app.features.details.*
import com.nuvio.app.features.home.MetaPreview
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnilistGraphQLResponse<T>(
    val data: T? = null,
    val errors: List<AnilistGraphQLError>? = null,
)

@Serializable
data class AnilistGraphQLError(
    val message: String? = null,
    val status: Int? = null,
)

@Serializable
data class AnilistMediaData(
    @SerialName("Media") val media: AnilistMediaDto? = null,
)

@Serializable
data class AnilistPageData(
    @SerialName("Page") val page: AnilistPageDto? = null,
)

@Serializable
data class AnilistPageDto(
    val media: List<AnilistMediaDto> = emptyList(),
)

@Serializable
data class AnilistCharacterData(
    @SerialName("Character") val character: AnilistCharacterDetailDto? = null,
)

@Serializable
data class AnilistStudioData(
    @SerialName("Studio") val studio: AnilistStudioDetailDto? = null,
)

@Serializable
data class AnilistMediaDto(
    val id: Int,
    val idMal: Int? = null,
    val title: AnilistTitleDto? = null,
    val format: String? = null,
    val status: String? = null,
    val description: String? = null,
    val startDate: AnilistFuzzyDateDto? = null,
    val endDate: AnilistFuzzyDateDto? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val countryOfOrigin: String? = null,
    val isAdult: Boolean? = null,
    val genres: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val coverImage: AnilistCoverImageDto? = null,
    val bannerImage: String? = null,
    val studios: AnilistStudiosConnectionDto? = null,
    val trailer: AnilistTrailerDto? = null,
    val nextAiringEpisode: AnilistAiringScheduleDto? = null,
    val characters: AnilistCharactersConnectionDto? = null,
    val recommendations: AnilistRecommendationsConnectionDto? = null,
    val streamingEpisodes: List<AnilistStreamingEpisodeDto> = emptyList(),
    val relations: AnilistRelationsConnectionDto? = null,
)

@Serializable
data class AnilistTitleDto(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
) {
    fun userPreferred(): String =
        english?.takeIf { it.isNotBlank() }
            ?: romaji?.takeIf { it.isNotBlank() }
            ?: native.orEmpty()
}

@Serializable
data class AnilistCoverImageDto(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val color: String? = null,
) {
    val best: String?
        get() = extraLarge ?: large ?: medium
}

@Serializable
data class AnilistFuzzyDateDto(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class AnilistStudiosConnectionDto(
    val nodes: List<AnilistStudioDto> = emptyList(),
)

@Serializable
data class AnilistStudioDto(
    val id: Int,
    val name: String,
)

@Serializable
data class AnilistTrailerDto(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class AnilistAiringScheduleDto(
    val airingAt: Long? = null,
    val timeUntilAiring: Long? = null,
    val episode: Int? = null,
)

@Serializable
data class AnilistCharactersConnectionDto(
    val edges: List<AnilistCharacterEdgeDto> = emptyList(),
)

@Serializable
data class AnilistCharacterEdgeDto(
    val role: String? = null,
    val node: AnilistCharacterNodeDto? = null,
    val voiceActors: List<AnilistVoiceActorDto> = emptyList(),
)

@Serializable
data class AnilistCharacterNodeDto(
    val id: Int,
    val name: AnilistNameDto? = null,
    val image: AnilistImageDto? = null,
)

@Serializable
data class AnilistVoiceActorDto(
    val id: Int,
    val name: AnilistNameDto? = null,
    val image: AnilistImageDto? = null,
)

@Serializable
data class AnilistNameDto(
    val full: String? = null,
    val native: String? = null,
    val userPreferred: String? = null,
)

@Serializable
data class AnilistImageDto(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class AnilistRecommendationsConnectionDto(
    val nodes: List<AnilistRecommendationNodeDto> = emptyList(),
)

@Serializable
data class AnilistRecommendationNodeDto(
    val mediaRecommendation: AnilistMediaDto? = null,
)

@Serializable
data class AnilistStreamingEpisodeDto(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null,
)

@Serializable
data class AnilistRelationsConnectionDto(
    val edges: List<AnilistRelationEdgeDto> = emptyList(),
)

@Serializable
data class AnilistRelationEdgeDto(
    val relationType: String? = null,
    val node: AnilistMediaDto? = null,
)

@Serializable
data class AnilistCharacterDetailDto(
    val id: Int,
    val name: AnilistNameDto? = null,
    val image: AnilistImageDto? = null,
    val description: String? = null,
    val media: AnilistPageDto? = null,
)

@Serializable
data class AnilistStaffData(
    @SerialName("Staff") val staff: AnilistStaffDetailDto? = null,
)

@Serializable
data class AnilistStaffDetailDto(
    val id: Int,
    val name: AnilistNameDto? = null,
    val image: AnilistImageDto? = null,
    val description: String? = null,
    val dateOfBirth: AnilistFuzzyDateDto? = null,
    val dateOfDeath: AnilistFuzzyDateDto? = null,
    val homeTown: String? = null,
    val characterMedia: AnilistPageDto? = null,
    val staffMedia: AnilistPageDto? = null,
)

@Serializable
data class AnilistStudioDetailDto(
    val id: Int,
    val name: String,
    val media: AnilistPageDto? = null,
)

fun AnilistStaffDetailDto.toPersonDetail(): PersonDetail {
    val charItems = characterMedia?.media?.map { it.toMetaPreview() }.orEmpty()
    val staffItems = staffMedia?.media?.map { it.toMetaPreview() }.orEmpty()
    val allCredits = (charItems + staffItems).distinctBy { it.id }
    val birth = dateOfBirth?.let { "${it.year ?: ""}-${it.month ?: ""}-${it.day ?: ""}".trim('-') }
    val death = dateOfDeath?.let { "${it.year ?: ""}-${it.month ?: ""}-${it.day ?: ""}".trim('-') }
    val cleanDesc = description?.replace(Regex("<br\\s*/?>"), "\n")?.replace(Regex("<[^>]*>"), "")?.trim()

    return PersonDetail(
        tmdbId = id,
        name = name?.full ?: name?.userPreferred.orEmpty(),
        biography = cleanDesc,
        birthday = birth?.takeIf { it.isNotBlank() },
        deathday = death?.takeIf { it.isNotBlank() },
        placeOfBirth = homeTown,
        profilePhoto = image?.large ?: image?.medium,
        knownFor = "Anime",
        movieCredits = allCredits.filter { it.type.equals("movie", ignoreCase = true) },
        tvCredits = allCredits.filter { !it.type.equals("movie", ignoreCase = true) },
    )
}

fun AnilistStudioDetailDto.toEntityBrowseData(): EntityBrowseData {
    val items = media?.media?.map { it.toMetaPreview() }.orEmpty()
    val header = EntityHeader(
        id = id,
        kind = EntityKind.COMPANY,
        name = name,
        logo = null,
        originCountry = "JP",
        secondaryLabel = null,
        description = null,
    )
    val rails = listOf(
        EntityRail(
            mediaType = EntityMediaType.TV,
            railType = EntityRailType.POPULAR,
            items = items,
            currentPage = 1,
            hasMore = false,
            isLoading = false,
        ),
    )
    return EntityBrowseData(header = header, rails = rails)
}

fun AnilistMediaDto.toMetaDetails(): MetaDetails {
    val displayTitle = title?.userPreferred().orEmpty()
    val cleanDesc = description?.replace(Regex("<br\\s*/?>"), "\n")
        ?.replace(Regex("<[^>]*>"), "")
        ?.trim()

    val releaseYear = seasonYear?.toString() ?: startDate?.year?.toString()
    val rating = averageScore?.let { (it / 10.0).toString() }
    val durationStr = duration?.let { "$it min" }

    val castList = characters?.edges?.mapNotNull { edge ->
        val charNode = edge.node ?: return@mapNotNull null
        val va = edge.voiceActors.firstOrNull()
        MetaPerson(
            name = charNode.name?.full ?: charNode.name?.userPreferred ?: "",
            role = edge.role ?: va?.name?.full,
            photo = charNode.image?.large ?: charNode.image?.medium,
            tmdbId = charNode.id,
        )
    }.orEmpty()

    val studioList = studios?.nodes?.map { s ->
        MetaCompany(
            name = s.name,
            tmdbId = s.id,
        )
    }.orEmpty()

    val recs = recommendations?.nodes?.mapNotNull { node ->
        node.mediaRecommendation?.toMetaPreview()
    }.orEmpty()

    val trailerList = trailer?.let { t ->
        if (!t.id.isNullOrBlank() && (t.site.equals("youtube", ignoreCase = true) || t.site == null)) {
            listOf(
                MetaTrailer(
                    id = t.id,
                    key = t.id,
                    name = "Official Trailer",
                    site = "YouTube",
                    type = "Trailer",
                    official = true,
                ),
            )
        } else emptyList()
    }.orEmpty()

    val videoList = if (streamingEpisodes.isNotEmpty()) {
        streamingEpisodes.mapIndexed { idx, ep ->
            val epNum = idx + 1
            MetaVideo(
                id = "anilist:$id:1:$epNum",
                title = ep.title ?: "Episode $epNum",
                thumbnail = ep.thumbnail,
                season = 1,
                episode = epNum,
                overview = null,
            )
        }
    } else if ((episodes ?: 0) > 0) {
        (1..(episodes ?: 1)).map { epNum ->
            MetaVideo(
                id = "anilist:$id:1:$epNum",
                title = "Episode $epNum",
                season = 1,
                episode = epNum,
                overview = null,
            )
        }
    } else {
        listOf(
            MetaVideo(
                id = "anilist:$id:1:1",
                title = displayTitle,
                season = 1,
                episode = 1,
                overview = cleanDesc,
            ),
        )
    }

    val relationList = relations?.edges?.mapNotNull { edge ->
        val relNode = edge.node ?: return@mapNotNull null
        MetaRelation(
            relationType = edge.relationType?.replace('_', ' ') ?: "Related",
            item = relNode.toMetaPreview(),
        )
    }.orEmpty()

    val mediaType = if (format.equals("MOVIE", ignoreCase = true)) "movie" else "series"

    return MetaDetails(
        id = "anilist:$id",
        type = mediaType,
        name = displayTitle,
        poster = coverImage?.best,
        background = bannerImage ?: coverImage?.best,
        description = cleanDesc,
        releaseInfo = releaseYear,
        status = status,
        imdbRating = rating,
        ageRating = if (isAdult == true) "18+" else "PG-13",
        runtime = durationStr,
        genres = genres,
        cast = castList,
        productionCompanies = studioList,
        totalEpisodes = episodes,
        relations = relationList,
        moreLikeThis = recs,
        moreLikeThisSource = MoreLikeThisSource.ANILIST,
        trailers = trailerList,
        videos = videoList,
    )
}

fun AnilistMediaDto.toMetaPreview(): MetaPreview {
    val displayTitle = title?.userPreferred().orEmpty()
    val releaseYear = seasonYear?.toString() ?: startDate?.year?.toString()
    val rating = averageScore?.let { (it / 10.0).toString() }
    val mediaType = if (format.equals("MOVIE", ignoreCase = true)) "movie" else "series"

    return MetaPreview(
        id = "anilist:$id",
        type = mediaType,
        name = displayTitle,
        poster = coverImage?.best,
        banner = bannerImage,
        imdbRating = rating,
        releaseInfo = releaseYear,
        genres = genres,
    )
}
