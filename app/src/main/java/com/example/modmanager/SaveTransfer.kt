package com.darkestdungeon.modmanager

import com.darkestdungeon.modmanager.data.storage.ModEntry
import com.darkestdungeon.modmanager.data.storage.ParsedSave

/**
 * Describes whether a serialized Wicked-port save can safely receive a copied
 * persistent mod order.
 */
data class SaveTransferAssessment(
    val sourceLoadOrder: List<String>,
    val targetLoadOrder: List<String>,
    val targetHasAppliedCatalog: Boolean,
    val sourceHasAppliedCatalog: Boolean,
    val canCopyOrder: Boolean,
    val reason: String
)

/** A ready-to-write transfer. */
data class TransferPlan(
    val sourceLoadOrder: List<String>,
    val targetLoadOrder: List<String>,
    val orderedTargetEntries: List<ModEntry>,
    val matched: List<String>,
    val appendedNotInSource: List<String>,
    val ignoredNotInstalled: List<String>,
    val canTransfer: Boolean,
    val reason: String
) {
    /** Entries to write for a full copy = all source entries in source order. */
    val entries: List<ModEntry> get() = orderedTargetEntries
}

object SaveTransferPlanner {

    /**
     * Builds a plan for copying the source save's load order onto the target
     * save's records. Matching is exact first, then normalized (case- and
     * space-insensitive); target records with no source counterpart are
     * appended at the end in their current relative order and reported.
     */
    fun buildPlan(source: ParsedSave, target: ParsedSave): TransferPlan {
        val sourceOrder = source.loadOrder.distinct()
        val targetEntries = target.entries
        val matched = mutableListOf<String>()
        val ignoredNotInstalled = mutableListOf<String>()

        // An empty persistent_ugcs section (fresh campaign, nothing persisted)
        // cannot donate or receive an order — refuse with a clear reason.
        val emptyReason = when {
            source.entries.isEmpty() ->
                "The source save has an empty persistent mod list (no mods enabled in that campaign)."
            targetEntries.isEmpty() ->
                "The target save has an empty persistent mod list (no mods enabled in that campaign). Nothing to reorder."
            else -> null
        }
        if (emptyReason != null) {
            return TransferPlan(
                sourceLoadOrder = sourceOrder,
                targetLoadOrder = target.loadOrder,
                orderedTargetEntries = emptyList(),
                matched = emptyList(),
                appendedNotInSource = emptyList(),
                ignoredNotInstalled = emptyList(),
                canTransfer = false,
                reason = emptyReason
            )
        }

        val used = HashSet<ModEntry>()
        val orderedTargetEntries = mutableListOf<ModEntry>()

        // Pass 1: exact name matches (source order wins).
        val targetByName = targetEntries.groupBy { it.name }
        val fallbackCandidates = LinkedHashMap<String, MutableList<ModEntry>>()
        val normalized = { s: String -> s.trim().lowercase().replace(" ", "") }

        for (sourceName in sourceOrder) {
            val exact = targetByName[sourceName]?.firstOrNull { it !in used }
            if (exact != null) {
                used.add(exact)
                orderedTargetEntries.add(exact)
                matched.add(sourceName)
                continue
            }
            fallbackCandidates[sourceName] = mutableListOf()
        }

        // Pass 2: normalized fallback matching for source entries that had no exact hit.
        val targetByNormalizedName = LinkedHashMap<String, MutableList<ModEntry>>()
        for (entry in targetEntries) {
            if (entry in used) continue
            targetByNormalizedName
                .getOrPut(normalized(entry.name)) { mutableListOf() }
                .add(entry)
        }
        for ((sourceName, _) in fallbackCandidates) {
            val hit = targetByNormalizedName[normalized(sourceName)]?.firstOrNull { it !in used }
            if (hit != null) {
                used.add(hit)
                orderedTargetEntries.add(hit)
                matched.add(sourceName)
            } else {
                ignoredNotInstalled.add(sourceName)
            }
        }

        // Pass 3: target records not present in the source keep their current
        // relative order, appended at the end.
        val appendedNotInSource = mutableListOf<String>()
        for (entry in targetEntries) {
            if (entry !in used) {
                appendedNotInSource.add(entry.name)
                orderedTargetEntries.add(entry)
            }
        }

        val canTransfer = orderedTargetEntries.size == targetEntries.size &&
            orderedTargetEntries.containsAll(targetEntries) &&
            matched.isNotEmpty()

        val reason = when {
            sourceOrder.isEmpty() -> "The source save has no persistent mod load order."
            matched.isEmpty() -> "No source mod matched any mod installed on the target save."
            appendedNotInSource.isNotEmpty() ->
                "Transfer possible: ${matched.size} matched, ${appendedNotInSource.size} target-only mod(s) will be appended at the end, ${ignoredNotInstalled.size} source mod(s) not installed on this device were ignored."
            else -> "Transfer possible: ${matched.size} mod(s) matched."
        }

        return TransferPlan(
            sourceLoadOrder = sourceOrder,
            targetLoadOrder = target.loadOrder,
            orderedTargetEntries = orderedTargetEntries,
            matched = matched,
            appendedNotInSource = appendedNotInSource,
            ignoredNotInstalled = ignoredNotInstalled,
            canTransfer = canTransfer,
            reason = reason
        )
    }

    /**
     * FULL-COPY plan: DISABLED (crashed the game on-device 2026-11-11).
     * Kept for reference/testing only — do NOT wire into the app.
     */
    fun buildFullCopyPlan(source: ParsedSave): TransferPlan {
        val sourceOrder = source.loadOrder.distinct()
        val seen = HashSet<String>()
        val ordered = source.entries.filter { e -> sourceOrder.contains(e.name) && seen.add(e.name) }
        return TransferPlan(
            sourceLoadOrder = sourceOrder,
            targetLoadOrder = emptyList(),
            orderedTargetEntries = ordered,
            matched = ordered.map { it.name },
            appendedNotInSource = emptyList(),
            ignoredNotInstalled = emptyList(),
            canTransfer = ordered.isNotEmpty(),
            reason = "Full copy: the target's persistent mod list will become the source's " +
                "${ordered.size} mod(s) in source order."
        )
    }

    /**
     * Builds a plan driven by NAMES (the app's own load order) rather than a
     * source save: reorders [save]'s records to follow [orderedNames] (exact
     * match first, then normalized fallback). Save entries not named keep
     * their relative order at the end; names not in the save are ignored
     * and reported.
     */
    fun buildPlanFromNames(save: ParsedSave, orderedNames: List<String>): TransferPlan {
        val entries = save.entries
        val used = HashSet<ModEntry>()
        val orderedTargetEntries = mutableListOf<ModEntry>()
        val matched = mutableListOf<String>()
        val ignoredNames = mutableListOf<String>()

        val normalized = { s: String -> s.trim().lowercase().replace(" ", "") }

        // Pass 1: exact names.
        val byName = entries.groupBy { it.name }
        val fallback = mutableListOf<String>()
        for (name in orderedNames.distinct()) {
            val hit = byName[name]?.firstOrNull { it !in used }
            if (hit != null) {
                used.add(hit)
                orderedTargetEntries.add(hit)
                matched.add(name)
            } else {
                fallback += name
            }
        }

        // Pass 2: normalized fallback for names that had no exact hit.
        val byNormalizedName = LinkedHashMap<String, MutableList<ModEntry>>()
        for (entry in entries) {
            if (entry in used) continue
            byNormalizedName.getOrPut(normalized(entry.name)) { mutableListOf() }.add(entry)
        }
        for (name in fallback) {
            val hit = byNormalizedName[normalized(name)]?.firstOrNull { it !in used }
            if (hit != null) {
                used.add(hit)
                orderedTargetEntries.add(hit)
                matched.add(name)
            } else {
                ignoredNames += name
            }
        }

        // Pass 3: save entries not in the app order keep relative order at the end.
        val appended = mutableListOf<String>()
        for (entry in entries) {
            if (entry !in used) {
                appended += entry.name
                orderedTargetEntries.add(entry)
            }
        }

        val canTransfer = orderedTargetEntries.size == entries.size && matched.isNotEmpty()
        val reason = when {
            entries.isEmpty() -> "The save has an empty persistent mod list; nothing to reorder."
            matched.isEmpty() -> "None of the app's mods matched this save's entries."
            else -> "${matched.size} matched, ${appended.size} save-only, ${ignoredNames.size} not in this save."
        }

        return TransferPlan(
            sourceLoadOrder = orderedNames,
            targetLoadOrder = save.loadOrder,
            orderedTargetEntries = orderedTargetEntries,
            matched = matched,
            appendedNotInSource = appended,
            ignoredNotInstalled = ignoredNames,
            canTransfer = canTransfer,
            reason = reason
        )
    }
}