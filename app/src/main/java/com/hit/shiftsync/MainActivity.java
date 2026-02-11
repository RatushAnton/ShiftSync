package com.hit.shiftsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

// --- IMPORTS ---
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

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView welcomeText, quotaText;
    private Button adminBtn;
    private CalendarView calendarView;

    // --- NEW: Store the current user so we can check roles later ---
    private User currentUserData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 1. Check Login Status
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 2. Bind UI Elements
        welcomeText = findViewById(R.id.welcomeText);
        quotaText = findViewById(R.id.quotaText);
        adminBtn = findViewById(R.id.adminPanelButton);
        calendarView = findViewById(R.id.calendarView);

        // 3. Load Data
        loadUserData(currentUser.getUid());
        loadShiftsToCalendar();

        // 4. Calendar Click Listener
        calendarView.setOnDayClickListener(eventDay -> {
            Calendar clickedDayCalendar = eventDay.getCalendar();
            checkForShiftDetails(clickedDayCalendar);
        });

        // 5. Admin Button Listener
        adminBtn.setOnClickListener(v -> {
            adminBtn.setEnabled(false);
            adminBtn.setText("Generating...");
            Toast.makeText(this, "Starting Algorithm...", Toast.LENGTH_SHORT).show();
            runShiftGenerationAlgorithm();
        });

        Button payBtn = findViewById(R.id.viewPaystubBtn);
        payBtn.setOnClickListener(v -> startActivity(new Intent(this, PayCheckActivity.class)));
    }

    // --- HELPER: Handle Click on Date ---
    private void checkForShiftDetails(Calendar clickedDate) {
        String myUid = mAuth.getCurrentUser().getUid();

        // 1. Normalize the clicked date to Midnight (Start of the day)
        Calendar queryDate = (Calendar) clickedDate.clone();
        queryDate.set(Calendar.HOUR_OF_DAY, 0);
        queryDate.set(Calendar.MINUTE, 0);
        queryDate.set(Calendar.SECOND, 0);
        queryDate.set(Calendar.MILLISECOND, 0);

        long targetDayStart = queryDate.getTimeInMillis();
        long targetDayEnd = targetDayStart + (24 * 60 * 60 * 1000); // 24 hours later

        // 2. SIMPLER QUERY: Get ALL my shifts
        db.collection("shifts")
                .whereEqualTo("userId", myUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    Shift foundShift = null;

                    // 3. Filter in Java (Client Side)
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Shift s = doc.toObject(Shift.class);
                        if (s.getStartTime() >= targetDayStart && s.getStartTime() < targetDayEnd) {
                            foundShift = s;
                            break; // Found it! Stop looking.
                        }
                    }

                    // 4. Show the appropriate dialog
                    if (foundShift != null) {
                        // Found a shift! Show details.
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
                        String startStr = sdf.format(new java.util.Date(foundShift.getStartTime()));
                        String endStr = sdf.format(new java.util.Date(foundShift.getEndTime()));

                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Shift Details")
                                .setMessage("Shift: " + foundShift.getType() +
                                        "\n\nStart: " + startStr +
                                        "\nEnd: " + endStr)
                                .setPositiveButton("OK", null);

                        // --- NEW: DELETE BUTTON LOGIC ---
                        // Only Admins or Managers can delete shifts
                        if (currentUserData != null &&
                                ("ADMIN".equalsIgnoreCase(currentUserData.getRole()) ||
                                        "MANAGER".equalsIgnoreCase(currentUserData.getRole()))) {

                            // We need to capture the shift in a final variable or helper
                            Shift shiftToDelete = foundShift;
                            builder.setNegativeButton("DELETE SHIFT", (dialog, which) -> {
                                confirmDeleteShift(shiftToDelete);
                            });
                        }
                        // --------------------------------

                        builder.show();

                    } else {
                        // No shift found -> Ask to Request Day Off
                        String dateString = String.format("%d-%02d-%02d",
                                queryDate.get(Calendar.YEAR),
                                queryDate.get(Calendar.MONTH) + 1,
                                queryDate.get(Calendar.DAY_OF_MONTH));

                        showRequestDialog(dateString);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // --- NEW HELPER: Delete Shift ---
    private void confirmDeleteShift(Shift shift) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this shift?\nThis cannot be undone.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {

                    db.collection("shifts").document(shift.getShiftId()).delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Shift Deleted", Toast.LENGTH_SHORT).show();
                                loadShiftsToCalendar(); // Refresh the blue dots
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Error deleting: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- HELPER: Request Day Off ---
    private void showRequestDialog(String date) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Request Day Off")
                .setMessage("No shift assigned on " + date + ".\n\nDo you want to mark this day as UNAVAILABLE?")
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

        String docId = user.getUid() + "_" + date;

        db.collection("constraints").document(docId).set(constraint)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Day marked as unavailable", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // --- HELPER: Load User Profile ---
    private void loadUserData(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);

                        // --- NEW: Save user to global variable ---
                        this.currentUserData = user;
                        // -----------------------------------------

                        welcomeText.setText("Hello, " + user.getFullName());
                        quotaText.setText("Target Quota: " + user.getShiftQuota());

                        if ("ADMIN".equalsIgnoreCase(user.getRole()) || "MANAGER".equalsIgnoreCase(user.getRole())) {
                            adminBtn.setVisibility(View.VISIBLE);

                            if ("MANAGER".equalsIgnoreCase(user.getRole())) {
                                adminBtn.setText("Manager Panel: Generate Schedule");
                            } else {
                                adminBtn.setText("Admin Panel: Generate Schedule");
                            }

                            Button staffBtn = findViewById(R.id.manageStaffBtn);
                            staffBtn.setVisibility(View.VISIBLE);
                            staffBtn.setOnClickListener(v -> {
                                startActivity(new Intent(MainActivity.this, StaffActivity.class));
                            });
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show());
    }

    // --- HELPER: Admin Algorithm Execution ---
    private void runShiftGenerationAlgorithm() {
        db.collection("users").get().addOnSuccessListener(userSnapshots -> {
            List<User> allDoctors = new ArrayList<>();
            for (QueryDocumentSnapshot doc : userSnapshots) {
                allDoctors.add(doc.toObject(User.class));
            }

            db.collection("constraints").get().addOnSuccessListener(constraintSnapshots -> {
                Map<String, List<String>> blockedMap = new HashMap<>();

                for (QueryDocumentSnapshot doc : constraintSnapshots) {
                    String uid = doc.getString("userId");
                    String date = doc.getString("date");

                    if (!blockedMap.containsKey(uid)) {
                        blockedMap.put(uid, new ArrayList<>());
                    }
                    blockedMap.get(uid).add(date);
                }

                ShiftGenerator generator = new ShiftGenerator();
                Calendar now = Calendar.getInstance();

                List<Shift> newRoster = generator.generateMonthlyRoster(
                        allDoctors,
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        blockedMap
                );

                saveRosterToFirebase(newRoster);

            }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load constraints", Toast.LENGTH_SHORT).show());

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load doctors", Toast.LENGTH_SHORT).show();
            adminBtn.setEnabled(true);
        });
    }

    private void saveRosterToFirebase(List<Shift> roster) {
        if (roster.isEmpty()) {
            Toast.makeText(this, "Algorithm returned 0 shifts. Check logic!", Toast.LENGTH_LONG).show();
            adminBtn.setEnabled(true);
            return;
        }

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        for (Shift shift : roster) {
            batch.set(db.collection("shifts").document(shift.getShiftId()), shift);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Success! Schedule Generated.", Toast.LENGTH_LONG).show();
            adminBtn.setText("Admin Panel: Generate Schedule");
            adminBtn.setEnabled(true);
            loadShiftsToCalendar();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // --- HELPER: Display Shifts on Calendar ---
    private void loadShiftsToCalendar() {
        db.collection("shifts").get().addOnSuccessListener(snapshots -> {
            List<EventDay> events = new ArrayList<>();
            String myUid = mAuth.getCurrentUser().getUid();
            int count = 0;

            for (QueryDocumentSnapshot doc : snapshots) {
                Shift shift = doc.toObject(Shift.class);

                if (shift.getUserId().equals(myUid)) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(shift.getStartTime());

                    events.add(new EventDay(calendar, R.drawable.ic_circle_blue));
                    count++;
                }
            }

            int finalCount = count;
            runOnUiThread(() -> {
                calendarView.setEvents(events);
                Toast.makeText(MainActivity.this, "Refreshed: " + finalCount + " shifts", Toast.LENGTH_SHORT).show();
            });
        });
    }
}