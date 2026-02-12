package com.hit.shiftsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

// Using Applandeo Material Calendar View for the shift display
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
 * MainActivity: The core dashboard of the application.
 * Handles displaying the calendar, loading user shifts, and providing access
 * to Manager/Admin tools based on user roles.
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView welcomeText, quotaText;
    private Button adminBtn;
    private CalendarView calendarView;
    private User currentUserData; // Stores the profile of the logged-in user

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 1. Security Check: Redirect to Login if no user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 2. Initialize UI Components
        welcomeText = findViewById(R.id.welcomeText);
        quotaText = findViewById(R.id.quotaText);
        adminBtn = findViewById(R.id.adminPanelButton);
        calendarView = findViewById(R.id.calendarView);

        // 3. Load Data from Firestore
        loadUserData(currentUser.getUid());
        loadShiftsToCalendar();

        // 4. Set up Calendar Interaction
        // When a user clicks a day, we check if there is a shift or allow them to request time off
        calendarView.setOnDayClickListener(eventDay -> {
            Calendar clickedDayCalendar = eventDay.getCalendar();
            checkForShiftDetails(clickedDayCalendar);
        });

        // 5. Admin/Manager Button Logic
        // Triggers the scheduling algorithm
        adminBtn.setOnClickListener(v -> {
            adminBtn.setEnabled(false);
            adminBtn.setText("Generating...");
            Toast.makeText(this, "Starting Algorithm...", Toast.LENGTH_SHORT).show();
            runShiftGenerationAlgorithm();
        });

        // Paystub Navigation
        Button payBtn = findViewById(R.id.viewPaystubBtn);
        payBtn.setOnClickListener(v -> startActivity(new Intent(this, PayCheckActivity.class)));
    }

    /**
     * Checks if a shift exists on the clicked date.
     * If YES -> Show details (and delete option for managers).
     * If NO -> Ask if the user wants to mark it as UNAVAILABLE.
     */
    private void checkForShiftDetails(Calendar clickedDate) {
        String myUid = mAuth.getCurrentUser().getUid();

        // Normalize time to 00:00:00 to ensure accurate date comparison
        Calendar queryDate = (Calendar) clickedDate.clone();
        queryDate.set(Calendar.HOUR_OF_DAY, 0);
        queryDate.set(Calendar.MINUTE, 0);
        queryDate.set(Calendar.SECOND, 0);
        queryDate.set(Calendar.MILLISECOND, 0);

        long targetDayStart = queryDate.getTimeInMillis();
        long targetDayEnd = targetDayStart + (24 * 60 * 60 * 1000); // End of the day

        // Query Firestore for my shifts
        db.collection("shifts")
                .whereEqualTo("userId", myUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    Shift foundShift = null;

                    // Filter client-side for the specific day
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Shift s = doc.toObject(Shift.class);
                        if (s.getStartTime() >= targetDayStart && s.getStartTime() < targetDayEnd) {
                            foundShift = s;
                            break;
                        }
                    }

                    if (foundShift != null) {
                        // Shift Found: Show Dialog
                        showShiftDetailsDialog(foundShift);
                    } else {
                        // No Shift: Allow "Request Off"
                        String dateString = String.format("%d-%02d-%02d",
                                queryDate.get(Calendar.YEAR),
                                queryDate.get(Calendar.MONTH) + 1,
                                queryDate.get(Calendar.DAY_OF_MONTH));
                        showRequestDialog(dateString);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show();
                });
    }

    private void showShiftDetailsDialog(Shift foundShift) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
        String startStr = sdf.format(new java.util.Date(foundShift.getStartTime()));
        String endStr = sdf.format(new java.util.Date(foundShift.getEndTime()));

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Shift Details")
                .setMessage("Type: " + foundShift.getType() +
                        "\nStart: " + startStr +
                        "\nEnd: " + endStr)
                .setPositiveButton("OK", null);

        // MANAGER FEATURE: Delete Shift
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
                                loadShiftsToCalendar(); // Refresh UI
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRequestDialog(String date) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Request Day Off")
                .setMessage("Mark " + date + " as UNAVAILABLE?")
                .setPositiveButton("Yes", (dialog, which) -> saveConstraint(date))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Saves a constraint (Unavailable Day) to Firestore
    private void saveConstraint(String date) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> constraint = new HashMap<>();
        constraint.put("userId", user.getUid());
        constraint.put("userName", user.getDisplayName());
        constraint.put("date", date);
        constraint.put("type", "UNAVAILABLE");

        // Use composite key (UserID + Date) to prevent duplicates
        String docId = user.getUid() + "_" + date;

        db.collection("constraints").document(docId).set(constraint)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Day blocked!", Toast.LENGTH_SHORT).show());
    }

    // Loads user profile and updates UI based on Role
    private void loadUserData(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        this.currentUserData = user;

                        welcomeText.setText("Hello, " + user.getFullName());
                        quotaText.setText("Target Quota: " + user.getShiftQuota());

                        // Show Admin/Manager controls if applicable
                        if ("ADMIN".equalsIgnoreCase(user.getRole()) || "MANAGER".equalsIgnoreCase(user.getRole())) {
                            adminBtn.setVisibility(View.VISIBLE);
                            adminBtn.setText("MANAGER".equalsIgnoreCase(user.getRole()) ?
                                    "Manager Panel: Generate" : "Admin Panel: Generate");

                            Button staffBtn = findViewById(R.id.manageStaffBtn);
                            staffBtn.setVisibility(View.VISIBLE);
                            staffBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, StaffActivity.class)));
                        }
                    }
                });
    }

    // Runs the Scheduling Algorithm
    private void runShiftGenerationAlgorithm() {
        // 1. Fetch All Doctors
        db.collection("users").get().addOnSuccessListener(userSnapshots -> {
            List<User> allDoctors = new ArrayList<>();
            for (QueryDocumentSnapshot doc : userSnapshots) {
                allDoctors.add(doc.toObject(User.class));
            }

            // 2. Fetch All Constraints (Blocked Days)
            db.collection("constraints").get().addOnSuccessListener(constraintSnapshots -> {
                Map<String, List<String>> blockedMap = new HashMap<>();
                for (QueryDocumentSnapshot doc : constraintSnapshots) {
                    String uid = doc.getString("userId");
                    String date = doc.getString("date");
                    if (!blockedMap.containsKey(uid)) blockedMap.put(uid, new ArrayList<>());
                    blockedMap.get(uid).add(date);
                }

                // 3. Execute Algorithm
                ShiftGenerator generator = new ShiftGenerator();
                Calendar now = Calendar.getInstance();
                List<Shift> newRoster = generator.generateMonthlyRoster(
                        allDoctors,
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        blockedMap
                );

                // 4. Save Results
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
            loadShiftsToCalendar();
        });
    }

    /**
     * Loads shifts from Firestore and displays them as Blue Dots on the calendar.
     */
    /**
     * Loads shifts and applies Smart Color Logic:
     * - Past Days: No Dot
     * - Today: Blue Dot
     * - Regular Shift (8h): Green Dot
     * - Half Shift (<8h): Yellow Dot
     * - Long Shift (>8h): Red Dot
     */
    private void loadShiftsToCalendar() {
        db.collection("shifts").get().addOnSuccessListener(snapshots -> {
            List<com.applandeo.materialcalendarview.EventDay> events = new ArrayList<>();
            String myUid = mAuth.getCurrentUser().getUid();

            // 1. Get Today's Date (at midnight) for comparison
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            // 2. Add Blue Dot for TODAY (Current Day)
            // Note: We clone 'today' because EventDay holds a reference to the calendar object
            Calendar todayEvent = (Calendar) today.clone();
            events.add(new com.applandeo.materialcalendarview.EventDay(todayEvent, R.drawable.ic_circle_blue));

            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                Shift shift = doc.toObject(Shift.class);

                // Filter: Only show MY shifts
                if (shift.getUserId().equals(myUid)) {
                    Calendar shiftDate = Calendar.getInstance();
                    shiftDate.setTimeInMillis(shift.getStartTime());

                    // LOGIC: "For days before today - don't mark any dots"
                    // We compare the shift date (normalized to midnight) with 'today'
                    Calendar checkDate = (Calendar) shiftDate.clone();
                    checkDate.set(Calendar.HOUR_OF_DAY, 0);
                    checkDate.set(Calendar.MINUTE, 0);
                    checkDate.set(Calendar.SECOND, 0);
                    checkDate.set(Calendar.MILLISECOND, 0);

                    if (checkDate.before(today)) {
                        continue; // Skip past shifts
                    }

                    // LOGIC: Determine Dot Color based on Duration
                    long durationMillis = shift.getEndTime() - shift.getStartTime();
                    double durationHours = durationMillis / (1000.0 * 60 * 60);

                    int dotDrawable;
                    if (durationHours > 8.5) {
                        // Long shift (> 8.5 hours) -> Red
                        dotDrawable = R.drawable.ic_dot_red;
                    } else if (durationHours < 7.5) {
                        // Half/Short shift (< 7.5 hours) -> Yellow
                        dotDrawable = R.drawable.ic_dot_yellow;
                    } else {
                        // Regular shift (approx 8 hours) -> Green
                        dotDrawable = R.drawable.ic_dot_green;
                    }

                    // Add the colored dot
                    events.add(new com.applandeo.materialcalendarview.EventDay(shiftDate, dotDrawable));
                }
            }

            // 3. Update the Calendar View on the main thread
            runOnUiThread(() -> {
                calendarView.setEvents(events);

                // Optional: Scroll to today so the user sees the blue dot immediately
                try {
                    calendarView.setDate(today);
                } catch (com.applandeo.materialcalendarview.exceptions.OutOfDateRangeException e) {
                    e.printStackTrace();
                }
            });
        });
    }
}