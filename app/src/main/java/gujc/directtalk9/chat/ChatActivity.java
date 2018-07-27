package gujc.directtalk9.chat;

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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
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
import gujc.directtalk9.R;
import gujc.directtalk9.common.Util9;
import gujc.directtalk9.model.ChatModel;
import gujc.directtalk9.model.NotificationModel;
import gujc.directtalk9.model.UserModel;

import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {
    private static final int PICK_FROM_ALBUM = 1;
    private static final int PICK_FROM_FILE = 2;

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
    private StorageReference storageReference;

    private ProgressDialog progressDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseDatabase.getInstance().getReference();
        storageReference  = FirebaseStorage.getInstance().getReference();

        dateFormatDay.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        dateFormatHour.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String toUid = getIntent().getStringExtra("toUid");
        String param_room_id = getIntent().getStringExtra("roomID");

        recyclerView = findViewById(R.id.recyclerView);
        msg_input = findViewById(R.id.msg_input);
        sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(sendBtnClickListener);

        findViewById(R.id.imageBtn).setOnClickListener(imageBtnClickListener);
        findViewById(R.id.fileBtn).setOnClickListener(fileBtnClickListener);

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

    // get a user info
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

    // Returns the room ID after locating the chatting room with the user ID.
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

    // get user list in a chatting room
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
            String msg = msg_input.getText().toString();
            sendMessage(msg, "0");
            msg_input.setText("");
        }
    };

    private void sendMessage(String msg, String msgtype) {
        sendBtn.setEnabled(false);

        if (roomID==null) {             // create chatting room for two user
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
        messages.msg = msg;
        messages.msgtype=msgtype;
        messages.timestamp = ServerValue.TIMESTAMP;

        // save last message
        db.child("rooms").child(roomID).child("lastmessage").setValue(messages);

        // save message
        messages.readUsers.put(myUid, true);
        db.child("rooms").child(roomID).child("messages").push().setValue(messages).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                //sendGCM();
                sendBtn.setEnabled(true);
            }
        });

        // inc unread message count
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode!= this.RESULT_OK) { return;}
        Uri fileUri = data.getData();
        final String filename = Util9.getUniqueValue();

        if (requestCode ==PICK_FROM_FILE) {
            //File file= new File(fileUri.getRootPath());
            showProgressDialog("Uploading selected File.");
            final ChatModel.FileInfo fileinfo  = getFileDetailFromUri(getApplicationContext(), fileUri);

            storageReference.child("files/"+filename).putFile(fileUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                    sendMessage(filename, "2");
                    db.child("rooms").child(roomID).child("files").child(filename).setValue(fileinfo);    // save file information
                    hideProgressDialog();
                }
            });
            return;
        }
        if (requestCode != PICK_FROM_ALBUM) { return;}

        showProgressDialog("Uploading selected Image.");
        // upload image
        storageReference.child("files/"+filename).putFile(fileUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                sendMessage(filename, "1");
                hideProgressDialog();
            }
        });
        // small image
        Glide.with(getApplicationContext())
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
            progressDialog = new ProgressDialog(this);
        }
        //progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle(title);
        progressDialog.setMessage("Please wait..");
        progressDialog.setCancelable(false);
        //progressDialog.setMax(100);
        //progressDialog.setProgress(0);
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

        List<ChatModel.Message> messageList;
        String beforeDay = null;
        MessageViewHolder beforeViewHolder;
        String rootPath = Util9.getRootPath()+"/DirectTalk9/";

        public RecyclerViewAdapter() {
            File dir = new File(rootPath);
            if (!dir.exists()) {
                if (!isStoragePermissionGranted()) {
                    return ;
                }
                dir.mkdirs();
            }

            messageList = new ArrayList<>();

            // get messages from server
            databaseReference = db.child("rooms").child(roomID).child("messages");
            valueEventListener = databaseReference.addValueEventListener(new ValueEventListener(){
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    beforeDay = null;
                    messageList.clear();
                    // Update number of messages unread to 0 => read all
                    db.child("rooms").child(roomID).child("unread").child(myUid).setValue(0);

                    Map<String, Object> unreadMessages = new HashMap<>();
                    for (DataSnapshot item: dataSnapshot.getChildren()) {
                        final ChatModel.Message message = item.getValue(ChatModel.Message.class);
                        if (!message.readUsers.containsKey(myUid)) {
                            message.readUsers.put(myUid, true);
                            unreadMessages.put(item.getKey(), message);
                        }
                        messageList.add(message);
                    }

                    if (unreadMessages.size()>0) {
                        // Marks read about unread messages
                        db.child("rooms").child(roomID).child("messages").updateChildren(unreadMessages).addOnCompleteListener(new OnCompleteListener<Void>() {
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
            ChatModel.Message message = messageList.get(position);
            if (myUid.equals(message.uid) ) {
                switch(message.msgtype){
                    case "1": return R.layout.item_chatimage_right;
                    case "2": return R.layout.item_chatfile_right;
                    default:  return R.layout.item_chatmsg_right;
                }
            } else {
                switch(message.msgtype){
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
            final ChatModel.Message message = messageList.get(position);

            setReadCounter(position, messageViewHolder.read_counter);

            String day = dateFormatDay.format( new Date( (long) message.timestamp) );
            String timestamp = dateFormatHour.format( new Date( (long) message.timestamp) );
            messageViewHolder.timestamp.setText(timestamp);
            if ("0".equals(message.msgtype)) {                                      // text message
                messageViewHolder.msg_item.setText(message.msg);
            } else
            if ("2".equals(message.msgtype)) {                                      // file transfer
                db.child("rooms").child(roomID).child("files").child(message.msg).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        ChatModel.FileInfo fileInfo = dataSnapshot.getValue(ChatModel.FileInfo.class);
                        messageViewHolder.msg_item.setText(fileInfo.filename + "\n" + fileInfo.filesize);
                        messageViewHolder.filename = fileInfo.filename;
                        messageViewHolder.realname = message.msg;
                        File file = new File(rootPath + fileInfo.filename);
                        if(file.exists()) {
                            messageViewHolder.button_item.setText("Open File");
                        } else {
                            messageViewHolder.button_item.setText("Download");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
            } else {                                                                // image transfer
                Glide.with(getApplicationContext())
                        .load(storageReference.child("filesmall/"+message.msg))
                        .apply(new RequestOptions().override(1000, 1000))
                        .into(messageViewHolder.img_item);
            }

            if (! myUid.equals(message.uid)) {
                UserModel userModel = userList.get(message.uid);
                messageViewHolder.msg_name.setText(userModel.getUsernm());

                if (userModel.getUserphoto()==null) {
                    Glide.with(getApplicationContext()).load(R.drawable.user)
                            .apply(requestOptions)
                            .into(messageViewHolder.user_photo);
                } else{
                    Glide.with(getApplicationContext())
                            .load(storageReference.child("userPhoto/"+userModel.getUserphoto()))
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
            };
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
                msgLine_item = view.findViewById(R.id.msgLine_item);
                if (msgLine_item!=null) {
                    msgLine_item.setOnClickListener(downloadClickListener);
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
                    if (!isStoragePermissionGranted()) {
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
                        uri = FileProvider.getUriForFile(getApplicationContext(), getPackageName() + ".provider", newFile);

                        List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                        for (ResolveInfo resolveInfo : resInfoList) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                    }else {
                        uri = Uri.fromFile(newFile);
                    }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setDataAndType(uri, type);//"application/vnd.android.package-archive");
                    startActivity(Intent.createChooser(intent, "Your title"));
                }
            };
        }
    }

    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.v("DirectTalk9","Permission is granted");
                return true;
            } else {
                Log.v("DirectTalk9","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v("DirectTalk9","Permission is granted");
            return true;
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
