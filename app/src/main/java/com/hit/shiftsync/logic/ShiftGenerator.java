package com.hit.shiftsync.logic;

import com.hit.shiftsync.models.Shift;
import com.hit.shiftsync.models.User;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShiftGenerator {

    /**
     * @param blockedDays Map where Key = UserID, Value = List of dates ("YYYY-MM-DD") they cannot work
     */
    public List<Shift> generateMonthlyRoster(List<User> allDoctors, int year, int month, Map<String, List<String>> blockedDays) {
        List<Shift> roster = new ArrayList<>();

        // Track quotas to keep it fair (Sort by who has worked the least)
        Map<String, Integer> shiftCounts = new HashMap<>();
        for (User doc : allDoctors) {
            shiftCounts.put(doc.getUid(), 0);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int day = 1; day <= daysInMonth; day++) {

            cal.set(year, month, day);
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sun=1 ... Sat=7

            // Format today's date for checking constraints
            String dateString = String.format("%d-%02d-%02d", year, month + 1, day);

            // 1. Identify WHO is available today
            List<User> availableForWork = new ArrayList<>();

            for (User doc : allDoctors) {
                // Rule 0: GOD MODE - Admins do not work shifts.
                if ("ADMIN".equalsIgnoreCase(doc.getRole())) continue;
                // Constraint A: Did they request this day off?
                if (isBlocked(doc, dateString, blockedDays)) continue;
                // Constraint B: Did they start a LONG shift yesterday? (Recovery Day)
                if (workedLongShiftYesterday(doc, cal, roster)) continue;

                availableForWork.add(doc);
            }

            // Sort candidates by fairness (who worked least so far)
            Collections.sort(availableForWork, (u1, u2) ->
                    Integer.compare(shiftCounts.get(u1.getUid()), shiftCounts.get(u2.getUid())));

            // --- ASSIGNMENT PHASE ---

            // 1. Assign LONG Shift (Every day needs 1)
            User longDoc = null;
            if (!availableForWork.isEmpty()) {
                longDoc = availableForWork.remove(0); // Pick top candidate
                createShift(roster, longDoc, cal, "LONG", shiftCounts);
            }

            // 2. Assign HALF Shift (Every day usually has 1)
            if (!availableForWork.isEmpty()) {
                User halfDoc = availableForWork.remove(0);
                createShift(roster, halfDoc, cal, "HALF", shiftCounts);
            }

            // 3. Assign REGULAR Shifts (Sun(1) - Thu(5) ONLY)
            // "Rest of the doctors in the team work the regular shift"
            if (dayOfWeek >= Calendar.SUNDAY && dayOfWeek <= Calendar.THURSDAY) {
                // Everyone remaining in the 'available' list works Regular
                for (User regularDoc : availableForWork) {
                    createShift(roster, regularDoc, cal, "REGULAR", shiftCounts);
                }
            }
        }
        return roster;
    }

    // --- Helpers ---

    private void createShift(List<Shift> roster, User user, Calendar cal, String type, Map<String, Integer> counts) {
        long start = 0;
        long end = 0;
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        if (type.equals("LONG")) {
            start = getTime(year, month, day, 8, 0);       // 08:00 Today
            end = getTime(year, month, day + 1, 10, 0);    // 10:00 TOMORROW (26h)
        } else if (type.equals("HALF")) {
            start = getTime(year, month, day, 8, 0);
            end = getTime(year, month, day, 21, 0);        // 21:00 Today
        } else { // REGULAR
            start = getTime(year, month, day, 8, 0);
            end = getTime(year, month, day, 16, 0);        // 16:00 Today
        }

        Shift shift = new Shift("S_" + day + "_" + user.getUid().substring(0,4),
                user.getUid(), user.getFullName(), start, end, type);
        roster.add(shift);

        // Update their count (You might weight LONG shifts as 2 points in the future!)
        counts.put(user.getUid(), counts.get(user.getUid()) + 1);
    }

    private boolean isBlocked(User doc, String todayDate, Map<String, List<String>> blockedDays) {
        if (blockedDays == null) return false;
        List<String> userBlocks = blockedDays.get(doc.getUid());
        return userBlocks != null && userBlocks.contains(todayDate);
    }

    private boolean workedLongShiftYesterday(User doc, Calendar todayCal, List<Shift> roster) {
        // Clone calendar to get yesterday without messing up the main loop
        Calendar yesterday = (Calendar) todayCal.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);

        // We only care if they started a LONG shift yesterday
        long yesterdayStartRange = yesterday.getTimeInMillis();
        // Simple check: Look for a shift that belongs to user, is LONG, and started yesterday
        for (Shift s : roster) {
            if (s.getUserId().equals(doc.getUid()) && s.getType().equals("LONG")) {
                if (isSameDay(s.getStartTime(), yesterdayStartRange)) return true;
            }
        }
        return false;
    }

    private boolean isSameDay(long t1, long t2) {
        Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(t1);
        Calendar c2 = Calendar.getInstance(); c2.setTimeInMillis(t2);
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private long getTime(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.set(y, m, d, h, min);
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }
}