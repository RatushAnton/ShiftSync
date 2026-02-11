package com.hit.shiftsync;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hit.shiftsync.models.Shift;
import com.hit.shiftsync.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;

public class PayCheckActivity extends AppCompatActivity {

    private TextView periodText, hoursText, rateText, payText;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paycheck);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        periodText = findViewById(R.id.payPeriodText);
        hoursText = findViewById(R.id.totalHoursText);
        rateText = findViewById(R.id.hourlyRateText);
        payText = findViewById(R.id.grossPayText);

        loadPayData();
    }

    private void loadPayData() {
        String targetUid = getIntent().getStringExtra("TARGET_USER_ID");
        String targetName = getIntent().getStringExtra("TARGET_USER_NAME");

        final String uidToCheck = (targetUid != null) ? targetUid : mAuth.getCurrentUser().getUid();

        if (targetName != null) {
            TextView header = findViewById(R.id.payPeriodText); // Or the main title
            // You might want to update the title to say "Paystub: Dr. House"
            getSupportActionBar().setTitle("Paystub: " + targetName);
        }

        // 1. Get User Data (for Hourly Rate)
        db.collection("users").document(uidToCheck).get().addOnSuccessListener(userSnap -> {
            User user = userSnap.toObject(User.class);
            if (user == null) return;

            rateText.setText("Hourly Rate: $" + user.getHourlyRate());

            // 2. Calculate Shifts for THIS Month
            calculateMonthlyHours(uidToCheck, user.getHourlyRate());
        });
    }

    private void calculateMonthlyHours(String uid, double rate) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);

        Calendar end = Calendar.getInstance();
        end.add(Calendar.MONTH, 1);
        end.set(Calendar.DAY_OF_MONTH, 1);
        end.set(Calendar.HOUR_OF_DAY, 0);

        // UI Update
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy");
        periodText.setText("Period: " + sdf.format(start.getTime()));

        db.collection("shifts")
                .whereEqualTo("userId", uid)
                .whereGreaterThanOrEqualTo("startTime", start.getTimeInMillis())
                .whereLessThan("startTime", end.getTimeInMillis())
                .get()
                .addOnSuccessListener(snapshots -> {
                    double totalHours = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Shift shift = doc.toObject(Shift.class);
                        long diff = shift.getEndTime() - shift.getStartTime();
                        // Convert millis to hours
                        double hours = diff / (1000.0 * 60 * 60);
                        totalHours += hours;
                    }

                    // Update UI
                    hoursText.setText(String.format("Total Hours: %.1f", totalHours));

                    double grossPay = totalHours * rate;
                    payText.setText(String.format("Estimated Pay: $%.2f", grossPay));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error calculating pay", Toast.LENGTH_SHORT).show());
    }
}