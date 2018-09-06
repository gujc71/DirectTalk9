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
import gujc.directtalk9.common.FirestoreAdapter;
import gujc.directtalk9.common.Util9;
import gujc.directtalk9.model.UserModel;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class SelectUserActivity extends AppCompatActivity {
    private String roomID;
    private Map<String, String> selectedUsers = new HashMap<>();
    private FirestoreAdapter firestoreAdapter;

    @Override
    public void onStart() {
        super.onStart();
        if (firestoreAdapter != null) {
            firestoreAdapter.startListening();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (firestoreAdapter != null) {
            firestoreAdapter.stopListening();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_user);

        roomID = getIntent().getStringExtra("roomID");

        firestoreAdapter = new RecyclerViewAdapter(FirebaseFirestore.getInstance().collection("users").orderBy("usernm"));
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager( new LinearLayoutManager((this)));
        recyclerView.setAdapter(firestoreAdapter);

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

            selectedUsers.put(FirebaseAuth.getInstance().getCurrentUser().getUid(), "");

            DocumentReference newRoom = FirebaseFirestore.getInstance().collection("rooms").document();
            CreateChattingRoom(newRoom);
        }
    };

    Button.OnClickListener addRoomUserClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            if (selectedUsers.size() <1) {
                Util9.showMessage(getApplicationContext(), "Please select 1 or more user");
                return;
            }
            CreateChattingRoom(FirebaseFirestore.getInstance().collection("rooms").document(roomID) );
        }
    };

    public void CreateChattingRoom(final DocumentReference room) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Map<String, Integer> users = new HashMap<>();
        String title = "";
        for( String key : selectedUsers.keySet()) {
            users.put(key, 0);
            if (title.length() < 20 & !key.equals(uid)) {
                title += selectedUsers.get(key) + ", ";
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("title", title.substring(0, title.length() - 2));
        data.put("users", users);

        room.set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Intent intent = new Intent(SelectUserActivity.this, ChatActivity.class);
                    intent.putExtra("roomID", room.getId());
                    startActivity(intent);
                    SelectUserActivity.this.finish();
                }
            }
        });
    }

    class RecyclerViewAdapter extends FirestoreAdapter<CustomViewHolder> {

        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));
        private StorageReference storageReference;
        private String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        RecyclerViewAdapter(Query query) {
            super(query);
            storageReference  = FirebaseStorage.getInstance().getReference();
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_user, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomViewHolder viewHolder, int position) {
            DocumentSnapshot documentSnapshot = getSnapshot(position);
            final UserModel userModel = documentSnapshot.toObject(UserModel.class);

            if (myUid.equals(userModel.getUid())) {
                viewHolder.itemView.setVisibility(View.INVISIBLE);
                viewHolder.itemView.getLayoutParams().height = 0;
                return;
            }

            viewHolder.user_name.setText(userModel.getUsernm());

            if (userModel.getUserphoto()==null) {
                Glide.with(getApplicationContext()).load(R.drawable.user)
                        .apply(requestOptions)
                        .into(viewHolder.user_photo);
            } else{
                Glide.with(getApplicationContext())
                        .load(storageReference.child("userPhoto/"+userModel.getUserphoto()))
                        .apply(requestOptions)
                        .into(viewHolder.user_photo);
            }

            viewHolder.userChk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectedUsers.put(userModel.getUid(), userModel.getUsernm());
                    } else {
                        selectedUsers.remove(userModel.getUid());
                    }
                }
            });
        }
    }

    private class CustomViewHolder extends RecyclerView.ViewHolder {
        public ImageView user_photo;
        public TextView user_name;
        public CheckBox userChk;

        CustomViewHolder(View view) {
            super(view);
            user_photo = view.findViewById(R.id.user_photo);
            user_name = view.findViewById(R.id.user_name);
            userChk = view.findViewById(R.id.userChk);
        }
    }

}
