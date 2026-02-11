package com.hit.shiftsync.models;

public class User {
    private String uid;
    private String fullName;
    private String email;
    private String role;         // "ADMIN", "MANAGER", "DOCTOR"
    private String department;
    private int shiftQuota;
    private double hourlyRate;   // NEW: For Paystubs (e.g., 50.0)

    public User() { }

    public User(String uid, String fullName, String email, String role, String department, int shiftQuota, double hourlyRate) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.department = department;
        this.shiftQuota = shiftQuota;
        this.hourlyRate = hourlyRate;
    }

    // Getters and Setters
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

    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }
}