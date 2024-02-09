
import com.example.meetingtogether.retrofit.FileInfo;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;
import java.util.List;

public class MessageDTO {
    @SerializedName("id")
    private int id;
    @SerializedName("content")
    private String message;

    @SerializedName("request_type")
    private RequestType type;
    private User user;
    @SerializedName("sender")
    private String senderId;
    @SerializedName("sender_name")
    private String senderName;
    @SerializedName("room_id")
    private int roomUuid;

    @SerializedName("room_name")
    private String roomName;
    
    private Room room;
    private List<Room> roomList;
    private List<User> userList;
    private User receiveUser;
    private String receiverUserId;
    @SerializedName("status")
    private Status status;

    @SerializedName("room_type")
    private RoomType roomType;
    @SerializedName("message_temp_id")
    private String messageTempId;
    @SerializedName("message_type")
    private MessageType messageType;

    @SerializedName("message_type2")
    private MessageType2 message_type2;

    private List<String> imagePath;
    @SerializedName("create_date")
    private LocalDateTime createDate;
    @SerializedName("no_read_cnt")
    private int noReadCnt;
    @SerializedName("file_info_list")
    private List<FileInfo> fileInfoList;
    @SerializedName("response_type")
    private ResponseType responseType;
    @SerializedName("is_callback")
    private boolean isCallback;
    @SerializedName("profile_img_path")
    private String profileImgPath;
    @SerializedName("file_cnt")
    private String fileCnt;

    private MessagePayload messagePayload;

    public enum RoomType{
        INDIVIDUAL,
        GROUP
    }

    public enum Status{
        PROCESS,
        SUCCESS,
        FAIL,
        PENDING
    }

    public enum ResponseType{
        ROOM_CREATE_SUCCESS
    }

    public enum RequestType{
        USER_ADD,
        ROOM_LIST,
        USER_LIST,
        MESSAGE,
        ROOM_ENTER,
        ROOM_OUT,
        ROOM_CREATE,
        EXIT,
        OTHER_USER_MSG_RENEW,
        IMAGE
    }

    public enum MessageType{
        @SerializedName("RECEIVE")
        RECEIVE,
        @SerializedName("SEND")
        SEND;
    }

    public enum MessageType2{
        @SerializedName("MESSAGE")
        MESSAGE,
        @SerializedName("IMAGE")
        IMAGE;
    }

    public enum MessagePayload{
        GENERAL,
        NO_READ_CNT
    }


    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Room> getRoomList() {
        return roomList;
    }

    public void setRoomList(List<Room> roomList) {
        this.roomList = roomList;
    }

    public List<User> getUserList() {
        return userList;
    }

    public void setUserList(List<User> userList) {
        this.userList = userList;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public RequestType getType() {
        return type;
    }

    public void setType(RequestType type) {
        this.type = type;
    }

    public User getReceiveUser() {
        return receiveUser;
    }

    public void setReceiveUser(User receiveUser) {
        this.receiveUser = receiveUser;
    }

    public int getRoomUuid() {
        return roomUuid;
    }

    public void setRoomUuid(int roomUuid) {
        this.roomUuid = roomUuid;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(String receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public String getMessageTempId() {
        return messageTempId;
    }

    public void setMessageTempId(String messageTempId) {
        this.messageTempId = messageTempId;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public List<String> getImagePath() {
        return imagePath;
    }

    public void setImagePath(List<String> imagePath) {
        this.imagePath = imagePath;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public int getNoReadCnt() {
        return noReadCnt;
    }

    public void setNoReadCnt(int noReadCnt) {
        this.noReadCnt = noReadCnt;
    }

    public List<FileInfo> getFileInfoList() {
        return fileInfoList;
    }

    public void setFileInfoList(List<FileInfo> fileInfoList) {
        this.fileInfoList = fileInfoList;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(ResponseType responseType) {
        this.responseType = responseType;
    }

    public boolean isCallback() {
        return isCallback;
    }

    public void setCallback(boolean callback) {
        isCallback = callback;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getProfileImgPath() {
        return profileImgPath;
    }

    public void setProfileImgPath(String profileImgPath) {
        this.profileImgPath = profileImgPath;
    }

    public MessagePayload getMessagePayload() {
        return messagePayload;
    }

    public void setMessagePayload(MessagePayload messagePayload) {
        this.messagePayload = messagePayload;
    }

    public MessageType2 getMessage_type2() {
        return message_type2;
    }

    public void setMessage_type2(MessageType2 message_type2) {
        this.message_type2 = message_type2;
    }

    public String getFileCnt() {
        return fileCnt;
    }

    public void setFileCnt(String fileCnt) {
        this.fileCnt = fileCnt;
    }
}
