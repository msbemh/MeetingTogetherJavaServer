import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import VO.User;

public class Room {
    private int uuid;
    private String name;
    private List<User> userList = new ArrayList<>();

    public Room(int uuid) {
        this.uuid = uuid;
    }

    public Room(int uuid, String name) {
        this.name = name;
        this.uuid = uuid;
    }

    public void updateWriter(User user){
        User findUser = this.userList.stream().filter(user1 -> user1.getId().equals(user.getId())).findFirst().get();
        findUser.setWriter(user.getWriter());
        findUser.setName(user.getName());
    }

    public void addUser(User user){
        userList.add(user);
    }

    public void removeUser(User user){
        this.userList = this.userList.stream().filter(user1 -> !user1.getId().equals(user.getId())).collect(Collectors.toList());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getUuid() {
        return uuid;
    }

    public void setUuid(int uuid) {
        this.uuid = uuid;
    }

    public List<User> getUserList() {
        return userList;
    }

    public void setUserList(List<User> userList) {
        this.userList = userList;
    }
}
