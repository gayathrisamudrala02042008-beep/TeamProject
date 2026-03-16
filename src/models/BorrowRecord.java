package models;

public class BorrowRecord {
    private int borrowId;
    private int memberId;
    private String isbn;
    private int quantity;            // total quantity borrowed
    private int remainingQuantity;   // quantity not yet returned
    private String issueDate;        // DD-MM-YYYY
    private String dueDate;          // DD-MM-YYYY
    private String returnDate;       // DD-MM-YYYY or empty when not fully returned
    private int fine;                // total fine for this borrowId in rupees
    private boolean pendingApproval; // true if staff approval is required

    public BorrowRecord() {}

    public BorrowRecord(int borrowId, int memberId, String isbn, int quantity,
                        int remainingQuantity, String issueDate,
                        String dueDate, String returnDate, int fine,
                        boolean pendingApproval) {
        this.borrowId = borrowId;
        this.memberId = memberId;
        this.isbn = isbn;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.fine = fine;
        this.pendingApproval = pendingApproval;
    }

    // Backward-friendly ctor (approved by default)
    public BorrowRecord(int borrowId, int memberId, String isbn, int quantity,
                        int remainingQuantity, String issueDate,
                        String dueDate, String returnDate, int fine) {
        this(borrowId, memberId, isbn, quantity, remainingQuantity, issueDate, dueDate, returnDate, fine, false);
    }

    public int getBorrowId() { return borrowId; }
    public void setBorrowId(int borrowId) { this.borrowId = borrowId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(int remainingQuantity) { this.remainingQuantity = remainingQuantity; }

    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }

    public int getFine() { return fine; }
    public void setFine(int fine) { this.fine = fine; }

    public boolean isPendingApproval() { return pendingApproval; }
    public void setPendingApproval(boolean pendingApproval) { this.pendingApproval = pendingApproval; }
}

