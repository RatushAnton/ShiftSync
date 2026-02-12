package com.hit.shiftsync.logic;

import com.hit.shiftsync.models.Shift;
import com.hit.shiftsync.models.User;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ShiftGenerator
 * ------------------------------------------------------------------
 * Logic Component that handles the automatic scheduling of shifts.
 * * ALGORITHM APPROACH: "Greedy Heuristic"
 * 1. Iterates through every day of the month.
 * 2. Identifies the 3 necessary shifts (Morning, Evening, Night).
 * 3. Selects the "Best Candidate" for each slot based on:
 * - Constraints (Is the doctor available?)
 * - Quota Deficit (Who is furthest from their monthly target?)
 * ------------------------------------------------------------------
 */
public class ShiftGenerator {

    /**
     * Generates a full monthly roster.
     * @param doctors List of all users with role 'DOCTOR'
     * @param year The target year (e.g., 2026)
     * @param month The target month (0-11)
     * @param blockedDays Map of UserID -> List of dates they cannot work
     * @return A list of Shift objects ready to be saved to Firestore
     */
    public List<Shift> generateMonthlyRoster(List<User> doctors, int year, int month, Map<String, List<String>> blockedDays) {
        List<Shift> roster = new ArrayList<>();

        // Step 1: Initialize a counter to track assigned shifts per doctor
        Map<String, Integer> currentShiftCounts = new HashMap<>();
        for (User doc : doctors) {
            currentShiftCounts.put(doc.getUid(), 0);
        }

        // Step 2: Determine how many days are in the target month
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Step 3: Iterate through every day of the month
        for (int day = 1; day <= daysInMonth; day++) {
            String dateString = String.format("%d-%02d-%02d", year, month + 1, day);

            // We need 3 shifts per day: 0=Morning, 1=Evening, 2=Night
            for (int shiftType = 0; shiftType < 3; shiftType++) {
                String type = getShiftType(shiftType);

                User bestCandidate = null;
                int maxDeficit = Integer.MIN_VALUE;

                // --- GREEDY SELECTION START ---
                // Find the doctor who needs this shift the most
                for (User doc : doctors) {

                    // Constraint Check: Is the doctor blocked on this specific date?
                    if (isBlocked(doc.getUid(), dateString, blockedDays)) {
                        continue; // Skip this doctor
                    }

                    // Heuristic: Calculate "Deficit" (Target Quota - Already Assigned)
                    // The higher the deficit, the more they need the shift.
                    int currentAssigned = currentShiftCounts.get(doc.getUid());
                    int deficit = doc.getShiftQuota() - currentAssigned;

                    // If this doctor has a higher need than our current best candidate, pick them
                    if (deficit > maxDeficit) {
                        maxDeficit = deficit;
                        bestCandidate = doc;
                    }
                }
                // --- GREEDY SELECTION END ---

                // If we found a valid doctor, create the shift
                if (bestCandidate != null) {
                    long startTime = getShiftTime(year, month, day, shiftType);
                    long endTime = startTime + (8 * 60 * 60 * 1000); // Standard 8-hour duration

                    Shift shift = new Shift(
                            UUID.randomUUID().toString(), // Generate unique ID
                            bestCandidate.getUid(),
                            bestCandidate.getFullName(),
                            startTime,
                            endTime,
                            type
                    );
                    roster.add(shift);

                    // Increment their shift count so the algorithm knows for next time
                    currentShiftCounts.put(bestCandidate.getUid(), currentShiftCounts.get(bestCandidate.getUid()) + 1);
                }
            }
        }
        return roster;
    }

    // Helper: Checks if a specific doctor has blocked a specific date
    private boolean isBlocked(String uid, String date, Map<String, List<String>> blockedDays) {
        if (blockedDays == null || !blockedDays.containsKey(uid)) return false;
        return blockedDays.get(uid).contains(date);
    }

    // Helper: Converts index to readable Shift Type
    private String getShiftType(int i) {
        switch (i) {
            case 0: return "MORNING";
            case 1: return "EVENING";
            default: return "NIGHT";
        }
    }

    // Helper: Creates a Timestamp for the shift start
    private long getShiftTime(int year, int month, int day, int type) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, day);
        if (type == 0) c.set(Calendar.HOUR_OF_DAY, 8);  // 08:00 start
        else if (type == 1) c.set(Calendar.HOUR_OF_DAY, 16); // 16:00 start
        else c.set(Calendar.HOUR_OF_DAY, 23); // 23:00 start

        c.set(Calendar.MINUTE, 0);
        return c.getTimeInMillis();
    }
}