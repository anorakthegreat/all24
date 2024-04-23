package org.team100.sim;

import org.dyn4j.geometry.Vector2;

/**
 * Controls apply force and torque.
 */
public class Player extends RobotBody {

    public Player(SimWorld world) {
        super("player", world);
    }

    @Override
    public boolean friend(RobotBody other) {
        // only one player so only friends are friends
        return other instanceof Friend;
    }

    @Override
    public Vector2 ampPosition() {
        return Friend.kAmpSpot;
    }

    @Override
    public Vector2 shootingPosition() {
        return Friend.kShootingSpot;
    }

    @Override
    public double shootingAngle() {
        return Friend.kShootingAngle;
    }

    @Override
    public Vector2 sourcePosition() {
        return Friend.kSource;
    }

    @Override
    public Vector2 opponentSourcePosition() {
        return Foe.kSource;
    }

    @Override
    public Vector2 defenderPosition() {
        // guess about a good spot to wait
        return Foe.kSource.sum(3, 2);
    }
}
