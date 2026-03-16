package models;

public class Member {
    private int memberId;
    private String name;
    private String role;      // "Student" or "Faculty"
    private int borrowLimit;
    private String priority;  // "HIGH" or "LOW"
    private String registrationDate; // DD-MM-YYYY

    public Member() {}

    public Member(int memberId, String name, String role, int borrowLimit,
                  String priority, String registrationDate) {
        this.memberId = memberId;
        this.name = name;
        this.role = role;
        this.borrowLimit = borrowLimit;
        this.priority = priority;
        this.registrationDate = registrationDate;
    }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getBorrowLimit() { return borrowLimit; }
    public void setBorrowLimit(int borrowLimit) { this.borrowLimit = borrowLimit; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }
}

