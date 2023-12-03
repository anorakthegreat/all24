package frc.robot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.team100.lib.commands.DriveUtil;
import org.team100.lib.controller.HolonomicDriveController2;
import org.team100.lib.controller.State100;
import org.team100.lib.motion.drivetrain.SwerveState;
import org.team100.lib.motion.drivetrain.kinematics.FrameTransform;

import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.networktables.DoubleArrayPublisher;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.wpilibj.AnalogGyro;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.AnalogGyroSim;
import edu.wpi.first.wpilibj.simulation.CallbackStore;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import util.PinkNoise;

/** Represents a swerve drive style drivetrain. */
public class Drivetrain extends SubsystemBase {
    /** 3 m/s */
    public static final double kMaxSpeed = 6.0;
    /** pi rad/s */
    public static final double kMaxAngularSpeed = 6 * Math.PI;

    private final Translation2d m_frontLeftLocation = new Translation2d(0.381, 0.381);
    private final Translation2d m_frontRightLocation = new Translation2d(0.381, -0.381);
    private final Translation2d m_backLeftLocation = new Translation2d(-0.381, 0.381);
    private final Translation2d m_backRightLocation = new Translation2d(-0.381, -0.381);

    // package visibility for testing
    final SwerveModule m_frontLeft;
    final SwerveModule m_frontRight;
    final SwerveModule m_backLeft;
    final SwerveModule m_backRight;

    final AnalogGyro m_gyro;
    // note gyro is NED, robot is NWU, see inversion below.
    final AnalogGyroSim gyroSim;

    final SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(
            m_frontLeftLocation, m_frontRightLocation, m_backLeftLocation, m_backRightLocation);

    // Pose2d robotPose = new Pose2d();
    private double m_prevTimeSeconds = Timer.getFPGATimestamp();
    private static final double m_nominalDtS = 0.02; // Seconds

    // from main2023
    public final ProfiledPIDController headingController;

    /*
     * Here we use SwerveDrivePoseEstimator so that we can fuse odometry readings.
     * The numbers used below are robot specific, and should be tuned.
     */
    private final SwerveDrivePoseEstimator m_poseEstimator;

    NetworkTableInstance inst = NetworkTableInstance.getDefault();
    NetworkTable desired = inst.getTable("desired");
    DoublePublisher xSpeedPubM_s = desired.getDoubleTopic("xspeed m_s").publish();
    DoublePublisher ySpeedPubM_s = desired.getDoubleTopic("yspeed m_s").publish();
    DoublePublisher thetaSpeedPubRad_s = desired.getDoubleTopic("thetaspeed rad_s").publish();
    NetworkTable actual = inst.getTable("actual");
    DoublePublisher actualXSpeedPubM_s = actual.getDoubleTopic("xspeed m_s").publish();
    DoublePublisher actualYSpeedPubM_s = actual.getDoubleTopic("yspeed m_s").publish();
    DoublePublisher actualThetaSpeedPubRad_s = actual.getDoubleTopic("thetaspeed rad_s").publish();

    DoubleArrayPublisher robotPosePub;
    // DoubleArrayPublisher waypointPub;
    StringPublisher fieldTypePub;

    List<CallbackStore> cbs = new ArrayList<>();

    ChassisSpeeds speeds;

    private SwerveState m_desiredState;

    private final HolonomicDriveController2 m_controller;
    FrameTransform m_frameTransform;

    public Drivetrain(AnalogGyro gyro,
            Supplier<PinkNoise> noiseSource,
            HolonomicDriveController2 controller,
            FrameTransform frameTransform) {
        m_gyro = gyro;
        gyroSim = new AnalogGyroSim(m_gyro);
        m_frontLeft = new SwerveModule("FrontLeft", 1, 2, 0, 1, 2, 3, noiseSource.get());
        m_frontRight = new SwerveModule("FrontRight", 3, 4, 4, 5, 6, 7, noiseSource.get());
        m_backLeft = new SwerveModule("BackLeft", 5, 6, 8, 9, 10, 11, noiseSource.get());
        m_backRight = new SwerveModule("BackRight", 7, 8, 12, 13, 14, 15, noiseSource.get());
        m_poseEstimator = new SwerveDrivePoseEstimator(
                m_kinematics,
                m_gyro.getRotation2d(), // NWU
                new SwerveModulePosition[] {
                        m_frontLeft.getPosition(),
                        m_frontRight.getPosition(),
                        m_backLeft.getPosition(),
                        m_backRight.getPosition()
                },
                new Pose2d());
        m_controller = controller;
        m_frameTransform = frameTransform;
        m_gyro.reset();
        headingController = new ProfiledPIDController( //
                0.67, // kP //0.75
                0, // kI
                0, // kD //0.1
                new TrapezoidProfile.Constraints(
                        2 * Math.PI, // speed rad/s
                        4 * Math.PI)); // accel rad/s/s
        headingController.setTolerance(0.01);

        // inst.startClient4("blarg");
        NetworkTable fieldTable = inst.getTable("field");
        robotPosePub = fieldTable.getDoubleArrayTopic("robotPose").publish();
        // waypointPub = fieldTable.getDoubleArrayTopic("waypoint").publish();
        fieldTypePub = fieldTable.getStringTopic(".type").publish();
        fieldTypePub.set("Field2d");
        setName("Drivetrain");
        truncate();
    }

    public Pose2d getPose() {
        updateOdometry();
        return m_poseEstimator.getEstimatedPosition();
    }

    public void resetOdometry(Pose2d pose) {
        m_poseEstimator.resetPosition(m_gyro.getRotation2d(), // NWU
                new SwerveModulePosition[] {
                        m_frontLeft.getPosition(),
                        m_frontRight.getPosition(),
                        m_backLeft.getPosition(),
                        m_backRight.getPosition()
                }, pose);
    }

    /**
     * Method to drive the robot using joystick info.
     *
     * @param xSpeedM_s     Speed of the robot in the x direction (forward).
     * @param ySpeedM_s     Speed of the robot in the y direction (sideways).
     * @param rotRad_s      Angular rate of the robot.
     * @param fieldRelative Whether the provided x and y speeds are relative to the
     *                      field.
     */
    // public void drive(double xSpeedM_s, double ySpeedM_s, double rotRad_s, boolean fieldRelative) {
    //     xSpeedPubM_s.set(xSpeedM_s);
    //     ySpeedPubM_s.set(ySpeedM_s);
    //     thetaSpeedPubRad_s.set(rotRad_s);

    //     ChassisSpeeds chassisSpeeds = fieldRelative ? ChassisSpeeds.fromFieldRelativeSpeeds(
    //             xSpeedM_s, ySpeedM_s, rotRad_s,
    //             m_gyro.getRotation2d() // NWU
    //     ) : new ChassisSpeeds(xSpeedM_s, ySpeedM_s, rotRad_s);


    //     setChassisSpeeds(chassisSpeeds);
    // }


    public void setChassisSpeeds(ChassisSpeeds chassisSpeeds) {
        setModuleStates(m_kinematics.toSwerveModuleStates(chassisSpeeds));
    }

    // adapted from main2023 on Jul 9 2023
    public void resetPose(Pose2d robotPose) {
        m_poseEstimator.resetPosition(m_gyro.getRotation2d(), new SwerveModulePosition[] {
                m_frontLeft.getPosition(),
                m_frontRight.getPosition(),
                m_backLeft.getPosition(),
                m_backRight.getPosition()
        },
                robotPose);
    }

    public void driveWithHeading(double xSpeedMetersPerSec, double ySpeedMetersPerSec, double rotRadiansPerSec,
            boolean fieldRelative) {
        double gyroRate = m_gyro.getRate() * 0.15;
        double rotConstant = 0;
        // System.out.println(gyroRate);
        Rotation2d rotation2 = getPose().getRotation().minus(new Rotation2d(gyroRate));
        xSpeedMetersPerSec = xSpeedMetersPerSec * (Math.abs(Math.abs(rotConstant * rotRadiansPerSec / kMaxSpeed) - 1));
        ySpeedMetersPerSec = ySpeedMetersPerSec * (Math.abs(Math.abs(rotConstant * rotRadiansPerSec / kMaxSpeed) - 1));
        ChassisSpeeds desiredChassisSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(xSpeedMetersPerSec,
                ySpeedMetersPerSec, rotRadiansPerSec, rotation2);
        ChassisSpeeds startChassisSpeeds = new ChassisSpeeds(xSpeedMetersPerSec, ySpeedMetersPerSec, rotRadiansPerSec);
        var swerveModuleStates = m_kinematics
                .toSwerveModuleStates(fieldRelative ? desiredChassisSpeeds : startChassisSpeeds);
        SwerveDriveKinematics.desaturateWheelSpeeds(
                swerveModuleStates, kMaxSpeed);

        // main2023 has a separate feedforward for heading mode
        m_frontLeft.setDesiredState(swerveModuleStates[0]);
        m_frontRight.setDesiredState(swerveModuleStates[1]);
        m_backLeft.setDesiredState(swerveModuleStates[2]);
        m_backRight.setDesiredState(swerveModuleStates[3]);
    }

    public void setModuleStates(SwerveModuleState[] swerveModuleStates) {
        m_frontLeft.publishState(swerveModuleStates[0]);
        m_frontRight.publishState(swerveModuleStates[1]);
        m_backLeft.publishState(swerveModuleStates[2]);
        m_backRight.publishState(swerveModuleStates[3]);

        SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, kMaxSpeed); // 3m/s max

        m_frontLeft.setDesiredState(swerveModuleStates[0]);
        m_frontRight.setDesiredState(swerveModuleStates[1]);
        m_backLeft.setDesiredState(swerveModuleStates[2]);
        m_backRight.setDesiredState(swerveModuleStates[3]);
    }

    /**
     * drive one module directly
     * 
     * @param drive desired speed m/s
     * @param turn  desired rotation rad
     */
    public void test(double drive, double turn) {
        m_frontLeft.setDesiredState(new SwerveModuleState(drive, new Rotation2d(turn) // NWU
        ));
    }

    /** Updates the field relative position of the robot. */
    public void updateOdometry() {
        m_poseEstimator.update(
                m_gyro.getRotation2d(), // NWU
                new SwerveModulePosition[] {
                        m_frontLeft.getPosition(),
                        m_frontRight.getPosition(),
                        m_backLeft.getPosition(),
                        m_backRight.getPosition()
                });

        Pose2d newEstimate = m_poseEstimator.getEstimatedPosition();
        robotPosePub.set(new double[] {
                newEstimate.getX(),
                newEstimate.getY(),
                newEstimate.getRotation().getDegrees()
        });
        // for testing
        // waypointPub.set(new double[] {
        // newEstimate.getX() + 1,
        // newEstimate.getY(),
        // newEstimate.getRotation().getDegrees()
        // });

        // Also apply vision measurements. We use 0.3 seconds in the past as an example
        // -- on a real robot, this must be calculated based either on latency or
        // timestamps.

        // m_poseEstimator.addVisionMeasurement(
        // ExampleGlobalMeasurementSensor.getEstimatedGlobalPose(
        // m_poseEstimator.getEstimatedPosition()),
        // Timer.getFPGATimestamp() - 0.3);

    }

    public void simulationInit() {
        m_frontLeft.simulationInit();
        m_frontRight.simulationInit();
        m_backLeft.simulationInit();
        m_backRight.simulationInit();
    }

    /** Drive to the desired reference. */
    @Override
    public void periodic() {
        updateOdometry();
        driveToReference();
        // m_field.setRobotPose(getPose());
    }

    private void driveToReference() {
        // TODO: pose should be a full state, with velocity and acceleration.
        Pose2d currentPose = getPose();

        Twist2d fieldRelativeTarget = m_controller.calculate(currentPose, m_desiredState);
        driveInFieldCoords(fieldRelativeTarget);
    }

    private void driveInFieldCoords(Twist2d twist) {
        ChassisSpeeds targetChassisSpeeds = m_frameTransform.fromFieldRelativeSpeeds(
                twist.dx, twist.dy, twist.dtheta, getPose().getRotation());
        // m_swerveLocal.setChassisSpeeds(targetChassisSpeeds);
        setChassisSpeeds(targetChassisSpeeds);
    }

    @Override
    public void simulationPeriodic() {
        double currentTimeSeconds = Timer.getFPGATimestamp();
        double dtS = m_prevTimeSeconds >= 0 ? currentTimeSeconds - m_prevTimeSeconds : m_nominalDtS;
        m_prevTimeSeconds = currentTimeSeconds;
        simulationPeriodic(dtS);
    }

    public void simulationPeriodic(final double dtS) {
        m_frontLeft.simulationPeriodic(dtS);
        m_frontRight.simulationPeriodic(dtS);
        m_backLeft.simulationPeriodic(dtS);
        m_backRight.simulationPeriodic(dtS);

        // in simulation these should be the values we just set
        // in SwerveModule.simulationPeriodic(), so we don't need
        // to adjust them *again*, just use them to update the gyro.
        SwerveModuleState[] states = new SwerveModuleState[] {
                m_frontLeft.getState(),
                m_frontRight.getState(),
                m_backLeft.getState(),
                m_backRight.getState()
        };

        // rotational velocity is correct here.
        speeds = m_kinematics.toChassisSpeeds(states);

        // finally adjust the simulator gyro.
        // the pose estimator figures out the X/Y part but it depends on the gyro.
        // since omega is the same in both coordinate schemes, just use that.
        double oldAngleDeg = gyroSim.getAngle();
        double dThetaDeg = -1.0 * new Rotation2d(speeds.omegaRadiansPerSecond * dtS).getDegrees();
        double newAngleDeg = oldAngleDeg + dThetaDeg;
        // note that the "angle" in a gyro is NED, but everything else (e.g robot pose)
        // is NWU, so invert here.
        gyroSim.setAngle(newAngleDeg);

        xSpeedPubM_s.set(speeds.vxMetersPerSecond);
        ySpeedPubM_s.set(speeds.vyMetersPerSecond);
        thetaSpeedPubRad_s.set(-1.0 * speeds.omegaRadiansPerSecond);
    }

    public void close() {
        m_frontLeft.close();
        m_frontRight.close();
        m_backLeft.close();
        m_backRight.close();
        m_gyro.close();
    }

    /**
     * Helper for incremental driving.
     * 
     * Note the returned state has zero acceleration, which is wrong.
     * 
     * TODO: correct acceleration.
     * 
     * @param twistM_S incremental input in m/s and rad/s
     * @return SwerveState representing 0.02 sec of twist applied to the current
     *         pose.
     */
    public static SwerveState incremental(Pose2d currentPose, Twist2d twistM_S) {
        Twist2d twistM = DriveUtil.scale(twistM_S, 0.02, 0.02);
        Pose2d ref = currentPose.exp(twistM);
        return new SwerveState(
                new State100(ref.getX(), twistM_S.dx, 0),
                new State100(ref.getY(), twistM_S.dy, 0),
                new State100(ref.getRotation().getRadians(), twistM_S.dtheta, 0));

    }

    public void setDesiredState(SwerveState desiredState) {
        m_desiredState = desiredState;
    }

    public void truncate() {
        stop();
        Pose2d currentPose = getPose();
        m_desiredState = new SwerveState(
                new State100(currentPose.getX(), 0, 0),
                new State100(currentPose.getY(), 0, 0),
                new State100(currentPose.getRotation().getRadians(), 0, 0));
    }

    public void stop() {
        // m_swerveLocal.stop();
    }
}
