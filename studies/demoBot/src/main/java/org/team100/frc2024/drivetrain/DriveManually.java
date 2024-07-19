package org.team100.frc2024.drivetrain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.team100.lib.commands.Command100;
import org.team100.lib.hid.DriverControl;
import org.team100.lib.tank.TankDriveSubsystem;
import org.team100.lib.tank.TankDriver;
import org.team100.lib.telemetry.SupplierLogger;
import org.team100.lib.telemetry.NamedChooser;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * Manual drivetrain control.
 * 
 * Provides four manual control modes:
 * 
 * -- raw module state
 * -- robot-relative
 * -- field-relative
 * -- field-relative with rotation control
 * 
 * Use the mode supplier to choose which mode to use, e.g. using a Sendable
 * Chooser.
 */
public class DriveManually extends Command100 {

    private static final SendableChooser<String> m_manualModeChooser = new NamedChooser<>("Manual Drive Mode") {
    };

    private Supplier<String> m_mode;
    /**
     * Velocity control in control units, [-1,1] on all axes. This needs to be
     * mapped to a feasible velocity control as early as possible.
     */
    private final Supplier<DriverControl.Velocity> m_twistSupplier;
    private final TankDriveSubsystem m_drive;
    private final Map<String, TankDriver> m_drivers;
    private final TankDriver m_defaultDriver;
    String currentManualMode = null;

    public DriveManually(
            SupplierLogger parent,
            Supplier<DriverControl.Velocity> twistSupplier,
            TankDriveSubsystem robotDrive) {
        super(parent);
        m_mode = m_manualModeChooser::getSelected;
        m_twistSupplier = twistSupplier;
        m_drive = robotDrive;
        m_defaultDriver = stop();
        m_drivers = new ConcurrentHashMap<>();
        SmartDashboard.putData(m_manualModeChooser);
        addRequirements(m_drive);
    }

    @Override
    public void execute100(double dt) {
        String manualMode = m_mode.get();
        if (manualMode == null) {
            return;
        }

        if (!(manualMode.equals(currentManualMode))) {
            currentManualMode = manualMode;
        }

        // input in [-1,1] control units
        DriverControl.Velocity input = m_twistSupplier.get();
        TankDriver d = m_drivers.getOrDefault(manualMode, m_defaultDriver);
        d.apply(input, dt);

    }

    @Override
    public void end100(boolean interrupted) {
        m_drive.stop();
    }

    /**
     * Override the TankDriver  mode.
     * 
     * For testing only.
     */
    public void overrideMode(Supplier<String> mode) {
        m_mode = mode;
    }

    /** Register a TankDriver for module state mode */
    public void register(String name, boolean isDefault) {
        addName(name, isDefault);
        m_drivers.put(
                name,
                new TankDriver() {
                    public void apply(DriverControl.Velocity t, double dt) {
                        m_drive.set(t.x(), t.y());
                    }
                });
    }

    //////////////

    private TankDriver stop() {
        return new TankDriver() {
            public void apply(DriverControl.Velocity t, double dt) {
                m_drive.stop();
            }
        };
    }

    private void addName(String name, boolean isDefault) {
        m_manualModeChooser.addOption(name, name);
        if (isDefault)
            m_manualModeChooser.setDefaultOption(name, name);
    }
}
