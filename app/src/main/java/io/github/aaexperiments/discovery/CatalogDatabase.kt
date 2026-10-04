package io.github.aaexperiments.discovery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.aaexperiments.core.DynamicOverrideCodec
import org.json.JSONArray
import org.json.JSONObject

data class CatalogSummary(
    val baseSha256: String,
    val dexSha256: Map<String, String>,
    val generatedAtEpochMs: Long,
    val identifierCount: Int,
    val editableCount: Int,
    val errorCount: Int,
    val resolverSchemaVersion: Int,
    val actualBaseSha256: String = baseSha256,
    val versionCode: Long = 0,
    val packageArtifactSha256: Map<String, String> = emptyMap()
)

data class CatalogChangeSummary(val added: Int, val removed: Int, val changed: Int)
data class ResolverRunSummary(
    val preserved: Int = 0,
    val rediscovered: Int = 0,
    val suspended: Int = 0,
    val newKeys: Int = 0,
    val deepResolved: Int = 0,
    val deepInconclusive: Int = 0,
    val deepDeferred: Int = 0,
    val targetedSemanticEndpoint: Int = 0,
    val targetedBudgetExhausted: Int = 0,
    val targetedSemanticCorridorExhausted: Int = 0,
    val targetedFallbackBudgetExhausted: Int = 0,
    val targetedSharedFanout: Int = 0,
    val targetedTechnicalOnly: Int = 0,
    val targetedNoConsumer: Int = 0,
    val targetedGenericOnly: Int = 0,
    val targetedMultipleEndpoints: Int = 0,
    val targetedValueFlowNotProven: Int = 0,
    val flowResultNotCaptured: Int = 0,
    val flowCapturedThenOverwritten: Int = 0,
    val flowCapturedButUnused: Int = 0,
    val flowUnsupportedOpcode: Int = 0,
    val flowCfgOriginAmbiguous: Int = 0,
    val flowUnknown: Int = 0
)
data class CatalogQuery(
    val text: String = "", val editableOnly: Boolean = false, val includeUnknown: Boolean = false,
    val kind: IdentifierKind? = null, val type: DiscoveredType? = null, val resolution: MappingResolution? = null,
    val runtimeStatus: RuntimeConsumerStatus? = null, val reviewedOnly: Boolean = false, val killSwitchOnly: Boolean = false,
    val overrideKeys: Set<String>? = null, val limit: Int = 500, val offset: Int = 0
)
data class CatalogQueryResult(val items: List<DiscoveredIdentifier>, val total: Int)

class CatalogDatabase(context: Context) : SQLiteOpenHelper(context, "catalog-v1.db", null, 7) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE builds(base_sha TEXT PRIMARY KEY, generated_at INTEGER NOT NULL, dex_hashes TEXT NOT NULL, identifier_count INTEGER NOT NULL, editable_count INTEGER NOT NULL, error_count INTEGER NOT NULL, resolver_schema INTEGER NOT NULL, base_apk_sha TEXT NOT NULL, version_code INTEGER NOT NULL, package_artifacts TEXT NOT NULL)")
        db.execSQL("CREATE TABLE identifiers(id INTEGER PRIMARY KEY AUTOINCREMENT, base_sha TEXT NOT NULL, key TEXT NOT NULL, namespace TEXT NOT NULL, value_type TEXT NOT NULL, compiled_default TEXT, confidence TEXT NOT NULL, reason TEXT, editable INTEGER NOT NULL, preference_key TEXT NOT NULL, occurrence_count INTEGER NOT NULL, kind TEXT NOT NULL, resolution TEXT NOT NULL, runtime_status TEXT NOT NULL, semantics_reviewed INTEGER NOT NULL, editability_reason TEXT, metadata TEXT NOT NULL, storage_type TEXT NOT NULL, semantic_type TEXT NOT NULL, evidence_confidence TEXT NOT NULL, semantics_provenance TEXT NOT NULL, UNIQUE(base_sha,key))")
        db.execSQL("CREATE TABLE getters(identifier_id INTEGER NOT NULL, ordinal INTEGER NOT NULL, dex TEXT NOT NULL, class_name TEXT NOT NULL, method_name TEXT NOT NULL, descriptor TEXT NOT NULL, return_type TEXT NOT NULL, parameter_types TEXT NOT NULL, instruction_count INTEGER NOT NULL, opcode_sha TEXT NOT NULL, is_clinit INTEGER NOT NULL, referenced_fields TEXT NOT NULL, invoked_methods TEXT NOT NULL, PRIMARY KEY(identifier_id,ordinal))")
        db.execSQL("CREATE TABLE consumers(identifier_id INTEGER NOT NULL, ordinal INTEGER NOT NULL, dex TEXT NOT NULL, class_name TEXT NOT NULL, method_name TEXT NOT NULL, descriptor TEXT NOT NULL, return_type TEXT NOT NULL, parameter_types TEXT NOT NULL, instruction_count INTEGER NOT NULL, opcode_sha TEXT NOT NULL, is_clinit INTEGER NOT NULL, referenced_fields TEXT NOT NULL, invoked_methods TEXT NOT NULL, depth INTEGER NOT NULL, link_kind TEXT NOT NULL, path TEXT NOT NULL, semantic_summary TEXT, PRIMARY KEY(identifier_id,ordinal))")
        db.execSQL("CREATE INDEX idx_identifiers_page ON identifiers(base_sha,editable,key)")
        db.execSQL("CREATE INDEX idx_identifiers_preference ON identifiers(base_sha,preference_key)")
        db.execSQL("CREATE INDEX idx_identifiers_filters ON identifiers(base_sha,kind,value_type,resolution,runtime_status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE builds ADD COLUMN resolver_schema INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN kind TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN resolution TEXT NOT NULL DEFAULT 'UNSUPPORTED'")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN runtime_status TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN semantics_reviewed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN editability_reason TEXT")
        }
        if (oldVersion < 3) db.execSQL("CREATE TABLE consumers(identifier_id INTEGER NOT NULL, ordinal INTEGER NOT NULL, dex TEXT NOT NULL, class_name TEXT NOT NULL, method_name TEXT NOT NULL, descriptor TEXT NOT NULL, return_type TEXT NOT NULL, parameter_types TEXT NOT NULL, instruction_count INTEGER NOT NULL, opcode_sha TEXT NOT NULL, is_clinit INTEGER NOT NULL, referenced_fields TEXT NOT NULL, invoked_methods TEXT NOT NULL, depth INTEGER NOT NULL, link_kind TEXT NOT NULL, path TEXT NOT NULL, semantic_summary TEXT, PRIMARY KEY(identifier_id,ordinal))")
        if (oldVersion < 4) db.execSQL("ALTER TABLE identifiers ADD COLUMN metadata TEXT NOT NULL DEFAULT '[]'")
        if (oldVersion < 5) db.execSQL("CREATE INDEX idx_identifiers_filters ON identifiers(base_sha,kind,value_type,resolution,runtime_status)")
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE identifiers ADD COLUMN storage_type TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN semantic_type TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN evidence_confidence TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE identifiers ADD COLUMN semantics_provenance TEXT NOT NULL DEFAULT 'NONE'")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE builds ADD COLUMN base_apk_sha TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE builds ADD COLUMN version_code INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE builds ADD COLUMN package_artifacts TEXT NOT NULL DEFAULT '[]'")
        }
    }

    fun put(inventory: DexInventory, onProgress: (AnalysisProgress) -> Unit = {}) {
        val buildKey = inventory.buildFingerprintSha256
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("getters", "identifier_id IN (SELECT id FROM identifiers WHERE base_sha=?)", arrayOf(buildKey))
            writableDatabase.delete("consumers", "identifier_id IN (SELECT id FROM identifiers WHERE base_sha=?)", arrayOf(buildKey))
            writableDatabase.delete("identifiers", "base_sha=?", arrayOf(buildKey))
            writableDatabase.delete("builds", "base_sha=?", arrayOf(buildKey))
            writableDatabase.insertOrThrow("builds", null, ContentValues().apply {
                put("base_sha", buildKey); put("generated_at", inventory.generatedAtEpochMs)
                put("base_apk_sha", inventory.baseSha256); put("version_code", inventory.packageVersionCode)
                put("package_artifacts", encodeMap(inventory.packageArtifactSha256))
                put("dex_hashes", encodeMap(inventory.dexSha256)); put("identifier_count", inventory.identifiers.size)
                put("editable_count", inventory.identifiers.count { it.editable }); put("error_count", inventory.errors.size)
                put("resolver_schema", SemanticClassifier.SCHEMA_VERSION)
            })
            onProgress(AnalysisProgress(AnalysisStage.SAVING_CATALOG, 0, inventory.identifiers.size))
            inventory.identifiers.forEachIndexed { index, identifier ->
                val id = writableDatabase.insertOrThrow("identifiers", null, ContentValues().apply {
                    put("base_sha", buildKey); put("key", identifier.key); put("namespace", identifier.namespace.name)
                    put("value_type", identifier.inferredType.name); put("compiled_default", identifier.compiledDefault)
                    put("confidence", identifier.confidence.name); put("reason", identifier.reason); put("editable", if (identifier.editable) 1 else 0)
                    put("preference_key", DynamicOverrideCodec.preferenceKey(identifier.key)); put("occurrence_count", identifier.occurrences.size)
                    put("kind", identifier.kind.name); put("resolution", identifier.resolution.name); put("runtime_status", identifier.runtimeConsumerStatus.name)
                    put("semantics_reviewed", if (identifier.semanticsReviewed) 1 else 0); put("editability_reason", identifier.editabilityReason?.name)
                    put("metadata", encodeMap(identifier.metadata))
                    put("storage_type", identifier.storageType.name); put("semantic_type", identifier.semanticType.name)
                    put("evidence_confidence", encodeConfidence(identifier.evidenceConfidence)); put("semantics_provenance", identifier.semanticsProvenance.name)
                })
                identifier.getterCandidates.forEachIndexed { ordinal, method ->
                    writableDatabase.insertOrThrow("getters", null, ContentValues().apply {
                        put("identifier_id", id); put("ordinal", ordinal); put("dex", method.dex); put("class_name", method.className)
                        put("method_name", method.methodName); put("descriptor", method.descriptor); put("return_type", method.returnType.name)
                        put("parameter_types", encodeList(method.parameterTypes)); put("instruction_count", method.instructionCount)
                        put("opcode_sha", method.opcodeSha256); put("is_clinit", if (method.isStaticInitializer) 1 else 0)
                        put("referenced_fields", encodeList(method.referencedFields)); put("invoked_methods", encodeList(method.invokedMethods))
                    })
                }
                identifier.consumers.forEachIndexed { ordinal, evidence ->
                    val method = evidence.method
                    writableDatabase.insertOrThrow("consumers", null, ContentValues().apply {
                        put("identifier_id", id); put("ordinal", ordinal); put("dex", method.dex); put("class_name", method.className)
                        put("method_name", method.methodName); put("descriptor", method.descriptor); put("return_type", method.returnType.name)
                        put("parameter_types", encodeList(method.parameterTypes)); put("instruction_count", method.instructionCount)
                        put("opcode_sha", method.opcodeSha256); put("is_clinit", if (method.isStaticInitializer) 1 else 0)
                        put("referenced_fields", encodeList(method.referencedFields)); put("invoked_methods", encodeList(method.invokedMethods))
                        put("depth", evidence.depth); put("link_kind", evidence.linkKind.name); put("path", encodeList(evidence.path))
                        put("semantic_summary", evidence.semanticSummary)
                    })
                }
                if (index == inventory.identifiers.lastIndex || index % 250 == 0) {
                    onProgress(AnalysisProgress(AnalysisStage.SAVING_CATALOG, index + 1, inventory.identifiers.size))
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        pruneToRecentBuilds(buildKey)
    }

    /** Keep the active catalog and one predecessor for the Status comparison. */
    fun pruneToRecentBuilds(currentBaseSha256: String, maximumBuilds: Int = 2) {
        val retained = readableDatabase.rawQuery(
            "SELECT base_sha FROM builds ORDER BY CASE WHEN base_sha=? THEN 0 ELSE 1 END, generated_at DESC LIMIT ?",
            arrayOf(currentBaseSha256, maximumBuilds.toString())
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val obsolete = readableDatabase.rawQuery("SELECT base_sha FROM builds", emptyArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0).let { if (it !in retained) add(it) } }
        }
        if (obsolete.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            obsolete.forEach { hash ->
                val args = arrayOf(hash)
                writableDatabase.delete("getters", "identifier_id IN (SELECT id FROM identifiers WHERE base_sha=?)", args)
                writableDatabase.delete("consumers", "identifier_id IN (SELECT id FROM identifiers WHERE base_sha=?)", args)
                writableDatabase.delete("identifiers", "base_sha=?", args)
                writableDatabase.delete("builds", "base_sha=?", args)
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        writableDatabase.execSQL("VACUUM")
    }

    fun summary(baseSha256: String): CatalogSummary? = readableDatabase.rawQuery(
        "SELECT generated_at,dex_hashes,identifier_count,editable_count,error_count,resolver_schema,base_apk_sha,version_code,package_artifacts FROM builds WHERE base_sha=?", arrayOf(baseSha256)
    ).use { c -> if (!c.moveToFirst()) null else CatalogSummary(baseSha256, decodeMap(c.getString(1)), c.getLong(0), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getString(6).ifBlank { baseSha256 }, c.getLong(7), decodeMap(c.getString(8))) }

    fun previousSummary(baseSha256: String): CatalogSummary? = readableDatabase.rawQuery(
        "SELECT base_sha,generated_at,dex_hashes,identifier_count,editable_count,error_count,resolver_schema,base_apk_sha,version_code,package_artifacts FROM builds WHERE base_sha<>? ORDER BY generated_at DESC LIMIT 1", arrayOf(baseSha256)
    ).use { c -> if (!c.moveToFirst()) null else CatalogSummary(c.getString(0), decodeMap(c.getString(2)), c.getLong(1), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6), c.getString(7).ifBlank { c.getString(0) }, c.getLong(8), decodeMap(c.getString(9))) }

    fun compatibilityBaselineMappings(baseSha256: String): Pair<CatalogSummary, List<DiscoveredIdentifier>>? {
        // A resolver-schema rebuild of the same APK should use its last catalog as the strongest
        // possible baseline. A genuinely new APK falls back to the most recent retained build.
        // Rediscovery compares getter fingerprints only. Loading historical consumers here keeps
        // two complete consumer graphs alive while the new DEX graph is being classified and can
        // exceed the manager's 256 MiB process heap.
        val baseline = summary(baseSha256) ?: previousSummary(baseSha256) ?: return null
        val rows = editablePage(baseline.baseSha256, Int.MAX_VALUE, 0).map(::compactCompatibilityMapping)
        return baseline to attachGetters(baseline.baseSha256, rows)
    }

    fun latestEditableMappings(): Pair<CatalogSummary, List<DiscoveredIdentifier>>? {
        val latestHash = readableDatabase.rawQuery(
            "SELECT base_sha FROM builds ORDER BY generated_at DESC LIMIT 1", emptyArray()
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        val baseline = summary(latestHash) ?: return null
        return baseline to attachGetters(latestHash, editablePage(latestHash, Int.MAX_VALUE, 0).map(::compactCompatibilityMapping))
    }

    fun resolverRunSummary(baseSha256: String): ResolverRunSummary {
        fun metadataCount(key: String, value: String) = readableDatabase.rawQuery(
            "SELECT count(*) FROM identifiers WHERE base_sha=? AND metadata LIKE ?",
            arrayOf(baseSha256, "%[\"$key\",\"$value\"]%")
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        return ResolverRunSummary(
            preserved = metadataCount("rediscoveryStatus", "PRESERVED") + metadataCount("rediscoveryStatus", "REDISCOVERED_BY_LITERAL"),
            rediscovered = metadataCount("rediscoveryStatus", "REDISCOVERED_STRUCTURAL"),
            suspended = metadataCount("rediscoveryStatus", "SUSPENDED_AMBIGUOUS"),
            newKeys = metadataCount("rediscoveryStatus", "NEW_KEY"),
            deepResolved = metadataCount("deepResolveStatus", "RESOLVED"),
            deepInconclusive = metadataCount("deepResolveStatus", "STILL_INCONCLUSIVE"),
            deepDeferred = metadataCount("deepResolveStatus", "DEFERRED_BATCH_LIMIT"),
            targetedSemanticEndpoint = metadataCount("targetedResolutionReason", "SEMANTIC_ENDPOINT_FOUND"),
            targetedBudgetExhausted = metadataCount("targetedResolutionReason", "BUDGET_EXHAUSTED"),
            targetedSemanticCorridorExhausted = metadataCount("targetedResolutionReason", "SEMANTIC_CORRIDOR_EXHAUSTED"),
            targetedFallbackBudgetExhausted = metadataCount("targetedResolutionReason", "FALLBACK_BUDGET_EXHAUSTED"),
            targetedSharedFanout = metadataCount("targetedResolutionReason", "SHARED_INFRASTRUCTURE_FANOUT"),
            targetedTechnicalOnly = metadataCount("targetedResolutionReason", "ONLY_TECHNICAL_CONSUMERS"),
            targetedNoConsumer = metadataCount("targetedResolutionReason", "NO_CONSUMER_FOUND"),
            targetedGenericOnly = metadataCount("targetedResolutionReason", "GENERIC_RUNTIME_PATH_ONLY"),
            targetedMultipleEndpoints = metadataCount("targetedResolutionReason", "MULTIPLE_SEMANTIC_ENDPOINTS"),
            targetedValueFlowNotProven = metadataCount("targetedResolutionReason", "VALUE_FLOW_NOT_PROVEN"),
            flowResultNotCaptured = metadataCount("valueFlowLimitation", "RESULT_NOT_CAPTURED"),
            flowCapturedThenOverwritten = metadataCount("valueFlowLimitation", "CAPTURED_THEN_OVERWRITTEN"),
            flowCapturedButUnused = metadataCount("valueFlowLimitation", "CAPTURED_BUT_UNUSED"),
            flowUnsupportedOpcode = metadataCount("valueFlowLimitation", "UNSUPPORTED_OPCODE"),
            flowCfgOriginAmbiguous = metadataCount("valueFlowLimitation", "CFG_ORIGIN_AMBIGUOUS"),
            flowUnknown = metadataCount("valueFlowLimitation", "UNKNOWN")
        )
    }

    fun inconclusiveKeys(baseSha256: String, limit: Int = 1): List<String> = readableDatabase.rawQuery(
        "SELECT key FROM identifiers WHERE base_sha=? AND editable=1 AND metadata LIKE ? AND metadata NOT LIKE ? ORDER BY key LIMIT ?",
        arrayOf(baseSha256, "%[\"consumerBudgetExhausted\",\"true\"]%", "%[\"deepResolveAttempted\",\"true\"]%", limit.toString())
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun inconclusiveCount(baseSha256: String): Int = readableDatabase.rawQuery(
        "SELECT count(*) FROM identifiers WHERE base_sha=? AND editable=1 AND metadata LIKE ? AND metadata NOT LIKE ?",
        arrayOf(baseSha256, "%[\"consumerBudgetExhausted\",\"true\"]%", "%[\"deepResolveAttempted\",\"true\"]%")
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    fun targetedRetryKeys(baseSha256: String): List<String> = readableDatabase.rawQuery(
        "SELECT key FROM identifiers WHERE base_sha=? AND editable=1 AND metadata NOT LIKE ? AND ((metadata LIKE ? AND (metadata LIKE ? OR metadata LIKE ? OR metadata LIKE ?)) OR metadata LIKE ?) ORDER BY key",
        arrayOf(baseSha256, "%[\"targetedStrategyVersion\",\"${ConsumerGraphAnalyzer.TARGETED_STRATEGY_VERSION}\"]%", "%[\"deepResolveStatus\",\"STILL_INCONCLUSIVE\"]%", "%[\"targetedResolutionReason\",\"BUDGET_EXHAUSTED\"]%", "%[\"targetedResolutionReason\",\"SEMANTIC_CORRIDOR_EXHAUSTED\"]%", "%[\"targetedResolutionReason\",\"VALUE_FLOW_NOT_PROVEN\"]%", "%[\"valueFlowLimitation\",\"UNKNOWN\"]%")
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun actionableDeepResolveCount(baseSha256: String): Int = inconclusiveCount(baseSha256) + readableDatabase.rawQuery(
        "SELECT count(*) FROM identifiers WHERE base_sha=? AND editable=1 AND metadata NOT LIKE ? AND ((metadata LIKE ? AND (metadata LIKE ? OR metadata LIKE ? OR metadata LIKE ?)) OR metadata LIKE ?)",
        arrayOf(baseSha256, "%[\"targetedStrategyVersion\",\"${ConsumerGraphAnalyzer.TARGETED_STRATEGY_VERSION}\"]%", "%[\"deepResolveStatus\",\"STILL_INCONCLUSIVE\"]%", "%[\"targetedResolutionReason\",\"BUDGET_EXHAUSTED\"]%", "%[\"targetedResolutionReason\",\"SEMANTIC_CORRIDOR_EXHAUSTED\"]%", "%[\"targetedResolutionReason\",\"VALUE_FLOW_NOT_PROVEN\"]%", "%[\"valueFlowLimitation\",\"UNKNOWN\"]%")
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    /** Updates only diagnostics produced by a directed pass; identity and editability stay intact. */
    fun updateDeepResolveResults(baseSha256: String, results: List<DiscoveredIdentifier>) {
        if (results.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            results.forEach { identifier ->
                val id = writableDatabase.rawQuery(
                    "SELECT id FROM identifiers WHERE base_sha=? AND key=?", arrayOf(baseSha256, identifier.key)
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null } ?: return@forEach
                writableDatabase.update("identifiers", ContentValues().apply {
                    put("runtime_status", identifier.runtimeConsumerStatus.name)
                    put("metadata", encodeMap(identifier.metadata))
                    put("evidence_confidence", encodeConfidence(identifier.evidenceConfidence))
                }, "id=?", arrayOf(id.toString()))
                writableDatabase.delete("consumers", "identifier_id=?", arrayOf(id.toString()))
                identifier.consumers.forEachIndexed { ordinal, evidence ->
                    val method = evidence.method
                    writableDatabase.insertOrThrow("consumers", null, ContentValues().apply {
                        put("identifier_id", id); put("ordinal", ordinal); put("dex", method.dex); put("class_name", method.className)
                        put("method_name", method.methodName); put("descriptor", method.descriptor); put("return_type", method.returnType.name)
                        put("parameter_types", encodeList(method.parameterTypes)); put("instruction_count", method.instructionCount)
                        put("opcode_sha", method.opcodeSha256); put("is_clinit", if (method.isStaticInitializer) 1 else 0)
                        put("referenced_fields", encodeList(method.referencedFields)); put("invoked_methods", encodeList(method.invokedMethods))
                        put("depth", evidence.depth); put("link_kind", evidence.linkKind.name); put("path", encodeList(evidence.path))
                        put("semantic_summary", evidence.semanticSummary)
                    })
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun editableChanges(current: String, previous: String): CatalogChangeSummary {
        fun count(sql: String) = readableDatabase.rawQuery(sql, arrayOf(current, previous)).use { it.moveToFirst(); it.getInt(0) }
        val added = count("SELECT count(*) FROM identifiers c LEFT JOIN identifiers p ON p.base_sha=?2 AND p.key=c.key AND p.editable=1 WHERE c.base_sha=?1 AND c.editable=1 AND p.id IS NULL")
        val removed = count("SELECT count(*) FROM identifiers p LEFT JOIN identifiers c ON c.base_sha=?1 AND c.key=p.key AND c.editable=1 WHERE p.base_sha=?2 AND p.editable=1 AND c.id IS NULL")
        val changed = count("SELECT count(*) FROM identifiers c JOIN identifiers p ON p.base_sha=?2 AND p.key=c.key WHERE c.base_sha=?1 AND c.editable=1 AND p.editable=1 AND (c.value_type<>p.value_type OR c.compiled_default IS NOT p.compiled_default OR c.confidence<>p.confidence OR c.resolution<>p.resolution OR c.runtime_status<>p.runtime_status)")
        return CatalogChangeSummary(added, removed, changed)
    }

    fun editablePage(baseSha256: String, limit: Int, offset: Int): List<DiscoveredIdentifier> = readableDatabase.rawQuery(
        "$ROW_COLUMNS FROM identifiers WHERE base_sha=? AND editable=1 ORDER BY key LIMIT ? OFFSET ?",
        arrayOf(baseSha256, limit.toString(), offset.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(row(c, false)) } }

    fun search(baseSha256: String, query: CatalogQuery): CatalogQueryResult {
        val where = mutableListOf("base_sha=?"); val args = mutableListOf(baseSha256)
        if (query.editableOnly) where += "editable=1"
        if (!query.includeUnknown) where += "value_type<>'UNKNOWN'"
        if (query.text.isNotBlank()) { where += "(key LIKE ? ESCAPE '\\' OR kind LIKE ? OR value_type LIKE ? OR resolution LIKE ?)"; val q="%${escapeLike(query.text.trim())}%"; repeat(4) { args += q } }
        query.kind?.let { where += "kind=?"; args += it.name }; query.type?.let { where += "value_type=?"; args += it.name }
        query.resolution?.let { where += "resolution=?"; args += it.name }; query.runtimeStatus?.let { where += "runtime_status=?"; args += it.name }
        if (query.reviewedOnly) where += "semantics_reviewed=1"
        if (query.killSwitchOnly) where += "key LIKE '%kill_switch%'"
        query.overrideKeys?.let { keys ->
            if (keys.isEmpty()) where += "0" else { where += "key IN (${keys.joinToString(",") { "?" }})"; args += keys }
        }
        val clause = where.joinToString(" AND ")
        val total = readableDatabase.rawQuery("SELECT count(*) FROM identifiers WHERE $clause", args.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
        val pageArgs = (args + query.limit.toString() + query.offset.toString()).toTypedArray()
        val items = readableDatabase.rawQuery("$ROW_COLUMNS FROM identifiers WHERE $clause ORDER BY key LIMIT ? OFFSET ?", pageArgs).use { c ->
            buildList { while (c.moveToNext()) add(row(c, false)) }
        }
        return CatalogQueryResult(items, total)
    }

    fun details(baseSha256: String, key: String): DiscoveredIdentifier? = readableDatabase.rawQuery(
        "$ROW_COLUMNS FROM identifiers WHERE base_sha=? AND key=? LIMIT 1", arrayOf(baseSha256, key)
    ).use { c -> if (!c.moveToFirst()) null else row(c, true) }

    /** Bulk attachment avoids one getters query plus one consumers query per exported row. */
    fun attachDetails(baseSha256: String, rows: List<DiscoveredIdentifier>): List<DiscoveredIdentifier> {
        if (rows.isEmpty()) return rows
        val wanted = rows.asSequence().map { it.key }.toHashSet()
        val getterMap = mutableMapOf<String, MutableList<MethodFingerprint>>()
        readableDatabase.rawQuery("SELECT i.key,g.dex,g.class_name,g.method_name,g.descriptor,g.return_type,g.parameter_types,g.instruction_count,g.opcode_sha,g.is_clinit,g.referenced_fields,g.invoked_methods FROM identifiers i JOIN getters g ON g.identifier_id=i.id WHERE i.base_sha=? ORDER BY i.key,g.ordinal", arrayOf(baseSha256)).use { c ->
            while (c.moveToNext()) if (c.getString(0) in wanted) getterMap.getOrPut(c.getString(0)) { mutableListOf() } += method(c, 1)
        }
        val consumerMap = mutableMapOf<String, MutableList<ConsumerEvidence>>()
        readableDatabase.rawQuery("SELECT i.key,c.dex,c.class_name,c.method_name,c.descriptor,c.return_type,c.parameter_types,c.instruction_count,c.opcode_sha,c.is_clinit,c.referenced_fields,c.invoked_methods,c.depth,c.link_kind,c.path,c.semantic_summary FROM identifiers i JOIN consumers c ON c.identifier_id=i.id WHERE i.base_sha=? ORDER BY i.key,c.ordinal", arrayOf(baseSha256)).use { c ->
            while (c.moveToNext()) if (c.getString(0) in wanted) consumerMap.getOrPut(c.getString(0)) { mutableListOf() } += ConsumerEvidence(method(c, 1), c.getInt(12), ConsumerLinkKind.valueOf(c.getString(13)), decodeList(c.getString(14)), c.getStringOrNull(15))
        }
        return rows.map { it.copy(getterCandidates = getterMap[it.key].orEmpty(), consumers = consumerMap[it.key].orEmpty()) }
    }

    /** Compatibility matching needs getter shapes, never the historical consumer graph. */
    private fun attachGetters(baseSha256: String, rows: List<DiscoveredIdentifier>): List<DiscoveredIdentifier> {
        if (rows.isEmpty()) return rows
        val wanted = rows.asSequence().map { it.key }.toHashSet()
        val getterMap = mutableMapOf<String, MutableList<MethodFingerprint>>()
        readableDatabase.rawQuery("SELECT i.key,g.dex,g.class_name,g.method_name,g.descriptor,g.return_type,g.parameter_types,g.instruction_count,g.opcode_sha,g.is_clinit,g.referenced_fields,g.invoked_methods FROM identifiers i JOIN getters g ON g.identifier_id=i.id WHERE i.base_sha=? ORDER BY i.key,g.ordinal", arrayOf(baseSha256)).use { c ->
            while (c.moveToNext()) if (c.getString(0) in wanted) getterMap.getOrPut(c.getString(0)) { mutableListOf() } += method(c, 1)
        }
        return rows.map { it.copy(getterCandidates = getterMap[it.key].orEmpty(), consumers = emptyList()) }
    }

    private fun compactCompatibilityMapping(value: DiscoveredIdentifier) = value.copy(
        occurrences = emptyList(),
        consumers = emptyList(),
        metadata = emptyMap(),
        reason = null,
        evidenceConfidence = EvidenceConfidence(),
    )

    fun identifiersByKeys(baseSha256: String, keys: Set<String>): Map<String, DiscoveredIdentifier> {
        if (keys.isEmpty()) return emptyMap()
        return keys.chunked(400).flatMap { chunk ->
            val marks = chunk.joinToString(",") { "?" }; val args = arrayOf(baseSha256, *chunk.toTypedArray())
            readableDatabase.rawQuery("$ROW_COLUMNS FROM identifiers WHERE base_sha=? AND key IN ($marks)", args).use { c ->
                buildList { while (c.moveToNext()) add(row(c, true)) }
            }
        }.associateBy { it.key }
    }

    fun compatibilityMappingsByKeys(baseSha256: String, keys: Set<String>): List<DiscoveredIdentifier> {
        if (keys.isEmpty()) return emptyList()
        val rows = keys.chunked(400).flatMap { chunk ->
            val marks = chunk.joinToString(",") { "?" }; val args = arrayOf(baseSha256, *chunk.toTypedArray())
            readableDatabase.rawQuery("$ROW_COLUMNS FROM identifiers WHERE base_sha=? AND key IN ($marks)", args).use { c ->
                buildList { while (c.moveToNext()) add(compactCompatibilityMapping(row(c, false))) }
            }
        }
        return attachGetters(baseSha256, rows)
    }

    fun identifiersByPreferenceKeys(baseSha256: String, preferenceKeys: Set<String>): List<Pair<String, DiscoveredIdentifier>> {
        if (preferenceKeys.isEmpty()) return emptyList()
        return preferenceKeys.chunked(400).flatMap { chunk ->
            val marks = chunk.joinToString(",") { "?" }; val args = arrayOf(baseSha256, *chunk.toTypedArray())
            readableDatabase.rawQuery("$ROW_COLUMNS,preference_key FROM identifiers WHERE base_sha=? AND preference_key IN ($marks)", args).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(19) to row(c, true)) }
            }
        }
    }

    private fun row(c: android.database.Cursor, details: Boolean) = DiscoveredIdentifier(
        c.getString(1), IdentifierNamespace.valueOf(c.getString(2)), DiscoveredType.valueOf(c.getString(3)), c.getStringOrNull(4),
        List(c.getInt(7)) { IdentifierOccurrence(emptyMethod(), it) }, if (details) getters(c.getLong(0)) else emptyList(), ResolutionConfidence.valueOf(c.getString(5)), c.getStringOrNull(6), c.getInt(14) != 0, decodeMap(c.getString(13)),
        kind = identifierKind(c.getString(8)), resolution = MappingResolution.valueOf(c.getString(9)),
        runtimeConsumerStatus = RuntimeConsumerStatus.valueOf(c.getString(10)), semanticsReviewed = c.getInt(11) != 0,
        editabilityReason = c.getStringOrNull(12)?.let(EditabilityReason::valueOf), consumers = if (details) consumers(c.getLong(0)) else emptyList(),
        storageType = enumOr(c.getString(15), StorageValueType.UNKNOWN), semanticType = enumOr(c.getString(16), SemanticValueType.UNKNOWN),
        evidenceConfidence = decodeConfidence(c.getString(17)), semanticsProvenance = enumOr(c.getString(18), SemanticsProvenance.NONE))

    private fun getters(identifierId: Long): List<MethodFingerprint> = readableDatabase.rawQuery(
        "SELECT dex,class_name,method_name,descriptor,return_type,parameter_types,instruction_count,opcode_sha,is_clinit,referenced_fields,invoked_methods FROM getters WHERE identifier_id=? ORDER BY ordinal", arrayOf(identifierId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(MethodFingerprint(c.getString(0),c.getString(1),c.getString(2),c.getString(3),DiscoveredType.valueOf(c.getString(4)),decodeList(c.getString(5)),c.getInt(6),c.getString(7),c.getInt(8)!=0,decodeList(c.getString(9)),decodeList(c.getString(10)))) } }

    private fun consumers(identifierId: Long): List<ConsumerEvidence> = readableDatabase.rawQuery(
        "SELECT dex,class_name,method_name,descriptor,return_type,parameter_types,instruction_count,opcode_sha,is_clinit,referenced_fields,invoked_methods,depth,link_kind,path,semantic_summary FROM consumers WHERE identifier_id=? ORDER BY ordinal", arrayOf(identifierId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(ConsumerEvidence(
        MethodFingerprint(c.getString(0),c.getString(1),c.getString(2),c.getString(3),DiscoveredType.valueOf(c.getString(4)),decodeList(c.getString(5)),c.getInt(6),c.getString(7),c.getInt(8)!=0,decodeList(c.getString(9)),decodeList(c.getString(10))),
        c.getInt(11), ConsumerLinkKind.valueOf(c.getString(12)), decodeList(c.getString(13)), c.getStringOrNull(14))) } }

    private fun method(c: android.database.Cursor, offset: Int) = MethodFingerprint(
        c.getString(offset),c.getString(offset+1),c.getString(offset+2),c.getString(offset+3),DiscoveredType.valueOf(c.getString(offset+4)),decodeList(c.getString(offset+5)),c.getInt(offset+6),c.getString(offset+7),c.getInt(offset+8)!=0,decodeList(c.getString(offset+9)),decodeList(c.getString(offset+10)))

    private fun emptyMethod() = MethodFingerprint("","","","",DiscoveredType.UNKNOWN,emptyList(),0,"",false,emptyList(),emptyList())
    private fun encodeList(values: List<String>) = JSONArray(values).toString()
    private fun decodeList(raw: String) = JSONArray(raw).let { a -> List(a.length()) { a.getString(it) } }
    private fun encodeMap(values: Map<String,String>) = JSONArray().apply { values.forEach { (k,v) -> put(JSONArray().put(k).put(v)) } }.toString()
    private fun decodeMap(raw: String): Map<String, String> = when (raw.trim().firstOrNull()) {
        '[' -> JSONArray(raw).let { a -> buildMap { repeat(a.length()) { val pair=a.getJSONArray(it); put(pair.getString(0),pair.getString(1)) } } }
        '{' -> JSONObject(raw).let { value -> value.keys().asSequence().associateWith { value.getString(it) } }
        else -> emptyMap()
    }
    private fun android.database.Cursor.getStringOrNull(index: Int) = if (isNull(index)) null else getString(index)
    private fun escapeLike(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    private fun encodeConfidence(value: EvidenceConfidence) = JSONObject().apply {
        put("identity", value.identity.name); put("storageType", value.storageType.name); put("semanticType", value.semanticType.name)
        put("defaultValue", value.defaultValue.name); put("getter", value.getter.name); put("runtimeConsumer", value.runtimeConsumer.name)
        put("semantics", value.semantics.name); put("evidence", JSONArray(value.evidence)); put("ambiguities", JSONArray(value.ambiguities))
    }.toString()
    private fun decodeConfidence(raw: String): EvidenceConfidence = runCatching { JSONObject(raw) }.getOrNull()?.let { value ->
        EvidenceConfidence(enumOr(value.optString("identity"), EvidenceStrength.NONE), enumOr(value.optString("storageType"), EvidenceStrength.NONE),
            enumOr(value.optString("semanticType"), EvidenceStrength.NONE), enumOr(value.optString("defaultValue"), EvidenceStrength.NONE),
            enumOr(value.optString("getter"), EvidenceStrength.NONE), enumOr(value.optString("runtimeConsumer"), EvidenceStrength.NONE),
            enumOr(value.optString("semantics"), EvidenceStrength.NONE), value.optJSONArray("evidence")?.let(::jsonStrings).orEmpty(),
            value.optJSONArray("ambiguities")?.let(::jsonStrings).orEmpty())
    } ?: EvidenceConfidence()
    private fun jsonStrings(value: JSONArray) = List(value.length()) { value.getString(it) }
    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
    private fun identifierKind(value: String) = if (value == "ENUM_LITERAL") IdentifierKind.JAVA_ENUM_LITERAL else enumOr(value, IdentifierKind.UNKNOWN)

    companion object {
        private const val ROW_COLUMNS = "SELECT id,key,namespace,value_type,compiled_default,confidence,reason,occurrence_count,kind,resolution,runtime_status,semantics_reviewed,editability_reason,metadata,editable,storage_type,semantic_type,evidence_confidence,semantics_provenance"
    }
}
