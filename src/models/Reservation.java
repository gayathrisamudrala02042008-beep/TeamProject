package models;

public class Reservation {
    private int memberId;
    private String isbn;
    private String reservationDate; // DD-MM-YYYY

    public Reservation() {}

    public Reservation(int memberId, String isbn, String reservationDate) {
        this.memberId = memberId;
        this.isbn = isbn;
        this.reservationDate = reservationDate;
    }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getReservationDate() { return reservationDate; }
    public void setReservationDate(String reservationDate) { this.reservationDate = reservationDate; }
}

