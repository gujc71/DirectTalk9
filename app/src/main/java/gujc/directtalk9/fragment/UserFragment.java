package gujc.directtalk9.fragment;

import android.support.v4.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import gujc.directtalk9.R;
import gujc.directtalk9.UserPWActivity;
import gujc.directtalk9.common.Util9;
import gujc.directtalk9.model.UserModel;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.UploadTask;

public class UserFragment extends Fragment {
    private static final int PICK_FROM_ALBUM = 1;
    private ImageView user_photo;
    private EditText user_id;
    private EditText user_name;
    private EditText user_msg;
    private Button saveBtn;
    private Button changePWBtn;

    private UserModel userModel;
    private Uri userPhotoUri;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user, container, false);

        user_id = view.findViewById(R.id.user_id);
        user_id.setEnabled(false);
        user_name = view.findViewById(R.id.user_name);
        user_msg = view.findViewById(R.id.user_msg);
        user_photo = view.findViewById(R.id.user_photo);
        user_photo.setOnClickListener(userPhotoIVClickListener);

        saveBtn = view.findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(saveBtnClickListener);
        changePWBtn = view.findViewById(R.id.changePWBtn);
        changePWBtn.setOnClickListener(changePWBtnClickListener);

        getUserInfoFromServer();
        return view;
    }

    void getUserInfoFromServer(){
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase.getInstance().getReference().child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                userModel = dataSnapshot.getValue(UserModel.class);
                user_id.setText(userModel.getUserid());
                user_name.setText(userModel.getUsernm());
                user_msg.setText(userModel.getUsermsg());
                if (userModel.getUserphoto()!= null && !"".equals(userModel.getUserphoto())) {
                    Glide.with(getActivity())
                         .load(FirebaseStorage.getInstance().getReference("userPhoto/"+userModel.getUserphoto()))
                         .into(user_photo);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    Button.OnClickListener userPhotoIVClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
            startActivityForResult(intent, PICK_FROM_ALBUM);
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==PICK_FROM_ALBUM && resultCode== getActivity().RESULT_OK) {
            user_photo.setImageURI(data.getData());
            userPhotoUri = data.getData();
        }
    }

    Button.OnClickListener saveBtnClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            if (!validateForm()) return;
            userModel.setUsernm(user_name.getText().toString());
            userModel.setUsermsg(user_msg.getText().toString());

            final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            if (userPhotoUri==null) {
                FirebaseDatabase.getInstance().getReference().child("users").child(uid).setValue(userModel).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Util9.showMessage(getActivity(), "Success to Save.");
                    }
                });
            } else {
                FirebaseStorage.getInstance().getReference().child("userPhoto").child(uid).putFile(userPhotoUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        userModel.setUserphoto( uid );
                        FirebaseDatabase.getInstance().getReference().child("users").child(uid).setValue(userModel).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                Util9.showMessage(getActivity(), "Success to Save.");
                            }
                        });
                    }
                });
            }
        }
    };

    Button.OnClickListener changePWBtnClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
        //getFragmentManager().beginTransaction().replace(R.id.container, new UserPWFragment()).commit();
        startActivity(new Intent(getActivity(), UserPWActivity.class));
        }
    };

    private boolean validateForm() {
        boolean valid = true;

        String userName = user_name.getText().toString();
        if (TextUtils.isEmpty(userName)) {
            user_name.setError("Required.");
            valid = false;
        } else {
            user_name.setError(null);
        }

        String userMsg = user_msg.getText().toString();
        if (TextUtils.isEmpty(userMsg)) {
            user_msg.setError("Required.");
            valid = false;
        } else {
            user_msg.setError(null);
        }
        Util9.hideKeyboard(getActivity());

        return valid;
    }

}
