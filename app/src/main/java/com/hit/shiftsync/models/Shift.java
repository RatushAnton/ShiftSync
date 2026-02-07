package com.hit.shiftsync.models;

public class Shift {
    private String shiftId;      // Unique ID for the shift
    private String userId;       // Who is working? (Links to User.uid)
    private String userName;     // Store name here too, saves us a database lookup later!
    private long startTime;      // Timestamp in milliseconds
    private long endTime;        // Timestamp in milliseconds
    private String type;         // "LONG" (26h), "HALF" (Morning), "NIGHT"
    private String note;         // e.g., "Replacing Dr. Levy"

    // Empty constructor for Firestore
    public Shift() { }

    public Shift(String shiftId, String userId, String userName, long startTime, long endTime, String type) {
        this.shiftId = shiftId;
        this.userId = userId;
        this.userName = userName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
        this.note = "";
    }

    // Getters and Setters
    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    // Helper method to check duration in hours (Good for Payslip logic later)
    public double getDurationHours() {
        long diff = endTime - startTime;
        return diff / (1000.0 * 60 * 60);
    }
}