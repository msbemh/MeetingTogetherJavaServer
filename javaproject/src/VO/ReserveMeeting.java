package VO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReserveMeeting {
    private int roomId;
    private String roomName;

    private LocalDateTime startDateTime;
    private boolean notifyComplete;
    private List<User> userList = new ArrayList<>();

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }
    
    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }
    
    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }
    
    public boolean isNotifyComplete() {
        return this.notifyComplete;
    }
    
    public void setIsNotifyComplete(boolean notifyComplete) {
        this.notifyComplete = notifyComplete;
    }
    
    public String getRoomName() {
		return this.roomName;
	}

	public void setRoomName(String roomName) {
		this.roomName = roomName;
	}

    public List<User> getUserList() {
        return this.userList;
    }

    public void setUserList(List<User> userList) {
        this.userList = userList;
    }


    public void addUser(User user){
        this.userList.add(user);
    }
    
    public void updateWriter(User user){
        User findUser = this.userList.stream().filter(user1 -> user1.getId().equals(user.getId())).findFirst().get();
        findUser.setWriter(user.getWriter());
        findUser.setName(user.getName());
    }

    
}
