package gujc.directtalk9.fragment;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import gujc.directtalk9.R;
import gujc.directtalk9.common.Util9;
import gujc.directtalk9.model.ChatModel;
import gujc.directtalk9.model.Message;
import gujc.directtalk9.model.NotificationModel;
import gujc.directtalk9.model.UserModel;
import gujc.directtalk9.photoview.ViewPagerActivity;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.app.Activity.RESULT_OK;

public class ChatFragment extends Fragment{

    private static final int PICK_FROM_ALBUM = 1;
    private static final int PICK_FROM_FILE = 2;
    private static String rootPath = Util9.getRootPath()+"/DirectTalk9/";

    private Button sendBtn;
    private EditText msg_input;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter mAdapter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private SimpleDateFormat dateFormatDay = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat dateFormatHour = new SimpleDateFormat("aa hh:mm");
    private String roomID;
    private String myUid;
    private String toUid;
    private Map<String, UserModel> userList = new HashMap<>();

    private ListenerRegistration listenerRegistration;
    private FirebaseFirestore firestore=null;
    private StorageReference storageReference;
    private LinearLayoutManager linearLayoutManager;

    private ProgressDialog progressDialog = null;
    private Integer userCount = 0;

    public ChatFragment() {
    }

    public static final ChatFragment getInstance(String toUid, String roomID) {
        ChatFragment f = new ChatFragment();
        Bundle bdl = new Bundle();
        bdl.putString("toUid", toUid);
        bdl.putString("roomID", roomID);
        f.setArguments(bdl);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        msg_input = view.findViewById(R.id.msg_input);
        sendBtn = view.findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(sendBtnClickListener);

        view.findViewById(R.id.imageBtn).setOnClickListener(imageBtnClickListener);
        view.findViewById(R.id.fileBtn).setOnClickListener(fileBtnClickListener);
        view.findViewById(R.id.msg_input).setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    Util9.hideKeyboard(getActivity());
                }
            }
        });

        if (getArguments() != null) {
            roomID = getArguments().getString("roomID");
            toUid = getArguments().getString("toUid");
        }

        firestore = FirebaseFirestore.getInstance();
        storageReference  = FirebaseStorage.getInstance().getReference();

        dateFormatDay.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        dateFormatHour.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        /*
         two user: roomid or uid talking
         multi user: roomid
         */
        if (!"".equals(toUid) && toUid!=null) {                     // find existing room for two user
            findChatRoom(toUid);
        } else
        if (!"".equals(roomID) && roomID!=null) { // existing room (multi user)
            setChatRoom(roomID);
        };

        if (roomID==null) {                                                     // new room for two user
            getUserInfoFromServer(myUid);
            getUserInfoFromServer(toUid);
            userCount = 2;
        };

        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (mAdapter!=null & bottom < oldBottom) {
                    final int lastAdapterItem = mAdapter.getItemCount() - 1;
                    recyclerView.post(new Runnable() {
                        @Override
                        public void run() {
                            int recyclerViewPositionOffset = -1000000;
                            View bottomView = linearLayoutManager.findViewByPosition(lastAdapterItem);
                            if (bottomView != null) {
                                recyclerViewPositionOffset = 0 - bottomView.getHeight();
                            }
                            linearLayoutManager.scrollToPositionWithOffset(lastAdapterItem, recyclerViewPositionOffset);
                        }
                    });
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mAdapter != null) {
            mAdapter.stopListening();
        }
    }

    // get a user info
    void getUserInfoFromServer(String id){
        firestore.collection("users").document(id).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                UserModel userModel = documentSnapshot.toObject(UserModel.class);
                userList.put(userModel.getUid(), userModel);
                if (roomID != null & userCount == userList.size()) {
                    mAdapter = new RecyclerViewAdapter();
                    recyclerView.setAdapter(mAdapter);
                }
            }
        });
    }

    // Returns the room ID after locating the chatting room with the user ID.
    void findChatRoom(final String toUid){
        firestore.collection("rooms").whereGreaterThanOrEqualTo("users."+myUid, 0).get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (!task.isSuccessful()) {return;}

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Long> users = (Map<String, Long>) document.get("users");
                            if (users.size()==2 & users.get(toUid)!=null){
                                setChatRoom(document.getId());
                                break;
                            }
                        }
                    }
                });
    }

    // get user list in a chatting room
    void setChatRoom(String rid) {
        roomID = rid;
        firestore.collection("rooms").document(roomID).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (!task.isSuccessful()) {return;}
                DocumentSnapshot document = task.getResult();
                Map<String, Long> users = (Map<String, Long>) document.get("users");

                for( String key : users.keySet() ){
                    getUserInfoFromServer(key);
                }
                userCount = users.size();
                //users.put(myUid, (long) 0);
                //document.getReference().update("users", users);
            }
        });
    }

    void setUnread2Read() {
        if (roomID==null) return;

        firestore.collection("rooms").document(roomID).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (!task.isSuccessful()) {return;}
                DocumentSnapshot document = task.getResult();
                Map<String, Long> users = (Map<String, Long>) document.get("users");

                users.put(myUid, (long) 0);
                document.getReference().update("users", users);
            }
        });
    }

    public void CreateChattingRoom(final DocumentReference room) {
        Map<String, Integer> users = new HashMap<>();
        String title = "";
        for( String key : userList.keySet() ){
            users.put(key, 0);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("title", null);
        data.put("users", users);

        room.set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    mAdapter = new RecyclerViewAdapter();
                    recyclerView.setAdapter(mAdapter);
                }
            }
        });
    }
    public Map<String, UserModel> getUserList() {
        return userList;
    }

    Button.OnClickListener sendBtnClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            String msg = msg_input.getText().toString();
            sendMessage(msg, "0", null);
            msg_input.setText("");
        }
    };

    private void sendMessage(final String msg, String msgtype, final ChatModel.FileInfo fileinfo) {
        sendBtn.setEnabled(false);

        if (roomID==null) {             // create chatting room for two user
            roomID = firestore.collection("rooms").document().getId();
            CreateChattingRoom( firestore.collection("rooms").document(roomID) );
        }

        final Map<String,Object> messages = new HashMap<>();
        messages.put("uid", myUid);
        messages.put("msg", msg);
        messages.put("msgtype", msgtype);
        messages.put("timestamp", FieldValue.serverTimestamp());
        if (fileinfo!=null){
            messages.put("filename", fileinfo.filename);
            messages.put("filesize", fileinfo.filesize);
        }

        final DocumentReference docRef = firestore.collection("rooms").document(roomID);
        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (!task.isSuccessful()) {return;}

                WriteBatch batch = firestore.batch();

                // save last message
                batch.set(docRef, messages, SetOptions.merge());

                // save message
                List<String> readUsers = new ArrayList();
                readUsers.add(myUid);
                messages.put("readUsers", readUsers);//new String[]{myUid} );
                batch.set(docRef.collection("messages").document(), messages);

                // inc unread message count
                DocumentSnapshot document = task.getResult();
                Map<String, Long> users = (Map<String, Long>) document.get("users");

                for( String key : users.keySet() ){
                    if (!myUid.equals(key)) users.put(key, users.get(key)+1);
                }
                document.getReference().update("users", users);

                batch.commit().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            //sendGCM();
                            sendBtn.setEnabled(true);
                        }
                    }
                });
            }

        });
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

    // choose image
    Button.OnClickListener imageBtnClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
            startActivityForResult(intent, PICK_FROM_ALBUM);
        }
    };

    // choose file
    Button.OnClickListener fileBtnClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("*/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FROM_FILE);
        }
    };

    // uploading image / file
    @Override
    public void onActivityResult(final int requestCode, int resultCode, Intent data) {
        if (resultCode!= RESULT_OK) { return;}
        Uri fileUri = data.getData();
        final String filename = Util9.getUniqueValue();

        showProgressDialog("Uploading selected File.");
        final ChatModel.FileInfo fileinfo  = getFileDetailFromUri(getContext(), fileUri);

        storageReference.child("files/"+filename).putFile(fileUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                sendMessage(filename, Integer.toString(requestCode), fileinfo);
                hideProgressDialog();
            }
        });
        if (requestCode != PICK_FROM_ALBUM) { return;}

        // small image
        Glide.with(getContext())
                .asBitmap()
                .load(fileUri)
                .apply(new RequestOptions().override(150, 150))
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                        byte[] data = baos.toByteArray();
                        storageReference.child("filesmall/"+filename).putBytes(data);
                    }
                });
    }

    // get file name and size from Uri
    public static ChatModel.FileInfo getFileDetailFromUri(final Context context, final Uri uri) {
        if (uri == null) { return null; }

        ChatModel.FileInfo fileDetail = new ChatModel.FileInfo();
        // File Scheme.
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            fileDetail.filename = file.getName();
            fileDetail.filesize = Util9.size2String(file.length());
        }
        // Content Scheme.
        else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Cursor returnCursor =
                    context.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor != null && returnCursor.moveToFirst()) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
                fileDetail.filename = returnCursor.getString(nameIndex);
                fileDetail.filesize = Util9.size2String(returnCursor.getLong(sizeIndex));
                returnCursor.close();
            }
        }

        return fileDetail;
    }

    public void showProgressDialog(String title ) {
        if (progressDialog==null) {
            progressDialog = new ProgressDialog(getContext());
        }
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle(title);
        progressDialog.setMessage("Please wait..");
        progressDialog.setCancelable(false);
        progressDialog.show();
    }
    public void setProgressDialog(int value) {
        progressDialog.setProgress(value);
    }
    public void hideProgressDialog() {
        progressDialog.dismiss();
    }
    // =======================================================================================

    class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));

        List<Message> messageList;
        String beforeDay = null;
        MessageViewHolder beforeViewHolder;

        RecyclerViewAdapter() {
            File dir = new File(rootPath);
            if (!dir.exists()) {
                if (!Util9.isPermissionGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    return;
                }
                dir.mkdirs();
            }

            messageList = new ArrayList<Message>();
            setUnread2Read();
            startListening();
        }

        public void startListening() {
            beforeDay = null;
            messageList.clear();

            CollectionReference roomRef = firestore.collection("rooms").document(roomID).collection("messages");
            // my chatting room information
            listenerRegistration = roomRef.orderBy("timestamp").addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot documentSnapshots, @Nullable FirebaseFirestoreException e) {
                    if (e != null) {return;}

                    Message message;
                    for (DocumentChange change : documentSnapshots.getDocumentChanges()) {
                        switch (change.getType()) {
                            case ADDED:
                                message = change.getDocument().toObject(Message.class);
                                //if (message.msg !=null & message.timestamp == null) {continue;} // FieldValue.serverTimestamp is so late

                                if (message.getReadUsers().indexOf(myUid) == -1) {
                                    message.getReadUsers().add(myUid);
                                    change.getDocument().getReference().update("readUsers", message.getReadUsers());
                                }
                                messageList.add(message);
                                notifyItemInserted(change.getNewIndex());
                                break;
                            case MODIFIED:
                                message = change.getDocument().toObject(Message.class);
                                messageList.set(change.getOldIndex(), message);
                                notifyItemChanged(change.getOldIndex());
                                break;
                            case REMOVED:
                                messageList.remove(change.getOldIndex());
                                notifyItemRemoved(change.getOldIndex());
                                break;
                        }
                    }
                    recyclerView.scrollToPosition(messageList.size() - 1);
                }
            });
        }

        public void stopListening() {
            if (listenerRegistration != null) {
                listenerRegistration.remove();
                listenerRegistration = null;
            }

            messageList.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Message message = messageList.get(position);
            if (myUid.equals(message.getUid()) ) {
                switch(message.getMsgtype()){
                    case "1": return R.layout.item_chatimage_right;
                    case "2": return R.layout.item_chatfile_right;
                    default:  return R.layout.item_chatmsg_right;
                }
            } else {
                switch(message.getMsgtype()){
                    case "1": return R.layout.item_chatimage_left;
                    case "2": return R.layout.item_chatfile_left;
                    default:  return R.layout.item_chatmsg_left;
                }
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
            final MessageViewHolder messageViewHolder = (MessageViewHolder) holder;
            final Message message = messageList.get(position);

            setReadCounter(message, messageViewHolder.read_counter);

            if ("0".equals(message.getMsgtype())) {                                      // text message
                messageViewHolder.msg_item.setText(message.getMsg());
            } else
            if ("2".equals(message.getMsgtype())) {                                      // file transfer
                messageViewHolder.msg_item.setText(message.getFilename() + "\n" + message.getFilesize());
                messageViewHolder.filename = message.getFilename();
                messageViewHolder.realname = message.getMsg();
                File file = new File(rootPath + message.getFilename());
                if(file.exists()) {
                    messageViewHolder.button_item.setText("Open File");
                } else {
                    messageViewHolder.button_item.setText("Download");
                }
            } else {                                                                // image transfer
                messageViewHolder.realname = message.getMsg();
                Glide.with(getContext())
                        .load(storageReference.child("filesmall/"+message.getMsg()))
                        .apply(new RequestOptions().override(1000, 1000))
                        .into(messageViewHolder.img_item);
            }

            if (! myUid.equals(message.getUid())) {
                UserModel userModel = userList.get(message.getUid());
                messageViewHolder.msg_name.setText(userModel.getUsernm());

                if (userModel.getUserphoto()==null) {
                    Glide.with(getContext()).load(R.drawable.user)
                            .apply(requestOptions)
                            .into(messageViewHolder.user_photo);
                } else{
                    Glide.with(getContext())
                            .load(storageReference.child("userPhoto/"+userModel.getUserphoto()))
                            .apply(requestOptions)
                            .into(messageViewHolder.user_photo);
                }
            }
            messageViewHolder.divider.setVisibility(View.INVISIBLE);
            messageViewHolder.divider.getLayoutParams().height = 0;
            messageViewHolder.timestamp.setText("");
            if (message.getTimestamp()==null) {return;}

            String day = dateFormatDay.format( message.getTimestamp());
            String timestamp = dateFormatHour.format( message.getTimestamp());
            messageViewHolder.timestamp.setText(timestamp);

            if (position==0) {
                messageViewHolder.divider_date.setText(day);
                messageViewHolder.divider.setVisibility(View.VISIBLE);
                messageViewHolder.divider.getLayoutParams().height = 60;
            } else {
                Message beforeMsg = messageList.get(position - 1);
                String beforeDay = dateFormatDay.format( beforeMsg.getTimestamp() );

                if (!day.equals(beforeDay) && beforeDay != null) {
                    messageViewHolder.divider_date.setText(day);
                    messageViewHolder.divider.setVisibility(View.VISIBLE);
                    messageViewHolder.divider.getLayoutParams().height = 60;
                }
            }
            /*messageViewHolder.timestamp.setText("");
            if (message.getTimestamp()==null) {return;}

            String day = dateFormatDay.format( message.getTimestamp());
            String timestamp = dateFormatHour.format( message.getTimestamp());

            messageViewHolder.timestamp.setText(timestamp);

            if (position==0) {
                messageViewHolder.divider_date.setText(day);
                messageViewHolder.divider.setVisibility(View.VISIBLE);
                messageViewHolder.divider.getLayoutParams().height = 60;
            };
            if (!day.equals(beforeDay) && beforeDay!=null) {
                beforeViewHolder.divider_date.setText(beforeDay);
                beforeViewHolder.divider.setVisibility(View.VISIBLE);
                beforeViewHolder.divider.getLayoutParams().height = 60;
            }
            beforeViewHolder = messageViewHolder;
            beforeDay = day;*/
        }

        void setReadCounter (Message message, final TextView textView) {
            int cnt = userCount - message.getReadUsers().size();
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


    }

    private class MessageViewHolder extends RecyclerView.ViewHolder {
        public ImageView user_photo;
        public TextView msg_item;
        public ImageView img_item;          // only item_chatimage_
        public TextView msg_name;
        public TextView timestamp;
        public TextView read_counter;
        public LinearLayout divider;
        public TextView divider_date;
        public TextView button_item;            // only item_chatfile_
        public LinearLayout msgLine_item;       // only item_chatfile_
        public String filename;
        public String realname;

        public MessageViewHolder(View view) {
            super(view);
            user_photo = view.findViewById(R.id.user_photo);
            msg_item = view.findViewById(R.id.msg_item);
            img_item = view.findViewById(R.id.img_item);
            timestamp = view.findViewById(R.id.timestamp);
            msg_name = view.findViewById(R.id.msg_name);
            read_counter = view.findViewById(R.id.read_counter);
            divider = view.findViewById(R.id.divider);
            divider_date = view.findViewById(R.id.divider_date);
            button_item = view.findViewById(R.id.button_item);
            msgLine_item = view.findViewById(R.id.msgLine_item);        // for file
            if (msgLine_item!=null) {
                msgLine_item.setOnClickListener(downloadClickListener);
            }
            if (img_item!=null) {                                       // for image
                img_item.setOnClickListener(imageClickListener);
            }
        }
        // file download and open
        Button.OnClickListener downloadClickListener = new View.OnClickListener() {
            public void onClick(View view) {
                if ("Download".equals(button_item.getText())) {
                    download();
                } else {
                    openWith();
                }
            }
            public void download() {
                if (!Util9.isPermissionGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    return ;
                }
                showProgressDialog("Downloading File.");

                final File localFile = new File(rootPath, filename);

                storageReference.child("files/"+realname).getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        button_item.setText("Open File");
                        hideProgressDialog();
                        Log.e("DirectTalk9 ","local file created " +localFile.toString());
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.e("DirectTalk9 ","local file not created  " +exception.toString());
                    }
                });
            }

            public void openWith() {
                File newFile = new File(rootPath + filename);
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                String ext = newFile.getName().substring(newFile.getName().lastIndexOf(".") + 1);
                String type = mime.getMimeTypeFromExtension(ext);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uri = FileProvider.getUriForFile(getContext(), getActivity().getPackageName() + ".provider", newFile);

                    List<ResolveInfo> resInfoList = getActivity().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        getActivity().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }else {
                    uri = Uri.fromFile(newFile);
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(uri, type);//"application/vnd.android.package-archive");
                startActivity(Intent.createChooser(intent, "Your title"));
            }
        };
        // photo view
        Button.OnClickListener imageClickListener = new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), ViewPagerActivity.class);
                intent.putExtra("roomID", roomID);
                intent.putExtra("realname", realname);
                startActivity(intent);
            }
        };
    }

    public void backPressed() {
    }
}
