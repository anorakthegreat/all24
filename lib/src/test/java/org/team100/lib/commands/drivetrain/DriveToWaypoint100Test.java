package org.team100.lib.commands.drivetrain;

import org.junit.jupiter.api.Test;
import org.team100.lib.controller.DriveFeedforwardController;
import org.team100.lib.controller.DriveMotionController;
import org.team100.lib.controller.DrivePIDController;
import org.team100.lib.controller.DrivePursuitController;
import org.team100.lib.controller.DriveRamseteController;
import org.team100.lib.geometry.GeometryUtil;
import org.team100.lib.motion.drivetrain.MockSwerveDriveSubsystem;
import org.team100.lib.swerve.SwerveKinematicLimits;
import org.team100.lib.trajectory.TrajectoryPlanner;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;

class DriveToWaypoint100Test {
    private static final SwerveDriveKinematics kinematics = new SwerveDriveKinematics(
            new Translation2d(0.1, 0.1),
            new Translation2d(0.1, -0.1),
            new Translation2d(-0.1, 0.1),
            new Translation2d(-0.1, -0.1));

    @Test
    void testWithPID() {
        Pose2d goal = GeometryUtil.kPoseZero;
        MockSwerveDriveSubsystem drivetrain = new MockSwerveDriveSubsystem();
        SwerveKinematicLimits limits = new SwerveKinematicLimits(4, 2, 10);
        TrajectoryPlanner planner = new TrajectoryPlanner(kinematics, limits);
        DriveMotionController controller = new DrivePIDController();
        DriveToWaypoint100 command = new DriveToWaypoint100(goal, drivetrain, planner, controller);

        // TODO: add some assertions
        command.initialize();
        command.execute();
        command.end(false);
    }

    @Test
    void testWithPursuit() {
        Pose2d goal = GeometryUtil.kPoseZero;
        MockSwerveDriveSubsystem drivetrain = new MockSwerveDriveSubsystem();
        SwerveKinematicLimits limits = new SwerveKinematicLimits(4, 2, 10);
        TrajectoryPlanner planner = new TrajectoryPlanner(kinematics, limits);
        DriveMotionController controller = new DrivePursuitController();
        DriveToWaypoint100 command = new DriveToWaypoint100(goal, drivetrain, planner, controller);

        // TODO: add some assertions
        command.initialize();
        command.execute();
        command.end(false);
    }

    @Test
    void testWithRamsete() {
        Pose2d goal = GeometryUtil.kPoseZero;
        MockSwerveDriveSubsystem drivetrain = new MockSwerveDriveSubsystem();
        SwerveKinematicLimits limits = new SwerveKinematicLimits(4, 2, 10);
        TrajectoryPlanner planner = new TrajectoryPlanner(kinematics, limits);
        DriveMotionController controller = new DriveRamseteController();
        DriveToWaypoint100 command = new DriveToWaypoint100(goal, drivetrain, planner, controller);

        // TODO: add some assertions
        command.initialize();
        command.execute();
        command.end(false);
    }

    @Test
    void testWithFF() {
        Pose2d goal = GeometryUtil.kPoseZero;
        MockSwerveDriveSubsystem drivetrain = new MockSwerveDriveSubsystem();
        SwerveKinematicLimits limits = new SwerveKinematicLimits(4, 2, 10);
        TrajectoryPlanner planner = new TrajectoryPlanner(kinematics, limits);
        DriveMotionController controller = new DriveFeedforwardController();
        DriveToWaypoint100 command = new DriveToWaypoint100(goal, drivetrain, planner, controller);

        // TODO: add some assertions
        command.initialize();
        command.execute();
        command.end(false);
    }
}
