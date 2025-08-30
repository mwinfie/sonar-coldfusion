package com.stepstone.sonar.plugin.coldfusion;

import org.junit.Assert;
import org.junit.Test;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.SensorDescriptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ColdfusionSensorSimpleTest {

    @Test
    public void testSensorDescriptor() {
        FileSystem fileSystem = mock(FileSystem.class);
        ActiveRules activeRules = mock(ActiveRules.class);
        ColdFusionSensor sensor = new ColdFusionSensor(fileSystem, activeRules);
        
        SensorDescriptor descriptor = mock(SensorDescriptor.class);
        sensor.describe(descriptor);
        
        // Verify that the sensor configures itself correctly
        verify(descriptor).onlyOnLanguage(ColdFusionPlugin.LANGUAGE_KEY);
        verify(descriptor).createIssuesForRuleRepository(ColdFusionPlugin.REPOSITORY_KEY);
    }
    
    @Test
    public void testSensorCreation() {
        FileSystem fileSystem = mock(FileSystem.class);
        ActiveRules activeRules = mock(ActiveRules.class);
        
        // Test that sensor can be created without exceptions
        ColdFusionSensor sensor = new ColdFusionSensor(fileSystem, activeRules);
        Assert.assertNotNull("Sensor should be created successfully", sensor);
    }
}
