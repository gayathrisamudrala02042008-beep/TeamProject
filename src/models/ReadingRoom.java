package models;

import java.util.ArrayList;
import java.util.List;

public class ReadingRoom {
    private int roomNumber;
    private int capacity;
    private List<Integer> memberIds;
    private String roomType;  // "Student" or "Faculty"

    public ReadingRoom() {
        this.memberIds = new ArrayList<>();
    }

    public ReadingRoom(int roomNumber, int capacity, String roomType) {
        this.roomNumber = roomNumber;
        this.capacity = capacity;
        this.roomType = roomType;
        this.memberIds = new ArrayList<>();
    }

    public int getRoomNumber() { return roomNumber; }
    public void setRoomNumber(int roomNumber) { this.roomNumber = roomNumber; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public List<Integer> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Integer> memberIds) { this.memberIds = memberIds; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
}

