package org.team100.lib.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.team100.lib.logging.SupplierLogger2;
import org.team100.lib.logging.SupplierLogger2.DoubleObjArraySupplierLogger2;
import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;
import org.team100.lib.telemetry.Telemetry.Level;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;

public class ForceViz {
    // see simgui.json for these names
    private static final double kScale = 0.5;

    private final List<Double> m_tactics = new ArrayList<>();
    private final List<Double> m_desired = new ArrayList<>();
    private final DoubleObjArraySupplierLogger2 m_log_tactics;
    private final DoubleObjArraySupplierLogger2 m_log_desired;

    public ForceViz(SupplierLogger2 fieldLogger) {
        m_log_tactics = fieldLogger.doubleObjArrayLogger(Level.DEBUG, "tactics");
        m_log_desired = fieldLogger.doubleObjArrayLogger(Level.DEBUG, "desired");

    }

    public void tactics(Translation2d p, FieldRelativeVelocity v) {
        put(m_tactics, p, v);
    }

    public void desired(Translation2d p, FieldRelativeVelocity v) {
        put(m_desired, p, v);
    }

    private void put(List<Double> f, Translation2d p, FieldRelativeVelocity v) {
        // ignore small forces
        if (v.norm() < 0.1)
            return;
        Optional<Rotation2d> angle = v.angle();
        if (angle.isEmpty())
            return;
        double direction = angle.get().getDegrees();
        double x = p.getX() - v.x() * kScale;
        double y = p.getY() - v.y() * kScale;
        f.add(x);
        f.add(y);
        f.add(direction);
    }

    public void render() {
        m_log_tactics.log(() -> m_tactics.toArray(new Double[0]));
        m_log_desired.log(() -> m_desired.toArray(new Double[0]));
    }

}