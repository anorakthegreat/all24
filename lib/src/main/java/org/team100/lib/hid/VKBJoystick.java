package org.team100.lib.hid;

import static org.team100.lib.hid.ControlUtil.clamp;
import static org.team100.lib.hid.ControlUtil.deadband;
import static org.team100.lib.hid.ControlUtil.expo;

import org.team100.lib.geometry.GeometryUtil;
import org.team100.lib.logging.Level;
import org.team100.lib.logging.LoggerFactory;
import org.team100.lib.logging.LoggerFactory.DoubleLogger;
import org.team100.lib.logging.LoggerFactory.EnumLogger;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.XboxController;

/**
 * This is a Microsoft Xbox controller, Logitech F310, or similar.
 * 
 * Controls mapping (please keep this in sync with the code below):
 * 
 * <pre>
 * left trigger [0,1]     == medium speed
 * left bumper button     == slow speed
 * left stick x [-1,1]    == omega
 * left stick y [-1,1]    ==
 * left stick button      == drive-to-amp
 * dpad/pov angle [0,360] == snaps
 * "back" button          == reset 0 rotation
 * "start" button         == reset 180 rotation
 * right stick x [-1,1]   == x velocity
 * right stick y [-1,1]   == y velocity
 * right stick button     == 
 * x button               == full cycle
 * y button               == drive to note
 * a button               == lock rotation to amp
 * b button               == aim and shoot
 * right trigger [0,1]    ==
 * right bumper button    ==
 * </pre>
 * 
 * Do not use stick buttons, they are prone to stray clicks
 */
public class VKBJoystick implements DriverControl {
    private static final double kDeadband = 0.1;
    private static final double kExpo = 0.65;
    private static final double kMedium = 0.5;
    private static final double kSlow = 0.15;

    private final Joystick m_controller;
    private final DoubleLogger m_log_right_y;
    private final DoubleLogger m_log_right_x;
    private final DoubleLogger m_log_left_x;
    private final EnumLogger m_log_speed;

    Rotation2d previousRotation = GeometryUtil.kRotationZero;

    public VKBJoystick(LoggerFactory parent) {
        m_controller = new Joystick(0);
        LoggerFactory child = parent.child(this);
        m_log_right_y = child.doubleLogger(Level.TRACE, "Xbox/right y");
        m_log_right_x = child.doubleLogger(Level.TRACE, "Xbox/right x");
        m_log_left_x = child.doubleLogger(Level.TRACE, "Xbox/left x");
        m_log_speed = child.enumLogger(Level.TRACE, "control_speed");
    }

    @Override
    public String getHIDName() {
        return m_controller.getName();
    }

    @Override
    public double shooterPivot() {
        // return -1.0 * m_controller.getLeftY();
        return 0;
    }

    /**
     * Applies expo to the magnitude of the cartesian input, since these are "round"
     * joysticks.
     */
    @Override
    public Velocity velocity() {
        double dx = expo(deadband(-1.0 * clamp(m_controller.getY(), 1), kDeadband, 1), kExpo);
        double dy = expo(deadband(-1.0 * clamp(m_controller.getX(), 1), kDeadband, 1), kExpo);
        double dtheta = expo(deadband(-1.0 * clamp(m_controller.getAxisType(3), 1), kDeadband, 1), kExpo);
        return new DriverControl.Velocity(dx, dy, dtheta);
    }

    /**
     * This used to be public and affect everything; now it just affects the
     * velocity() output above.
     */
    private Speed speed() {
        // // TODO 2025 version
        // // if (m_controller.getLeftBumperButton())
        // // TODO 2024 version
        // if (m_controller.getLeftBumper())
        //     return Speed.SLOW;
        // if (m_controller.getLeftTriggerAxis() > .9)
        //     return Speed.MEDIUM;
        return Speed.NORMAL;

        
    }

    @Override
    public Rotation2d desiredRotation() {
        // double desiredAngleDegrees = m_controller.getPOV();

        // if (desiredAngleDegrees < 0) {
        //     return null;
        // }
        // return Rotation2d.fromDegrees(-1.0 * desiredAngleDegrees);
        return null;

    }

    @Override
    public boolean resetRotation0() {
        return m_controller.getRawButton(2);
        // return false;
    }

    @Override
    public boolean resetRotation180() {
        return false;
    }

    @Override
    public boolean fullCycle() {
        return false;
    }

    @Override
    public boolean driveToNote() {
        return false;
    }

    @Override
    public boolean driveToAmp() {
        return false;
    }

    @Override
    public boolean ampLock() {
        return false;
    }

    @Override
    public boolean shooterLock() {
        return false;
    }

    ////////////////////////////////////
    //
    // TODO: clean these up

    @Override
    public boolean actualCircle() {
        return false;
    }

    @Override
    public boolean test() {
        return false;
    }

}
