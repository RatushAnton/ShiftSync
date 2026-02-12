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
 * * Implements a heuristic scheduling algorithm to assign shifts to doctors.
 * Goal: Minimize quota deficit (Greedy approach) while respecting constraints.
 */
public class ShiftGenerator {

    /**
     * Generates a roster for a specific month.
     * @param doctors List of available staff
     * @param year Target year
     * @param month Target month (0-11)
     * @param blockedDays Map of UserID -> List of blocked dates (Strings)
     * @return List of generated Shift objects
     */
    public List<Shift> generateMonthlyRoster(List<User> doctors, int year, int month, Map<String, List<String>> blockedDays) {
        List<Shift> roster = new ArrayList<>();

        // Track how many shifts each doctor has been assigned so far
        Map<String, Integer> currentShiftCounts = new HashMap<>();
        for (User doc : doctors) {
            currentShiftCounts.put(doc.getUid(), 0);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Loop through every day of the month
        for (int day = 1; day <= daysInMonth; day++) {
            String dateString = String.format("%d-%02d-%02d", year, month + 1, day);

            // Generate 3 shifts per day (Morning, Evening, Night)
            for (int shiftType = 0; shiftType < 3; shiftType++) {
                String type = getShiftType(shiftType); // MORNING, EVENING, NIGHT

                User bestCandidate = null;
                int maxDeficit = Integer.MIN_VALUE;

                // GREEDY SELECTION: Find the doctor who needs shifts the most
                for (User doc : doctors) {
                    // 1. Check if doctor is blocked on this day
                    if (isBlocked(doc.getUid(), dateString, blockedDays)) {
                        continue;
                    }

                    // 2. Calculate "Need" (Quota - Current Assigned)
                    int currentAssigned = currentShiftCounts.get(doc.getUid());
                    int deficit = doc.getShiftQuota() - currentAssigned;

                    // 3. Pick the one with the highest deficit
                    if (deficit > maxDeficit) {
                        maxDeficit = deficit;
                        bestCandidate = doc;
                    }
                }

                // If a candidate was found, assign the shift
                if (bestCandidate != null) {
                    long startTime = getShiftTime(year, month, day, shiftType);
                    long endTime = startTime + (8 * 60 * 60 * 1000); // 8 hours later

                    Shift shift = new Shift(
                            UUID.randomUUID().toString(),
                            bestCandidate.getUid(),
                            bestCandidate.getFullName(),
                            startTime,
                            endTime,
                            type
                    );
                    roster.add(shift);

                    // Update their count
                    currentShiftCounts.put(bestCandidate.getUid(), currentShiftCounts.get(bestCandidate.getUid()) + 1);
                }
            }
        }
        return roster;
    }

    private boolean isBlocked(String uid, String date, Map<String, List<String>> blockedDays) {
        if (blockedDays == null || !blockedDays.containsKey(uid)) return false;
        return blockedDays.get(uid).contains(date);
    }

    private String getShiftType(int i) {
        switch (i) {
            case 0: return "MORNING";
            case 1: return "EVENING";
            default: return "NIGHT";
        }
    }

    private long getShiftTime(int year, int month, int day, int type) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, day);
        if (type == 0) c.set(Calendar.HOUR_OF_DAY, 8);  // 08:00
        else if (type == 1) c.set(Calendar.HOUR_OF_DAY, 16); // 16:00
        else c.set(Calendar.HOUR_OF_DAY, 23); // 23:00

        c.set(Calendar.MINUTE, 0);
        return c.getTimeInMillis();
    }
}