package com.werewolfkill.game.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class PhaseTimerService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10); // Pool for multiple rooms

    private final Map<UUID, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    private final Map<UUID, Long> phaseEndTimes = new ConcurrentHashMap<>();

    private final Map<UUID, ScheduledFuture<?>> hunterRevengeTimers = new ConcurrentHashMap<>();

    @Autowired
    private GameService gameService;

    // Phase durations (in seconds)
    private static final int STARTING_DURATION = 5;
    private static final int NIGHT_DURATION = 60;
    private static final int DAY_DURATION = 120;
    private static final int VOTING_DURATION = 60;

    /**
     * Start timer for STARTING phase
     */
    public void startStartingTimer(UUID roomId) {
        startTimer(roomId, STARTING_DURATION, () -> gameService.transitionToNightFromStarting(roomId));
    }

    /**
     * Start timer for NIGHT phase
     */
    public void startNightTimer(UUID roomId) {
        startTimer(roomId, NIGHT_DURATION, () -> gameService.transitionToDay(roomId));
    }

    /**
     * Start timer for DAY phase
     */
    public void startDayTimer(UUID roomId) {
        startTimer(roomId, DAY_DURATION, () -> gameService.transitionToVoting(roomId));
    }

    /**
     * Start timer for VOTING phase
     */
    public void startVotingTimer(UUID roomId) {
        startTimer(roomId, VOTING_DURATION, () -> gameService.transitionToNight(roomId));
    }

    /**
     * Generic timer starter
     */
    private void startTimer(UUID roomId, int durationSeconds, Runnable onComplete) {
        // Cancel existing timer if any
        cancelTimer(roomId);

        // Calculate end time
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        phaseEndTimes.put(roomId, endTime);

        System.out.println("⏰ Starting " + durationSeconds + "s timer for room " + roomId);
        System.out.println("   Will end at: " + new java.util.Date(endTime));

        // Schedule the transition
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                System.out.println("⏰ Timer expired for room " + roomId);
                onComplete.run();
                phaseEndTimes.remove(roomId);
            } catch (Exception e) {
                System.err.println("❌ Error in phase transition: " + e.getMessage());
                e.printStackTrace();
            }
        }, durationSeconds, TimeUnit.SECONDS);

        activeTimers.put(roomId, future);
    }

    /**
     * Cancel timer (when early transition happens or game ends)
     */
    public void cancelTimer(UUID roomId) {
        ScheduledFuture<?> existing = activeTimers.remove(roomId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            System.out.println("⏰ Cancelled timer for room " + roomId);
        }
        phaseEndTimes.remove(roomId);
    }

    /**
     * Get remaining time for a phase (for sync purposes)
     */
    public long getRemainingTime(UUID roomId) {
        Long endTime = phaseEndTimes.get(roomId);
        if (endTime == null)
            return 0;

        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000); // Return seconds
    }

    /**
     * Get phase end timestamp
     */
    public Long getPhaseEndTime(UUID roomId) {
        return phaseEndTimes.get(roomId);
    }

    /**
     * ✅ NEW: Start timer for hunter revenge
     */
    public void startHunterRevengeTimer(UUID roomId, int seconds, Runnable onTimeout) {
        // Cancel existing timer if any
        cancelHunterRevengeTimer(roomId);

        System.out.println("⏰ Starting " + seconds + "s hunter revenge timer for room " + roomId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                System.out.println("⏰ Hunter revenge timer expired for room " + roomId);
                onTimeout.run();
            } catch (Exception e) {
                System.err.println("❌ Error in hunter revenge timeout: " + e.getMessage());
                e.printStackTrace();
            }
        }, seconds, TimeUnit.SECONDS);

        hunterRevengeTimers.put(roomId, future);
    }

    /**
     * ✅ NEW: Cancel hunter revenge timer
     */
    public void cancelHunterRevengeTimer(UUID roomId) {
        ScheduledFuture<?> existing = hunterRevengeTimers.remove(roomId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            System.out.println("⏰ Cancelled hunter revenge timer for room " + roomId);
        }
    }

    /**
     * Cleanup on shutdown
     */
    public void cleanup() {
        activeTimers.values().forEach(future -> future.cancel(false));
        activeTimers.clear();

        hunterRevengeTimers.values().forEach(future -> future.cancel(false));
        hunterRevengeTimers.clear();

        phaseEndTimes.clear();
        scheduler.shutdown();
    }

}