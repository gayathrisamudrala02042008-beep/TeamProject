package models;

public class ServiceRequest {
    private int requestId;
    private RequestType type;
    private int memberId;
    private String isbn;         // for borrow/reserve/renew
    private int borrowId;        // for return
    private int quantity;        // for borrow/return
    private String requestDate;  // DD-MM-YYYY
    private String actionDate;   // issue/return/today date depending on request
    private RequestStatus status;
    private String processedBy;  // staff username
    private String processedDate; // DD-MM-YYYY
    private String note;         // rejection/extra note

    public ServiceRequest() {}

    public ServiceRequest(int requestId, RequestType type, int memberId, String isbn,
                          int borrowId, int quantity, String requestDate, String actionDate,
                          RequestStatus status, String processedBy, String processedDate, String note) {
        this.requestId = requestId;
        this.type = type;
        this.memberId = memberId;
        this.isbn = isbn;
        this.borrowId = borrowId;
        this.quantity = quantity;
        this.requestDate = requestDate;
        this.actionDate = actionDate;
        this.status = status;
        this.processedBy = processedBy;
        this.processedDate = processedDate;
        this.note = note;
    }

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public RequestType getType() { return type; }
    public void setType(RequestType type) { this.type = type; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public int getBorrowId() { return borrowId; }
    public void setBorrowId(int borrowId) { this.borrowId = borrowId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getRequestDate() { return requestDate; }
    public void setRequestDate(String requestDate) { this.requestDate = requestDate; }

    public String getActionDate() { return actionDate; }
    public void setActionDate(String actionDate) { this.actionDate = actionDate; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    public String getProcessedDate() { return processedDate; }
    public void setProcessedDate(String processedDate) { this.processedDate = processedDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

