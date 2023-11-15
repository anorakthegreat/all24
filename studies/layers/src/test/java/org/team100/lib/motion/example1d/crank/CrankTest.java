package org.team100.lib.motion.example1d.crank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import edu.wpi.first.wpilibj.simulation.SimHooks;

class CrankTest {
    @Test
    void testSimple() {
        CrankContainer container = new CrankContainer();
        assertNotNull(container);
    }

    @Test
    void testActuation() {
        CrankVelocityServo servo = new CrankVelocityServo(new CrankActuation(0));
        assertNotNull(servo.m_state);
        CrankSubsystem subsystem = new CrankSubsystem(new CrankZeroVelocitySupplier1d(), servo);
        subsystem.setEnable(new CrankPositionLimit(0, 1));
        // subsystem.setFilter(new FeasibleFilter(1, 1));
        subsystem.periodic();
        assertEquals(0, servo.m_state.getVelocityM_S(), 0.001);  
    }

    @Test
    void testUnfiltered() {
        CrankVelocityServo servo = new CrankVelocityServo(new CrankActuation(0));
        assertNotNull(servo.m_state);
        CrankSubsystem subsystem = new CrankSubsystem(new CrankZeroVelocitySupplier1d(), servo);
        subsystem.setEnable(new CrankPositionLimit(0, 1));
        // subsystem.setFilter(new FeasibleFilter(1, 1));
        subsystem.periodic();
        assertEquals(0, servo.m_state.getVelocityM_S(), 0.001);
        // subsystem.setProfileFollower(new ManualVelocitySupplier1d<>(() -> 1.0, CrankWorkstate::new));
        subsystem.setConfigurationController(new CrankManualConfiguration(() -> 1.0));
        subsystem.periodic();
        // instantly the commanded velocity
        assertEquals(1, servo.m_state.getVelocityM_S(), 0.001);
    }

    // this does not yet work
   // @Test
    void testFiltered() {
        CrankVelocityServo servo = new CrankVelocityServo(new CrankActuation(0));
        CrankSubsystem subsystem = new CrankSubsystem(new CrankZeroVelocitySupplier1d(), servo);
        subsystem.setEnable(new CrankPositionLimit(0, 1));
        subsystem.setFilter(new CrankFeasibleFilter(1, 1));
        subsystem.periodic();
        assertEquals(0, servo.m_state.getVelocityM_S(), 0.001);
        subsystem.setProfileFollower(new CrankManualVelocitySupplier1d(() -> 1.0));
        SimHooks.stepTiming(0.5);
        subsystem.periodic();
        // this is acceleration limited. :-)
        // why is this not always the same?
        assertEquals(0.500, servo.m_state.getVelocityM_S(), 0.003);
    }
}
