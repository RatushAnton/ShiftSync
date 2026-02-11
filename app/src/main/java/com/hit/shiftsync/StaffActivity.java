package com.hit.shiftsync;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.widget.Button;
import android.content.Intent;


import androidx.appcompat.app.AppCompatActivity;

import com.hit.shiftsync.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class StaffActivity extends AppCompatActivity {

    private ListView listView;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<User> staffList;
    private ArrayAdapter<String> adapter;
    private List<String> displayNames; // What we show in the list

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        listView = findViewById(R.id.staffListView);

        staffList = new ArrayList<>();
        displayNames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayNames);
        listView.setAdapter(adapter);

        // Click to Edit User
        listView.setOnItemClickListener((parent, view, position, id) -> {
            User selectedUser = staffList.get(position);
            showEditDialog(selectedUser);
        });

        loadStaff();
    }

    private void loadStaff() {
        String myUid = mAuth.getCurrentUser().getUid();

        // 1. Get MY details first (to check if I am Admin or Manager)
        db.collection("users").document(myUid).get().addOnSuccessListener(mySnapshot -> {
            User me = mySnapshot.toObject(User.class);

            if (me == null) return;

            // 2. Query logic: Admin sees all, Manager sees Department
            com.google.firebase.firestore.Query query = db.collection("users");

            if ("MANAGER".equals(me.getRole())) {
                query = query.whereEqualTo("department", me.getDepartment());
            }

            query.get().addOnSuccessListener(snapshots -> {
                staffList.clear();
                displayNames.clear();

                for (QueryDocumentSnapshot doc : snapshots) {
                    User u = doc.toObject(User.class);
                    staffList.add(u);
                    // Display: "Dr. House (DOCTOR) - Internal"
                    displayNames.add(u.getFullName() + " (" + u.getRole() + ")");
                }
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void showEditDialog(User user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit " + user.getFullName());

        // Create a layout programmatically for the dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // 1. Salary Input
        final EditText salaryInput = new EditText(this);
        salaryInput.setHint("Hourly Rate (Currently: " + user.getHourlyRate() + ")");
        salaryInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(salaryInput);

        // 2. Role Spinner (Dropdown)
        final Spinner roleSpinner = new Spinner(this);
        String[] roles = {"DOCTOR", "MANAGER", "ADMIN"};
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roles);
        roleSpinner.setAdapter(roleAdapter);

        for(int i=0; i<roles.length; i++) {
            if(roles[i].equals(user.getRole())) roleSpinner.setSelection(i);
        }
        layout.addView(roleSpinner);

        if (!"ADMIN".equals(user.getRole())) {
            Button paystubBtn = new Button(this);
            paystubBtn.setText("View Monthly Paystub");
            paystubBtn.setOnClickListener(v -> {
                Intent intent = new Intent(StaffActivity.this, PayCheckActivity.class);
                intent.putExtra("TARGET_USER_ID", user.getUid());
                intent.putExtra("TARGET_USER_NAME", user.getFullName());
                startActivity(intent);
            });
            layout.addView(paystubBtn);
        }

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newSalaryStr = salaryInput.getText().toString();
            String newRole = roleSpinner.getSelectedItem().toString();

            double newSalary = newSalaryStr.isEmpty() ? user.getHourlyRate() : Double.parseDouble(newSalaryStr);

            updateUser(user.getUid(), newRole, newSalary);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateUser(String uid, String role, double salary) {
        db.collection("users").document(uid)
                .update("role", role, "hourlyRate", salary)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show();
                    loadStaff(); // Refresh list
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}