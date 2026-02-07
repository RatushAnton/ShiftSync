package com.hit.shiftsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    // 1. Declare variables here (at class level)
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 2. Initialize Firebase
        mAuth = FirebaseAuth.getInstance();

        // 3. Bind UI Elements
        Button loginBtn = findViewById(R.id.loginButton);
        EditText emailInput = findViewById(R.id.emailInput);
        EditText passInput = findViewById(R.id.passwordInput);
        TextView regLink = findViewById(R.id.registerLink);

        // 4. Handle Login Click
        loginBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String pass = passInput.getText().toString().trim();

            if (!email.isEmpty() && !pass.isEmpty()) {
                loginUser(email, pass);
            } else {
                Toast.makeText(LoginActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            }
        });

        // 5. Handle Register Link Click
        regLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    // 6. The Login Method
    private void loginUser(String email, String pass) {
        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Login success, move to Main Dashboard
                        Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // Login failed
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}