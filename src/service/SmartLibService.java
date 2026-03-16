package service;

import exception.BookNotAvailableException;
import exception.BorrowLimitExceededException;
import exception.InvalidMemberException;
import exception.MemberAlreadyAssignedException;
import exception.ReadingRoomFullException;
import models.Book;
import models.BorrowRecord;
import models.Member;
import models.ReadingRoom;
import models.RequestStatus;
import models.RequestType;
import models.Reservation;
import models.ServiceRequest;
import utility.CSVUtil;
import utility.InputUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Stack;

public class SmartLibService {

    private HashMap<String, Book> books;
    private HashMap<Integer, Member> members;
    private LinkedList<BorrowRecord> borrowHistory;

    // ISBN -> reservation queue (Faculty = HIGH priority first)
    private HashMap<String, PriorityQueue<Member>> reservationQueues;

    // Reservations history for reporting
    private LinkedList<Reservation> reservationsHistory;

    // Requests (must be approved by staff)
    private LinkedList<ServiceRequest> serviceRequests;

    // Reading rooms: separate for Student and Faculty, circular allocation
    private ReadingRoom[] studentRooms;
    private ReadingRoom[] facultyRooms;
    private int studentRoomIndex;
    private int facultyRoomIndex;
    private static final int STUDENT_ROOM_CAPACITY = 10;
    private static final int FACULTY_ROOM_CAPACITY = 5;
    private static final int STUDENT_ROOM_COUNT = 2;
    private static final int FACULTY_ROOM_COUNT = 2;

    // Waiting queues for reading rooms
    private Queue<Integer> studentWaitingQueue;
    private Queue<Integer> facultyWaitingQueue;

    // Undo stack for staff-approved actions only
    private Stack<BorrowRecord> actionStack = new Stack<>();

    // Rack management: same ISBN -> same rack number
    private static final int RACK_COUNT = 50;
    private String[] racks; // rack index -> ISBN
    private HashMap<String, Integer> isbnToRack;

    // Auto-increment ids
    private int nextBorrowId = 1;
    private int nextRequestId = 1;

    // Session
    private String currentUserRole = null; // Staff/Student/Faculty
    private int currentMemberId = -1;
    private String currentStaffUser = null;

    public SmartLibService() {
        CSVUtil.initializeFiles();
        books = new HashMap<>();
        members = new HashMap<>();
        borrowHistory = new LinkedList<>();
        reservationQueues = new HashMap<>();
        reservationsHistory = new LinkedList<>();
        serviceRequests = new LinkedList<>();

        racks = new String[RACK_COUNT];
        isbnToRack = new HashMap<>();

        studentRooms = new ReadingRoom[STUDENT_ROOM_COUNT];
        for (int i = 0; i < studentRooms.length; i++) {
            studentRooms[i] = new ReadingRoom(i + 1, STUDENT_ROOM_CAPACITY, "Student");
        }
        facultyRooms = new ReadingRoom[FACULTY_ROOM_COUNT];
        for (int i = 0; i < facultyRooms.length; i++) {
            facultyRooms[i] = new ReadingRoom(i + 1, FACULTY_ROOM_CAPACITY, "Faculty");
        }
        studentRoomIndex = 0;
        facultyRoomIndex = 0;

        studentWaitingQueue = new LinkedList<>();
        facultyWaitingQueue = new LinkedList<>();

        loadFromCSV();
    }

    // --------------------- LOGIN ----------------------
    public void login() {
        while (true) {
            System.out.println("\n===== LOGIN =====");
            System.out.println("1. Staff");
            System.out.println("2. Student");
            System.out.println("3. Faculty");
            int ch = InputUtil.readInt("Choose role: ");

            if (ch == 1) {
                String user = InputUtil.readString("Staff username: ");
                String pass = InputUtil.readString("Staff password: ");
                // simple hard-coded login for hackathon
                if ("admin".equals(user) && "admin".equals(pass)) {
                    currentUserRole = "Staff";
                    currentStaffUser = user;
                    currentMemberId = -1;
                    System.out.println("Logged in as Staff.");
                    return;
                }
                System.out.println("Invalid staff credentials. Try again.");
            } else if (ch == 2 || ch == 3) {
                int memberId = InputUtil.readInt("Member ID: ");
                Member m = members.get(memberId);
                if (m == null) {
                    System.out.println("Member not found. Ask staff to register you.");
                    continue;
                }
                String expected = (ch == 2) ? "Student" : "Faculty";
                if (!expected.equalsIgnoreCase(m.getRole())) {
                    System.out.println("Role mismatch. Your registered role is: " + m.getRole());
                    continue;
                }
                currentUserRole = expected;
                currentMemberId = memberId;
                currentStaffUser = null;
                System.out.println("Logged in as " + expected + " (MemberID " + memberId + ").");
                return;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    public String getCurrentUserRole() { return currentUserRole; }
    public int getCurrentMemberId() { return currentMemberId; }

    public void logout() {
        currentUserRole = null;
        currentMemberId = -1;
        currentStaffUser = null;
    }

    // --------------------- LOAD / SAVE ----------------------
    private void loadFromCSV() {
        // load books
        for (Book b : CSVUtil.loadBooks()) {
            books.put(b.getIsbn(), b);
            if (b.getRackNo() > 0 && b.getRackNo() <= RACK_COUNT) {
                racks[b.getRackNo() - 1] = b.getIsbn();
                isbnToRack.put(b.getIsbn(), b.getRackNo());
            } else {
                int assigned = ensureRackForIsbn(b.getIsbn());
                b.setRackNo(assigned);
            }
        }
        // load members
        for (Member m : CSVUtil.loadMembers()) {
            members.put(m.getMemberId(), m);
        }
        // load borrow records
        LinkedList<BorrowRecord> loaded = CSVUtil.loadBorrowRecords();
        borrowHistory.addAll(loaded);
        int maxBorrowId = 0;
        for (BorrowRecord r : borrowHistory) maxBorrowId = Math.max(maxBorrowId, r.getBorrowId());
        nextBorrowId = maxBorrowId + 1;

        // load requests
        LinkedList<ServiceRequest> reqLoaded = CSVUtil.loadRequests();
        serviceRequests.addAll(reqLoaded);
        nextRequestId = 1;
        for (ServiceRequest r : serviceRequests) {
            if (r.getRequestId() >= nextRequestId) {
                nextRequestId = r.getRequestId() + 1;
            }
        }
    }

    // --------------------- RACK MANAGEMENT ----------------------
    private int ensureRackForIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) return -1;
        Integer existing = isbnToRack.get(isbn);
        if (existing != null) return existing;

        for (int i = 0; i < racks.length; i++) {
            if (racks[i] == null) {
                racks[i] = isbn;
                isbnToRack.put(isbn, i + 1); // rack number is 1-based
                return i + 1;
            }
            if (isbn.equals(racks[i])) {
                isbnToRack.put(isbn, i + 1);
                return i + 1;
            }
        }
        // no rack available
        isbnToRack.put(isbn, -1);
        return -1;
    }

    public void viewRacks() {
        System.out.println("\n===== RACKS =====");
        for (int i = 0; i < racks.length; i++) {
            String isbn = racks[i];
            if (isbn == null) continue;
            Book b = books.get(isbn);
            String title = (b == null) ? "?" : b.getTitle();
            int avail = (b == null) ? 0 : b.getAvailableCopies();
            int total = (b == null) ? 0 : b.getTotalCopies();
            System.out.println("Rack " + (i + 1) + " -> ISBN: " + isbn + " | Title: " + title +
                    " | Available: " + avail + " | Total: " + total);
        }
        System.out.println("=================\n");
    }

    // --------------------- BOOK MANAGEMENT (Staff) ------------------------
    public void addBook() {
        if (!isStaff()) { System.out.println("Only staff can add books."); return; }
        Book book = InputUtil.readBook();
        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            System.out.println("Invalid ISBN.");
            return;
        }
        if (books.containsKey(book.getIsbn())) {
            System.out.println("Book already exists. Use update copies feature (not included).");
            return;
        }
        int rackNo = ensureRackForIsbn(book.getIsbn());
        book.setRackNo(rackNo);
        books.put(book.getIsbn(), book);
        CSVUtil.rewriteBooks(new ArrayList<>(books.values()), (isbn) -> isbnToRack.getOrDefault(isbn, -1));
        System.out.println("Book added successfully. Assigned rack: " + isbnToRack.get(book.getIsbn()));

        System.out.println("\n===== ALL BOOKS =====");

        for (Book b : books.values()) {
            System.out.println(
                "ISBN: " + b.getIsbn() +
                " | Title: " + b.getTitle() +
                " | Author: " + b.getAuthor() +
                " | Available: " + b.getAvailableCopies() +
                " | Rack: " + isbnToRack.getOrDefault(b.getIsbn(), -1)
            );
        }

        System.out.println("=====================\n");
    }

    public void updateBookCopies() {
        if (!isStaff()) { System.out.println("Only staff can update book copies."); return; }

        String isbn = InputUtil.readString("Enter ISBN to update: ");
        Book book = books.get(isbn);
        if (book == null) {
            System.out.println("Book not found: " + isbn);
            return;
        }

        int issued = Math.max(0, book.getTotalCopies() - book.getAvailableCopies());

        System.out.println("\nCurrent: Total=" + book.getTotalCopies() +
                " | Available=" + book.getAvailableCopies() +
                " | Issued=" + issued +
                " | Rack=" + isbnToRack.getOrDefault(isbn, -1));
        System.out.println("1. Add/Remove copies (delta)");
        System.out.println("2. Set Total Copies (safe)");
        int ch = InputUtil.readInt("Enter choice: ");

        if (ch == 1) {
            int delta = InputUtil.readInt("Enter delta (e.g. 5 to add, -2 to remove): ");
            if (delta == 0) {
                System.out.println("No change.");
                return;
            }

            if (delta > 0) {
                book.setTotalCopies(book.getTotalCopies() + delta);
                book.setAvailableCopies(book.getAvailableCopies() + delta);
            } else {
                int remove = -delta;
                if (remove > book.getAvailableCopies()) {
                    System.out.println("Cannot remove " + remove + " copies. Available copies are only: " + book.getAvailableCopies());
                    return;
                }
                book.setTotalCopies(book.getTotalCopies() - remove);
                book.setAvailableCopies(book.getAvailableCopies() - remove);
            }
        } else if (ch == 2) {
            int newTotal = InputUtil.readInt("Enter new total copies: ");
            if (newTotal < issued) {
                System.out.println("Cannot set TotalCopies to " + newTotal +
                        " because " + issued + " copies are currently issued.");
                return;
            }
            book.setTotalCopies(newTotal);
            book.setAvailableCopies(newTotal - issued);
        } else {
            System.out.println("Invalid choice.");
            return;
        }

        int rackNo = ensureRackForIsbn(isbn);
        book.setRackNo(rackNo);

        CSVUtil.rewriteBooks(new ArrayList<>(books.values()), (x) -> isbnToRack.getOrDefault(x, -1));
        System.out.println("Book copies updated successfully.");
        System.out.println("Updated: Total=" + book.getTotalCopies() + " | Available=" + book.getAvailableCopies() + " | Rack=" + rackNo);
    }

    // --------------------- MEMBER MANAGEMENT (Staff) ----------------------
    public void registerMember() {
        if (!isStaff()) { System.out.println("Only staff can register members."); return; }
        int memberId = InputUtil.readInt("Enter Member ID: ");
        String name = InputUtil.readString("Enter Name: ");
        String role = InputUtil.readString("Enter Role (Student/Faculty): ");
        String registrationDate = InputUtil.readDate("Enter Registration Date (DD-MM-YYYY): ");

        int borrowLimit;
        String priority;
        if ("Faculty".equalsIgnoreCase(role)) {
            borrowLimit = 5;
            priority = "HIGH";
            role = "Faculty";
        } else {
            borrowLimit = 3;
            priority = "LOW";
            role = "Student";
        }

        Member member = new Member(memberId, name, role, borrowLimit, priority, registrationDate);

        members.put(memberId, member);
        CSVUtil.rewriteMembers(new ArrayList<>(members.values()));

        System.out.println("\nMember registered successfully.\n");

        System.out.println("===== ALL MEMBERS =====");

        for (Member m : members.values()) {
            System.out.println(
                "ID: " + m.getMemberId() +
                " | Name: " + m.getName() +
                " | Role: " + m.getRole() +
                " | BorrowLimit: " + m.getBorrowLimit()
            );
        }

        System.out.println("========================\n");
    }

    // --------------------- SEARCH OPERATIONS ----------------------
    public void searchBookMenu() {
        System.out.println("1. Linear Search by Title");
        System.out.println("2. Binary Search by Title (sorted)");
        System.out.println("3. Search by ISBN");
        int choice = InputUtil.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> linearSearchByTitle();
            case 2 -> binarySearchByTitle();
            case 3 -> searchByIsbn();
            default -> System.out.println("Invalid choice.");
        }
    }

    private void linearSearchByTitle() {
        String title = InputUtil.readString("Enter Title to search: ");
        boolean found = false;
        for (Book b : books.values()) {
            if (b.getTitle() != null && b.getTitle().equalsIgnoreCase(title)) {
                System.out.println("Found: ISBN " + b.getIsbn() + ", Title " + b.getTitle() +
                        ", Rack: " + isbnToRack.getOrDefault(b.getIsbn(), -1));
                found = true;
            }
        }
        if (!found) System.out.println("No book found with given title.");
        System.out.println();
    }

    private void binarySearchByTitle() {
        String title = InputUtil.readString("Enter Title to search: ");
        ArrayList<Book> list = new ArrayList<>(books.values());
        list.sort(Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));

        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            Book midBook = list.get(mid);
            int cmp = safe(midBook.getTitle()).compareToIgnoreCase(safe(title));
            if (cmp == 0) {
                System.out.println("Found: ISBN " + midBook.getIsbn() + ", Title " + midBook.getTitle() +
                        ", Rack: " + isbnToRack.getOrDefault(midBook.getIsbn(), -1));
                System.out.println();
                return;
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        System.out.println("No book found with given title.");
        System.out.println();
    }

    private void searchByIsbn() {
        String isbn = InputUtil.readString("Enter ISBN: ");
        Book b = books.get(isbn);
        if (b != null) {
            System.out.println("Found: ISBN " + b.getIsbn() +
                    ", Title " + b.getTitle() +
                    ", Author " + b.getAuthor() +
                    ", Rack: " + isbnToRack.getOrDefault(isbn, -1));
        } else {
            System.out.println("No book found with given ISBN.");
        }
        System.out.println();
    }

    // --------------------- SORTING ----------------------
    public void sortBooksMenu() {
        System.out.println("1. Sort by Publication Year");
        System.out.println("2. Sort by Issue Count (Popularity)");
        int ch = InputUtil.readInt("Enter choice: ");
        ArrayList<Book> list = new ArrayList<>(books.values());
        if (ch == 1) {
            list.sort(Comparator.comparingInt(Book::getPublicationYear));
        } else if (ch == 2) {
            list.sort(Comparator.comparingInt(Book::getIssueCount).reversed());
        } else {
            System.out.println("Invalid choice.");
            return;
        }
        System.out.println("\nSorted books:");
        for (Book b : list) {
            System.out.println("ISBN: " + b.getIsbn() +
                    ", Title: " + b.getTitle() +
                    ", Year: " + b.getPublicationYear() +
                    ", Issued: " + b.getIssueCount() +
                    ", Rack: " + isbnToRack.getOrDefault(b.getIsbn(), -1));
        }
        System.out.println();
    }

    // --------------------- MEMBER ACTIONS -> REQUESTS ----------------------
    public void requestBorrowBook() {
        if (!isMember()) { System.out.println("Only Student/Faculty can request borrow."); return; }
        String isbn = InputUtil.readString("Enter ISBN: ");
        int quantity = InputUtil.readInt("Enter Number of Copies: ");
        String issueDate = InputUtil.readDate("Enter Issue Date (DD-MM-YYYY): ");

        ServiceRequest req = new ServiceRequest(
                nextRequestId++,
                RequestType.BORROW,
                currentMemberId,
                isbn,
                0,
                quantity,
                issueDate,
                issueDate,
                RequestStatus.PENDING,
                "",
                "",
                ""
        );
        serviceRequests.add(req);
        CSVUtil.rewriteRequests(serviceRequests);
        showNotification("Borrow request submitted. RequestID: " + req.getRequestId() + " (Pending staff approval)");
    }

    public void requestReturnBook() {
        if (!isMember()) { 
            System.out.println("Only Student/Faculty can request return."); 
            return; 
        }

        int borrowId = InputUtil.readInt("Enter Borrow ID: ");
        BorrowRecord record = findBorrowById(borrowId);

        if (record == null) {
            System.out.println("Invalid Borrow ID. No such borrow record exists.");
            return;
        }

        if (record.getMemberId() != currentMemberId) {
            System.out.println("This Borrow ID does not belong to you.");
            return;
        }

        if (record.getRemainingQuantity() == 0) {
            System.out.println("All copies already returned.");
            return;
        }

        int qty = InputUtil.readInt("Enter Number of Copies Returning: ");

        if (qty <= 0 || qty > record.getRemainingQuantity()) {
            System.out.println("Invalid quantity. Remaining copies: " + record.getRemainingQuantity());
            return;
        }

        String returnDate = InputUtil.readDate("Enter Return Date (DD-MM-YYYY): ");

        ServiceRequest req = new ServiceRequest(
                nextRequestId++,
                RequestType.RETURN,
                currentMemberId,
                "",
                borrowId,
                qty,
                returnDate,
                returnDate,
                RequestStatus.PENDING,
                "",
                "",
                ""
        );

        serviceRequests.add(req);
        CSVUtil.rewriteRequests(serviceRequests);

        showNotification("Return request submitted. RequestID: " + req.getRequestId());
    }

    public void requestRenewBook() {
        if (!isMember()) { System.out.println("Only Student/Faculty can request renew."); return; }
        String isbn = InputUtil.readString("Enter ISBN: ");
        String todayDate = InputUtil.readDate("Enter today's date (DD-MM-YYYY): ");

        ServiceRequest req = new ServiceRequest(
                nextRequestId++,
                RequestType.RENEW,
                currentMemberId,
                isbn,
                0,
                0,
                todayDate,
                todayDate,
                RequestStatus.PENDING,
                "",
                "",
                ""
        );
        serviceRequests.add(req);
        CSVUtil.rewriteRequests(serviceRequests);
        showNotification("Renew request submitted. RequestID: " + req.getRequestId() + " (Pending staff approval)");
    }

    public void requestReserveBook() {
        if (!isMember()) { System.out.println("Only Student/Faculty can request reserve."); return; }
        String isbn = InputUtil.readString("Enter ISBN: ");
        String reservationDate = InputUtil.readDate("Enter Reservation Date (DD-MM-YYYY): ");

        ServiceRequest req = new ServiceRequest(
                nextRequestId++,
                RequestType.RESERVE,
                currentMemberId,
                isbn,
                0,
                0,
                reservationDate,
                reservationDate,
                RequestStatus.PENDING,
                "",
                "",
                ""
        );
        serviceRequests.add(req);
        CSVUtil.rewriteRequests(serviceRequests);
        showNotification("Reservation request submitted. RequestID: " + req.getRequestId() + " (Pending staff approval)");
    }

    public void requestAssignReadingRoom() {
        if (!isMember()) { System.out.println("Only Student/Faculty can request reading room."); return; }
        String date = InputUtil.readDate("Enter Request Date (DD-MM-YYYY): ");
        ServiceRequest req = new ServiceRequest(
                nextRequestId++,
                RequestType.READING_ROOM_ASSIGN,
                currentMemberId,
                "",
                0,
                0,
                date,
                date,
                RequestStatus.PENDING,
                "",
                "",
                ""
        );
        serviceRequests.add(req);
        CSVUtil.rewriteRequests(serviceRequests);
        showNotification("Reading-room assign request submitted. RequestID: " + req.getRequestId());
    }

    public void requestReleaseReadingRoom() {
        if (!isMember()) { System.out.println("Only Student/Faculty can request reading room release."); return; }
        String date = InputUtil.readDate("Enter Request Date (DD-MM-YYYY): ");
        ServiceRequest req = new ServiceRequest(
                nextRequestId++,
                RequestType.READING_ROOM_RELEASE,
                currentMemberId,
                "",
                0,
                0,
                date,
                date,
                RequestStatus.PENDING,
                "",
                "",
                ""
        );
        serviceRequests.add(req);
        CSVUtil.rewriteRequests(serviceRequests);
        showNotification("Reading-room release request submitted. RequestID: " + req.getRequestId());
    }

    public void viewMyRequests() {
        if (!isMember()) { System.out.println("Only members can view this."); return; }
        System.out.println("\n===== MY REQUESTS =====");
        for (ServiceRequest r : serviceRequests) {
            if (r.getMemberId() != currentMemberId) continue;
            System.out.println(formatRequest(r));
        }
        System.out.println("=======================\n");
    }

    public void viewMyBorrows() {
        if (!isMember()) { 
            System.out.println("Only members can view this."); 
            return; 
        }

        System.out.println("\n===== MY BORROW RECORDS =====");

        boolean found = false;

        for (BorrowRecord r : borrowHistory) {
            if (r.getMemberId() == currentMemberId) {
                found = true;

                System.out.println(
                    "BorrowID: " + r.getBorrowId() +
                    " | ISBN: " + r.getIsbn() +
                    " | Issued: " + r.getIssueDate() +
                    " | Due: " + r.getDueDate() +
                    " | Remaining: " + r.getRemainingQuantity() +
                    " | Returned: " + (r.getReturnDate()==null || r.getReturnDate().isEmpty() ? "NO" : r.getReturnDate()) +
                    " | Fine: ₹" + r.getFine()
                );
            }
        }

        if (!found) {
            System.out.println("No borrow records found.");
        }

        System.out.println("=============================\n");
    }

    // --------------------- STAFF: APPROVE REQUESTS ----------------------
    public void viewPendingRequests() {
        if (!isStaff()) { System.out.println("Only staff can view pending requests."); return; }
        System.out.println("\n===== PENDING REQUESTS =====");
        boolean any = false;
        for (ServiceRequest r : serviceRequests) {
            if (r.getStatus() == RequestStatus.PENDING) {
                any = true;
                System.out.println(formatRequest(r));
            }
        }
        if (!any) System.out.println("No pending requests.");
        System.out.println("============================\n");
    }

    public void viewAllRequests() {
        if (!isStaff()) { System.out.println("Only staff can view all requests."); return; }

        System.out.println("\n===== ALL REQUESTS =====");
        System.out.println("1. All");
        System.out.println("2. Pending only");
        System.out.println("3. Approved only");
        System.out.println("4. Rejected only");
        int ch = InputUtil.readInt("Enter choice: ");

        RequestStatus filter = null;
        if (ch == 2) filter = RequestStatus.PENDING;
        else if (ch == 3) filter = RequestStatus.APPROVED;
        else if (ch == 4) filter = RequestStatus.REJECTED;
        else if (ch != 1) { System.out.println("Invalid choice."); return; }

        ArrayList<ServiceRequest> list = new ArrayList<>(serviceRequests);
        list.sort(Comparator.comparingInt(ServiceRequest::getRequestId));

        boolean any = false;
        for (ServiceRequest r : list) {
            if (filter != null && r.getStatus() != filter) continue;
            any = true;
            System.out.println(formatRequest(r));
        }

        if (!any) System.out.println("No requests for this filter.");
        System.out.println("======================\n");
    }

    public void processRequest() {
        if (!isStaff()) { System.out.println("Only staff can approve/reject requests."); return; }
        int requestId = InputUtil.readInt("Enter Request ID to process: ");
        ServiceRequest req = findRequestById(requestId);
        if (req == null) {
            System.out.println("Request not found.");
            return;
        }
        if (req.getStatus() != RequestStatus.PENDING) {
            System.out.println("Request already processed: " + req.getStatus());
            return;
        }

        System.out.println("Request: " + formatRequest(req));
        System.out.println("1. Approve");
        System.out.println("2. Reject");
        int ch = InputUtil.readInt("Enter choice: ");
        if (ch == 1) {
            try {
                approveRequest(req);
                req.setStatus(RequestStatus.APPROVED);
                req.setProcessedBy(currentStaffUser);
                req.setProcessedDate(req.getActionDate());
                req.setNote("APPROVED");
                CSVUtil.rewriteRequests(serviceRequests);
                showNotification("Request " + req.getRequestId() + " approved.");
            } catch (Exception e) {
                // approval failed -> reject with reason
                req.setStatus(RequestStatus.REJECTED);
                req.setProcessedBy(currentStaffUser);
                req.setProcessedDate(req.getActionDate());
                req.setNote(e.getMessage());
                CSVUtil.rewriteRequests(serviceRequests);
                System.out.println("Approval failed. Request rejected. Reason: " + e.getMessage());
            }
        } else if (ch == 2) {
            String note = InputUtil.readString("Reject reason: ");
            req.setStatus(RequestStatus.REJECTED);
            req.setProcessedBy(currentStaffUser);
            req.setProcessedDate(req.getActionDate());
            req.setNote(note);
            CSVUtil.rewriteRequests(serviceRequests);
            showNotification("Request " + req.getRequestId() + " rejected.");
        } else {
            System.out.println("Invalid choice.");
        }
    }

    private void approveRequest(ServiceRequest req)
            throws InvalidMemberException, BookNotAvailableException, BorrowLimitExceededException, ReadingRoomFullException, MemberAlreadyAssignedException {
        switch (req.getType()) {
            case BORROW -> approveBorrow(req);
            case RETURN -> approveReturn(req);
            case RENEW -> approveRenew(req);
            case RESERVE -> approveReserve(req);
            case READING_ROOM_ASSIGN -> approveReadingRoomAssign(req);
            case READING_ROOM_RELEASE -> approveReadingRoomRelease(req);
            default -> throw new IllegalArgumentException("Unknown request type.");
        }
    }

    private void approveBorrow(ServiceRequest req)
            throws InvalidMemberException, BookNotAvailableException, BorrowLimitExceededException {

        int memberId = req.getMemberId();
        String isbn = req.getIsbn();
        int quantity = req.getQuantity();
        String issueDate = req.getActionDate();

        // Validate quantity
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        // Validate member
        Member member = validateMember(memberId);

        // Validate book
        Book book = validateBookAvailable(isbn);

        // Check available copies
        if (book.getAvailableCopies() < quantity) {
            throw new BookNotAvailableException(
                    "Not enough copies available. Available copies: " + book.getAvailableCopies());
        }

        // Check borrow limit
        int currentBorrowed = countActiveBorrows(memberId);
        if (currentBorrowed + quantity > member.getBorrowLimit()) {
            throw new BorrowLimitExceededException(
                    "Borrow limit exceeded for Member ID: " + memberId +
                    ". Limit: " + member.getBorrowLimit());
        }

        // Determine allowed days based on role
        int allowedDays = getAllowedDays(member);

        // Calculate due date
        String dueDate = addDays(issueDate, allowedDays);

        // Ensure rack assignment
        int rackNo = ensureRackForIsbn(isbn);
        Book rackBook = books.get(isbn);
        if (rackBook != null) {
            rackBook.setRackNo(rackNo);
        }

        // Update book availability
        book.setAvailableCopies(book.getAvailableCopies() - quantity);
        book.setIssueCount(book.getIssueCount() + quantity);

        // Generate borrow record
        int borrowId = nextBorrowId++;

        BorrowRecord record = new BorrowRecord(
                borrowId,
                memberId,
                isbn,
                quantity,
                quantity,
                issueDate,
                dueDate,
                "",
                0,
                false
        );

        // Store record
        borrowHistory.add(record);
        actionStack.push(record);

        // Save to CSV
        CSVUtil.rewriteBorrowRecords(borrowHistory);
        CSVUtil.rewriteBooks(new ArrayList<>(books.values()),
                (x) -> isbnToRack.getOrDefault(x, -1));

        // Display result
        System.out.println("\n----- BORROW APPROVED -----");
        System.out.println("Borrow ID: " + borrowId);
        System.out.println("Member ID: " + memberId);
        System.out.println("ISBN: " + isbn);
        System.out.println("Quantity: " + quantity);
        System.out.println("Issue Date: " + issueDate);
        System.out.println("Due Date: " + dueDate);
        System.out.println("Rack Number: " + rackNo);
        System.out.println("---------------------------");

        showNotification("Borrow approved successfully.\nBorrow ID: "
                + borrowId + " | Due Date: " + dueDate);
    }

    private void approveReturn(ServiceRequest req) throws InvalidMemberException {
        int borrowId = req.getBorrowId();
        int quantityReturning = req.getQuantity();
        String returnDate = req.getActionDate();

        BorrowRecord target = findBorrowById(borrowId);
        if (target == null) throw new IllegalArgumentException("Borrow record not found for Borrow ID: " + borrowId);

        // Security: member must match request
        if (target.getMemberId() != req.getMemberId()) throw new IllegalArgumentException("BorrowID does not belong to requesting member.");

        if (quantityReturning <= 0 || quantityReturning > target.getRemainingQuantity()) {
            throw new IllegalArgumentException("Invalid quantity. Remaining: " + target.getRemainingQuantity());
        }

        int daysUsed = daysBetween(target.getIssueDate(), returnDate);
        if (daysUsed < 0) throw new IllegalArgumentException("Return date cannot be before issue date.");

        int allowedDays = daysBetween(target.getIssueDate(), target.getDueDate());
        int lateDays = Math.max(0, daysUsed - allowedDays);

        Member member = members.get(target.getMemberId());
        if (member == null) throw new InvalidMemberException("Member not found: " + target.getMemberId());

        Book book = books.get(target.getIsbn());
        if (book == null) throw new IllegalArgumentException("Book not found: " + target.getIsbn());

        int finePerDay = "Faculty".equalsIgnoreCase(member.getRole()) ? 2 : 5;
        int fineForThisReturn = lateDays * finePerDay * quantityReturning;

        int newRemaining = target.getRemainingQuantity() - quantityReturning;
        target.setRemainingQuantity(newRemaining);

        if (newRemaining == 0) target.setReturnDate(returnDate);

        target.setFine(target.getFine() + fineForThisReturn);
        actionStack.push(target);

        // Return increases availability, but reservations may consume immediately
        book.setAvailableCopies(book.getAvailableCopies() + quantityReturning);
        // Increase available copies
        book.setAvailableCopies(book.getAvailableCopies() + quantityReturning);
        
        // Get rack number where book belongs
        int rackNo = isbnToRack.getOrDefault(book.getIsbn(), -1);
        
        // Message to staff about where to place the book
        System.out.println("\n===== STAFF NOTICE =====");
        System.out.println("Returned book should be placed in Rack Number: " + rackNo);
        System.out.println("Book Title: " + book.getTitle());
        System.out.println("Available Copies Updated: " + book.getAvailableCopies());
        System.out.println("========================");

        // Reservation handling: auto-issue from available copies (if any)
        handleReservationOnReturn(book.getIsbn(), returnDate);

        CSVUtil.rewriteBorrowRecords(borrowHistory);
        CSVUtil.rewriteBooks(new ArrayList<>(books.values()), (x) -> isbnToRack.getOrDefault(x, -1));

        // total fine for member
        int totalFineForMember = 0;
        for (BorrowRecord r : borrowHistory) if (r.getMemberId() == member.getMemberId()) totalFineForMember += r.getFine();

        System.out.println("\n--- Return Approved ---");
        System.out.println("Borrow ID: " + borrowId);
        System.out.println("Returned Quantity: " + quantityReturning);
        System.out.println("Remaining for this borrow: " + newRemaining);
        System.out.println("Late Days: " + lateDays);
        System.out.println("Fine for this return: ₹" + fineForThisReturn);
        System.out.println("Total Fine for Member: ₹" + totalFineForMember);

        if (fineForThisReturn > 0) showNotification("Fine pending: ₹" + fineForThisReturn);
        showNotification("Return approved for BorrowID: " + borrowId);
    }

    private void approveRenew(ServiceRequest req) {
        int memberId = req.getMemberId();
        String isbn = req.getIsbn();
        String todayDate = req.getActionDate();

        BorrowRecord target = null;
        for (BorrowRecord r : borrowHistory) {
            if (r.getMemberId() == memberId &&
                    safe(r.getIsbn()).equals(isbn) &&
                    (r.getReturnDate() == null || r.getReturnDate().isEmpty()) &&
                    r.getRemainingQuantity() > 0) {
                target = r;
                break;
            }
        }
        if (target == null) throw new IllegalArgumentException("Active borrow record not found for this member and book.");

        int daysFromDue = daysBetween(target.getDueDate(), todayDate);
        if (daysFromDue > 0) throw new IllegalArgumentException("Cannot renew. Due date has already passed.");

        PriorityQueue<Member> queue = reservationQueues.get(isbn);
        if (queue != null && !queue.isEmpty()) throw new IllegalArgumentException("Cannot renew. Reservation exists for this book.");

        Member member = members.get(memberId);
        if (member == null) throw new IllegalArgumentException("Member not found.");

        int allowedDays = getAllowedDays(member);
        String newDueDate = addDays(todayDate, allowedDays);

        target.setDueDate(newDueDate);
        CSVUtil.rewriteBorrowRecords(borrowHistory);

        System.out.println("Renewal approved. New due date: " + newDueDate);
        showNotification("Renewal approved for ISBN " + isbn + ". New due date: " + newDueDate);
    }

    private void approveReserve(ServiceRequest req) {
        int memberId = req.getMemberId();
        String isbn = req.getIsbn();
        String reservationDate = req.getActionDate();

        Member member = members.get(memberId);
        Book book = books.get(isbn);
        if (member == null || book == null) throw new IllegalArgumentException("Invalid member or book.");

        if (book.getAvailableCopies() > 0) throw new IllegalArgumentException("Book is available. No need to reserve.");

        reservationQueues.computeIfAbsent(isbn, k ->
                new PriorityQueue<>(Comparator.comparingInt(m -> "HIGH".equalsIgnoreCase(m.getPriority()) ? 0 : 1))
        );
        reservationQueues.get(isbn).offer(member);
        reservationsHistory.add(new Reservation(memberId, isbn, reservationDate));
        System.out.println("Reservation approved for Member " + memberId + " and ISBN " + isbn);
        showNotification("Reservation approved. You will be auto-assigned when available.");
    }

    private void approveReadingRoomAssign(ServiceRequest req)
            throws ReadingRoomFullException, MemberAlreadyAssignedException, InvalidMemberException {
        int memberId = req.getMemberId();
        Member member = validateMember(memberId);
        if (isMemberInAnyRoom(memberId)) throw new MemberAlreadyAssignedException("Member already assigned to a reading room.");

        ReadingRoom room = allocateSeatInReadingRoomCircular(memberId, member.getRole());
        System.out.println("Assigned to " + room.getRoomType() + " Reading Room Number: " +
                room.getRoomNumber() + " (Capacity: " + room.getCapacity() + ")");
        showNotification("Reading room seat approved. Room " + room.getRoomType() + "-" + room.getRoomNumber());
    }

    private void approveReadingRoomRelease(ServiceRequest req) {
        int memberId = req.getMemberId();

        ReadingRoom roomFound = null;
        String roleFound = null;

        for (ReadingRoom room : facultyRooms) {
            if (room.getMemberIds().remove((Integer) memberId)) {
                roomFound = room;
                roleFound = "Faculty";
                break;
            }
        }
        if (roomFound == null) {
            for (ReadingRoom room : studentRooms) {
                if (room.getMemberIds().remove((Integer) memberId)) {
                    roomFound = room;
                    roleFound = "Student";
                    break;
                }
            }
        }

        if (roomFound == null) throw new IllegalArgumentException("Member is not currently in any reading room.");

        Integer nextId = "Faculty".equalsIgnoreCase(roleFound) ? facultyWaitingQueue.poll() : studentWaitingQueue.poll();
        if (nextId != null) roomFound.getMemberIds().add(nextId);

        System.out.println("Reading room release approved for member " + memberId + ". Freed seat in " +
                roleFound + " Room " + roomFound.getRoomNumber());
        showNotification("Reading room release approved.");
    }

    // --------------------- REPORTS / NOTIFICATIONS / ANALYTICS ----------------------
    public void generateDailyReport() {
        if (!isStaff()) { System.out.println("Only staff can generate reports."); return; }

        String reportDate = InputUtil.readDate("Enter Report Date (DD-MM-YYYY): ");

        ArrayList<Member> membersAdded = new ArrayList<>();
        ArrayList<BorrowRecord> borrowedToday = new ArrayList<>();
        ArrayList<BorrowRecord> returnedToday = new ArrayList<>();
        ArrayList<BorrowRecord> finesToday = new ArrayList<>();
        ArrayList<Reservation> reservationsToday = new ArrayList<>();

        int totalFineToday = 0;

        for (Member m : members.values()) {
            if (reportDate.equals(m.getRegistrationDate())) membersAdded.add(m);
        }

        for (BorrowRecord r : borrowHistory) {
            if (reportDate.equals(r.getIssueDate())) borrowedToday.add(r);

            if (r.getReturnDate() != null && !r.getReturnDate().isEmpty() && reportDate.equals(r.getReturnDate())) {
                returnedToday.add(r);
                if (r.getFine() > 0) {
                    finesToday.add(r);
                    totalFineToday += r.getFine();
                }
            }
        }

        for (Reservation res : reservationsHistory) {
            if (reportDate.equals(res.getReservationDate())) reservationsToday.add(res);
        }

        System.out.println("\n================ DAILY REPORT ================");
        System.out.println("Date: " + reportDate);

        System.out.println("\nMembers Added Today");
        System.out.println("------------------------------------------------");
        System.out.printf("%-10s %-20s %-10s\n","MemberID","Name","Role");
        System.out.println("------------------------------------------------");
        for (Member m : membersAdded) {
            System.out.printf("%-10d %-20s %-10s\n", m.getMemberId(), m.getName(), m.getRole());
            CSVUtil.appendDailyReport(reportDate, String.valueOf(m.getMemberId()), m.getName(), m.getRole(),
                    "", "", "", "", "", "", "", "NEW_MEMBER");
        }

        System.out.println("\nBooks Borrowed Today");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("%-10s %-10s %-15s %-8s %-12s\n", "BorrowID","MemberID","ISBN","Qty","IssueDate");
        System.out.println("----------------------------------------------------------------");
        for (BorrowRecord r : borrowedToday) {
            Member m = members.get(r.getMemberId());
            System.out.printf("%-10d %-10d %-15s %-8d %-12s\n",
                    r.getBorrowId(), r.getMemberId(), r.getIsbn(), r.getQuantity(), r.getIssueDate());
            CSVUtil.appendDailyReport(reportDate, String.valueOf(r.getMemberId()), m == null ? "" : m.getName(),
                    m == null ? "" : m.getRole(), String.valueOf(r.getBorrowId()), r.getIsbn(),
                    String.valueOf(r.getQuantity()), r.getIssueDate(), "", "0", "", "BORROW");
        }

        System.out.println("\nBooks Returned Today");
        System.out.println("-----------------------------------------------------------------------");
        System.out.printf("%-10s %-10s %-15s %-8s %-12s %-6s\n", "BorrowID","MemberID","ISBN","Qty","ReturnDate","Fine");
        System.out.println("-----------------------------------------------------------------------");
        for (BorrowRecord r : returnedToday) {
            Member m = members.get(r.getMemberId());
            int returnedQty = r.getQuantity() - r.getRemainingQuantity();
            System.out.printf("%-10d %-10d %-15s %-8d %-12s %-6d\n",
                    r.getBorrowId(), r.getMemberId(), r.getIsbn(), returnedQty, r.getReturnDate(), r.getFine());
            CSVUtil.appendDailyReport(reportDate, String.valueOf(r.getMemberId()), m == null ? "" : m.getName(),
                    m == null ? "" : m.getRole(), String.valueOf(r.getBorrowId()), r.getIsbn(),
                    String.valueOf(returnedQty), r.getIssueDate(), r.getReturnDate(), String.valueOf(r.getFine()), "", "RETURN");
        }

        System.out.println("\nReservations Today");
        System.out.println("---------------------------------------------");
        System.out.printf("%-10s %-15s %-12s\n","MemberID","ISBN","Date");
        System.out.println("---------------------------------------------");
        for (Reservation res : reservationsToday) {
            System.out.printf("%-10d %-15s %-12s\n", res.getMemberId(), res.getIsbn(), res.getReservationDate());
            Member m = members.get(res.getMemberId());
            CSVUtil.appendDailyReport(reportDate, String.valueOf(res.getMemberId()), m == null ? "" : m.getName(),
                    m == null ? "" : m.getRole(), "", res.getIsbn(), "", "", "", "", res.getReservationDate(), "RESERVE");
        }

        System.out.println("\nFines Collected Today");
        System.out.println("------------------------------------");
        System.out.printf("%-10s %-10s %-10s\n","MemberID","BorrowID","Fine");
        System.out.println("------------------------------------");
        for (BorrowRecord r : finesToday) {
            System.out.printf("%-10d %-10d %-10d\n", r.getMemberId(), r.getBorrowId(), r.getFine());
            Member m = members.get(r.getMemberId());
            CSVUtil.appendDailyReport(reportDate, String.valueOf(r.getMemberId()), m == null ? "" : m.getName(),
                    m == null ? "" : m.getRole(), String.valueOf(r.getBorrowId()), r.getIsbn(),
                    "", "", r.getReturnDate(), String.valueOf(r.getFine()), "", "FINE");
        }

        System.out.println("\nTotal Fine Collected Today: ₹" + totalFineToday);
        System.out.println("==============================================\n");
    }

    public void showDueTomorrowNotifications() {
        String tomorrow = InputUtil.readDate("Enter tomorrow's date (DD-MM-YYYY): ");
        boolean found = false;
        System.out.println("\n******** DUE TOMORROW NOTIFICATIONS ********");
        for (BorrowRecord r : borrowHistory) {
            if ((r.getReturnDate() == null || r.getReturnDate().isEmpty()) && safe(r.getDueDate()).equals(tomorrow)) {
                System.out.println("Member ID: " + r.getMemberId() + " | ISBN: " + r.getIsbn() + " | Due Date: " + r.getDueDate());
                found = true;
            }
        }
        if (!found) System.out.println("No books due tomorrow.");
        System.out.println("*******************************************\n");
    }

    public void showTopKBorrowedBooks() {
        int k = InputUtil.readInt("Enter K (Top books): ");
        ArrayList<Book> list = new ArrayList<>(books.values());
        list.sort(Comparator.comparingInt(Book::getIssueCount).reversed());
        System.out.println("\nTop " + k + " Borrowed Books:");
        for (int i = 0; i < Math.min(k, list.size()); i++) {
            Book b = list.get(i);
            System.out.println((i + 1) + ". " + b.getTitle() + " | ISBN: " + b.getIsbn() + " | Borrowed: " + b.getIssueCount());
        }
        System.out.println();
    }

    public void showMostActiveMembers() {
        HashMap<Integer, Integer> memberBorrowCount = new HashMap<>();
        for (BorrowRecord r : borrowHistory) {
            memberBorrowCount.put(r.getMemberId(), memberBorrowCount.getOrDefault(r.getMemberId(), 0) + r.getQuantity());
        }
        ArrayList<Integer> memberIds = new ArrayList<>(memberBorrowCount.keySet());
        memberIds.sort((a, b) -> memberBorrowCount.get(b) - memberBorrowCount.get(a));
        System.out.println("\nMost Active Members:");
        for (Integer id : memberIds) {
            Member m = members.get(id);
            if (m == null) continue;
            System.out.println("Member ID: " + id + " | Name: " + m.getName() + " | Books Borrowed: " + memberBorrowCount.get(id));
        }
        System.out.println();
    }

    public void undoLastAction() {
        if (!isStaff()) { System.out.println("Only staff can undo approved actions."); return; }
        if (actionStack.isEmpty()) {
            System.out.println("No actions to undo.");
            return;
        }

        BorrowRecord last = actionStack.pop();
        Book book = books.get(last.getIsbn());
        Member member = members.get(last.getMemberId());
        if (book == null || member == null) {
            System.out.println("Undo failed: missing book/member.");
            return;
        }
        int finePerDay = "Faculty".equalsIgnoreCase(member.getRole()) ? 2 : 5;

        // UNDO BORROW (not partially returned)
        if (last.getRemainingQuantity() == last.getQuantity() && (last.getReturnDate() == null || last.getReturnDate().isEmpty())) {
            borrowHistory.remove(last);
            book.setAvailableCopies(book.getAvailableCopies() + last.getQuantity());
            book.setIssueCount(Math.max(0, book.getIssueCount() - last.getQuantity()));
            System.out.println("Undo successful: Borrow operation cancelled.");
        } else {
            int returnedQty = last.getQuantity() - last.getRemainingQuantity();
            String originalReturnDate = last.getReturnDate();
            if (originalReturnDate == null || originalReturnDate.isEmpty()) originalReturnDate = last.getDueDate();

            int daysUsed = daysBetween(last.getIssueDate(), originalReturnDate);
            int allowedDays = daysBetween(last.getIssueDate(), last.getDueDate());
            int lateDays = Math.max(0, daysUsed - allowedDays);

            int memberFineReduction = lateDays * finePerDay * returnedQty;
            last.setFine(Math.max(0, last.getFine() - memberFineReduction));
            last.setRemainingQuantity(last.getQuantity());
            last.setReturnDate("");
            book.setAvailableCopies(Math.max(0, book.getAvailableCopies() - returnedQty));
            System.out.println("Undo successful: Return operation cancelled for " + returnedQty + " copies.");
        }

        CSVUtil.rewriteBorrowRecords(borrowHistory);
        CSVUtil.rewriteBooks(new ArrayList<>(books.values()), (x) -> isbnToRack.getOrDefault(x, -1));
    }

    // --------------------- INTERNAL HELPERS ----------------------
    private boolean isStaff() { return "Staff".equalsIgnoreCase(currentUserRole); }
    private boolean isMember() { return "Student".equalsIgnoreCase(currentUserRole) || "Faculty".equalsIgnoreCase(currentUserRole); }

    private Member validateMember(int memberId) throws InvalidMemberException {
        Member member = members.get(memberId);
        if (member == null) throw new InvalidMemberException("Member not found: " + memberId);
        return member;
    }

    private Book validateBookAvailable(String isbn) throws BookNotAvailableException {
        Book book = books.get(isbn);
        if (book == null) throw new BookNotAvailableException("Book not found: " + isbn);
        if (book.getAvailableCopies() <= 0) throw new BookNotAvailableException("Book not available: " + isbn);
        return book;
    }

    private int countActiveBorrows(int memberId) {
        int count = 0;
        for (BorrowRecord r : borrowHistory) {
            if (r.getMemberId() == memberId && r.getRemainingQuantity() > 0) count += r.getRemainingQuantity();
        }
        return count;
    }

    private int getAllowedDays(Member m) {
        return "Faculty".equalsIgnoreCase(m.getRole()) ? 10 : 5;
    }

    private BorrowRecord findBorrowById(int borrowId) {
        for (BorrowRecord r : borrowHistory) if (r.getBorrowId() == borrowId) return r;
        return null;
    }

    private ServiceRequest findRequestById(int requestId) {
        for (ServiceRequest r : serviceRequests) if (r.getRequestId() == requestId) return r;
        return null;
    }

    private String formatRequest(ServiceRequest r) {
        return "RequestID: " + r.getRequestId() +
                " | Type: " + (r.getType() == null ? "" : r.getType().name()) +
                " | MemberID: " + r.getMemberId() +
                " | ISBN: " + safe(r.getIsbn()) +
                " | BorrowID: " + r.getBorrowId() +
                " | Qty: " + r.getQuantity() +
                " | Date: " + safe(r.getRequestDate()) +
                " | Status: " + (r.getStatus() == null ? "" : r.getStatus().name()) +
                (safe(r.getNote()).isEmpty() ? "" : " | Note: " + r.getNote());
    }

    private String formatBorrow(BorrowRecord r) {
        return "BorrowID: " + r.getBorrowId() +
                " | ISBN: " + r.getIsbn() +
                " | Qty: " + r.getQuantity() +
                " | Remaining: " + r.getRemainingQuantity() +
                " | Issue: " + r.getIssueDate() +
                " | Due: " + r.getDueDate() +
                " | Return: " + (safe(r.getReturnDate()).isEmpty() ? "-" : r.getReturnDate()) +
                " | Fine: ₹" + r.getFine();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // --------------------- DATE UTILITIES (NO java.time) ----------------------
    private String addDays(String date, int daysToAdd) {
        int[] d = parseDate(date);
        int day = d[0];
        int month = d[1];
        int year = d[2];

        day += daysToAdd;
        while (true) {
            int daysInMonth = daysInMonth(month, year);
            if (day <= daysInMonth) break;
            day -= daysInMonth;
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
        return formatDate(day, month, year);
    }

    private int daysBetween(String start, String end) {
        int[] s = parseDate(start);
        int[] e = parseDate(end);
        int startDays = toAbsoluteDays(s[0], s[1], s[2]);
        int endDays = toAbsoluteDays(e[0], e[1], e[2]);
        return endDays - startDays;
    }

    private int[] parseDate(String date) {
        String[] parts = date.split("-");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = Integer.parseInt(parts[2]);
        return new int[]{day, month, year};
    }

    private String formatDate(int day, int month, int year) {
        String d = (day < 10 ? "0" : "") + day;
        String m = (month < 10 ? "0" : "") + month;
        return d + "-" + m + "-" + year;
    }

    private int toAbsoluteDays(int day, int month, int year) {
        int days = day;
        for (int y = 1; y < year; y++) days += isLeapYear(y) ? 366 : 365;
        for (int m = 1; m < month; m++) days += daysInMonth(m, year);
        return days;
    }

    private int daysInMonth(int month, int year) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> isLeapYear(year) ? 29 : 28;
            default -> 30;
        };
    }

    private boolean isLeapYear(int year) {
        if (year % 400 == 0) return true;
        if (year % 100 == 0) return false;
        return year % 4 == 0;
    }

    // --------------------- NOTIFICATIONS ----------------------
    public void showNotification(String message) {
        System.out.println("\n******** NOTIFICATION ********");
        System.out.println(message);
        System.out.println("******************************\n");
    }

    // --------------------- READING ROOM INTERNALS ----------------------
    private ReadingRoom allocateSeatInReadingRoomCircular(int memberId, String role) throws ReadingRoomFullException {
        if ("Faculty".equalsIgnoreCase(role)) {
            for (int i = 0; i < facultyRooms.length; i++) {
                ReadingRoom room = facultyRooms[facultyRoomIndex];
                facultyRoomIndex = (facultyRoomIndex + 1) % FACULTY_ROOM_COUNT;
                if (room.getMemberIds().size() < room.getCapacity()) {
                    room.getMemberIds().add(memberId);
                    return room;
                }
            }
            facultyWaitingQueue.offer(memberId);
            throw new ReadingRoomFullException("All Faculty rooms are full. Added to waiting queue.");
        } else {
            for (int i = 0; i < studentRooms.length; i++) {
                ReadingRoom room = studentRooms[studentRoomIndex];
                studentRoomIndex = (studentRoomIndex + 1) % STUDENT_ROOM_COUNT;
                if (room.getMemberIds().size() < room.getCapacity()) {
                    room.getMemberIds().add(memberId);
                    return room;
                }
            }
            studentWaitingQueue.offer(memberId);
            throw new ReadingRoomFullException("All Student rooms are full. Added to waiting queue.");
        }
    }

    private boolean isMemberInAnyRoom(int memberId) {
        for (ReadingRoom room : facultyRooms) if (room.getMemberIds().contains(memberId)) return true;
        for (ReadingRoom room : studentRooms) if (room.getMemberIds().contains(memberId)) return true;
        return false;
    }

    // --------------------- RESERVATION ON RETURN ----------------------
    private void handleReservationOnReturn(String isbn, String returnDate) {
        Book book = books.get(isbn);
        PriorityQueue<Member> queue = reservationQueues.get(isbn);
        if (queue == null || queue.isEmpty() || book == null) return;

        // While there are available copies, auto-issue one copy per reserved member
        while (book.getAvailableCopies() > 0 && !queue.isEmpty()) {
            Member nextMember = queue.poll();
            if (nextMember == null) break;

            int quantity = 1;
            String issueDate = returnDate;
            int allowedDays = getAllowedDays(nextMember);
            String dueDate = addDays(issueDate, allowedDays);

            book.setAvailableCopies(book.getAvailableCopies() - quantity);
            book.setIssueCount(book.getIssueCount() + quantity);

            int borrowId = nextBorrowId++;
            BorrowRecord record = new BorrowRecord(
                    borrowId,
                    nextMember.getMemberId(),
                    isbn,
                    quantity,
                    quantity,
                    issueDate,
                    dueDate,
                    "",
                    0,
                    false
            );
            borrowHistory.add(record);
            actionStack.push(record);

            showNotification("Reserved ISBN " + isbn + " auto-issued to Member " + nextMember.getMemberId() +
                    ". BorrowID: " + borrowId + " Due: " + dueDate);
        }

        CSVUtil.rewriteBorrowRecords(borrowHistory);
        CSVUtil.rewriteBooks(new ArrayList<>(books.values()), (x) -> isbnToRack.getOrDefault(x, -1));
    }
}

