package main;

import service.SmartLibService;
import utility.InputUtil;

public class Main {

    public static void main(String[] args) {

        SmartLibService service = new SmartLibService(); // single service instance

        while (true) {   // LOGIN LOOP
            service.login();
            boolean logout = false;

            while (!logout) {   // MENU LOOP
                String role = service.getCurrentUserRole();

                System.out.println("===== SMARTLIB – Intelligent Library & Knowledge Resource Manager =====");
                System.out.println("Logged in as: " + role + (("Student".equalsIgnoreCase(role) || "Faculty".equalsIgnoreCase(role))
                        ? (" (MemberID " + service.getCurrentMemberId() + ")") : ""));

                if ("Staff".equalsIgnoreCase(role)) {
                    System.out.println("0. Logout");
                    System.out.println("1. Add Book");
                    System.out.println("2. Update Book Copies");
                    System.out.println("3. Register Member");
                    System.out.println("4. Search Book");
                    System.out.println("5. Sort Books");
                    System.out.println("6. View Pending Requests");
                    System.out.println("7. View All Requests");
                    System.out.println("8. Process Request (Approve/Reject)");
                    System.out.println("9. View Racks");
                    System.out.println("10. Show Due Tomorrow Notifications");
                    System.out.println("11. Top K Borrowed Books");
                    System.out.println("12. Most Active Members");
                    System.out.println("13. Undo Last Approved Action");
                    System.out.println("14.  Generate Daily Report");

                    int choice = InputUtil.readInt("Enter your choice: ");
                    switch (choice) {
                        case 0 -> { service.logout(); logout = true; System.out.println("Logged out."); }
                        case 1 -> service.addBook();
                        case 2 -> service.updateBookCopies();
                        case 3 -> service.registerMember();
                        case 4 -> service.searchBookMenu();
                        case 5 -> service.sortBooksMenu();
                        case 6 -> service.viewPendingRequests();
                        case 7 -> service.viewAllRequests();
                        case 8 -> service.processRequest();
                        case 9 -> service.viewRacks();
                        case 10 -> service.showDueTomorrowNotifications();
                        case 11 -> service.showTopKBorrowedBooks();
                        case 12 -> service.showMostActiveMembers();
                        case 13 -> service.undoLastAction();
                        case 14 -> service.generateDailyReport();
                        default -> System.out.println("Invalid choice.");
                    }
                } else {
                    // MEMBER MENU (Student or Faculty)
                    System.out.println("0. Logout");
                    System.out.println("1. Search Book");
                    System.out.println("2. Request Borrow Book (Staff approval required)");
                    System.out.println("3. Request Return Book (Staff approval required)");
                    System.out.println("4. Request Renew Book (Staff approval required)");
                    System.out.println("5. Request Reserve Book (Staff approval required)");
                    System.out.println("6. Request Assign Reading Room (Staff approval required)");
                    System.out.println("7. Request Release Reading Room (Staff approval required)");
                    System.out.println("8. View My Requests");
                    System.out.println("9. View My Borrow Records");
                    System.out.println("10. View Racks");

                    int choice = InputUtil.readInt("Enter your choice: ");
                    switch (choice) {
                        case 0 -> { service.logout(); logout = true; System.out.println("Logged out."); }
                        case 1 -> service.searchBookMenu();
                        case 2 -> service.requestBorrowBook();
                        case 3 -> service.requestReturnBook();
                        case 4 -> service.requestRenewBook();
                        case 5 -> service.requestReserveBook();
                        case 6 -> service.requestAssignReadingRoom();
                        case 7 -> service.requestReleaseReadingRoom();
                        case 8 -> service.viewMyRequests();
                        case 9 -> service.viewMyBorrows();
                        case 10 -> service.viewRacks();
                        default -> System.out.println("Invalid choice.");
                    }
                }

                System.out.println();
            }
        }
    }
}

