package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.MethodFingerprint
import java.security.MessageDigest
import kotlin.math.abs

data class SubgraphLayer(
    val depth: Int,
    val nodeCount: Int,
    val edgeCount: Int,
    val featureTokens: Set<String>,
    val digest: String
)

data class MethodSubgraphProfile(
    val rootSignature: String,
    val method: MethodFingerprint,
    val layers: List<SubgraphLayer>
)

data class SubgraphCandidate(
    val old: MethodSubgraphProfile,
    val new: MethodSubgraphProfile,
    val score: Int,
    val reasons: List<String>
)

data class GlobalSubgraphResult(
    val matches: Map<String, SubgraphCandidate>,
    val ambiguousOldMethods: Set<String>,
    val unclaimedNewMethods: Set<String>
)

data class ClassSubgraphProfile(
    val className: String,
    val methodCount: Int,
    val localTokens: Set<String>,
    val incomingStableOwners: Map<String, Int>,
    val outgoingStableOwners: Map<String, Int>
)

data class GlobalClassMatchResult(
    val matches: Map<String, String>,
    val ambiguousOldClasses: Set<String>,
    val score: Int
)

/** Class-family evidence used when individual wrapper fingerprints are symmetric. */
object CompactClassGraphProfiler {
    fun profiles(methods: Collection<MethodFingerprint>, classNames: Set<String>): Map<String, ClassSubgraphProfile> {
        val bySignature = methods.associateBy(CompactMethodGraphProfiler::signature)
        val incoming = mutableMapOf<String, MutableList<MethodFingerprint>>()
        methods.forEach { caller -> caller.invokedMethods.forEach { called ->
            if (called in bySignature) incoming.getOrPut(called, ::mutableListOf).add(caller)
        } }
        return classNames.associateWith { className ->
            val members = methods.filter { it.className == className }
            val local = members.flatMap { method -> listOf(
                "abi:${method.descriptor}",
                "size:${method.descriptor}:${method.instructionCount / 4}",
                "opcode:${method.opcodeSha256.take(16)}"
            ) }.toSet()
            val inbound = mutableMapOf<String, Int>()
            val seen = members.mapTo(mutableSetOf(), CompactMethodGraphProfiler::signature)
            var frontier = seen.toSet()
            for (depth in 1..3) {
                val next = linkedSetOf<String>()
                frontier.forEach { target -> incoming[target].orEmpty().forEach { caller ->
                    val callerSignature = CompactMethodGraphProfiler.signature(caller)
                    if (!seen.add(callerSignature)) return@forEach
                    val owner = stableOwner(caller.className)
                    if (owner != null) {
                        val weight = 4 - depth
                        val ownerKey = "owner:$owner"
                        // A stable owner plus its call-site method name is useful even
                        // when the descriptor contains renamed app-internal classes.
                        val methodKey = "method:$owner#${caller.methodName}"
                        inbound[ownerKey] = (inbound[ownerKey] ?: 0) + weight
                        inbound[methodKey] = (inbound[methodKey] ?: 0) + weight
                    } else next += callerSignature
                } }
                frontier = next
            }
            val outbound = mutableMapOf<String, Int>()
            members.forEach { member -> member.invokedMethods.forEach { call ->
                val owner = call.substringBefore("->")
                if (owner != className) stableOwner(owner)?.let { outbound[it] = (outbound[it] ?: 0) + 1 }
            } }
            ClassSubgraphProfile(className, members.size, local, inbound, outbound)
        }
    }

    private fun stableOwner(descriptor: String): String? {
        val simple = descriptor.removePrefix("L").removeSuffix(";").substringAfterLast('/')
        return simple.takeIf { it.length > 4 && it.any(Char::isUpperCase) }
    }
}

/** Exact global assignment for small class families; ties remain ambiguous. */
object GlobalClassSubgraphMatcher {
    fun match(old: Collection<ClassSubgraphProfile>, current: Collection<ClassSubgraphProfile>, minimumMargin: Int = 8): GlobalClassMatchResult {
        if (old.size != current.size || old.size > 10) return GlobalClassMatchResult(emptyMap(), old.mapTo(mutableSetOf()) { it.className }, 0)
        val left = old.toList()
        val right = current.toList()
        data class Solution(val score: Int, val pairs: Map<String, String>)
        fun solve(forbidden: Pair<String, String>? = null): Solution {
            var best = Solution(Int.MIN_VALUE, emptyMap())
            fun visit(index: Int, used: Int, score: Int, pairs: MutableMap<String, String>) {
                if (index == left.size) {
                    if (score > best.score) best = Solution(score, pairs.toMap())
                    return
                }
                right.indices.forEach { candidate ->
                    if (used and (1 shl candidate) != 0) return@forEach
                    val pair = left[index].className to right[candidate].className
                    if (pair == forbidden) return@forEach
                    pairs[pair.first] = pair.second
                    visit(index + 1, used or (1 shl candidate), score + score(left[index], right[candidate]), pairs)
                    pairs.remove(pair.first)
                }
            }
            visit(0, 0, 0, linkedMapOf())
            return best
        }
        val optimal = solve()
        val accepted = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()
        optimal.pairs.forEach { (from, to) ->
            val alternative = solve(from to to)
            if (optimal.score - alternative.score >= minimumMargin) accepted[from] = to else ambiguous += from
        }
        return GlobalClassMatchResult(accepted, ambiguous, optimal.score)
    }

    fun score(old: ClassSubgraphProfile, current: ClassSubgraphProfile): Int {
        val local = jaccard(old.localTokens, current.localTokens)
        val incoming = multisetSimilarity(old.incomingStableOwners, current.incomingStableOwners)
        val outgoing = multisetSimilarity(old.outgoingStableOwners, current.outgoingStableOwners)
        val count = if (abs(old.methodCount - current.methodCount) <= 1) 15 else 0
        return (local * 35 + incoming * 40 + outgoing * 25).toInt() + count
    }

    private fun <T> jaccard(left: Set<T>, right: Set<T>): Double =
        if (left.isEmpty() && right.isEmpty()) 1.0 else (left intersect right).size.toDouble() / (left union right).size.coerceAtLeast(1)

    private fun multisetSimilarity(left: Map<String, Int>, right: Map<String, Int>): Double {
        val keys = left.keys + right.keys
        if (keys.isEmpty()) return 1.0
        val common = keys.sumOf { minOf(left[it] ?: 0, right[it] ?: 0) }
        val total = keys.sumOf { maxOf(left[it] ?: 0, right[it] ?: 0) }
        return common.toDouble() / total.coerceAtLeast(1)
    }
}

/** Compact, name-independent multi-method neighborhood profiler. */
object CompactMethodGraphProfiler {
    fun profiles(methods: Collection<MethodFingerprint>, radius: Int = 2): Map<String, MethodSubgraphProfile> {
        val bySignature = methods.associateBy(::signature)
        val incoming = incoming(bySignature)
        return bySignature.mapValues { (signature, method) -> profile(signature, method, bySignature, incoming, radius) }
    }

    fun profileRoots(methods: Collection<MethodFingerprint>, roots: Collection<MethodFingerprint>, radius: Int = 2): Map<String, MethodSubgraphProfile> {
        val bySignature = methods.associateBy(::signature)
        val incoming = incoming(bySignature)
        return roots.associate { root -> signature(root) to profile(signature(root), root, bySignature, incoming, radius) }
    }

    private fun profile(
        rootSignature: String,
        root: MethodFingerprint,
        methods: Map<String, MethodFingerprint>,
        incoming: Map<String, Set<String>>,
        radius: Int
    ): MethodSubgraphProfile {
        val seen = linkedSetOf(rootSignature)
        var frontier = linkedSetOf(rootSignature)
        val layers = buildList {
            for (depth in 0..radius) {
                val nodes = frontier.mapNotNull(methods::get)
                val edges = nodes.sumOf { method ->
                    method.invokedMethods.count { it in methods } + incoming[signature(method)].orEmpty().size
                }
                val tokens = nodes.flatMap { method ->
                    tokens(method) + incoming[signature(method)].orEmpty().mapNotNull(methods::get).flatMap { caller ->
                        buildList {
                            add("caller:${methodShape(caller)}")
                            ownerAnchor(caller.className)?.let { add("caller-owner:$it") }
                        }
                    }
                }.toSortedSet()
                add(SubgraphLayer(depth, nodes.size, edges, tokens, digest(tokens.joinToString("\n"))))
                val next = linkedSetOf<String>()
                nodes.forEach { method ->
                    method.invokedMethods.forEach { invoked -> if (invoked in methods && seen.add(invoked)) next += invoked }
                    incoming[signature(method)].orEmpty().forEach { caller -> if (seen.add(caller)) next += caller }
                }
                frontier = next
            }
        }
        return MethodSubgraphProfile(rootSignature, root, layers)
    }

    private fun tokens(method: MethodFingerprint): List<String> = buildList {
        add("return:${method.returnType}")
        add("parameters:${method.parameterTypes.joinToString(",")}")
        add("instructions4:${method.instructionCount / 4}")
        method.referencedFields.map { it.substringAfter(':') }.sorted().forEach { add("field:$it") }
        method.invokedMethods.sorted().forEach {
            add("call:${callShape(it)}")
            ownerAnchor(it.substringBefore("->"))?.let { owner -> add("callee-owner:$owner") }
        }
    }

    private fun callShape(value: String) = value.substringAfter("->").substringAfter('(').let { "($it" }
    private fun methodShape(method: MethodFingerprint) = "${method.descriptor}:${method.instructionCount / 4}:${method.opcodeSha256.take(12)}"
    private fun ownerAnchor(descriptor: String): String? {
        val simple = descriptor.removePrefix("L").removeSuffix(";").substringAfterLast('/')
        return simple.takeIf { it.length > 4 && it.any(Char::isUpperCase) }
    }
    private fun incoming(methods: Map<String, MethodFingerprint>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        methods.forEach { (caller, method) ->
            method.invokedMethods.forEach { called -> if (called in methods) result.getOrPut(called, ::linkedSetOf).add(caller) }
        }
        return result
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun signature(method: MethodFingerprint) = "${method.className}->${method.methodName}${method.descriptor}"
}

/** Precision-first one-to-one matching over whole candidate components. */
object GlobalSubgraphMatcher {
    fun match(
        oldProfiles: Collection<MethodSubgraphProfile>,
        newProfiles: Collection<MethodSubgraphProfile>,
        minimumScore: Int = 120,
        minimumMargin: Int = 15
    ): GlobalSubgraphResult {
        val candidates = oldProfiles.associate { old ->
            old.rootSignature to newProfiles.asSequence()
                .filter { compatible(old.method, it.method) }
                .map { score(old, it) }
                .filter { it.score >= minimumScore }
                .sortedByDescending { it.score }
                .toList()
        }
        val accepted = mutableMapOf<String, SubgraphCandidate>()
        val ambiguous = mutableSetOf<String>()
        connectedComponents(candidates).forEach { component ->
            // Exact assignment is intentionally bounded. A large, densely ambiguous
            // component is unsafe to guess and is therefore suspended.
            if (component.old.size > 12 || component.new.size > 12) {
                ambiguous += component.old
                return@forEach
            }
            val solution = solve(component, candidates)
            solution.assignment.forEach { (old, candidate) ->
                val alternative = solve(component, candidates, old to candidate.new.rootSignature)
                if (solution.score - alternative.score >= minimumMargin) accepted[old] = candidate
                else ambiguous += old
            }
            ambiguous += component.old - solution.assignment.keys
        }
        candidates.filterValues { it.isNotEmpty() }.keys.filterNotTo(ambiguous) { it in accepted }
        val claimed = accepted.values.mapTo(mutableSetOf()) { it.new.rootSignature }
        return GlobalSubgraphResult(accepted, ambiguous, newProfiles.mapTo(mutableSetOf()) { it.rootSignature } - claimed)
    }

    private data class Component(val old: Set<String>, val new: Set<String>)
    private data class Assignment(val score: Int, val assignment: Map<String, SubgraphCandidate>)

    private fun connectedComponents(candidates: Map<String, List<SubgraphCandidate>>): List<Component> {
        val reverse = mutableMapOf<String, MutableSet<String>>()
        candidates.forEach { (old, edges) -> edges.forEach { reverse.getOrPut(it.new.rootSignature, ::linkedSetOf).add(old) } }
        val remaining = candidates.filterValues { it.isNotEmpty() }.keys.toMutableSet()
        return buildList {
            while (remaining.isNotEmpty()) {
                val oldSet = linkedSetOf<String>()
                val newSet = linkedSetOf<String>()
                val queue = ArrayDeque<String>().apply { add(remaining.first()) }
                while (queue.isNotEmpty()) {
                    val old = queue.removeFirst()
                    if (!oldSet.add(old)) continue
                    remaining.remove(old)
                    candidates.getValue(old).forEach { edge ->
                        if (newSet.add(edge.new.rootSignature)) reverse[edge.new.rootSignature].orEmpty().forEach(queue::add)
                    }
                }
                add(Component(oldSet, newSet))
            }
        }
    }

    /** Maximum-weight bipartite assignment for one ambiguity component. */
    private fun solve(
        component: Component,
        candidates: Map<String, List<SubgraphCandidate>>,
        forbidden: Pair<String, String>? = null
    ): Assignment {
        val old = component.old.sortedBy { candidates.getValue(it).size }
        val newIndex = component.new.withIndex().associate { it.value to it.index }
        data class State(val score: Int, val edges: List<SubgraphCandidate>)
        val memo = mutableMapOf<Pair<Int, Int>, State>()
        fun visit(index: Int, used: Int): State {
            if (index == old.size) return State(0, emptyList())
            val key = index to used
            memo[key]?.let { return it }
            val oldSignature = old[index]
            var best = visit(index + 1, used)
            candidates.getValue(oldSignature).forEach { edge ->
                if (forbidden == (oldSignature to edge.new.rootSignature)) return@forEach
                val bit = 1 shl newIndex.getValue(edge.new.rootSignature)
                if (used and bit != 0) return@forEach
                val tail = visit(index + 1, used or bit)
                val proposed = State(edge.score + tail.score, listOf(edge) + tail.edges)
                if (proposed.score > best.score) best = proposed
            }
            memo[key] = best
            return best
        }
        val best = visit(0, 0)
        return Assignment(best.score, best.edges.associateBy { it.old.rootSignature })
    }

    fun score(old: MethodSubgraphProfile, new: MethodSubgraphProfile): SubgraphCandidate {
        var score = 0
        val reasons = mutableListOf<String>()
        if (old.method.returnType == new.method.returnType) { score += 25; reasons += "same return type" }
        if (old.method.parameterTypes == new.method.parameterTypes) { score += 20; reasons += "same parameter types" }
        if (old.method.opcodeSha256 == new.method.opcodeSha256) { score += 35; reasons += "identical root opcode sequence" }
        else if (abs(old.method.instructionCount - new.method.instructionCount) <= 2) { score += 18; reasons += "similar root size" }
        old.layers.zip(new.layers).forEach { (left, right) ->
            val similarity = jaccard(left.featureTokens, right.featureTokens)
            val layerScore = (similarity * when (left.depth) { 0 -> 45; 1 -> 35; else -> 25 }).toInt()
            score += layerScore
            if (left.digest == right.digest) reasons += "identical layer ${left.depth}"
            else if (similarity >= .7) reasons += "similar layer ${left.depth}"
            if (abs(left.nodeCount - right.nodeCount) <= 1) score += 3
            if (abs(left.edgeCount - right.edgeCount) <= 1) score += 2
        }
        return SubgraphCandidate(old, new, score.coerceAtMost(200), reasons)
    }

    private fun compatible(old: MethodFingerprint, new: MethodFingerprint) =
        old.returnType == new.returnType && old.parameterTypes == new.parameterTypes

    private fun <T> jaccard(left: Set<T>, right: Set<T>): Double {
        if (left.isEmpty() && right.isEmpty()) return 1.0
        return (left intersect right).size.toDouble() / (left union right).size.coerceAtLeast(1)
    }
}
