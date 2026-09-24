package com.forexpilot.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button createAccountButton;
    private TextView forgotPassword;

    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        createAccountButton = findViewById(R.id.createAccountButton);
        forgotPassword = findViewById(R.id.forgotPassword);

        firebaseAuth = FirebaseAuth.getInstance();

        loginButton.setOnClickListener(v -> login());

        createAccountButton.setOnClickListener(v -> createAccount());

        forgotPassword.setOnClickListener(v -> resetPassword());
    }

    private boolean validateFields() {

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Enter your email");
            emailInput.requestFocus();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Enter your password");
            passwordInput.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return false;
        }

        return true;
    }

    private void login() {

        if (!validateFields()) {
            return;
        }

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        loginButton.setEnabled(false);

        firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    loginButton.setEnabled(true);

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                this,
                                "Login successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        openDashboard();

                    } else {

                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";

                        Toast.makeText(
                                this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void createAccount() {

        if (!validateFields()) {
            return;
        }

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        createAccountButton.setEnabled(false);

        firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    createAccountButton.setEnabled(true);

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                this,
                                "Account created successfully",
                                Toast.LENGTH_SHORT
                        ).show();

                        openDashboard();

                    } else {

                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Account creation failed";

                        Toast.makeText(
                                this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void resetPassword() {

        String email = emailInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Enter your email first");
            emailInput.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return;
        }

        firebaseAuth
                .sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                this,
                                "Password reset email sent",
                                Toast.LENGTH_LONG
                        ).show();

                    } else {

                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Could not send reset email";

                        Toast.makeText(
                                this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void openDashboard() {

        Intent intent = new Intent(
                LoginActivity.this,
                MainActivity.class
        );

        startActivity(intent);
        finish();
    }
}