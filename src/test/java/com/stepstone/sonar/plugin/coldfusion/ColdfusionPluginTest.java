package com.stepstone.sonar.plugin.coldfusion;

import org.junit.Assert;
import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.utils.Version;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basic test for ColdFusion plugin
 */
public class ColdfusionPluginTest {

    @Test
    public void testPluginExtensions() {
        // Create a mock runtime
        SonarRuntime runtime = mock(SonarRuntime.class);
        when(runtime.getApiVersion()).thenReturn(Version.create(12, 0));

        Plugin.Context context = new Plugin.Context(runtime);
        new ColdFusionPlugin().define(context);
        
        // Just verify that extensions were registered
        Assert.assertTrue("Expected at least one extension", context.getExtensions().size() > 0);
    }
}
