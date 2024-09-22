package org.team100.frc2024.motion.amp;

import org.team100.frc2024.motion.FeedToAmp;
import org.team100.frc2024.motion.FeederSubsystem;
import org.team100.frc2024.motion.intake.Intake;
import org.team100.frc2024.motion.shooter.DrumShooter;
import org.team100.lib.commands.drivetrain.DriveWithProfile2;
import org.team100.lib.controller.HolonomicDriveController100;
import org.team100.lib.geometry.GeometryUtil;
import org.team100.lib.motion.drivetrain.SwerveDriveSubsystem;
import org.team100.lib.motion.drivetrain.kinodynamics.SwerveKinodynamics;
import org.team100.lib.logging.SupplierLogger2;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.ParallelCommandGroup;
import edu.wpi.first.wpilibj2.command.ParallelDeadlineGroup;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;

public class DriveToAmp extends SequentialCommandGroup {
    private static final double kAmpSwitchingPt = 1.6;
    private static final double kAmpUp = 1.8;
    private static final double kCloseToAmpYM = 6.799454;
    private static final double kNearAmpYM = 6.474794;
    private static final double kAmpXM = 1.834296;
    private static final double kFieldWidthM = 8.221;

    private static final Pose2d kBlueNearAmp = new Pose2d(
            kAmpXM, kNearAmpYM, GeometryUtil.kRotation90);
    private static final Pose2d kRedNearAmp = new Pose2d(
            kAmpXM, kFieldWidthM - kNearAmpYM, GeometryUtil.kRotation90);
    private static final Pose2d kBlueCloseToAmp = new Pose2d(
            kAmpXM, kCloseToAmpYM, GeometryUtil.kRotation90);
    private static final Pose2d kRedCloseToAmp = new Pose2d(
            kAmpXM, kFieldWidthM - kCloseToAmpYM, GeometryUtil.kRotation90);

    public DriveToAmp(
            SupplierLogger2 parent,
            SwerveDriveSubsystem drive,
            SwerveKinodynamics limits,
            AmpPivot amp,
            AmpFeeder ampFeeder,
            Intake intake,
            DrumShooter shooter,
            FeederSubsystem feeder) {

        addCommands(
                new ParallelDeadlineGroup(
                        new DriveWithProfile2(parent,
                                () -> DriverStation.getAlliance().map(
                                        x -> switch (x) {
                                            case Red -> kRedNearAmp;
                                            case Blue -> kBlueNearAmp;
                                        }),
                                drive,
                                new HolonomicDriveController100(parent), limits),
                        new FeedToAmp(intake, shooter, ampFeeder, feeder)),
                new ParallelCommandGroup(
                        // new AmpSet(parent, amp, kAmpUp),
                        new AmpFastThenSlow(parent, amp, kAmpSwitchingPt, kAmpUp),
                        new SequentialCommandGroup(
                                new DriveWithProfile2(parent,
                                        () -> DriverStation.getAlliance().map(
                                                x -> switch (x) {
                                                    case Red -> kRedCloseToAmp;
                                                    case Blue -> kBlueCloseToAmp;
                                                }),
                                        drive,
                                        new HolonomicDriveController100(parent), limits),
                                new ParallelCommandGroup(
                                        ampFeeder.run(ampFeeder::outtake),
                                        new WaitCommand(1)))));

    }
}
