package com.werewolfkill.game.model.enums;

/**
 * Enum representing different player roles in the Werewolf game
 */
public enum Role {
    WEREWOLF("Werewolf", "EVIL"),
    VILLAGER("Villager", "GOOD"),
    SEER("Seer", "GOOD"),
    DOCTOR("Doctor", "GOOD"),
    HUNTER("Hunter", "GOOD");

    private final String displayName;
    private final String team;

    Role(String displayName, String team) {
        this.displayName = displayName;
        this.team = team;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTeam() {
        return team;
    }

    public boolean isWerewolf() {
        return this == WEREWOLF;
    }

    public boolean isVillagerTeam() {
        return team.equals("GOOD");
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}