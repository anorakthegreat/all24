package org.team100.frc2024.motion;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import org.team100.frc2024.commands.drivetrain.manual.ManualWithShooterLock;
import org.team100.frc2024.motion.drivetrain.ShooterUtil;
import org.team100.frc2024.motion.intake.Intake;
import org.team100.frc2024.motion.shooter.DrumShooter;
import org.team100.lib.commands.Command100;
import org.team100.lib.hid.DriverControl;
import org.team100.lib.motion.drivetrain.SwerveDriveSubsystem;
import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;
import org.team100.lib.logging.SupplierLogger2;
import org.team100.lib.logging.SupplierLogger2.DoubleSupplierLogger2;
import org.team100.lib.telemetry.Telemetry.Level;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;

public class ShootSmartWithRotation extends Command100 {
    private final DrumShooter m_shooter;
    private final SwerveDriveSubsystem m_drive;
    private final ManualWithShooterLock m_driver;
    private final FeederSubsystem m_feeder;
    private final Intake m_intake;
    private final Supplier<DriverControl.Velocity> m_twistSupplier;

    // LOGGERS
    private final DoubleSupplierLogger2 m_log_angle;
    private final DoubleSupplierLogger2 m_log_realangle;

    public ShootSmartWithRotation(
            SupplierLogger2 parent,
            SwerveDriveSubsystem drive,
            DrumShooter shooter,
            FeederSubsystem feeder,
            Intake intake,
            ManualWithShooterLock driver,
            Supplier<DriverControl.Velocity> twistSupplier) {
        super(parent);
        SupplierLogger2 child = parent.child(this);
        m_log_angle = child.doubleLogger(Level.TRACE, "angle");
        m_log_realangle = child.doubleLogger(Level.TRACE, "realangle");
        m_shooter = shooter;
        m_drive = drive;
        m_intake = intake;
        m_driver = driver;
        m_feeder = feeder;
        m_twistSupplier = twistSupplier;
        addRequirements(m_intake, m_feeder, m_shooter, m_drive);
    }

    @Override
    public void initialize100() {
        m_driver.reset(m_drive.getState().pose());
    }

    public void execute100(double dt) {
        FieldRelativeVelocity twist = m_driver.apply(m_drive.getState(), m_twistSupplier.get());
        m_drive.driveInFieldCoords(twist, 0.02);
        Optional<Alliance> alliance = DriverStation.getAlliance();
        if (!alliance.isPresent()) {
            return;
        }
        m_shooter.forward();
        Translation2d robotLocation = m_drive.getState().pose().getTranslation();
        Translation2d speakerLocation = ShooterUtil.getSpeakerTranslation(alliance.get());
        Translation2d difference = robotLocation.minus(speakerLocation);
        double angle = MathUtil.angleModulus(Math.atan2(difference.getY(), difference.getX()) - Math.PI);
        m_log_angle.log(() -> angle);
        double angleModulus = MathUtil.angleModulus(m_drive.getState().pose().getRotation().getRadians());
        m_log_realangle.log(() -> angleModulus);
        double angleError = angle - angleModulus;
        double distance = robotLocation.getDistance(speakerLocation);
        m_shooter.setAngle(ShooterUtil.getAngleRad(distance));
        double rangeM = robotLocation.getDistance(speakerLocation);
        double angleRad = ShooterUtil.getAngleRad(rangeM);
        if (Math.hypot(m_drive.getState().y().v(), m_drive.getState().x().v()) > 0.01) {
            return;
        }
        OptionalDouble shooterPivotPosition = m_shooter.getPivotPosition();
        if (shooterPivotPosition.isPresent()) {
            double errorRad = shooterPivotPosition.getAsDouble() - angleRad;
            if (m_shooter.atVelocitySetpoint() && Math.abs(errorRad) < 0.01 && Math.abs(angleError) < 0.05) {
                m_feeder.feed();
                m_intake.intake();
            }
        }
    }
}
