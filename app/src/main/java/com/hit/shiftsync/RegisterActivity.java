package com.hit.shiftsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hit.shiftsync.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameInput, emailInput, passInput;
    private Spinner deptSpinner;
    private Button regBtn;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI
        nameInput = findViewById(R.id.regNameInput);
        emailInput = findViewById(R.id.regEmailInput);
        passInput = findViewById(R.id.regPassInput);
        deptSpinner = findViewById(R.id.departmentSpinner);
        regBtn = findViewById(R.id.registerButton);

        // Setup simple spinner for departments
        String[] depts = new String[]{"Internal Medicine", "Anesthesiology", "Surgery", "Emergency"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, depts);
        deptSpinner.setAdapter(adapter);

        regBtn.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String email = emailInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        String dept = deptSpinner.getSelectedItem().toString();

        if (email.isEmpty() || pass.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Create Auth User
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Auth successful, now save extra details to Firestore
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        saveUserToFirestore(firebaseUser.getUid(), name, email, dept);
                    } else {
                        Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String uid, String name, String email, String dept) {
        // Logic: Anesthesiology needs 9 shifts, others need 4 (As per your brief)
        int quota = dept.equals("Anesthesiology") ? 9 : 4;

        // Create the User Object
        User newUser = new User(uid, name, email, "DOCTOR", dept, quota);

        // Save to "users" collection
        db.collection("users").document(uid).set(newUser)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Welcome, Dr. " + name, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish(); // Close registration screen
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Database Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}