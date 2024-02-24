
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import VO.ReserveMeeting;
import VO.User;

public class Server {

    public static final int PORT = 11002;

    /** 현재 서버에 접속한 사용자 리스트를 관리 */
    public static List<User> allUserList = new ArrayList<>();

    /** 현재 서버에 생성된 Room 리스트를 관리 */
    // public static List<Room> allRoomList = new ArrayList<>();

    /** 예약 리스트 관리 */
    public static List<ReserveMeeting> reserveMeetingList = new ArrayList<>();

    /**
     * 방마다 존재하는 사용자 뼈대 구성
     * 원래는 접속된 방과 유저만 관리 했었지만
     * 접속 되지 않은 유저의 경우 해당 메시지를 DB에 넣어 놓았다가
     * 해당 유저가 접속하여 소켓연결이 되면 해당 DB를 조회하고 삭제해서 1번만 보내주도록 한다.
     */
    public static List<Room> allBoneRoomList = new ArrayList<>();

    public static Gson gson;

    public static ReserveTimerService reserveTimerService;
    public static DBService dbService;

    public static void main(String[] args) {

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer());
        gson = gsonBuilder.create();

        /** DB Service 생성 */
        dbService = new DBService();

        /** 모든 사용자 조회 */
        allUserList = dbService.getAllUserList();

        /** 예약된 리스트가 존재하는지 확인 */
        reserveMeetingList = dbService.getReserveMeetingList();
        System.out.println("[TEST] reserveMeetingList" + reserveMeetingList);

        /** 미팅 예약 기능을 위한 타이머 동작 */
        reserveTimerService = new ReserveTimerService();

        reserveTimerService.startTask(new Runnable() {
            @Override
            public void run() {
                while(!reserveTimerService.isShutdown()){
                    try {
                        Thread.sleep(1000);
                        for(int i=0; i<reserveMeetingList.size(); i++){
                            ReserveMeeting reserveMeeting = reserveMeetingList.get(i);
                            
                            // 미팅 알림 시간이거나 지나면 알림 보내주기
                            if(!reserveMeeting.isNotifyComplete() && 
                                (LocalDateTime.now().isEqual(reserveMeeting.getStartDateTime()) || LocalDateTime.now().isAfter(reserveMeeting.getStartDateTime()))){
                                int meetingId = reserveMeeting.getRoomId();
                                String roomName = reserveMeeting.getRoomName();

                                // 먼저 notify complete true로 설정
                                reserveMeeting.setIsNotifyComplete(true);
                                dbService.updateMeetingNotify(meetingId);

                                MessageDTO messageDTO = new MessageDTO();
                                messageDTO.setType(MessageDTO.RequestType.MEETING_RESERVE_NOTIFICATION);
                                messageDTO.setRoomName(roomName);
                                messageDTO.setRoomUuid(meetingId);
                                sendInReserveMeeting(messageDTO, meetingId);
                            }
                        }
                    }catch (Exception e){
                        System.out.println("예약 타이머 동작중 에러가 발생했습니다. e - " + e.getMessage());
                    }
                }
            }
        });

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("채팅 서버 동작중 (포트: " + PORT + " )");

            // 처음 뼈대를 위한 모든 방 리스트를 조회한다.
            allBoneRoomList = dbService.getRoomList();

            displayServerUserAndRoomList();

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
                 * 처음 소켓이 연결 되면 이곳으로 오게 된다.
                 */
                if(type == MessageDTO.RequestType.USER_ADD){

                    // 이미 사용자가 존재한다면 패스
                    // User existUser = findUser(receiveMsgDTO.getUser().getId());
                    // if(existUser != null){
                    //     System.out.println("이미 존재 하므로 정리하자.");
                    //     exit(existUser);
                    // }

                    User user = findUser(receiveMsgDTO.getUser().getId());
                    if(user == null){
                        user = new User();
                        user.setId(receiveMsgDTO.getUser().getId());
                        user.setName(receiveMsgDTO.getUser().getName());
                        user.setPhoneNum(receiveMsgDTO.getUser().getPhoneNum());
                        allUserList.add(user);
                    }
                    user.setWriter(writer);
                    me = user;

                    //me.setWriter(writer);

                    //allUserList.add(me);

                    /**
                     * 소켓 연결된 사용자가 가진 모든 방을 찾는다.
                     * 채팅방에 있는 List<User> 의 Writer를 넣어준다.
                     */
                    // List<Integer> myRoomList = dbService.getMyRoomList(me);
                    // for(Integer roomId : myRoomList){
                    //     // Room myRoom = findRoom(roomId);
                    //     Room myBoneRoom = findBoneRoom(roomId);

                    //     // if(myBoneRoom == null){
                    //     //     // myRoom = new Room(roomId);
                    //     //     Room newBoneRoom = new Room(roomId);
                    //     //     //allRoomList.add(newBoneRoom);
                    //     //     allBoneRoomList.add(newBoneRoom);
                    //     // }

                    //     // myRoom.addUser(me);
                    //     myBoneRoom.updateWriter(me);
                    // }

                    /**
                     * 소켓 연결된 사용자가 가진 모든 예약된 회의방을 찾늗다.
                     * 예약된 회의방에 있는 List<User> 의 Writer를 넣어준다.
                     */
                    // List<Integer> myReserveMeetingList = dbService.getMyRoomList(me);
                    // for(Integer meetingId : myReserveMeetingList){
                    //     ReserveMeeting reserveMeeting = findReserveMeeting(meetingId);
                    //     reserveMeeting.updateWriter(me);
                    // }

                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setUser(me);
                    sendMsgDTO.setType(MessageDTO.RequestType.USER_ADD);
                    send(sendMsgDTO, writer);

                    displayServerUserAndRoomList();
                // 방 리스트
                }else if(type == MessageDTO.RequestType.ROOM_LIST){
                    MessageDTO sendMsgDTO = new MessageDTO();
                    sendMsgDTO.setType(MessageDTO.RequestType.ROOM_LIST);
                    //sendMsgDTO.setRoomList(allRoomList);
                    sendMsgDTO.setRoomList(allBoneRoomList);
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
                        if(roomUuid == -1 && !dbService.isExistRoom(me, receiverUserId)){
                            // 방이 없으면, 방을 생성하고, 매핑 데이터(2개) 추가
                            // 메모리에 방과 방안에 나를 넣어서 메모리에 추가
                            // 친구들은 socket에 연결되면 그때 DB를 조회하여, 매핑 데이터를 읽어오고
                            // 매핑된 데이터를 기반으로 방과 방에 자신을 넣어서 메모리에 삽입한다.
                            // 그럼 나중에 메시지를 보내면 메모리에 있는 녀석들은 즉각 받을 수 있겠지

                            String roomName = UUID.randomUUID().toString();
                            roomUuid = dbService.createIndividualRoom(me, receiverUserId, roomName);

                            // 서버 메모리에도 참가했다라는 내용 적용
                            Room createdRoom = new Room(roomUuid, roomName);
                            createdRoom.addUser(me);

                            // 상대방도 존재한다면 넣어주자
                            User anotherUser = findUser(receiverUserId);
                            if(anotherUser != null) room.addUser(anotherUser);

                            //allRoomList.add(room);
                            allBoneRoomList.add(room);

                            // 방 생성 완료료 표시
                            sendMsgDTO.setResponseType(MessageDTO.ResponseType.ROOM_CREATE_SUCCESS);
                        }
                    }else if(roomType.equals(MessageDTO.RoomType.GROUP)){
                        // 해당 그룹 채팅방이 메모리에 존재하는지 확인
                        int roomId = roomUuid;
                        //boolean isExist = allRoomList.stream().anyMatch(r -> r.getUuid() == roomId);
                        boolean isExist = allBoneRoomList.stream().anyMatch(r -> r.getUuid() == roomId);

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
                            //allRoomList.add(groupRoom);
                            allBoneRoomList.add(groupRoom);
                        }
                    }

                    // 메시지 저장
                    // 이미지는 PHP 에서 이미 저장하고 온거다
                    int messageId = receiveMsgDTO.getId();
                    if(type == MessageDTO.RequestType.MESSAGE){
                        messageId = dbService.saveMsg(roomUuid, me, message, type.name());
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
                    sendMsgDTO = dbService.getMessageDTONoReadCnt(sendMsgDTO);

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
                    if(isInRoom(me.getId())){
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
                
                // 회의방이 생성이 완료 됐다고 알림을 받아서 메모리 관리를 해준다.
                // 회의방은 PHP에서 생성하였다.
                }else if(type == MessageDTO.RequestType.NOTIFY_MEETING_RESERVE_CREATED || type == MessageDTO.RequestType.NOTIFY_MEETING_RESERVE_DELETED){
                    /** 예약된 리스트가 존재하는지 확인 */
                    reserveMeetingList = dbService.getReserveMeetingList();
                    System.out.println("[TEST] 새로운 예약 회의방 생성/삭제로 인한 메모리 리프레쉬 reserveMeetingList" + reserveMeetingList);
                    displayServerUserAndRoomList();
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
        // if(allUserList != null){
        //     // 사용자 제거
        //     allUserList = allUserList.stream().filter(pUser -> !pUser.getId().equals(user.getId())).collect(Collectors.toList());
        //     // allUserList.remove(user);
        // }

        if(allUserList != null){         
            allUserList.forEach(pUser -> {
                if(user.getId().equals(pUser.getId())){
                    pUser.setWriter(null);
                }
                
                //room.setUserList(filteredRoomUserList);
            });

            // allUserList.remove(user);
        }

        // if(allRoomList != null){
        //     // 모든 방을 뒤져서 사용자 제거
        //     allRoomList.forEach(room -> {
        //         List<User> filteredRoomUserList = room.getUserList().stream().filter(fUser -> {
        //             if(fUser.getId().equals(user.getId())) return false;
        //             return true;
        //         }).collect(Collectors.toList());

        //         room.setUserList(filteredRoomUserList);
        //     });

        //     // 방에 아무도 없으면 삭제
        //     allRoomList = allRoomList.stream().filter(room -> room.getUserList().size() > 0).collect(Collectors.toList());
        // }

        // if(allBoneRoomList != null){
        //     // 모든 방을 뒤져서 사용자의 writer 제거
        //     allBoneRoomList.forEach(room -> {
        //         for(User pUser : room.getUserList()){
        //             if(pUser.getId().equals(user.getId())){
        //                 pUser.setWriter(null);
        //             }
        //         }
        //         //room.setUserList(filteredRoomUserList);
        //     });

        //     // 방에 아무도 없으면 삭제
        //     //allRoomList = allRoomList.stream().filter(room -> room.getUserList().size() > 0).collect(Collectors.toList());
        // }

        // if(reserveMeetingList != null){
        //     // 모든 방을 뒤져서 사용자의 writer 제거
        //     reserveMeetingList.forEach(room -> {
        //         for(User pUser : room.getUserList()){
        //             if(pUser.getId().equals(user.getId())){
        //                 pUser.setWriter(null);
        //             }
        //         }
        //         //room.setUserList(filteredRoomUserList);
        //     });

        //     // 방에 아무도 없으면 삭제
        //     //allRoomList = allRoomList.stream().filter(room -> room.getUserList().size() > 0).collect(Collectors.toList());
        // }

        // if(allBoneRoomList != null){
        //     // 모든 방을 뒤져서 writer 제거
        //     allBoneRoomList.forEach(room -> {
        //         List<User> filteredRoomUserList = room.getUserList();
        //         for(User fUser : filteredRoomUserList){
        //             if(user.getId().equals(fUser.getId())){
        //                 fUser.setWriter(null);
        //             }
        //         }
        //     });
        // }
        System.out.println("메모리 정리 완료");

    }


    private static void outRoom(int roomUuid, User user){
        //Room room = allRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);
        Room room = allBoneRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);
        if(room != null) {
            List<User> userList = room.getUserList();
            for(User pUser : userList){
                if(pUser.getId().equals(user.getId())){
                    pUser.setWriter(null);
                    break;
                }
            }
            //room.getUserList().remove(user);
        }
        // 해당 방에 인원이 아무도 없으면 자동 삭제
        // if(room != null && room.getUserList().isEmpty()) allRoomList.remove(room);
    }

    public static User findUser(String uuid){
        if(allUserList == null) return null;
        return allUserList.stream().filter(user -> user.getId().equals(uuid)).findAny().orElse(null);
    }

    private static boolean isInRoom(String userUuid){
        // boolean isInRoom =  allRoomList.stream()
        //         .flatMap(room -> room.getUserList().stream().map(user -> user.getUuid()))
        //         .filter(fUserUuid -> fUserUuid.equals(userUuid)).findAny().isPresent();
        boolean isInRoom =  allBoneRoomList.stream()
                .flatMap(room -> room.getUserList().stream().map(user -> user))
                .filter(fUser -> fUser.getId().equals(userUuid) && fUser.getWriter() != null).findAny().isPresent();

        return isInRoom;
    }

    private static Room findRoom(int roomUuid){
        //return allRoomList.stream().filter(room -> room.getUuid() == roomUuid).findAny().orElse(null);
        return allBoneRoomList.stream().filter(room -> room.getUuid() == roomUuid).findAny().orElse(null);
    }

    private static Room findBoneRoom(int roomUuid){
        return allBoneRoomList.stream().filter(room -> room.getUuid() == roomUuid).findAny().orElse(null);
    }

    private static ReserveMeeting findReserveMeeting(int meetingId){
        return reserveMeetingList.stream().filter(room -> room.getRoomId() == meetingId).findAny().orElse(null);
    }

    // private static Room findRoom(User user){
    //     for(int i=0; i<allRoomList.size(); i++){
    //         Room room = allRoomList.get(i);
    //         for(int j=0; j<room.getUserList().size(); j++){
    //             User user1 = room.getUserList().get(i);
    //             if(user1.getUuid().equals(user.getUuid())){
    //                 return room;
    //             }
    //         }
    //     }
    //     return null;
    // }

    private static void send(MessageDTO messageDTO, PrintWriter writer){
        String jsonString = gson.toJson(messageDTO);
        writer.println(jsonString);
    }

    private static void sendInRoom(MessageDTO messageDTO, int roomUuid) throws UnsupportedEncodingException {
        sendInRoom(messageDTO, roomUuid, null);
    }

    private static void sendInReserveMeeting(MessageDTO messageDTO, int meetingId){
        ReserveMeeting reserveMeeting = reserveMeetingList.stream().filter(room1 -> room1.getRoomId() == meetingId).findAny().orElse(null);

        if(reserveMeeting != null){
            List<User> userListInRoom = reserveMeeting.getUserList();

            for(int i=0; i<userListInRoom.size(); i++){
                User receiveUser = userListInRoom.get(i);

                String jsonString = gson.toJson(messageDTO);

                if(receiveUser.getWriter() != null){
                    receiveUser.getWriter().println(jsonString);
                }

            }
        }
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
                // writer 가 없다면 DB에 메시지 저장
                }else{
                    MessageDTO.RequestType type = messageDTO.getType();
                    if(type == MessageDTO.RequestType.MESSAGE || type == MessageDTO.RequestType.IMAGE){
                        messageDTO.setReceiverUserId(receiveUser.getId());
                        dbService.saveMessageQueue(messageDTO);
                    }
                }

            }
        }
    }

    private static void sendInRoomExceptionMe(MessageDTO messageDTO, int roomUuid, User me) throws UnsupportedEncodingException {
        //Room room = allRoomList.stream().filter(room1 -> room1.getUuid() == roomUuid).findAny().orElse(null);
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

                // 나를 제외하고 메시지 보내기
                if(!me.getId().equals(receiveUser.getId())){
                    String jsonString = gson.toJson(messageDTO);
                    // 이곳에 상태가 IN 일 때만 보내주자.
                    if(receiveUser.getWriter() != null) receiveUser.getWriter().println(jsonString);
                }
            }
        }
    }

    private static void displayServerUserAndRoomList(){
        System.out.println("**********[모든 방 리스트]**********");
        if(allBoneRoomList.isEmpty()) System.out.println("없음");
        allBoneRoomList.stream().forEach(room -> {
            System.out.println(room.getUuid() + "번방 ");
            System.out.println("(해당 방의 멤버)");
            if(room.getUserList().isEmpty()) System.out.println("없음");
            room.getUserList().stream().forEach(user -> {
                boolean isConnSocket = user.getWriter() != null;
                System.out.print("  - " + user.getName());
                if(isConnSocket){
                    System.out.print("(소켓연결)");
                }
                System.out.println("");
            });
        });
        // if(allRoomList.isEmpty()) System.out.println("없음");
        // allRoomList.stream().forEach(room -> {
        //     System.out.println(room.getUuid() + "번방 ");
        //     System.out.println("(해당 방의 멤버)");
        //     if(room.getUserList().isEmpty()) System.out.println("없음");
        //     room.getUserList().stream().forEach(user -> {
        //         System.out.println("  - " + user.getName());
        //     });
        // });


        System.out.println("**********[모든 사용자 리스트]**********");
        if(allUserList.isEmpty()) System.out.println("없음");
        allUserList.stream().forEach(user -> {
            boolean isConnSocket = user.getWriter() != null;
                System.out.print(user.getName());
                if(isConnSocket){
                    System.out.print("(소켓연결)");
                }
                System.out.println("");
        });

        System.out.println("**********[예약 회의방 사용자 리스트]**********");
        if(reserveMeetingList.isEmpty()) System.out.println("없음");
        reserveMeetingList.stream().forEach(reserveMeeting -> {
            System.out.println(reserveMeeting.getRoomId() + "번방 ");
            System.out.println("(해당 방의 멤버)");
            if(reserveMeeting.getUserList().isEmpty()) System.out.println("없음");
            reserveMeeting.getUserList().stream().forEach(user -> {
                boolean isConnSocket = user.getWriter() != null;
                System.out.print("  - " + user.getName());
                if(isConnSocket){
                    System.out.print("(소켓연결)");
                }
                System.out.println("");
            });
        });
    }

}