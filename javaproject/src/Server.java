import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import MessageDTO.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Server {

    public static final int PORT = 11002;

    /** 현재 서버에 접속한 사용자 리스트를 관리 */
    public static List<User> allUserList = new ArrayList<>();

    /** 현재 서버에 생성된 Room 리스트를 관리 */
    public static List<Room> allRoomList = new ArrayList<>();

    /** 예약 리스트 관리 */
    public static List<ReserveMeetingDTO> reserveMeetingDTOList = new ArrayList<>();

    /** 
     * 방마다 존재하는 사용자 뼈대 구성
     * 원래는 접속된 방과 유저만 관리 했었지만
     * 접속 되지 않은 유저의 경우 해당 메시지를 DB에 넣어 놓았다가 
     * 해당 유저가 접속하여 소켓연결이 되면 해당 DB를 조회하고 삭제해서 1번만 보내주도록 한다.
     */
    public static List<Room> allBoneRoomList = new ArrayList<>();

    /** JDBC 설정 */
    public static String dburl = "jdbc:mysql://34.64.140.200:3306/meeting";
    public static String dbUser = "root";
    public static String dbpasswd = "Alshalsh92@";

    public static Gson gson;
    public static Connection conn =null; 			//연결을 맺어낼 객체
    public static PreparedStatement ps = null;	    //명령을 선언할 객체
    public static ResultSet rs = null; 			//결과값을 담아낼 객체

    public static Map<String, Queue> queueMap = new HashMap<>(); 			//결과값을 담아낼 객체

    public static void main(String[] args) {

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer());
        gson = gsonBuilder.create();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("채팅 서버 동작중 (포트: " + PORT + " )");

            //드라이버 로딩
            Class.forName("com.mysql.cj.jdbc.Driver");

            conn = DriverManager.getConnection(dburl, dbUser, dbpasswd);

             // 처음 뼈대를 위한 모든 방 리스트를 조회한다.
            allBoneRoomList = getRoomList();

            /**
             * 각각의 클라이언트마다 새로운 Thread에서 소켓 통신을 할 수 있도록 한다.
             */
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("새로운 소켓이 연결 됐습니다 ( " + clientSocket.getInetAddress() + " )");

                // 클라이언트 소켓에 대해서 Thread 시작
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 각각의 클라이언트 소켓 컨텍스트 */
    private static void handleClient(Socket clientSocket) {
        // 해당 클라이언트의 사용자
        User me = null;
        // 해당 클라이언트의 방
        Room room = null;

        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);

            /** 수신 대기 */
            String jsonString;

            while ((jsonString = reader.readLine()) != null) {

                MessageDTO receiveMsgDTO = gson.fromJson(jsonString, MessageDTO.class);
                MessageDTO.RequestType type = receiveMsgDTO.getType();

                /**
                 * 서버에서 사용자 추가
                 * 사용자 리스트에만 추가를 해준다.
                 */
                if(type == MessageDTO.RequestType.USER_ADD){

                    // 이미 사용자가 존재한다면 패스
                    User existUser = findUser(receiveMsgDTO.getUser().getId());
                    if(existUser != null){
                        System.out.println("이미 존재 하므로 정리하자.");
                        exit(existUser);
                    }

                    me = receiveMsgDTO.getUser();
                    me.setWriter(writer);

                    allUserList.add(me);

                    /**
                     * 내가 가진 모든 방 리스트를 불러오고
                     * 방이 없다면 메모리에 추가하고
                     * 해당 되는 모든 방에 현재 유저를 집어 넣는다.
                     */
                    List<Integer> myRoomList = getMyRoomList(me);
                    for(Integer roomId : myRoomList){
                        Room myRoom = findRoom(roomId);
                        Room myBoneRoom = findBoneRoom(roomId);

                        if(myRoom == null){
                            myRoom = new Room(roomId);
                            allRoomList.add(myRoom);
                        }
                        
                        myRoom.addUser(me);
                        myBoneRoom.updateWriter(me);
                    }

                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setUser(me);
                    sendMsgDTO.setType(MessageDTO.RequestType.USER_ADD);
                    send(sendMsgDTO, writer);

                    displayServerUserAndRoomList();
                // 방 리스트
                }else if(type == MessageDTO.RequestType.ROOM_LIST){
                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setType(MessageDTO.RequestType.ROOM_LIST);
                    sendMsgDTO.setRoomList(allRoomList);
                    send(sendMsgDTO, writer);

                // 사용자 리스트
                }else if(type == MessageDTO.RequestType.USER_LIST){

                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setType(MessageDTO.RequestType.USER_LIST);
                    sendMsgDTO.setUserList(allUserList);
                    send(sendMsgDTO, writer);

                // 텍스트/이미지
                }else if(type == MessageDTO.RequestType.MESSAGE || type == MessageDTO.RequestType.IMAGE){
                    String message = receiveMsgDTO.getMessage();
                    int roomUuid = receiveMsgDTO.getRoomUuid();
                    Room receiveRoom = receiveMsgDTO.getRoom();
                    String receiverUserId = receiveMsgDTO.getReceiverUserId();

                    MessageDTO.RoomType roomType = receiveMsgDTO.getRoomType();

                    MessageDTO sendMsgDTO = receiveMsgDTO;

                    /**
                     * 개인 메시지의 경우, 첫! 메시지가 보내질 때 방이 없다면, 방을 생성한다.
                     */
                    if(roomType.equals(MessageDTO.RoomType.INDIVIDUAL)){
                        // 상대방과 나와의 방이 존재하는지 확인
                        if(roomUuid == -1 && !isExistRoom(me, receiverUserId)){
                            // 방이 없으면, 방을 생성하고, 매핑 데이터(2개) 추가
                            // 메모리에 방과 방안에 나를 넣어서 메모리에 추가
                            // 친구들은 socket에 연결되면 그때 DB를 조회하여, 매핑 데이터를 읽어오고
                            // 매핑된 데이터를 기반으로 방과 방에 자신을 넣어서 메모리에 삽입한다.
                            // 그럼 나중에 메시지를 보내면 메모리에 있는 녀석들은 즉각 받을 수 있겠지
                            roomUuid = createIndividualRoom(me, receiverUserId);
                            // 방 생성 완료료 표시
                            sendMsgDTO.setResponseType(MessageDTO.ResponseType.ROOM_CREATE_SUCCESS);
                        }
                    }else if(roomType.equals(MessageDTO.RoomType.GROUP)){
                        // 해당 그룹 채팅방이 메모리에 존재하는지 확인
                        int roomId = roomUuid;
                        boolean isExist = allRoomList.stream().anyMatch(r -> r.getUuid() == roomId);

                        Room groupRoom = null;
                        if(!isExist){
                            groupRoom = new Room(roomUuid, "group");

                            // 나 자신 추가
                            groupRoom.addUser(me);

                            // 초대한 인원들이 소켓 연결 되어져 있으면 바로 추가
                            String[] receiverUserIds = receiverUserId.split(";");
                            for(String receiverUserIdStr : receiverUserIds){
                                // 상대방이 메모리에 존재한다면 넣어주자
                                 User anotherUser = findUser(receiverUserIdStr);
                                 if(anotherUser != null) groupRoom.addUser(anotherUser);
                            }

                            // 생성될 때, 해당 방에 속하는 녀석들은 모두 넣어준다.
                            allRoomList.add(groupRoom);
                        }
                    }

                    // 메시지 저장
                    // 이미지는 PHP 에서 이미 저장하고 온거다
                    int messageId = receiveMsgDTO.getId();
                    if(type == MessageDTO.RequestType.MESSAGE){
                        messageId = saveMsg(roomUuid, me, message, type.name());
                    }
                    
                    // 메시지 정보 조회


                    // 메시지 보내기 (같은 방)
                    sendMsgDTO.setId(messageId);
                    sendMsgDTO.setStatus(MessageDTO.Status.SUCCESS);
                    sendMsgDTO.setUser(me);
                    sendMsgDTO.setSenderName(me.getName());
                    sendMsgDTO.setRoomUuid(roomUuid);
                    sendMsgDTO.setSenderId(me.getId());
                    // 읽지 않은 메시지 가져오기
                    sendMsgDTO = getMessageDTONoReadCnt(sendMsgDTO);

                    sendInRoom(sendMsgDTO, roomUuid, me);

                // 해당 방의 다른 유저들에게 메시지 새롭게 갱신 시켜주기
                // 읽지 않은 메시지를 새롭게 갱싱해주기 위해서
                }else if(type == MessageDTO.RequestType.OTHER_USER_MSG_RENEW){
                    int roomUuid = receiveMsgDTO.getRoomUuid();
                    sendInRoomExceptionMe(receiveMsgDTO, roomUuid, me);
            
                // 방에 입장
                }else if(type == MessageDTO.RequestType.ROOM_ENTER){
                    int roomUuid = receiveMsgDTO.getRoomUuid();
                    room = findRoom(roomUuid);

                    // 방이 없을 경우
                    if(room == null){
                        MessageDTO sendMsgDTO = new MessageDTO();
                        sendMsgDTO.setType(MessageDTO.RequestType.ROOM_ENTER);
                        // sendMsgDTO.setStatus(MessageDTO.Status.NONE_ROOM);
                        send(sendMsgDTO, writer);
                        continue;
                    }

                    // 이미 방에 있을 경우
                    if(isInRoom(me.getUuid())){
                        MessageDTO sendMsgDTO = new MessageDTO();
                        sendMsgDTO.setType(MessageDTO.RequestType.ROOM_ENTER);
                        // sendMsgDTO.setStatus(MessageDTO.Status.ALREADY_IN_ROOM);
                        send(sendMsgDTO, writer);
                        continue;
                    }

                    room.addUser(me);

                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setType(MessageDTO.RequestType.ROOM_ENTER);
                    sendMsgDTO.setStatus(MessageDTO.Status.SUCCESS);
                    sendMsgDTO.setUser(me);
                    sendMsgDTO.setRoom(room);
                    sendInRoom(sendMsgDTO, roomUuid);

                    displayServerUserAndRoomList();
                // 방에서 나가기
                }else if(type == MessageDTO.RequestType.ROOM_OUT){
                    int roomUuid = receiveMsgDTO.getRoomUuid();
                    room = findRoom(roomUuid);

                    // 방이 없을 경우
                    if(room == null){
                        MessageDTO sendMsgDTO = new MessageDTO();
                        sendMsgDTO.setType(MessageDTO.RequestType.ROOM_OUT);
                        // sendMsgDTO.setStatus(MessageDTO.Status.NONE_ROOM);
                        send(sendMsgDTO, writer);
                        continue;
                    }

                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setType(MessageDTO.RequestType.ROOM_OUT);
                    sendMsgDTO.setStatus(MessageDTO.Status.SUCCESS);
                    sendMsgDTO.setUser(me);

                    outRoom(room.getUuid(), me);

                    sendInRoom(sendMsgDTO, roomUuid);

                    displayServerUserAndRoomList();
                // 방 생성
                }else if(type == MessageDTO.RequestType.ROOM_CREATE){
                    // String roomName = receiveMsgDTO.getRoomName();

                    // // 방 생성
                    // room = new Room(roomName);
                    // allRoomList.add(room);

                    // // 방에 입장
                    // room.getUserList().add(me);

                    // MessageDTO sendMsgDTO = new MessageDTO();
                    // sendMsgDTO.setType(MessageDTO.RequestType.ROOM_CREATE);
                    // sendMsgDTO.setStatus(MessageDTO.Status.SUCCESS);
                    // sendMsgDTO.setRoom(room);
                    // send(sendMsgDTO, writer);

                    // displayServerUserAndRoomList();
                // 프로그램 종료
                }else if(type == MessageDTO.RequestType.EXIT){
                    // int roomUuid = receiveMsgDTO.getRoomUuid();
                    // room = findRoom(roomUuid);

                    // 방이 없을 경우
                    // if(room == null){
                    //     MessageDTO sendMsgDTO = new MessageDTO();
                    //     sendMsgDTO.setType(MessageDTO.RequestType.ROOM_OUT);
                    //     // sendMsgDTO.setStatus(MessageDTO.Status.NONE_ROOM);
                    //     send(sendMsgDTO, writer);
                    //     continue;
                    // }

                    // 아예 나가기
                    exit(me);

                    // MessageDTO sendMsgDTO = new MessageDTO();
                    // sendMsgDTO.setType(MessageDTO.RequestType.EXIT);
                    // sendMsgDTO.setStatus(MessageDTO.Status.SUCCESS);
                    // sendInRoom(sendMsgDTO, room.getUuid());

                    displayServerUserAndRoomList();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(me.getName() + "의 소켓이 종료 되었습니다.");
//            if (e instanceof SocketException && e.getMessage().equals("Socket closed")) {
//                // 소켓이 닫혔을 때의 처리
//                System.out.println("클라이언트 소켓이 닫혔습니다.");
//            // 추가로 원하는 작업 수행
//            } else {
//                // 다른 IOException에 대한 예외 처리
//                e.printStackTrace();
//            }
        // 소켓이 닫혔을 때 여기에 추가로 정리 작업을 수행할 수 있음
        } finally {
            System.out.println("이곳에서 나머지 메로리 정리를 하자");
            exit(me);

            displayServerUserAndRoomList();
        }
    }

    private static void exit(User user){
        if(user == null) return;
        if(allUserList != null){
            // 사용자 제거
            allUserList = allUserList.stream().filter(pUser -> !pUser.getId().equals(user.getId())).collect(Collectors.toList());
            // allUserList.remove(user);
        }

        if(allRoomList != null){
            // 모든 방을 뒤져서 사용자 제거
            allRoomList.forEach(room -> {
                List<User> filteredRoomUserList = room.getUserList().stream().filter(fUser -> {
                    if(fUser.getId().equals(user.getId())) return false;
                    return true;
                }).collect(Collectors.toList());

                room.setUserList(filteredRoomUserList);
            });

            // 방에 아무도 없으면 삭제
            allRoomList = allRoomList.stream().filter(room -> room.getUserList().size() > 0).collect(Collectors.toList());
        }     
        
        if(allBoneRoomList != null){
            // 모든 방을 뒤져서 writer 제거
            allBoneRoomList.forEach(room -> {
                List<User> filteredRoomUserList = room.getUserList();
                for(User fUser : filteredRoomUserList){
                    if(user.getId().equals(fUser.getId())){
                        fUser.setWriter(null);
                    }
                }
            });
        }    
        System.out.println("메모리 정리 완료");  

    }

   
    private static void outRoom(int roomUuid, User user){
        Room room = allRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);
        if(room != null) room.getUserList().remove(user);
        // 해당 방에 인원이 아무도 없으면 자동 삭제
        if(room != null && room.getUserList().isEmpty()) allRoomList.remove(room);
    }

    private static User findUser(String uuid){
        if(allUserList == null) return null;
        return allUserList.stream().filter(user -> user.getId().equals(uuid)).findAny().orElse(null);
    }

    private static boolean isInRoom(String userUuid){
        boolean isInRoom =  allRoomList.stream()
                .flatMap(room -> room.getUserList().stream().map(user -> user.getUuid()))
                .filter(fUserUuid -> fUserUuid.equals(userUuid)).findAny().isPresent();

        return isInRoom;
    }

    private static Room findRoom(int roomUuid){
        return allRoomList.stream().filter(room -> room.getUuid() == roomUuid).findAny().orElse(null);
    }

    private static Room findBoneRoom(int roomUuid){
        return allBoneRoomList.stream().filter(room -> room.getUuid() == roomUuid).findAny().orElse(null);
    }

    private static Room findRoom(User user){
        for(int i=0; i<allRoomList.size(); i++){
            Room room = allRoomList.get(i);
            for(int j=0; j<room.getUserList().size(); j++){
                User user1 = room.getUserList().get(i);
                if(user1.getUuid().equals(user.getUuid())){
                    return room;
                }
            }
        }
        return null;
    }

    private static void send(MessageDTO messageDTO, PrintWriter writer){
        String jsonString = gson.toJson(messageDTO);
        writer.println(jsonString);
    }

    private static void sendInRoom(MessageDTO messageDTO, int roomUuid) throws UnsupportedEncodingException {
       sendInRoom(messageDTO, roomUuid, null);
    }

    private static void sendInRoom(MessageDTO messageDTO, int roomUuid, User me) throws UnsupportedEncodingException {
        // Room room = allRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);
        Room room = allBoneRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);

        if(room != null){
            List<User> userListInRoom = room.getUserList();

            /**
             * 당연히 보낸 사람에게도 메시지가 가서, 전송이 완료 됐다라는 것을 알아야 한다.
             * 
             * 개인 톡방인데 상대방의 상태가 OUT이면 상대방이 방에서 나갔다라는 뜻
             */
            messageDTO.setRoomUuid(roomUuid);
            for(int i=0; i<userListInRoom.size(); i++){
                User receiveUser = userListInRoom.get(i);

                // 자신에게 보내줄 때에는 callback true
                if(me.getId().equals(receiveUser.getId())){
                    messageDTO.setCallback(true);
                    messageDTO.setMessageType(MessageDTO.MessageType.SEND);
                }else{
                    messageDTO.setCallback(false);
                    messageDTO.setMessageType(MessageDTO.MessageType.RECEIVE);
                }

                String jsonString = gson.toJson(messageDTO);
                // 이곳에 상태가 IN 일 때만 보내주자.
                if(receiveUser.getWriter() != null){
                    receiveUser.getWriter().println(jsonString);
                }else{
                    MessageDTO.RequestType type = messageDTO.getType();
                    if(type == MessageDTO.RequestType.MESSAGE || type == MessageDTO.RequestType.IMAGE){
                        messageDTO.setReceiverUserId(receiveUser.getId());
                        saveMessageQueue(messageDTO);
                    }
                }
                
            }
        }
    }

    private static void sendInRoomExceptionMe(MessageDTO messageDTO, int roomUuid, User me) throws UnsupportedEncodingException {
        Room room = allRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);

        if(room != null){
            List<User> userListInRoom = room.getUserList();

            /**
             * 당연히 보낸 사람에게도 메시지가 가서, 전송이 완료 됐다라는 것을 알아야 한다.
             * 
             * 개인 톡방인데 상대방의 상태가 OUT이면 상대방이 방에서 나갔다라는 뜻
             */
            messageDTO.setRoomUuid(roomUuid);
            for(int i=0; i<userListInRoom.size(); i++){
                User receiveUser = userListInRoom.get(i);

                // 나를 제외하고 메시지 보내기
                if(!me.getId().equals(receiveUser.getId())){
                    String jsonString = gson.toJson(messageDTO);
                    // 이곳에 상태가 IN 일 때만 보내주자.
                    receiveUser.getWriter().println(jsonString);
                }
            }
        }
    }

    private static void displayServerUserAndRoomList(){
        System.out.println("**********[모든 방 리스트]**********");
        if(allRoomList.isEmpty()) System.out.println("없음");
        allRoomList.stream().forEach(room -> {
            System.out.println(room.getUuid() + "번방 ");
            System.out.println("(해당 방의 멤버)");
            if(room.getUserList().isEmpty()) System.out.println("없음");
            room.getUserList().stream().forEach(user -> {
                System.out.println("  - " + user.getName());
            });
        });

        System.out.println("**********[모든 사용자 리스트]**********");
        if(allUserList.isEmpty()) System.out.println("없음");
        allUserList.stream().forEach(user -> {
            System.out.println(user.getName());
        });
    }

    private static int saveMsg(int roomUuid, User me, String msg, String typeName){
        String sql = "INSERT INTO message(room_id, content, sender, status, type, create_user, create_date, update_user, update_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, now(), ?, now())";
        int messageId = -1;
        try {
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setInt(1, roomUuid);
            ps.setString(2, msg);
            ps.setString(3, me.getId());
            ps.setString(4, MessageDTO.Status.SUCCESS.name());
            ps.setString(5, typeName);
            ps.setString(6, me.getId());
            ps.setString(7, me.getId());
            
            int result = ps.executeUpdate(); //명렁어 실행

            conn.commit();  // 커밋

            ResultSet generatedKeys = ps.getGeneratedKeys();
            if(generatedKeys.next()){
                messageId = generatedKeys.getInt(1);
            }

            if(result > 0){
                System.out.println("메시지 저장 성공");
            }else{
                System.out.println("실패");
            }

            return messageId;
        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }

    private static void saveMessageQueue(MessageDTO messageDTO){
        String sql = "INSERT INTO message_queue (message_id, receiver)" +
                     "VALUES (?, ?)";
        int messageId = messageDTO.getId();
        String receiver = messageDTO.getReceiverUserId();
        try {
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setInt(1, messageId);
            ps.setString(2, receiver);
            
            int result = ps.executeUpdate(); //명렁어 실행

            conn.commit();  // 커밋

            if(result > 0){
                System.out.println("전달 되지 못한 메시지 저장 성공");
            }else{
                System.out.println("전달 되지 못한 메시지 저장 실패");
            }
        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }

    private static int createIndividualRoom(User me, String receiveUserId){
        try {
            StringBuilder stringBuilder = new StringBuilder();
            String roomName = UUID.randomUUID().toString();
            int roomId = -1;

            stringBuilder.append("INSERT INTO room (NAME, type, create_user, create_date, update_user, update_date) ");
            stringBuilder.append("VALUES (?, ?, ?, now(), ?, now()) ");
            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setString(1, roomName);
            ps.setString(2, "INDIVIDUAL");
            ps.setString(3, me.getId());
            ps.setString(4, me.getId());
            int rows = ps.executeUpdate(); //명렁어 실행

            ResultSet generatedKeys = ps.getGeneratedKeys();
            if(generatedKeys.next()){
                roomId = generatedKeys.getInt(1);

                // 내가 방에 참여
                // 나는 이미 방에 참여한 상태이기 때문에 in_date 를 현재 날짜로 한다.
                stringBuilder.setLength(0);
                stringBuilder.append("INSERT INTO user_room_map (user_id, room_id, status, in_date)");
                stringBuilder.append("VALUES (?, ?, ?, now())");
                sql = stringBuilder.toString();

                ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

                ps.setString(1, me.getId());
                ps.setInt(2, roomId);
                ps.setString(3, "IN");
                rows = ps.executeUpdate(); //명렁어 실행

                // 상대방 또한 자동으로 방에 참여
                stringBuilder.setLength(0);
                stringBuilder.append("INSERT INTO user_room_map (user_id, room_id, status)");
                stringBuilder.append("VALUES (?, ?, ?)");
                sql = stringBuilder.toString();

                ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

                ps.setString(1, receiveUserId);
                ps.setInt(2, roomId);
                ps.setString(3, "IN");
                rows = ps.executeUpdate(); //명렁어 실행

                conn.commit();  // 커밋

                // 서버 메모리에도 참가했다라는 내용 적용
                Room room = new Room(roomId, roomName);
                room.addUser(me);

                // 상대방도 존재한다면 넣어주자
                User anotherUser = findUser(receiveUserId);
                if(anotherUser != null) room.addUser(anotherUser);

                allRoomList.add(room);
            }

            if(rows > 0){
                System.out.println("성공");
            }else{
                System.out.println("실패");
            }

            return roomId;

        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }

    private static List<Room> getRoomList(){
        List<Room> roomList = new ArrayList<>();

        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SELECT                                    ");
            stringBuilder.append("    A.id AS room_id,                      ");
            stringBuilder.append("    A.TYPE AS room_type,                  ");
            stringBuilder.append("    B.user_id,                            ");
            stringBuilder.append("    C.name AS user_name                   ");
            stringBuilder.append("FROM room A                               ");
            stringBuilder.append("INNER JOIN user_room_map B                ");
            stringBuilder.append("ON A.id = B.room_id                       ");
            stringBuilder.append("LEFT OUTER JOIN user C                    ");
            stringBuilder.append("ON B.user_id = C.user_id                  ");
            stringBuilder.append("ORDER BY room_id ASC                      ");
            
            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            rs = ps.executeQuery();

            Integer before_room_id = -1;
            while (rs.next()) {
                // 결과 처리
                Integer room_id = rs.getInt("room_id");
                String user_id = rs.getString("user_id");
                String user_name = rs.getString("user_name");

                if(before_room_id != room_id){
                    before_room_id = room_id;
                    Room room = new Room(room_id);
                    roomList.add(room);
                }

                User user = new User(user_name);
                user.setId(user_id);

                Room room = roomList.get(roomList.size()-1);
                room.addUser(user);                
            }

            return roomList;
        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }

    private static boolean isExistRoom(User sender, String receiverUserId){
        
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SELECT                                        ");
            stringBuilder.append("    COUNT(exist_info.friend_id) AS cnt        ");
            stringBuilder.append("FROM(                                         ");
            // stringBuilder.append("    -- 채팅방 목록                             ");
            // stringBuilder.append("    -- 특정 친구의 룸 매핑 정보                 ");
            stringBuilder.append("    SELECT                                    ");
            stringBuilder.append("        A.user_id AS friend_id,               ");
            stringBuilder.append("        A.room_id                             ");
            stringBuilder.append("    FROM(                                     ");
            stringBuilder.append("        SELECT * FROM user_room_map           ");
            stringBuilder.append("        WHERE user_id = ?                     ");
            stringBuilder.append("    ) A                                       ");
            // stringBuilder.append("    -- 내가 포함된 방 번호를 가진 사용자 정보    ");
            stringBuilder.append("    INNER JOIN (                              ");
            stringBuilder.append("        SELECT * FROM user_room_map           ");
            stringBuilder.append("        WHERE user_id = ?                     ");
            stringBuilder.append("    ) B                                       ");
            stringBuilder.append("    ON A.room_id = B.room_id                  ");
            stringBuilder.append(") AS exist_info                               ");

            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setString(1, receiverUserId);
            ps.setString(2, sender.getId());

            rs = ps.executeQuery();

            rs.next();

            int cnt = rs.getInt("cnt");

            if(cnt > 0){
                return true;
            }else{
                return false;
            }
        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }

    private static List<Integer> getMyRoomList(User me){
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SELECT                                    ");
            stringBuilder.append("    user_id,                              ");
            stringBuilder.append("    room_id,                              ");
            stringBuilder.append("    status                                ");
            stringBuilder.append("FROM user_room_map                        ");
            stringBuilder.append("WHERE user_id = ?                         ");   
            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setString(1, me.getId());

            rs = ps.executeQuery();

            List<Integer> myRoomList = new ArrayList<>();
            while (rs.next()) {
                // 결과 처리
                Integer room_id = rs.getInt("room_id");
                myRoomList.add(room_id);
            }

            return myRoomList;
        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }

    private static MessageDTO getMessageDTONoReadCnt(MessageDTO messageDTO){

        try {
            conn.commit();
            
            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append("SELECT                                                                                    ");
            stringBuilder.append("    message_info.id,                                                                      ");
            stringBuilder.append("    message_info.room_id,                                                                 ");
            stringBuilder.append("    message_info.content,                                                                 ");
            stringBuilder.append("    message_info.sender,                                                                  ");
            stringBuilder.append("    message_info.create_date,                                                             ");
            stringBuilder.append("    profile.recent_profile_img_path AS profile_img_path,                           ");
            stringBuilder.append("    COUNT(                                                                                ");
            stringBuilder.append("        CASE                                                                              ");
            stringBuilder.append("            WHEN basic = 'in_date' THEN null                                              ");
            stringBuilder.append("            WHEN basic = 'out_date' THEN                                                  ");
            stringBuilder.append("                CASE                                                                      ");
            stringBuilder.append("                    WHEN read_info.basic_date IS NULL THEN 1                              ");
            stringBuilder.append("                    WHEN message_info.create_date > read_info.basic_date THEN 1           ");
            stringBuilder.append("                    ELSE null                                                             ");
            stringBuilder.append("                END                                                                       ");
            stringBuilder.append("        END ) AS no_read_cnt                                                              ");
            stringBuilder.append("FROM message AS message_info                                                              ");
            stringBuilder.append("LEFT OUTER JOIN (                                                                         ");
            stringBuilder.append("    SELECT                                                                                ");
            stringBuilder.append("        user_id,                                                                          ");
            stringBuilder.append("        room_id,                                                                          ");
            stringBuilder.append("        CASE                                                                              ");
            stringBuilder.append("            WHEN out_date > in_date THEN 'out_date'                                       ");
            stringBuilder.append("            WHEN out_date <= in_date THEN 'in_date'                                       ");
            stringBuilder.append("            WHEN in_date IS NULL THEN 'out_date'                                          ");
            stringBuilder.append("            WHEN out_date IS NULL THEN 'in_date'                                          ");
            stringBuilder.append("        END AS basic,                                                                     ");
            stringBuilder.append("        CASE                                                                              ");
            stringBuilder.append("            WHEN out_date > in_date THEN out_date                                         ");
            stringBuilder.append("            ELSE in_date                                                                  ");
            stringBuilder.append("        END AS basic_date                                                                 ");
            stringBuilder.append("    FROM user_room_map                                                                    ");
            stringBuilder.append("    WHERE room_id = ?                                                                     ");
            stringBuilder.append(") as read_info                                                                            ");
            stringBuilder.append("ON read_info.room_id = message_info.room_id                                               ");
            // stringBuilder.append("-- 사용자 프로필 가져오기                                                                   ");
            stringBuilder.append("LEFT OUTER JOIN (                                                                         ");
            stringBuilder.append("  SELECT                                                                                    ");
            stringBuilder.append("    user_id,                                                                              ");
            stringBuilder.append("    max(profile_img_path) AS recent_profile_img_path                                      ");
            stringBuilder.append("  FROM user_profile_map A                                                                   ");
            stringBuilder.append("  WHERE TYPE = 'PROFILE_IMAGE'                                                              ");
            stringBuilder.append("  GROUP BY user_id                                                                          ");
            stringBuilder.append(") AS profile                                                                              ");
            stringBuilder.append("ON profile.user_id = message_info.sender                                                  ");
            stringBuilder.append("WHERE message_info.room_id = ?                                                            ");
            stringBuilder.append("AND message_info.id = ?                                                                   ");
            stringBuilder.append("GROUP BY message_info.id                                                                  ");
            stringBuilder.append("ORDER BY create_date DESC                                                                 ");

            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            //conn.setAutoCommit(false);  // 트랜 잭션 시작

            int roomId = messageDTO.getRoomUuid();
            int messageId = messageDTO.getId();

            ps.setInt(1, roomId);
            ps.setInt(2, roomId);
            ps.setInt(3, messageId);

            rs = ps.executeQuery();

            while (rs.next()) {
                int noReadCnt = rs.getInt("no_read_cnt");
                // 날짜 값을 가져오기
                Timestamp timestamp = rs.getTimestamp("create_date");
                String profileImgPath = rs.getString("profile_img_path");
                
                // java.sql.Timestamp를 java.time.LocalDateTime으로 변환
                LocalDateTime createDate = timestamp.toLocalDateTime();
                messageDTO.setNoReadCnt(noReadCnt);
                messageDTO.setCreateDate(createDate);
                messageDTO.setProfileImgPath(profileImgPath);
            }

            return messageDTO;
        } catch (SQLException e) {
            // 롤백
            if (conn != null) { 
                try { 
                    conn.rollback(); 
                } catch(SQLException ex) {
                    ex.printStackTrace();
                } 
            }
            throw new RuntimeException(e);
        }
    }


}