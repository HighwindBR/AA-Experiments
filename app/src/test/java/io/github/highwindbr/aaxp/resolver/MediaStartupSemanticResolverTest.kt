package io.github.highwindbr.aaxp.resolver

import io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver as S
import io.github.highwindbr.aaxp.runtime.MediaStartupTrace
import org.junit.Assert.*
import org.junit.Test

class MediaStartupSemanticResolverTest {
    private fun method(owner: String, name: String, params: List<String> = emptyList(),
        ret: String = "V", stat: Boolean = false, vararg anchors: String) =
        S.Method(owner, name, params, ret, stat, anchors.toSet())

    private val owner = "La/b/ProjectedService;"
    private val route = "Lxy/Ab;"
    private val options = "Lq;"
    private fun fixture(): S.Inventory = S.Inventory(listOf(
        method(owner, "a", anchors = *arrayOf("onProjectionReady(). Notifying CarClientManager that projection is ready")),
        method(owner, "b", anchors = *arrayOf("onProjectionEnd()")),
        method(owner, "c", anchors = *arrayOf("onProjectionTearDown()")),
        method(route, "z", listOf("Landroid/content/Intent;", options),
            anchors = *arrayOf("startCarActivity(intent:%s, activityOptions:%s)")),
        method(route, "o", ret = route, stat = true),
        method("Lm;", "d", listOf("Landroid/content/ComponentName;"),
            anchors = *arrayOf("onMediaAppChanged %s"))
    ), setOf(options))

    @Test fun identicalSemanticStructureOnRenamedMethodsRequiresReview() {
        val inv = fixture()
        val result = S.resolve(inv, "unreviewed-APK-hash")
        assertEquals(S.Status.REVIEW_ONLY, result.status)
        assertEquals(false, result.autoLaunchAllowed)
        assertEquals(7, result.roles.size)
        assertTrue(result.roles["launch.startCarActivity"]!!.contains("->z("))
    }

    @Test fun alreadyVerifiedBuildRemainsKnownButActivePermissionIsNotGrantedByReport() {
        val result = S.resolve(fixture(), MediaStartupTrace.BASE_SHA256)
        assertEquals(S.Status.KNOWN_BUILD_VALIDATED, result.status)
        assertFalse(result.autoLaunchAllowed)
        assertTrue(result.missing.isEmpty())
    }

    @Test fun duplicateLaunchRoutesCannotWinByFirstOrLexicographicOrder() {
        val old = fixture()
        val other = method("Lnew/Other;", "k",
            listOf("Landroid/content/Intent;", "Lx;"),
            anchors = *arrayOf("startCarActivity(intent:%s, activityOptions:%s)"))
        val result = S.resolve(old.copy(methods = old.methods + other), "new")
        assertEquals(S.Status.AMBIGUOUS, result.status)
        assertFalse(result.autoLaunchAllowed)
    }

    @Test fun lifecycleMustBeFromSameOwnerAndAllThreeAnchorsPresent() {
        val original = fixture()
        val swapped = original.copy(methods = original.methods.map {
            if (it.name == "b") it.copy(owner = "Lanother;") else it
        })
        assertEquals(S.Status.UNSUPPORTED, S.resolve(swapped, "new").status)
        assertEquals(S.Status.UNSUPPORTED,
            S.resolve(original.copy(methods = original.methods.filterNot { it.name == "c" }), "new").status)
    }

    @Test fun wrongFactoryAndMissingOptionsNeverAuthorizeLaunch() {
        val old = fixture()
        assertEquals(S.Status.UNSUPPORTED, S.resolve(
            old.copy(methods = old.methods.filterNot { it.name == "o" }), "new").status)
        assertEquals(S.Status.UNSUPPORTED, S.resolve(
            old.copy(noArgConstructors = emptySet()), "new").status)
    }

    @Test fun mediaSelectionRequiresTypedComponentAndUniqueAnchor() {
        val old = fixture()
        assertEquals(S.Status.UNSUPPORTED, S.resolve(old.copy(methods = old.methods.map {
            if (it.owner == "Lm;") it.copy(parameters = listOf("Landroid/content/Intent;")) else it
        }), "new").status)
        val duplicate = method("Lm2;", "a", listOf("Landroid/content/ComponentName;"),
            anchors = *arrayOf("onMediaAppChanged called current=%s"))
        assertEquals(S.Status.UNSUPPORTED,
            S.resolve(old.copy(methods = old.methods + duplicate), "new").status)
    }

    @Test fun parserOrBudgetErrorFailsClosed() {
        val result = S.resolve(fixture().copy(errors = listOf("truncated dex")), "any")
        assertEquals(S.Status.ERROR, result.status)
        assertFalse(result.autoLaunchAllowed)
        assertTrue(result.roles.isEmpty())
    }

    @Test fun resolverDoesNotUseObfuscatedNames() {
        val old = fixture()
        val changed = old.copy(methods = old.methods.map {
            it.copy(name = when (it.name) {
                "a" -> "zz1"; "b" -> "zz2"; "c" -> "zz3"
                "z" -> "ff"; "o" -> "gg"; "d" -> "hh"; else -> it.name
            })
        })
        assertEquals(S.Status.REVIEW_ONLY, S.resolve(changed, "new").status)
    }
}
