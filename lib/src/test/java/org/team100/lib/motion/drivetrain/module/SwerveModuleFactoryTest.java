package org.team100.lib.motion.drivetrain.module;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.team100.lib.encoder.turning.AnalogTurningEncoder;
import org.team100.lib.motion.drivetrain.kinodynamics.SwerveKinodynamics;
import org.team100.lib.motion.drivetrain.kinodynamics.SwerveKinodynamicsFactory;
import org.team100.lib.testing.TimelessTest;

/** This just exercises the code. */
class SwerveModuleFactoryTest extends TimelessTest {
    @Test
    void testWCP() {
        SwerveKinodynamics k = SwerveKinodynamicsFactory.forTest();
        SwerveModule100 module = WCPSwerveModule100.get("test", 0, 0, 0, 0, 0, k);
        assertNotNull(module);
        module.close();
    }

    @Test
    void testAMCAN() {
        SwerveKinodynamics k = SwerveKinodynamicsFactory.forTest();
        SwerveModule100 module = AMCANSwerveModule100.get("test", 0, 0, 0, 0, 0, AnalogTurningEncoder.Drive.DIRECT, k);
        assertNotNull(module);
        module.close();
    }

    @Test
    void testAM() {
        SwerveKinodynamics k = SwerveKinodynamicsFactory.forTest();
        SwerveModule100 module = AMSwerveModule100.get("test", 0, 0, 0, 0, 0, k);
        assertNotNull(module);
        module.close();
    }
}
