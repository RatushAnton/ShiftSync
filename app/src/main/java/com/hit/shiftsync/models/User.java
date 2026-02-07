package com.hit.shiftsync.models;

public class User {
    private String uid;          // The unique ID from Firebase Auth
    private String fullName;     // e.g., "Dr. Yossi Cohen"
    private String email;
    private String role;         // "ADMIN" or "DOCTOR"
    private String department;   // e.g., "Internal", "Anesthesiology"
    private int shiftQuota;      // How many shifts they MUST do (e.g., 9)

    // Empty constructor is REQUIRED for Firestore!
    // If you don't have this, the app will crash when reading data.
    public User() { }

    public User(String uid, String fullName, String email, String role, String department, int shiftQuota) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.department = department;
        this.shiftQuota = shiftQuota;
    }

    // Getters and Setters (You can generate these by Right Click -> Generate -> Getter and Setter)
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public int getShiftQuota() { return shiftQuota; }
    public void setShiftQuota(int shiftQuota) { this.shiftQuota = shiftQuota; }
}