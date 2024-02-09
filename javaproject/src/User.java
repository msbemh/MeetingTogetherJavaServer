import com.google.gson.annotations.Expose;

import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.UUID;

public class User {
    private String uuid;
    private String name;
    private String id;
    private String phoneNum;

    public String getId() {
        return id;
    }

    public void setId(String userId) {
        this.id = userId;
    }

    @Expose(serialize = false, deserialize = false)
    private transient PrintWriter writer;

    @Expose(serialize = false, deserialize = false)
    private transient Socket clientSocket;

    public User(String name) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;

        System.out.println("사용자 " + name + "이(가) 생성 됐습니다.");
    }

    public User(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public String getPhoneNum(){
        return this.phoneNum;
    }

    public void setPhoneNum(String phoneNum){
        this.phoneNum = phoneNum;
    }

    public PrintWriter getWriter() {
        return writer;
    }

    public void setWriter(PrintWriter writer) {
        this.writer = writer;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Socket getSocket() {
        return this.clientSocket;
    }

    public void setSocket(Socket socket) {
        this.clientSocket = socket;
    }

}
