package com.hit.shiftsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

// Using Applandeo Material Calendar View for the visual schedule
import com.applandeo.materialcalendarview.CalendarView;
import com.applandeo.materialcalendarview.EventDay;
import com.hit.shiftsync.logic.ShiftGenerator;
import com.hit.shiftsync.models.Shift;
import com.hit.shiftsync.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MainActivity
 * ------------------------------------------------------------------
 * The central dashboard of the application.
 * Responsibilities:
 * 1. Authenticate User (Redirect to Login if null).
 * 2. Load User Profile & Role (Doctor vs Manager/Admin).
 * 3. Display Calendar with visually coded shifts (Dots).
 * 4. Provide navigation to sub-features (Paystubs, Manager Panel).
 * ------------------------------------------------------------------
 */
public class MainActivity extends AppCompatActivity {

    // Firebase Components
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // UI Components
    private TextView welcomeText, quotaText;
    private Button adminBtn;
    private CalendarView calendarView;

    private User currentUserData; // Cached user profile for permission checks

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Security Check: Ensure user is logged in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish(); // Prevent back-navigation
            return;
        }

        // Bind UI Elements
        welcomeText = findViewById(R.id.welcomeText);
        quotaText = findViewById(R.id.quotaText);
        adminBtn = findViewById(R.id.adminPanelButton);
        calendarView = findViewById(R.id.calendarView);

        // Load Data
        loadUserData(currentUser.getUid());
        loadShiftsToCalendar();

        // Calendar Interaction: Handle click on specific days
        calendarView.setOnDayClickListener(eventDay -> {
            Calendar clickedDayCalendar = eventDay.getCalendar();
            checkForShiftDetails(clickedDayCalendar);
        });

        // Admin/Manager Logic: Trigger the Algorithm
        adminBtn.setOnClickListener(v -> {
            adminBtn.setEnabled(false);
            adminBtn.setText("Generating...");
            Toast.makeText(this, "Starting Scheduling Algorithm...", Toast.LENGTH_SHORT).show();
            runShiftGenerationAlgorithm();
        });

        // Navigation: Paystub
        Button payBtn = findViewById(R.id.viewPaystubBtn);
        payBtn.setOnClickListener(v -> startActivity(new Intent(this, PayCheckActivity.class)));
    }

    /**
     * Logic to determine what happens when a day is clicked.
     * - If Shift Exists: Show Details (and Delete option for Managers).
     * - If Empty: Allow user to Request Day Off (Constraint).
     */
    private void checkForShiftDetails(Calendar clickedDate) {
        String myUid = mAuth.getCurrentUser().getUid();

        // Normalize clicked date to Midnight for accurate comparison
        Calendar queryDate = (Calendar) clickedDate.clone();
        setMidnight(queryDate);

        long targetDayStart = queryDate.getTimeInMillis();
        long targetDayEnd = targetDayStart + (24 * 60 * 60 * 1000);

        db.collection("shifts")
                .whereEqualTo("userId", myUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    Shift foundShift = null;

                    // Filter results to find shift on this specific day
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Shift s = doc.toObject(Shift.class);
                        if (s.getStartTime() >= targetDayStart && s.getStartTime() < targetDayEnd) {
                            foundShift = s;
                            break;
                        }
                    }

                    if (foundShift != null) {
                        showShiftDetailsDialog(foundShift);
                    } else {
                        // Format date for the dialog message
                        String dateString = String.format("%d-%02d-%02d",
                                queryDate.get(Calendar.YEAR),
                                queryDate.get(Calendar.MONTH) + 1,
                                queryDate.get(Calendar.DAY_OF_MONTH));
                        showRequestDialog(dateString);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show());
    }

    // Displays shift info. Managers get a "DELETE" button.
    private void showShiftDetailsDialog(Shift foundShift) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
        String startStr = sdf.format(new java.util.Date(foundShift.getStartTime()));
        String endStr = sdf.format(new java.util.Date(foundShift.getEndTime()));

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Shift Details")
                .setMessage("Shift Type: " + foundShift.getType() +
                        "\nStart: " + startStr +
                        "\nEnd: " + endStr)
                .setPositiveButton("OK", null);

        // RBAC (Role Based Access Control): Only Managers/Admins can delete
        if (currentUserData != null &&
                ("ADMIN".equalsIgnoreCase(currentUserData.getRole()) ||
                        "MANAGER".equalsIgnoreCase(currentUserData.getRole()))) {

            builder.setNegativeButton("DELETE SHIFT", (dialog, which) -> {
                confirmDeleteShift(foundShift);
            });
        }
        builder.show();
    }

    private void confirmDeleteShift(Shift shift) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this shift?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    db.collection("shifts").document(shift.getShiftId()).delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Shift Deleted", Toast.LENGTH_SHORT).show();
                                loadShiftsToCalendar(); // Refresh UI to remove dot
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Allows users to mark a day as "UNAVAILABLE"
    private void showRequestDialog(String date) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Request Day Off")
                .setMessage("Mark " + date + " as UNAVAILABLE for future scheduling?")
                .setPositiveButton("Yes, Block Date", (dialog, which) -> saveConstraint(date))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveConstraint(String date) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> constraint = new HashMap<>();
        constraint.put("userId", user.getUid());
        constraint.put("userName", user.getDisplayName());
        constraint.put("date", date);
        constraint.put("type", "UNAVAILABLE");

        // Composite Key (Uid + Date) prevents duplicate entries
        String docId = user.getUid() + "_" + date;

        db.collection("constraints").document(docId).set(constraint)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Day blocked!", Toast.LENGTH_SHORT).show());
    }

    // Fetch User Profile to determine Role and Name
    private void loadUserData(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        this.currentUserData = user;

                        welcomeText.setText("Hello, " + user.getFullName());
                        quotaText.setText("Target Quota: " + user.getShiftQuota());

                        // Reveal Admin buttons if role matches
                        if ("ADMIN".equalsIgnoreCase(user.getRole()) || "MANAGER".equalsIgnoreCase(user.getRole())) {
                            adminBtn.setVisibility(View.VISIBLE);
                            adminBtn.setText("MANAGER".equalsIgnoreCase(user.getRole()) ?
                                    "Manager Panel: Generate Schedule" : "Admin Panel: Generate Schedule");

                            Button staffBtn = findViewById(R.id.manageStaffBtn);
                            staffBtn.setVisibility(View.VISIBLE);
                            staffBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, StaffActivity.class)));
                        }
                    }
                });
    }

    // --- ALGORITHM EXECUTION ---
    private void runShiftGenerationAlgorithm() {
        // 1. Fetch Doctors
        db.collection("users").get().addOnSuccessListener(userSnapshots -> {
            List<User> allDoctors = new ArrayList<>();
            for (QueryDocumentSnapshot doc : userSnapshots) {
                allDoctors.add(doc.toObject(User.class));
            }

            // 2. Fetch Constraints
            db.collection("constraints").get().addOnSuccessListener(constraintSnapshots -> {
                Map<String, List<String>> blockedMap = new HashMap<>();
                for (QueryDocumentSnapshot doc : constraintSnapshots) {
                    String uid = doc.getString("userId");
                    String date = doc.getString("date");
                    if (!blockedMap.containsKey(uid)) blockedMap.put(uid, new ArrayList<>());
                    blockedMap.get(uid).add(date);
                }

                // 3. Run Logic
                ShiftGenerator generator = new ShiftGenerator();
                Calendar now = Calendar.getInstance();
                List<Shift> newRoster = generator.generateMonthlyRoster(
                        allDoctors,
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        blockedMap
                );

                // 4. Save
                saveRosterToFirebase(newRoster);
            });
        });
    }

    private void saveRosterToFirebase(List<Shift> roster) {
        if (roster.isEmpty()) {
            adminBtn.setEnabled(true);
            return;
        }
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        for (Shift shift : roster) {
            batch.set(db.collection("shifts").document(shift.getShiftId()), shift);
        }
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Schedule Generated Successfully", Toast.LENGTH_LONG).show();
            adminBtn.setEnabled(true);
            loadShiftsToCalendar(); // Refresh UI
        });
    }

    /**
     * Loads shifts from Firestore and visualizes them on the Calendar.
     * Uses Color Coding:
     * - Blue: Today (Default)
     * - Green: Regular Shift
     * - Yellow: Short Shift
     * - Red: Long Shift
     */
    private void loadShiftsToCalendar() {
        db.collection("shifts").get().addOnSuccessListener(snapshots -> {
            // Map ensures we don't have duplicate events on the same day (Shift overrides Today)
            Map<String, com.applandeo.materialcalendarview.EventDay> eventsMap = new HashMap<>();
            String myUid = mAuth.getCurrentUser().getUid();

            // 1. Set Default "Today" Indicator
            Calendar today = Calendar.getInstance();
            setMidnight(today);
            String todayKey = formatDate(today);
            eventsMap.put(todayKey, new com.applandeo.materialcalendarview.EventDay(today, R.drawable.ic_circle_blue));

            // 2. Process Shifts
            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                Shift shift = doc.toObject(Shift.class);

                if (shift.getUserId().equals(myUid)) {
                    Calendar shiftCal = Calendar.getInstance();
                    shiftCal.setTimeInMillis(shift.getStartTime());
                    setMidnight(shiftCal); // Normalize to Midnight for library compatibility

                    // Do not show dots for past days
                    if (shiftCal.before(today)) {
                        continue;
                    }

                    // Calculate Duration to pick color
                    long durationMillis = shift.getEndTime() - shift.getStartTime();
                    double durationHours = durationMillis / (1000.0 * 60 * 60);

                    int dotDrawable;
                    if (durationHours > 8.5) {
                        dotDrawable = R.drawable.ic_dot_red;
                    } else if (durationHours < 7.5) {
                        dotDrawable = R.drawable.ic_dot_yellow;
                    } else {
                        dotDrawable = R.drawable.ic_dot_green;
                    }

                    String shiftKey = formatDate(shiftCal);
                    // This put() overrides the blue dot if a shift exists today
                    eventsMap.put(shiftKey, new com.applandeo.materialcalendarview.EventDay(shiftCal, dotDrawable));
                }
            }

            // 3. Update UI Thread
            runOnUiThread(() -> {
                List<com.applandeo.materialcalendarview.EventDay> finalEvents = new ArrayList<>(eventsMap.values());
                calendarView.setEvents(finalEvents);
            });
        });
    }

    // Helper: Resets Calendar time to 00:00:00 for accurate day comparison
    private void setMidnight(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    // Helper: Generates unique key for Map based on date
    private String formatDate(Calendar cal) {
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH);
    }
}