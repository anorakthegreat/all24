package org.team100.commands;

import java.util.function.Supplier;

import org.team100.Debug;
import org.team100.kinodynamics.Kinodynamics;
import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;
import org.team100.planner.Drive;
import org.team100.sim.ForceViz;
import org.team100.subsystems.CameraSubsystem;
import org.team100.subsystems.DriveSubsystem;
import org.team100.util.Arg;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj2.command.Command;

/**
 * This command should be followed by DriveToNote, so that the goal of this
 * command can be very approximate.
 */
public class DriveToSource extends Command {
    private final DriveSubsystem m_drive;
    private final Supplier<Pose2d> m_goal;
    private final Supplier<Double> m_yBias;
    private final boolean m_debug;
    private final Tactics m_tactics;
    private final ForceViz m_viz;

    public DriveToSource(
            DriveSubsystem drive,
            CameraSubsystem camera,
            Supplier<Pose2d> goal,
            Supplier<Double> yBias,
            Tactics tactics,
            ForceViz viz,
            boolean debug) {
        Arg.nonnull(drive);
        Arg.nonnull(camera);
        m_drive = drive;
        m_goal = goal;
        m_yBias = yBias;
        m_debug = debug && Debug.enable();
        m_tactics = tactics;
        m_viz = viz;
        addRequirements(drive);
    }

    @Override
    public void execute() {
        if (m_debug)
            System.out.print("DriveToSource");
        Pose2d pose = m_drive.getPose();
        if (m_debug)
            System.out.printf(" pose (%5.2f,%5.2f)", pose.getX(), pose.getY());
        FieldRelativeVelocity desired = Drive.goToGoal(pose, m_goal.get(), m_debug);
        // provide "lanes"
        desired = desired.plus(new FieldRelativeVelocity(0, m_yBias.get(), 0));
        if (m_debug)
            m_viz.desired(pose, desired);
        if (m_debug)
            System.out.printf(" desired %s", desired);
        FieldRelativeVelocity v = m_tactics.apply(desired);
        if (m_debug)
            System.out.printf(" tactics %s", v);
        v = v.plus(desired);
        v = v.clamp(Kinodynamics.kMaxVelocity, Kinodynamics.kMaxOmega);
        if (m_debug)
            System.out.printf(" final %s\n", v);
        m_drive.drive(v);
    }
}
