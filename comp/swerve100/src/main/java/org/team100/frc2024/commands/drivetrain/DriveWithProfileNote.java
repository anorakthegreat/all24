package org.team100.frc2024.commands.drivetrain;

import java.util.Optional;
import java.util.function.Supplier;

import org.team100.frc2024.motion.intake.Intake;
import org.team100.lib.controller.drivetrain.HolonomicFieldRelativeController;
import org.team100.lib.dashboard.Glassy;
import org.team100.lib.experiments.Experiment;
import org.team100.lib.experiments.Experiments;
import org.team100.lib.framework.TimedRobot100;
import org.team100.lib.logging.FieldLogger;
import org.team100.lib.logging.Level;
import org.team100.lib.logging.LoggerFactory;
import org.team100.lib.logging.LoggerFactory.BooleanLogger;
import org.team100.lib.motion.drivetrain.SwerveDriveSubsystem;
import org.team100.lib.motion.drivetrain.SwerveState;
import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;
import org.team100.lib.motion.drivetrain.kinodynamics.SwerveKinodynamics;
import org.team100.lib.profile.TrapezoidProfile100;
import org.team100.lib.state.State100;
import org.team100.lib.util.Math100;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj2.command.Command;

/**
 * Creates a profile to the translation of a note and follows it.
 * 
 * If the goal supplier runs empty, this remembers the previous goal for 1 sec,
 * and then gives up.
 * 
 * TODO: coordinate the axes; currently it's three independent profiles.
 * 
 * TODO: force the theta axis to finish first, so that the approach is correct.
 */
public class DriveWithProfileNote extends Command implements Glassy  {
    private final FieldLogger.Log m_field_log;
    private final Intake m_intake;
    private final Supplier<Optional<Translation2d>> m_fieldRelativeGoal;
    private final SwerveDriveSubsystem m_swerve;
    private final HolonomicFieldRelativeController m_controller;
    private final SwerveKinodynamics m_limits;
    private final TrapezoidProfile100 xProfile;
    private final TrapezoidProfile100 yProfile;
    private final TrapezoidProfile100 thetaProfile;

    // LOGGERS
    private final BooleanLogger m_log_note_detected;

    private Translation2d m_previousGoal;
    private State100 m_xSetpoint;
    private State100 m_ySetpoint;
    private State100 m_thetaSetpoint;
    private int m_count;

    public DriveWithProfileNote(
            FieldLogger.Log fieldLogger,
            LoggerFactory parent,
            Intake intake,
            Supplier<Optional<Translation2d>> fieldRelativeGoal,
            SwerveDriveSubsystem drivetrain,
            HolonomicFieldRelativeController controller,
            SwerveKinodynamics limits) {
        m_field_log = fieldLogger;
        LoggerFactory child = parent.child(this);
        m_log_note_detected = child.booleanLogger(Level.TRACE, "Note detected");

        m_intake = intake;
        m_fieldRelativeGoal = fieldRelativeGoal;
        m_swerve = drivetrain;
        m_controller = controller;
        m_limits = limits;

        xProfile = new TrapezoidProfile100(
                m_limits.getMaxDriveVelocityM_S(),
                m_limits.getMaxDriveAccelerationM_S2() / 2,
                0.01);
        yProfile = new TrapezoidProfile100(
                m_limits.getMaxDriveVelocityM_S(),
                m_limits.getMaxDriveAccelerationM_S2() / 2,
                0.01);
        thetaProfile = new TrapezoidProfile100(
                m_limits.getMaxAngleSpeedRad_S(),
                m_limits.getMaxAngleAccelRad_S2() / 4,
                0.01);

        m_previousGoal = null;
        m_count = 0;

        addRequirements(m_swerve, m_intake);
    }

    @Override
    public void initialize() {
        m_xSetpoint = m_swerve.getState().x();
        m_ySetpoint = m_swerve.getState().y();
        m_thetaSetpoint = m_swerve.getState().theta();
    }

    /**
     * Returns the current goal, or the previous one if the current one is newly
     * empty.
     */
    private Optional<Translation2d> getGoal() {
        Optional<Translation2d> optGoal = m_fieldRelativeGoal.get();
        m_log_note_detected.log(optGoal::isPresent);
        if (optGoal.isPresent()) {
            // Supplier is ok, use this goal and reset the history mechanism.
            m_previousGoal = optGoal.get();
            m_count = 0;
            return optGoal;
        }
        if (m_count > 50) {
            // Supplier is empty and timer has expired.
            return Optional.empty();
        }
        if (m_previousGoal == null) {
            // Nothing to fall back to.
            return Optional.empty();
        }
        m_count++;
        return Optional.of(m_previousGoal);
    }

    @Override
    public void execute() {
        // intake the whole time
        m_intake.intakeSmart();

        Optional<Translation2d> optGoal = getGoal();
        if (optGoal.isEmpty()) {
            // No current goal, timer expired, or no past goal.
            return;
        }

        Translation2d goal = optGoal.get();

        State100 thetaGoal = getThetaGoalState(m_swerve.getState().pose(), goal);
        State100 xGoal = new State100(goal.getX(), 0, 0);
        State100 yGoal = new State100(goal.getY(), 0, 0);

        m_xSetpoint = xProfile.calculate(TimedRobot100.LOOP_PERIOD_S, m_xSetpoint, xGoal);
        m_ySetpoint = yProfile.calculate(TimedRobot100.LOOP_PERIOD_S, m_ySetpoint, yGoal);
        // make sure the setpoint uses the modulus close to the measurement.
        final double thetaMeasurement = m_swerve.getState().pose().getRotation().getRadians();
        m_thetaSetpoint = new State100(
                Math100.getMinDistance(thetaMeasurement, m_thetaSetpoint.x()),
                m_thetaSetpoint.v());
        m_thetaSetpoint = thetaProfile.calculate(TimedRobot100.LOOP_PERIOD_S, m_thetaSetpoint, thetaGoal);

        SwerveState measurement = m_swerve.getState();
        SwerveState setpoint = new SwerveState(m_xSetpoint, m_ySetpoint, m_thetaSetpoint);
        FieldRelativeVelocity output = m_controller.calculate(measurement, setpoint);

        m_swerve.driveInFieldCoords(output);

        m_field_log.m_log_target.log(() -> new double[] { goal.getX(), goal.getY(), 0 });
    }

    private static State100 getThetaGoalState(Pose2d pose, Translation2d goal) {
        // take the short path
        final double measurementRad = pose.getRotation().getRadians();
        final double goalRad = getThetaGoalRad(goal, pose);
        return new State100(Math100.getMinDistance(measurementRad, goalRad), 0);
    }

    private static double getThetaGoalRad(Translation2d goal, Pose2d pose) {
        if (Experiments.instance.enabled(Experiment.DriveToNoteWithRotation)) {
            // face the rear of the robot towards the goal.
            Translation2d toGoal = goal.minus(pose.getTranslation());
            return toGoal.getAngle().getRadians() + Math.PI;
        } else {
            // leave the rotation alone
            return pose.getRotation().getRadians();
        }
    }

    @Override
    public void end(boolean interrupted) {
        //
    }
}
