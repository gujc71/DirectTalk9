package gujc.directtalk9.chat;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import gujc.directtalk9.R;
import gujc.directtalk9.model.ChatModel;
import gujc.directtalk9.model.NotificationModel;
import gujc.directtalk9.model.UserModel;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {

    private Button sendBtn;
    private EditText msg_input;
    private RecyclerView recyclerView;

    private SimpleDateFormat dateFormatDay = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat dateFormatHour = new SimpleDateFormat("aa hh:mm");
    private String roomID;
    private String myUid;
    private Map<String, UserModel> userList = new HashMap<>();

    private DatabaseReference databaseReference;
    private ValueEventListener valueEventListener;
    private DatabaseReference db=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseDatabase.getInstance().getReference();

        dateFormatDay.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        dateFormatHour.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String toUid = getIntent().getStringExtra("toUid");
        String param_room_id = getIntent().getStringExtra("roomID");

        recyclerView = findViewById(R.id.recyclerView);
        msg_input = findViewById(R.id.msg_input);
        sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(sendBtnClickListener);

        /*
         two user: roomid or uid talking
         multi user: roomid
         */
        if (!"".equals(toUid) && toUid!=null) {                     // find existing room for two user
            findChatRoom(toUid);
        } else
        if (!"".equals(param_room_id) && param_room_id!=null) { // existing room (multi user)
            setChatRoom(param_room_id);
        };

        if (roomID==null) {                                                     // new room for two user
            getUserInfoFromServer(myUid);
            getUserInfoFromServer(toUid);
        };
    }

    void getUserInfoFromServer(String id){
        db.child("users").child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                UserModel userModel = dataSnapshot.getValue(UserModel.class);
                userList.put(userModel.getUid(), userModel);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    void findChatRoom(final String toUid){
        db.child("rooms").orderByChild("users/"+myUid).equalTo("i").addListenerForSingleValueEvent(new ValueEventListener(){
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot item: dataSnapshot.getChildren()) {
                    Map<String, String> users = (Map<String, String>) item.child("users").getValue();
                    if (users.size()==2 && users.containsKey(toUid)) {
                        setChatRoom(item.getKey());
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {   }
        });
    }

    void setChatRoom(String rid) {
        roomID = rid;
        db.child("rooms").child(roomID).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot item: dataSnapshot.getChildren()) {
                    getUserInfoFromServer(item.getKey());
                }
                recyclerView.setLayoutManager(new LinearLayoutManager(ChatActivity.this));
                recyclerView.setAdapter(new RecyclerViewAdapter());
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    Button.OnClickListener sendBtnClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            sendBtn.setEnabled(false);

            if (roomID==null) {             // two user
                ChatModel chatModel = new ChatModel();
                for( String key : userList.keySet() ){
                    chatModel.users.put(key, "i");
                }
                roomID = db.child("rooms").push().getKey();
                db.child("rooms/"+roomID).setValue(chatModel);
                recyclerView.setLayoutManager(new LinearLayoutManager(ChatActivity.this));
                recyclerView.setAdapter(new RecyclerViewAdapter());
            }
            ChatModel.Message messages = new ChatModel.Message();
            messages.uid = myUid;
            messages.msg = msg_input.getText().toString();
            messages.timestamp = ServerValue.TIMESTAMP;
            db.child("rooms").child(roomID).child("lastmessage").setValue(messages);
            messages.readUsers.put(myUid, true);
            db.child("rooms").child(roomID).child("messages").push().setValue(messages).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    //sendGCM();
                    sendBtn.setEnabled(true);
                    msg_input.setText("");
                }
            });
            db.child("rooms").child(roomID).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (final DataSnapshot item : dataSnapshot.getChildren()) {
                        final String uid = item.getKey();
                        if (!myUid.equals(item.getKey())) {
                            db.child("rooms").child(roomID).child("unread").child(item.getKey()).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    Integer cnt = dataSnapshot.getValue(Integer.class);
                                    if (cnt==null) cnt=0;
                                    db.child("rooms").child(roomID).child("unread").child(uid).setValue(cnt+1);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) { }
            });
        }
    };

    void sendGCM(){
         Gson gson = new Gson();
         NotificationModel notificationModel = new NotificationModel();
         notificationModel.notification.title = userList.get(myUid).getUsernm();
         notificationModel.notification.body = msg_input.getText().toString();
         notificationModel.data.title = userList.get(myUid).getUsernm();
         notificationModel.data.body = msg_input.getText().toString();

         for ( Map.Entry<String, UserModel> elem : userList.entrySet() ){
             if (myUid.equals(elem.getValue().getUid())) continue;
             notificationModel.to = elem.getValue().getToken();
             RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf8"), gson.toJson(notificationModel));
             Request request = new Request.Builder()
                    .header("Content-Type", "application/json")
                    .addHeader("Authorization", "key=")
                    .url("https://fcm.googleapis.com/fcm/send")
                    .post(requestBody)
                    .build();

             OkHttpClient okHttpClient = new OkHttpClient();
             okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {                }
             });
         }
    }

    // =======================================================================================

    class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        List<ChatModel.Message> messageList;
        String beforeDay = null;
        MessageViewHolder beforeViewHolder;
        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));

        public RecyclerViewAdapter() {
            messageList = new ArrayList<>();

            databaseReference = db.child("rooms").child(roomID).child("messages");
            valueEventListener = databaseReference.addValueEventListener(new ValueEventListener(){
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    beforeDay = null;
                    messageList.clear();
                    db.child("rooms").child(roomID).child("unread").child(myUid).setValue(0);

                    Map<String, Object> readUsers = new HashMap<>();
                    for (DataSnapshot item: dataSnapshot.getChildren()) {
                        ChatModel.Message message = item.getValue(ChatModel.Message.class);
                        if (!message.readUsers.containsKey(myUid)) {
                            message.readUsers.put(myUid, true);
                            readUsers.put(item.getKey(), message);
                        }
                        messageList.add(message);
                    }

                    if (readUsers.size()>0) {
                        db.child("rooms").child(roomID).child("messages").updateChildren(readUsers).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                notifyDataSetChanged();
                                recyclerView.scrollToPosition(messageList.size() - 1);
                            }
                        });
                    } else{
                        notifyDataSetChanged();
                        recyclerView.scrollToPosition(messageList.size() - 1);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {      }
            });
        }

        @Override
        public int getItemViewType(int position) {
            if (myUid.equals(messageList.get(position).uid) ) {
                return R.layout.item_chatmsg_right;
            } else {
                return R.layout.item_chatmsg_left;
            }
        }
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MessageViewHolder messageViewHolder = (MessageViewHolder) holder;
            ChatModel.Message message = messageList.get(position);

            setReadCounter(position, messageViewHolder.read_counter);

            String day = dateFormatDay.format( new Date( (long) message.timestamp) );
            String timestamp = dateFormatHour.format( new Date( (long) message.timestamp) );
            messageViewHolder.timestamp.setText(timestamp);
            messageViewHolder.msg_item.setText(message.msg);

            if (! myUid.equals(message.uid)) {
                UserModel userModel = userList.get(message.uid);
                messageViewHolder.msg_name.setText(userModel.getUsernm());

                if (userModel.getUserphoto()==null) {
                    Glide.with(getApplicationContext()).load(R.drawable.user)
                            .apply(requestOptions)
                            .into(messageViewHolder.user_photo);
                } else{
                    Glide.with(getApplicationContext()).load(userModel.getUserphoto())
                            .apply(requestOptions)
                            .into(messageViewHolder.user_photo);
                }
            }

            messageViewHolder.divider.setVisibility(View.INVISIBLE);
            messageViewHolder.divider.getLayoutParams().height = 0;

            if (position==0) {
                messageViewHolder.divider_date.setText(day);
                messageViewHolder.divider.setVisibility(View.VISIBLE);
                messageViewHolder.divider.getLayoutParams().height = 60;
            } else
            if (!day.equals(beforeDay) && beforeDay!=null) {
                beforeViewHolder.divider_date.setText(beforeDay);
                beforeViewHolder.divider.setVisibility(View.VISIBLE);
                beforeViewHolder.divider.getLayoutParams().height = 60;
            }
            beforeViewHolder = messageViewHolder;
            beforeDay = day;
        }

        void setReadCounter (final int pos, final TextView textView) {
            int cnt = userList.size() - messageList.get(pos).readUsers.size();
            if (cnt > 0) {
                textView.setVisibility(View.VISIBLE);
                textView.setText(String.valueOf(cnt));
            } else {
                textView.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return messageList.size();
        }

        private class MessageViewHolder extends RecyclerView.ViewHolder {
            public TextView msg_item;
            public ImageView user_photo;
            public TextView msg_name;
            public TextView timestamp;
            public TextView read_counter;
            public LinearLayout divider;
            public TextView divider_date;

            public MessageViewHolder(View view) {
                super(view);
                msg_item = view.findViewById(R.id.msg_item);
                user_photo = view.findViewById(R.id.user_photo);
                timestamp = view.findViewById(R.id.timestamp);
                msg_name = view.findViewById(R.id.msg_name);
                read_counter = view.findViewById(R.id.read_counter);
                divider = view.findViewById(R.id.divider);
                divider_date = view.findViewById(R.id.divider_date);
            }
        }
    }

    @Override
    public void onBackPressed() {
        //        super.onBackPressed();
        if (valueEventListener!=null) {
            databaseReference.removeEventListener(valueEventListener);
        }
        finish();;
    }
}
