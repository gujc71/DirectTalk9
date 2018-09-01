package gujc.directtalk9.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatModel {
    public Map<String, String> users = new HashMap<>() ;
    public Map<String, String> messages = new HashMap<>() ;

    public static class Message {
        public String uid;
        public String msg;
        public String msgtype;          // 0: msg, 1: image, 2: file
        public Date timestamp;
        public List<String> readUsers = new ArrayList<>();
        public String filename;
        public String filesize;
    }

    public static class FileInfo {
        public String filename;
        public String filesize;
    }
}
