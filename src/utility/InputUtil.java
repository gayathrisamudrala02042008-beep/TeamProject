package utility;

import models.Book;

import java.util.Scanner;

public class InputUtil {

    private static final Scanner SCANNER = new Scanner(System.in);

    private InputUtil() {}

    public static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine();
            try {
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid integer. Please try again.");
            }
        }
    }

    public static String readString(String prompt) {
        System.out.print(prompt);
        return SCANNER.nextLine().trim();
    }

    public static String readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String date = SCANNER.nextLine().trim();
            if (date.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] parts = date.split("-");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31 && year >= 1) {
                    return date;
                }
            }
            System.out.println("Invalid date format! Use DD-MM-YYYY (Example: 05-04-2026)");
        }
    }

    public static Book readBook() {
        String isbn = readString("Enter ISBN: ");
        String title = readString("Enter Title: ");
        String author = readString("Enter Author: ");
        int year = readInt("Enter Publication Year: ");
        int totalCopies = readInt("Enter Total Copies: ");
        int availableCopies = totalCopies;
        int issueCount = 0;
        return new Book(isbn, title, author, year, totalCopies, availableCopies, issueCount);
    }
}

