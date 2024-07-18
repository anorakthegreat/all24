package org.team100.lib.motion.drivetrain.kinodynamics;

import static edu.wpi.first.units.Units.MetersPerSecond;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.proto.SwerveModuleStateProto;
import edu.wpi.first.math.kinematics.struct.SwerveModuleStateStruct;
import edu.wpi.first.units.Distance;
import edu.wpi.first.units.Measure;
import edu.wpi.first.units.Velocity;
import edu.wpi.first.util.protobuf.ProtobufSerializable;
import edu.wpi.first.util.struct.StructSerializable;

import java.util.Objects;
import java.util.Optional;

import org.team100.lib.util.Util;

/**
 * This is a copy of {@link edu.wpi.first.math.kinematics.SwerveModuleState} but
 * with second-order fields.
 * 
 * Represents the state of one swerve module.
 * 
 * TODO: this might be safer/simpler as an x and y velocity instead of a speed
 * and an angle.
 */
public class SwerveModuleState100
        implements Comparable<SwerveModuleState100>, ProtobufSerializable, StructSerializable {
    /** Speed of the wheel of the module. */
    public double speedMetersPerSecond;

    /**
     * Angle of the module. It can be empty, in cases where the angle is
     * indeterminate (e.g. calculating the angle required for zero speed).
     * TODO: make this private
     */
    public Optional<Rotation2d> angle = Optional.empty();

    /** Acceleration of the wheel of the module. */
    public double accelMetersPerSecond_2 = 0;

    /** Anglular velocity of the module, radians per second */
    public double omega = 0;

    /** SwerveModuleState protobuf for serialization. */
    public static final SwerveModuleStateProto proto = new SwerveModuleStateProto();

    /** SwerveModuleState struct for serialization. */
    public static final SwerveModuleStateStruct struct = new SwerveModuleStateStruct();

    /**
     * Constructs a SwerveModuleState with zeros for speed and *indeterminate*
     * angle.
     */
    public SwerveModuleState100() {
    }

    /**
     * Constructs a SwerveModuleState.
     *
     * @param speedMetersPerSecond The speed of the wheel of the module.
     * @param angle                The angle of the module.
     */
    public SwerveModuleState100(double speedMetersPerSecond, Optional<Rotation2d> angle) {
        this.speedMetersPerSecond = speedMetersPerSecond;
        this.angle = angle;
    }

    /**
     * Constructs a SwerveModuleState.
     *
     * @param accelMetersPerSecond   The speed of the wheel of the module.
     * @param angle                  The angle of the module.
     * @param accelMetersPerSecond_2 The acceleration of the wheel of the module.
     * @param angle_2                The angular velocity of the module.
     */
    public SwerveModuleState100(double accelMetersPerSecond, Optional<Rotation2d> angle, double accelMetersPerSecond_2,
            double angle_2) {
        this.speedMetersPerSecond = accelMetersPerSecond;
        this.angle = angle;
        this.accelMetersPerSecond_2 = accelMetersPerSecond_2;
        this.omega = angle_2;
    }

    /**
     * Constructs a SwerveModuleState.
     *
     * @param speed The speed of the wheel of the module.
     * @param angle The angle of the module.
     */
    public SwerveModuleState100(Measure<Velocity<Distance>> speed, Optional<Rotation2d> angle) {
        this(speed.in(MetersPerSecond), angle);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SwerveModuleState100) {
            SwerveModuleState100 other = (SwerveModuleState100) obj;
            return Math.abs(other.speedMetersPerSecond - speedMetersPerSecond) < 1E-9
                    && angle.equals(other.angle);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(speedMetersPerSecond, angle, accelMetersPerSecond_2, omega);
    }

    /**
     * Compares two swerve module states. One swerve module is "greater" than the
     * other if its speed
     * is higher than the other.
     *
     * @param other The other swerve module.
     * @return 1 if this is greater, 0 if both are equal, -1 if other is greater.
     */
    @Override
    public int compareTo(SwerveModuleState100 other) {
        return Double.compare(this.speedMetersPerSecond, other.speedMetersPerSecond);
    }

    @Override
    public String toString() {
        return String.format(
                "SwerveModuleState(Speed: %.2f m/s, Angle: %s, Acceleration: %.2f m/s, Anglular Velocity: %s)",
                speedMetersPerSecond, angle, accelMetersPerSecond_2, omega);
    }

    /**
     * Minimize the change in heading the desired swerve module state would require
     * by potentially
     * reversing the direction the wheel spins. If this is used with the
     * PIDController class's
     * continuous input functionality, the furthest a wheel will ever rotate is 90
     * degrees.
     *
     * @param desiredState The desired state.
     * @param currentAngle The current module angle.
     * @return Optimized swerve module state.
     */
    public static SwerveModuleState100 optimize(
            SwerveModuleState100 desiredState, Rotation2d currentAngle) {
        // this does happen
        if (desiredState.angle.isEmpty()) {
            Util.warn("SwerveModuleState100.optimize: empty angle!");
            return desiredState;
        }
        var delta = desiredState.angle.get().minus(currentAngle);
        if (Math.abs(delta.getDegrees()) > 90.0) {
            return new SwerveModuleState100(
                    -desiredState.speedMetersPerSecond,
                    Optional.of(desiredState.angle.get().rotateBy(Rotation2d.fromDegrees(180.0))),
                    desiredState.accelMetersPerSecond_2,
                    desiredState.omega * -1);
        } else {
            return new SwerveModuleState100(desiredState.speedMetersPerSecond, desiredState.angle,
                    desiredState.accelMetersPerSecond_2, desiredState.omega);
        }
    }
}
