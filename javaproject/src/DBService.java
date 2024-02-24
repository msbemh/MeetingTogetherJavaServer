import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import VO.ReserveMeeting;
import VO.User;

public class DBService {

    /** JDBC 설정 */
    private String dburl = "jdbc:mysql://34.64.140.200:3306/meeting";
    private String dbUser = "root";
    private String dbpasswd = "Alshalsh92@";

    public static Connection conn =null; 			//연결을 맺어낼 객체
    private PreparedStatement ps = null;	    //명령을 선언할 객체
    private ResultSet rs = null; 			//결과값을 담아낼 객체

    public DBService() {
        //드라이버 로딩
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            if(conn == null) conn = DriverManager.getConnection(dburl, dbUser, dbpasswd);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int saveMsg(int roomUuid, User me, String msg, String typeName){
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

    public void saveMessageQueue(MessageDTO messageDTO){
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

    public int createIndividualRoom(User me, String receiveUserId, String roomName){
        try {
            StringBuilder stringBuilder = new StringBuilder();
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

    public List<Room> getRoomList(){
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

                User user = Server.findUser(user_id);
                if(user == null){
                    user = new User(user_name);
                    user.setId(user_id);
                    Server.allUserList.add(user);
                }
                
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

    // private User findUser(List<User> userList, String id){
    //     for(User pUser : userList){
    //         if(pUser.getId().equals(id)){
    //             return pUser;
    //         }
    //     }
    //     return null;
    // }

    public boolean isExistRoom(User sender, String receiverUserId){

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

    public List<Integer> getMyRoomList(User me){
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

    public MessageDTO getMessageDTONoReadCnt(MessageDTO messageDTO){

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

    public List<ReserveMeeting> getReserveMeetingList(){

        try {
            StringBuilder stringBuilder = new StringBuilder();

            // 캐시 제거를 위해 close 후에 다시 조회한다.
            conn.close();
            conn = DriverManager.getConnection(dburl, dbUser, dbpasswd);

            Server.reserveMeetingList.clear();

            stringBuilder.append("SELECT                                        ");
            stringBuilder.append("    A.id AS meeting_id                        ");
            stringBuilder.append("    ,A.title                                  ");
            stringBuilder.append("    ,A.reserve_start_date                     ");
            stringBuilder.append("    ,A.reserve_end_date                       ");
            stringBuilder.append("    ,A.host                                   ");
            stringBuilder.append("    ,A.is_notify_complete                     ");
            stringBuilder.append("    ,B.user_id                                ");
            stringBuilder.append("FROM meeting A                                ");
            stringBuilder.append("LEFT OUTER JOIN meeting_reserve_user_map B    ");
            stringBuilder.append("ON A.id = B.meeting_id                        ");
            stringBuilder.append("WHERE A.reserve_end_date >= NOW()             ");
            stringBuilder.append("AND A.is_notify_complete = 0                  ");

            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            
            rs = ps.executeQuery();

            while (rs.next()) {
                int meetingId = rs.getInt("meeting_id");
                String roomName = rs.getString("title");
                String userId = rs.getString("user_id");
                
                Timestamp reserveStartDateTime = rs.getTimestamp("reserve_start_date");
                Timestamp reserveEndDateTime = rs.getTimestamp("reserve_end_date");

                // java.sql.Timestamp를 java.time.LocalDateTime으로 변환
                LocalDateTime reserveStartDate = reserveStartDateTime.toLocalDateTime();
                LocalDateTime reserveEndDate = reserveEndDateTime.toLocalDateTime();

                ReserveMeeting reserveMeeting = Server.reserveMeetingList.stream().filter(r -> r.getRoomId() == meetingId).findAny().orElse(null);

                if(reserveMeeting == null){
                    reserveMeeting = new ReserveMeeting();
                    reserveMeeting.setRoomId(meetingId);
                    reserveMeeting.setRoomName(roomName);
                    reserveMeeting.setStartDateTime(reserveStartDate);
                    Server.reserveMeetingList.add(reserveMeeting);
                }

                User user = Server.findUser(userId);
                if(user == null){
                    user = new User();
                    user.setId(userId);
                    Server.allUserList.add(user);
                }

                reserveMeeting.addUser(user);  
            }

            return Server.reserveMeetingList;
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

    public List<User> getReservedUserList(int meetingId){
        List<User> reservedUserList = new ArrayList<>();
        try {

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append("SELECT * FROM meeting_reserve_user_map ");
            stringBuilder.append("WHERE meeting_id = ? ");

            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            //conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setInt(1, meetingId);

            rs = ps.executeQuery();

            while (rs.next()) {

                String userId = rs.getString("user_id");

                User user =  new User();
                user.setId(userId);

                reservedUserList.add(user);
            }

            return reservedUserList;
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

    public void updateMeetingNotify(int meetingId){
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("UPDATE meeting SET is_notify_complete = 1 ");
        stringBuilder.append("WHERE id = ?                              ");
        
        String sql = stringBuilder.toString();

        try {
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setInt(1, meetingId);

            int result = ps.executeUpdate(); //명렁어 실행

            conn.commit();  // 커밋

            if(result > 0){
                System.out.println("알림 보냈다 라는 표시 업데이트 성공");
            }else{
                System.out.println("알림 보냈다 라는 표시 업데이트 실패");
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

    public List<Integer> getMyReserveMeetingList(User me){
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SELECT                                    ");
            stringBuilder.append("    user_id,                              ");
            stringBuilder.append("    meeting_id,                           ");
            stringBuilder.append("FROM meeting_reserve_user_map             ");
            stringBuilder.append("WHERE user_id = ?                         ");
            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            conn.setAutoCommit(false);  // 트랜 잭션 시작

            ps.setString(1, me.getId());

            rs = ps.executeQuery();

            List<Integer> myRoomList = new ArrayList<>();
            while (rs.next()) {
                // 결과 처리
                Integer room_id = rs.getInt("meeting_id");
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

    public List<User> getAllUserList(){
        List<User> userList = new ArrayList<>();
        try {
            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append("SELECT                            ");
            stringBuilder.append("    user_id,                      ");
            stringBuilder.append("    name,                         ");
            stringBuilder.append("    phone_num                     ");
            stringBuilder.append("FROM user                         ");

            String sql = stringBuilder.toString();

            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            //conn.setAutoCommit(false);  // 트랜 잭션 시작
            rs = ps.executeQuery();

            while (rs.next()) {

                String userId = rs.getString("user_id");
                String name = rs.getString("name");
                String phoneNum = rs.getString("phone_num");

                User user =  new User();
                user.setId(userId);
                user.setName(name);
                user.setPhoneNum(phoneNum);

                userList.add(user);
            }

            return userList;
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
