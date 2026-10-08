package com.nuvio.app.features.catalog

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey

const val CATALOG_PAGE_SIZE = 100
private const val DUPLICATE_CATALOG_PAGE_ADVANCE_LIMIT = 3

data class CatalogPage(
    val items: List<MetaPreview>,
    val rawItemCount: Int,
    val nextSkip: Int?,
)

data class CatalogPaginationState(
    val nextSkip: Int?,
    val consecutiveDuplicatePages: Int = 0,
)

fun nextCatalogPaginationState(
    supportsPagination: Boolean,
    requestedSkip: Int,
    page: CatalogPage,
    loadedNewItems: Boolean,
    consecutiveDuplicatePages: Int,
): CatalogPaginationState {
    if (!supportsPagination || page.rawItemCount <= 0 || page.nextSkip == null) {
        return CatalogPaginationState(nextSkip = null)
    }
    if (loadedNewItems) {
        return CatalogPaginationState(nextSkip = page.nextSkip)
    }

    val duplicatePages = consecutiveDuplicatePages + 1
    val advancedSkip = if (page.nextSkip > requestedSkip) {
        page.nextSkip
    } else {
        requestedSkip + page.rawItemCount.coerceAtLeast(1)
    }
    return if (duplicatePages < DUPLICATE_CATALOG_PAGE_ADVANCE_LIMIT && advancedSkip > requestedSkip) {
        CatalogPaginationState(
            nextSkip = advancedSkip,
            consecutiveDuplicatePages = duplicatePages,
        )
    } else {
        CatalogPaginationState(
            nextSkip = null,
            consecutiveDuplicatePages = duplicatePages,
        )
    }
}

fun mergeCatalogItems(
    existing: List<MetaPreview>,
    incoming: List<MetaPreview>,
): List<MetaPreview> {
    if (incoming.isEmpty()) return existing
    val seen = existing.mapTo(mutableSetOf()) { item -> item.stableKey() }
    return buildList(existing.size + incoming.size) {
        addAll(existing)
        incoming.forEach { item ->
            val key = item.stableKey()
            if (seen.add(key)) {
                add(item)
            }
        }
    }
}

fun dedupeCatalogItems(items: List<MetaPreview>): List<MetaPreview> {
    if (items.size < 2) return items
    val seen = mutableSetOf<String>()
    return buildList(items.size) {
        items.forEach { item ->
            if (seen.add(item.stableKey())) {
                add(item)
            }
        }
    }
}
