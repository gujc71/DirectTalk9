package gujc.directtalk9.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatModel {
    public Map<String, String> users = new HashMap<>() ;
    public Map<String, String> messages = new HashMap<>() ;

    public static class FileInfo {
        public String filename;
        public String filesize;
    }
}
