package gujc.directtalk9.chat;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import gujc.directtalk9.R;
import gujc.directtalk9.common.Util9;
import gujc.directtalk9.model.ChatModel;
import gujc.directtalk9.model.UserModel;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectUserActivity extends AppCompatActivity {
    private String roomID;
    public Map<String, String> selectedUsers = new HashMap<>() ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_user);

        roomID = getIntent().getStringExtra("roomID");

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager( new LinearLayoutManager((this)));
        recyclerView.setAdapter(new UserListRecyclerViewAdapter());

        Button makeRoomBtn = findViewById(R.id.makeRoomBtn);
        if (roomID==null)
             makeRoomBtn.setOnClickListener(makeRoomClickListener);
        else makeRoomBtn.setOnClickListener(addRoomUserClickListener);
    }

    Button.OnClickListener makeRoomClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            if (selectedUsers.size() <2) {
                Util9.showMessage(getApplicationContext(), "Please select 2 or more user");
                return;
            }
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            selectedUsers.put(uid, "i");
            final String room_id = FirebaseDatabase.getInstance().getReference().child("rooms").push().getKey();

            FirebaseDatabase.getInstance().getReference().child("rooms/"+room_id).child("users").setValue(selectedUsers).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Intent intent = new Intent(SelectUserActivity.this, ChatActivity.class);
                    intent.putExtra("roomID", room_id);
                    startActivity(intent);
                    SelectUserActivity.this.finish();
                }
            });
        }
    };

    Button.OnClickListener addRoomUserClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            if (selectedUsers.size() <1) {
                Util9.showMessage(getApplicationContext(), "Please select 1 or more user");
                return;
            }

            FirebaseDatabase.getInstance().getReference().child("rooms").child(roomID).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (final DataSnapshot item : dataSnapshot.getChildren()) {
                        selectedUsers.put(item.getKey(), (String) item.getValue());
                    }
                    FirebaseDatabase.getInstance().getReference().child("rooms/"+roomID).child("users").setValue(selectedUsers).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Intent intent = new Intent(SelectUserActivity.this, ChatActivity.class);
                            intent.putExtra("roomID", roomID);
                            startActivity(intent);
                            SelectUserActivity.this.finish();
                        }
                    });
                }

                @Override
                public void onCancelled(DatabaseError databaseError) { }
            });
        }
    };
    class UserListRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{

        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));
        List<UserModel> userModels;
        private StorageReference storageReference;

        public UserListRecyclerViewAdapter() {
            storageReference  = FirebaseStorage.getInstance().getReference();
            userModels = new ArrayList<>();
            final String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            FirebaseDatabase.getInstance().getReference().child("users").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    userModels.clear();
                    for(DataSnapshot snapshot: dataSnapshot.getChildren()) {
                        UserModel userModel = snapshot.getValue(UserModel.class);
                        if (myUid.equals(userModel.getUid())) continue;

                        userModels.add(userModel);
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_user, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            CustomViewHolder customViewHolder = (CustomViewHolder) holder;
            final UserModel user = userModels.get(position);

            customViewHolder.user_name.setText(user.getUsernm());

            if (user.getUserphoto()==null) {
                Glide.with(getApplicationContext()).load(R.drawable.user)
                        .apply(requestOptions)
                        .into(customViewHolder.user_photo);
            } else{
                Glide.with(getApplicationContext())
                        .load(storageReference.child("userPhoto/"+user.getUserphoto()))
                        .apply(requestOptions)
                        .into(customViewHolder.user_photo);
            }

            ((CustomViewHolder)holder).userChk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    selectedUsers.put(user.getUid(), "i");
                } else {
                    selectedUsers.remove(user.getUid());
                }
                }
            });
        }

        @Override
        public int getItemCount() {
            return userModels.size();
        }
    }

    private class CustomViewHolder extends RecyclerView.ViewHolder {
        public ImageView user_photo;
        public TextView user_name;
        public CheckBox userChk;

        public CustomViewHolder(View view) {
            super(view);
            user_photo = view.findViewById(R.id.user_photo);
            user_name = view.findViewById(R.id.user_name);
            userChk = view.findViewById(R.id.userChk);
        }
    }

}
