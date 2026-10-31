package io.github.highwindbr.aaxp.runtime;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaStartupLabProfileTest {
    private JSONObject profile() throws Exception {
        JSONObject roles = new JSONObject();
        roles.put("projection.ready","Labc/Lifecycle;->f()V");
        roles.put("projection.end","Labc/Lifecycle;->d()V");
        roles.put("projection.teardown","Labc/Lifecycle;->h()V");
        roles.put("launch.controllerFactory","Labc/Controller;->a()Labc/Controller;");
        roles.put("launch.startCarActivity","Labc/Controller;->k(Landroid/content/Intent;Labc/Options;)V");
        roles.put("launch.auxiliary","Labc/Controller;->i(Landroid/content/Intent;)V");
        roles.put("launch.optionsNoArgConstructor","Labc/Options;-><init>()V");
        roles.put("media.selectedApp","Labc/Media;->l(Landroid/content/ComponentName;)V");
        return new JSONObject().put("mode","MANUAL_ONLY").put("baseSha256","base")
            .put("buildFingerprintSha256","fingerprint").put("roles",roles);
    }
    @Test public void validBuildBoundRolesParse() throws Exception {
        MediaStartupLabProfile x=MediaStartupLabProfile.parse(profile(),"base","fingerprint");
        assertEquals(8,x.targets.size());
        assertEquals("abc.Controller",x.role("launch.auxiliary").javaOwner());
    }
    @Test public void fingerprintOrModeMismatchRejects() throws Exception {
        for(String base:new String[]{"other","BASE-EDIT"}) {
            try{MediaStartupLabProfile.parse(profile(),base,"fingerprint");fail();}
            catch(IllegalArgumentException expected){}
        }
        JSONObject x=profile().put("mode","AUTO");
        try{MediaStartupLabProfile.parse(x,"base","fingerprint");fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void newModeRequiresValidActivityRole() throws Exception {
        JSONObject profile = profile().put("mode",MediaStartupLabProfile.MODE);
        profile.getJSONObject("roles").put("activity.firstProjected","Ltracker;->run()V");
        MediaStartupLabProfile parsed = MediaStartupLabProfile.parse(profile,"base","fingerprint");
        assertEquals(9,parsed.targets.size());
        assertEquals(MediaStartupLabProfile.MODE,parsed.mode);
        profile.getJSONObject("roles").put("activity.firstProjected","Ltracker;->run(I)V");
        try{MediaStartupLabProfile.parse(profile,"base","fingerprint");fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void bOnlyProfileIsAcceptedAndAuxiliaryIsAbsent() throws Exception {
        JSONObject data = profile();
        data.getJSONObject("roles").remove("launch.auxiliary");
        MediaStartupLabProfile parsed = MediaStartupLabProfile.parse(data,"base","fingerprint");
        assertNull(parsed.role("launch.auxiliary"));
        assertNotNull(parsed.role("launch.startCarActivity"));
    }

    @Test public void aOnlyProfileRequiresNoDirectRoute() throws Exception {
        JSONObject data = profile();
        JSONObject roles = data.getJSONObject("roles");
        roles.remove("launch.startCarActivity");
        roles.put("launch.navigationObserver",
            "Labc/Controller;->k(Landroid/content/Intent;Labc/Options;)V");
        MediaStartupLabProfile parsed = MediaStartupLabProfile.parse(data,"base","fingerprint");
        assertNull(parsed.role("launch.startCarActivity"));
        assertNotNull(parsed.role("launch.auxiliary"));
        assertNotNull(parsed.role("launch.navigationObserver"));
    }

    @Test public void aOnlyManualProfileMayOmitNavigationObserver() throws Exception {
        JSONObject data = profile();
        JSONObject roles = data.getJSONObject("roles");
        roles.remove("launch.startCarActivity");
        roles.remove("launch.optionsNoArgConstructor");
        MediaStartupLabProfile parsed = MediaStartupLabProfile.parse(data,"base","fingerprint");
        assertNotNull(parsed.role("launch.auxiliary"));
        assertNull(parsed.role("launch.navigationObserver"));
    }

    @Test public void profileWithNoRoutesIsRejected() throws Exception {
        JSONObject data = profile();
        JSONObject roles = data.getJSONObject("roles");
        roles.remove("launch.auxiliary");
        roles.remove("launch.startCarActivity");
        try { MediaStartupLabProfile.parse(data,"base","fingerprint"); fail(); }
        catch (IllegalArgumentException expected) {}
    }

    @Test public void incompatibleRolesRejectBeforeReflection() throws Exception {
        JSONObject x=profile();
        x.getJSONObject("roles").put("launch.auxiliary",
            "Labc/Other;->i(Landroid/content/Intent;)V");
        try{MediaStartupLabProfile.parse(x,"base","fingerprint");fail();}
        catch(IllegalArgumentException expected){}
    }
}
