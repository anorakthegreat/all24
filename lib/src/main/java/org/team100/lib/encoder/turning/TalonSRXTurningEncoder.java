package org.team100.lib.encoder.turning;

import java.util.OptionalDouble;

import org.team100.lib.encoder.Encoder100;
import org.team100.lib.motor.turning.CANTurningMotor;
import org.team100.lib.telemetry.Telemetry;
import org.team100.lib.telemetry.Telemetry.Level;
import org.team100.lib.units.Angle100;
import org.team100.lib.util.Names;

import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.math.MathUtil;

/**
 * This was an attempt to use the TalonSRX data interface for absolute encoder,
 * but it didn't work out. Maybe we should delete it.
 */
public class TalonSRXTurningEncoder implements Encoder100<Angle100> {
    private static final int ticksPerRevolution = 1666;

    private final Telemetry t = Telemetry.get();
    private final String m_name;
    private final WPI_TalonSRX m_motor;

    public TalonSRXTurningEncoder(String name, CANTurningMotor motor) {
        m_name = Names.append(name, this);
        m_motor = motor.getMotor();
    }

    @Override
    public OptionalDouble getPosition() {
        return OptionalDouble.of(getPositionRad());
    }

    @Override
    public OptionalDouble getRate() {
        return OptionalDouble.of(getRateRad_S());
    }

    @Override
    public void reset() {
        m_motor.setSelectedSensorPosition(0);
    }

    @Override
    public void close() {
        //
    }

    //////////////////////////////////////////////

    private double getPositionRad() {
        // should be fast, no need to cache
        double rawPosition = m_motor.getSelectedSensorPosition();
        double positionRad = MathUtil.angleModulus(rawPosition / ticksPerRevolution * 2 * Math.PI);
        t.log(Level.DEBUG, m_name, "position (rad)", positionRad);
        return positionRad;
    }

    private double getRateRad_S() {
        // rate in ticks per 100ms.
        // should be fast, no need to cache
        double rawRate = m_motor.getSelectedSensorVelocity();
        // times ten because rate is per 100ms
        double rateRad_S = 2.0 * Math.PI * 10.0 * rawRate / ticksPerRevolution;
        t.log(Level.DEBUG, m_name, "velocity (rad_s)", rateRad_S);
        return rateRad_S;
    }

}