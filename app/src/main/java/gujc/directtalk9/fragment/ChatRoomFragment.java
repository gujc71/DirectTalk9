package gujc.directtalk9.fragment;

import android.support.v4.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import gujc.directtalk9.R;
import gujc.directtalk9.chat.ChatActivity;
import gujc.directtalk9.chat.SelectUserActivity;
import gujc.directtalk9.model.ChatModel;
import gujc.directtalk9.model.ChatRoomModel;
import gujc.directtalk9.model.UserModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

public class ChatRoomFragment extends Fragment{

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public ChatRoomFragment() {
    }
    public static ChatRoomFragment newInstance() {
        ChatRoomFragment fragment = new ChatRoomFragment();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chatroom, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(new ChatRecyclerViewAdapter());
        recyclerView.setLayoutManager(new LinearLayoutManager(inflater.getContext()));

        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        FloatingActionButton makeRoomBtn = view.findViewById(R.id.makeRoomBtn);
        makeRoomBtn.setOnClickListener( new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), SelectUserActivity.class));
            }
        });

        return view;
    }

    // =============================================================================================
    class ChatRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        private List<ChatRoomModel> roomList = new ArrayList<>();
        private Map<String, UserModel> userList = new HashMap<>();
        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));
        private String myUid;

        public ChatRecyclerViewAdapter() {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            FirebaseDatabase.getInstance().getReference().child("users").addListenerForSingleValueEvent(new ValueEventListener(){
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot item : dataSnapshot.getChildren()) {
                        userList.put(item.getKey(), item.getValue(UserModel.class));
                    }
                    getRoomInfo();
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {}
            });
        }

        public void getRoomInfo() {
            FirebaseDatabase.getInstance().getReference().child("rooms").orderByChild("users/"+myUid).equalTo("i").addListenerForSingleValueEvent(new ValueEventListener(){

                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot item : dataSnapshot.getChildren()) {
                        ChatRoomModel chatRoomModel = new ChatRoomModel();
                        chatRoomModel.setRoomID(item.getKey());

                        roomList.add( chatRoomModel );

                        Map<String, Object> map = (Map<String, Object>)item.child("users").getValue();
                        if (map.size()==2) {
                            for (String key : map.keySet()) {
                                if (myUid.equals(key)) continue;
                                UserModel userModel = userList.get(key);
                                chatRoomModel.setTitle(userModel.getUsernm());
                                chatRoomModel.setPhoto(userModel.getUserphoto());
                            }
                        } else {                // group chat room
                            String title = "";
                            for (String key : map.keySet()) {
                                if (myUid.equals(key)) continue;
                                UserModel userModel = userList.get(key);
                                title += userModel.getUsernm() + ", ";
                            }
                            chatRoomModel.setTitle( title.substring(0, title.length() - 2) );
                        }
                        chatRoomModel.setUserCount(map.size());

                        if (item.child("messages").getValue()==null) continue;

                        Map<String, ChatModel.Message> cMap = new TreeMap<>(Collections.<String>reverseOrder());    // sorting
                        cMap.putAll( (Map<String, ChatModel.Message>) item.child("messages").getValue());

                        String msgID=(String) cMap.keySet().toArray()[0];

                        ChatModel.Message message = item.child("messages/"+msgID).getValue(ChatModel.Message.class);
                        chatRoomModel.setLastMsg(message.msg);
                        chatRoomModel.setLastDatetime(simpleDateFormat.format( new Date( (long) message.timestamp) ) );
                    }
                    notifyDataSetChanged();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chatroom, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RoomViewHolder roomViewHolder = (RoomViewHolder) holder;

            final ChatRoomModel chatRoomModel = roomList.get(position);

            roomViewHolder.room_title.setText(chatRoomModel.getTitle());
            roomViewHolder.last_msg.setText(chatRoomModel.getLastMsg());
            roomViewHolder.last_time.setText(chatRoomModel.getLastDatetime());
            if (chatRoomModel.getPhoto()==null) {
                Glide.with(getActivity()).load(R.drawable.user)
                        .apply(requestOptions)
                        .into(roomViewHolder.room_image);
            } else{
                Glide.with(getActivity()).load(chatRoomModel.getPhoto())
                        .apply(requestOptions)
                        .into(roomViewHolder.room_image);
            }
            if (chatRoomModel.getUserCount() > 2) {
                roomViewHolder.room_count.setText(chatRoomModel.getUserCount().toString());
            } else {
                roomViewHolder.room_count.setVisibility(View.INVISIBLE);
            }

            roomViewHolder.itemView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), ChatActivity.class);
                    intent.putExtra("roomID", chatRoomModel.getRoomID());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return roomList.size();
        }

        private class RoomViewHolder extends RecyclerView.ViewHolder {
            public ImageView room_image;
            public TextView room_title;
            public TextView last_msg;
            public TextView last_time;
            public TextView room_count;

            public RoomViewHolder(View view) {
                super(view);
                room_image = view.findViewById(R.id.room_image);
                room_title = view.findViewById(R.id.room_title);
                last_msg = view.findViewById(R.id.last_msg);
                last_time = view.findViewById(R.id.last_time);
                room_count = view.findViewById(R.id.room_count);
            }
        }
    }
}
