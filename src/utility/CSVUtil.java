package utility;

import models.Book;
import models.BorrowRecord;
import models.Member;
import models.RequestStatus;
import models.RequestType;
import models.ServiceRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CSVUtil {

    private static final Path BOOKS_FILE = Path.of("books.csv");
    private static final Path MEMBERS_FILE = Path.of("members.csv");
    private static final Path BORROW_FILE = Path.of("borrow_records.csv");
    private static final Path DAILY_REPORT_FILE = Path.of("daily_report.csv");
    private static final Path REQUESTS_FILE = Path.of("service_requests.csv");

    private static final String BOOKS_HEADER = "ISBN,Title,Author,PublicationYear,TotalCopies,AvailableCopies,IssueCount,RackNo";
    private static final String MEMBERS_HEADER = "MemberID,Name,Role,BorrowLimit,Priority,RegistrationDate";
    private static final String BORROW_HEADER = "BorrowID,MemberID,ISBN,Quantity,RemainingQuantity,IssueDate,DueDate,ReturnDate,Fine,PendingApproval";
    private static final String DAILY_REPORT_HEADER = "Date,MemberID,MemberName,Role,BorrowID,ISBN,Quantity,IssueDate,ReturnDate,Fine,ReservationDate,Action";
    private static final String REQUESTS_HEADER = "RequestID,Type,MemberID,ISBN,BorrowID,Quantity,RequestDate,ActionDate,Status,ProcessedBy,ProcessedDate,Note";

    private CSVUtil() {}

    public static Path booksFile() { return BOOKS_FILE; }
    public static Path membersFile() { return MEMBERS_FILE; }
    public static Path borrowFile() { return BORROW_FILE; }
    public static Path dailyReportFile() { return DAILY_REPORT_FILE; }
    public static Path requestsFile() { return REQUESTS_FILE; }

    public static void initializeFiles() {
        try {
            ensureFileWithHeader(BOOKS_FILE, BOOKS_HEADER);
            ensureFileWithHeader(MEMBERS_FILE, MEMBERS_HEADER);
            ensureFileWithHeader(BORROW_FILE, BORROW_HEADER);
            ensureFileWithHeader(DAILY_REPORT_FILE, DAILY_REPORT_HEADER);
            ensureFileWithHeader(REQUESTS_FILE, REQUESTS_HEADER);
        } catch (IOException e) {
            System.out.println("Error initializing CSV files: " + e.getMessage());
        }
    }

    public static void ensureFileWithHeader(Path file, String headerLine) throws IOException {
        if (Files.notExists(file)) {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, headerLine + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    // --------------------- Generic CSV helpers ---------------------
    public static List<String[]> readAll(Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        if (Files.notExists(file)) return rows;
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;
                rows.add(parseLine(line));
            }
        }
        return rows;
    }

    public static void overwrite(Path file, String headerLine, List<String[]> rows) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            bw.write(headerLine);
            bw.newLine();
            for (String[] r : rows) {
                bw.write(toLine(r));
                bw.newLine();
            }
        }
    }

    public static void append(Path file, String[] row) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, toLine(row) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static String toLine(String[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cols[i]));
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String v = value.replace("\"", "\"\"");
        return needsQuotes ? ("\"" + v + "\"") : v;
    }

    public static String[] parseLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        cur.append('\"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == ',') {
                    cols.add(cur.toString());
                    cur.setLength(0);
                } else if (c == '\"') {
                    inQuotes = true;
                } else {
                    cur.append(c);
                }
            }
        }
        cols.add(cur.toString());
        return cols.toArray(new String[0]);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // --------------------- Domain load/rewrite ---------------------
    public static List<Book> loadBooks() {
        List<Book> result = new ArrayList<>();
        try {
            for (String[] row : readAll(BOOKS_FILE)) {
                if (row.length < 7) continue;
                Book b = new Book();
                b.setIsbn(row[0]);
                b.setTitle(row[1]);
                b.setAuthor(row[2]);
                b.setPublicationYear(parseIntSafe(row[3]));
                b.setTotalCopies(parseIntSafe(row[4]));
                b.setAvailableCopies(parseIntSafe(row[5]));
                b.setIssueCount(parseIntSafe(row[6]));
                if (row.length >= 8) {
                    b.setRackNo(parseIntSafe(row[7]));
                } else {
                    b.setRackNo(-1);
                }
                if (!safe(b.getIsbn()).isBlank()) result.add(b);
            }
        } catch (IOException e) {
            System.out.println("Error loading books: " + e.getMessage());
        }
        return result;
    }

    public static List<Member> loadMembers() {
        List<Member> result = new ArrayList<>();
        try {
            for (String[] row : readAll(MEMBERS_FILE)) {
                if (row.length < 6) continue;
                Member m = new Member();
                m.setMemberId(parseIntSafe(row[0]));
                m.setName(row[1]);
                m.setRole(row[2]);
                m.setBorrowLimit(parseIntSafe(row[3]));
                m.setPriority(row[4]);
                m.setRegistrationDate(row[5]);
                if (m.getMemberId() > 0) result.add(m);
            }
        } catch (IOException e) {
            System.out.println("Error loading members: " + e.getMessage());
        }
        return result;
    }

    public static LinkedList<BorrowRecord> loadBorrowRecords() {
        LinkedList<BorrowRecord> result = new LinkedList<>();
        try {
            for (String[] row : readAll(BORROW_FILE)) {
                if (row.length < 10) continue;
                BorrowRecord r = new BorrowRecord();
                r.setBorrowId(parseIntSafe(row[0]));
                r.setMemberId(parseIntSafe(row[1]));
                r.setIsbn(row[2]);
                r.setQuantity(parseIntSafe(row[3]));
                r.setRemainingQuantity(parseIntSafe(row[4]));
                r.setIssueDate(row[5]);
                r.setDueDate(row[6]);
                r.setReturnDate(row[7]);
                r.setFine(parseIntSafe(row[8]));
                r.setPendingApproval(parseBooleanSafe(row[9]));
                if (r.getBorrowId() > 0) result.add(r);
            }
        } catch (IOException e) {
            System.out.println("Error loading borrow records: " + e.getMessage());
        }
        return result;
    }

    public static LinkedList<ServiceRequest> loadRequests() {
        LinkedList<ServiceRequest> result = new LinkedList<>();
        try {
            for (String[] row : readAll(REQUESTS_FILE)) {
                if (row.length < 12) continue;
                ServiceRequest r = new ServiceRequest();
                r.setRequestId(parseIntSafe(row[0]));
                r.setType(parseRequestTypeSafe(row[1]));
                r.setMemberId(parseIntSafe(row[2]));
                r.setIsbn(row[3]);
                r.setBorrowId(parseIntSafe(row[4]));
                r.setQuantity(parseIntSafe(row[5]));
                r.setRequestDate(row[6]);
                r.setActionDate(row[7]);
                r.setStatus(parseRequestStatusSafe(row[8]));
                r.setProcessedBy(row[9]);
                r.setProcessedDate(row[10]);
                r.setNote(row[11]);
                if (r.getRequestId() > 0) result.add(r);
            }
        } catch (IOException e) {
            System.out.println("Error loading requests: " + e.getMessage());
        }
        return result;
    }

    public static void rewriteBooks(List<Book> books, java.util.function.Function<String, Integer> rackLookup) {
        try {
            List<String[]> rows = new ArrayList<>();
            for (Book b : books) {
                Integer rackNo = rackLookup == null ? null : rackLookup.apply(b.getIsbn());
                if (rackNo == null) rackNo = b.getRackNo();
                rows.add(new String[]{
                        safe(b.getIsbn()),
                        safe(b.getTitle()),
                        safe(b.getAuthor()),
                        String.valueOf(b.getPublicationYear()),
                        String.valueOf(b.getTotalCopies()),
                        String.valueOf(b.getAvailableCopies()),
                        String.valueOf(b.getIssueCount()),
                        String.valueOf(rackNo == null ? -1 : rackNo)
                });
            }
            overwrite(BOOKS_FILE, BOOKS_HEADER, rows);
        } catch (IOException e) {
            System.out.println("Error rewriting books: " + e.getMessage());
        }
    }

    public static void rewriteMembers(List<Member> members) {
        try {
            List<String[]> rows = new ArrayList<>();
            for (Member m : members) {
                rows.add(new String[]{
                        String.valueOf(m.getMemberId()),
                        safe(m.getName()),
                        safe(m.getRole()),
                        String.valueOf(m.getBorrowLimit()),
                        safe(m.getPriority()),
                        safe(m.getRegistrationDate())
                });
            }
            overwrite(MEMBERS_FILE, MEMBERS_HEADER, rows);
        } catch (IOException e) {
            System.out.println("Error rewriting members: " + e.getMessage());
        }
    }

    public static void rewriteBorrowRecords(LinkedList<BorrowRecord> borrowHistory) {
        try {
            List<String[]> rows = new ArrayList<>();
            for (BorrowRecord r : borrowHistory) {
                rows.add(new String[]{
                        String.valueOf(r.getBorrowId()),
                        String.valueOf(r.getMemberId()),
                        safe(r.getIsbn()),
                        String.valueOf(r.getQuantity()),
                        String.valueOf(r.getRemainingQuantity()),
                        safe(r.getIssueDate()),
                        safe(r.getDueDate()),
                        safe(r.getReturnDate()),
                        String.valueOf(r.getFine()),
                        String.valueOf(r.isPendingApproval())
                });
            }
            overwrite(BORROW_FILE, BORROW_HEADER, rows);
        } catch (IOException e) {
            System.out.println("Error rewriting borrow records: " + e.getMessage());
        }
    }

    public static void rewriteRequests(LinkedList<ServiceRequest> requests) {
        try {
            List<String[]> rows = new ArrayList<>();
            for (ServiceRequest r : requests) {
                rows.add(new String[]{
                        String.valueOf(r.getRequestId()),
                        r.getType() == null ? "" : r.getType().name(),
                        String.valueOf(r.getMemberId()),
                        safe(r.getIsbn()),
                        String.valueOf(r.getBorrowId()),
                        String.valueOf(r.getQuantity()),
                        safe(r.getRequestDate()),
                        safe(r.getActionDate()),
                        r.getStatus() == null ? "" : r.getStatus().name(),
                        safe(r.getProcessedBy()),
                        safe(r.getProcessedDate()),
                        safe(r.getNote())
                });
            }
            overwrite(REQUESTS_FILE, REQUESTS_HEADER, rows);
        } catch (IOException e) {
            System.out.println("Error rewriting requests: " + e.getMessage());
        }
    }

    public static void appendDailyReport(
            String date, String memberId, String memberName, String role,
            String borrowId, String isbn, String quantity,
            String issueDate, String returnDate, String fine,
            String reservationDate, String action
    ) {
        try {
            append(DAILY_REPORT_FILE, new String[]{
                    safe(date),
                    safe(memberId),
                    safe(memberName),
                    safe(role),
                    safe(borrowId),
                    safe(isbn),
                    safe(quantity),
                    safe(issueDate),
                    safe(returnDate),
                    safe(fine),
                    safe(reservationDate),
                    safe(action)
            });
        } catch (IOException e) {
            System.out.println("Error writing daily report: " + e.getMessage());
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean parseBooleanSafe(String s) {
        try {
            return Boolean.parseBoolean(s.trim());
        } catch (Exception e) {
            return false;
        }
    }

    private static RequestType parseRequestTypeSafe(String s) {
        try {
            return RequestType.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static RequestStatus parseRequestStatusSafe(String s) {
        try {
            return RequestStatus.valueOf(s.trim());
        } catch (Exception e) {
            return RequestStatus.PENDING;
        }
    }
}

