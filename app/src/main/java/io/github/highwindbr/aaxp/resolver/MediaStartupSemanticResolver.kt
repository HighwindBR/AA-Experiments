package io.github.highwindbr.aaxp.resolver

import io.github.highwindbr.aaxp.runtime.MediaStartupTrace
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * PF5: bounded, offline DEX proof for Media Startup. Does not produce an
 * executable mapping for new builds: an unreviewed or ambiguous result can
 * never unlock active hooks (PF6 cross-build validation remains required).
 *
 * No dependency on obfuscated owner or method names for discovery. Anchor
 * relationships, method ABI and singleton-factory/option constructor checks
 * are all required. A matching method name alone has zero authority.
 */
object MediaStartupSemanticResolver {
    private const val INTENT = "Landroid/content/Intent;"
    private const val COMPONENT = "Landroid/content/ComponentName;"
    private const val VOID = "V"
    private const val MAX_DEX_BYTES = 32 * 1024 * 1024
    private const val MAX_CANDIDATES = 256

    enum class Status { KNOWN_BUILD_VALIDATED, REVIEW_ONLY, AMBIGUOUS, UNSUPPORTED, ERROR }

    data class Method(
        val owner: String,
        val name: String,
        val parameters: List<String>,
        val returns: String,
        val static: Boolean,
        val strings: Set<String> = emptySet(),
        val invoked: Set<String> = emptySet(),
        val runnable: Boolean = false
    ) {
        fun prototype(): String = "(${parameters.joinToString("")})$returns"
        fun reference(): String = "$owner->$name${prototype()}"
    }

    data class Evidence(
        val status: Status,
        val baseSha256: String,
        val roles: Map<String, String>,
        val missing: List<String>,
        val explanation: String,
        val autoLaunchAllowed: Boolean = false
    ) {
        fun summary(): String = buildString {
            append("PF5 semantic DEX analysis: ").append(status.name).append('\n')
            append("Base SHA-256: ").append(baseSha256).append('\n')
            roles.toSortedMap().forEach { (role, method) ->
                append(role).append(": ").append(method).append('\n')
            }
            if (missing.isNotEmpty()) {
                append("Unresolved: ").append(missing.joinToString(", ")).append('\n')
            }
            append(explanation).append('\n')
            append("PF5 discovery grants no runtime permission by itself; PF6.1 manual lab is a separate explicit opt-in.")
        }
    }

    internal data class Inventory(
        val methods: List<Method>,
        val noArgConstructors: Set<String>,
        val errors: List<String> = emptyList()
    )

    /** Synthetic inputs permit deterministic negative and ambiguity tests. */
    internal fun resolve(inventory: Inventory, baseHash: String): Evidence {
        if (inventory.errors.isNotEmpty())
            return Evidence(Status.ERROR, baseHash, emptyMap(), inventory.errors,
                "DEX scan failed; no execution mapping is produced.")

        val methods = inventory.methods.distinctBy { it.reference() }
        fun byAnchor(fragment: String, predicate: (Method) -> Boolean): List<Method> =
            methods.filter { m -> m.strings.any { it.contains(fragment, ignoreCase = false) } && predicate(m) }

        val lifecycle = mapOf(
            "ready" to "onProjectionReady(). Notifying CarClientManager",
            "end" to "onProjectionEnd()",
            "teardown" to "onProjectionTearDown()"
        ).mapValues { (_, anchor) ->
            byAnchor(anchor) { it.parameters.isEmpty() && it.returns == VOID }
        }

        val ready = lifecycle["ready"].orEmpty().singleOrNull()
        val end = lifecycle["end"].orEmpty().singleOrNull()
        val teardown = lifecycle["teardown"].orEmpty().singleOrNull()
        val lifecycleOwner = ready?.owner?.takeIf { it == end?.owner && it == teardown?.owner }

        val routes = byAnchor("startCarActivity(intent:") {
            it.parameters.size == 2 && it.parameters.first() == INTENT &&
                it.returns == VOID && !it.static &&
                it.parameters[1].startsWith("L") && it.parameters[1].endsWith(";")
        }
        val route = routes.singleOrNull()
        val factory = route?.let { r ->
            methods.filter { it.owner == r.owner && it.static &&
                it.parameters.isEmpty() && it.returns == r.owner }.singleOrNull()
        }
        val options = route?.parameters?.getOrNull(1)
        val ctorProven = options != null && options in inventory.noArgConstructors
        val auxiliaries = route?.let { start ->
            methods.filter { candidate ->
                candidate.owner == start.owner && !candidate.static &&
                    candidate.parameters == listOf(INTENT) && candidate.returns == VOID &&
                    start.reference() in candidate.invoked &&
                    "$options-><init>()V" in candidate.invoked
            }
        }.orEmpty()
        val auxiliary = auxiliaries.singleOrNull()

        // ActivityTracker implements Android's stable Runnable interface. Verify
        // the controller factory *and* four framework API calls, not the name
        // of its obfuscated class or a log literal.
        val activityCandidates = factory?.let { f ->
            methods.filter { m ->
                m.runnable && !m.static && m.name == "run" &&
                    m.parameters.isEmpty() && m.returns == VOID &&
                    f.reference() in m.invoked &&
                    "Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;" in m.invoked &&
                    "Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;" in m.invoked &&
                    "Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;" in m.invoked &&
                    "Landroid/os/SystemClock;->elapsedRealtime()J" in m.invoked
            }
        }.orEmpty()
        val firstActivity = activityCandidates.singleOrNull()

        val media = byAnchor("onMediaAppChanged") {
            it.parameters == listOf(COMPONENT) && it.returns == VOID
        }.singleOrNull()

        val roles = buildMap {
            ready?.let { put("projection.ready", it.reference()) }
            end?.let { put("projection.end", it.reference()) }
            teardown?.let { put("projection.teardown", it.reference()) }
            route?.let { put("launch.startCarActivity", it.reference()) }
            auxiliary?.let { put("launch.auxiliary", it.reference()) }
            firstActivity?.let { put("activity.firstProjected", it.reference()) }
            factory?.let { put("launch.controllerFactory", it.reference()) }
            if (ctorProven) put("launch.optionsNoArgConstructor", "$options-><init>()V")
            media?.let { put("media.selectedApp", it.reference()) }
        }
        val missing = buildList {
            if (lifecycleOwner == null) add("consistent lifecycle family (ready/end/teardown)")
            if (route == null) add("unique Intent-based startCarActivity")
            if (factory == null) add("unique static controller factory")
            if (!ctorProven) add("launch options constructor")
            if (auxiliary == null) add("unique auxiliary Intent launcher with verified delegation")
            if (media == null) add("unique media selected-app observer")
        }
        val proof = missing.isEmpty()
        val validated = MediaStartupTrace.isSupported(baseHash)
        val status = when {
            !proof -> if (lifecycle.values.any { it.size > 1 } ||
                routes.size > 1 || auxiliaries.size > 1 ||
                (route != null && methods.count { it.owner == route.owner && it.static &&
                    it.parameters.isEmpty() && it.returns == route.owner } > 1))
                    Status.AMBIGUOUS else Status.UNSUPPORTED
            validated -> Status.KNOWN_BUILD_VALIDATED
            else -> Status.REVIEW_ONLY
        }
        return Evidence(status, baseHash, roles, missing,
            when (status) {
                Status.KNOWN_BUILD_VALIDATED -> "Anchor/ABI proof corroborates the already field-tested AA 18.1. Existing pinned PF4 path stays unchanged."
                Status.REVIEW_ONLY -> "Unique launch candidate on an unreviewed build. ActivityTracker: ${if (firstActivity != null) "UNIQUE" else if (activityCandidates.size > 1) "AMBIGUOUS" else "MISSING"}. Only a separately armed experimental lab may execute."
                Status.AMBIGUOUS -> "Several internal methods match. Refusing to select a controller."
                Status.UNSUPPORTED -> "Required lifecycle or launch evidence was not proven. No active remapping."
                Status.ERROR -> "Unreadable DEX."
            })
    }

    /**
     * Scans only base APK DEX, one entry at a time. Minimal method metadata
     * is retained only for literal anchor matches and their controller class.
     * Per-entry memory is bounded; never retains entire method graph.
     */
    fun inspect(baseApk: File): Evidence {
        if (!baseApk.isFile) return Evidence(Status.ERROR, "", emptyMap(),
            listOf("missing base APK"), "Base APK not readable.")
        val hash = sha256(baseApk)
        val selected = LinkedHashMap<String, Method>()
        val constructors = HashSet<String>()
        val problems = mutableListOf<String>()
        var dexCount = 0
        val activityApi = setOf(
            "Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;",
            "Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;",
            "Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;",
            "Landroid/os/SystemClock;->elapsedRealtime()J"
        )
        try {
            ZipFile(baseApk).use { zip ->
                val entries = zip.entries().asSequence().filter {
                    it.name.matches(Regex("classes(\\d*)\\.dex"))
                }.toList()
                if (entries.isEmpty()) problems.add("no DEX in base APK")
                for (entry in entries) {
                    if (entry.size > MAX_DEX_BYTES || entry.size < 0) {
                        problems.add("DEX size outside bounds: ${entry.name}")
                        break
                    }
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (bytes.size > MAX_DEX_BYTES) {
                        problems.add("DEX exceeds memory cap: ${entry.name}")
                        break
                    }
                    dexCount++
                    val dex = DexBackedDexFile.fromInputStream(
                        Opcodes.getDefault(), BufferedInputStream(bytes.inputStream()))
                    for (cls in dex.classes) {
                        val rows = cls.methods.toList()
                        val anchored = mutableListOf<Method>()
                        var relevantController = false
                        val implementsRunnable = cls.interfaces.any {
                            it.toString() == "Ljava/lang/Runnable;"
                        }
                        for (method in rows) {
                            if (method.name == "<init>" &&
                                method.parameterTypes.isEmpty() && method.returnType == VOID) {
                                constructors.add(cls.type)
                            }
                            val impl = method.implementation ?: continue
                            // Bounded structural scan: retain only run() methods
                            // that use all four stable Android API operations.
                            // Resolve the controller relationship afterwards.
                            if (implementsRunnable && method.name == "run" &&
                                method.parameterTypes.isEmpty() && method.returnType == VOID) {
                                val refs = impl.instructions.mapNotNull { ins ->
                                    val ref = (ins as? ReferenceInstruction)?.reference
                                        as? org.jf.dexlib2.iface.reference.MethodReference
                                        ?: return@mapNotNull null
                                    "${ref.definingClass}->${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
                                }.toSet()
                                if (refs.containsAll(activityApi)) {
                                    val candidate = Method(cls.type, method.name,
                                        emptyList(), VOID,
                                        AccessFlags.STATIC.isSet(method.accessFlags),
                                        invoked = refs, runnable = true)
                                    selected[candidate.reference()] = candidate
                                }
                            }
                            val anchors = linkedSetOf<String>()
                            for (instruction in impl.instructions) {
                                val value = ((instruction as? ReferenceInstruction)?.reference
                                    as? StringReference)?.string ?: continue
                                if (value.contains("onProjectionReady(). Notifying CarClientManager") ||
                                    value.contains("onProjectionEnd()") ||
                                    value.contains("onProjectionTearDown()") ||
                                    value.contains("startCarActivity(intent:") ||
                                    value.contains("onMediaAppChanged")) anchors.add(value)
                            }
                            if (anchors.isNotEmpty()) {
                                val match = Method(cls.type, method.name,
                                    method.parameterTypes.map { it.toString() },
                                    method.returnType, AccessFlags.STATIC.isSet(method.accessFlags), anchors)
                                anchored += match
                                if (anchors.any { it.contains("startCarActivity(intent:") }) relevantController = true
                            }
                        }
                        anchored.forEach { selected[it.reference()] = it }
                        if (relevantController) {
                            rows.forEach { method ->
                                // Retain singleton factory plus one-Intent
                                // wrappers. Examine only this already anchored
                                // controller, not the entire APK call graph.
                                val factoryLike = AccessFlags.STATIC.isSet(method.accessFlags) &&
                                    method.parameterTypes.isEmpty() && method.returnType == cls.type
                                val helperLike = !AccessFlags.STATIC.isSet(method.accessFlags) &&
                                    method.parameterTypes.map { it.toString() } ==
                                        listOf(INTENT) && method.returnType == VOID
                                if (method.implementation == null ||
                                    (!factoryLike && !helperLike)) return@forEach
                                val calls = if (!helperLike) emptySet() else
                                    method.implementation!!.instructions.mapNotNull { ins ->
                                        val ref = (ins as? ReferenceInstruction)?.reference
                                            as? org.jf.dexlib2.iface.reference.MethodReference
                                            ?: return@mapNotNull null
                                        "${ref.definingClass}->${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
                                    }.toSet()
                                val item = Method(cls.type, method.name,
                                    method.parameterTypes.map { it.toString() },
                                    method.returnType, factoryLike, invoked = calls)
                                selected.putIfAbsent(item.reference(), item)
                            }
                        }
                        if (selected.size > MAX_CANDIDATES) {
                            problems.add("candidate budget exceeded")
                            break
                        }
                    }
                    if (problems.isNotEmpty()) break
                }
            }
        } catch (e: Exception) {
            problems.add("DEX parse: ${e.javaClass.simpleName}")
        }
        if (dexCount == 0) problems.add("no DEX parsed")
        return resolve(Inventory(selected.values.toList(), constructors, problems), hash)
    }

    private fun sha256(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                d.update(bytes, 0, count)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}
