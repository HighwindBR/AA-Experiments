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
    @Test public void incompatibleRolesRejectBeforeReflection() throws Exception {
        JSONObject x=profile();
        x.getJSONObject("roles").put("launch.auxiliary",
            "Labc/Other;->i(Landroid/content/Intent;)V");
        try{MediaStartupLabProfile.parse(x,"base","fingerprint");fail();}
        catch(IllegalArgumentException expected){}
    }
}
