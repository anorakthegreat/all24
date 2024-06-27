package org.team100.lib.motion.drivetrain.manual;

import java.util.function.Supplier;

import org.team100.lib.commands.drivetrain.FieldRelativeDriver;
import org.team100.lib.commands.drivetrain.HeadingLatch;
import org.team100.lib.controller.State100;
import org.team100.lib.hid.DriverControl;
import org.team100.lib.motion.drivetrain.SwerveState;
import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;
import org.team100.lib.motion.drivetrain.kinodynamics.SwerveKinodynamics;
import org.team100.lib.profile.TrapezoidProfile100;
import org.team100.lib.sensors.HeadingInterface;
import org.team100.lib.telemetry.Telemetry;
import org.team100.lib.telemetry.Telemetry.Level;
import org.team100.lib.util.DriveUtil;
import org.team100.lib.util.Math100;
import org.team100.lib.util.Names;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;

/**
 * Function that supports manual cartesian control, and both manual and locked
 * rotational control.
 * 
 * Rotation uses a profile, velocity feedforward, and positional feedback.
 */
public class ManualWithProfiledHeading implements FieldRelativeDriver {
    private static final double kDtSec = 0.02;
    private final Telemetry t = Telemetry.get();
    private final SwerveKinodynamics m_swerveKinodynamics;
    private final HeadingInterface m_heading;
    /** Absolute input supplier, null if free */
    private final Supplier<Rotation2d> m_desiredRotation;
    private final HeadingLatch m_latch;
    private final PIDController m_thetaController;
    private final PIDController m_omegaController;
    private final String m_name;

    // package private for testing
    Rotation2d m_goal = null;
    State100 m_thetaSetpoint = null;

    /**
     * 
     * @param parent
     * @param swerveKinodynamics
     * @param heading
     * @param desiredRotation    absolute input supplier, null if free. usually
     *                           POV-derived.
     * @param thetaController
     * @param omegaController
     */
    public ManualWithProfiledHeading(
            String parent,
            SwerveKinodynamics swerveKinodynamics,
            HeadingInterface heading,
            Supplier<Rotation2d> desiredRotation,
            PIDController thetaController,
            PIDController omegaController) {
        m_swerveKinodynamics = swerveKinodynamics;
        m_heading = heading;
        m_desiredRotation = desiredRotation;
        m_thetaController = thetaController;
        m_omegaController = omegaController;
        m_name = Names.append(parent, this);
        m_latch = new HeadingLatch();
    }

    public void reset(Pose2d currentPose) {
        m_goal = null;
        m_latch.unlatch();
        m_thetaController.reset();
        m_omegaController.reset();
        updateSetpoint(currentPose.getRotation().getRadians(), getHeadingRateNWURad_S());
    }

    private double getHeadingRateNWURad_S() {
        return m_heading.getHeadingRateNWU();
    }

    /** Call this to keep the setpoint in sync with the manual rotation. */
    private void updateSetpoint(double x, double v) {
        m_thetaSetpoint = new State100(x, v);
    }

    /**
     * Clips the input to the unit circle, scales to maximum (not simultaneously
     * feasible) speeds, and then desaturates to a feasible holonomic velocity.
     * 
     * If you touch the POV and not the twist rotation, it remembers the POV. if you
     * use the twist rotation, it forgets and just uses that.
     * 
     * Desaturation prefers the rotational profile completely in the snap case, and
     * normally in the non-snap case.
     * 
     * This uses a fixed dt = 0.02 for the profile.
     * 
     * @param state    current drivetrain state from the pose estimator
     * @param twist1_1 control units, [-1,1]
     * @return feasible field-relative velocity in m/s and rad/s
     */
    public FieldRelativeVelocity apply(SwerveState state, DriverControl.Velocity twist1_1) {
        Pose2d currentPose = state.pose();

        // clip the input to the unit circle
        DriverControl.Velocity clipped = DriveUtil.clampTwist(twist1_1, 1.0);
        // scale to max in both translation and rotation
        FieldRelativeVelocity twistM_S = DriveUtil.scale(
                clipped,
                m_swerveKinodynamics.getMaxDriveVelocityM_S(),
                m_swerveKinodynamics.getMaxAngleSpeedRad_S());

        Rotation2d currentRotation = currentPose.getRotation();
        double headingMeasurement = currentRotation.getRadians();
        double headingRate = getHeadingRateNWURad_S();

        Rotation2d pov = m_desiredRotation.get();
        m_goal = m_latch.latchedRotation(currentRotation, pov, twistM_S.theta());
        if (m_goal == null) {
            // we're not in snap mode, so it's pure manual
            // in this case there is no setpoint
            m_thetaSetpoint = null;
            t.log(Level.TRACE, m_name, "mode", "free");
            // desaturate to feasibility
            return m_swerveKinodynamics.analyticDesaturation(twistM_S);
        }

        // take the short path
        m_goal = new Rotation2d(
                Math100.getMinDistance(headingMeasurement, m_goal.getRadians()));

        // if this is the first run since the latch, then the setpoint should be
        // whatever the measurement is
        if (m_thetaSetpoint == null) {
            // TODO: to avoid overshoot, maybe pick a setpoint that is feasible without
            // overshoot?
            updateSetpoint(headingMeasurement, headingRate);
        }

        // use the modulus closest to the measurement
        m_thetaSetpoint = new State100(
                Math100.getMinDistance(headingMeasurement, m_thetaSetpoint.x()),
                m_thetaSetpoint.v());

        // in snap mode we take dx and dy from the user, and use the profile for dtheta.
        // the omega goal in snap mode is always zero.
        State100 goalState = new State100(m_goal.getRadians(), 0);

        // the profile has no state and is ~free to instantiate so make a new one every
        // time. the max speed adapts to the observed speed (plus a little).
        // the max speed should be half of the absolute max, to compromise translation
        // and rotation, unless the actual translation speed is less, in which case we
        // can rotate faster.

        // how fast do we want to go?
        double xySpeed = twistM_S.norm();
        // fraction of the maximum speed
        double xyRatio = Math.min(1, xySpeed / m_swerveKinodynamics.getMaxDriveVelocityM_S());
        // fraction left for rotation
        double oRatio = 1 - xyRatio;
        // actual speed is at least half
        double kRotationSpeed = Math.max(0.5, oRatio);

        double maxSpeedRad_S = Math.max(Math.abs(headingRate) + 0.001,
                m_swerveKinodynamics.getMaxAngleSpeedRad_S() * kRotationSpeed) * 0.1;
        double maxAccelRad_S2 = m_swerveKinodynamics.getMaxAngleAccelRad_S2() * kRotationSpeed * 0.1;

        t.log(Level.TRACE, m_name, "maxSpeedRad_S", maxSpeedRad_S);
        t.log(Level.TRACE, m_name, "maxAccelRad_S2", maxAccelRad_S2);

        final TrapezoidProfile100 m_profile = new TrapezoidProfile100(
                maxSpeedRad_S,
                maxAccelRad_S2,
                0.01);

        m_thetaSetpoint = m_profile.calculate(kDtSec, m_thetaSetpoint, goalState);

        // the snap overrides the user input for omega.
        double thetaFF = m_thetaSetpoint.v();

        double thetaFB = m_thetaController.calculate(headingMeasurement, m_thetaSetpoint.x());

        double omegaFB = m_omegaController.calculate(headingRate, m_thetaSetpoint.v());
        // switch (Identity.instance) {
        // case BLANK:
        // break;
        // default:
        // if (Math.abs(omegaFB) < 0.2) {
        // omegaFB = 0;
        // }
        // if (Math.abs(thetaFB) < 0.5) {
        // thetaFB = 0;
        // }
        // }
        double omega = MathUtil.clamp(
                thetaFF + thetaFB + omegaFB,
                -m_swerveKinodynamics.getMaxAngleSpeedRad_S(),
                m_swerveKinodynamics.getMaxAngleSpeedRad_S());
        FieldRelativeVelocity twistWithSnapM_S = new FieldRelativeVelocity(twistM_S.x(), twistM_S.y(), omega);

        t.log(Level.TRACE, m_name, "mode", "snap");
        t.log(Level.TRACE, m_name, "goal/theta", m_goal.getRadians());
        t.log(Level.TRACE, m_name, "setpoint/theta", m_thetaSetpoint);
        t.log(Level.TRACE, m_name, "measurement/theta", headingMeasurement);
        t.log(Level.TRACE, m_name, "measurement/omega", headingRate);
        t.log(Level.TRACE, m_name, "error/theta", m_thetaSetpoint.x() - headingMeasurement);
        t.log(Level.TRACE, m_name, "error/omega", m_thetaSetpoint.v() - headingRate);
        t.log(Level.TRACE, m_name, "thetaFF", thetaFF);
        t.log(Level.TRACE, m_name, "thetaFB", thetaFB);
        t.log(Level.TRACE, m_name, "omegaFB", omegaFB);
        t.log(Level.TRACE, m_name, "output/omega", omega);

        // desaturate the end result to feasibility by preferring the rotation over
        // translation
        twistWithSnapM_S = m_swerveKinodynamics.preferRotation(twistWithSnapM_S);
        return twistWithSnapM_S;
    }

    @Override
    public String getGlassName() {
        return "ManualWithProfiledHeading";
    }

}
