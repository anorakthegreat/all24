package org.team100.lib.motion.components;

import java.util.OptionalDouble;

import org.team100.lib.dashboard.Glassy;

/**
 * Linear position control, e.g. for elevators.
 */
public interface LinearPositionServo extends Glassy {
    /**
     * It is essential to call this after a period of disuse, to prevent transients.
     * 
     * To prevent oscillation, the previous setpoint is used to compute the profile,
     * but there needs to be an initial setpoint.
     */
    void reset();

    /**
     * This is movement and force on the output.
     * 
     * @param goalM
     */
     /**
     * The angle measure here *does not* wind up, so 0 and 2pi are the same.
     * 
     * The measurements here are output measurements, e.g. shaft radians, not motor
     * radians.
     * 
     * @param goalRad           radians
     * @param feedForwardTorque used for gravity compensation
     */
    void setPosition(double goalRad, double feedForwardTorqueNm);

    /**
     * The angle measure here *does not* wind up, so 0 and 2pi are the same.
     * 
     * The measurements here are output measurements, e.g. shaft radians, not motor
     * radians.
     * 
     * @param goalRad           radians
     * @param goalVelocityRad_S rad/s
     * @param feedForwardTorque used for gravity compensation
     */
    void setPositionWithVelocity(double goalRad, double goalVelocityRad_S, double feedForwardTorqueNm);


    OptionalDouble getPosition();

    OptionalDouble getVelocity();

    void stop();

    void close();

    @Override
    default String getGlassName() {
        return "LinearPositionServo";
    }
}
