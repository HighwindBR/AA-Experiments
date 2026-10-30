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
        method(route, "i", listOf("Landroid/content/Intent;"),
            anchors = *emptyArray<String>()).copy(invoked = setOf(
                "$route->z(Landroid/content/Intent;$options)V",
                "$options-><init>()V")),
        method("Lrun/Tracker;", "run", stat = false)
            .copy(runnable = true, invoked = setOf(
                "$route->o()$route",
                "Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;",
                "Landroid/content/Intent;->setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;",
                "Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;",
                "Landroid/os/SystemClock;->elapsedRealtime()J"
            )),
        method("Lm;", "d", listOf("Landroid/content/ComponentName;"),
            anchors = *arrayOf("onMediaAppChanged %s"))
    ), setOf(options))

    @Test fun identicalSemanticStructureOnRenamedMethodsRequiresReview() {
        val inv = fixture()
        val result = S.resolve(inv, "unreviewed-APK-hash")
        assertEquals(S.Status.REVIEW_ONLY, result.status)
        assertEquals(false, result.autoLaunchAllowed)
        assertEquals(9, result.roles.size)
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

    @Test fun activityTrackerRequiresUniqueRunnableWithStructuralCallsAndFactory() {
        val original = fixture()
        val tracker = original.methods.first { it.runnable }
        assertEquals("Lrun/Tracker;->run()V", S.resolve(original,"new").roles["activity.firstProjected"])
        val noFactory = original.copy(methods = original.methods.map {
            if (it.runnable) it.copy(invoked = it.invoked - "$route->o()$route") else it
        })
        assertNull(S.resolve(noFactory,"new").roles["activity.firstProjected"])
        assertEquals(S.Status.REVIEW_ONLY,S.resolve(noFactory,"new").status)
        val duplicate = original.copy(methods = original.methods +
            tracker.copy(owner = "Lrun/Other;"))
        assertNull(S.resolve(duplicate,"new").roles["activity.firstProjected"])
        val noApi = original.copy(methods = original.methods.map {
            if (it.runnable) it.copy(invoked =
                it.invoked - "Landroid/os/SystemClock;->elapsedRealtime()J") else it
        })
        assertNull(S.resolve(noApi,"new").roles["activity.firstProjected"])
    }

    @Test fun parserOrBudgetErrorFailsClosed() {
        val result = S.resolve(fixture().copy(errors = listOf("truncated dex")), "any")
        assertEquals(S.Status.ERROR, result.status)
        assertFalse(result.autoLaunchAllowed)
        assertTrue(result.roles.isEmpty())
    }

    @Test fun auxiliaryMustActuallyDelegateToControllerAndConstructOptions() {
        val original = fixture()
        val helper = original.methods.first { it.name == "i" }
        val broken = original.copy(methods = original.methods.map {
            if (it === helper) it.copy(invoked = setOf("$options-><init>()V"))
            else it
        })
        assertEquals(S.Status.UNSUPPORTED, S.resolve(broken, "new").status)
        val twoHelpers = original.copy(methods = original.methods +
            helper.copy(name = "another"))
        assertEquals(S.Status.AMBIGUOUS, S.resolve(twoHelpers, "new").status)
    }

    @Test fun resolverDoesNotUseObfuscatedNames() {
        val old = fixture()
        val changed = old.copy(methods = old.methods.map {
            val renamed = when (it.name) {
                "a" -> "zz1"; "b" -> "zz2"; "c" -> "zz3"
                "z" -> "ff"; "o" -> "gg"; "d" -> "hh"; "i" -> "ii"; else -> it.name
            }
            it.copy(name = renamed, invoked = it.invoked.map { call ->
                when {
                    call.startsWith("$route->z(") -> call.replace("$route->z(", "$route->ff(")
                    call.startsWith("$route->o(") -> call.replace("$route->o(", "$route->gg(")
                    else -> call
                }
            }.toSet())
        })
        assertEquals(S.Status.REVIEW_ONLY, S.resolve(changed, "new").status)
    }
}
