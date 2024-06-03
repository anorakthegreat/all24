package org.team100.control;

import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;

/**
 * The Pilot is the interface for manual or autonomous control.
 */
public interface Pilot {
    /**
     * In comp this is controller units [-1,1] but here we want the autopilot to use
     * field units, so the human uses field units too.
     */
    default FieldRelativeVelocity driveVelocity() {
        return new FieldRelativeVelocity(0, 0, 0);
    }

    /** Run the intake until a note is in the indexer. */
    default boolean intake() {
        return false;
    }

    default boolean outtake() {
        return false;
    }

    default boolean shoot() {
        return false;
    }

    default boolean lob() {
        return false;
    }

    default boolean amp() {
        return false;
    }

    default boolean rotateToShoot() {
        return false;
    }

    /** Drive to the speaker with a note and shoot it. */
    default boolean scoreSpeaker() {
        return false;
    }

    /** Drive to the amp with a note and score it. */
    default boolean scoreAmp() {
        return false;
    }

    default boolean driveToSource() {
        return false;
    }

    /** Drive to the passing spot with a note and lob it. */
    default boolean pass() {
        return false;
    }

    default boolean driveToNote() {
        return false;
    }

    default boolean shootCommand() {
        return false;
    }

    default boolean defend() {
        return false;
    }

    /**
     * The controller states here are monitored with triggers, which notice
     * **EDGES** so we need to start with everything off, and wait until the trigger
     * is listening.
     */
    void begin();

    /** Turn off all the outputs. */
    void reset();

    default void periodic() {

    }
}
